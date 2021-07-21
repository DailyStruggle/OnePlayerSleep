package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config;
import OnePlayerSleep.types.Message;

import java.util.logging.Level;

//set up message threads for all relevant players
public class AnnounceSleep extends BukkitRunnable{
	private OnePlayerSleep plugin;
	private Config config;
	private String playerName;
	private World world;
	private Message message;

	public AnnounceSleep(OnePlayerSleep plugin, Config config, String playerName, World world) {
		this.plugin = plugin;
		this.config = config;
		this.playerName = playerName;
		this.world = world;
		this.message = null;
	}

	public AnnounceSleep(OnePlayerSleep plugin, Config config, String playerName, World world, Message message) {
		this.plugin = plugin;
		this.config = config;
		this.playerName = playerName;
		this.world = world;
		this.message = message;
	}
	
	@Override
	public void run() {
		Boolean perPlayer = (Boolean) config.getConfigValue("randomPerPlayer", false);
		Boolean messageToSleepingIgnored = (Boolean) config.getConfigValue("messageToSleepingIgnored", true);

		if(this.message == null) {
			this.message = this.config.pickRandomMessage(this.world, playerName);
		}

		for (String worldName : this.config.getMsgToWorlds(this.world.getName())) {
			World w = Bukkit.getWorld(worldName);

			for (Player p : w.getPlayers()) {
				if(!messageToSleepingIgnored && p.isSleepingIgnored()) continue;

				//skip if has perm
				if(p.hasPermission("sleep.ignore")) continue;
				
				if(perPlayer) {
					new SendMessage(this.plugin, this.config, this.world, this.playerName, p).runTaskAsynchronously(this.plugin);
				}
				else {
					new SendMessage(this.plugin, this.config, this.world, this.playerName, p,  this.message).runTaskAsynchronously(this.plugin);
				}
			}
		}
		if(this.config.logMessages() || this.playerName == this.config.getServerName()) {
			Bukkit.getLogger().log(Level.INFO, this.message.msg.getText());
		}
	}
}
