package types;

import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class Message {
	public TextComponent msg;
	public String hoverText;
	public String response;
	public Double chance;
	
	public Message(String global, String hover, String response, Double chance) {
		this.msg = new TextComponent(global);
		this.msg.setHoverEvent( new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder( hover ).create()));
		this.msg.setClickEvent( new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sleep wakeup"));
		this.response = response;
		this.hoverText = hover;
		this.chance = chance;
	}
	
	public Message fillPlaceHolders(Player p) {
		String global = this.msg.getText().replace("[player]", p.getName());
		String hover = this.hoverText.replace("[player]", p.getName());
		Message res = new Message(global, hover, this.response, this.chance);
		return res;
	}
}
