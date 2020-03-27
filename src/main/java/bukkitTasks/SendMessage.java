package bukkitTasks;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import OnePlayerSleep.OnePlayerSleep;
import tools.Config;
import types.Message;


//send message to a player
//choose a message if per-player randomization is active
public class SendMessage extends BukkitRunnable{
	private OnePlayerSleep plugin;
	private Config config;
	private Message message;
	private Player sourcePlayer;
	private Player targetPlayer;
	boolean doRandom;
	
	public SendMessage(OnePlayerSleep plugin, Config config, Player sourcePlayer, Player targetPlayer) {
		this.plugin = plugin;
		this.config = config;
		this.sourcePlayer = sourcePlayer;
		this.targetPlayer = targetPlayer;
		this.doRandom = true;
	}
	
	public SendMessage(OnePlayerSleep plugin, Config config, Message message, Player targetPlayer) {
		this.plugin = plugin;
		this.config = config;
		this.message = message;
		this.targetPlayer = targetPlayer;
		this.doRandom = false;
	}
	
	@Override
	public void run() {
		if(this.targetPlayer.hasPermission("sleep.ignore")) {
			return;
		}
		if(this.doRandom) {
			this.message = this.config.pickRandomMessage();
			this.message = this.message.fillPlaceHolders(this.sourcePlayer);
		}
		this.targetPlayer.spigot().sendMessage(this.message.msg);
		if(this.plugin.wakeData.containsKey(targetPlayer)) {
			this.plugin.wakeData.remove(targetPlayer);
		}
		this.plugin.wakeData.put(this.targetPlayer, this.message);
	}
	
}
