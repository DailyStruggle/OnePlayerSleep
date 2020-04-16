package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config;
import OnePlayerSleep.tools.LocalPlaceholders;
import OnePlayerSleep.types.Message;


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
	
	public SendMessage(OnePlayerSleep plugin, Config config, Message message, Player sourcePlayer, Player targetPlayer) {
		this.plugin = plugin;
		this.config = config;
		this.message = message;
		this.sourcePlayer = sourcePlayer;
		this.targetPlayer = targetPlayer;
		this.doRandom = false;
	}
	
	@Override
	public void run() {
		if(this.targetPlayer.hasPermission("sleep.ignore")) {
			return;
		}
		String global = this.message.msg.getText();
		String hover = this.message.hoverText;
		if(this.doRandom) {
			this.message = this.config.pickRandomMessage();
			global = LocalPlaceholders.fillPlaceHolders(
					this.message.msg.getText(),
					this.sourcePlayer,
					this.config);
			hover = LocalPlaceholders.fillPlaceHolders(
					this.message.hoverText,
					this.sourcePlayer,
					this.config);
		}
		if(this.config.hasPAPI()) global = PlaceholderAPI.setPlaceholders(this.targetPlayer, global);
		if(this.config.hasPAPI()) hover = PlaceholderAPI.setPlaceholders(this.sourcePlayer, hover);
		String wakeup = LocalPlaceholders.fillPlaceHolders(
				this.message.wakeup,
				this.targetPlayer,
				this.config );
		if(this.config.hasPAPI()) wakeup = PlaceholderAPI.setPlaceholders(this.targetPlayer, wakeup);
		
		this.message = new Message(this.message.name, global, hover, wakeup, this.message.cantWakeup, this.message.chance);
		this.targetPlayer.spigot().sendMessage(this.message.msg);
		
		if(this.plugin.wakeData.containsKey(targetPlayer)) {
			this.plugin.wakeData.remove(targetPlayer);
		}
		this.plugin.wakeData.put(this.targetPlayer, this.message);
	}
}
