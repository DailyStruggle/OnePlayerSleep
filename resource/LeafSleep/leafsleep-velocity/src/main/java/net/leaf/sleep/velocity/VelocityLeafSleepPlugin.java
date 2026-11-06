package net.leaf.sleep.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.leaf.sleep.common.SleepSyncFrame;
import org.slf4j.Logger;

/**
 * Velocity proxy plugin-message fanout relay for {@code LeafSleep} ({@code LeafSleep ADR-001},
 * {@code ADR-002}).
 *
 * <p>Intercepts {@code leafsleep:sync} frames from backend servers, validates the binary frame
 * header, records shard-to-server and shard-group membership dynamically, and fans the frame out to
 * eligible peer backend servers that have active player connections without echoing back to the
 * origin shard.
 *
 * @governedBy ADR-001
 * @governedBy ADR-002
 * @trace S-004
 */
@Plugin(
    id = "leafsleep",
    name = "LeafSleep",
    version = "1.0.0",
    description = "Cross-server sharded sleep relay for Velocity.",
    authors = {"leaf"})
public final class VelocityLeafSleepPlugin {

  public static final MinecraftChannelIdentifier SYNC_CHANNEL =
      MinecraftChannelIdentifier.create("leafsleep", "sync");

  private final ProxyServer proxyServer;
  private final Logger logger;
  private final Map<String, String> shardIdToServerName = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> serverToShardGroups = new ConcurrentHashMap<>();

  @Inject
  public VelocityLeafSleepPlugin(ProxyServer proxyServer, Logger logger) {
    this.proxyServer = proxyServer;
    this.logger = logger;
  }

  @Subscribe
  public void onProxyInitialize(ProxyInitializeEvent event) {
    proxyServer.getChannelRegistrar().register(SYNC_CHANNEL);
    logger.info("LeafSleep Velocity sync relay initialized on channel {}.", SYNC_CHANNEL.getId());
  }

  @Subscribe
  public void onPluginMessage(PluginMessageEvent event) {
    if (!SYNC_CHANNEL.equals(event.getIdentifier())) {
      return;
    }
    event.setResult(PluginMessageEvent.ForwardResult.handled());
    if (!(event.getSource() instanceof ServerConnection sourceServer)) {
      return;
    }
    byte[] data = event.getData();
    String originName = sourceServer.getServerInfo().getName();
    String envSecret = System.getenv("LEAFSLEEP_HMAC_SECRET");
    byte[] secretKey =
        (envSecret != null && !envSecret.isBlank())
            ? envSecret.getBytes(StandardCharsets.UTF_8)
            : null;
    SleepSyncFrame frame;
    try {
      frame =
          secretKey != null
              ? SleepSyncFrame.decodeSigned(data, secretKey)
              : SleepSyncFrame.decodeBytes(data);
    } catch (IOException ex) {
      logger.warn(
          "Dropped malformed LeafSleep sync frame from {}: {}", originName, ex.getMessage());
      return;
    }

    if (!frame.shardId().isBlank()) {
      shardIdToServerName.put(frame.shardId(), originName);
    }
    if (!frame.shardGroupId().isBlank()) {
      serverToShardGroups
          .computeIfAbsent(
              originName.toLowerCase(Locale.ROOT), k -> ConcurrentHashMap.newKeySet())
          .add(frame.shardGroupId());
    }

    String originShardServer = shardIdToServerName.get(frame.shardId());
    for (RegisteredServer target : proxyServer.getAllServers()) {
      String targetName = target.getServerInfo().getName();
      if (targetName.equalsIgnoreCase(originName)
          || (originShardServer != null && targetName.equalsIgnoreCase(originShardServer))) {
        continue;
      }
      if (target.getPlayersConnected().isEmpty()) {
        continue;
      }
      Set<String> knownGroups = serverToShardGroups.get(targetName.toLowerCase(Locale.ROOT));
      if (knownGroups != null
          && !knownGroups.isEmpty()
          && !frame.shardGroupId().isBlank()
          && !knownGroups.contains(frame.shardGroupId())) {
        continue;
      }
      target.sendPluginMessage(SYNC_CHANNEL, data);
    }
  }
}
