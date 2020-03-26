package commands;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import OnePlayerSleep.OnePlayerSleep;
import bukkitTasks.AnnounceWakeup;
import tools.Config;
import types.Message;

public class Wakeup implements CommandExecutor {
	OnePlayerSleep plugin;
	Config config;
	
	public Wakeup(OnePlayerSleep plugin, Config config) {
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		Player player = (Player) sender;
		Boolean KickFromBed = this.config.config.getBoolean("kickFromBed");
		Boolean otherWorldKick = this.config.config.getBoolean("kickFromBed");
		if( this.config.config.getBoolean("allowKickFromOtherWorld") ) {
			Boolean hasSleepingPlayers = false;
			if( !otherWorldKick ){
				for(World w : this.plugin.doSleep.keySet()) {
					this.plugin.doSleep.get(w).cancel();
					this.plugin.doSleep.remove(w);
					if(this.plugin.sleepingPlayers.get(w).size() > 0)
						hasSleepingPlayers = true;
					if(KickFromBed) 
					{
						for ( Player p : this.plugin.sleepingPlayers.get(w)) {
							Double health = p.getHealth();
							p.damage(1);
							p.setHealth(health);
						}
					}else {
						if(this.plugin.sleepingPlayers.get(w).size() > 0) {
							hasSleepingPlayers = true;
						}
					}
				}
			}
			else {
				World w = player.getWorld();
				this.plugin.doSleep.get(w).cancel();
				this.plugin.doSleep.remove(w);
				if(this.plugin.sleepingPlayers.get(w).size() > 0)
					hasSleepingPlayers = true;
				if(KickFromBed) 
				{
					for ( Player p : this.plugin.sleepingPlayers.get(w)) {
						Double health = p.getHealth();
						p.damage(1);
						p.setHealth(health);
					}
				}
			}
			if(!hasSleepingPlayers) {
				player.sendMessage("no players sleeping!");
				return true;
			}
			
		}
		else {
			World w = player.getWorld();
			if(this.plugin.sleepingPlayers.get(w).size() == 0) {
				player.sendMessage("no players sleeping!");
				return true;
			}
			if(this.plugin.doSleep.containsKey(w)) {
				this.plugin.doSleep.get(w).cancel();
				this.plugin.doSleep.remove(w);
				Message m = this.plugin.wakeData.get(player);
				new AnnounceWakeup(this.plugin,this.config,player,m).runTaskAsynchronously(this.plugin);
				
				if(KickFromBed) 
				{
					for ( int idx = 0; idx < this.plugin.sleepingPlayers.get(w).size(); idx++) {
						Player p = this.plugin.sleepingPlayers.get(w).get(idx);
						Double health = p.getHealth();
						p.damage(1);
						p.setHealth(health);
					}
				}
				
			}
			else {
				
			}
		}
		return true;
	}
}
