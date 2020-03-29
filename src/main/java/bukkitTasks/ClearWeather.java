package bukkitTasks;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class ClearWeather extends BukkitRunnable{
	private World world;
	
	public ClearWeather(World world) {
		this.world = world;
	}

	@Override
	public void run() {
		Bukkit.getConsoleSender().sendMessage("§4[OnePlayerSleep] clearing weather"); 
		this.world.setStorm(false);
		this.world.setThundering(false);
		this.world.setWeatherDuration(5400);
	}
}
