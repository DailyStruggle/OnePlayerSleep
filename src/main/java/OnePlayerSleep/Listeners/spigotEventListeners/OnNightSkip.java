package OnePlayerSleep.Listeners.spigotEventListeners;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.tools.Config.Config;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.world.TimeSkipEvent;

public class OnNightSkip implements Listener {
    private final OnePlayerSleep plugin;

    public OnNightSkip(OnePlayerSleep plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNightSkip(TimeSkipEvent event) {
        String reason;
        try {
            reason = event.getSkipReason().name();
        } catch (NoSuchMethodError e) {
            try {
                reason = event.getClass().getMethod("getReason").invoke(event).toString();
            } catch (Exception ex) {
                return;
            }
        }
        if(!reason.equals("NIGHT_SKIP")) return;
        plugin.wakeupCommandTime.set(System.currentTimeMillis());
    }
}
