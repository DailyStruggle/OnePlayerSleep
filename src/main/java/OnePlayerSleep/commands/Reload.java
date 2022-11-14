package OnePlayerSleep.commands;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.tools.SendMessage;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import OnePlayerSleep.tools.Config.Config;

public class Reload implements CommandExecutor {
	private final OnePlayerSleep plugin;
	private final Config config;
	
	public Reload(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(sender.hasPermission("sleep.reload"))
		{
			String str = this.config.getLog("reloading");
			SendMessage.sendMessage(Bukkit.getConsoleSender(),str);
			if(sender instanceof Player) {
				if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) str = PlaceholderAPI.setPlaceholders((Player)sender, str);
				SendMessage.sendMessage(sender,str);
			}
			
			this.config.refreshConfigs();
			
			str = this.config.getLog("reloaded");
			SendMessage.sendMessage(Bukkit.getConsoleSender(),str);
			if(sender instanceof Player) {
				if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) str = PlaceholderAPI.setPlaceholders((Player)sender, str);
				SendMessage.sendMessage(sender,str);
			}
			
			return true;
		}
		return false;
	}
}
