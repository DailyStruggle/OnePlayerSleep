package OnePlayerSleep.commands;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.tools.Config.Config;
import OnePlayerSleep.tools.SendMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Sleep implements CommandExecutor {
	private final Map<String,String> perms = new HashMap<String,String>();
	private final OnePlayerSleep plugin;

	public Sleep(OnePlayerSleep plugin) {
		this.plugin = plugin;
		//load commands and required permission nodes into map
		perms.put("reload","sleep.reload");
		perms.put("wakeup","sleep.wakeup");
		perms.put("test","sleep.test");
		perms.put("help","sleep.see");
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(!command.getName().equalsIgnoreCase("sleep")) return true;
		if(args.length == 0) {
			plugin.getCommand("sleep help").execute(sender, label, args);
			return true;
		}

		Config config = this.plugin.getPluginConfig();
		if(perms.containsKey(args[0])) {
			if(!sender.hasPermission(perms.get(args[0]))) {
				SendMessage.sendMessage(sender,config.getLog("noPerms"));
			}
			else plugin.getCommand("sleep " + args[0]).execute(sender, label, Arrays.copyOfRange(args, 1, args.length));
		}
		else {
			String msg = config.getLog("badArg", args[0]);
			String senderName = (sender instanceof Player) ? sender.getName() : config.getServerName();
			msg = config.fillPlaceHolders(msg,senderName);
			SendMessage.sendMessage(sender,msg);
		}
		return true;
	}
}
