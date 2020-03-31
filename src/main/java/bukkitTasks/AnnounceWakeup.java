package bukkitTasks;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import OnePlayerSleep.OnePlayerSleep;
import tools.Config;
import tools.LocalPlaceholders;
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
		Boolean doOtherWorld = config.config.getBoolean("doOtherWorlds");
		Boolean doOtherDim = config.config.getBoolean("doOtherDimensions");
		for (Player p : plugin.getServer().getOnlinePlayers()) {
			if(p.hasPermission("sleep.ignore")) continue;
			if( !doOtherWorld && !this.player.getWorld().getName().replace("_nether","").replace("the_end","").equals( p.getWorld().getName().replace("_nether","").replace("the_end","") ) ) continue;
			if( !doOtherDim && !this.player.getWorld().getEnvironment().equals( p.getWorld().getEnvironment() ) ) continue;
			
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
			
			String wakeupMsg = LocalPlaceholders.fillPlaceHolders(
					this.msg.wakeup, 
					p.getName(), 
					p.getDisplayName(), 
					p.getWorld().getName(),
					dimStr );
			p.sendMessage(wakeupMsg);
		}
	}
}
