package OnePlayerSleep.bukkitTasks;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import OnePlayerSleep.tools.Config.Config;
import OnePlayerSleep.types.MessageImpl;

import java.util.UUID;


//send message to a player
//choose a message if per-player randomization is active
public class SendMessageRunable extends BukkitRunnable{
	private final OnePlayerSleep plugin;
	private final Config config;
	private MessageImpl message;
	private final String sourcePlayerName;
	private final Player targetPlayer;
	private final World sourceWorld;

	public SendMessageRunable(OnePlayerSleep plugin, Config config, World sourceWorld, String sourcePlayerName, Player targetPlayer) {
		this.plugin = plugin;
		this.config = config;
		this.sourceWorld = sourceWorld;
		this.sourcePlayerName = sourcePlayerName;
		this.targetPlayer = targetPlayer;
		this.message = null;
	}
	
	public SendMessageRunable(OnePlayerSleep plugin, Config config, World sourceWorld, String sourcePlayerName, Player targetPlayer, MessageImpl message) {
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
		UUID playerID = (sourcePlayerName.equals(config.getServerName())) ? new UUID(0,0) : Bukkit.getPlayer(sourcePlayerName).getUniqueId();

		String global = config.fillPlaceHolders(this.message.msg.getText(), sourceWorld);
		String hover = config.fillPlaceHolders(this.message.hoverText, sourceWorld);
		if(this.message == null) this.message = this.config.pickRandomMessage(this.sourceWorld, sourcePlayerName);

		boolean hasPAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
		if(hasPAPI) global = PlaceholderAPI.setPlaceholders(Bukkit.getOfflinePlayer(playerID), global);
		if(hasPAPI) hover = PlaceholderAPI.setPlaceholders(Bukkit.getOfflinePlayer(playerID), hover);

		BaseComponent[] components = TextComponent.fromLegacyText(global);
		BaseComponent[] hoverComponents = TextComponent.fromLegacyText(hover);
		HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponents);
		ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sleep wakeup " + message.worldName + " " + message.name);
		for(BaseComponent component : components) {
			component.setHoverEvent(hoverEvent);
			component.setClickEvent(clickEvent);
		}
		this.targetPlayer.spigot().sendMessage(components);

		this.plugin.wakeData.remove(targetPlayer.getUniqueId());
		this.plugin.wakeData.put(this.targetPlayer.getUniqueId(), this.message);
	}
}
