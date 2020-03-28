package bukkitTasks;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import OnePlayerSleep.OnePlayerSleep;
import tools.Config;

public class PassTime extends BukkitRunnable{
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
		if(this.world.getTime() < config.config.getInt("stopTime") 
				&& this.world.getTime() >= config.config.getInt("startTime")) {
			this.didNightPass = true;
			this.world.setTime(this.world.getTime() + config.config.getInt("increment"));
			this.plugin.doSleep.remove(this.world);
			this.plugin.doSleep.put(this.world, new PassTime(this.plugin, this.config, this.world, this.didNightPass).runTaskLater(this.plugin, 1));
			if(this.config.config.getBoolean("globalNightSkipSync")) {
				for (World w : this.plugin.doSleep.keySet()) {
					w.setTime(world.getTime());
				}
			}
			return;
		}
		if(this.world.getTime() >= config.config.getInt("stopTime") 
				|| this.world.getTime() < config.config.getInt("startTime") ) {
			if(didNightPass) {
				this.cancel();
				this.plugin.doSleep.remove(this.world);
				this.plugin.doSleep.put(this.world, new ClearWeather(this.world).runTask(this.plugin));
			}
			else {
				this.cancel();
				this.plugin.doSleep.remove(this.world);
				this.plugin.doSleep.put(this.world, new ClearWeather(this.world).runTaskLater(this.plugin, this.config.config.getLong("sleepDelay")));
			}
		}
	}

}
