package commands;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import OnePlayerSleep.OnePlayerSleep;

public class Sleep implements CommandExecutor {
	private OnePlayerSleep plugin;
	private Map<String,String> subCommands = new HashMap<String,String>();
	
	public Sleep(OnePlayerSleep plugin) {
		this.plugin = plugin;
		subCommands.put("reload","sleep.reload");
		subCommands.put("wakeup","sleep.wakeup");
		subCommands.put("help","sleep.help");
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(command.getName().equalsIgnoreCase("sleep")) {
			if(args.length == 0) {
				plugin.getCommand("sleep help").execute(sender, label, args);
			}
			else 
			{
				String cmd = command.toString();
				for(int i = 0; i<args.length; i++)
					cmd = cmd + args[i].toString();
				if(		args.length == 1 && subCommands.containsKey(args[0])) {
					if(sender.hasPermission(subCommands.get(args[0]))) {
						plugin.getCommand("sleep " + args[0]).execute(sender, label, args);
						return true;
					}
					else {
						return false;
					}
				}
				if(!subCommands.containsKey(args[0])) {
					sender.sendMessage("invalid Sleep command");
				}
				return true;
			}
		}
		return true;
		
	}
}
