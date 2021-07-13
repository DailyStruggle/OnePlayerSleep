package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config;
import OnePlayerSleep.types.Message;

import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

//set up message threads for all relevant players
public class AnnounceSleep extends BukkitRunnable{
	private static final Pattern dims = Pattern.compile("_nether|_the_end", Pattern.CASE_INSENSITIVE);
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
		Boolean perPlayer = config.config.getBoolean("randomPerPlayer");
		Boolean messageToSleepingIgnored = config.config.getBoolean("messageToSleepingIgnored", true);
		List<String> worldNames = this.config.getMsgToWorlds(this.world.getName());

		if(this.message == null) {
			this.message = this.config.pickRandomMessage(this.world, playerName);
		}

		for (String worldName : worldNames) {
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
