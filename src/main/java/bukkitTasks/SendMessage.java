package bukkitTasks;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import OnePlayerSleep.OnePlayerSleep;
import tools.Config;
import tools.LocalPlaceholders;
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
		int dim;
		String dimStr;
		if(this.doRandom) {
			this.message = this.config.pickRandomMessage();
			dim = this.sourcePlayer.getWorld().getEnvironment().equals(World.Environment.NETHER) ? 1 : 0;
			dim = dim + (this.sourcePlayer.getWorld().getEnvironment().equals(World.Environment.THE_END) ? 2 : 0);
			switch (dim) {
				case 1:  dimStr = config.config.getString("_nether");
					break;
				case 2:  dimStr = config.config.getString("_the_end");
					break;
				default: dimStr = config.config.getString("default");
					break;
			}
			
			global = LocalPlaceholders.fillPlaceHolders(
					this.message.msg.getText(),
					this.sourcePlayer.getName(),
					this.sourcePlayer.getDisplayName(),
					this.sourcePlayer.getWorld().getName(),
					dimStr );
			hover = LocalPlaceholders.fillPlaceHolders(
					this.message.hoverText,
					this.sourcePlayer.getName(),
					this.sourcePlayer.getDisplayName(),
					this.sourcePlayer.getWorld().getName(),
					dimStr );
		}
		dim = this.sourcePlayer.getWorld().getEnvironment().equals(World.Environment.NETHER) ? 1 : 0;
		dim = dim + (this.sourcePlayer.getWorld().getEnvironment().equals(World.Environment.THE_END) ? 2 : 0);
		switch (dim) {
			case 1:  dimStr = config.config.getString("_nether");
				break;
			case 2:  dimStr = config.config.getString("_the_end");
				break;
			default: dimStr = config.config.getString("default");
				break;
		}
		String wakeup = LocalPlaceholders.fillPlaceHolders(
				this.message.wakeup,
				this.targetPlayer.getName(),
				this.targetPlayer.getDisplayName(),
				this.targetPlayer.getWorld().getName(),
				dimStr );
		
		this.message = new Message(global, hover, wakeup, this.message.cantWakeup, this.message.chance);
		this.targetPlayer.spigot().sendMessage(this.message.msg);
		
		if(this.plugin.wakeData.containsKey(targetPlayer)) {
			this.plugin.wakeData.remove(targetPlayer);
		}
		this.plugin.wakeData.put(this.targetPlayer, this.message);
	}
}
