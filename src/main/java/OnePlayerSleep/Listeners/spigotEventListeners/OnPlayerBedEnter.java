package OnePlayerSleep.Listeners.spigotEventListeners;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.bukkitTasks.OnSleepChecks;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import OnePlayerSleep.tools.Config.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/*
Class for filtering bed entry and setting up an async task for filtering world behaviors
 */
public class OnPlayerBedEnter implements Listener {
	private final OnePlayerSleep plugin;
	private final Config config;
	private final Map<Player,Long> lastTme = new HashMap<>();

	public OnPlayerBedEnter(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}
	
	@EventHandler
	public void onPlayerBedEnter(PlayerBedEnterEvent event) {
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

		//cooldown logic
		long currentTime = System.currentTimeMillis();
		if(		this.lastTme.containsKey(event.getPlayer()) &&
				currentTime < this.lastTme.get(event.getPlayer()) + (Integer)config.getConfigValue("sleepCooldown",2000)) {
			event.setCancelled(true);
			String msg = config.getLog("cooldownMessage");
			msg = this.config.fillPlaceHolders(
					msg,
					event.getPlayer().getName());
			try {
				event.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
			} catch (Throwable throwable) {
				event.getPlayer().sendMessage(msg);
			}
			return;
		}

		if(event.getBedEnterResult().equals(PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_NOW)) {
			event.setCancelled(true);
			boolean weatherSleep = Boolean.parseBoolean(this.config.getConfigValue("weatherSleep", false).toString());
			if(weatherSleep && !myWorld.isClearWeather()) {
				event.setUseBed(Event.Result.ALLOW);
				event.getPlayer().sleep(event.getBed().getLocation(), true);
			}
			else if (myWorld.getTime() >= this.config.getStartTime(myWorld.getName())
					&& myWorld.getTime() <= this.config.getStopTime(myWorld.getName())) {
				if (!myWorld.isClearWeather()) {
					event.setUseBed(Event.Result.ALLOW);
					event.getPlayer().sleep(event.getBed().getLocation(), true);
				} else {
					String msg = config.getLog("badTimeMessage");
					msg = this.config.fillPlaceHolders(
							msg,
							event.getPlayer().getName());
					try {
						event.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
					} catch (Throwable throwable) {
						event.getPlayer().sendMessage(msg);
					}
				}
			}
			else {
				String msg = config.getLog("badTimeMessage");
				msg = this.config.fillPlaceHolders(
						msg,
						event.getPlayer().getName());
				try {
					event.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
				} catch (Throwable throwable) {
					event.getPlayer().sendMessage(msg);
				}
			}
			return;
		}

		if(event.getBedEnterResult().equals(PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_HERE)) event.setUseBed(Event.Result.ALLOW);
		else if(!event.getBedEnterResult().equals(PlayerBedEnterEvent.BedEnterResult.OK)) {
			return;
		}

		this.lastTme.put(event.getPlayer(), currentTime);
		event.getPlayer().setPlayerTime(13000,false);

		//do other relevant checks asynchronously
		new OnSleepChecks(this.plugin, this.config, event.getPlayer(), myWorld).runTaskAsynchronously(plugin);
	}
}
