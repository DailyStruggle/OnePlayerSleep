package events;

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;

import tools.Config;

public class onWeatherChange implements Listener {
	private Config config;
	
	public onWeatherChange(Config config) {
		this.config = config;
	}
	
	@EventHandler
	public void onWeatherChange(WeatherChangeEvent event) {
		if(!this.config.config.getBoolean("doOtherWorlds")) return;
		ArrayList<World> worlds = (ArrayList<World>) Bukkit.getWorlds();
		int i = worlds.indexOf(event.getWorld());
		for (World w : worlds) {
			if(w.equals(event.getWorld())) continue;
			w.setStorm(worlds.get(i).hasStorm());
			w.setWeatherDuration(worlds.get(i).getWeatherDuration()+5);
			w.setThundering(worlds.get(i).isThundering());
			w.setThunderDuration(worlds.get(i).getThunderDuration()+5);
		}
	}
}
