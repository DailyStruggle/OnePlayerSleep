package bukkitTasks;

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;


import OnePlayerSleep.OnePlayerSleep;
import tools.Config;

public class OnSleepChecks extends BukkitRunnable{
	private OnePlayerSleep plugin;
	private Config config;
	private Player player;
	
	public OnSleepChecks(OnePlayerSleep plugin, Config config, Player player) {
		this.plugin = plugin;
		this.config = config;
		this.player = player;
	}
	
	@Override
	public void run() {
		//if player isn't sleeping anymore when the server gets here, pull out
		if( !(this.player.isSleeping()) ) {
			this.cancel();
			return;
		}
		
		//add player to list of sleeping players
		if(!this.plugin.sleepingPlayers.containsKey(this.player.getWorld())) this.plugin.sleepingPlayers.put(this.player.getWorld(),new ArrayList<Player>());
		this.plugin.sleepingPlayers.get(this.player.getWorld()).add(this.player);
		
		Boolean doOtherWorld = config.config.getBoolean("doOtherWorlds");
		Boolean doOtherDim = config.config.getBoolean("doOtherDimensions");
		
		int numPlayers = 0;
		int numSleepingPlayers = 0;
		//check for valid players
		for (World w : Bukkit.getWorlds()){
			if( !doOtherWorld && !this.player.getWorld().getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
			if( !doOtherDim && !this.player.getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;
			if( this.plugin.sleepingPlayers.containsKey(w) )numSleepingPlayers = numSleepingPlayers + this.plugin.sleepingPlayers.get(w).size();
			for (Player p : w.getPlayers()) {
				if(p.isSleepingIgnored() || p.hasPermission("sleep.ignore")) continue;
				numPlayers = numPlayers + 1;
				if(numPlayers > 1) break;
			}
		}
		//only announce the first bed entry, when there's more than one player to see it
		if(numSleepingPlayers < 2 && numPlayers > 1) {
			new AnnounceSleep(this.plugin, this.config, this.player).runTaskAsynchronously(this.plugin);
			if(plugin.doSleep.containsKey(this.player.getWorld())) plugin.doSleep.remove(this.player.getWorld());
			plugin.doSleep.put(this.player.getWorld(), new PassTime(this.plugin, this.config, this.player.getWorld()).runTaskLater(this.plugin, config.config.getInt("sleepDelay")));
		}
		
		if(!plugin.clearWeather.containsKey(this.player.getWorld())) {
			Long dT = (this.config.config.getLong("stopTime") - this.player.getWorld().getTime()) / this.config.config.getLong("increment");
			Long cap = (this.player.getWorld().getTime() - this.config.config.getLong("startTime")) / this.config.config.getLong("increment");
			if(dT>1 && cap>=0) plugin.clearWeather.put(this.player.getWorld(), new ClearWeather(this.player.getWorld()).runTaskLater(this.plugin, dT+this.config.config.getLong("sleepDelay")));
			else plugin.clearWeather.put(this.player.getWorld(), new ClearWeather(this.player.getWorld()).runTaskLater(this.plugin, 2*this.config.config.getLong("sleepDelay")));
		}
	}

}
