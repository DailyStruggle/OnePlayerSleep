package OnePlayerSleep;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import tools.Config;
import types.Message;
import commands.Reload;
import commands.Sleep;
import commands.TabComplete;
import commands.Wakeup;
import events.onPlayerBedEnter;
import events.onPlayerBedLeave;

public final class OnePlayerSleep extends JavaPlugin implements Listener {
	private Config config = new Config(this);
	
	public Map<World,BukkitTask> doSleep = new HashMap<World,BukkitTask>();
	public Map<Player, Message> wakeData = new HashMap<Player, Message>(); //list of players receiving wakeup option
	public Map<World, ArrayList<Player>> sleepingPlayers = new HashMap<World, ArrayList<Player>>(); //list of sleeping players for each world
	
	@Override
	public void onEnable() {		getCommand("sleep").setExecutor(new Sleep(this));
		getCommand("sleep").setTabCompleter(new TabComplete());
		
		getCommand("sleep reload").setExecutor(new Reload(this, this.config));
		getCommand("sleep wakeup").setExecutor(new Wakeup(this, this.config));
		
		this.config.refreshConfigs();
		this.config.checkConfigs();
		
		for(World w : Bukkit.getWorlds()) {
			sleepingPlayers.put(w, new ArrayList<Player>());
		}
		
		for( Player p : Bukkit.getOnlinePlayers() ) {
			if(p.isSleeping()) {
				getServer().getPluginManager().callEvent(new PlayerBedEnterEvent(p, p.getWorld().getBlockAt(p.getBedLocation()), BedEnterResult.OK));
			}
		}
		
		
		getServer().getPluginManager().registerEvents(new onPlayerBedEnter(this, config), this);
		getServer().getPluginManager().registerEvents(new onPlayerBedLeave(this, config), this);
		getServer().getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public void onDisable() {
		
	}
	
	public Config getPluginConfig() {
		return this.config;
	}
}
