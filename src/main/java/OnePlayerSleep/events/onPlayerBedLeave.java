package OnePlayerSleep.events;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.bukkitTasks.ClearWeather;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import OnePlayerSleep.tools.Config;

import java.util.Set;

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
		this.plugin.numSleepingPlayers--;
		Boolean doOtherWorld = config.config.getBoolean("doOtherWorlds");
		Boolean doOtherDim = config.config.getBoolean("doOtherDimensions");
		World world = event.getPlayer().getWorld();
		if(		this.plugin.sleepingPlayers.containsKey(world) &&
				this.plugin.sleepingPlayers.get(world).contains(event.getPlayer())){
			this.plugin.sleepingPlayers.get(world).remove(event.getPlayer());
			if(this.plugin.sleepingPlayers.get(world).size() == 0) this.plugin.sleepingPlayers.remove(world);
		}

		Long sleepingPlayers = Long.valueOf(0);
		Set<World> sleepingWorlds = this.plugin.sleepingPlayers.keySet();
		for (World w : sleepingWorlds) {
			if( !doOtherWorld && !event.getPlayer().getWorld().getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
			if( !doOtherDim && !event.getPlayer().getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;
			sleepingPlayers = sleepingPlayers + this.plugin.sleepingPlayers.get(w).size();
		}
		if(sleepingPlayers == 0) {
			for (World w : Bukkit.getWorlds()) {
				if( !doOtherWorld && !event.getPlayer().getWorld().getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
				if( !doOtherDim && !event.getPlayer().getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;
				if( this.plugin.doSleep.containsKey(w)) {
					this.plugin.doSleep.get(w).cancel();
				}
			}
		}
		else if( event.getPlayer().getWorld().getTime() >= 23460 &&
					event.getPlayer().getWorld().getTime() <=  23999) {
			for (World w : Bukkit.getWorlds()) {
				if( !doOtherWorld && !event.getPlayer().getWorld().getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
				if( !doOtherDim && !event.getPlayer().getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;
				if(w.getTime()!= world.getTime()) w.setTime(world.getTime());
				this.plugin.doSleep.get(w).cancel();;
				this.plugin.clearWeather.get(w).cancel();;
				this.plugin.clearWeather.remove(w);
				this.plugin.clearWeather.put(w, new ClearWeather(w).runTask(this.plugin));
				if(this.config.config.getBoolean("resetAllStatistics")) {
					for (Player p : w.getPlayers()) { 
						if(p.isSleepingIgnored() || p.hasPermission("sleep.ignore")) continue;
						p.setStatistic(Statistic.TIME_SINCE_REST, 0);
					}
				}
			}
		}
	}
}
