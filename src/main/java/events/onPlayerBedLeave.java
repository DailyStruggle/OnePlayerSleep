package events;

import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;

import OnePlayerSleep.OnePlayerSleep;
import bukkitTasks.ClearWeather;
import tools.Config;

public class onPlayerBedLeave implements Listener {
	private OnePlayerSleep plugin;
	private Config config;
	
	public onPlayerBedLeave(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}
	
	@EventHandler
	public void onPlayerBedLeave (PlayerBedLeaveEvent event) {
		if(event.getPlayer().hasPermission("sleep.ignore")) return; 
		World world = event.getPlayer().getWorld();
		this.plugin.sleepingPlayers.get(world).remove(event.getPlayer());
		if(this.plugin.sleepingPlayers.get(world).isEmpty()) {
			if(this.plugin.doSleep.containsKey(world)) {
				this.plugin.doSleep.get(world).cancel();
				this.plugin.doSleep.remove(world);
			}
		}
		if(		this.config.config.getBoolean("globalNightSkipSync") &&
				event.getPlayer().getStatistic( Statistic.TIME_SINCE_REST ) < 10) {
			for (World w : this.plugin.doSleep.keySet()) {
				if(w.getTime()!= world.getTime()) {
					w.setTime(world.getTime());
					this.plugin.doSleep.remove(w);
					this.plugin.doSleep.put(w, new ClearWeather(w).runTask(this.plugin));
					if(this.config.config.getBoolean("resetAllStatistics")) {
						for (Player p : w.getPlayers()) {
							if(p.hasPermission("sleep.ignore")) continue;
							p.setStatistic(Statistic.TIME_SINCE_REST, 0);
						}
					}
				}
			}
		}
	}
}
