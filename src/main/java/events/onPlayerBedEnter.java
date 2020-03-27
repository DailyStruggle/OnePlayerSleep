package events;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;

import OnePlayerSleep.OnePlayerSleep;
import bukkitTasks.AnnounceSleep;
import bukkitTasks.PassTime;
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
		if(event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
		if(this.plugin.sleepingPlayers.get(event.getPlayer().getWorld()).size() > 0) return;
		if(event.getPlayer().hasPermission("sleep.ignore")) return;
		this.plugin.sleepingPlayers.get(event.getPlayer().getWorld()).add(event.getPlayer());
		new AnnounceSleep(this.plugin, this.config, event.getPlayer()).runTaskAsynchronously(this.plugin);
		if(!plugin.doSleep.containsKey(event.getPlayer().getWorld())) {
			plugin.doSleep.put(event.getPlayer().getWorld(), new PassTime(this.plugin, this.config, event.getPlayer().getWorld()).runTaskLater(this.plugin, config.config.getInt("sleepDelay")));
		}
	}
}
