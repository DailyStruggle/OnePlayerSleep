package com.minekartastudio.kartasoloslumber.bukkitTasks;

import com.minekartastudio.kartasoloslumber.KartaSoloSlumber;
import com.minekartastudio.kartasoloslumber.tools.Config.Config;
import com.minekartastudio.kartasoloslumber.tools.SendMessage;
import com.minekartastudio.kartasoloslumber.types.MessageImpl;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

//set up message threads for all relevant players
public class AnnounceCancel extends BukkitRunnable{
	private final Config config;
	private final MessageImpl msg;
	private final World world;

	public AnnounceCancel(Config config, MessageImpl msg, World world) {
		this.config = config;
		this.msg = msg;
		this.world = world;
	}
	
	@Override
	public void run() {
		Boolean messageToSleepingIgnored = (Boolean) config.getConfigValue("messageToSleepingIgnored",true);

		//format and ship it
		for (String worldName : this.config.getMsgToWorlds(this.world.getName())) {
			World world = Bukkit.getWorld(worldName);
			if(world == null) continue;
			for(Player p : world.getPlayers()) {
				if (!messageToSleepingIgnored && p.isSleepingIgnored()) continue;
				if (p.hasPermission("sleep.ignore")) continue;
				String wakeupMsg = config.fillPlaceHolders(
						this.msg.cancel,
						p.getName());
				if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
					wakeupMsg = PlaceholderAPI.setPlaceholders(p, wakeupMsg);
				SendMessage.sendMessage(p,wakeupMsg);
			}
		}
	}
}
