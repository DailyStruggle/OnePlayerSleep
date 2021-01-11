package OnePlayerSleep.commands;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.bukkitTasks.AnnounceWakeup;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import OnePlayerSleep.tools.Config;
import OnePlayerSleep.types.Message;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
		Boolean useSleepingIgnored = config.config.getBoolean("useSleepingIgnored", true);
		Boolean cantKickAPlayer = false;
		Boolean hasSleepingPlayers = false;
		Set<World> worlds = new HashSet<World>(this.plugin.sleepingPlayers.keySet());
		Message msg = null;

		switch(args.length) {
			case 0: { //if no args, use last message given to player
				if( isPlayer ) msg = this.plugin.wakeData.get(((Player)sender));
				else msg = config.pickRandomMessage();
				break;
			}
			case 1: { //if 1 arg, look up message name
				if(isPlayer) msg = config.getMessage(args[0], (Player)sender);
				else msg = config.getMessage(args[0]);

				break;
			}
			default: { //else bad args
				sender.sendMessage(this.plugin.getPluginConfig().messages.getString("badArgs"));
				return true;
			}
		}

		for(World w : worlds) {
			if( isPlayer && !doOtherWorld && !((Player)sender).getWorld().getName().replace("_nether","").replace("the_end","").equals( w.getName().replace("_nether","").replace("the_end","") ) ) continue;
			if( isPlayer && !doOtherDim && !((Player)sender).getWorld().getEnvironment().equals( w.getEnvironment() ) ) continue;
			ArrayList<Player> sleepingPlayers = new ArrayList<Player>(this.plugin.sleepingPlayers.get(w));
			for ( Player p : sleepingPlayers) {
				if(useSleepingIgnored && p.isSleepingIgnored())
					continue;
				if(p.hasPermission("sleep.ignore"))
					continue;
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
		
		if(isPlayer && cantKickAPlayer) sender.sendMessage(PlaceholderAPI.setPlaceholders((Player)sender, msg.cantWakeup));
		else new AnnounceWakeup(this.plugin,this.config,((Player)sender),msg).runTaskAsynchronously(this.plugin);

		return true;
	}
}
