package OnePlayerSleep.tools;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Map;

public class LocalPlaceholders {
	public static String fillPlaceHolders(String in, Player player, Config config) {
		ConfigurationSection worlds = config.messages.getConfigurationSection("worlds");
		String worldName = player.getWorld().getName().replace("_nether","").replace("the_end","");
		if(!worlds.contains(worldName)) {
			worlds.set(worldName, "&a" + worldName);
		}
		worldName = worlds.getString(worldName);

		String dimStr = config.messages.getConfigurationSection("dimensions").getString(player.getWorld().getEnvironment().name());

		if(in.isEmpty()) return in;
		String res = in;
		res = res.replace("[username]", player.getName());
		res = res.replace("[displayname]", player.getDisplayName());
		if(worldName != null) res = res.replace("[world]", worldName.replace("_nether","").replace("_the_end",""));
		if(dimStr!=null) res = res.replace("[dimension]", dimStr);
		res = ChatColor.translateAlternateColorCodes('&',res);
		return res;
	}
}
