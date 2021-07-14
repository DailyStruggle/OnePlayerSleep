package OnePlayerSleep.events;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.bukkitTasks.OnSleepChecks;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import OnePlayerSleep.tools.Config;

import java.util.HashMap;
import java.util.Map;

/*
Class for filtering bed entry and setting up an async task for filtering world behaviors
 */
public class onPlayerBedEnter implements Listener {
	private final OnePlayerSleep plugin;
	private final Config config;
	private final Map<Player,Long> lastTme = new HashMap<>();

	public onPlayerBedEnter(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}
	
	@EventHandler
	public void onPlayerBedEnter(PlayerBedEnterEvent event) {
		//skip if player needs to be ignored by the plugin
		if(config.config.getBoolean("messageFromSleepingIgnored", false)
				&& event.getPlayer().isSleepingIgnored()) return;
		if(event.getPlayer().hasPermission("sleep.ignore")) return;

		World myWorld = event.getBed().getWorld();

		//check config to prevent explosion in a dimension, otherwise we can't do anything there
		if(		(myWorld.getEnvironment() == World.Environment.NETHER || myWorld.getEnvironment() == World.Environment.THE_END)
				&& !this.config.worlds.getConfigurationSection(event.getBed().getWorld().getName()).getBoolean("cancelBedExplode",false)		)
		{
			return;
		}

		//check time
		if(		(myWorld.getTime() < config.config.getInt("startTime")
				|| myWorld.getTime() > config.config.getInt("stopTime"))
				&& !myWorld.hasStorm()		)
		{
			String msg = config.messages.getString("badTimeMessage");
			msg = this.config.fillPlaceHolders(
					msg,
					event.getPlayer().getName());
			event.getPlayer().sendMessage(msg);
			event.setCancelled(true);
			return;
		}

		//cooldown logic
		Long currentTime = System.currentTimeMillis();
		if(		this.lastTme.containsKey(event.getPlayer()) &&
				currentTime < this.lastTme.get(event.getPlayer()) + config.config.getLong("sleepCooldown")) {
			event.setCancelled(true);
			String msg = config.messages.getString("cooldownMessage");
			msg = this.config.fillPlaceHolders(
					msg,
					event.getPlayer().getName());
			event.getPlayer().sendMessage(msg);
			event.setCancelled(true);
			return;
		}

		event.setUseBed(Event.Result.ALLOW);

		this.lastTme.put(event.getPlayer(), currentTime);
		
		//do other relevant checks asynchronously
		new OnSleepChecks(this.plugin, this.config, event.getPlayer(), myWorld).runTaskAsynchronously(plugin);
	}
}
