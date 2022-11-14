package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.tools.Config.Config;
import OnePlayerSleep.tools.SendMessage;
import OnePlayerSleep.types.MessageImpl;
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
			for(Player p : Bukkit.getWorld(worldName).getPlayers()) {
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
