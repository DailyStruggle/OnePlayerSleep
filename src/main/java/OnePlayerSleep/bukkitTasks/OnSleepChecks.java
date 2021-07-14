package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config;

import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class OnSleepChecks extends BukkitRunnable{
	private static final Pattern dims = Pattern.compile("_nether|_the_end", Pattern.CASE_INSENSITIVE);
	private OnePlayerSleep plugin;
	private Config config;
	private Player player;
	private World world;
	private Boolean bypassSleep = false; //flag for testing messages
	
	public OnSleepChecks(OnePlayerSleep plugin, Config config, Player player, World world) {
		this.plugin = plugin;
		this.config = config;
		this.player = player;
		this.world = world;
	}

	@Override
	public void run() {
		//if player isn't sleeping anymore when the server gets here, pull out
		// also skip if called by a test function
		if( 	bypassSleep
				|| (this.player != null && !this.player.isSleeping()))
		{
			this.cancel();
			return;
		}

		//add player to list of sleeping players for the world
		if(player != null)
		{
			this.plugin.sleepingPlayers.putIfAbsent(this.world, new HashSet<>());
			this.plugin.sleepingPlayers.get(this.world).add(this.player);
		}

		List<String> syncWorlds = this.config.getSyncWorlds(this.world.getName());

		//check for valid players
		int numPlayers = 0;
		int numSleepingPlayers = 0;
		for (String worldName : syncWorlds){
			World w = Bukkit.getWorld(worldName);
			if(w == null)
			{
				Bukkit.getLogger().log(Level.WARNING, "[sleep] could not find world '" + worldName + "'"
						+ "\n        please check your messages.yml");
			}

			if( this.plugin.sleepingPlayers.containsKey(w) ) numSleepingPlayers += this.plugin.sleepingPlayers.get(w).size();
			numPlayers += w.getPlayers().size();
		}

		//only announce the first bed entry, and only when there's more than one player to see it
		if(numSleepingPlayers < 2 && numPlayers >= this.config.getMinPlayers()) {
			//async message selection and delivery
			new AnnounceSleep(this.plugin, this.config, this.player.getName(), this.world).runTaskAsynchronously(this.plugin);
			
			//start sleep task
			if(plugin.doSleep.containsKey(this.world)) plugin.doSleep.remove(this.world);
			plugin.doSleep.put(this.world, new PassTime(this.plugin, this.config, this.world)
					.runTaskLater(this.plugin, config.config.getInt("sleepDelay")));
		}
		
		//set up clear weather task for later
		if(!plugin.clearWeather.containsKey(this.world)) {
			//calculate how long to wait before clearing weather
			Long dT = (this.config.config.getLong("stopTime") - this.world.getTime()) / this.config.config.getLong("increment");
			Long cap = (this.world.getTime() - this.config.config.getLong("startTime")) / this.config.config.getLong("increment");
			
			//calculate how long to clear weather for
			Double randomFactor = new Random().nextDouble()*168000;
			Long duration = 12000 + randomFactor.longValue();
			
			//start task
			if(dT>1 && cap>=0) plugin.clearWeather.put(this.world, new ClearWeather(this.world,duration).runTaskLater(this.plugin, dT+this.config.config.getLong("sleepDelay")));
			else plugin.clearWeather.put(this.world, new ClearWeather(this.world,duration).runTaskLater(this.plugin, 2*this.config.config.getLong("sleepDelay")));
		}
	}

}
