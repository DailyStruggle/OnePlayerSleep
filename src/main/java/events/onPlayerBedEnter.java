package events;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.player.PlayerBedEnterEvent;

import OnePlayerSleep.OnePlayerSleep;
import bukkitTasks.OnSleepChecks;
import tools.Config;

public class onPlayerBedEnter implements Listener {
	private OnePlayerSleep plugin;
	private Config config;
	
	public onPlayerBedEnter(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}
	
	@EventHandler
	public void onPlayerBedEnter(PlayerBedEnterEvent event) {
		if(			config.version.contains("1_13") ||
					config.version.contains("1_14") ||
					config.version.contains("1_15")) {
			if(event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
		}
		if(		event.getPlayer().getWorld().getTime() < config.config.getInt("startTime") ||
				event.getPlayer().getWorld().getTime() > config.config.getInt("stopTime") ) {
			event.setUseBed(Result.DENY);
			event.setCancelled(true);
			return;
		}
		new OnSleepChecks(this.plugin, this.config, event.getPlayer()).runTaskAsynchronously(plugin);
	}
}
