package bukkitTasks;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import OnePlayerSleep.OnePlayerSleep;
import tools.Config;

public class DoSleep extends BukkitRunnable {
	private OnePlayerSleep plugin;
	private Config config;
	private World world;
	
	public DoSleep(OnePlayerSleep plugin, Config config, World world) {
		this.plugin = plugin;
		this.config = config;
		this.world = world;
	}
	
	@Override
	public void run() {
		long increment = config.config.getInt("increment");
		Boolean didNightPass = false;
		while(this.world.getTime() < config.config.getInt("stopTime") 
				&& this.world.getTime() > config.config.getInt("startTime")  
				&& !this.isCancelled()) {
			didNightPass = true;
			this.world.setTime(world.getTime()+increment);
		}
		if(this.world.getTime() > config.config.getInt("stopTime") 
				&& this.world.getTime() < config.config.getInt("startTime") ) {
			if(didNightPass)
				this.world.setStorm(false);
			else {
				this.cancel();
				plugin.doSleep.remove(this.world);
				plugin.doSleep.put(this.world, new ClearWeather(this.world).runTaskLaterAsynchronously(this.plugin, this.config.config.getLong("sleepDelay")));
			}
		}
	}
}
