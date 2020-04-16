package OnePlayerSleep.commands;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.bukkitTasks.OnSleepChecks;
import OnePlayerSleep.bukkitTasks.SendMessage;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import OnePlayerSleep.tools.Config;
import OnePlayerSleep.tools.LocalPlaceholders;
import OnePlayerSleep.types.Message;

public class Test implements CommandExecutor {
	private OnePlayerSleep plugin;
	
	public Test(OnePlayerSleep plugin) {
		this.plugin = plugin;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(command.getName().equalsIgnoreCase("sleep test")) {
			if(!(sender instanceof Player)){
				sender.sendMessage("[sleep] only players can use this command!");
				return true;
			}
			Player player = (Player)sender;
			Config config = this.plugin.getPluginConfig();

			Boolean doOtherWorld= config.config.getBoolean("doOtherWorlds");
			Boolean doOtherDim = config.config.getBoolean("doOtherDimensions");
			Boolean perPlayer = config.config.getBoolean("randomPerPlayer");
			Message resMsg = new Message("","","","","",0.0);
			ConfigurationSection worlds = config.messages.getConfigurationSection("worlds");
			String worldName = player.getWorld().getName().replace("_nether","").replace("the_end","");
			if(!worlds.contains(worldName)) {
				worlds.set(worldName, "&a" + worldName);
			}
			worldName = worlds.getString(worldName);
			String dimStr = config.messages.getConfigurationSection("dimensions").getString(player.getWorld().getEnvironment().name());

			new OnSleepChecks(this.plugin, config, player, true).runTaskAsynchronously(this.plugin);

			Message[] res;
			switch(args.length) {
				case 0: {
					//if just /sleep test, pick a random message
					res = new Message[1];
					res[0] = this.plugin.getPluginConfig().pickRandomMessage();
					String global = LocalPlaceholders.fillPlaceHolders(res[0].msg.getText(), player, config);
					String hover = LocalPlaceholders.fillPlaceHolders(res[0].hoverText, player, config);
					res[0] = new Message(res[0].name, global, hover, res[0].wakeup, res[0].cantWakeup, res[0].chance);
					break;
				}
				default: {
					//if trying to specify message
					res = new Message[args.length];
					for( int i = 0; i<args.length; i++) {
						if(!config.messages.getConfigurationSection("messages").contains(args[i])) {
							sender.sendMessage(config.messages.getString("badArgs"));
							return true;
						}
						res[i] = config.getMessage(args[i], player);
					}
				}
			}

			for( Message m : res) {
				for (World w : plugin.getServer().getWorlds()) {
					worldName = w.getName().replace("_nether","").replace("the_end","");
					if(!worlds.contains(worldName)) {
						worlds.set(worldName, "&a" + worldName);
					}

					//skip if player's world isn't the same as receiver's world, disregarding the difference between dimension names
					if( !doOtherWorld && !player.getWorld().getName().replace("_nether","").replace("the_end","").equals( worldName ) ) continue;

					//skip if player is in another dimension
					if( !doOtherDim && !player.getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;

					for (Player p : w.getPlayers()) {
						//skip if has perm
						if(p.isSleepingIgnored() || p.hasPermission("sleep.ignore")) continue;

						new SendMessage(this.plugin, config, m, player, p).runTaskAsynchronously(this.plugin);
					}
				}
			}
		}
		return true;
	}
	
}
