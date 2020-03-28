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
		// TODO Auto-generated method stub
		if( !(this.player.isSleeping()) ) {
			this.cancel();
			return;
		}
		if(this.player.hasPermission("sleep.ignore")) return;
		if(this.player.isSleepingIgnored()) return;
		if(this.plugin.sleepingPlayers.get(this.player.getWorld()).size() > 0) return;
		this.plugin.sleepingPlayers.get(this.player.getWorld()).add(this.player);
		int numPlayers = 0;
		int numSleepingPlayers = 0;
		if(this.config.config.getBoolean("showMessageToOtherWorld")) {
			for (World w : (this.plugin.sleepingPlayers.keySet())){
				for ( Player p : w.getPlayers()) {
					if(!p.isSleepingIgnored() && !p.hasPermission("sleep.ignore"))
						numPlayers = numPlayers + 1;
				}
				numSleepingPlayers = numSleepingPlayers	+ this.plugin.sleepingPlayers.get(w).size();
			}
			if(numSleepingPlayers < 2 && numPlayers > 1) new AnnounceSleep(this.plugin, this.config, this.player).runTaskAsynchronously(this.plugin);
		}
		else{
			for ( Player p : this.player.getWorld().getPlayers()) {
				if(!p.isSleepingIgnored() && !p.hasPermission("sleep.ignore"))
					numPlayers = numPlayers + 1;
			}
			numSleepingPlayers = plugin.sleepingPlayers.get(this.player.getWorld()).size();
		}
		if(numSleepingPlayers < 2 && numPlayers > 1) new AnnounceSleep(this.plugin, this.config, this.player).runTaskAsynchronously(this.plugin);
		if(!plugin.doSleep.containsKey(this.player.getWorld())) {
			plugin.doSleep.put(this.player.getWorld(), new PassTime(this.plugin, this.config, this.player.getWorld()).runTaskLater(this.plugin, config.config.getInt("sleepDelay")));
		}
	}

}
