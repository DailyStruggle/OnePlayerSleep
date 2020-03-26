package async;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import OnePlayerSleep.OnePlayerSleep;
import tools.Config;

public class DoSleep extends BukkitRunnable {
	private Config config;
	private World world;
	
	public DoSleep(OnePlayerSleep plugin, Config config, World world) {
		this.config = config;
		this.world = world;
	}
	
	@Override
	public void run() {
		long increment = config.config.getInt("increment");
		while(world.getTime() < config.config.getInt("stopTime") 
				&& !this.isCancelled()) {
			world.setTime(world.getTime()+increment);
		}
	}
}
