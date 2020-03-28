package tools;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import OnePlayerSleep.OnePlayerSleep;
import types.Message;

public class Config {
	public FileConfiguration config;
	public FileConfiguration messages;
	public String version;
	
	private ArrayList<Message> messageArray = new ArrayList<Message>(); 
	private Double totalChance = 0.0;
	private ArrayList<Double> chanceRanges = new ArrayList<Double>();
	private OnePlayerSleep plugin;
	
	public Config(OnePlayerSleep plugin) {
		this.plugin = plugin;
		String s = this.plugin.getServer().getClass().getPackage().getName();
		this.version = s.substring(s.lastIndexOf('.')+1);
	}
	
	
	public void refreshConfigs() {
		//load config.yml file
		File f = new File(this.plugin.getDataFolder(), "config.yml");
		if(f.exists())
			this.config = YamlConfiguration.loadConfiguration(f);
		else
		{
			plugin.saveResource("config.yml", false);
			this.config = YamlConfiguration.loadConfiguration(f);
		}
		
		//load messages.yml file
		f = new File(this.plugin.getDataFolder(), "messages.yml");
		if(f.exists())
			this.messages = YamlConfiguration.loadConfiguration(f);
		else
		{
			plugin.saveResource("messages.yml", false);
			this.messages = YamlConfiguration.loadConfiguration(f);
		}
		
		if( 	this.messages.getDouble(version) < 1.1 || 
				this.config.getDouble(version) < 1.2) {
			updateConfigs();
			
			f = new File(this.plugin.getDataFolder(), "config.yml");
			plugin.saveResource("config.yml", true);
			this.config = YamlConfiguration.loadConfiguration(f);
			
			f = new File(this.plugin.getDataFolder(), "messages.yml");
			plugin.saveResource("messages.yml", true);
			this.messages = YamlConfiguration.loadConfiguration(f);
		}
		
		Set<String> messageNames = this.messages.getConfigurationSection("messages").getKeys(false);
		this.messageArray = new ArrayList<Message>();
		this.totalChance = 0.0;
		this.chanceRanges = new ArrayList<Double>();
		this.chanceRanges.add(0, 0.0);
		int i = 0;
		for (String t : messageNames) {
			String msg = this.fillColorCodes(this.messages.getConfigurationSection("messages").getConfigurationSection(t).getString("global"));
			String hover_msg = this.fillColorCodes(this.messages.getConfigurationSection("messages").getConfigurationSection(t).getString("hover"));
			String response = this.fillColorCodes(this.messages.getConfigurationSection("messages").getConfigurationSection(t).getString("wakeup"));
			String cantWakeup = this.fillColorCodes(this.messages.getConfigurationSection("messages").getConfigurationSection(t).getString("cantWakeup"));
			Double chance = this.messages.getConfigurationSection("messages").getConfigurationSection(t).getDouble("chance");
			this.messageArray.add(i, new Message( msg, hover_msg, response, cantWakeup, chance) );
			this.totalChance = this.totalChance + chance;
			this.chanceRanges.add(i+1, this.chanceRanges.get(i) + chance);
			i = i+1;
		}
		
		String msg = this.fillColorCodes(this.messages.getString("onNoPlayersSleeping"));
		this.messages.set("onNoPlayersSleeping", msg);
	}
	
	//determine valid configuration variables
	public void checkConfigs() {
		//sleepDelay value
		if(			!this.config.isSet("sleepDelay") 
				||  !this.config.isInt("sleepDelay")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no sleepDelay value. Setting to default"); 
			this.config.set("sleepDelay", 60);
		}
		if(this.config.getInt("sleepDelay") < 0) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: invalid sleepDelay value. Setting to default"); 
			this.config.set("sleepDelay", 60);
		}
		
		//stopTime value
		if(			!this.config.isSet("stopTime") 
				||  !this.config.isInt("stopTime")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no stopTime value. Setting to default"); 
			this.config.set("stopTime", 60);
		}
		this.config.set("stopTime", ((this.config.getLong("stopTime") % 24000) + 24000) % 24000); //guarantee a value within 0-23999
		
		//increment value
		if(			!this.config.isSet("increment") 
				||  !this.config.isInt("increment")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no increment value. Setting to default"); 
			this.config.set("increment", 10);
		}
		if(this.config.getInt("increment") < 0) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: invalid increment value. Setting to default"); 
			this.config.set("increment", 10);
		}
		
		//globalNightSkipSync value
		if(			!this.config.isSet("globalNightSkipSync") 
				||  !this.config.isBoolean("globalNightSkipSync")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no globalNightSkipSync value. Setting to default"); 
			this.config.set("globalNightSkipSync", false);
		}
		
		//showMessageToOtherWorld value
		if(			!this.config.isSet("showMessageToOtherWorld") 
				||  !this.config.isBoolean("showMessageToOtherWorld")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no showMessageToOtherWorld value. Setting to default"); 
			this.config.set("showMessageToOtherWorld", false);
		}
		
		//allowKickFromOtherWorld value
		if(			!this.config.isSet("allowKickFromOtherWorld") 
				||  !this.config.isBoolean("allowKickFromOtherWorld")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no allowKickFromOtherWorld value. Setting to default"); 
			this.config.set("allowKickFromOtherWorld", false);
		}
		
		//kickFromBed value
		if(			!this.config.isSet("kickFromBed") 
				||  !this.config.isBoolean("kickFromBed")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no kickFromBed value. Setting to default"); 
			this.config.set("kickFromBed", false);
		}
		
		//randomPerPlayer value
		if(			!this.config.isSet("resetAllStatistics") 
				||  !this.config.isBoolean("resetAllStatistics")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no resetAllStatistics value. Setting to default"); 
			this.config.set("resetAllStatistics", true);
		}
		
		//randomPerPlayer value
		if(			!this.config.isSet("randomPerPlayer") 
				||  !this.config.isBoolean("randomPerPlayer")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no randomPerPlayer value. Setting to default"); 
			this.config.set("randomPerPlayer", false);
		}
		
		//onNoPlayerSleeping value
		if(			!this.messages.isSet("onNoPlayersSleeping") 
				||  !this.messages.isString("onNoPlayersSleeping")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no onNoPlayersSleeping value. Setting to default"); 
			this.messages.set("onNoPlayersSleeping", "No players sleeping!");
		}
	}
	
	//update config files based on version number
	public void updateConfigs() {
		if(this.config.getDouble(version) == 1.0) {
			this.config.set("globalNightSkipSync", false);
			this.config.set("version", 1.1);
		}
		if(this.config.getDouble(version) == 1.1) {
			this.config.set("resetAllStatistics", true);
			this.config.set("version", 1.2);
		}
		
		
		if(this.messages.getDouble(version) == 1.0) {
			Set<String> messageNames = this.messages.getConfigurationSection("messages").getKeys(false);
			this.messageArray = new ArrayList<Message>();
			for (String t : messageNames) {
				this.messages.getConfigurationSection(t).set("cantWakeup", "&csomeone's a deep sleeper");
			}
			this.messages.set("version", 1.1);
		}
		
		return;
	}
	
	public Message pickRandomMessage() {
		int numMessages = this.messageArray.size();;
		if(numMessages == 1) return messageArray.get(0);
		
		//pick a random float
		Random r = new Random();
		double randomValue = (totalChance) * r.nextDouble();
		
		//lookup iterator by binary search of ranges
		int iter_low = 0;
		int iter_high = this.chanceRanges.size();
		int i = iter_high/2;
		while(true) {
			double range_low = this.chanceRanges.get(i);
			double range_high = this.chanceRanges.get(i+1);
			if(randomValue <= range_low) {
				iter_high = i;
				i = (iter_high - iter_low)/2 + iter_low;
				continue;
			}
			if(randomValue > range_high) {
				iter_low = i;
				i = (iter_high - iter_low)/2 + iter_low;
				continue;
			}
			break;
		}
		return messageArray.get(i);
	}
	
	private String fillColorCodes(String in) {
		if(in.isEmpty()) return in;
		String res = new String(in);
		res = res.replace("&0", ChatColor.BLACK.toString());
		res = res.replace("&1", ChatColor.DARK_BLUE.toString());
		res = res.replace("&2", ChatColor.DARK_GREEN.toString());
		res = res.replace("&3", ChatColor.DARK_AQUA.toString());
		res = res.replace("&4", ChatColor.DARK_RED.toString());
		res = res.replace("&5", ChatColor.DARK_PURPLE.toString());
		res = res.replace("&6", ChatColor.GOLD.toString());
		res = res.replace("&7", ChatColor.GRAY.toString());
		res = res.replace("&8", ChatColor.DARK_GRAY.toString());
		res = res.replace("&9", ChatColor.BLUE.toString());
		res = res.replace("&a", ChatColor.GREEN.toString());
		res = res.replace("&b", ChatColor.AQUA.toString());
		res = res.replace("&c", ChatColor.RED.toString());
		res = res.replace("&d", ChatColor.LIGHT_PURPLE.toString());
		res = res.replace("&e", ChatColor.YELLOW.toString());
		res = res.replace("&f", ChatColor.WHITE.toString());
		res = res.replace("&l", ChatColor.BOLD.toString());
		res = res.replace("&m", ChatColor.STRIKETHROUGH.toString());
		res = res.replace("&o", ChatColor.ITALIC.toString());
		return res;
	}
}
