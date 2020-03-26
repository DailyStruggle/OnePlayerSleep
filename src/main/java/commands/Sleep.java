package commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import OnePlayerSleep.OnePlayerSleep;

public class Sleep implements CommandExecutor {
	private OnePlayerSleep plugin;
	
	public Sleep(OnePlayerSleep plugin) {
		this.plugin = plugin;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(command.getName().equalsIgnoreCase("sleep")) {
			if(args.length > 0)
			{
				String cmd = command.toString();
				for(int i = 0; i<args.length; i++)
					cmd = cmd + args[i].toString();
				if(		args.length == 1 )
					if(args[0].equalsIgnoreCase("reload") && sender.hasPermission("sleep.reload"))
						plugin.getCommand("sleep reload").execute(sender, label, args);
					else if( args[0].equalsIgnoreCase("wakeup") && sender.hasPermission("sleep.wakeup") ) 
						plugin.getCommand("sleep wakeup").execute(sender, label, args);
					else
						return false;
				return true;
			}
			else
			{
				if(sender.hasPermission("sleep.see"))
				{
					return true;
				}
				else
					return false;
			}
		}
		return true;
		
	}
}
