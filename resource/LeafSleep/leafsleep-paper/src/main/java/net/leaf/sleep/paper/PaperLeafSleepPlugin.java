package net.leaf.sleep.paper;

import io.github.dailystruggle.rtp.bukkitplatform.server.BukkitPluginServerAccessor;
import net.leaf.sleep.common.LeafSleepRuntime;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper / Spigot / Folia plugin entry point for {@code LeafSleep}, delegating platform services to
 * {@link BukkitPluginServerAccessor} and domain lifecycle to {@link LeafSleepRuntime} ({@code
 * LeafSleep ADR-002}).
 */
public final class PaperLeafSleepPlugin extends JavaPlugin {

  private BukkitPluginServerAccessor accessor;
  private LeafSleepRuntime runtime;

  @Override
  public void onLoad() {
    if (!getDataFolder().exists()) {
      getDataFolder().mkdirs();
    }
    this.accessor = new BukkitPluginServerAccessor(this, "leafsleep");
    this.runtime = new LeafSleepRuntime(this.accessor);
    this.runtime.load();
  }

  @Override
  public void onEnable() {
    if (this.accessor == null || this.runtime == null) {
      this.accessor = new BukkitPluginServerAccessor(this, "leafsleep");
      this.runtime = new LeafSleepRuntime(this.accessor);
    }
    this.accessor.registerEvents();
    this.runtime.start();
    getLogger().info("LeafSleep (Paper/Folia) enabled.");
  }

  @Override
  public void onDisable() {
    if (this.runtime != null) {
      this.runtime.close();
    }
  }
}
