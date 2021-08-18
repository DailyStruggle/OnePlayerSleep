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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class OnePlayerSleep extends JavaPlugin implements Listener {
	private Config config;

	public ConcurrentHashMap<World,BukkitTask> doSleep;
	public ConcurrentHashMap<World,BukkitTask> clearWeather;
	public ConcurrentHashMap<Player, Message> wakeData; //list of players receiving messages
	public ConcurrentHashMap<World, HashSet<Player>> sleepingPlayers; //list of sleeping players for each world
	public ConcurrentHashMap<World,Long> numPlayers;

	@Override
	public void onEnable() {
		this.config = new Config(this);
		this.numPlayers = new ConcurrentHashMap<>();
		this.doSleep = new ConcurrentHashMap<>();
		this.clearWeather = new ConcurrentHashMap<>();
		this.wakeData = new ConcurrentHashMap<>();
		this.sleepingPlayers = new ConcurrentHashMap<>();

		//make /sleep work
		Objects.requireNonNull(getCommand("sleep")).setExecutor(new Sleep(this, this.config));

		//let players tab for available subcommands
		Objects.requireNonNull(getCommand("sleep")).setTabCompleter(new TabComplete(this.config));

		//set executors so i can look them up from /sleep
		Objects.requireNonNull(getCommand("sleep help")).setExecutor(new Help(this, this.config));
		Objects.requireNonNull(getCommand("sleep reload")).setExecutor(new Reload(this, this.config));
		Objects.requireNonNull(getCommand("sleep test")).setExecutor(new Test(this, this.config));
		Objects.requireNonNull(getCommand("sleep wakeup")).setExecutor(new Wakeup(this, this.config));

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
		getServer().getPluginManager().registerEvents(new onPlayerChangeWorld(this, config), this);
		getServer().getPluginManager().registerEvents(new onWeatherChange(config), this);
		getServer().getPluginManager().registerEvents(this, this);
		
		if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			new PAPI_expansion(this).register();
		}

		int pluginId = 7096; // <-- Replace with the id of your plugin!
		new Metrics(this, pluginId);

		//fix player counts on reload
		boolean messageFromSleepingIgnored = (Boolean) this.config.getConfigValue("messageFromSleepingIgnored", true);
		for (org.bukkit.World w : Bukkit.getWorlds()) {
			this.numPlayers.put(w,Long.valueOf(0));
			for (Player p : w.getPlayers()){
				if(p.hasPermission("sleep.ignore")) continue;
				this.numPlayers.put(w, this.numPlayers.get(w)+1);
				if(!messageFromSleepingIgnored && p.isSleepingIgnored()) continue;
				if(p.isSleeping()){
					if(!this.sleepingPlayers.containsKey(w)){
						this.sleepingPlayers.put(w, new HashSet<>());
					}
					this.sleepingPlayers.get(w).add(p);
				}
			}
			if(numPlayers.get(w) == 0) numPlayers.remove(w);
		}
	}
	
	@Override
	public void onDisable() {
		
	}
	
	public Config getPluginConfig() {
		return this.config;
	}
}
