package bukkitTasks;

import java.util.ArrayList;
import java.util.Random;

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
	private Boolean bypassSleep = false;
	
	public OnSleepChecks(OnePlayerSleep plugin, Config config, Player player) {
		this.plugin = plugin;
		this.config = config;
		this.player = player;

	}

	public OnSleepChecks(OnePlayerSleep plugin, Config config, Player player, Boolean bypassSleep) {
		this.plugin = plugin;
		this.config = config;
		this.player = player;
		this.bypassSleep = bypassSleep;
	}

	@Override
	public void run() {
		//if player isn't sleeping anymore when the server gets here, pull out
		if( !(this.player.isSleeping())  && !this.bypassSleep) {
			this.cancel();
			return;
		}
		
		//add player to list of sleeping players
		if(!this.plugin.sleepingPlayers.containsKey(this.player.getWorld())) this.plugin.sleepingPlayers.put(this.player.getWorld(),new ArrayList<Player>());
		if(!this.plugin.sleepingPlayers.get(this.player.getWorld()).contains(this.player)) this.plugin.sleepingPlayers.get(this.player.getWorld()).add(this.player);
		
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
			//async message selection and delivery
			//skip if called by a test function
			if(!this.bypassSleep) new AnnounceSleep(this.plugin, this.config, this.player).runTaskAsynchronously(this.plugin);
			
			//start sleep task
			if(plugin.doSleep.containsKey(this.player.getWorld())) plugin.doSleep.remove(this.player.getWorld());
			plugin.doSleep.put(this.player.getWorld(), new PassTime(this.plugin, this.config, this.player.getWorld()).runTaskLater(this.plugin, config.config.getInt("sleepDelay")));
		}
		
		//set up clear weather task for later
		if(!plugin.clearWeather.containsKey(this.player.getWorld())) {
			//calculate how long to wait before clearing weather
			Long dT = (this.config.config.getLong("stopTime") - this.player.getWorld().getTime()) / this.config.config.getLong("increment");
			Long cap = (this.player.getWorld().getTime() - this.config.config.getLong("startTime")) / this.config.config.getLong("increment");
			
			//calculate how long to clear weather for
			Double randomFactor = new Random().nextDouble()*168000;
			Long duration = 12000 + randomFactor.longValue();
			
			//start task
			if(dT>1 && cap>=0) plugin.clearWeather.put(this.player.getWorld(), new ClearWeather(this.player.getWorld(),duration).runTaskLater(this.plugin, dT+this.config.config.getLong("sleepDelay")));
			else plugin.clearWeather.put(this.player.getWorld(), new ClearWeather(this.player.getWorld(),duration).runTaskLater(this.plugin, 2*this.config.config.getLong("sleepDelay")));
		}
	}

}
