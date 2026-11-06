package net.leaf.sleep.neoforge;

import io.github.dailystruggle.rtp.neoforge.v26_1_R1.server.NeoForgeModServerAccessor;
import java.nio.file.Path;
import net.leaf.sleep.common.LeafSleepRuntime;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * NeoForge mod entry point for {@code LeafSleep}, delegating platform services to {@link
 * NeoForgeModServerAccessor} and domain lifecycle to {@link LeafSleepRuntime} ({@code LeafSleep
 * ADR-002}).
 */
@Mod("leafsleep")
public final class NeoForgeSleepMod {

  private final LeafSleepRuntime runtime;

  public NeoForgeSleepMod() {
    NeoForgeModServerAccessor accessor =
        new NeoForgeModServerAccessor("LeafSleep", Path.of("config", "leafsleep"));
    accessor.registerEvents();
    this.runtime = new LeafSleepRuntime(accessor);
    this.runtime.start();
    NeoForge.EVENT_BUS.addListener(
        (ServerStoppingEvent event) -> {
          if (this.runtime != null) {
            this.runtime.close();
          }
        });
    accessor.logger().info("LeafSleep (NeoForge) initialized.");
  }
}
