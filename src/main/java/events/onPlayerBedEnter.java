package events;

import OnePlayerSleep.OnePlayerSleep;
import bukkitTasks.OnSleepChecks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import tools.Config;

import java.util.HashMap;
import java.util.Map;

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
		//skip if player needs to be ignored by the plugin
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
		
		//cooldown logic
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
		
		//do other relevant checks asynchronously
		new OnSleepChecks(this.plugin, this.config, event.getPlayer()).runTaskAsynchronously(plugin);
	}
}
