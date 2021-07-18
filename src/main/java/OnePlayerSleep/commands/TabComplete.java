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
import java.util.logging.Level;

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
		String myWorldName = (sender instanceof Player) ?
				((Player)sender).getWorld().getName() :
				this.config.getServerWorldName();

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
					List<String> worldNames;
					if(sender.hasPermission("sleep.global")) {
						worldNames = new ArrayList<>();
						for (World w : Bukkit.getWorlds()) {
							worldNames.add(w.getName());
						}
					}
					else worldNames = this.config.getMsgToWorlds(myWorldName);
					StringUtil.copyPartialMatches(args[args.length - 1], worldNames, res);
				}
				break;
			}
			default: { //subsequent args are list.message
				Set<String> listNames = this.config.getMessageListNames();
				if( 	(args[0].equalsIgnoreCase("test")  && sender.hasPermission("sleep.test") ) ||
						(args[0].equalsIgnoreCase("wakeup")  && sender.hasPermission("sleep.wakeup") ) ) {
					res = new ArrayList<>();
					List<String> names;

					Integer delimiterIdx = args[args.length-1].indexOf('.');
					if(delimiterIdx > 0) {
						String cmdListName = args[args.length-1].substring(0,delimiterIdx);

						if(!listNames.contains(cmdListName)) return res;
						names = new ArrayList<>(this.config.getMessageNames(cmdListName));
						for(int i = 0; i < names.size(); i++)
						{
							names.set(i, cmdListName + "." + names.get(i));
						}
					}
					else
					{
						names = new ArrayList<>(this.config.getMessageListNames());
					}

					StringUtil.copyPartialMatches(args[args.length - 1], names, res);
				}
			}
		}
		return res;
	}
}
