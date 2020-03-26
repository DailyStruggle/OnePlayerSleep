package bukkitTasks;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import OnePlayerSleep.OnePlayerSleep;
import tools.Config;
import types.Message;

//set up message threads for all relevant players
public class AnnounceWakeup extends BukkitRunnable{
	private OnePlayerSleep plugin;
	private Config config;
	private Player player;
	private Message msg;
	
	public AnnounceWakeup(OnePlayerSleep plugin, Config config, Player player, Message msg) {
		this.plugin = plugin;
		this.config = config;
		this.player = player;
		this.msg = msg;
	}
	
	@Override
	public void run() {
		Boolean otherWorldShow = config.config.getBoolean("showMessageToOtherWorld");
		for (Player p : plugin.getServer().getOnlinePlayers()) {
			if(		otherWorldShow &&
					player.getWorld() != p.getWorld() )
				continue;
			else {
				msg.response = msg.response.replace("[player]", player.getName());
				p.sendMessage(this.msg.response);
			}
			
		}
	}
}
