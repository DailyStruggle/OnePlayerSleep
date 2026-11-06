package net.leaf.sleep.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Compact binary delta frame for sub-50ms cross-shard sleep coordination over {@link
 * SleepSyncTransport} ({@code LeafSleep ADR-001}).
 *
 * <p>Supports raw binary serialization (for plugin messaging and Redis pub/sub) and HMAC-SHA256
 * authenticated envelopes with replay-window enforcement (for direct UDP/TCP peer gossip and
 * untrusted network buses).
 *
 * @governedBy ADR-001
 * @governedBy ADR-002
 * @trace S-004
 */
public record SleepSyncFrame(
    FrameType type,
    String shardId,
    String shardGroupId,
    String worldName,
    UUID playerId,
    String playerName,
    int eligibleCount,
    int sleepingCount,
    int cancellingCount,
    int ignoredCount,
    int maxPlayers,
    List<String> sleepingPlayers,
    List<String> cancellingPlayers,
    long targetDayEpoch,
    long targetDayTimeTicks,
    boolean clearWeather,
    long timestampMs) {

  private static final int MAGIC = 0x4C534C50; // "LSLP"
  private static final byte VERSION = 2;
  private static final int HMAC_BYTES = 32;

  /** Default maximum clock skew / replay window (30 seconds) for signed frames. */
  public static final long DEFAULT_REPLAY_WINDOW_MS = 30_000L;

  /** Delta frame types per {@code ADR-001}. */
  public enum FrameType {
    BED_ENTER,
    BED_LEAVE,
    SLEEP_VOTE,
    CANCEL_SLEEP,
    EPOCH_COMMIT,
    SHARD_HEARTBEAT
  }

  public SleepSyncFrame {
    Objects.requireNonNull(type, "type");
    shardId = shardId == null ? "" : shardId;
    shardGroupId = shardGroupId == null ? "" : shardGroupId;
    worldName = worldName == null ? "" : worldName;
    playerName = playerName == null ? "" : playerName;
    maxPlayers = Math.max(1, maxPlayers);
    sleepingPlayers = sleepingPlayers == null ? List.of() : List.copyOf(sleepingPlayers);
    cancellingPlayers = cancellingPlayers == null ? List.of() : List.copyOf(cancellingPlayers);
  }

  /** Convenience constructor with default cancel/player-list metadata. */
  public SleepSyncFrame(
      FrameType type,
      String shardId,
      String shardGroupId,
      String worldName,
      UUID playerId,
      String playerName,
      int eligibleCount,
      int sleepingCount,
      int ignoredCount,
      long targetDayEpoch,
      long targetDayTimeTicks,
      boolean clearWeather,
      long timestampMs) {
    this(
        type,
        shardId,
        shardGroupId,
        worldName,
        playerId,
        playerName,
        eligibleCount,
        sleepingCount,
        0,
        ignoredCount,
        20,
        List.of(),
        List.of(),
        targetDayEpoch,
        targetDayTimeTicks,
        clearWeather,
        timestampMs);
  }

  /** Serializes this frame into a compact binary payload. */
  public byte[] encodeBytes() {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(160);
      DataOutputStream out = new DataOutputStream(baos);
      out.writeInt(MAGIC);
      out.writeByte(VERSION);
      out.writeByte(type.ordinal());
      out.writeUTF(shardId);
      out.writeUTF(shardGroupId);
      out.writeUTF(worldName);
      if (playerId != null) {
        out.writeBoolean(true);
        out.writeLong(playerId.getMostSignificantBits());
        out.writeLong(playerId.getLeastSignificantBits());
      } else {
        out.writeBoolean(false);
      }
      out.writeUTF(playerName);
      out.writeInt(eligibleCount);
      out.writeInt(sleepingCount);
      out.writeInt(cancellingCount);
      out.writeInt(ignoredCount);
      out.writeInt(maxPlayers);
      writeStringList(out, sleepingPlayers);
      writeStringList(out, cancellingPlayers);
      out.writeLong(targetDayEpoch);
      out.writeLong(targetDayTimeTicks);
      out.writeBoolean(clearWeather);
      out.writeLong(timestampMs);
      out.flush();
      return baos.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to encode SleepSyncFrame", ex);
    }
  }

  private static void writeStringList(DataOutputStream out, List<String> list) throws IOException {
    int count = Math.min(32, list.size());
    out.writeShort(count);
    for (int i = 0; i < count; i++) {
      out.writeUTF(list.get(i) == null ? "" : list.get(i));
    }
  }

  private static List<String> readStringList(DataInputStream in) throws IOException {
    int count = in.readUnsignedShort();
    if (count <= 0) return List.of();
    List<String> out = new ArrayList<>(Math.min(count, 32));
    for (int i = 0; i < count; i++) {
      out.add(in.readUTF());
    }
    return out;
  }

  /** Deserializes a {@link SleepSyncFrame} from raw binary bytes. */
  public static SleepSyncFrame decodeBytes(byte[] bytes) throws IOException {
    if (bytes == null || bytes.length < 6) {
      throw new IOException("Frame too short");
    }
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
    int magic = in.readInt();
    if (magic != MAGIC) {
      throw new IOException("Invalid SleepSyncFrame magic: 0x" + Integer.toHexString(magic));
    }
    byte version = in.readByte();
    if (version != 1 && version != VERSION) {
      throw new IOException("Unsupported SleepSyncFrame version: " + version);
    }
    int ord = in.readUnsignedByte();
    FrameType[] types = FrameType.values();
    if (ord < 0 || ord >= types.length) {
      throw new IOException("Invalid FrameType ordinal: " + ord);
    }
    FrameType type = types[ord];
    String shardId = in.readUTF();
    String shardGroupId = in.readUTF();
    String worldName = in.readUTF();
    UUID playerId = null;
    if (in.readBoolean()) {
      playerId = new UUID(in.readLong(), in.readLong());
    }
    String playerName = in.readUTF();
    int eligible = in.readInt();
    int sleeping = in.readInt();
    int cancelling = version >= 2 ? in.readInt() : 0;
    int ignored = in.readInt();
    int maxPlayers = version >= 2 ? in.readInt() : 20;
    List<String> sleepingNames = version >= 2 ? readStringList(in) : List.of();
    List<String> cancellingNames = version >= 2 ? readStringList(in) : List.of();
    long epoch = in.readLong();
    long dayTime = in.readLong();
    boolean clearWeather = in.readBoolean();
    long ts = in.readLong();
    return new SleepSyncFrame(
        type,
        shardId,
        shardGroupId,
        worldName,
        playerId,
        playerName,
        eligible,
        sleeping,
        cancelling,
        ignored,
        maxPlayers,
        sleepingNames,
        cancellingNames,
        epoch,
        dayTime,
        clearWeather,
        ts);
  }

  /** Encodes this frame with an appended 32-byte HMAC-SHA256 signature. */
  public byte[] encodeSigned(byte[] secretKey) {
    if (secretKey == null || secretKey.length == 0) {
      throw new IllegalArgumentException("HMAC secret key must not be empty");
    }
    byte[] payload = encodeBytes();
    byte[] mac = computeHmac(payload, 0, payload.length, secretKey);
    byte[] signed = new byte[payload.length + HMAC_BYTES];
    System.arraycopy(payload, 0, signed, 0, payload.length);
    System.arraycopy(mac, 0, signed, payload.length, HMAC_BYTES);
    return signed;
  }

  /**
   * Verifies the trailing HMAC-SHA256 signature and replay window ({@link
   * #DEFAULT_REPLAY_WINDOW_MS}), then decodes the frame.
   */
  public static SleepSyncFrame decodeSigned(byte[] signedBytes, byte[] secretKey)
      throws IOException {
    return decodeSigned(signedBytes, secretKey, DEFAULT_REPLAY_WINDOW_MS);
  }

  /**
   * Verifies the trailing HMAC-SHA256 signature and enforces a maximum timestamp skew {@code
   * maxSkewMs} (when {@code maxSkewMs > 0}), then decodes the frame.
   */
  public static SleepSyncFrame decodeSigned(
      byte[] signedBytes, byte[] secretKey, long maxSkewMs) throws IOException {
    if (secretKey == null || secretKey.length == 0) {
      throw new IOException("HMAC secret key is required for signed SleepSyncFrame");
    }
    if (signedBytes == null || signedBytes.length <= HMAC_BYTES + 6) {
      throw new IOException("Signed frame too short");
    }
    int payloadLen = signedBytes.length - HMAC_BYTES;
    byte[] expectedMac = computeHmac(signedBytes, 0, payloadLen, secretKey);
    byte[] actualMac = new byte[HMAC_BYTES];
    System.arraycopy(signedBytes, payloadLen, actualMac, 0, HMAC_BYTES);
    if (!MessageDigest.isEqual(expectedMac, actualMac)) {
      throw new IOException("HMAC-SHA256 signature verification failed for SleepSyncFrame");
    }
    byte[] payload = new byte[payloadLen];
    System.arraycopy(signedBytes, 0, payload, 0, payloadLen);
    SleepSyncFrame decoded = decodeBytes(payload);
    if (maxSkewMs > 0L && decoded.timestampMs() > 0L) {
      long skew = Math.abs(System.currentTimeMillis() - decoded.timestampMs());
      if (skew > maxSkewMs) {
        throw new IOException("SleepSyncFrame timestamp outside replay window (skew=" + skew + "ms)");
      }
    }
    return decoded;
  }

  /** Extracts a 64-bit fingerprint from the trailing HMAC signature for replay deduplication. */
  public static long extractSignatureFingerprint(byte[] signedBytes) {
    if (signedBytes == null || signedBytes.length < 8) {
      return 0L;
    }
    return ByteBuffer.wrap(signedBytes, signedBytes.length - 8, 8).getLong();
  }

  private static byte[] computeHmac(byte[] data, int offset, int length, byte[] secretKey) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
      mac.update(data, offset, length);
      return mac.doFinal();
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("HmacSHA256 unavailable", ex);
    }
  }
}
