package net.leaf.sleep.common;

import io.github.dailystruggle.rtp.common.predict.UnitCurve;
import java.util.Locale;

/**
 * Sleep and cancel quorum evaluation policies and {@link UnitCurve}-backed network scaling curves
 * across a logical shard group ({@code LeafSleep ADR-001}).
 *
 * @governedBy ADR-001
 * @governedBy ADR-002
 * @governedBy ADR-087
 */
public enum QuorumPolicy {
  /** Any single eligible sleeping player satisfies quorum across the shard group. */
  ONE_PLAYER("one player"),
  /** At least {@code ceil(eligible * (quorumValue / 100.0))} sleeping players required. */
  PERCENTAGE("percentage"),
  /** At least {@code min(eligible, max(1, quorumValue))} sleeping players required. */
  FIXED_COUNT("fixed count"),
  /** Strictly more than 50% ({@code (eligible / 2) + 1}) of eligible players required. */
  ABSOLUTE_MAJORITY("absolute majority"),
  /**
   * Scales required sleepers smoothly from {@code minRequired} to {@code maxRequired} based on
   * {@code eligibleCount / serverMaxPlayers} using {@link UnitCurve}.
   */
  CURVE("curve");

  /** Shaping curve function backed by {@link UnitCurve} ({@code :predict-api}). */
  public enum CurveType {
    LOGARITHMIC("logarithmic"),
    POWER("power"),
    SMOOTHERSTEP("smootherstep"),
    SIGMOID("sigmoid"),
    LINEAR("linear");

    private final String displayName;

    CurveType(String displayName) {
      this.displayName = displayName;
    }

    public String displayName() {
      return displayName;
    }

    public static CurveType fromString(String raw) {
      if (raw == null || raw.isBlank()) {
        return LOGARITHMIC;
      }
      String norm = normalizeToken(raw);
      return switch (norm) {
        case "LOGARITHMIC", "LOG" -> LOGARITHMIC;
        case "POWER", "POW", "SQRT" -> POWER;
        case "SMOOTHERSTEP", "SMOOTHSTEP", "SMOOTH" -> SMOOTHERSTEP;
        case "SIGMOID", "LOGISTIC", "SCURVE" -> SIGMOID;
        case "LINEAR", "LIN" -> LINEAR;
        default -> LOGARITHMIC;
      };
    }

    /** Evaluates this curve at normalized population load {@code t in [0.0, 1.0]}. */
    public double evaluate(double t, double parameter, double midpoint) {
      double u = UnitCurve.clamp01(t);
      if (u <= 0.0) return 0.0;
      if (u >= 1.0) return 1.0;
      return switch (this) {
        case LOGARITHMIC -> UnitCurve.logarithmic(u, parameter <= 1.0 ? 10.0 : parameter);
        case POWER -> UnitCurve.power(u, parameter <= 0.0 ? 0.5 : parameter);
        case SMOOTHERSTEP -> UnitCurve.smootherstep(u);
        case SIGMOID -> {
          double steep = parameter <= 0.0 ? 10.0 : parameter;
          double mid = UnitCurve.clamp01(midpoint);
          double s0 = UnitCurve.sigmoid(0.0, mid, steep);
          double s1 = UnitCurve.sigmoid(1.0, mid, steep);
          double su = UnitCurve.sigmoid(u, mid, steep);
          double span = s1 - s0;
          yield span <= 1e-9 ? u : UnitCurve.clamp01((su - s0) / span);
        }
        case LINEAR -> u;
      };
    }
  }

  /**
   * Policy governing how {@code /sleep cancel} clicks interact with an active sleep proposal.
   */
  public enum CancelPolicy {
    /** Classic 1-click veto: a single cancel click aborts the sleep countdown immediately. */
    VETO("veto"),
    /**
     * Cancel threshold scales along {@code cancelCurve} based on {@code eligible / maxPlayers}.
     */
    CURVE("curve"),
    /**
     * Combines both sleep and cancel curves: each cancel vote offsets a sleep vote (requiring 1
     * additional sleeper to pass) and reaching {@code cancelCurve} aborts the countdown.
     */
    TUG_OF_WAR("tug of war");

    private final String displayName;

    CancelPolicy(String displayName) {
      this.displayName = displayName;
    }

    public String displayName() {
      return displayName;
    }

    public static CancelPolicy fromString(String raw) {
      if (raw == null || raw.isBlank()) {
        return TUG_OF_WAR;
      }
      String norm = normalizeToken(raw);
      return switch (norm) {
        case "VETO", "ONE_PLAYER", "SINGLE", "CLASSIC" -> VETO;
        case "CURVE", "SCALED" -> CURVE;
        case "TUG_OF_WAR", "BOTH", "NET", "OFFSET" -> TUG_OF_WAR;
        default -> TUG_OF_WAR;
      };
    }
  }

  /**
   * Parameters for a {@link UnitCurve}-backed player count curve over {@code eligible /
   * serverMaxPlayers}.
   */
  public record CurveConfig(
      CurveType type, int minRequired, int maxRequired, double parameter, double midpoint) {

    public static final CurveConfig DEFAULT_SLEEP =
        new CurveConfig(CurveType.LOGARITHMIC, 1, 8, 10.0, 0.35);

    public static final CurveConfig DEFAULT_CANCEL =
        new CurveConfig(CurveType.LOGARITHMIC, 1, 5, 10.0, 0.35);

    public CurveConfig {
      type = type == null ? CurveType.LOGARITHMIC : type;
      minRequired = Math.max(1, minRequired);
      maxRequired = Math.max(minRequired, maxRequired);
      double defaultParam = type == CurveType.POWER ? 0.5 : 10.0;
      parameter = (!Double.isFinite(parameter) || parameter <= 0.0) ? defaultParam : parameter;
      midpoint = !Double.isFinite(midpoint) ? 0.35 : UnitCurve.clamp01(midpoint);
    }

    /**
     * Computes the required player count for {@code eligibleCount} against {@code
     * serverMaxPlayers}, guaranteed to be monotonically non-decreasing and bounded in {@code [1,
     * eligibleCount]}.
     */
    public int evaluateRequired(int eligibleCount, int serverMaxPlayers) {
      if (eligibleCount <= 0) {
        return 0;
      }
      if (eligibleCount == 1) {
        return 1;
      }
      int effectiveMax = Math.max(eligibleCount, Math.max(2, serverMaxPlayers));
      double t =
          UnitCurve.clamp01((double) (eligibleCount - 1) / (double) (effectiveMax - 1));
      double shaped = type.evaluate(t, parameter, midpoint);
      double interpolated = UnitCurve.linear(shaped, minRequired, maxRequired);
      int req = (int) Math.round(interpolated);
      if (minRequired == 1 && eligibleCount < effectiveMax) {
        // Never demand > 50% of online players at small group sizes when minRequired is 1
        req = Math.min(req, Math.max(1, eligibleCount / 2));
      }
      return Math.min(eligibleCount, Math.max(minRequired, req));
    }
  }

  private final String displayName;

  QuorumPolicy(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }

  private static String normalizeToken(String raw) {
    return raw.trim().replace('-', '_').replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
  }

  /**
   * Parses a policy name case-insensitively (accepting spaced or underscored tokens), falling back
   * to {@link #ONE_PLAYER}.
   *
   * @param raw policy string
   * @return parsed quorum policy; never {@code null}
   */
  public static QuorumPolicy fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      return ONE_PLAYER;
    }
    String norm = normalizeToken(raw);
    return switch (norm) {
      case "ONE_PLAYER", "ONE", "SINGLE" -> ONE_PLAYER;
      case "PERCENTAGE", "PERCENT" -> PERCENTAGE;
      case "FIXED_COUNT", "FIXED" -> FIXED_COUNT;
      case "ABSOLUTE_MAJORITY", "MAJORITY" -> ABSOLUTE_MAJORITY;
      case "CURVE", "BOTH" -> CURVE;
      default -> ONE_PLAYER;
    };
  }

  /**
   * Computes the base number of sleeping players required to meet quorum for {@code eligibleCount}.
   */
  public int requiredSleepers(int eligibleCount, int quorumValue) {
    return requiredSleepers(eligibleCount, quorumValue, 20, CurveConfig.DEFAULT_SLEEP);
  }

  /**
   * Computes the base number of sleeping players required to meet quorum for {@code eligibleCount}
   * given {@code serverMaxPlayers} and {@code sleepCurve}.
   */
  public int requiredSleepers(
      int eligibleCount, int quorumValue, int serverMaxPlayers, CurveConfig sleepCurve) {
    if (eligibleCount <= 0) {
      return 0;
    }
    return switch (this) {
      case ONE_PLAYER -> 1;
      case PERCENTAGE -> {
        int clampedPct = Math.min(100, Math.max(1, quorumValue));
        yield Math.max(1, (int) Math.ceil(eligibleCount * (clampedPct / 100.0)));
      }
      case FIXED_COUNT -> Math.min(eligibleCount, Math.max(1, quorumValue));
      case ABSOLUTE_MAJORITY -> (eligibleCount / 2) + 1;
      case CURVE ->
          (sleepCurve != null ? sleepCurve : CurveConfig.DEFAULT_SLEEP)
              .evaluateRequired(eligibleCount, serverMaxPlayers);
    };
  }

  /**
   * Computes the effective number of sleeping players required to pass the night after accounting
   * for active {@code cancellingCount} under {@code cancelPolicy}.
   */
  public int effectiveRequiredSleepers(
      int eligibleCount,
      int quorumValue,
      int serverMaxPlayers,
      CurveConfig sleepCurve,
      CancelPolicy cancelPolicy,
      int cancellingCount) {
    int base = requiredSleepers(eligibleCount, quorumValue, serverMaxPlayers, sleepCurve);
    if (base <= 0) {
      return 0;
    }
    if (cancelPolicy == CancelPolicy.TUG_OF_WAR && cancellingCount > 0) {
      return Math.min(eligibleCount, base + cancellingCount);
    }
    return base;
  }

  /**
   * Computes the number of cancel clicks required to abort an active sleep proposal under {@code
   * cancelPolicy}.
   */
  public static int requiredCancels(
      int eligibleCount,
      int serverMaxPlayers,
      CancelPolicy cancelPolicy,
      CurveConfig cancelCurve) {
    if (eligibleCount <= 0) {
      return 1;
    }
    CancelPolicy pol = cancelPolicy != null ? cancelPolicy : CancelPolicy.TUG_OF_WAR;
    return switch (pol) {
      case VETO -> 1;
      case CURVE, TUG_OF_WAR ->
          (cancelCurve != null ? cancelCurve : CurveConfig.DEFAULT_CANCEL)
              .evaluateRequired(eligibleCount, serverMaxPlayers);
    };
  }

  /**
   * Formats a crystal-clear, ultra-short status string telling players exactly how many more
   * players are needed to pass the night or cancel.
   */
  public static String formatShortRequirements(int needToSkip, int needToCancel) {
    int safeCancel = Math.max(1, needToCancel);
    if (needToSkip <= 0) {
      return "Skipping! (" + safeCancel + " to cancel)";
    }
    return "Need " + needToSkip + " more to skip (" + safeCancel + " to cancel)";
  }

  /**
   * Evaluates whether {@code sleepingCount} satisfies quorum for {@code eligibleCount}.
   */
  public boolean isQuorumMet(int eligibleCount, int sleepingCount, int quorumValue) {
    if (eligibleCount <= 0 || sleepingCount <= 0) {
      return false;
    }
    return sleepingCount >= requiredSleepers(eligibleCount, quorumValue);
  }
}
