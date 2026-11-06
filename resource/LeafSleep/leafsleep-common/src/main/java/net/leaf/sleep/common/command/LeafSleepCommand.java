package net.leaf.sleep.common.command;

import io.github.dailystruggle.commandsapi.common.CommandParameter;
import io.github.dailystruggle.commandsapi.common.CommandsAPI;
import io.github.dailystruggle.commandsapi.common.CommandsAPICommand;
import io.github.dailystruggle.commandsapi.common.localCommands.TreeCommand;
import io.github.dailystruggle.server.api.BookMenuSpec;
import io.github.dailystruggle.server.api.CallerLocation;
import io.github.dailystruggle.server.api.PlayerSleepSnapshot;
import io.github.dailystruggle.server.api.ServerAccessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.leaf.sleep.common.LeafSleepBitSuite;
import net.leaf.sleep.common.LeafSleepConfig;
import net.leaf.sleep.common.QuorumPolicy;
import net.leaf.sleep.common.ShardSleepCoordinator;
import net.leaf.sleep.common.SleepDatabaseStore;
import net.leaf.sleep.common.SleepSyncTransport;

/**
 * Platform-neutral {@code /sleep} (alias {@code /ops}) command tree built on {@link CommandsAPI}
 * ({@code LeafSleep ADR-002}).
 *
 * <p>Implements {@code status}, {@code sleep}, {@code cancel}, {@code ignore}, {@code reload},
 * {@code test} ({@code [OPS bit]}), and {@code version} with configurable failure messages (S-007)
 * and warning audit logging (S-004).
 *
 * @governedBy ADR-001
 * @governedBy ADR-002
 * @trace S-004
 * @trace S-007
 */
public final class LeafSleepCommand implements TreeCommand {

  private final String name;
  private final ServerAccessor accessor;
  private final Supplier<LeafSleepConfig> configSupplier;
  private final Supplier<ShardSleepCoordinator> coordinatorSupplier;
  private final Supplier<SleepSyncTransport> transportSupplier;
  private final Supplier<SleepDatabaseStore> storeSupplier;
  private final Supplier<LeafSleepBitSuite> bitSuiteSupplier;
  private final Supplier<List<String>> reloadAction;
  private final StatusSub statusSub;
  private final Map<String, CommandParameter> parameterLookup = new ConcurrentHashMap<>();
  private final Map<String, CommandsAPICommand> commandLookup = new ConcurrentHashMap<>();

  public LeafSleepCommand(
      ServerAccessor accessor,
      Supplier<LeafSleepConfig> configSupplier,
      Supplier<ShardSleepCoordinator> coordinatorSupplier,
      Supplier<SleepSyncTransport> transportSupplier,
      Supplier<SleepDatabaseStore> storeSupplier,
      Supplier<LeafSleepBitSuite> bitSuiteSupplier,
      Supplier<List<String>> reloadAction) {
    this(
        "sleep",
        accessor,
        configSupplier,
        coordinatorSupplier,
        transportSupplier,
        storeSupplier,
        bitSuiteSupplier,
        reloadAction);
  }

  public LeafSleepCommand(
      String name,
      ServerAccessor accessor,
      Supplier<LeafSleepConfig> configSupplier,
      Supplier<ShardSleepCoordinator> coordinatorSupplier,
      Supplier<SleepSyncTransport> transportSupplier,
      Supplier<SleepDatabaseStore> storeSupplier,
      Supplier<LeafSleepBitSuite> bitSuiteSupplier,
      Supplier<List<String>> reloadAction) {
    this.name = (name == null || name.isBlank()) ? "sleep" : name;
    this.accessor = Objects.requireNonNull(accessor, "accessor");
    this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
    this.coordinatorSupplier = Objects.requireNonNull(coordinatorSupplier, "coordinatorSupplier");
    this.transportSupplier = Objects.requireNonNull(transportSupplier, "transportSupplier");
    this.storeSupplier = Objects.requireNonNull(storeSupplier, "storeSupplier");
    this.bitSuiteSupplier = Objects.requireNonNull(bitSuiteSupplier, "bitSuiteSupplier");
    this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
    this.statusSub = new StatusSub(this);

    addSubCommand(this.statusSub);
    addSubCommand(new SleepSub(this));
    addSubCommand(new CancelSub(this));
    addSubCommand(new IgnoreSub(this));
    addSubCommand(new MessageSub(this));
    addSubCommand(new TokenSub(this));
    addSubCommand(new ReloadSub(this));
    addSubCommand(new TestSub(this));
    addSubCommand(new VersionSub(this));
    addSubCommand(new HelpSub(this));
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String permission() {
    return "leafsleep.status";
  }

  @Override
  public String description() {
    return "Cross-server sharded sleep coordination command (/sleep, /ops, /playersleep).";
  }

  @Override
  public CommandsAPICommand parent() {
    return null;
  }

  @Override
  public long avgTime() {
    return 0L;
  }

  @Override
  public Map<String, CommandParameter> getParameterLookup() {
    return parameterLookup;
  }

  @Override
  public Map<String, CommandsAPICommand> getCommandLookup() {
    return commandLookup;
  }

  @Override
  public CompletableFuture<Boolean> onCommand(
      UUID callerId,
      Predicate<String> permissionCheckMethod,
      Consumer<String> messageMethod,
      String[] args,
      int i,
      Map<String, CommandParameter> tempParameters) {
    Predicate<String> effectivePerm = withLegacyPermissionAliases(permissionCheckMethod);
    String[] normalized = normalizePositionalArgs(args);
    return TreeCommand.super.onCommand(
        callerId, effectivePerm, messageMethod, normalized, i, tempParameters);
  }

  private static Predicate<String> withLegacyPermissionAliases(Predicate<String> base) {
    if (base == null) {
      return p -> false;
    }
    return perm -> {
      if (perm == null || perm.isBlank()) return true;
      if (base.test(perm)) return true;
      if (perm.startsWith("leafsleep.")) {
        String suffix = perm.substring("leafsleep.".length());
        if (base.test("oneplayersleep." + suffix)) {
          return true;
        }
        if ("status".equals(suffix) || "message".equals(suffix)) {
          return base.test("leafsleep.sleep") || base.test("oneplayersleep.sleep");
        }
        if ("reload".equals(suffix) || "test".equals(suffix)) {
          return base.test("leafsleep.admin") || base.test("oneplayersleep.admin");
        }
      }
      return false;
    };
  }

  private static String[] normalizePositionalArgs(String[] args) {
    if (args == null || args.length != 2) {
      return args != null ? args : new String[0];
    }
    String first = args[0] != null ? args[0].trim() : "";
    String second = args[1] != null ? args[1].trim() : "";
    if (second.isEmpty() || second.contains("=") || second.equalsIgnoreCase("help")) {
      return args;
    }
    if (first.equalsIgnoreCase("sleep")) {
      return new String[] {args[0], "message=" + second};
    }
    if (first.equalsIgnoreCase("message") || first.equalsIgnoreCase("token")) {
      return new String[] {args[0], "id=" + second};
    }
    return args;
  }

  @Override
  public void msgNoPermission(UUID callerId, String permission) {
    LeafSleepConfig cfg = configSupplier.get();
    String msg = cfg.messages().noPermission();
    accessor.log(Level.WARNING, "[LeafSleep] " + msg + " (" + permission + ")");
    sendReply(callerId, msg, true);
  }

  @Override
  public void msgBadParameter(UUID callerId, String parameterName, String parameterValue) {
    LeafSleepConfig cfg = configSupplier.get();
    String msg =
        cfg.messages()
            .badParameter()
            .replace("[param]", String.valueOf(parameterName))
            .replace("[value]", String.valueOf(parameterValue));
    accessor.log(Level.WARNING, "[LeafSleep] " + msg);
    sendReply(callerId, msg, true);
  }

  @Override
  public void msgInvalidCommand(UUID callerId, String argument) {
    LeafSleepConfig cfg = configSupplier.get();
    String msg = cfg.messages().invalidCommand().replace("[arg]", String.valueOf(argument));
    accessor.log(Level.WARNING, "[LeafSleep] " + msg);
    sendReply(callerId, msg, true);
  }

  @Override
  public boolean onCommand(
      UUID callerId, Map<String, List<String>> parameterValues, CommandsAPICommand nextCommand) {
    if (nextCommand == null) {
      return statusSub.onCommand(callerId, parameterValues, null);
    }
    return true;
  }

  private void sendReply(UUID callerId, String message, boolean error) {
    LeafSleepConfig cfg = configSupplier.get();
    accessor.sendMessage(callerId, cfg.messages().prefix() + message, error);
  }

  private String resolveCallerWorld(UUID callerId) {
    if (callerId != null && !callerId.equals(CommandsAPI.serverId)) {
      CallerLocation loc = accessor.resolveCallerLocation(callerId);
      if (loc != null && loc.worldName() != null && !loc.worldName().isBlank()) {
        return loc.worldName();
      }
      for (String w : accessor.getWorldNames()) {
        for (PlayerSleepSnapshot snap : accessor.getWorldPlayers(w)) {
          if (callerId.equals(snap.playerId())) {
            return w;
          }
        }
      }
    }
    List<String> worlds = accessor.getWorldNames();
    return worlds.isEmpty() ? "world" : worlds.get(0);
  }

  private String resolveCallerName(UUID callerId, String worldName) {
    if (callerId == null || callerId.equals(CommandsAPI.serverId)) {
      return "Console";
    }
    for (PlayerSleepSnapshot snap : accessor.getWorldPlayers(worldName)) {
      if (callerId.equals(snap.playerId()) && !snap.playerName().isBlank()) {
        return snap.playerName();
      }
    }
    for (String w : accessor.getWorldNames()) {
      if (w.equals(worldName)) continue;
      for (PlayerSleepSnapshot snap : accessor.getWorldPlayers(w)) {
        if (callerId.equals(snap.playerId()) && !snap.playerName().isBlank()) {
          return snap.playerName();
        }
      }
    }
    return "Player";
  }

  private abstract class BaseSub implements TreeCommand {
    private final String subName;
    private final String perm;
    private final String desc;
    private final CommandsAPICommand parent;
    private final Map<String, CommandParameter> params = new ConcurrentHashMap<>();
    private final Map<String, CommandsAPICommand> subs = new ConcurrentHashMap<>();

    BaseSub(String subName, String perm, String desc, CommandsAPICommand parent) {
      this.subName = subName;
      this.perm = perm;
      this.desc = desc;
      this.parent = parent;
    }

    @Override
    public String name() {
      return subName;
    }

    @Override
    public String permission() {
      return perm;
    }

    @Override
    public String description() {
      return desc;
    }

    @Override
    public CommandsAPICommand parent() {
      return parent;
    }

    @Override
    public long avgTime() {
      return 0L;
    }

    @Override
    public Map<String, CommandParameter> getParameterLookup() {
      return params;
    }

    @Override
    public Map<String, CommandsAPICommand> getCommandLookup() {
      return subs;
    }

    @Override
    public void msgNoPermission(UUID callerId, String permission) {
      LeafSleepCommand.this.msgNoPermission(callerId, permission);
    }

    @Override
    public void msgBadParameter(UUID callerId, String parameterName, String parameterValue) {
      LeafSleepCommand.this.msgBadParameter(callerId, parameterName, parameterValue);
    }

    @Override
    public void msgInvalidCommand(UUID callerId, String argument) {
      LeafSleepCommand.this.msgInvalidCommand(callerId, argument);
    }
  }

  private final class StatusSub extends BaseSub {
    StatusSub(CommandsAPICommand parent) {
      super(
          "status",
          "leafsleep.status",
          "Display local and cross-shard sleep quorum status.",
          parent);
    }

    @Override
    public boolean onCommand(
        UUID callerId, Map<String, List<String>> parameterValues, CommandsAPICommand nextCommand) {
      LeafSleepConfig cfg = configSupplier.get();
      ShardSleepCoordinator coord = coordinatorSupplier.get();
      SleepSyncTransport transport = transportSupplier.get();
      SleepDatabaseStore store = storeSupplier.get();

      sendReply(callerId, cfg.messages().statusHeader(), false);
      Map<String, ShardSleepCoordinator.GroupQuorumStatus> statuses = coord.allGroupStatuses();
      int maxShards = 1;
      for (ShardSleepCoordinator.GroupQuorumStatus st : statuses.values()) {
        maxShards = Math.max(maxShards, st.activeShardCount());
        long worldTime = accessor.getWorldTime(st.primaryWorldName());
        LeafSleepConfig.WorldSleepConfig wCfg = cfg.resolveWorldConfig(st.primaryWorldName());
        String policyLabel =
            st.policy() == QuorumPolicy.CURVE
                ? st.policy().displayName() + ":" + wCfg.sleepCurve().type().displayName()
                : st.policy().displayName();
        String line =
            cfg.messages()
                .statusWorldLine()
                .replace("[world]", st.primaryWorldName())
                .replace("[shardGroup]", st.shardGroupId())
                .replace("[policy]", policyLabel)
                .replace("[cancelPolicy]", st.cancelPolicy().displayName())
                .replace("[requirements]", st.requirementsSummary())
                .replace("[sleeping]", Integer.toString(st.sleepingCount()))
                .replace("[required]", Integer.toString(st.requiredCount()))
                .replace("[cancelling]", Integer.toString(st.cancellingCount()))
                .replace("[cancelRequired]", Integer.toString(st.cancelRequiredCount()))
                .replace("[needToSkip]", Integer.toString(st.needToSkip()))
                .replace("[needToCancel]", Integer.toString(st.needToCancel()))
                .replace(
                    "[sleepingPlayers]",
                    ShardSleepCoordinator.formatPlayerSet(st.sleepingPlayers()))
                .replace(
                    "[cancellingPlayers]",
                    ShardSleepCoordinator.formatPlayerSet(st.cancellingPlayers()))
                .replace("[eligible]", Integer.toString(st.eligibleCount()))
                .replace("[maxPlayers]", Integer.toString(st.maxPlayers()))
                .replace("[ignored]", Integer.toString(st.ignoredCount()))
                .replace("[time]", Long.toString(worldTime))
                .replace("[epoch]", Long.toString(st.lastCommittedEpoch()));
        sendReply(callerId, line, false);
      }
      String perfSuffix =
          String.format(
              Locale.ROOT,
              " | TPS=%.2f MSPT=%.2fms (leafsleepAvg=%.3fms max=%.3fms)",
              accessor.getTPS(20),
              accessor.getMSPT(),
              coord.avgTickDurationMs(),
              coord.maxTickDurationMs());
      String healthLabel =
          (transport.isHealthy() && store.isHealthy()) ? "HEALTHY" : "DEGRADED";
      String transportLine =
          cfg.messages()
                  .statusTransportLine()
                  .replace("[transport]", transport.mode().name())
                  .replace("[shards]", Integer.toString(maxShards))
                  .replace("[storage]", store.storageType().name() + "(" + healthLabel + ")")
              + perfSuffix;
      sendReply(callerId, transportLine, false);
      coord.sendInteractiveStatusActionsIfApplicable(callerId, resolveCallerWorld(callerId));
      return true;
    }
  }

  private final class SleepSub extends BaseSub {
    SleepSub(CommandsAPICommand parent) {
      super("sleep", "leafsleep.sleep", "Vote for or initiate a night skip.", parent);
      addParameter(
          "world",
          new CommandParameter("leafsleep.sleep", "Target world name", (u, v) -> true) {
            @Override
            public Set<String> values() {
              return new HashSet<>(accessor.getWorldNames());
            }
          });
      addParameter(
          "message",
          new CommandParameter("leafsleep.sleep", "Preferred sleep message ID", (u, v) -> true) {
            @Override
            public Set<String> values() {
              return configSupplier.get().sleepMessages().stream()
                  .map(LeafSleepConfig.SleepMessageEntry::id)
                  .collect(Collectors.toSet());
            }
          });
    }

    @Override
    public boolean onCommand(
        UUID callerId, Map<String, List<String>> parameterValues, CommandsAPICommand nextCommand) {
      List<String> worldArg = parameterValues != null ? parameterValues.get("world") : null;
      String world =
          (worldArg != null && !worldArg.isEmpty() && !worldArg.get(0).isBlank())
              ? worldArg.get(0)
              : resolveCallerWorld(callerId);
      List<String> msgArg = parameterValues != null ? parameterValues.get("message") : null;
      String preferredMsg =
          (msgArg != null && !msgArg.isEmpty() && !msgArg.get(0).isBlank()) ? msgArg.get(0) : null;
      String playerName = resolveCallerName(callerId, world);
      coordinatorSupplier.get().voteSleep(callerId, playerName, world, preferredMsg);
      return true;
    }
  }

  private final class CancelSub extends BaseSub {
    CancelSub(CommandsAPICommand parent) {
      super(
          "cancel",
          "leafsleep.cancel",
          "Cancel an active sleep warmup across the shard group.",
          parent);
      addParameter(
          "world",
          new CommandParameter("leafsleep.cancel", "Target world name", (u, v) -> true) {
            @Override
            public Set<String> values() {
              return new HashSet<>(accessor.getWorldNames());
            }
          });
    }

    @Override
    public boolean onCommand(
        UUID callerId, Map<String, List<String>> parameterValues, CommandsAPICommand nextCommand) {
      List<String> worldArg = parameterValues != null ? parameterValues.get("world") : null;
      String world =
          (worldArg != null && !worldArg.isEmpty() && !worldArg.get(0).isBlank())
              ? worldArg.get(0)
              : resolveCallerWorld(callerId);
      String playerName = resolveCallerName(callerId, world);
      coordinatorSupplier.get().cancelSleep(callerId, playerName, world);
      return true;
    }
  }

  private final class IgnoreSub extends BaseSub {
    IgnoreSub(CommandsAPICommand parent) {
      super("ignore", "leafsleep.ignore", "Toggle persistent sleep-ignore status.", parent);
    }

    @Override
    public boolean onCommand(
        UUID callerId, Map<String, List<String>> parameterValues, CommandsAPICommand nextCommand) {
      coordinatorSupplier.get().toggleIgnore(callerId);
      return true;
    }
  }

  private final class MessageSub extends BaseSub {
    MessageSub(CommandsAPICommand parent) {
      super(
          "message",
          "leafsleep.sleep",
          "View or select your personal sleep and wakeup message theme.",
          parent);
      addParameter(
          "id",
          new CommandParameter(
              "leafsleep.sleep",
              "Sleep message theme ID (or 'random')",
              (u, v) -> v != null && !v.isBlank()) {
            @Override
            public Set<String> values() {
              Set<String> ids = new LinkedHashSet<>();
              ids.add("random");
              for (LeafSleepConfig.SleepMessageEntry entry : configSupplier.get().sleepMessages()) {
                ids.add(entry.id());
              }
              return ids;
            }
          });
    }

    @Override
    public boolean onCommand(
        UUID callerId, Map<String, List<String>> parameterValues, CommandsAPICommand nextCommand) {
      LeafSleepConfig cfg = configSupplier.get();
      ShardSleepCoordinator coord = coordinatorSupplier.get();
      List<String> idArg = parameterValues != null ? parameterValues.get("id") : null;
      if (idArg == null || idArg.isEmpty() || idArg.get(0).isBlank()) {
        String current = coord.getWakeupMessagePreference(callerId);
        List<String> availableIds = new ArrayList<>();
        availableIds.add("random");
        for (LeafSleepConfig.SleepMessageEntry entry : cfg.sleepMessages()) {
          availableIds.add(entry.id());
        }
        String msg =
            cfg.messages()
                .messagePrefCurrent()
                .replace("[message]", current)
                .replace("[available]", String.join(", ", availableIds));
        sendReply(callerId, msg, false);
        if (callerId != null && !callerId.equals(CommandsAPI.serverId)) {
          List<BookMenuSpec.Fragment> chips = new ArrayList<>();
          chips.add(
              new BookMenuSpec.Fragment(
                  cfg.messages().prefix(), "", BookMenuSpec.ClickAction.NONE, ""));
          for (int i = 0; i < availableIds.size(); i++) {
            String themeId = availableIds.get(i);
            if (i > 0) {
              chips.add(new BookMenuSpec.Fragment(" ", "", BookMenuSpec.ClickAction.NONE, ""));
            }
            boolean selected = themeId.equalsIgnoreCase(current);
            String label = selected ? "&a[" + themeId + "*]" : "&e[" + themeId + "]";
            chips.add(
                new BookMenuSpec.Fragment(
                    label,
                    "&fClick to select sleep message theme &e" + themeId,
                    BookMenuSpec.ClickAction.RUN_COMMAND,
                    "/" + LeafSleepCommand.this.name() + " message id=" + themeId));
          }
          accessor.sendInteractiveLine(callerId, new BookMenuSpec.Line(chips));
        }
        return true;
      }
      String requested = idArg.get(0).trim();
      if (!coord.setWakeupMessagePreference(callerId, requested)) {
        msgBadParameter(callerId, "id", requested);
      }
      return true;
    }
  }

  private final class TokenSub extends BaseSub {
    TokenSub(CommandsAPICommand parent) {
      super("token", "leafsleep.status", "Redeem a one-time sleep or cancel action token.", parent);
      addParameter(
          "id",
          new CommandParameter(
              "leafsleep.status",
              "One-time token identifier",
              (u, v) -> v != null && !v.isBlank()) {
            @Override
            public Set<String> values() {
              return Set.of();
            }
          });
    }

    @Override
    public boolean onCommand(
        UUID callerId, Map<String, List<String>> parameterValues, CommandsAPICommand nextCommand) {
      List<String> tokenArg = parameterValues != null ? parameterValues.get("id") : null;
      if (tokenArg == null || tokenArg.isEmpty() || tokenArg.get(0).isBlank()) {
        msgBadParameter(callerId, "id", "");
        return true;
      }
      String world = resolveCallerWorld(callerId);
      String playerName = resolveCallerName(callerId, world);
      if (!coordinatorSupplier.get().redeemActionToken(callerId, playerName, world, tokenArg.get(0))) {
        msgBadParameter(callerId, "id", tokenArg.get(0));
      }
      return true;
    }
  }

  private final class ReloadSub extends BaseSub {
    ReloadSub(CommandsAPICommand parent) {
      super(
          "reload",
          "leafsleep.reload",
          "Validate YAML configuration and hot-reload LeafSleep.",
          parent);
    }

    @Override
    public boolean onCommand(
        UUID callerId, Map<String, List<String>> parameterValues, CommandsAPICommand nextCommand) {
      LeafSleepConfig cfg = configSupplier.get();
      List<String> errors = reloadAction.get();
      if (errors == null || errors.isEmpty()) {
        sendReply(callerId, configSupplier.get().messages().reloadSuccess(), false);
      } else {
        sendReply(
            callerId,
            cfg.messages().reloadFailed().replace("[errors]", String.join("; ", errors)),
            true);
      }
      return true;
    }
  }

  private final class TestSub extends BaseSub {
    TestSub(CommandsAPICommand parent) {
      super("test", "leafsleep.test", "Run [OPS bit] PBIT/CBIT/IBIT self-tests.", parent);
    }

    @Override
    public boolean onCommand(
        UUID callerId, Map<String, List<String>> parameterValues, CommandsAPICommand nextCommand) {
      accessor
          .scheduler()
          .ifPresentOrElse(
              s ->
                  s.runAsync(
                      () -> {
                        List<String> lines = bitSuiteSupplier.get().executeAllAndFormat();
                        s.runSync(
                            () -> {
                              for (String line : lines) {
                                accessor.sendMessage(callerId, line, false);
                              }
                            });
                      }),
              () -> {
                List<String> lines = bitSuiteSupplier.get().executeAllAndFormat();
                for (String line : lines) {
                  accessor.sendMessage(callerId, line, false);
                }
              });
      return true;
    }
  }

  private final class VersionSub extends BaseSub {
    VersionSub(CommandsAPICommand parent) {
      super("version", "leafsleep.status", "Display LeafSleep / OnePlayerSleep version.", parent);
    }

    @Override
    public boolean onCommand(
        UUID callerId, Map<String, List<String>> parameterValues, CommandsAPICommand nextCommand) {
      sendReply(
          callerId,
          "LeafSleep v1.0.0 (OnePlayerSleep v4.0.0 sharded sequel) on "
              + accessor.getPlatform()
              + " "
              + accessor.getServerVersion()
              + " (shard="
              + configSupplier.get().serverShardId()
              + ")",
          false);
      return true;
    }
  }

  private final class HelpSub extends BaseSub {
    HelpSub(CommandsAPICommand parent) {
      super("help", "leafsleep.status", "List available /sleep subcommands.", parent);
    }

    @Override
    public boolean onCommand(
        UUID callerId, Map<String, List<String>> parameterValues, CommandsAPICommand nextCommand) {
      sendReply(
          callerId,
          "Subcommands: /"
              + LeafSleepCommand.this.name()
              + " <status|sleep|cancel|ignore|message|reload|test|version|help>",
          false);
      if (callerId != null && !callerId.equals(CommandsAPI.serverId)) {
        LeafSleepConfig cfg = configSupplier.get();
        String root = "/" + LeafSleepCommand.this.name() + " ";
        List<BookMenuSpec.Fragment> chips =
            List.of(
                new BookMenuSpec.Fragment(
                    cfg.messages().prefix(), "", BookMenuSpec.ClickAction.NONE, ""),
                new BookMenuSpec.Fragment(
                    "&b[Status]",
                    "&fView current sleep quorum status",
                    BookMenuSpec.ClickAction.RUN_COMMAND,
                    root + "status"),
                new BookMenuSpec.Fragment(" ", "", BookMenuSpec.ClickAction.NONE, ""),
                new BookMenuSpec.Fragment(
                    "&a[Sleep]",
                    "&fVote to skip the night",
                    BookMenuSpec.ClickAction.RUN_COMMAND,
                    root + "sleep"),
                new BookMenuSpec.Fragment(" ", "", BookMenuSpec.ClickAction.NONE, ""),
                new BookMenuSpec.Fragment(
                    "&c[Cancel]",
                    "&fVote to cancel an active night skip",
                    BookMenuSpec.ClickAction.RUN_COMMAND,
                    root + "cancel"),
                new BookMenuSpec.Fragment(" ", "", BookMenuSpec.ClickAction.NONE, ""),
                new BookMenuSpec.Fragment(
                    "&e[Message]",
                    "&fChoose your personal wakeup message theme",
                    BookMenuSpec.ClickAction.RUN_COMMAND,
                    root + "message"),
                new BookMenuSpec.Fragment(" ", "", BookMenuSpec.ClickAction.NONE, ""),
                new BookMenuSpec.Fragment(
                    "&7[Ignore]",
                    "&fToggle sleep-ignore opt-out",
                    BookMenuSpec.ClickAction.RUN_COMMAND,
                    root + "ignore"));
        accessor.sendInteractiveLine(callerId, new BookMenuSpec.Line(chips));
      }
      return true;
    }
  }
}
