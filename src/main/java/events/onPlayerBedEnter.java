package events;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerBedEnterEvent;

import OnePlayerSleep.OnePlayerSleep;
import bukkitTasks.OnSleepChecks;
import tools.Config;

public class onPlayerBedEnter implements Listener {
	private OnePlayerSleep plugin;
	private Config config;
	private Map<Player,Long> lastTme = new HashMap<Player,Long>();
	
	public onPlayerBedEnter(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}
	
	@EventHandler
	public void onPlayerBedEnter(PlayerBedEnterEvent event) {
		if(event.getPlayer().isSleepingIgnored() || event.getPlayer().hasPermission("sleep.ignore")) return; 
		if(			config.version.contains("1_13") ||
					config.version.contains("1_14") ||
					config.version.contains("1_15")) {
			if(event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
		}
		if(	(	event.getPlayer().getWorld().getTime() < config.config.getInt("startTime") ||
				event.getPlayer().getWorld().getTime() > config.config.getInt("stopTime") ) &&
				!event.getPlayer().getWorld().hasStorm()) {
			event.setCancelled(true);
			return;
		}
		Long currentTime = System.currentTimeMillis();
		if(		this.lastTme.containsKey(event.getPlayer()) &&
				currentTime < this.lastTme.get(event.getPlayer()) + config.config.getLong("sleepCooldown")) {
			event.setCancelled(true);
			event.getPlayer().sendMessage( config.messages.getString("cooldownMessage") );
			return;
		}
		if(this.lastTme.containsKey(event.getPlayer())) 
			this.lastTme.remove(event.getPlayer());
		this.lastTme.put(event.getPlayer(), currentTime);
		new OnSleepChecks(this.plugin, this.config, event.getPlayer()).runTaskAsynchronously(plugin);
	}
}
