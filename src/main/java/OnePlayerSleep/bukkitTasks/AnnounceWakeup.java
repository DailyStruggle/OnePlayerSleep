package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config.Config;
import OnePlayerSleep.types.MessageImpl;

//set up message threads for all relevant players
public class AnnounceWakeup extends BukkitRunnable{
	private final Config config;
	private final String playerName;
	private final MessageImpl msg;
	private final World world;

	public AnnounceWakeup(OnePlayerSleep plugin, Config config, String playerName, MessageImpl msg, World world) {
		this.config = config;
		this.playerName = playerName;
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
						this.msg.wakeup,
						p.getName());
				if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
					wakeupMsg = PlaceholderAPI.setPlaceholders(p, wakeupMsg);
				p.sendMessage(wakeupMsg);
			}
		}
	}
}
