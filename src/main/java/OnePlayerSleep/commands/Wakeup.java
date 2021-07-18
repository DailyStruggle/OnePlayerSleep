package OnePlayerSleep.commands;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.bukkitTasks.AnnounceWakeup;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import OnePlayerSleep.tools.Config;
import OnePlayerSleep.types.Message;
import org.bukkit.event.player.PlayerBedLeaveEvent;

import java.util.logging.Level;

import java.util.*;
import java.util.regex.Pattern;

public class Wakeup implements CommandExecutor {
	private static final Pattern dims = Pattern.compile("_nether|_the_end", Pattern.CASE_INSENSITIVE);
	OnePlayerSleep plugin;
	Config config;
	
	public Wakeup(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(!sender.hasPermission("sleep.wakeup")) return false;

		Boolean isPlayer = (sender instanceof Player);
		String playerName = isPlayer
				? sender.getName()
				: this.config.getServerName();
		String myWorldName = isPlayer
				? ((Player)sender).getWorld().getName()
				: config.getServerWorldName();

		Boolean cantKickAPlayer = false;

		String cmdWorldName;
		Message msg;
		switch(args.length) {
			case 0: { //no args, use last message given to player
				if(isPlayer) msg = this.plugin.wakeData.get(sender);
				else msg = config.pickRandomMessage(Bukkit.getWorld(myWorldName), playerName);
				cmdWorldName = (isPlayer) ?
						((Player)sender).getWorld().getName() :
						this.config.getServerWorldName();
				break;
			}
			case 1: { //only world name
				cmdWorldName = args[0];
				msg = config.pickRandomMessage(Bukkit.getWorld(args[0]), playerName);
				break;
			}
			default: { //world name + message name
				cmdWorldName = args[0];
				Set<String> listNames = this.config.getMessageListNames();
				Integer delimiterIdx = args[1].indexOf('.');
				if(delimiterIdx > 0) {
					String listName = args[1].substring(0,delimiterIdx);
					String msgName = args[1].substring(delimiterIdx+1);

					if(!listNames.contains(listName))
					{
						sender.sendMessage(this.config.getLog("invalidList", listName));
						return true;
					}

					msg = this.config.getMessage(listName, msgName, playerName);
					if(msg == null)
					{
						sender.sendMessage(this.config.getLog("invalidMsg", msgName));
						return true;
					}
				}
				else {
					if(!listNames.contains(args[1]))
					{
						sender.sendMessage(this.config.getLog("invalidList", args[1]));
						return true;
					}
					msg = this.config.pickRandomMessage(args[1], playerName);
					msg.setWorld(cmdWorldName);
				}
				break;
			}
		}

		if(msg == null) msg = config.pickRandomMessage(Bukkit.getWorld(myWorldName), playerName);

		//check if user should have a say in this
		if((!config.getMsgToWorlds(myWorldName).contains(cmdWorldName))&&(!sender.hasPermission("sleep.global"))) {
			sender.sendMessage(this.config.getLog("noGlobalPerms", cmdWorldName));
			return true;
		}

		//check if there's anything to do
		List<String> syncWorlds = config.getSyncWorlds(myWorldName);
		Integer numSleeping = 0;
		for(String worldName : syncWorlds) {
			World world = Bukkit.getWorld(worldName);
			if(	!this.plugin.sleepingPlayers.containsKey(world) ) continue;
			numSleeping += this.plugin.sleepingPlayers.get(world).size();
		}

		if(numSleeping == 0) {
			String onNoPlayersSleeping = config.getLog("onNoPlayersSleeping");
			if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
				onNoPlayersSleeping = PlaceholderAPI.setPlaceholders((Player)sender, onNoPlayersSleeping);
			sender.sendMessage(onNoPlayersSleeping);
			return true;
		}

		for(String worldName : syncWorlds) {
			World world = Bukkit.getWorld(worldName);
			if(	!this.plugin.sleepingPlayers.containsKey(world) ) continue;
			HashSet<Player> players = this.plugin.sleepingPlayers.get(world);
			//attempt to wake each player
			for(Player p : players) {
				if(!p.isSleeping()) {
					players.remove(p);
					continue;
				}
				if(p.hasPermission("sleep.bypass")) {
					cantKickAPlayer = true;
					continue;
				}
				p.wakeup(true);
			}
		}
		
		//if failed to kick everyone
		if(cantKickAPlayer) {
			String send;
			if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
				send = PlaceholderAPI.setPlaceholders((Player)sender, msg.cantWakeup);
			else send = msg.cantWakeup;
			sender.sendMessage(send);
			return true;
		}

		//send a wakeup message
		for(String worldName : this.config.getMsgToWorlds(myWorldName)) {
			World world = Bukkit.getWorld(worldName);
			new AnnounceWakeup(this.plugin, this.config, playerName, msg, world).runTaskAsynchronously(this.plugin);
		}

		return true;
	}
}
