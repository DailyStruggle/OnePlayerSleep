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
		if(!sender.hasPermission("sleep.wakeup")) return false;
		Player player = (Player) sender;
		Boolean KickFromBed = this.config.config.getBoolean("kickFromBed");
		Boolean cantKickAPlayer = false;
		Boolean hasSleepingPlayers = false;
		if( this.config.config.getBoolean("allowKickFromOtherWorld") ) {
			for(World w : this.plugin.doSleep.keySet()) {
				if(this.plugin.sleepingPlayers.get(w).size() > 0)
					hasSleepingPlayers = true;
				for ( Player p : this.plugin.sleepingPlayers.get(w)) {
					if(p.hasPermission("sleep.bypass")) {
						cantKickAPlayer = true;
						continue;
					}
					if(KickFromBed) {
						Double health = p.getHealth();
						p.damage(1);
						p.setHealth(health);
					}
				}
				if(!cantKickAPlayer && hasSleepingPlayers && this.plugin.doSleep.containsKey(w)) {
					this.plugin.doSleep.get(w).cancel();
					this.plugin.doSleep.remove(w);
				}
			}
		}
		else {
			World w = player.getWorld();
			if(this.plugin.sleepingPlayers.get(w).size() == 0) {
				player.sendMessage("no players sleeping!");
				return true;
			}
			hasSleepingPlayers = true;
			for ( int idx = 0; idx < this.plugin.sleepingPlayers.get(w).size(); idx++) {
				Player p = this.plugin.sleepingPlayers.get(w).get(idx);
				if(p.hasPermission("sleep.bypass")) {
					cantKickAPlayer = true;
				}
				else if(KickFromBed) {
					Double health = p.getHealth();
					p.damage(1);
					p.setHealth(health);
				}
			}
			if(!cantKickAPlayer && this.plugin.doSleep.containsKey(w)) {
				this.plugin.doSleep.get(w).cancel();
				this.plugin.doSleep.remove(w);
			}
		}
		
		Message m = this.plugin.wakeData.get(player);
		if(!cantKickAPlayer && hasSleepingPlayers) {
			new AnnounceWakeup(this.plugin,this.config,player,m).runTaskAsynchronously(this.plugin);
		}
		if(cantKickAPlayer && hasSleepingPlayers) {
			player.sendMessage(m.cantWakeup);
		}
		if(!hasSleepingPlayers) {
			String msg = config.messages.getString("onNoPlayersSleeping");
			player.sendMessage(msg);
		}
		return true;
	}
}
