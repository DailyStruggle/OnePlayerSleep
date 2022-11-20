package OnePlayerSleep.Listeners.spigotEventListeners;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import OnePlayerSleep.tools.Config.Config;

public class OnPlayerQuit implements Listener {
    private final OnePlayerSleep plugin;
    private final Config config;

    public OnPlayerQuit(OnePlayerSleep plugin, Config config) {
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
            int numSleepingPlayers = 0;
            this.plugin.sleepingPlayers.get(me.getWorld().getUID()).remove(me);

            this.config.checkWorldExists(myWorldName);
            for(String worldName : this.config.getSyncWorlds(myWorldName))
            {
                World world = Bukkit.getWorld(worldName);
                if(world == null) continue;
                numSleepingPlayers += this.plugin.sleepingPlayers.get(world.getUID()).size();
            }

            if(numSleepingPlayers == 0) {
                if( this.plugin.doSleep.containsKey(me.getWorld().getUID())) {
                    this.plugin.doSleep.get(me.getWorld().getUID()).cancel();
                }
                if( this.plugin.clearWeather.containsKey(me.getWorld().getUID())) {
                    this.plugin.clearWeather.get(me.getWorld().getUID()).cancel();
                }
            }
        }
    }
}
