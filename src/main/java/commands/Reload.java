package commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import OnePlayerSleep.OnePlayerSleep;
import me.clip.placeholderapi.PlaceholderAPI;
import tools.Config;

public class Reload implements CommandExecutor {
	private OnePlayerSleep plugin;
	
	public Reload(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
	}
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(sender.hasPermission("sleep.reload"))
		{
			String str = "reloading OnePlayerSleep";
			System.out.println(str);
			if(sender instanceof Player) {
				if(this.plugin.getPluginConfig().hasPAPI()) str = PlaceholderAPI.setPlaceholders((Player)sender, str);
				sender.sendMessage(str);
			}
			
			Config config = plugin.getPluginConfig();
			config.refreshConfigs();
			
			return true;
		}
		return false;
	}
}
