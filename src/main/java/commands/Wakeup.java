package commands;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import OnePlayerSleep.OnePlayerSleep;
import bukkitTasks.AnnounceWakeup;
import me.clip.placeholderapi.PlaceholderAPI;
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
		Set<World> worlds = new HashSet<World>(this.plugin.sleepingPlayers.keySet());
		for(World w : worlds) {
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
					if(this.plugin.sleepingPlayers.get(w).contains(p)) this.plugin.sleepingPlayers.get(w).remove(p);
					if(this.plugin.sleepingPlayers.get(w).size() == 0) this.plugin.sleepingPlayers.remove(w);
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
			String onNoPlayersSleeping = config.messages.getString("onNoPlayersSleeping");
			if(isPlayer && this.config.hasPAPI()) onNoPlayersSleeping = PlaceholderAPI.setPlaceholders((Player)sender, onNoPlayersSleeping);
			sender.sendMessage(onNoPlayersSleeping);
			return true;
		}
		
		if(isPlayer) {
			Message m = this.plugin.wakeData.get(((Player)sender));
			if(!cantKickAPlayer && hasSleepingPlayers) {
				new AnnounceWakeup(this.plugin,this.config,((Player)sender),m).runTaskAsynchronously(this.plugin);
			}
			String cantWakeup = m.cantWakeup;
			if(this.config.hasPAPI()) cantWakeup = PlaceholderAPI.setPlaceholders((Player)sender, cantWakeup);
			if(cantKickAPlayer && hasSleepingPlayers) {
				sender.sendMessage(cantWakeup);
			}
		}
		else {
			plugin.getServer().broadcastMessage("[server]: Everyone wake up!");
		}
		return true;
	}
}
