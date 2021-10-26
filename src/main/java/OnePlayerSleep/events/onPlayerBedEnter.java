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
	public void OnPlayerBedEnter(PlayerBedEnterEvent event) {
		//skip if player needs to be ignored by the plugin
		if(		(!(Boolean)config.getConfigValue("messageFromSleepingIgnored", false))
				&& event.getPlayer().isSleepingIgnored()) return;
		if(event.getPlayer().hasPermission("sleep.ignore")) return;

		World myWorld = event.getBed().getWorld();
		this.config.checkWorldExists(myWorld.getName());

		//check config to prevent explosion in a dimension, otherwise we can't do anything there
		if(		(myWorld.getEnvironment().equals(World.Environment.NETHER) || myWorld.getEnvironment().equals(World.Environment.THE_END))
				&& !this.config.getCancelBedExplode(myWorld.getName())		)
		{
			return;
		}

		//check time
		if(		(myWorld.getTime() < this.config.getStartTime(myWorld.getName())
				|| myWorld.getTime() > this.config.getStopTime(myWorld.getName()))
				&& !myWorld.hasStorm()		)
		{
			String msg = config.getLog("badTimeMessage");
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
				currentTime < this.lastTme.get(event.getPlayer()) + (Integer)config.getConfigValue("sleepCooldown",2000)) {
			event.setCancelled(true);
			String msg = config.getLog("cooldownMessage");
			msg = this.config.fillPlaceHolders(
					msg,
					event.getPlayer().getName());
			event.getPlayer().sendMessage(msg);
			event.setCancelled(true);
			return;
		}

		this.lastTme.put(event.getPlayer(), currentTime);

		if(event.getBedEnterResult().equals(PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_HERE)) event.setUseBed(Event.Result.ALLOW);

		//do other relevant checks asynchronously
		new OnSleepChecks(this.plugin, this.config, event.getPlayer(), myWorld).runTaskAsynchronously(plugin);
	}
}
