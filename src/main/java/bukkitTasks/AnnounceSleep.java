package bukkitTasks;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import OnePlayerSleep.OnePlayerSleep;
import tools.Config;
import tools.LocalPlaceholders;
import types.Message;

//set up message threads for all relevant players
public class AnnounceSleep extends BukkitRunnable{
	private OnePlayerSleep plugin;
	private Config config;
	private Player player;
	
	public AnnounceSleep(OnePlayerSleep plugin, Config config, Player player) {
		this.plugin = plugin;
		this.config = config;
		this.player = player;
	}
	
	@Override
	public void run() {
		Boolean doOtherWorld= config.config.getBoolean("doOtherWorlds");
		Boolean doOtherDim = config.config.getBoolean("doOtherDimensions");
		Boolean perPlayer = config.config.getBoolean("randomPerPlayer");
		Message resMsg = new Message("","","","",0.0);
		if(!perPlayer) {
			resMsg = this.config.pickRandomMessage();
			int dim = this.player.getWorld().getEnvironment().equals(World.Environment.NETHER) ? 1 : 0;
			dim = dim + (this.player.getWorld().getEnvironment().equals(World.Environment.THE_END) ? 2 : 0);
			
			String dimStr;
			switch (dim) {
				case 1:  dimStr = config.messages.getString("_nether");
					break;
				case 2:  dimStr = config.messages.getString("_the_end");
					break;
				default: dimStr = config.messages.getString("default");
					break;
					
			}
			
			String global = LocalPlaceholders.fillPlaceHolders(
					resMsg.msg.getText(),
					this.player.getName(),
					this.player.getDisplayName(),
					this.player.getWorld().getName(),
					dimStr );
			String hover = LocalPlaceholders.fillPlaceHolders(
					resMsg.hoverText,
					this.player.getName(),
					this.player.getDisplayName(),
					this.player.getWorld().getName(),
					dimStr );
			resMsg = new Message(global, hover, resMsg.wakeup, resMsg.cantWakeup, resMsg.chance);
		}
		
		for (Player p : plugin.getServer().getOnlinePlayers()) {
			//skip if has perm
			if(p.hasPermission("sleep.ignore")) continue;
			
			//skip if player's world isn't the same as receiver's world, disregarding the difference between dimension names
			if( !doOtherWorld && !player.getWorld().getName().replace("_nether","").replace("the_end","").equals( p.getWorld().getName().replace("_nether","").replace("the_end","") ) ) continue;
			
			//skip if player is in another dimension
			if( !doOtherDim && !player.getWorld().getEnvironment().equals( p.getWorld().getEnvironment() ) ) continue;
			
			if(perPlayer) {
				new SendMessage(this.plugin, this.config, this.player, p).runTaskAsynchronously(this.plugin);
			}
			else {
				new SendMessage(this.plugin, this.config, resMsg, this.player, p).runTaskAsynchronously(this.plugin);
			}
			
		}
	}
}
