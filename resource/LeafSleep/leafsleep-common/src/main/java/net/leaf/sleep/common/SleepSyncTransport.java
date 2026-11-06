package net.leaf.sleep.common;

import io.github.dailystruggle.rtp.common.predict.BudgetPacer;
import io.github.dailystruggle.rtp.proxy.common.transport.redis.resp.RespConnection;
import io.github.dailystruggle.rtp.proxy.common.transport.redis.resp.RespEndpoint;
import io.github.dailystruggle.rtp.proxy.common.transport.redis.resp.RespPool;
import io.github.dailystruggle.rtp.proxy.common.transport.redis.resp.RespPubSub;
import io.github.dailystruggle.server.api.ServerAccessor;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Fast transient synchronization bus for sub-50ms cross-shard sleep coordination ({@code LeafSleep
 * ADR-001}).
 *
 * <p>Supports {@code LOCAL_ONLY}, {@code PLUGIN_MESSAGING} ({@code leafsleep:sync}), {@code
 * DIRECT_SOCKET} (HMAC-SHA256 authenticated UDP peer gossip with replay protection), and {@code
 * REDIS_PUBSUB} (backed by {@link RespPool} and {@link RespPubSub} from {@code :database-api}),
 * guarded by {@link BudgetPacer} to prevent bed-click spam amplification.
 *
 * @governedBy ADR-001
 * @governedBy ADR-002
 * @trace S-004
 * @trace S-005
 */
public interface SleepSyncTransport extends AutoCloseable {

  /** Starts the transport and subscribes {@code receiver} to incoming peer frames. */
  void start(Consumer<SleepSyncFrame> receiver);

  /** Broadcasts a delta frame to peer shards if admitted by the {@link BudgetPacer} guard. */
  boolean publish(SleepSyncFrame frame);

  /** Returns the configured transport mode. */
  LeafSleepConfig.TransportType mode();

  /** Returns whether this transport is currently healthy and open. */
  boolean isHealthy();

  /** Total frames successfully published by this transport instance. */
  long publishedCount();

  /** Total frames dropped by {@link BudgetPacer} rate limiting or transport errors. */
  long droppedCount();

  @Override
  void close();

  /**
   * Factory creating the appropriate {@link SleepSyncTransport} for {@code config}.
   *
   * @param accessor platform server accessor
   * @param config parsed LeafSleep configuration
   * @return initialized transport instance; never {@code null}
   */
  static SleepSyncTransport create(ServerAccessor accessor, LeafSleepConfig config) {
    Objects.requireNonNull(accessor, "accessor");
    Objects.requireNonNull(config, "config");
    LeafSleepConfig.ShardingConfig sharding = config.sharding();
    if (!sharding.enabled()) {
      return new LocalOnlyTransport(config.budgetMaxFramesPerTick());
    }
    return switch (sharding.transport()) {
      case LOCAL_ONLY -> new LocalOnlyTransport(config.budgetMaxFramesPerTick());
      case PLUGIN_MESSAGING -> {
        String envVal = System.getenv(sharding.hmacSecretEnv());
        byte[] secret =
            (envVal != null && !envVal.isBlank())
                ? envVal.getBytes(StandardCharsets.UTF_8)
                : null;
        yield new PluginMessagingTransport(
            accessor,
            sharding.transport(),
            sharding.channel(),
            secret,
            config.budgetMaxFramesPerTick());
      }
      case REDIS_PUBSUB -> {
        String envVal = System.getenv(sharding.hmacSecretEnv());
        String redisHostEnv = System.getenv("LEAFSLEEP_REDIS_HOST");
        String redisPortEnv = System.getenv("LEAFSLEEP_REDIS_PORT");
        String redisPass = System.getenv("LEAFSLEEP_REDIS_PASSWORD");
        String redisHost =
            (redisHostEnv != null && !redisHostEnv.isBlank())
                ? redisHostEnv.trim()
                : sharding.bindHost();
        int redisPort = sharding.bindPort() == 25620 ? 6379 : sharding.bindPort();
        if (redisPortEnv != null && !redisPortEnv.isBlank()) {
          try {
            redisPort = Integer.parseInt(redisPortEnv.trim());
          } catch (NumberFormatException ignored) {
          }
        }
        byte[] secret =
            (envVal != null && !envVal.isBlank())
                ? envVal.getBytes(StandardCharsets.UTF_8)
                : null;
        yield new RedisPubSubTransport(
            accessor,
            redisHost,
            redisPort,
            redisPass,
            sharding.channel(),
            secret,
            config.budgetMaxFramesPerTick());
      }
      case DIRECT_SOCKET -> {
        String envVal = System.getenv(sharding.hmacSecretEnv());
        byte[] secret;
        if (envVal != null && !envVal.isBlank()) {
          secret = envVal.getBytes(StandardCharsets.UTF_8);
        } else {
          accessor.log(
              Level.WARNING,
              "[LeafSleep] Environment variable '"
                  + sharding.hmacSecretEnv()
                  + "' is not set for DIRECT_SOCKET transport; using cluster fallback key. "
                  + "Set "
                  + sharding.hmacSecretEnv()
                  + " in production (S-004).");
          secret = "leafsleep-default-cluster-secret-key".getBytes(StandardCharsets.UTF_8);
        }
        yield new DirectSocketTransport(
            accessor,
            sharding.bindHost(),
            sharding.bindPort(),
            sharding.peers(),
            secret,
            config.budgetMaxFramesPerTick());
      }
    };
  }

  /**
   * Bounded sliding-window replay cache preventing duplicate or replayed signed packets within
   * {@link SleepSyncFrame#DEFAULT_REPLAY_WINDOW_MS}.
   */
  final class ReplayWindowCache {
    private static final int MAX_ENTRIES = 2048;
    private final Map<Long, Long> seenFingerprints = new ConcurrentHashMap<>();
    private final long windowMs;

    public ReplayWindowCache(long windowMs) {
      this.windowMs = Math.max(1_000L, windowMs);
    }

    /**
     * Records {@code fingerprint} if not seen within the active replay window.
     *
     * @return {@code true} if the frame is fresh and admitted; {@code false} if it is a duplicate
     */
    public boolean checkAndRecord(long fingerprint) {
      if (fingerprint == 0L) {
        return true;
      }
      long now = System.currentTimeMillis();
      Long prev = seenFingerprints.put(fingerprint, now);
      if (prev != null && (now - prev) <= windowMs) {
        return false;
      }
      if (seenFingerprints.size() > MAX_ENTRIES) {
        evictExpired(now);
      }
      return true;
    }

    private void evictExpired(long now) {
      Iterator<Map.Entry<Long, Long>> it = seenFingerprints.entrySet().iterator();
      int removed = 0;
      while (it.hasNext()) {
        Map.Entry<Long, Long> entry = it.next();
        if ((now - entry.getValue()) > windowMs || removed < MAX_ENTRIES / 4) {
          it.remove();
          removed++;
          if (seenFingerprints.size() <= MAX_ENTRIES * 3 / 4) {
            break;
          }
        }
      }
    }
  }

  /**
   * Per-shard and per-player {@link BudgetPacer} rate limiter preventing bed-click spam from
   * amplifying across the network ({@code ADR-001}).
   */
  final class PacerGuard {
    private static final long TICK_BUDGET_NANOS = 5_000_000L; // 5ms per-tick outbound budget
    private final int maxFramesPerWindow;
    private final BudgetPacer shardPacer;
    private final Map<UUID, Long> playerLastEventMs = new ConcurrentHashMap<>();
    private volatile long windowStartMs = System.currentTimeMillis();
    private int framesInWindow = 0;
    private long elapsedNanosInWindow = 0L;

    public PacerGuard(int maxFramesPerTick) {
      this.maxFramesPerWindow = Math.max(1, maxFramesPerTick);
      this.shardPacer =
          BudgetPacer.ofTaskPipe(Math.max(1_000L, TICK_BUDGET_NANOS / this.maxFramesPerWindow));
    }

    public synchronized boolean tryAcquire(SleepSyncFrame frame) {
      if (frame != null
          && (frame.type() == SleepSyncFrame.FrameType.EPOCH_COMMIT
              || frame.type() == SleepSyncFrame.FrameType.CANCEL_SLEEP)) {
        return true;
      }
      long nowMs = System.currentTimeMillis();
      if (nowMs - windowStartMs >= 50L) {
        windowStartMs = nowMs;
        framesInWindow = 0;
        elapsedNanosInWindow = 0L;
      }
      if (framesInWindow >= maxFramesPerWindow) {
        return false;
      }
      boolean ranAny = framesInWindow > 0;
      if (!shardPacer.canExecute(elapsedNanosInWindow, TICK_BUDGET_NANOS, ranAny)) {
        return false;
      }
      if (frame != null
          && frame.playerId() != null
          && (frame.type() == SleepSyncFrame.FrameType.BED_ENTER
              || frame.type() == SleepSyncFrame.FrameType.BED_LEAVE)) {
        Long prev = playerLastEventMs.get(frame.playerId());
        if (prev != null && (nowMs - prev) < 25L) {
          return false;
        }
        playerLastEventMs.put(frame.playerId(), nowMs);
      }
      framesInWindow++;
      return true;
    }

    public void recordSendDuration(long durationNanos) {
      long safe = Math.max(1_000L, durationNanos);
      shardPacer.recordDuration(safe);
      synchronized (this) {
        elapsedNanosInWindow += safe;
      }
    }
  }

  /** Local-only (or in-memory peer hub) transport for single-server or multi-shard simulation. */
  final class LocalOnlyTransport implements SleepSyncTransport {
    private final PacerGuard pacer;
    private final CopyOnWriteArrayList<LocalOnlyTransport> connectedPeers =
        new CopyOnWriteArrayList<>();
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private volatile Consumer<SleepSyncFrame> receiver;
    private volatile boolean closed;

    public LocalOnlyTransport(int maxFramesPerTick) {
      this.pacer = new PacerGuard(maxFramesPerTick);
    }

    /** Connects two in-memory transports for deterministic multi-shard simulation tests. */
    public void connectPeer(LocalOnlyTransport peer) {
      if (peer != null && peer != this) {
        connectedPeers.addIfAbsent(peer);
        peer.connectedPeers.addIfAbsent(this);
      }
    }

    @Override
    public synchronized void start(Consumer<SleepSyncFrame> receiver) {
      this.receiver = receiver;
      this.closed = false;
    }

    @Override
    public boolean publish(SleepSyncFrame frame) {
      if (closed || frame == null) return false;
      long start = System.nanoTime();
      if (!pacer.tryAcquire(frame)) {
        dropped.incrementAndGet();
        return false;
      }
      byte[] wire = frame.encodeBytes();
      pacer.recordSendDuration(System.nanoTime() - start);
      for (LocalOnlyTransport peer : connectedPeers) {
        Consumer<SleepSyncFrame> r = peer.receiver;
        if (r != null && !peer.closed) {
          try {
            r.accept(SleepSyncFrame.decodeBytes(wire));
          } catch (IOException ignored) {
          }
        }
      }
      published.incrementAndGet();
      return true;
    }

    @Override
    public LeafSleepConfig.TransportType mode() {
      return LeafSleepConfig.TransportType.LOCAL_ONLY;
    }

    @Override
    public boolean isHealthy() {
      return !closed;
    }

    @Override
    public long publishedCount() {
      return published.get();
    }

    @Override
    public long droppedCount() {
      return dropped.get();
    }

    @Override
    public synchronized void close() {
      closed = true;
      connectedPeers.clear();
    }
  }

  /** Plugin messaging ({@code leafsleep:sync}) channel transport implementation. */
  final class PluginMessagingTransport implements SleepSyncTransport {
    private final ServerAccessor accessor;
    private final LeafSleepConfig.TransportType transportType;
    private final String channel;
    private final byte[] hmacSecret;
    private final PacerGuard pacer;
    private final ReplayWindowCache replayCache =
        new ReplayWindowCache(SleepSyncFrame.DEFAULT_REPLAY_WINDOW_MS);
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private volatile AutoCloseable subscription;
    private volatile boolean closed;

    public PluginMessagingTransport(
        ServerAccessor accessor,
        LeafSleepConfig.TransportType transportType,
        String channel,
        int maxFramesPerTick) {
      this(accessor, transportType, channel, null, maxFramesPerTick);
    }

    public PluginMessagingTransport(
        ServerAccessor accessor,
        LeafSleepConfig.TransportType transportType,
        String channel,
        byte[] hmacSecret,
        int maxFramesPerTick) {
      this.accessor = Objects.requireNonNull(accessor, "accessor");
      this.transportType =
          transportType == null ? LeafSleepConfig.TransportType.PLUGIN_MESSAGING : transportType;
      this.channel = (channel == null || channel.isBlank()) ? "leafsleep:sync" : channel;
      this.hmacSecret = (hmacSecret != null && hmacSecret.length > 0) ? hmacSecret.clone() : null;
      this.pacer = new PacerGuard(maxFramesPerTick);
    }

    @Override
    public synchronized void start(Consumer<SleepSyncFrame> receiver) {
      this.closed = false;
      this.subscription =
          accessor.registerMessagingChannel(
              channel,
              (playerId, bytes) -> {
                if (closed || receiver == null || bytes == null) return;
                try {
                  SleepSyncFrame decoded;
                  if (hmacSecret != null) {
                    decoded = SleepSyncFrame.decodeSigned(bytes, hmacSecret);
                    long fp = SleepSyncFrame.extractSignatureFingerprint(bytes);
                    if (!replayCache.checkAndRecord(fp)) {
                      dropped.incrementAndGet();
                      return;
                    }
                  } else {
                    decoded = SleepSyncFrame.decodeBytes(bytes);
                  }
                  accessor
                      .scheduler()
                      .ifPresentOrElse(
                          s -> s.runSync(() -> receiver.accept(decoded)),
                          () -> receiver.accept(decoded));
                } catch (IOException ex) {
                  dropped.incrementAndGet();
                  accessor.log(
                      Level.WARNING,
                      "[LeafSleep] Dropped malformed frame on channel "
                          + channel
                          + ": "
                          + ex.getMessage());
                }
              });
    }

    @Override
    public boolean publish(SleepSyncFrame frame) {
      if (closed || frame == null) return false;
      long start = System.nanoTime();
      if (!pacer.tryAcquire(frame)) {
        dropped.incrementAndGet();
        return false;
      }
      byte[] payload =
          hmacSecret != null ? frame.encodeSigned(hmacSecret) : frame.encodeBytes();
      boolean sent = accessor.sendPluginMessage(channel, payload);
      pacer.recordSendDuration(System.nanoTime() - start);
      if (sent) {
        published.incrementAndGet();
      } else {
        dropped.incrementAndGet();
      }
      return sent;
    }

    @Override
    public LeafSleepConfig.TransportType mode() {
      return transportType;
    }

    @Override
    public boolean isHealthy() {
      return !closed;
    }

    @Override
    public long publishedCount() {
      return published.get();
    }

    @Override
    public long droppedCount() {
      return dropped.get();
    }

    @Override
    public synchronized void close() {
      closed = true;
      AutoCloseable sub = this.subscription;
      if (sub != null) {
        try {
          sub.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  /**
   * Redis RESP2 pub/sub transport backed by {@link RespPool} and {@link RespPubSub} from {@code
   * :database-api}, enabling multi-proxy and empty-shard fan-out without requiring an online player
   * on every backend server.
   */
  final class RedisPubSubTransport implements SleepSyncTransport {
    private final ServerAccessor accessor;
    private final RespEndpoint endpoint;
    private final String password;
    private final String channel;
    private final byte[] hmacSecret;
    private final PacerGuard pacer;
    private final ReplayWindowCache replayCache =
        new ReplayWindowCache(SleepSyncFrame.DEFAULT_REPLAY_WINDOW_MS);
    private final RespPool pool;
    private final ExecutorService publishExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean healthy = new AtomicBoolean(false);
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private volatile RespPubSub subscriber;
    private volatile Thread subscriberThread;

    public RedisPubSubTransport(
        ServerAccessor accessor,
        String hostOrUrl,
        int port,
        String password,
        String channel,
        byte[] hmacSecret,
        int maxFramesPerTick) {
      this.accessor = Objects.requireNonNull(accessor, "accessor");
      this.endpoint = RespEndpoint.parse(hostOrUrl == null ? "127.0.0.1" : hostOrUrl, port > 0 ? port : 6379);
      this.password = (password != null && !password.isEmpty()) ? password : null;
      this.channel = (channel == null || channel.isBlank()) ? "leafsleep:sync" : channel;
      this.hmacSecret = (hmacSecret != null && hmacSecret.length > 0) ? hmacSecret.clone() : null;
      this.pacer = new PacerGuard(maxFramesPerTick);
      this.pool = new RespPool(this.endpoint, 2000, this.password, 8);
      this.publishExecutor =
          Executors.newSingleThreadExecutor(
              r -> {
                Thread t = new Thread(r, "leafsleep-redis-pub-writer-" + this.endpoint.port());
                t.setDaemon(true);
                return t;
              });
    }

    @Override
    public void start(Consumer<SleepSyncFrame> receiver) {
      if (!running.compareAndSet(false, true)) return;
      RespPubSub pubSub =
          new RespPubSub() {
            @Override
            public void onMessage(String ch, String message) {
              if (!running.get() || receiver == null || message == null || message.isEmpty()) {
                return;
              }
              try {
                byte[] wire = Base64.getDecoder().decode(message);
                SleepSyncFrame frame;
                if (hmacSecret != null) {
                  frame = SleepSyncFrame.decodeSigned(wire, hmacSecret);
                  long fp = SleepSyncFrame.extractSignatureFingerprint(wire);
                  if (!replayCache.checkAndRecord(fp)) {
                    dropped.incrementAndGet();
                    return;
                  }
                } else {
                  frame = SleepSyncFrame.decodeBytes(wire);
                }
                accessor
                    .scheduler()
                    .ifPresentOrElse(
                        s -> s.runSync(() -> receiver.accept(frame)),
                        () -> receiver.accept(frame));
              } catch (IllegalArgumentException | IOException ex) {
                dropped.incrementAndGet();
                accessor.log(
                    Level.WARNING,
                    "[LeafSleep] Dropped invalid Redis pub/sub frame on "
                        + ch
                        + ": "
                        + ex.getMessage());
              }
            }

            @Override
            public void onSubscribe(String ch, int subscribedChannels) {
              healthy.set(true);
            }
          };
      this.subscriber = pubSub;
      healthy.set(true);
      Thread t =
          new Thread(() -> subscribeLoop(pubSub), "leafsleep-redis-pubsub-" + endpoint.port());
      t.setDaemon(true);
      t.start();
      this.subscriberThread = t;
    }

    private void subscribeLoop(RespPubSub pubSub) {
      while (running.get()) {
        try (RespConnection subConn = new RespConnection(endpoint, 0, password)) {
          healthy.set(true);
          pubSub.proceed(subConn, channel);
        } catch (Exception ex) {
          healthy.set(false);
          if (!running.get()) {
            break;
          }
          accessor.log(
              Level.WARNING,
              "[LeafSleep] Redis pub/sub listener disconnected from "
                  + endpoint
                  + ": "
                  + ex.getMessage());
          try {
            Thread.sleep(250L);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }

    @Override
    public boolean publish(SleepSyncFrame frame) {
      if (!running.get() || frame == null) return false;
      long start = System.nanoTime();
      if (!pacer.tryAcquire(frame)) {
        dropped.incrementAndGet();
        return false;
      }
      byte[] wire = hmacSecret != null ? frame.encodeSigned(hmacSecret) : frame.encodeBytes();
      String encoded = Base64.getEncoder().encodeToString(wire);
      pacer.recordSendDuration(System.nanoTime() - start);
      try {
        publishExecutor.execute(
            () -> {
              if (!running.get()) return;
              try (RespConnection conn = pool.getResource()) {
                conn.publish(channel, encoded);
                healthy.set(true);
              } catch (Exception ex) {
                healthy.set(false);
                dropped.incrementAndGet();
                accessor.log(
                    Level.WARNING,
                    "[LeafSleep] Failed to publish frame to Redis channel "
                        + channel
                        + ": "
                        + ex.getMessage());
              }
            });
        published.incrementAndGet();
        return true;
      } catch (RejectedExecutionException ex) {
        dropped.incrementAndGet();
        return false;
      }
    }

    @Override
    public LeafSleepConfig.TransportType mode() {
      return LeafSleepConfig.TransportType.REDIS_PUBSUB;
    }

    @Override
    public boolean isHealthy() {
      return running.get() && healthy.get() && !pool.isClosed();
    }

    @Override
    public long publishedCount() {
      return published.get();
    }

    @Override
    public long droppedCount() {
      return dropped.get();
    }

    @Override
    public void close() {
      if (!running.compareAndSet(true, false)) return;
      healthy.set(false);
      publishExecutor.shutdownNow();
      RespPubSub sub = this.subscriber;
      if (sub != null) {
        sub.close();
      }
      pool.close();
      Thread t = this.subscriberThread;
      if (t != null) {
        t.interrupt();
      }
    }
  }

  /**
   * Direct peer UDP gossip transport signed with HMAC-SHA256 and guarded by a sliding replay window
   * for sub-50ms server-to-server sleep coordination without a proxy hop ({@code ADR-001}).
   */
  final class DirectSocketTransport implements SleepSyncTransport {
    private final ServerAccessor accessor;
    private final String bindHost;
    private final int bindPort;
    private final List<InetSocketAddress> peerAddresses;
    private final byte[] hmacSecret;
    private final PacerGuard pacer;
    private final ReplayWindowCache replayCache =
        new ReplayWindowCache(SleepSyncFrame.DEFAULT_REPLAY_WINDOW_MS);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private volatile DatagramSocket socket;
    private volatile Thread readerThread;

    public DirectSocketTransport(
        ServerAccessor accessor,
        String bindHost,
        int bindPort,
        List<String> peers,
        byte[] hmacSecret,
        int maxFramesPerTick) {
      this.accessor = Objects.requireNonNull(accessor, "accessor");
      this.bindHost = (bindHost == null || bindHost.isBlank()) ? "127.0.0.1" : bindHost;
      this.bindPort = bindPort;
      this.peerAddresses = parsePeers(peers);
      this.hmacSecret = hmacSecret != null ? hmacSecret.clone() : new byte[0];
      this.pacer = new PacerGuard(maxFramesPerTick);
    }

    private static List<InetSocketAddress> parsePeers(List<String> peers) {
      List<InetSocketAddress> out = new ArrayList<>();
      if (peers == null) return out;
      for (String raw : peers) {
        if (raw == null || raw.isBlank()) continue;
        String trimmed = raw.trim();
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && colon < trimmed.length() - 1) {
          try {
            String host = trimmed.substring(0, colon);
            int port = Integer.parseInt(trimmed.substring(colon + 1));
            out.add(new InetSocketAddress(host, port));
          } catch (NumberFormatException ignored) {
          }
        }
      }
      return List.copyOf(out);
    }

    @Override
    public void start(Consumer<SleepSyncFrame> receiver) {
      if (!running.compareAndSet(false, true)) return;
      try {
        DatagramSocket ds = new DatagramSocket(new InetSocketAddress(bindHost, bindPort));
        this.socket = ds;
        Thread t =
            new Thread(
                () -> readLoop(ds, receiver), "leafsleep-direct-gossip-" + ds.getLocalPort());
        t.setDaemon(true);
        t.start();
        this.readerThread = t;
      } catch (IOException ex) {
        running.set(false);
        accessor.log(
            Level.WARNING,
            "[LeafSleep] Failed to bind direct socket on "
                + bindHost
                + ":"
                + bindPort
                + ": "
                + ex.getMessage());
      }
    }

    /** Returns the bound local UDP port (useful when {@code bindPort == 0} in tests). */
    public int boundPort() {
      DatagramSocket ds = this.socket;
      return ds != null ? ds.getLocalPort() : bindPort;
    }

    private void readLoop(DatagramSocket ds, Consumer<SleepSyncFrame> receiver) {
      byte[] buf = new byte[2048];
      while (running.get() && !ds.isClosed()) {
        try {
          DatagramPacket pkt = new DatagramPacket(buf, buf.length);
          ds.receive(pkt);
          byte[] data = new byte[pkt.getLength()];
          System.arraycopy(pkt.getData(), pkt.getOffset(), data, 0, pkt.getLength());
          SleepSyncFrame frame = SleepSyncFrame.decodeSigned(data, hmacSecret);
          long fp = SleepSyncFrame.extractSignatureFingerprint(data);
          if (!replayCache.checkAndRecord(fp)) {
            dropped.incrementAndGet();
            continue;
          }
          if (receiver != null) {
            accessor
                .scheduler()
                .ifPresentOrElse(
                    s -> s.runSync(() -> receiver.accept(frame)),
                    () -> receiver.accept(frame));
          }
        } catch (IOException ex) {
          if (running.get() && !ds.isClosed()) {
            dropped.incrementAndGet();
          }
        }
      }
    }

    @Override
    public boolean publish(SleepSyncFrame frame) {
      DatagramSocket ds = this.socket;
      if (!running.get() || ds == null || ds.isClosed() || frame == null) {
        return false;
      }
      long start = System.nanoTime();
      if (!pacer.tryAcquire(frame)) {
        dropped.incrementAndGet();
        return false;
      }
      byte[] signed = frame.encodeSigned(hmacSecret);
      boolean sentAny = false;
      for (InetSocketAddress peer : peerAddresses) {
        try {
          DatagramPacket pkt = new DatagramPacket(signed, signed.length, peer);
          ds.send(pkt);
          sentAny = true;
        } catch (IOException ex) {
          dropped.incrementAndGet();
        }
      }
      pacer.recordSendDuration(System.nanoTime() - start);
      if (sentAny || peerAddresses.isEmpty()) {
        published.incrementAndGet();
        return true;
      }
      return false;
    }

    @Override
    public LeafSleepConfig.TransportType mode() {
      return LeafSleepConfig.TransportType.DIRECT_SOCKET;
    }

    @Override
    public boolean isHealthy() {
      DatagramSocket ds = this.socket;
      return running.get() && ds != null && !ds.isClosed();
    }

    @Override
    public long publishedCount() {
      return published.get();
    }

    @Override
    public long droppedCount() {
      return dropped.get();
    }

    @Override
    public void close() {
      running.set(false);
      DatagramSocket ds = this.socket;
      if (ds != null) {
        ds.close();
      }
    }
  }
}
