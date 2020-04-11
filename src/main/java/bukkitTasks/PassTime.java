package bukkitTasks;

import OnePlayerSleep.OnePlayerSleep;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
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
		Boolean doOtherWorld = this.config.config.getBoolean("doOtherWorlds");
		Boolean doOtherDim = this.config.config.getBoolean("doOtherDimensions");
		if(this.world.getTime() < config.config.getInt("stopTime") 
				&& this.world.getTime() >= config.config.getInt("startTime")) {
			this.world.setTime(this.world.getTime() + config.config.getInt("increment"));
			this.plugin.doSleep.remove(this.world);
			this.plugin.doSleep.put(this.world, new PassTime(this.plugin, this.config, this.world, true).runTaskLater(this.plugin, 1));
			if( doOtherWorld || doOtherDim ) {
				for (World w : this.plugin.doSleep.keySet()) {
					if( !doOtherWorld && !this.world.getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
					if( !doOtherDim && !this.world.getEnvironment().equals( w.getEnvironment() ) ) continue;
					w.setTime(world.getTime());
				}
			}
			return;
		}
		
		for (World w : this.plugin.doSleep.keySet()) {
			if( !doOtherWorld && !this.world.getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
			if( !doOtherDim && !this.world.getEnvironment().equals( w.getEnvironment() ) ) continue;
			this.plugin.doSleep.remove(w);
			if(didNightPass) this.plugin.doSleep.put(w, new ClearWeather(w).runTask(this.plugin));
			else this.plugin.doSleep.put(w, new ClearWeather(w).runTaskLater(this.plugin,this.config.config.getLong("sleepDelay")));
		}
		this.cancel();
	}

}
