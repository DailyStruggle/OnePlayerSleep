package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.tools.SendMessage;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config.Config;
import OnePlayerSleep.types.MessageImpl;

import java.util.UUID;

//set up message threads for all relevant players
public class AnnounceSleep extends BukkitRunnable{
	private final OnePlayerSleep plugin;
	private final Config config;
	private final String playerName;
	private final World world;
	private MessageImpl message;
	Boolean hasPAPI;


	public AnnounceSleep(OnePlayerSleep plugin, Config config, String playerName, World world) {
		this.plugin = plugin;
		this.config = config;
		this.playerName = playerName;
		this.world = world;
		this.message = null;
		this.hasPAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
	}

	public AnnounceSleep(OnePlayerSleep plugin, Config config, String playerName, World world, MessageImpl message) {
		this.plugin = plugin;
		this.config = config;
		this.playerName = playerName;
		this.world = world;
		this.message = message;
		this.hasPAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
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
			if(w == null) continue;
			for (Player p : w.getPlayers()) {
				if(!messageToSleepingIgnored && p.isSleepingIgnored()) continue;

				//skip if has perm
				if(p.hasPermission("sleep.ignore")) continue;
				
				if(perPlayer) {
					new SendMessageRunable(this.plugin, this.config, this.world, this.playerName, p).runTaskAsynchronously(this.plugin);
				}
				else {
					new SendMessageRunable(this.plugin, this.config, this.world, this.playerName, p,  this.message).runTaskAsynchronously(this.plugin);
				}
			}
		}
		if(this.config.logMessages() || this.playerName.equals(this.config.getServerName())) {
			String consoleMsg = this.message.msg.getText();
			consoleMsg = config.fillPlaceHolders(consoleMsg,playerName);
			Player player = Bukkit.getPlayer(playerName);
			UUID playerID;
			if(player!=null) playerID = player.getUniqueId();
			else  {
				if(playerName.equals(config.getServerName())) playerID = new UUID(0,0);
				else return;
			}
			if(hasPAPI) consoleMsg = PlaceholderAPI.setPlaceholders(Bukkit.getOfflinePlayer(playerID),consoleMsg);
			SendMessage.sendMessage(Bukkit.getConsoleSender(),consoleMsg);
		}
	}
}
