package OnePlayerSleep.commands;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.tools.Config;
import net.md_5.bungee.api.chat.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class Help implements CommandExecutor{
	private final Config config;

	public Help(OnePlayerSleep plugin, Config config) {
		this.config = config;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(!sender.hasPermission("sleep.see")) return false;

		BaseComponent[] msg = TextComponent.fromLegacyText(this.config.getLog("help"));
		for(BaseComponent component : msg) {
			component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("/sleep").create()));
			component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sleep help"));
		}
		sender.spigot().sendMessage(msg);
		
		if(sender.hasPermission("sleep.wakeup")) {
			msg = TextComponent.fromLegacyText(this.config.getLog("wakeup"));
			for(BaseComponent component : msg) {
				component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("/sleep wakeup").create()));
				component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sleep wakeup"));
			}
			sender.spigot().sendMessage(msg);
		}

		if(sender.hasPermission("sleep.reload")) {
			msg = TextComponent.fromLegacyText(this.config.getLog("reload"));
			for(BaseComponent component : msg) {
				component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("/sleep reload").create()));
				component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sleep reload"));
			}
			sender.spigot().sendMessage(msg);
		}


		if(sender.hasPermission("sleep.test")) {
			msg = TextComponent.fromLegacyText(this.config.getLog("test"));
			for(BaseComponent component : msg) {
				component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("/sleep test").create()));
				component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sleep test"));
			}
			sender.spigot().sendMessage(msg);
		}
		return true;
	}
	
}
