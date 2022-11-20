package OnePlayerSleep.commands;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.bukkitTasks.AnnounceSleep;
import OnePlayerSleep.tools.SendMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import OnePlayerSleep.tools.Config.Config;
import OnePlayerSleep.types.MessageImpl;

import java.util.List;
import java.util.Set;

public class Test implements CommandExecutor {
	private final OnePlayerSleep plugin;
	private final Config config;

	public Test(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(!command.getName().equalsIgnoreCase("sleep test")) return true;

		boolean isPlayer = sender instanceof Player;
		String playerName = isPlayer
				? sender.getName()
				: this.config.getServerName();
		String worldName = isPlayer
				? ((Player)sender).getWorld().getName()
				: this.config.getServerWorldName();
		if(args.length > 0) worldName = args[0];
		if(!this.config.checkWorldExists(worldName)) {
			if(isPlayer) SendMessage.sendMessage(sender,this.config.getLog("invalidWorld", worldName));
			return true;
		}

		World world = Bukkit.getWorld(worldName);
		if(world == null) {
			new IllegalStateException().printStackTrace();
			return true;
		}


		MessageImpl[] res;
		switch(args.length) {
			case 0: //if just /sleep test, pick 1 random message from player's world
			{
				res = new MessageImpl[1];
				res[0] = this.plugin.getPluginConfig().pickRandomMessage(world, playerName);
				break;
			}
			case 1: //if world arg, pick 1 random message from the world's list
			{
				res = new MessageImpl[1];
				World world1 = Bukkit.getWorld(args[0]);
				if(world1 == null) {
					new IllegalStateException().printStackTrace();
					return true;
				}
				res[0] = this.plugin.getPluginConfig().pickRandomMessage(world1, playerName);
				break;
			}
			default: //if world arg and message args
			{
				res = new MessageImpl[args.length-1];
				Set<String> messageListNames = this.config.getMessageListNames();
				for( int i = 0; i<res.length; i++)
				{
					//detect delimiter exists and where
					String listName;
					int delimiterIdx = args[i+1].indexOf('.');
					if(delimiterIdx > 0)
					{
						listName = args[i+1].substring(0, delimiterIdx);
						String msgName = args[i+1].substring(delimiterIdx+1);

						if(!messageListNames.contains(listName))
						{
							SendMessage.sendMessage(sender,this.config.getLog("invalidList", listName));
							continue;
						}

						MessageImpl msg = this.config.getMessage(listName,msgName,playerName);
						if(msg==null)
						{
							SendMessage.sendMessage(sender,this.config.getLog("invalidMsg", msgName));
							continue;
						}

						res[i] = this.config.getMessage(listName, msgName, playerName);
					}
					else
					{
						listName = args[i+1];
						if(!messageListNames.contains(listName))
						{
							SendMessage.sendMessage(sender,this.config.getLog("invalidList", listName));
							continue;
						}
						res[i] = this.config.pickRandomMessage(listName,playerName);
					}
					res[i].setWorld(args[0]);
				}
			}
		}

		for( MessageImpl message : res) {
			if(message == null) continue;
			if(!isPlayer) {
				message.setWorld(worldName);
				message.msg.setText(this.config.fillPlaceHolders(message.msg.getText(), world));
				message.hoverText = this.config.fillPlaceHolders(message.hoverText, world);
			}

			List<String> msgToWorldNames = this.config.getMsgToWorlds(worldName);
			for(String msgToWorldName : msgToWorldNames) {
				new AnnounceSleep(this.plugin,this.config,playerName,Bukkit.getWorld(msgToWorldName),message).runTaskAsynchronously(this.plugin);
			}
		}
		return true;
	}
	
}
