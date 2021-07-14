package OnePlayerSleep.types;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class Message {
	public TextComponent msg;
	public String worldName;
	public String name;
	public String hoverText;
	public String wakeup;
	public String cantWakeup;
	public Double chance;

	public Message(String worldName, String name, String global, String hover, String wakeup, String cantWakeup, Double chance) {
		this.name = name;
		this.wakeup = ChatColor.translateAlternateColorCodes('&',wakeup);
		this.hoverText = ChatColor.translateAlternateColorCodes('&',hover);
		this.cantWakeup = ChatColor.translateAlternateColorCodes('&',cantWakeup);
		this.chance = chance;

		this.msg = new TextComponent(global);
		this.msg.setHoverEvent( new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(hoverText).create()));
		this.msg.setClickEvent( new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sleep wakeup " + worldName + " " + name));
	}

	public void setWorld(String worldName) {
		this.worldName = worldName;
		this.msg.setClickEvent( new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sleep wakeup " + worldName + " " + this.name));
	}
}
