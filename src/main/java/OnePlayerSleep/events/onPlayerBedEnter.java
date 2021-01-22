package OnePlayerSleep.events;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.bukkitTasks.OnSleepChecks;
import OnePlayerSleep.tools.LocalPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import OnePlayerSleep.tools.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class onPlayerBedEnter implements Listener {
	private OnePlayerSleep plugin;
	private Config config;
	private Map<Player,Long> lastTme = new HashMap<Player,Long>();

	public onPlayerBedEnter(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}
	
	@EventHandler
	public void onPlayerBedEnter(PlayerBedEnterEvent event) {
		//skip if player needs to be ignored by the plugin
		if(config.config.getBoolean("useSleepingIgnored", true)
				&& event.getPlayer().isSleepingIgnored()) return;

		if(event.getPlayer().hasPermission("sleep.ignore")) return;

		Boolean isNether = event.getPlayer().getWorld().getName().contains("_nether");
		Boolean isEnd = event.getPlayer().getWorld().getName().contains("_the_end");
		if((isNether || isEnd) && !config.config.getBoolean("doOtherDimensions", false)) return;

		if (event.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_HERE) {
			event.setUseBed(Event.Result.ALLOW);
		}

		if(
			(	   event.getPlayer().getWorld().getTime() < config.config.getInt("startTime")
				|| event.getPlayer().getWorld().getTime() > config.config.getInt("stopTime"))
			&& !event.getPlayer().getWorld().hasStorm()
			) {
			String msg = config.messages.getString("badTimeMessage");
			msg = LocalPlaceholders.fillPlaceHolders(
					msg,
					event.getPlayer(),
					this.config);
			event.getPlayer().sendMessage(msg);
			event.setCancelled(true);
			return;
		}
		event.setUseBed(Event.Result.ALLOW);



		//cooldown logic
		Long currentTime = System.currentTimeMillis();
		if(		this.lastTme.containsKey(event.getPlayer()) &&
				currentTime < this.lastTme.get(event.getPlayer()) + config.config.getLong("sleepCooldown")) {
			event.setCancelled(true);
			String msg = config.messages.getString("cooldownMessage");
			msg = LocalPlaceholders.fillPlaceHolders(
					msg,
					event.getPlayer(),
					this.config);
			event.getPlayer().sendMessage(msg);
			return;
		}
		if(this.lastTme.containsKey(event.getPlayer())) 
			this.lastTme.remove(event.getPlayer());
		this.lastTme.put(event.getPlayer(), currentTime);
		
		//do other relevant checks asynchronously
		new OnSleepChecks(this.plugin, this.config, event.getPlayer()).runTaskAsynchronously(plugin);
		this.plugin.numSleepingPlayers++;
	}
}
