package OnePlayerSleep.OnePlayerSleep;

import OnePlayerSleep.commands.*;
import OnePlayerSleep.events.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import OnePlayerSleep.tools.Config;
import OnePlayerSleep.tools.Metrics;
import OnePlayerSleep.tools.PAPI_expansion;
import OnePlayerSleep.types.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class OnePlayerSleep extends JavaPlugin implements Listener {
	private Config config;

	public Map<World,BukkitTask> doSleep;
	public Map<World,BukkitTask> clearWeather;
	public Map<Player, Message> wakeData; //list of players receiving wakeup option
	public Map<World, ArrayList<Player>> sleepingPlayers; //list of sleeping players for each world

	public Integer numPlayers;
	public Integer numSleepingPlayers;


	@Override
	public void onEnable() {
		this.config = new Config(this);
		this.numPlayers = 0;
		this.numSleepingPlayers = 0;
		this.doSleep = new HashMap<>();
		this.clearWeather = new HashMap<>();
		this.wakeData = new HashMap<>();
		this.sleepingPlayers = new HashMap<>();

		//make /sleep work
		getCommand("sleep").setExecutor(new Sleep(this));
		
		//let players tab for available subcommands
		getCommand("sleep").setTabCompleter(new TabComplete(this.config));
		
		//set executors so i can look them up from /sleep
		getCommand("sleep help").setExecutor(new Help());
		getCommand("sleep reload").setExecutor(new Reload(this, this.config));
		getCommand("sleep test").setExecutor(new Test(this));
		getCommand("sleep wakeup").setExecutor(new Wakeup(this, this.config));
		
		//other way to call wakeup, so sleep.wakeup doesn't depend on sleep.see
		getCommand("sleepwakeup").setExecutor(new Wakeup(this, this.config));

		//load config files and check if they're up to date
		// 	also check for hooks
		this.config.refreshConfigs();

		//if this plugin got reloaded, redo bed enter OnePlayerSleep.events
		for( Player p : Bukkit.getOnlinePlayers() ) {
			if(p.isSleeping()) {
				getServer().getPluginManager().callEvent(new PlayerBedEnterEvent(p, p.getWorld().getBlockAt(p.getBedLocation()), BedEnterResult.OK));
			}
		}
		
		//register all the spigot OnePlayerSleep.events I need
		getServer().getPluginManager().registerEvents(new onBedExplode(this, config), this);
		getServer().getPluginManager().registerEvents(new onPlayerBedEnter(this, config), this);
		getServer().getPluginManager().registerEvents(new onPlayerBedLeave(this, config), this);
		getServer().getPluginManager().registerEvents(new onPlayerJoin(this, config), this);
		getServer().getPluginManager().registerEvents(new onPlayerQuit(this, config), this);
		getServer().getPluginManager().registerEvents(new onWeatherChange(config), this);
		getServer().getPluginManager().registerEvents(this, this);
		
		if(this.config.hasPAPI()) {
			new PAPI_expansion(this).register();
		}

		int pluginId = 7096; // <-- Replace with the id of your plugin!
		new Metrics(this, pluginId);

		Boolean useSleepingIgnored = this.config.config.getBoolean("useSleepingIgnored", true);
		for (org.bukkit.World w : Bukkit.getWorlds()) {
			for (Player p : w.getPlayers()){
				if(useSleepingIgnored && p.isSleepingIgnored()) continue;
				if(p.hasPermission("sleep.ignore")) continue;
				this.numPlayers++;
				if(p.isSleeping()){
					this.numSleepingPlayers++;
					if(!this.sleepingPlayers.containsKey(w)){
						this.sleepingPlayers.put(w, new ArrayList<>());
					}
					this.sleepingPlayers.get(w).add(p);
				}
			}
		}
	}
	
	@Override
	public void onDisable() {
		
	}
	
	public Config getPluginConfig() {
		return this.config;
	}
}
