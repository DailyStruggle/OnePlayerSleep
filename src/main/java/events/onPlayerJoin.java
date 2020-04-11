package events;

import OnePlayerSleep.OnePlayerSleep;
import bukkitTasks.OnSleepChecks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import tools.Config;

public class onPlayerJoin implements Listener {
    private OnePlayerSleep plugin;
    private Config config;

    public onPlayerJoin(OnePlayerSleep plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        if(event.getPlayer().isSleepingIgnored() || event.getPlayer().isSleepingIgnored())
            return;
        this.plugin.numPlayers++;
    }
}
