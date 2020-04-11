package events;

import OnePlayerSleep.OnePlayerSleep;
import bukkitTasks.OnSleepChecks;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import tools.Config;

public class onPlayerQuit implements Listener {
    private OnePlayerSleep plugin;
    private Config config;

    public onPlayerQuit(OnePlayerSleep plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        if(event.getPlayer().isSleepingIgnored() || event.getPlayer().isSleepingIgnored())
            return;
        Boolean doOtherWorld = config.config.getBoolean("doOtherWorlds");
        Boolean doOtherDim = config.config.getBoolean("doOtherDimensions");
        this.plugin.numPlayers--;
        if(event.getPlayer().isSleeping()){

            this.plugin.numSleepingPlayers--;
            if(this.plugin.numSleepingPlayers < 0) this.plugin.numSleepingPlayers = 0;
            if(this.plugin.numSleepingPlayers == 0) {
                for (World w : Bukkit.getWorlds()) {
                    if( !doOtherWorld && !event.getPlayer().getWorld().getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
                    if( !doOtherDim && !event.getPlayer().getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;
                    if( this.plugin.doSleep.containsKey(w)) {
                        this.plugin.doSleep.get(w).cancel();
                    }
                }
            }
        }

    }
}
