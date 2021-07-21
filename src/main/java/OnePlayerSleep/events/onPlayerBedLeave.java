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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class onPlayerBedLeave implements Listener {
	private static final Pattern dims = Pattern.compile("_nether|_the_end", Pattern.CASE_INSENSITIVE);
	private OnePlayerSleep plugin;
	private Config config;
	
	public onPlayerBedLeave(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}
	
	@EventHandler
	public void onPlayerBedLeave (PlayerBedLeaveEvent event) {
		Boolean messageFromSleepingIgnored = (Boolean) config.getConfigValue("messageFromSleepingIgnored", true);
		if(!messageFromSleepingIgnored && event.getPlayer().isSleepingIgnored()) return;
		if(event.getPlayer().hasPermission("sleep.ignore")) return;

		//remove player from sleep lookup table
		World myWorld = event.getPlayer().getWorld();
		String myWorldName = myWorld.getName();
		if(		this.plugin.sleepingPlayers.containsKey(myWorld) &&
				this.plugin.sleepingPlayers.get(myWorld).contains(event.getPlayer())){
			this.plugin.sleepingPlayers.get(myWorld).remove(event.getPlayer());
			if(this.plugin.sleepingPlayers.get(myWorld).size() == 0) this.plugin.sleepingPlayers.remove(myWorld);
		}

		Long numSleepingPlayers = Long.valueOf(0);
		for(String theirWorldName : this.config.getSyncWorlds(event.getBed().getWorld().getName()))
		{
			if(this.plugin.sleepingPlayers.containsKey(Bukkit.getWorld(theirWorldName)))
				numSleepingPlayers += this.plugin.sleepingPlayers.get(Bukkit.getWorld(theirWorldName)).size();
		}

		if(numSleepingPlayers == 0) {
			for(String worldName : this.config.getSyncWorlds(myWorldName)) {
				if( this.plugin.doSleep.containsKey(Bukkit.getWorld(worldName))) {
					this.plugin.doSleep.get(Bukkit.getWorld(worldName)).cancel();
				}
			}
		}
		else if( event.getPlayer().getWorld().getTime() >= 23460) {
			for(String worldName : this.config.getSyncWorlds(myWorldName)) {
				World w = Bukkit.getWorld(worldName);
				if(w.getTime()!= myWorld.getTime()) w.setTime(myWorld.getTime());
				this.plugin.doSleep.get(w).cancel();
				this.plugin.clearWeather.get(w).cancel();
				this.plugin.clearWeather.remove(w);
				this.plugin.clearWeather.put(w, new ClearWeather(w).runTask(this.plugin));
				if((Boolean)this.config.getConfigValue("resetAllStatistics", true)) {
					for (Player p : w.getPlayers()) {
						if(p.hasPermission("sleep.ignore")) continue;
						p.setStatistic(Statistic.TIME_SINCE_REST, 0);
					}
				}
			}
		}
	}
}
