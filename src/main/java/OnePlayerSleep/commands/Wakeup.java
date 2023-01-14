package OnePlayerSleep.commands;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.bukkitTasks.AnnounceWakeup;
import OnePlayerSleep.tools.SendMessage;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import OnePlayerSleep.tools.Config.Config;
import OnePlayerSleep.types.MessageImpl;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Wakeup implements CommandExecutor {
	OnePlayerSleep plugin;
	Config config;
	
	public Wakeup(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
		if(!sender.hasPermission("sleep.wakeup")) return false;

		boolean isPlayer = (sender instanceof Player);
		String playerName = isPlayer
				? sender.getName()
				: this.config.getServerName();
		String myWorldName = isPlayer
				? ((Player)sender).getWorld().getName()
				: config.getServerWorldName();
		this.config.checkWorldExists(myWorldName);

		boolean cantKickAPlayer = false;

		String cmdWorldName = isPlayer
				? ((Player)sender).getWorld().getName()
				: this.config.getServerWorldName();
		if(args.length > 0) cmdWorldName = args[0];
		if(!this.config.checkWorldExists(cmdWorldName)) {
			if(isPlayer) SendMessage.sendMessage(sender,this.config.getLog("invalidWorld", cmdWorldName));
			return true;
		}
		MessageImpl msg;
		World world1 = Bukkit.getWorld(myWorldName);
		if(world1 == null) return true;
		switch(args.length) {
			case 0: { //no args, use last message given to player
				if(isPlayer) msg = this.plugin.wakeData.get(((Player) sender).getUniqueId());
				else msg = config.pickRandomMessage(world1, playerName);
				break;
			}
			case 1: { //only world name
				World world = Bukkit.getWorld(args[0]);
				if(world == null) return true;
				msg = config.pickRandomMessage(world, playerName);
				break;
			}
			default: { //world name + message name
				Set<String> listNames = this.config.getMessageListNames();
				int delimiterIdx = args[1].indexOf('.');
				if(delimiterIdx > 0) {
					String listName = args[1].substring(0,delimiterIdx);
					String msgName = args[1].substring(delimiterIdx+1);

					if(!listNames.contains(listName))
					{
						SendMessage.sendMessage(sender,this.config.getLog("invalidList", listName));
						return true;
					}

					msg = this.config.getMessage(listName, msgName, playerName);
					if(msg == null)
					{
						SendMessage.sendMessage(sender,this.config.getLog("invalidMsg", msgName));
						return true;
					}
				}
				else {
					if(!listNames.contains(args[1]))
					{
						SendMessage.sendMessage(sender,this.config.getLog("invalidList", args[1]));
						return true;
					}
					msg = this.config.pickRandomMessage(args[1], playerName);
					msg.setWorld(cmdWorldName);
				}
				break;
			}
		}

		if(msg == null) msg = config.pickRandomMessage(world1, playerName);

		//check if user should have a say in this
		if((!config.getMsgToWorlds(myWorldName).contains(cmdWorldName))&&(!sender.hasPermission("sleep.global"))) {
			SendMessage.sendMessage(sender,this.config.getLog("noGlobalPerms", cmdWorldName));
			return true;
		}

		//check if there's anything to do
		List<String> syncWorlds = config.getSyncWorlds(myWorldName);
		int numSleeping = 0;
		for(String worldName : syncWorlds) {
			World world = Bukkit.getWorld(worldName);
			if(world==null) continue;
			if(	!this.plugin.sleepingPlayers.containsKey(world.getUID())) continue;
			numSleeping += this.plugin.sleepingPlayers.get(world.getUID()).size();
		}

		if(numSleeping == 0) {
			String onNoPlayersSleeping = config.getLog("onNoPlayersSleeping");
			if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
				assert sender instanceof Player;
				onNoPlayersSleeping = PlaceholderAPI.setPlaceholders((Player)sender, onNoPlayersSleeping);
			}
			SendMessage.sendMessage(sender,onNoPlayersSleeping);
			return true;
		}

		List<UUID> wakeupPlayers = new ArrayList<>();
		for(String worldName : syncWorlds) {
			World world = Bukkit.getWorld(worldName);
			if(world == null) continue;
			if(	!this.plugin.sleepingPlayers.containsKey(world.getUID()) ) continue;
			HashSet<Player> players = this.plugin.sleepingPlayers.get(world.getUID());
			if(players == null) continue;
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
				wakeupPlayers.add(p.getUniqueId());
			}
		}
		
		//if failed to kick everyone
		if(cantKickAPlayer) {
			String send;
			if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
				assert sender instanceof Player;
				send = PlaceholderAPI.setPlaceholders((Player)sender, msg.cantWakeup);
			}
			else send = msg.cantWakeup;
			SendMessage.sendMessage(sender,send);
			return true;
		}

		//send a wakeup message
		plugin.wakeupCommandTime.set(System.currentTimeMillis());
		for (UUID uuid : wakeupPlayers) {
			Player p = Bukkit.getPlayer(uuid);
			if (p != null) p.wakeup(true);
		}

		for(String worldName : this.config.getMsgToWorlds(myWorldName)) {
			World world = Bukkit.getWorld(worldName);
			new AnnounceWakeup(this.plugin, this.config, playerName, msg, world).runTaskAsynchronously(this.plugin);
		}

		return true;
	}
}
