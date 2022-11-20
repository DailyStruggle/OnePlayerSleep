package OnePlayerSleep.Listeners.spigotEventListeners;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.tools.Config.Config;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;

public class OnBedExplode implements Listener {
    private final Config config;

    public OnBedExplode(Config config) {
        this.config = config;
    }

    @EventHandler
    public void onBedExplode(BlockExplodeEvent event) {
        Block block = event.getBlock();
        if (!(block.getType().toString().toLowerCase().contains("bed") && !block.getType().equals(Material.BEDROCK)))
            return;

        this.config.checkWorldExists(event.getBlock().getWorld().getName());
        if(this.config.getCancelBedExplode(event.getBlock().getWorld().getName())) event.setCancelled(true);
    }
}
