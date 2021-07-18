package OnePlayerSleep.events;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.tools.Config;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;

public class onPlayerChangeWorld implements Listener {
    private static final Pattern dims = Pattern.compile("_nether|_the_end", Pattern.CASE_INSENSITIVE);
    private OnePlayerSleep plugin;
    private Config config;

    public onPlayerChangeWorld(OnePlayerSleep plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event){
        Boolean messageFromSleepingIgnored = (Boolean) config.getConfigValue("messageFromSleepingIgnored", true);
        if(messageFromSleepingIgnored && event.getPlayer().isSleepingIgnored()) return;
        if(event.getPlayer().hasPermission("sleep.ignore"))
            return;

        //handle if changing world while sleeping somehow
        Player me = event.getPlayer();
        World from = event.getFrom();
        World to = me.getWorld();
        if(me.isSleeping()){
            Integer numSleepingPlayers = 0;
            this.plugin.sleepingPlayers.get(from).remove(me);

            for(String theirWorldName : this.config.getSyncWorlds(from.getName()))
            {
                numSleepingPlayers += this.plugin.sleepingPlayers.get(Bukkit.getWorld(theirWorldName)).size();
            }

            if(numSleepingPlayers == 0) {
                for (String worldName : this.config.getSyncWorlds(from.getName())) {
                    if( this.plugin.doSleep.containsKey(Bukkit.getWorld(worldName))) {
                        this.plugin.doSleep.get(Bukkit.getWorld(worldName)).cancel();
                    }
                }
            }
        }

        //decrement previous world counter, remove if zero to reduce loop ranges
        this.plugin.numPlayers.put( from , this.plugin.numPlayers.get(from)-1 );
        if(this.plugin.numPlayers.get(from) == 0) this.plugin.numPlayers.remove(from);

        //increment next world counter, add if nonexistent
        this.plugin.numPlayers.putIfAbsent(to,Long.valueOf(0));
        this.plugin.numPlayers.put( to , this.plugin.numPlayers.get(event.getPlayer().getWorld())+1 );
    }
}
