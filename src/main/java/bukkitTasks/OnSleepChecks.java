package bukkitTasks;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;


import OnePlayerSleep.OnePlayerSleep;
import tools.Config;

public class OnSleepChecks extends BukkitRunnable{
	private OnePlayerSleep plugin;
	private Config config;
	private Player player;
	
	public OnSleepChecks(OnePlayerSleep plugin, Config config, Player player) {
		this.plugin = plugin;
		this.config = config;
		this.player = player;
	}
	
	@Override
	public void run() {
		//if player isn't sleeping anymore when the server gets here, pull out
		if( !(this.player.isSleeping()) ) {
			this.cancel();
			return;
		}
		
		//add player to list of sleeping players
		this.plugin.sleepingPlayers.get(this.player.getWorld()).add(this.player);
		
		int numPlayers = 0;
		int numSleepingPlayers = 0;
		if(this.config.config.getBoolean("showMessageToOtherWorld")) {
			//check all worlds for valid players
			for (World w : (this.plugin.sleepingPlayers.keySet())){
				numSleepingPlayers = numSleepingPlayers	+ this.plugin.sleepingPlayers.get(w).size();
				for ( Player p : w.getPlayers()) 
					if(!p.isSleepingIgnored() && !p.hasPermission("sleep.ignore")) numPlayers = numPlayers + 1;
			}
		}
		else{
			//check just this world for valid players
			for ( Player p : this.player.getWorld().getPlayers()) {
				if(!p.isSleepingIgnored() && !p.hasPermission("sleep.ignore"))
					numPlayers = numPlayers + 1;
			}
			numSleepingPlayers = plugin.sleepingPlayers.get(this.player.getWorld()).size();
		}
		
		//only announce the first bed entry, when there's more than one player to see it
		if(numSleepingPlayers < 2 && numPlayers > 1) new AnnounceSleep(this.plugin, this.config, this.player).runTaskAsynchronously(this.plugin);
		
		//if not already doing sleep stuff in this world, start doing stuff
		if(!plugin.doSleep.containsKey(this.player.getWorld())) {
			plugin.doSleep.put(this.player.getWorld(), new PassTime(this.plugin, this.config, this.player.getWorld()).runTaskLater(this.plugin, config.config.getInt("sleepDelay")));
		}
	}

}
