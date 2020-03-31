package commands;

import java.util.ArrayList;

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
		Boolean isPlayer = (sender instanceof Player);
		
		Boolean doOtherWorld= config.config.getBoolean("doOtherWorlds");
		Boolean doOtherDim = config.config.getBoolean("doOtherDimensions");
		
		Boolean KickFromBed = this.config.config.getBoolean("kickFromBed");
		Boolean cantKickAPlayer = false;
		Boolean hasSleepingPlayers = false;
		for(World w : this.plugin.sleepingPlayers.keySet()) {
			if( isPlayer && !doOtherWorld && !((Player)sender).getWorld().getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
			if( isPlayer && !doOtherDim && !((Player)sender).getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;
			ArrayList<Player> sleepingPlayers = new ArrayList<Player>(this.plugin.sleepingPlayers.get(w));
			for ( Player p : sleepingPlayers) {
				if(p.isSleepingIgnored() || p.hasPermission("sleep.ignore")) continue;
				hasSleepingPlayers = true;
				if(p.hasPermission("sleep.bypass") && !isPlayer) {
					cantKickAPlayer = true;
				}
				else if(KickFromBed) {
					Double health = p.getHealth();
					p.damage(1);
					p.setHealth(health);
				}
			}
			if(!cantKickAPlayer && hasSleepingPlayers && this.plugin.doSleep.containsKey(w)) {
				this.plugin.doSleep.get(w).cancel();
			}
		}
		if(!hasSleepingPlayers) {
			sender.sendMessage(config.messages.getString("onNoPlayersSleeping"));
			return true;
		}
		
		if(isPlayer) {
			Message m = this.plugin.wakeData.get(((Player)sender));
			if(!cantKickAPlayer && hasSleepingPlayers) {
				new AnnounceWakeup(this.plugin,this.config,((Player)sender),m).runTaskAsynchronously(this.plugin);
			}
			if(cantKickAPlayer && hasSleepingPlayers) {
				sender.sendMessage(m.cantWakeup);
			}
		}
		else {
			plugin.getServer().broadcastMessage("[server]: Everyone wake up!");
		}
		if(!hasSleepingPlayers) {
			String msg = config.messages.getString("onNoPlayersSleeping", "§cNo players sleeping!");
			sender.sendMessage(msg);
		}
		return true;
	}
}
