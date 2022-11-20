package OnePlayerSleep.Listeners.spigotEventListeners;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import OnePlayerSleep.tools.Config.Config;

public class OnPlayerJoin implements Listener {
    private final OnePlayerSleep plugin;
    private final Config config;

    public OnPlayerJoin(OnePlayerSleep plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        Boolean messageFromSleepingIgnored = (Boolean) config.getConfigValue("messageFromSleepingIgnored", true);
        if(!messageFromSleepingIgnored && event.getPlayer().isSleepingIgnored()) return;
        if(event.getPlayer().hasPermission("sleep.ignore"))
            return;

        World myWorld = event.getPlayer().getWorld();
        this.plugin.numPlayers.putIfAbsent(myWorld.getUID(),1L);

        this.plugin.numPlayers.put( event.getPlayer().getWorld().getUID(),
                this.plugin.numPlayers.get(event.getPlayer().getWorld().getUID())+1 );
    }
}
