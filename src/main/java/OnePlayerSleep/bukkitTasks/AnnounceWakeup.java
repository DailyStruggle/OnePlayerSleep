package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config;
import OnePlayerSleep.types.Message;

import java.util.List;

//set up message threads for all relevant players
public class AnnounceWakeup extends BukkitRunnable{
	private OnePlayerSleep plugin;
	private Config config;
	private String playerName;
	private Message msg;
	private World world;

	public AnnounceWakeup(OnePlayerSleep plugin, Config config, String playerName, Message msg, World world) {
		this.plugin = plugin;
		this.config = config;
		this.playerName = playerName;
		this.msg = msg;
		this.world = world;
	}
	
	@Override
	public void run() {
		Boolean messageToSleepingIgnored = config.config.getBoolean("messageToSleepingIgnored");

		//format and ship it
		List<String> worldNames = this.config.worlds.getConfigurationSection(this.world.getName()).getStringList("sendTo");
		for (String worldName : worldNames) {

			for(Player p : Bukkit.getWorld(worldName).getPlayers()) {
				if (!messageToSleepingIgnored && p.isSleepingIgnored()) continue;
				if (p.hasPermission("sleep.ignore")) continue;
				String wakeupMsg = config.fillPlaceHolders(
						this.msg.wakeup,
						p.getName());
				if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
					wakeupMsg = PlaceholderAPI.setPlaceholders(p, wakeupMsg);
				p.sendMessage(wakeupMsg);
			}
		}
	}
}
