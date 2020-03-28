package bukkitTasks;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class ClearWeather extends BukkitRunnable{
	private World world;
	
	public ClearWeather(World world) {
		this.world = world;
	}

	@Override
	public void run() {
		this.world.setStorm(false);
		this.world.setWeatherDuration(5400);
	}
}
