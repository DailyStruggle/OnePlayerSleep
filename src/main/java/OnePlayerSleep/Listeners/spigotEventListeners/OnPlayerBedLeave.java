package OnePlayerSleep.Listeners.spigotEventListeners;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.bukkitTasks.AnnounceCancel;
import OnePlayerSleep.bukkitTasks.AnnounceWakeup;
import OnePlayerSleep.bukkitTasks.ClearWeather;
import OnePlayerSleep.types.MessageImpl;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import OnePlayerSleep.tools.Config.Config;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public class OnPlayerBedLeave implements Listener {
	private final OnePlayerSleep plugin;
	private final Config config;
	
	public OnPlayerBedLeave(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}
	
	@EventHandler
	public void onPlayerBedLeave (PlayerBedLeaveEvent event) {
		Boolean messageFromSleepingIgnored = (Boolean) config.getConfigValue("messageFromSleepingIgnored", true);
		if(!messageFromSleepingIgnored && event.getPlayer().isSleepingIgnored()) return;
		if(event.getPlayer().hasPermission("sleep.ignore")) return;
		long dt = System.currentTimeMillis() - plugin.wakeupCommandTime.get();
		long dt2 = System.currentTimeMillis() - plugin.clearWeatherTime.getOrDefault(event.getPlayer().getWorld().getUID(),new AtomicLong(0L)).get();
		dt = Math.min(dt,dt2);

		//remove player from sleep lookup table
		World myWorld = event.getPlayer().getWorld();
		String myWorldName = myWorld.getName();
		this.config.checkWorldExists(myWorldName);
		if(		this.plugin.sleepingPlayers.containsKey(myWorld.getUID()) &&
				this.plugin.sleepingPlayers.get(myWorld.getUID()).contains(event.getPlayer())){
			this.plugin.sleepingPlayers.get(myWorld.getUID()).remove(event.getPlayer());
			if(this.plugin.sleepingPlayers.get(myWorld.getUID()).size() == 0) this.plugin.sleepingPlayers.remove(myWorld.getUID());
		}

		long numSleepingPlayers = 0L;
		for(String theirWorldName : this.config.getSyncWorlds(event.getBed().getWorld().getName()))
		{
			this.config.checkWorldExists(theirWorldName);
			World world = Bukkit.getWorld(theirWorldName);
			if(world == null) continue;
			if(this.plugin.sleepingPlayers.containsKey(world.getUID()))
				numSleepingPlayers += this.plugin.sleepingPlayers.get(world.getUID()).size();
		}

		if(numSleepingPlayers == 0) {
			int increment = 2 * (Integer)config.getConfigValue("increment",150);
			for(String worldName : this.config.getSyncWorlds(myWorldName)) {
				World world = Bukkit.getWorld(worldName);
				if(world == null) continue;
				if( this.plugin.doSleep.containsKey(world.getUID())) {
					this.plugin.doSleep.get(world.getUID()).cancel();
					if(		dt>100
							&& (myWorld.getTime() > this.config.getStartTime(myWorld.getName())+increment
							&& myWorld.getTime() < this.config.getStopTime(myWorld.getName())-increment)
							&& myWorld.getTime() < 22000
					) {
						MessageImpl msg = this.plugin.wakeData.get(event.getPlayer().getUniqueId());
						new AnnounceCancel(this.config, msg, world).runTaskAsynchronously(this.plugin);
					}
					else {
						new ClearWeather(myWorld).runTask(this.plugin);
					}
				}
			}
		}
		else if((myWorld.getTime() < this.config.getStartTime(myWorld.getName())
				|| myWorld.getTime() > this.config.getStopTime(myWorld.getName()))
				&& !myWorld.hasStorm()) {
			for(String worldName : this.config.getSyncWorlds(myWorldName)) {
				World w = Bukkit.getWorld(worldName);
				if(w == null) continue;
				if(w.getTime()!= myWorld.getTime()) w.setTime(myWorld.getTime());
				UUID uuid = w.getUID();
				this.plugin.doSleep.get(uuid).cancel();
				this.plugin.clearWeather.get(uuid).cancel();
				this.plugin.clearWeather.remove(uuid);
				this.plugin.clearWeather.put(uuid, new ClearWeather(w).runTask(this.plugin));
				if((Boolean)this.config.getConfigValue("resetAllStatistics", true)) {
					for (Player p : w.getPlayers()) {
						if(p.hasPermission("sleep.ignore")) continue;
						p.setStatistic(Statistic.TIME_SINCE_REST, 0);
					}
				}
			}
		}
	}
}
