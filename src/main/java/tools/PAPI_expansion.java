package tools;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;

import OnePlayerSleep.OnePlayerSleep;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

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
        return "1.3.5";
    }
	
	@Override
    public String onRequest(OfflinePlayer player, String identifier){
		if(player == null){
            return "";
        }
        
		// %OnePlayerSleep_sleeping_player_count%
        if(identifier.equals("sleeping_player_count")){
            Integer res = 0;
            for (org.bukkit.World w : plugin.sleepingPlayers.keySet()) {
            	res = res + plugin.sleepingPlayers.get(w).size();
            }
        	return res.toString();
        }
        
        // %OnePlayerSleep_total_player_count%
        if(identifier.equals("total_player_count")){
            Integer res = 0;
            Boolean doOtherDim = plugin.getPluginConfig().config.getBoolean("doOtherDimensions");
    		for (org.bukkit.World w : Bukkit.getWorlds()) {
            	if( !doOtherDim && !w.getEnvironment().equals( Environment.NORMAL ) ) continue;
    			for (Player p : w.getPlayers()) {
    				if(p.isSleepingIgnored() || p.hasPermission("sleep.ignore")) continue; 
    				res = res + 1;
    			}
            }
        	return res.toString();
        }
        
        return null;
    }
	
	@Override
    public String onPlaceholderRequest(Player player, String identifier){
		if(player == null){
            return "";
        }
        
		// %OnePlayerSleep_sleeping_player_count%
        if(identifier.equals("sleeping_player_count")){
            Integer res = 0;
            for (org.bukkit.World w : plugin.sleepingPlayers.keySet()) {
            	res = res + plugin.sleepingPlayers.get(w).size();
            }
        	return res.toString();
        }
        
        // %OnePlayerSleep_total_player_count%
        if(identifier.equals("total_player_count")){
            Integer res = 0;
            Boolean doOtherDim = plugin.getPluginConfig().config.getBoolean("doOtherDimensions");
    		for (org.bukkit.World w : Bukkit.getWorlds()) {
            	if( !doOtherDim && !w.getEnvironment().equals( Environment.NORMAL ) ) continue;
    			for (Player p : w.getPlayers()) {
    				if(p.isSleepingIgnored() || p.hasPermission("sleep.ignore")) continue; 
    				res = res + 1;
    			}
            }
        	return res.toString();
        }
        
        return null;
    }
}
