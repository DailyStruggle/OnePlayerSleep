package OnePlayerSleep.events;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import OnePlayerSleep.tools.Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;

public class onPlayerQuit implements Listener {
    private static final Pattern dims = Pattern.compile("_nether|_the_end", Pattern.CASE_INSENSITIVE);
    private OnePlayerSleep plugin;
    private Config config;

    public onPlayerQuit(OnePlayerSleep plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        Boolean messageFromSleepingIgnored = (Boolean) config.getConfigValue("messageFromSleepingIgnored", true);
        if(messageFromSleepingIgnored && event.getPlayer().isSleepingIgnored()) return;
        if(event.getPlayer().hasPermission("sleep.ignore"))
            return;

        Player me = event.getPlayer();
        String myWorldName = me.getWorld().getName();

        //if quit while sleeping, cancel events if no players sleeping
        if(me.isSleeping()){
            Integer numSleepingPlayers = 0;
            this.plugin.sleepingPlayers.get(me.getWorld()).remove(me);

            for(String worldName : this.config.getSyncWorlds(myWorldName))
            {
                numSleepingPlayers += this.plugin.sleepingPlayers.get(Bukkit.getWorld(worldName)).size();
            }

            if(numSleepingPlayers == 0) {
                for (String worldName : this.config.getSyncWorlds(myWorldName)) {
                    if( this.plugin.doSleep.containsKey(Bukkit.getWorld(worldName))) {
                        this.plugin.doSleep.get(Bukkit.getWorld(worldName)).cancel();
                    }
                }
            }
        }
    }
}
