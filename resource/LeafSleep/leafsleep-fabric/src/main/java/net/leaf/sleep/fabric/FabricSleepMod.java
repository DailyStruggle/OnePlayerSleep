package net.leaf.sleep.fabric;

import io.github.dailystruggle.rtp.fabric.unobf.server.FabricModServerAccessor;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.leaf.sleep.common.LeafSleepRuntime;

/**
 * Fabric mod entry point for {@code LeafSleep}, delegating platform services to {@link
 * FabricModServerAccessor} and domain lifecycle to {@link LeafSleepRuntime} ({@code LeafSleep
 * ADR-002}).
 */
public final class FabricSleepMod implements ModInitializer {

  private LeafSleepRuntime runtime;

  @Override
  public void onInitialize() {
    FabricModServerAccessor accessor =
        new FabricModServerAccessor("LeafSleep", Path.of("config", "leafsleep"));
    accessor.registerEvents();
    this.runtime = new LeafSleepRuntime(accessor);
    this.runtime.start();
    ServerLifecycleEvents.SERVER_STOPPING.register(
        server -> {
          if (this.runtime != null) {
            this.runtime.close();
          }
        });
    accessor.logger().info("LeafSleep (Fabric) initialized.");
  }
}
