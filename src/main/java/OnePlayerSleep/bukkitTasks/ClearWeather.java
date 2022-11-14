package OnePlayerSleep.bukkitTasks;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

public class ClearWeather extends BukkitRunnable{
	private final World world;
	private Long duration = 0L;
	
	public ClearWeather(World world) {
		this.world = world;
	}
	
	public ClearWeather(World world, Long duration) {
		this.world = world;
		this.duration = duration;
	}

	@Override
	public void run() {
		this.world.setStorm(false);
		this.world.setThundering(false);
		if(this.duration == 0) {
			double randomFactor = new Random().nextDouble()*168000;
			this.duration = 12000 + (long)randomFactor;
		}
		this.world.setWeatherDuration(this.duration.intValue());
		this.cancel();
	}
}
