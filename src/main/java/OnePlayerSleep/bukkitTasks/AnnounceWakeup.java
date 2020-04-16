package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config;
import OnePlayerSleep.tools.LocalPlaceholders;
import OnePlayerSleep.types.Message;

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
			
			String wakeupMsg = LocalPlaceholders.fillPlaceHolders(
					this.msg.wakeup, 
					p,
					this.config);
			if(this.config.hasPAPI()) wakeupMsg = PlaceholderAPI.setPlaceholders(p, wakeupMsg);
			p.sendMessage(wakeupMsg);
		}
	}
}
