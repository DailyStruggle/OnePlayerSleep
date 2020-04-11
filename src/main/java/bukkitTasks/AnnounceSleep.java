package bukkitTasks;

import OnePlayerSleep.OnePlayerSleep;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
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
		Message resMsg = new Message( "", "","","","",0.0);
		ConfigurationSection worlds = this.config.messages.getConfigurationSection("worlds");
		String worldName = this.player.getWorld().getName().replace("_nether","").replace("the_end","");
		if(!worlds.contains(worldName)) {
			worlds.set(worldName, "&a" + worldName);
		}
		worldName = worlds.getString(worldName);
		
		if(!perPlayer) {
			resMsg = this.config.pickRandomMessage();
			
			String dimStr = this.config.messages.getConfigurationSection("dimensions").getString(this.player.getWorld().getEnvironment().name());
			
			String global = LocalPlaceholders.fillPlaceHolders(
					resMsg.msg.getText(),
					this.player.getName(),
					this.player.getDisplayName(),
					worldName,
					dimStr );
			
			String hover = LocalPlaceholders.fillPlaceHolders(
					resMsg.hoverText,
					this.player.getName(),
					this.player.getDisplayName(),
					worldName,
					dimStr );
			
			resMsg = new Message(resMsg.name, global, hover, resMsg.wakeup, resMsg.cantWakeup, resMsg.chance);
		}
		
		for (World w : plugin.getServer().getWorlds()) {
			worldName = w.getName().replace("_nether","").replace("the_end","");
			if(!worlds.contains(worldName)) {
				worlds.set(worldName, "&a" + worldName);
			}
			
			//skip if player's world isn't the same as receiver's world, disregarding the difference between dimension names
			if( !doOtherWorld && !player.getWorld().getName().replace("_nether","").replace("the_end","").equals( worldName ) ) continue;
			
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
