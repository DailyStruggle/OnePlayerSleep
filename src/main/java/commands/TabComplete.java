package commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import tools.Config;

public class TabComplete implements TabCompleter {
	private Map<String,String> subCommands = new HashMap<String,String>();
	
	private Config config;
	
	public TabComplete(Config config) {
		//load commands and permission nodes into map
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

		switch(args.length){
			case 1: {
				//fill list based on command permission nodes
				List<String> res = new ArrayList<String>();
				for (Map.Entry<String, String> entry : subCommands.entrySet())
					if(sender.hasPermission(entry.getValue())) res.add(entry.getKey());
				return res;
			}
			default: {
				switch(args[0]){
					case "test":{
						return this.config.messageNames;
					}
					default: {
						return null;
					}
				}
			}
		}

	}
}
