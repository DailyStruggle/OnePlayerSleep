package bukkitTasks;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import OnePlayerSleep.OnePlayerSleep;
import tools.Config;
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
		Boolean otherWorldShow = config.config.getBoolean("showMessageToOtherWorld");
		Boolean perPlayer = config.config.getBoolean("randomPerPlayer");
		
		Message resMsg = new Message("","","",0.0);
		if(!perPlayer) {
			resMsg = this.config.pickRandomMessage();
			resMsg = resMsg.fillPlaceHolders(this.player);
		}
		
		for (Player p : plugin.getServer().getOnlinePlayers()) {
			if(		otherWorldShow &&
					player.getWorld() != p.getWorld() )
				continue;
			if(perPlayer) {
				new SendMessage(this.plugin, this.config, this.player, p).runTaskAsynchronously(this.plugin);
			}
			else {
				new SendMessage(this.plugin, this.config, resMsg, p).runTaskAsynchronously(this.plugin);
			}
			
		}
	}
}
