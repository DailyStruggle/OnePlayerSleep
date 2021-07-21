package OnePlayerSleep.events;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import OnePlayerSleep.tools.Config;

public class onPlayerQuit implements Listener {
    private OnePlayerSleep plugin;
    private Config config;

    public onPlayerQuit(OnePlayerSleep plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        Boolean messageFromSleepingIgnored = (Boolean) config.getConfigValue("messageFromSleepingIgnored", true);
        if(!messageFromSleepingIgnored && event.getPlayer().isSleepingIgnored()) return;
        if(event.getPlayer().hasPermission("sleep.ignore"))
            return;

        Player me = event.getPlayer();
        String myWorldName = me.getWorld().getName();

        //if quit while sleeping, cancel events if no players sleeping
        if(me.isSleeping()){
            Integer numSleepingPlayers = 0;
            this.plugin.sleepingPlayers.get(me.getWorld()).remove(me);

            this.config.checkWorldExists(myWorldName);
            for(String worldName : this.config.getSyncWorlds(myWorldName))
            {
                numSleepingPlayers += this.plugin.sleepingPlayers.get(Bukkit.getWorld(worldName)).size();
            }

            if(numSleepingPlayers == 0) {
                if( this.plugin.doSleep.containsKey(me.getWorld())) {
                    this.plugin.doSleep.get(me.getWorld()).cancel();
                }
                if( this.plugin.clearWeather.containsKey(me.getWorld())) {
                    this.plugin.clearWeather.get(me.getWorld()).cancel();
                }
            }
        }
    }
}
