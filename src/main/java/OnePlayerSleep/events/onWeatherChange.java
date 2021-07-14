package OnePlayerSleep.events;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import OnePlayerSleep.tools.Config;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class onWeatherChange implements Listener {
	private Config config;

	public onWeatherChange(Config config) {
		this.config = config;
	}
	
	@EventHandler
	public void onWeatherChange(WeatherChangeEvent event) {
		for(String worldName : this.config.getSyncWorlds(event.getWorld().getName())) {
			World w = Bukkit.getWorld(worldName);
			w.setStorm(event.toWeatherState());

			//set other weathers a little longer than this world's weather duration so only one world causes the next weather update
			w.setWeatherDuration(event.getWorld().getWeatherDuration()+5);
			w.setThundering(event.getWorld().isThundering());
			w.setThunderDuration(event.getWorld().getThunderDuration()+5);
		}
	}
}
