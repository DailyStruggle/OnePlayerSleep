package OnePlayerSleep.tools;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class PAPI_expansion extends PlaceholderExpansion{
	private OnePlayerSleep plugin;

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
	public String getAuthor() {
		return plugin.getDescription().getAuthors().toString();
	}

	@Override
	public String getIdentifier() {
		return "oneplayersleep";
	}

	@Override
    public String getVersion(){
        return "1.4.2";
    }
	
	@Override
    public String onPlaceholderRequest(Player player, String identifier){
		if(player == null){
            return "";
        }

        // %oneplayersleep_sleeping_player_count%
        if(identifier.equalsIgnoreCase("sleeping_player_count")){
            return this.plugin.numSleepingPlayers.toString();
        }

        // %oneplayersleep_total_player_count%
        if(identifier.equalsIgnoreCase("total_player_count")){
            return this.plugin.numPlayers.toString();
        }

        return null;
    }
}
