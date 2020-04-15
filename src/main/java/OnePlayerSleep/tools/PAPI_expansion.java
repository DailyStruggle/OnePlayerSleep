package OnePlayerSleep.tools;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class PAPI_expansion extends PlaceholderExpansion{
	private OnePlayerSleep plugin;
	
	@Override
    public boolean canRegister(){
        return true;
    }
	
	@Override
    public boolean register(){
		if(!canRegister()){
            return false;
        }
        
        plugin = (OnePlayerSleep) Bukkit.getPluginManager().getPlugin("OnePlayerSleep");
        
        if(plugin == null){
            return false;
        }
        
        return super.register();
    }
	
	@Override
	public String getAuthor() {
		return plugin.getDescription().getAuthors().toString();
	}

	@Override
	public String getIdentifier() {
		return "OnePlayerSleep";
	}

	@Override
    public String getVersion(){
        return "1.4.0";
    }
	
	@Override
    public String onRequest(OfflinePlayer player, String identifier){
		if(player == null){
            return "";
        }
        
		// %OnePlayerSleep_sleeping_player_count%
        if(identifier.equalsIgnoreCase("sleeping_player_count")){
            return this.plugin.numSleepingPlayers.toString();
        }
        
        // %OnePlayerSleep_total_player_count%
        if(identifier.equalsIgnoreCase("total_player_count")){
            return this.plugin.numPlayers.toString();
        }
        
        return null;
    }
	
	@Override
    public String onPlaceholderRequest(Player player, String identifier){
		if(player == null){
            return "";
        }

        // %OnePlayerSleep_sleeping_player_count%
        if(identifier.equalsIgnoreCase("sleeping_player_count")){
            return this.plugin.numSleepingPlayers.toString();
        }

        // %OnePlayerSleep_total_player_count%
        if(identifier.equalsIgnoreCase("total_player_count")){
            return this.plugin.numPlayers.toString();
        }

        return null;
    }
}
