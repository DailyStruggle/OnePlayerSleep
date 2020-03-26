package commands;

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import OnePlayerSleep.OnePlayerSleep;
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
			if(sender instanceof Player) sender.sendMessage(str);
			
			Config config = plugin.getPluginConfig();
			config.refreshConfigs();
			config.checkConfigs();
			
			plugin.sleepingPlayers.clear();
			for(World w : Bukkit.getWorlds()) {
				plugin.sleepingPlayers.put(w, new ArrayList<Player>());
			}
			
			return true;
		}
		return false;
	}
}
