package OnePlayerSleep.events;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import OnePlayerSleep.tools.Config;

public class onPlayerJoin implements Listener {
    private OnePlayerSleep plugin;
    private Config config;

    public onPlayerJoin(OnePlayerSleep plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        if(config.config.getBoolean("useSleepingIgnored", true)
                && event.getPlayer().isSleepingIgnored())
            return;
        if(event.getPlayer().hasPermission("sleep.ignore"))
            return;
        this.plugin.numPlayers++;
    }
}
