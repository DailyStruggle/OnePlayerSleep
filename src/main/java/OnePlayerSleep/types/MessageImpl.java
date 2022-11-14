package OnePlayerSleep.types;

import OnePlayerSleep.API.types.Message;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class MessageImpl implements Message {
	public TextComponent msg;
	public String worldName;
	public String name;
	public String hoverText;
	public String wakeup;
	public String cantWakeup;
	public String cancel;
	public Double chance;

	public MessageImpl(String worldName, String name, String global, String hover, String wakeup, String cantWakeup, String cancel, Double chance) {
		this.name = name;
		this.wakeup = wakeup;
		this.hoverText = hover;
		this.cantWakeup = cantWakeup;
		this.cancel = cancel;
		this.chance = chance;

		this.msg = new TextComponent(global);
		this.msg.setHoverEvent( new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(hoverText).create()));
		this.msg.setClickEvent( new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sleep wakeup " + worldName + " " + name));
	}

	public void setWorld(String worldName) {
		this.worldName = worldName;
		this.msg.setClickEvent( new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sleep wakeup " + worldName + " " + this.name));
	}

	@Override
	public String getName() {
		return null;
	}

	@Override
	public String getMessage() {
		return null;
	}

	@Override
	public String getHover() {
		return null;
	}

	@Override
	public String getWakeup() {
		return null;
	}

	@Override
	public String getCantWakeup() {
		return null;
	}

	@Override
	public Double getChance() {
		return null;
	}
}
