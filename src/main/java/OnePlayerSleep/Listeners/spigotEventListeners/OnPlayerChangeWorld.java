package OnePlayerSleep.Listeners.spigotEventListeners;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.tools.Config.Config;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

public class OnPlayerChangeWorld implements Listener {
    private final OnePlayerSleep plugin;
    private final Config config;

    public OnPlayerChangeWorld(OnePlayerSleep plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event){
        Boolean messageFromSleepingIgnored = (Boolean) config.getConfigValue("messageFromSleepingIgnored", true);
        if(!messageFromSleepingIgnored && event.getPlayer().isSleepingIgnored()) return;
        if(event.getPlayer().hasPermission("sleep.ignore"))
            return;

        //handle if changing world while sleeping somehow
        Player me = event.getPlayer();
        World from = event.getFrom();
        World to = me.getWorld();
        this.config.checkWorldExists(from.getName());
        this.config.checkWorldExists(to.getName());
        if(me.isSleeping()){
            int numSleepingPlayers = 0;
            this.plugin.sleepingPlayers.get(from.getUID()).remove(me);

            for(String theirWorldName : this.config.getSyncWorlds(from.getName()))
            {
                World world = Bukkit.getWorld(theirWorldName);
                if(world == null) continue;
                numSleepingPlayers += this.plugin.sleepingPlayers.get(world.getUID()).size();
            }

            if(numSleepingPlayers == 0) {
                for (String worldName : this.config.getSyncWorlds(from.getName())) {
                    World world = Bukkit.getWorld(worldName);
                    if(world == null) continue;
                    if( this.plugin.doSleep.containsKey(world.getUID())) {
                        this.plugin.doSleep.get(world.getUID()).cancel();
                    }
                }
            }
        }

        //decrement previous world counter, remove if zero to reduce loop ranges
        this.plugin.numPlayers.putIfAbsent(from.getUID(),0L);
        this.plugin.numPlayers.put(from.getUID() , this.plugin.numPlayers.get(from.getUID())-1 );
        if(this.plugin.numPlayers.get(from.getUID()) == 0) this.plugin.numPlayers.remove(from.getUID());

        //increment next world counter, add if nonexistent
        this.plugin.numPlayers.putIfAbsent(to.getUID(),0L);
        this.plugin.numPlayers.put(to.getUID(), this.plugin.numPlayers.get(event.getPlayer().getWorld().getUID())+1 );
    }
}
