package OnePlayerSleep.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import OnePlayerSleep.tools.Config;

import java.util.*;

public class TabComplete implements TabCompleter {
	private Map<String,String> subCommands = new HashMap<String,String>();
	
	private Config config;
	
	public TabComplete(Config config) {
		//load OnePlayerSleep.commands and permission nodes into map
		subCommands.put("reload","sleep.reload");
		subCommands.put("wakeup","sleep.wakeup");
		subCommands.put("test","sleep.test");
		subCommands.put("help","sleep.help");
		this.config = config;
	}
	
	@Override
	public List<String> onTabComplete(CommandSender sender, Command command,
			String alias, String[] args) {
		if(!sender.hasPermission("sleep.see")) return null;

		List<String> res = null;

		switch(args.length){
			case 1: { //sleep subcommands
				res = new ArrayList<>();
				List<String> subCom = new ArrayList<String>();
				//fill list based on command permission nodes
				for (Map.Entry<String, String> entry : subCommands.entrySet()) {
					if (sender.hasPermission(entry.getValue()))
						subCom.add(entry.getKey());
				}
				StringUtil.copyPartialMatches(args[0],subCom,res);
				break;
			}
			case 2: { //if either test or wakeup, first arg should be a world name
				if( 	(args[0].equalsIgnoreCase("test")  && sender.hasPermission("sleep.test") ) ||
						(args[0].equalsIgnoreCase("wakeup")  && sender.hasPermission("sleep.wakeup") ) ) {
					res = new ArrayList<>();
					List<String> worldNames = new ArrayList<>();
					for (World w : Bukkit.getWorlds()) {
						worldNames.add(w.getName());
					}
					StringUtil.copyPartialMatches(args[args.length - 1], worldNames, res);
				}
				break;
			}
			default: { //subsequent args are list.message
				if( 	(args[0].equalsIgnoreCase("test")  && sender.hasPermission("sleep.test") ) ||
						(args[0].equalsIgnoreCase("wakeup")  && sender.hasPermission("sleep.wakeup") ) ) {
					res = new ArrayList<>();
					String worldName = null;
					if(sender instanceof Player) worldName = ((Player)sender).getWorld().getName();

					List<String> names = new ArrayList<>();

					Integer delimiterIdx = args[args.length-1].indexOf('.');
					if(delimiterIdx > 0) {
						String listName = args[args.length-1].substring(0,delimiterIdx);
						if(!this.config.messages.getConfigurationSection("messages").contains(listName)) return res;
						names.addAll(this.config.getMessageNames(listName));
						for(int i = 0; i < names.size(); i++)
						{
							names.set(i,listName + "." + names.get(i));
						}
					}
					else
					{
						names.addAll(this.config.messages.getConfigurationSection("messages").getKeys(false).stream().toList());
					}

					StringUtil.copyPartialMatches(args[args.length - 1], names, res);
				}
			}
		}
		return res;
	}
}
