package OnePlayerSleep.OnePlayerSleep;

import OnePlayerSleep.Listeners.spigotEventListeners.*;
import OnePlayerSleep.commands.*;
import OnePlayerSleep.tools.Config.Config;
import OnePlayerSleep.tools.Config.Configs;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import OnePlayerSleep.tools.Metrics;
import OnePlayerSleep.tools.softdepends.PAPI_expansion;
import OnePlayerSleep.types.MessageImpl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class OnePlayerSleep extends JavaPlugin implements Listener {
	private static OnePlayerSleep instance;

	private static Configs configs;

	public ConcurrentHashMap<UUID,BukkitTask> doSleep;
	public ConcurrentHashMap<UUID,BukkitTask> clearWeather;
	public ConcurrentHashMap<UUID,AtomicLong> clearWeatherTime;
	public ConcurrentHashMap<UUID, MessageImpl> wakeData; //list of players receiving messages
	public ConcurrentHashMap<UUID, HashSet<Player>> sleepingPlayers; //list of sleeping players for each world
	public ConcurrentHashMap<UUID,Long> numPlayers;
	public AtomicLong wakeupCommandTime = new AtomicLong(0L);

	public static OnePlayerSleep getInstance() {
		return instance;
	}

	public static Configs getConfigs() {
		return configs;
	}

	@Override
	public void onEnable() {
		instance = this;
		configs = new Configs();
		this.numPlayers = new ConcurrentHashMap<>();
		this.doSleep = new ConcurrentHashMap<>();
		this.clearWeather = new ConcurrentHashMap<>();
		this.clearWeatherTime = new ConcurrentHashMap<>();
		this.wakeData = new ConcurrentHashMap<>();
		this.sleepingPlayers = new ConcurrentHashMap<>();

		//make /sleep work
		Objects.requireNonNull(getCommand("sleep")).setExecutor(new Sleep(this));

		//let players tab for available subcommands
		Objects.requireNonNull(getCommand("sleep")).setTabCompleter(new TabComplete(configs.config));

		//set executors so i can look them up from /sleep
		Objects.requireNonNull(getCommand("sleep help")).setExecutor(new Help(this, configs.config));
		Objects.requireNonNull(getCommand("sleep reload")).setExecutor(new Reload(this, configs.config));
		Objects.requireNonNull(getCommand("sleep test")).setExecutor(new Test(this, configs.config));
		Objects.requireNonNull(getCommand("sleep wakeup")).setExecutor(new Wakeup(this, configs.config));

		//load configs.config files and check if they're up to date
		// 	also check for hooks
		configs.config.refreshConfigs();

		//if this plugin got reloaded, redo bed enter OnePlayerSleep.events
		for( Player p : Bukkit.getOnlinePlayers() ) {
			if(p.isSleeping()) {
				getServer().getPluginManager().callEvent(new PlayerBedEnterEvent(p, p.getWorld().getBlockAt(p.getBedLocation()), BedEnterResult.OK));
			}
		}
		
		//register all the spigot OnePlayerSleep.events I need
		getServer().getPluginManager().registerEvents(new OnBedExplode(configs.config), this);
		getServer().getPluginManager().registerEvents(new OnPlayerBedEnter(this, configs.config), this);
		getServer().getPluginManager().registerEvents(new OnPlayerBedLeave(this, configs.config), this);
		getServer().getPluginManager().registerEvents(new OnPlayerJoin(this, configs.config), this);
		getServer().getPluginManager().registerEvents(new OnPlayerQuit(this, configs.config), this);
		getServer().getPluginManager().registerEvents(new OnPlayerChangeWorld(this, configs.config), this);
		getServer().getPluginManager().registerEvents(new OnWeatherChange(configs.config), this);
		getServer().getPluginManager().registerEvents(new OnNightSkip(this), this);
		getServer().getPluginManager().registerEvents(this, this);
		
		if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			new PAPI_expansion(this).register();
		}

		int pluginId = 7096; // <-- Replace with the id of your plugin!
		new Metrics(this, pluginId);

		//fix player counts on reload
		boolean messageFromSleepingIgnored = (Boolean) configs.config.getConfigValue("messageFromSleepingIgnored", true);
		for (org.bukkit.World w : Bukkit.getWorlds()) {
			this.numPlayers.put(w.getUID(), 0L);
			for (Player p : w.getPlayers()){
				if(p.hasPermission("sleep.ignore")) continue;
				this.numPlayers.put(w.getUID(), this.numPlayers.get(w.getUID())+1);
				if(!messageFromSleepingIgnored && p.isSleepingIgnored()) continue;
				if(p.isSleeping()){
					if(!this.sleepingPlayers.containsKey(w.getUID())){
						this.sleepingPlayers.put(w.getUID(), new HashSet<>());
					}
					this.sleepingPlayers.get(w.getUID()).add(p);
				}
			}
			if(numPlayers.get(w.getUID()) == 0) numPlayers.remove(w.getUID());
		}
	}
	
	@Override
	public void onDisable() {
		
	}
	
	public Config getPluginConfig() {
		return configs.config;
	}
}
