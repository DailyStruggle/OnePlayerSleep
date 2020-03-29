package events;

import org.bukkit.Bukkit;
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
		if(event.getPlayer().isSleepingIgnored() || event.getPlayer().hasPermission("sleep.ignore")) return; 
		Boolean doOtherWorld = config.config.getBoolean("doOtherWorlds");
		Boolean doOtherDim = config.config.getBoolean("doOtherDimensions");
		World world = event.getPlayer().getWorld();
		this.plugin.sleepingPlayers.get(world).remove(event.getPlayer());
		
		Long sleepingPlayers = Long.valueOf(0);
		for (World w : this.plugin.doSleep.keySet()) {
			if( !doOtherWorld && !event.getPlayer().getWorld().getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
			if( !doOtherDim && !event.getPlayer().getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;
			sleepingPlayers = sleepingPlayers + this.plugin.sleepingPlayers.get(w).size();
		}
		if(sleepingPlayers > 0) {
			for (World w : this.plugin.doSleep.keySet()) {
				if( !doOtherWorld && !event.getPlayer().getWorld().getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
				if( !doOtherDim && !event.getPlayer().getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;
				if( this.plugin.doSleep.containsKey(w)) {
					this.plugin.doSleep.get(w).cancel();
					this.plugin.doSleep.remove(w);
				}
			}
		}
		else if( event.getPlayer().getStatistic( Statistic.TIME_SINCE_REST ) < 3) {
			for (World w : this.plugin.doSleep.keySet()) {
				if( !doOtherWorld && !event.getPlayer().getWorld().getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
				if( !doOtherDim && !event.getPlayer().getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;
				if(w.getTime()!= world.getTime()) w.setTime(world.getTime());
				this.plugin.doSleep.get(w).cancel();;
				this.plugin.doSleep.remove(w);
				this.plugin.clearWeather.get(w).cancel();;
				this.plugin.clearWeather.remove(w);
				this.plugin.clearWeather.put(w, new ClearWeather(w).runTask(this.plugin));
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
