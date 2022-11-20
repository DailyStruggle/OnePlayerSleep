package OnePlayerSleep.tools.softdepends;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class PAPI_expansion extends PlaceholderExpansion{
	private final OnePlayerSleep plugin;

	public PAPI_expansion(OnePlayerSleep plugin){
	    this.plugin = plugin;
    }

    @Override
    public boolean persist(){
        return true;
    }

	@Override
    public boolean canRegister(){
        return true;
    }
	
	@Override
	public @NotNull String getAuthor() {
		return plugin.getDescription().getAuthors().toString();
	}

	@Override
	public @NotNull String getIdentifier() {
		return "onePlayerSleep";
	}

	@Override
    public @NotNull String getVersion(){
        return "3.1.10";
    }
	
	@Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier){
		if(player == null){
            return "";
        }

        // %oneplayersleep_sleeping_player_count%
        if(identifier.equalsIgnoreCase("sleeping_player_count")){
            int res = 0;
            for(Map.Entry<UUID, HashSet<Player>> entry : this.plugin.sleepingPlayers.entrySet()){
                res += entry.getValue().size();
            }
            return Integer.toString(res);
        }

        // %oneplayersleep_total_player_count%
        if(identifier.equalsIgnoreCase("total_player_count")){
            Long res = 0L;
            for(Map.Entry<UUID,Long> entry : this.plugin.numPlayers.entrySet()){
                res += entry.getValue();
            }
            return res.toString();
        }

        return null;
    }
}
