package OnePlayerSleep.tools;

import OnePlayerSleep.tools.softdepends.PAPIChecker;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SendMessage {
    private static final Pattern hexColorPattern1 = Pattern.compile("(&?#[0-9a-fA-F]{6})");
    private static final Pattern hexColorPattern2 = Pattern.compile("(&[0-9a-fA-F]&[0-9a-fA-F]&[0-9a-fA-F]&[0-9a-fA-F]&[0-9a-fA-F]&[0-9a-fA-F])");

    public static void sendMessage(CommandSender target1, CommandSender target2, String message) {
        if(message == null || message.isEmpty()) return;
        sendMessage(target1,message);
        if(!target1.getName().equals(target2.getName())) {
            sendMessage(target2, message);
        }
    }

    public static void sendMessage(CommandSender sender, String message) {
        if(message == null || message.isEmpty()) return;
        if(sender instanceof Player) sendMessage((Player) sender,message);
        else {
            message = format(Bukkit.getOfflinePlayer(new UUID(0,0)),message);
            BaseComponent[] components = TextComponent.fromLegacyText(message);
            sender.spigot().sendMessage(components);
        }
    }

    public static void sendMessage(Player player, String message) {
        if(message == null || message.isEmpty()) return;
        message = format(player,message);
        BaseComponent[] components = TextComponent.fromLegacyText(message);
        player.spigot().sendMessage(components);
    }

    public static void sendMessage(CommandSender sender, String message, String hover, String click) {
        if(message.equals("")) return;

        OfflinePlayer player;
        if(sender instanceof Player) player = (OfflinePlayer) sender;
        else player = Bukkit.getOfflinePlayer(new UUID(0,0)).getPlayer();

        message = format(player,message);

        BaseComponent[] textComponents = TextComponent.fromLegacyText(message);

        if (!hover.equals("")) {
            BaseComponent[] hoverComponents = TextComponent.fromLegacyText(format(player, hover));
            //noinspection deprecation
            HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverComponents));
            for (BaseComponent component : textComponents) {
                component.setHoverEvent(hoverEvent);
            }
        }

        if (!click.equals("")) {
            ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, click);
            for (BaseComponent component : textComponents) {
                component.setClickEvent(clickEvent);
            }
        }

        sender.spigot().sendMessage(textComponents);
    }

    public static String format(@Nullable OfflinePlayer player, @Nullable String text) {
        if(text == null) return "";

        //check PAPI exists and fill remaining PAPI placeholders
        text = PAPIChecker.fillPlaceholders(player,text);

        text = formatDry(text);
        return text;
    }

    public static String formatDry(@Nullable String text) {
        if(text == null) return "";

        text = ChatColor.translateAlternateColorCodes('&',text);
        text = Hex2Color(text);

        return text;
    }

    private static String Hex2Color(String text) {
        //reduce patterns
        if(text == null) return "";
        Matcher matcher2 = hexColorPattern2.matcher(text);
        while (matcher2.find()) {
            String hexColor = text.substring(matcher2.start(), matcher2.end());
            String shortColor = "#" + hexColor.replaceAll("&","");
            text = text.replaceAll(hexColor, shortColor);
        }

        //colorize
        Matcher matcher1 = hexColorPattern1.matcher(text);
        while (matcher1.find()) {
            String hexColor = text.substring(matcher1.start(), matcher1.end());
            String bukkitColor;
            bukkitColor = ChatColor.of(hexColor.substring(hexColor.indexOf('#'))).toString();
            text = text.replaceAll(hexColor, bukkitColor);
            matcher1.reset(text);
        }
        return text;
    }

    public static void log(Level level, String message) {
        if(message.isEmpty()) return;

        message = format(null,message);

        Logger logger = Bukkit.getLogger();
        if(logger!=null) logger.log(level,message);
    }

    public static void log(Level level, String message, Exception exception) {
        if(message.isEmpty()) return;

        message = format(null,message);

        Bukkit.getLogger().log(level,message,exception);
    }

    public static void title(Player player, String title, String subtitle, int in, int stay, int out) {
        boolean noTitle = title == null || title.isEmpty();
        boolean noSubtitle = subtitle == null || subtitle.isEmpty();

        if(noTitle && noSubtitle) return;

        if(title!=null) title = Hex2Color(ChatColor.translateAlternateColorCodes('&',title));
        if(subtitle!=null) subtitle = Hex2Color(ChatColor.translateAlternateColorCodes('&',subtitle));

        player.sendTitle(title,subtitle,in,stay,out);
    }

    public static void actionbar(Player player, String bar) {
        if(bar == null || bar.isEmpty()) return;
        bar = Hex2Color(ChatColor.translateAlternateColorCodes('&',bar));
        BaseComponent[] components = TextComponent.fromLegacyText(bar);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);
    }
}
