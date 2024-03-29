package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.tools.SendMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config.Config;

import java.util.*;

public class OnSleepChecks extends BukkitRunnable{
	private final OnePlayerSleep plugin;
	private final Config config;
	private final Player player;
	private final World world;

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
		//flag for testing messages
		if(this.player != null && !this.player.isSleeping())
		{
			this.cancel();
			return;
		}

		//add player to list of sleeping players for the world
		if(player != null)
		{
			this.plugin.sleepingPlayers.putIfAbsent(this.world.getUID(), new HashSet<>());
			this.plugin.sleepingPlayers.get(this.world.getUID()).add(this.player);
		}

		List<String> syncWorlds = this.config.getSyncWorlds(this.world.getName());

		//check for valid players
		int numPlayers = 0;
		int numSleepingPlayers = 0;
		for (String worldName : syncWorlds){
			World w = Bukkit.getWorld(worldName);
			if(w == null)
			{
				SendMessage.sendMessage(Bukkit.getConsoleSender(),this.config.getLog("invalidWorld",worldName));
				SendMessage.sendMessage(Bukkit.getConsoleSender(),this.config.getLog("invalidWorld",worldName));
				continue;
			}

			if( this.plugin.sleepingPlayers.containsKey(w.getUID()) )
				numSleepingPlayers += this.plugin.sleepingPlayers.get(w.getUID()).size();
			numPlayers += w.getPlayers().size();
		}

		//only announce the first bed entry, and only when there's more than one player to see it
		Integer sleepDelay = (Integer)this.config.getConfigValue("sleepDelay", 60);
		if(numSleepingPlayers < 2 && numPlayers >= this.config.getMinPlayers()) {
			//async message selection and delivery
			new AnnounceSleep(this.plugin, this.config, this.player.getName(), this.world).runTaskAsynchronously(this.plugin);

			//start sleep task
			plugin.doSleep.remove(this.world.getUID());
			plugin.doSleep.put(this.world.getUID(), new PassTime(this.plugin, this.config, this.world)
					.runTaskLater(this.plugin, sleepDelay));
		}
		
		//set up clear weather task for later
		if(!plugin.clearWeather.containsKey(this.world.getUID())) {
			//calculate how long to wait before clearing weather
			long dT = (this.config.getStopTime(this.world.getName()) - this.world.getTime()) / (Integer) this.config.getConfigValue("increment", 150);
			long cap = (this.world.getTime() - this.config.getStartTime(this.world.getName())) / (Integer) this.config.getConfigValue("increment", 150);
			
			//calculate how long to clear weather for
			double randomFactor = new Random().nextDouble()*168000;
			Long duration = 12000 + (long)randomFactor;
			
			//start task
			if(dT>1 && cap>=0) this.plugin.clearWeather.put(this.world.getUID(), new ClearWeather(this.world,duration).runTaskLater(this.plugin, dT+sleepDelay));
			else this.plugin.clearWeather.put(this.world.getUID(), new ClearWeather(this.world,duration).runTaskLater(this.plugin, 2L * sleepDelay));
		}
	}
}
