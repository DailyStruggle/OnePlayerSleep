package commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import OnePlayerSleep.OnePlayerSleep;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class Sleep implements CommandExecutor {
	private OnePlayerSleep plugin;
	
	public Sleep(OnePlayerSleep plugin) {
		this.plugin = plugin;
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
				if(		args.length == 1 )
					if(args[0].equalsIgnoreCase("reload") && sender.hasPermission("sleep.reload"))
						plugin.getCommand("sleep reload").execute(sender, label, args);
					else if( args[0].equalsIgnoreCase("wakeup") && sender.hasPermission("sleep.wakeup") ) 
						plugin.getCommand("sleep wakeup").execute(sender, label, args);
					else if( args[0].equalsIgnoreCase("help") && sender.hasPermission("sleep.help") ) 
						plugin.getCommand("sleep help").execute(sender, label, args);
					else
						sender.sendMessage("Sleep subcommand not found");
				return true;
			}
		}
		return true;
		
	}
}
