package OnePlayerSleep.commands;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.bukkitTasks.AnnounceSleep;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import OnePlayerSleep.tools.Config;
import OnePlayerSleep.types.Message;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class Test implements CommandExecutor {
	private OnePlayerSleep plugin;
	private Config config;

	public Test(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(!command.getName().equalsIgnoreCase("sleep test")) return true;

		Boolean isPlayer = sender instanceof Player;
		String playerName = isPlayer
				? sender.getName()
				: this.config.getServerName();
		String worldName = isPlayer
				? ((Player)sender).getWorld().getName()
				: this.config.getServerWorldName();

		Message[] res;
		switch(args.length) {
			case 0: //if just /sleep test, pick 1 random message from player's world
			{
				res = new Message[1];
				World world = Bukkit.getWorld(worldName);
				if(world != null) {
					res[0] = this.plugin.getPluginConfig().pickRandomMessage(world, playerName);
				}
				else {
					sender.sendMessage(this.config.getLog("invalidWorld", worldName));
				}
				break;
			}
			case 1: //if world arg, pick 1 random message from the world's list
			{
				res = new Message[1];
				World w = Bukkit.getWorld(args[0]);
				if(w == null) {
					sender.sendMessage(this.config.getLog("invalidWorld", worldName));
					return true;
				}
				res[0] = this.plugin.getPluginConfig().pickRandomMessage(w, playerName);
				break;
			}
			default: //if world arg and message args
			{
				World w = Bukkit.getWorld(args[0]);
				if(w == null) {
					sender.sendMessage(this.config.getLog("invalidWorld", worldName));
					return true;
				}
				worldName = args[0];

				res = new Message[args.length-1];
				Set<String> messageListNames = this.config.getMessageListNames();
				for( int i = 0; i<res.length; i++)
				{
					//detect delimiter exists and where
					String listName;
					Integer delimiterIdx = args[i+1].indexOf('.');
					if(delimiterIdx > 0)
					{
						listName = args[i+1].substring(0, delimiterIdx);
						String msgName = args[i+1].substring(delimiterIdx+1);

						if(!messageListNames.contains(listName))
						{
							sender.sendMessage(this.config.getLog("invalidList", listName));
							continue;
						}

						Message msg = this.config.getMessage(listName,msgName,playerName);
						if(msg==null)
						{
							sender.sendMessage(this.config.getLog("invalidMsg", msgName));
							continue;
						}

						res[i] = this.config.getMessage(listName, msgName, playerName);
					}
					else
					{
						listName = args[i+1];
						if(!messageListNames.contains(listName))
						{
							sender.sendMessage(this.config.getLog("invalidList", listName));
							continue;
						}
						res[i] = this.config.pickRandomMessage(listName,playerName);
					}
					res[i].setWorld(args[0]);
				}
			}
		}

		for( Message message : res) {
			if(message == null) continue;
			World world = Bukkit.getWorld(message.worldName);
			if(world == null) {
				sender.sendMessage(this.config.getLog("invalidWorld", message.worldName));
				continue;
			}

			List<String> msgToWorldNames = this.config.getMsgToWorlds(worldName);
			for(String msgToWorldName : msgToWorldNames) {
				World msgToWorld = Bukkit.getWorld(msgToWorldName);
				if(msgToWorld == null) {
					sender.sendMessage(this.config.getLog("invalidWorld", message.worldName));
					continue;
				}

				new AnnounceSleep(this.plugin,this.config,playerName,msgToWorld,message).runTaskAsynchronously(this.plugin);
			}
		}
		return true;
	}
	
}
