package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config;
import OnePlayerSleep.types.Message;


//send message to a player
//choose a message if per-player randomization is active
public class SendMessage extends BukkitRunnable{
	private OnePlayerSleep plugin;
	private Config config;
	private Message message;
	private String sourcePlayerName;
	private Player targetPlayer;
	private World sourceWorld;

	public SendMessage(OnePlayerSleep plugin, Config config, World sourceWorld, String sourcePlayerName, Player targetPlayer) {
		this.plugin = plugin;
		this.config = config;
		this.sourceWorld = sourceWorld;
		this.sourcePlayerName = sourcePlayerName;
		this.targetPlayer = targetPlayer;
		this.message = null;
	}
	
	public SendMessage(OnePlayerSleep plugin, Config config, World sourceWorld, String sourcePlayerName, Player targetPlayer, Message message) {
		this.plugin = plugin;
		this.config = config;
		this.sourceWorld = sourceWorld;
		this.sourcePlayerName = sourcePlayerName;
		this.targetPlayer = targetPlayer;
		this.message = message;
	}
	
	@Override
	public void run() {
		if(this.targetPlayer.hasPermission("sleep.ignore")) {
			return;
		}
		Boolean isPlayer = (!this.sourcePlayerName.equals(this.config.getServerName()));

		String global = this.message.msg.getText();
		String hover = this.message.hoverText;
		if(this.message == null) this.message = this.config.pickRandomMessage(this.sourceWorld, sourcePlayerName);

		Boolean hasPAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
		if(hasPAPI && isPlayer) global = PlaceholderAPI.setPlaceholders(Bukkit.getPlayer(this.sourcePlayerName), global);
		if(hasPAPI && isPlayer) hover = PlaceholderAPI.setPlaceholders(Bukkit.getPlayer(this.sourcePlayerName), hover);
		String wakeup = config.fillPlaceHolders(
				this.message.wakeup,
				this.targetPlayer.getName());
		if(hasPAPI) wakeup = PlaceholderAPI.setPlaceholders(this.targetPlayer, wakeup);
		
		this.message = new Message(this.message.worldName, this.message.name, global, hover, wakeup, this.message.cantWakeup, this.message.chance);
		this.targetPlayer.spigot().sendMessage(this.message.msg);
		
		if(this.plugin.wakeData.containsKey(targetPlayer)) {
			this.plugin.wakeData.remove(targetPlayer);
		}
		this.plugin.wakeData.put(this.targetPlayer, this.message);
	}
}
