package tools;

import OnePlayerSleep.OnePlayerSleep;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import types.Message;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class Config {
	public FileConfiguration config;
	public FileConfiguration messages;
	public String version;
	public List<String> messageNames = new ArrayList<String>();

	private ArrayList<Message> messageArray = new ArrayList<Message>();
	private Double totalChance = 0.0;
	private ArrayList<Double> chanceRanges = new ArrayList<Double>();
	private OnePlayerSleep plugin;
	
	private Boolean hasPAPI;
	
	public Config(OnePlayerSleep plugin) {
		this.plugin = plugin;
		String s = this.plugin.getServer().getClass().getPackage().getName();
		this.version = s.substring(s.lastIndexOf('.')+1);
	}
	
	
	public void refreshConfigs() {
		this.messageNames = new ArrayList<String>();
		this.messageArray = new ArrayList<Message>();
		this.totalChance = 0.0;
		this.chanceRanges = new ArrayList<Double>();

		this.hasPAPI = false;
		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			this.hasPAPI = true;
		}
		
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
		
		checkConfigs();
		
		if( 	(this.messages.getDouble("version") < 1.5) ) {
			Bukkit.getConsoleSender().sendMessage("�b[OnePlayerSleep] old messages.yml detected. Getting a newer version");
			this.renameFileInPluginDir("messages.yml","messages.old.yml");
			
			this.plugin.saveResource("messages.yml", false);
			this.messages = YamlConfiguration.loadConfiguration(f);
		}
		if( 	(this.config.getDouble("version") < 1.4) ) {
			Bukkit.getConsoleSender().sendMessage("�b[OnePlayerSleep] old config.yml detected. Updating");
			
			updateConfig();
			
			f = new File(this.plugin.getDataFolder(), "config.yml");
			this.config = YamlConfiguration.loadConfiguration(f);
		}
		
		this.messages.set("onNoPlayersSleeping", LocalPlaceholders.fillColorCodes(this.messages.getString("onNoPlayersSleeping")));
		this.messages.set("cooldownMessage", LocalPlaceholders.fillColorCodes(this.messages.getString("cooldownMessage")));
		
		this.messages.set("default", LocalPlaceholders.fillColorCodes(this.messages.getString("default")));
		this.messages.set("_nether", LocalPlaceholders.fillColorCodes(this.messages.getString("_nether")));
		this.messages.set("_the_end", LocalPlaceholders.fillColorCodes(this.messages.getString("_the_end")));
		
		Set<String> allMessageNames = this.messages.getConfigurationSection("messages").getKeys(false);
		this.messageArray = new ArrayList<Message>();
		this.totalChance = 0.0;
		this.chanceRanges = new ArrayList<Double>();
		this.chanceRanges.add(0, 0.0);
		int i = 0;
		for (String t : allMessageNames) {
			String msg = LocalPlaceholders.fillColorCodes(this.messages.getConfigurationSection("messages").getConfigurationSection(t).getString("global","[player] &bis sleeping"));
			String hover_msg = LocalPlaceholders.fillColorCodes(this.messages.getConfigurationSection("messages").getConfigurationSection(t).getString("hover","&eWake up!"));
			String response = LocalPlaceholders.fillColorCodes(this.messages.getConfigurationSection("messages").getConfigurationSection(t).getString("wakeup","[player] says &cWake up!"));
			String cantWakeup = LocalPlaceholders.fillColorCodes(this.messages.getConfigurationSection("messages").getConfigurationSection(t).getString("cantWakeup","&csomeone's a deep sleeper"));
			Double chance = this.messages.getConfigurationSection("messages").getConfigurationSection(t).getDouble("chance");
			this.messageArray.add(i, new Message(t, msg, hover_msg, response, cantWakeup, chance) );
			this.totalChance = this.totalChance + chance;
			this.chanceRanges.add(i+1, this.chanceRanges.get(i) + chance);
			this.messageNames.add(t);
			i = i+1;
		}
		
		String msg = LocalPlaceholders.fillColorCodes(this.messages.getString("onNoPlayersSleeping"));
		this.messages.set("onNoPlayersSleeping", msg);
	}
	
	public Boolean hasPAPI() {
		return this.hasPAPI;
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

	public Message getMessage(String name) {
		Message res;
		String msg = this.messages.getConfigurationSection("messages").getConfigurationSection(name).getString("global","[player] &bis sleeping");
		String hover_msg = this.messages.getConfigurationSection("messages").getConfigurationSection(name).getString("hover","&eWake up!");
		String response = this.messages.getConfigurationSection("messages").getConfigurationSection(name).getString("wakeup","[player] says &cWake up!");
		String cantWakeup = this.messages.getConfigurationSection("messages").getConfigurationSection(name).getString("cantWakeup","&csomeone's a deep sleeper");
		Double chance = this.messages.getConfigurationSection("messages").getConfigurationSection(name).getDouble("chance");
		res = new Message(name, msg, hover_msg, response, cantWakeup, chance);

		return res;
	}
	public Message getMessage(String name, Player player) {
		Message res;
		String msg = LocalPlaceholders.fillPlaceHolders(this.messages.getConfigurationSection("messages").getConfigurationSection(name).getString("global","[player] &bis sleeping"), player, this);
		String hover_msg = LocalPlaceholders.fillPlaceHolders(this.messages.getConfigurationSection("messages").getConfigurationSection(name).getString("hover","&eWake up!"), player, this);
		String response = LocalPlaceholders.fillPlaceHolders(this.messages.getConfigurationSection("messages").getConfigurationSection(name).getString("wakeup","[player] says &cWake up!"), player, this);
		String cantWakeup = LocalPlaceholders.fillPlaceHolders(this.messages.getConfigurationSection("messages").getConfigurationSection(name).getString("cantWakeup","&csomeone's a deep sleeper"), player, this);
		Double chance = this.messages.getConfigurationSection("messages").getConfigurationSection(name).getDouble("chance");
		res = new Message(name, msg, hover_msg, response, cantWakeup, chance);

		return res;
	}


		//determine valid configuration variables
	private void checkConfigs() {
		//sleepDelay value
		if(			!this.config.isSet("sleepDelay") 
				||  !this.config.isInt("sleepDelay")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no sleepDelay value. Setting to default"); 
			this.config.set("sleepDelay", 60);
		}
		
		//startTime value
		if(			!this.config.isSet("startTime") 
				||  !this.config.isInt("startTime")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no stopTime value. Setting to default"); 
			this.config.set("startTime", 12010);
		}
		
		//stopTime value
		if(			!this.config.isSet("stopTime") 
				||  !this.config.isInt("stopTime")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no stopTime value. Setting to default"); 
			this.config.set("stopTime", 23992);
		}
		
		//increment value
		if(			!this.config.isSet("increment") 
				||  !this.config.isInt("increment")
				|| 	this.config.getInt("increment") < 1) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no increment value or invalid value. Setting to default"); 
			this.config.set("increment", 75);
		}
		
		//sleepCooldown value
		if(			!this.config.isSet("sleepCooldown") 
				||  !this.config.isInt("sleepCooldown")
				|| 	this.config.getInt("sleepCooldown") < 1) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no sleepCooldown value or invalid value. Setting to default"); 
			this.config.set("sleepCooldown", 2000);
		}

		//kickFromBed value
		if(			!this.config.isSet("kickFromBed") 
				||  !this.config.isBoolean("kickFromBed")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no kickFromBed value. Setting to default"); 
			this.config.set("kickFromBed", false);
		}
		
		//randomPerPlayer value
		if(			!this.config.isSet("randomPerPlayer") 
				||  !this.config.isBoolean("randomPerPlayer")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no randomPerPlayer value. Setting to default"); 
			this.config.set("randomPerPlayer", false);
		}
		
		//ResetAllStatistics value
		if(			!this.config.isSet("resetAllStatistics") 
				||  !this.config.isBoolean("resetAllStatistics")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no resetAllStatistics value. Setting to default"); 
			this.config.set("resetAllStatistics", true);
		}
		
		//doOtherWorlds value
		if(			!this.config.isSet("doOtherWorlds") 
				||  !this.config.isBoolean("doOtherWorlds")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no doOtherWorlds value. Setting to default"); 
			this.config.set("doOtherWorlds", false);
		}
		
		//doOtherDimensions value
		if(			!this.config.isSet("doOtherDimensions") 
				||  !this.config.isBoolean("doOtherDimensions")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no doOtherDimensions value. Setting to default"); 
			this.config.set("doOtherDimensions", false);
		}
		
		//onNoPlayerSleeping value
		if(			!this.messages.isSet("onNoPlayersSleeping") 
				||  !this.messages.isString("onNoPlayersSleeping")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no onNoPlayersSleeping value. Setting to default"); 
			this.messages.set("onNoPlayersSleeping", ChatColor.YELLOW.toString() + "No players sleeping!");
		}
		
		//cooldownMessage value
		if(			!this.messages.isSet("cooldownMessage") 
				||  !this.messages.isString("cooldownMessage")) {
			Bukkit.getConsoleSender().sendMessage("�4[OnePlayerSleep] error: no cooldownMessage value. Setting to default"); 
			this.messages.set("onNoPlayersSleeping", ChatColor.YELLOW.toString() + "You can't sleep again yet");
		}
	}
	
	//update config files based on version number
	private void updateConfig() {
		this.renameFileInPluginDir("config.yml","config.old.yml");
		plugin.saveResource("config.yml", false);
		Map<String, Object> oldValues = this.config.getValues(false);
		// Read default config to keep comments
		ArrayList<String> linesInDefaultConfig = new ArrayList<String>();
		try {
			Scanner scanner = new Scanner(
					new File(plugin.getDataFolder().getAbsolutePath() + File.separator + "config.yml"));
			while (scanner.hasNextLine()) {
				linesInDefaultConfig.add(scanner.nextLine() + "");
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		ArrayList<String> newLines = new ArrayList<String>();
		for (String line : linesInDefaultConfig) {
			String newline = line;
			if (line.startsWith("version:")) {
				newline = "version: 1.4";
			} else {
				for (String node : oldValues.keySet()) {
					if (line.startsWith(node + ":")) {
						String quotes = "";
						newline = node + ": " + quotes + oldValues.get(node).toString() + quotes;
						break;
					}
				}
			}
			if (newline != null)
				newLines.add(newline);
		}

		FileWriter fw;
		String[] linesArray = newLines.toArray(new String[linesInDefaultConfig.size()]);
		try {
			fw = new FileWriter(plugin.getDataFolder().getAbsolutePath() + File.separator + "config.yml");
			for (int i = 0; i < linesArray.length; i++) {
				fw.write(linesArray[i] + "\n");
			}
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return;
	}
	
	private void renameFileInPluginDir(String oldName, String newName) {
		File oldFile = new File(this.plugin.getDataFolder().getAbsolutePath() + File.separator + oldName);
		File newFile = new File(this.plugin.getDataFolder().getAbsolutePath() + File.separator + newName);
		try {
			Files.deleteIfExists(newFile.toPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
		oldFile.getAbsoluteFile().renameTo(newFile.getAbsoluteFile());
	}
}
