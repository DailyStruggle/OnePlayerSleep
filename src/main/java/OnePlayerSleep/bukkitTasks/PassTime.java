package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config;

import java.util.List;
import java.util.regex.Pattern;

public class PassTime extends BukkitRunnable{
	private static final Pattern dims = Pattern.compile("_nether|_the_end", Pattern.CASE_INSENSITIVE);
	private OnePlayerSleep plugin;
	private Config config;
	private World world;
	private Boolean didNightPass;

	public PassTime(OnePlayerSleep plugin, Config config, World world) {
		this.plugin = plugin;
		this.config = config;
		this.world = world;
		this.didNightPass= false;
	}
	
	public PassTime(OnePlayerSleep plugin, Config config, World world, Boolean didNightPass) {
		this.plugin = plugin;
		this.config = config;
		this.world = world;
		this.didNightPass= didNightPass;
	}

	@Override
	public void run() {
		//while in the given timespan, set time
		List<String> worldNames = this.config.getSyncWorlds(this.world.getName());
		Integer timeBetweenIncrements = (Integer) config.getConfigValue("increment",150);
		if (this.world.getTime() < this.config.getStopTime(this.world.getName())
				&& this.world.getTime() >= this.config.getStartTime(this.world.getName())) {
			Long newTime = this.world.getTime() + timeBetweenIncrements;
			this.plugin.doSleep.remove(this.world);
			this.plugin.doSleep.put(this.world,
					new PassTime(this.plugin, this.config, this.world, true)
							.runTaskLater(this.plugin, timeBetweenIncrements));

			//update all synced worlds
			for(String worldName : worldNames) {
				World targetWorld = Bukkit.getWorld(worldName);
				targetWorld.setTime(newTime);
			}
			return;
		}

		//else clear weather and stop
		this.plugin.doSleep.remove(this.world);
		for(String worldName : worldNames) {
			World targetWorld = Bukkit.getWorld(worldName);
			if (didNightPass) this.plugin.clearWeather.put(targetWorld, new ClearWeather(targetWorld).runTask(this.plugin));
			else this.plugin.clearWeather.put(this.world, new ClearWeather(targetWorld).runTaskLater(this.plugin, (Integer)this.config.getConfigValue("sleepDelay",60)));
		}

		this.cancel();
		this.plugin.doSleep.remove(this.world);
	}
}
