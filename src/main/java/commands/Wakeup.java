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
		
		Boolean doOtherWorld= config.config.getBoolean("doOtherWorlds");
		Boolean doOtherDim = config.config.getBoolean("doOtherDimensions");
		
		Boolean KickFromBed = this.config.config.getBoolean("kickFromBed");
		Boolean cantKickAPlayer = false;
		Boolean hasSleepingPlayers = false;
		for(World w : this.plugin.doSleep.keySet()) {
			if( !doOtherWorld && !player.getWorld().getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
			if( !doOtherDim && !player.getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;
			if(this.plugin.sleepingPlayers.get(w).size() > 0)
				hasSleepingPlayers = true;
			for ( int idx = 0; idx < this.plugin.sleepingPlayers.get(w).size(); idx++) {
				Player p = this.plugin.sleepingPlayers.get(w).get(idx);
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
		if(hasSleepingPlayers) {
			player.sendMessage(config.messages.getString("onNoPlayersSleeping"));
			return true;
		}
		
		Message m = this.plugin.wakeData.get(player);
		if(!cantKickAPlayer && hasSleepingPlayers) {
			new AnnounceWakeup(this.plugin,this.config,player,m).runTaskAsynchronously(this.plugin);
		}
		if(cantKickAPlayer && hasSleepingPlayers) {
			player.sendMessage(m.cantWakeup);
		}
		if(!hasSleepingPlayers) {
			String msg = config.messages.getString("onNoPlayersSleeping", "§cNo players sleeping!");
			player.sendMessage(msg);
		}
		return true;
	}
}
