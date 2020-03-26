package events;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;

import OnePlayerSleep.OnePlayerSleep;
import tools.Config;

public class onPlayerBedLeave implements Listener {
	private OnePlayerSleep plugin;
	
	public onPlayerBedLeave(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
	}
	
	@EventHandler
	public void onPlayerBedLeave (PlayerBedLeaveEvent event) {
		World world = event.getPlayer().getWorld();
		this.plugin.sleepingPlayers.get(world).remove(event.getPlayer());
		if(this.plugin.sleepingPlayers.get(world).isEmpty()) {
			if(this.plugin.doSleep.containsKey(world)) {
				this.plugin.doSleep.get(world).cancel();
				this.plugin.doSleep.remove(world);
			}
		}
	}
}
