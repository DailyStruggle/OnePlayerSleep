package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config;
import OnePlayerSleep.tools.LocalPlaceholders;
import OnePlayerSleep.types.Message;

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
		Message resMsg = new Message( "", "","","","",0.0);
		ConfigurationSection worlds = this.config.messages.getConfigurationSection("worlds");

		if(!perPlayer) {
			resMsg = this.config.pickRandomMessage();
			
			String global = LocalPlaceholders.fillPlaceHolders(
					resMsg.msg.getText(),
					this.player,
					this.config);
			
			String hover = LocalPlaceholders.fillPlaceHolders(
					resMsg.hoverText,
					this.player,
					this.config);
			
			resMsg = new Message(resMsg.name, global, hover, resMsg.wakeup, resMsg.cantWakeup, resMsg.chance);
		}
		
		for (World w : plugin.getServer().getWorlds()) {
			//skip if player's world isn't the same as receiver's world, disregarding the difference between dimension names
			if( !doOtherWorld && !player.getWorld().getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
			
			//skip if player is in another dimension
			if( !doOtherDim && !player.getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;
			
			for (Player p : w.getPlayers()) {
				//skip if has perm
				if(p.isSleepingIgnored() || p.hasPermission("sleep.ignore")) continue; 
				
				if(perPlayer) {
					new SendMessage(this.plugin, this.config, this.player, p).runTaskAsynchronously(this.plugin);
				}
				else {
					new SendMessage(this.plugin, this.config, resMsg, this.player, p).runTaskAsynchronously(this.plugin);
				}
			}
		}
	}
}
