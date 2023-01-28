package OnePlayerSleep.tools.Config;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.tools.SendMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import OnePlayerSleep.types.MessageImpl;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Config {
	private final OnePlayerSleep plugin;
	private FileConfiguration config;
	private FileConfiguration messages;
	private FileConfiguration worlds;
	private FileConfiguration lang;
	public String version;

	public String title;
	public String subtitle;
	public int fadeIn;
	public int fadeOut;
	public String actionbar;

	//key: message list name
	//value: lookup table for values
	private Map<String,NavigableMap<Double, MessageImpl>> messageLookup = new HashMap<>();

	//key: message list name
	//value: maximum random value to work with
	private Map<String,Double> totalChance = new HashMap<>();

	public Config() {
		this.plugin = OnePlayerSleep.getInstance();
		String s = this.plugin.getServer().getClass().getPackage().getName();
		this.version = s.substring(s.lastIndexOf('.')+1);
	}
	
	
	public void refreshConfigs() {
		//load lang.yml file first
		File f = new File(this.plugin.getDataFolder(), "lang.yml");
		if(!f.exists())
		{
			plugin.saveResource("lang.yml", false);
		}
		this.lang = YamlConfiguration.loadConfiguration(f);

		if( 	(this.lang.getDouble("version") < 1.0) ) {
			SendMessage.sendMessage(Bukkit.getConsoleSender(),this.getLog("oldFile", "lang.yml"));
			updateLang();

			f = new File(this.plugin.getDataFolder(), "lang.yml");
			this.lang = YamlConfiguration.loadConfiguration(f);
		}

		//load config.yml file
		f = new File(this.plugin.getDataFolder(), "config.yml");
		if(!f.exists())
		{
			plugin.saveResource("config.yml", false);
		}
		this.config = YamlConfiguration.loadConfiguration(f);

		if( 	(this.config.getDouble("version") < 2.1) ) {
			SendMessage.sendMessage(Bukkit.getConsoleSender(),this.getLog("oldFile", "config.yml"));

			updateConfig();

			f = new File(this.plugin.getDataFolder(), "config.yml");
			this.config = YamlConfiguration.loadConfiguration(f);
		}

		//load messages.yml file
		f = new File(this.plugin.getDataFolder(), "messages.yml");
		if(!f.exists())
		{
			plugin.saveResource("messages.yml", false);
		}
		this.messages = YamlConfiguration.loadConfiguration(f);

		if( 	(this.messages.getDouble("version") < 2.2) ) {
			SendMessage.sendMessage(Bukkit.getConsoleSender(),this.getLog("oldFile", "messages.yml"));
			this.renameFileInPluginDir("messages.yml","messages.old.yml");
			
			this.plugin.saveResource("messages.yml", false);
			this.messages = YamlConfiguration.loadConfiguration(f);
		}

		this.title = this.messages.getString("title","");
		this.subtitle = this.messages.getString("subtitle","");
		this.fadeIn = this.messages.getInt("fadeIn",0);
		this.fadeOut = this.messages.getInt("fadeOut",0);

		this.actionbar = this.messages.getString("actiobar","");

		//load up message map for quick selection later
		ConfigurationSection messages = this.messages.getConfigurationSection("messages");
		if(messages == null) {
			new IllegalStateException("missing messages section").printStackTrace();
			return;
		}
		Set<String> messageListNames = messages.getKeys(false);
		this.messageLookup = new HashMap<>();
		this.totalChance = new HashMap<>();
		for(String messageListName : messageListNames) {
			ConfigurationSection section = messages.getConfigurationSection(messageListName);
			if(section == null) {
				new IllegalStateException("missing message section - " + messageListName).printStackTrace();
				return;
			}
			Set<String> messageNames = section.getKeys(false);

			double totalChance = 0.0;
			NavigableMap<Double, MessageImpl> messageLookup = new TreeMap<>();
			for ( String messageName : messageNames) {
				ConfigurationSection message = section.getConfigurationSection(messageName);
				String msg 			= message.getString("global", "");
				String hover_msg 	= message.getString("hover", "");
				String response 	= message.getString("wakeup", "");
				String cantWakeup 	= message.getString("cantWakeup", "");
				String cancel 	= message.getString("cancel", "");
				double chance = message.getDouble("chance", 1.0);
				messageLookup.put(totalChance, new MessageImpl("", messageListName+"."+message.getName(), msg, hover_msg, response, cantWakeup, cancel, chance));
				totalChance += chance;
			}
			this.messageLookup.putIfAbsent(messageListName,messageLookup);
			this.totalChance.putIfAbsent(messageListName,totalChance);
		}


		//load worlds.yml file
		f = new File(this.plugin.getDataFolder(), "worlds.yml");
		if(!f.exists())
		{
			plugin.saveResource("worlds.yml", false);
		}
		this.worlds = YamlConfiguration.loadConfiguration(f);

		if( 	(this.worlds.getDouble("version") < 1.2) ) {
			SendMessage.sendMessage(Bukkit.getConsoleSender(),this.getLog("oldFile", "worlds.yml"));
			this.renameFileInPluginDir("worlds.yml","worlds.old.yml");

			this.plugin.saveResource("worlds.yml", false);
			this.worlds = YamlConfiguration.loadConfiguration(f);
		}

		//update world list and save
		Bukkit.getScheduler().runTaskAsynchronously(OnePlayerSleep.getInstance(),this::fillWorldsFile);
	}

	public MessageImpl pickRandomMessage(World world, String playerName) {
		if(!this.worlds.contains(world.getName()))
			this.worlds.set(world.getName(),this.worlds.getConfigurationSection("default"));
		MessageImpl res;
		String listName = this.worlds.getConfigurationSection(world.getName()).getString("msgGroup");
		res = pickRandomMessage(listName, playerName);
		res.setWorld(world.getName());
		return res;
	}

	public MessageImpl pickRandomMessage(String listName, String playerName) {
		if(!this.messages.getConfigurationSection("messages").contains(listName)){
			SendMessage.sendMessage(Bukkit.getConsoleSender(),this.getLog("invalidList", listName));
			return null;
		}

		Random r = new Random();
		Double randomValue = (this.totalChance.get(listName)) * r.nextDouble();
		MessageImpl res = this.messageLookup.get(listName).floorEntry(randomValue).getValue();

		String msg = fillPlaceHolders(res.msg.getText(), playerName);
		String hover_msg = fillPlaceHolders(res.hoverText, playerName);
		String wakeup = fillPlaceHolders(res.wakeup, playerName);
		String cantWakeup = fillPlaceHolders(res.cantWakeup, playerName);
		String cancel = fillPlaceHolders(res.cancel, playerName);
		Double chance = res.chance;
		res = new MessageImpl("", res.name, msg, hover_msg, wakeup, cantWakeup, cancel, chance);

		return res;
	}

	public MessageImpl getMessage(String listName, String messageName, String playerName) {
		MessageImpl res;

		ConfigurationSection cfg = this.messages.getConfigurationSection("messages").getConfigurationSection(listName).getConfigurationSection(messageName);
		if(cfg != null) {
			String msg = fillPlaceHolders(cfg.getString("global", ""), playerName);
			String hover_msg = fillPlaceHolders(cfg.getString("hover", ""), playerName);
			String response = fillPlaceHolders(cfg.getString("wakeup", ""), playerName);
			String cantWakeup = fillPlaceHolders(cfg.getString("cantWakeup", ""), playerName);
			String cancel = fillPlaceHolders(cfg.getString("cancel", ""), playerName);
			Double chance = cfg.getDouble("chance");
			res = new MessageImpl("", listName+"."+messageName, msg, hover_msg, response, cantWakeup, cancel, chance);
		} else res = null;

		return res;
	}
	
	//update config files based on version number
	private void updateLang() {
		this.renameFileInPluginDir("lang.yml","lang.old.yml");
		plugin.saveResource("lang.yml", false);
		Map<String, Object> oldValues = this.lang.getValues(false);
		// Read default config to keep comments
		ArrayList<String> linesInDefaultConfig = new ArrayList<>();
		try {
			Scanner scanner = new Scanner(
					new File(plugin.getDataFolder().getAbsolutePath() + File.separator + "lang.yml"));
			while (scanner.hasNextLine()) {
				linesInDefaultConfig.add(scanner.nextLine() + "");
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		ArrayList<String> newLines = new ArrayList<>();
		for (String line : linesInDefaultConfig) {
			String newline = line;
			if (line.startsWith("version:")) {
				newline = "version: 1.0";
			} else {
				for (String node : oldValues.keySet()) {
					if (line.startsWith(node + ":")) {
						String quotes = "\"";
						newline = node + ": " + quotes + oldValues.get(node).toString() + quotes;
						break;
					}
				}
			}
			newLines.add(newline);
		}

		FileWriter fw;
		String[] linesArray = newLines.toArray(new String[linesInDefaultConfig.size()]);
		try {
			fw = new FileWriter(plugin.getDataFolder().getAbsolutePath() + File.separator + "lang.yml");
			for (String s : linesArray) {
				fw.write(s + "\n");
			}
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void updateConfig() {
		this.renameFileInPluginDir("config.yml","config.old.yml");
		plugin.saveResource("config.yml", false);
		Map<String, Object> oldValues = this.config.getValues(false);
		// Read default config to keep comments
		ArrayList<String> linesInDefaultConfig = new ArrayList<>();
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

		ArrayList<String> newLines = new ArrayList<>();
		for (String line : linesInDefaultConfig) {
			String newline = line;
			if (line.startsWith("version:")) {
				newline = "version: 2.1";
			} else {
				for (String node : oldValues.keySet()) {
					if (line.startsWith(node + ":")) {
						String quotes = "";
						newline = node + ": " + quotes + oldValues.get(node).toString() + quotes;
						break;
					}
				}
			}
			newLines.add(newline);
		}

		FileWriter fw;
		String[] linesArray = newLines.toArray(new String[linesInDefaultConfig.size()]);
		try {
			fw = new FileWriter(plugin.getDataFolder().getAbsolutePath() + File.separator + "config.yml");
			for (String s : linesArray) {
				fw.write(s + "\n");
			}
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void fillWorldsFile() {
		this.worlds = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "worlds.yml"));
		renameFileInPluginDir("worlds.yml","worlds.temp.yml");

		ArrayList<String> linesInWorlds = new ArrayList<>();
		ConfigurationSection worldsConfigurationSection = this.worlds.getConfigurationSection("default");
		Objects.requireNonNull(worldsConfigurationSection);
		String defaultMessageGroup = worldsConfigurationSection.getString("msgGroup");
		List<String> defaultTarget = worldsConfigurationSection.getStringList("sendTo");
		List<String> defaultSync = worldsConfigurationSection.getStringList("timeSync");
		Integer defaultStartTime = worldsConfigurationSection.getInt("startTime");
		Integer defaultStopTime = worldsConfigurationSection.getInt("stopTime");

		try {
			Scanner scanner = new Scanner(
					new File(plugin.getDataFolder().getAbsolutePath() + File.separator + "worlds.temp.yml"));
			//for each line in original messages file
			while (scanner.hasNextLine()) {
				String s = scanner.nextLine();

				//append at first blank line
				if(!s.matches(".*[a-z].*")) {
					//for each missing world, add some default data
					for(World w : Bukkit.getWorlds()) {
						String worldName = w.getName();
						if(this.worlds.contains(worldName)) continue;
						this.worlds.set(worldName, worldsConfigurationSection);

						if(linesInWorlds.get(linesInWorlds.size()-1).length() < 4)
							linesInWorlds.set(linesInWorlds.size()-1,"    " + worldName + ":");
						else linesInWorlds.add(worldName + ":");
						linesInWorlds.add("    name: \"[" + worldName + "]\"");
						linesInWorlds.add("    msgGroup: \"" + defaultMessageGroup + "\"");
						linesInWorlds.add("    sendTo:");
						linesInWorlds.add("        - \"" + worldName + "\"");
						for(String target : defaultTarget)
						{
							if(target.equals(worldName)) continue;
							linesInWorlds.add("        - \"" + target + "\"");
						}

						linesInWorlds.add("    timeSync:");
						linesInWorlds.add("        - \"" + worldName + "\"");
						for(String sync : defaultSync)
						{
							if(sync.equals(worldName)) continue;
							linesInWorlds.add("        - \"" + sync + "\"");
						}

						linesInWorlds.add("    startTime: " + defaultStartTime);
						linesInWorlds.add("    stopTime: " + defaultStopTime);

						if(!w.getEnvironment().equals(World.Environment.NORMAL))
						{
							linesInWorlds.add("    cancelBedExplode: false");
						}
					}
				}

				//add line
				linesInWorlds.add(s + "");
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}


		FileWriter fw;
		String[] linesArray = linesInWorlds.toArray(new String[linesInWorlds.size()]);
		try {
			fw = new FileWriter(plugin.getDataFolder().getAbsolutePath() + File.separator + "worlds.yml");
			for (String s : linesArray) {
				fw.write(s + "\n");
			}
			fw.close();
			File f = new File(this.plugin.getDataFolder().getAbsolutePath() + File.separator + "worlds.temp.yml");
			f.delete();
		} catch (IOException e) {
			e.printStackTrace();
		}

		//-------------UPDATE INTERNAL VERSION ACCORDINGLY-------------
		this.worlds = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "worlds.yml"));

		//table of worlds ordered by dimension for quick lookup
		Map<World.Environment,List<String>> dimWorldList = new HashMap<>();
		for(World.Environment e : World.Environment.values()) {
			dimWorldList.put(e, new ArrayList<>());
		}
		for(String worldName : this.worlds.getKeys(false)) {
			if(worldName.equals("default") || worldName.equals("version")) continue;
			if(this.checkWorldExists(worldName))
				dimWorldList.get(Bukkit.getWorld(worldName).getEnvironment()).add(worldName);
		}

		//reconfigure placeholders as world names
		for(String worldName : this.worlds.getKeys(false)) {
			if(worldName.equals("default") || worldName.equals("version")) continue;
			List<String> sendTo = this.worlds.getConfigurationSection(worldName).getStringList("sendTo");
			List<String> timeSync = this.worlds.getConfigurationSection(worldName).getStringList("timeSync");
			List<String> sendToNew = new ArrayList<>();
			List<String> timeSyncNew = new ArrayList<>();
			for(String p : sendTo) {
				if(p.equals("ALL")) {
					sendToNew.addAll(Bukkit.getWorlds().stream().map(world -> world.getName()).collect(Collectors.toList()));
					continue;
				}
				try{
					World.Environment env = World.Environment.valueOf(p);
					sendToNew.addAll(dimWorldList.get(env));
					continue;
				}
				catch (IllegalArgumentException ex) {
					//was not an environment, do nothing
				}
				sendToNew.add(p);
			}
			for(String p : timeSync) {
				if(p.equals("ALL")) {
					timeSyncNew.addAll(Bukkit.getWorlds().stream().map(world -> world.getName()).collect(Collectors.toList()));
					continue;
				}
				try{
					World.Environment env = World.Environment.valueOf(p);
					timeSyncNew.addAll(dimWorldList.get(env));
					continue;
				}
				catch (IllegalArgumentException ex) {
					//was not an environment, do nothing
				}
				timeSyncNew.add(p);
			}

			this.worlds.getConfigurationSection(worldName).set("sendTo", sendToNew);
			this.worlds.getConfigurationSection(worldName).set("timeSync", timeSyncNew);

			this.getMsgToWorlds(worldName);
		}
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

	public List<String> getMessageNames(String listName) {
		List<String> res = new ArrayList<>();
		if(listName == null || listName.isEmpty()) {
			listName = this.worlds.getConfigurationSection(this.getServerWorldName()).getString("msgGroup");
		}
		if(!this.messages.getConfigurationSection("messages").contains(listName)) {
			SendMessage.sendMessage(Bukkit.getConsoleSender(),getLog("invalidList",listName));
		}
		res.addAll(this.messages.getConfigurationSection("messages").getConfigurationSection(listName).getKeys(false));
		return res;
	}

	public List<String> getMsgToWorlds(String worldName) {
		ConfigurationSection section = worlds.getConfigurationSection(worldName);
		if(section == null) {
			fillWorldsFile();
			section = worlds.getConfigurationSection(worldName);
			if(section == null){
				new IllegalStateException("missing world - " + worldName).printStackTrace();
				return new ArrayList<>();
			}
		}
		return section.getStringList("sendTo");
	}

	public List<String> getSyncWorlds(String worldName) {
		ConfigurationSection section = worlds.getConfigurationSection(worldName);
		if(section == null) {
			fillWorldsFile();
			section = worlds.getConfigurationSection(worldName);
			if(section == null){
				new IllegalStateException("missing world - " + worldName).printStackTrace();
				return new ArrayList<>();
			}
		}
		return section.getStringList("timeSync");
	}

	public String fillPlaceHolders(String res, World world) {
		if(res == null || res.isEmpty()) return res;
		if(world != null) {
			String worldName = this.getWorldPlaceholder(world.getName());
			String dimName = this.getDimensionPlaceholder(world.getEnvironment());
			res = res.replace("[world]", worldName);
			res = res.replace("[dimension]", dimName);
		}

		res = ChatColor.translateAlternateColorCodes('&', res);
		res = Hex2Color(res);
		return res;
	}

	public String fillPlaceHolders(String res, String playerName) {
		if(res == null || res.isEmpty()) return res;

		boolean isPlayer = !playerName.equals(this.getServerName());

		Player player = Bukkit.getPlayer(playerName);
		if(player == null) isPlayer = false;

		playerName = (isPlayer)
				? playerName
				: this.messages.getConfigurationSection("server").getString("name");
		String playerDisplayName = (isPlayer)
				? player.getDisplayName()
				: playerName;
		World world = (isPlayer)
				? player.getWorld()
				: Bukkit.getWorld( this.getServerWorldName() );

		res = res.replace("[username]", playerName);
		res = res.replace("[displayname]", playerDisplayName);
		if(world!=null) {
			String worldName = this.getWorldPlaceholder(world.getName());
			String dimName = this.getDimensionPlaceholder(world.getEnvironment());

			if(worldName != null) res = res.replace("[world]", worldName);
			if(dimName!=null) res = res.replace("[dimension]", dimName);
		}

		//bukkit color codes
		res = ChatColor.translateAlternateColorCodes('&', res);
		res = Hex2Color(res);
		return res;
	}

	public String getWorldPlaceholder(String worldName) {
		return this.worlds.getConfigurationSection(worldName).getString("name");
	}
	public String getDimensionPlaceholder(World.Environment environment) {
		return this.messages.getConfigurationSection("dimensions").getString(environment.name());
	}

	public String getServerName() {
		return this.messages.getConfigurationSection("server").getString("name");
	}

	public String getServerWorldName() {
		return this.messages.getConfigurationSection("server").getString("world", "world");
	}

	public Boolean logMessages() {
		return this.config.getBoolean("logMessages");
	}

	public Integer getMinPlayers() {
		return this.config.getInt("minPlayers", 2);
	}

	public String getLog(String key) {
		String msg = this.lang.getString(key);
//		msg = ChatColor.translateAlternateColorCodes('&',msg);
		return msg;
	}

	public String getLog(String key, String placeholder) {
		String msg = this.getLog(key);

		String replace;
		switch(key) {
			case "oldFile": replace = "[filename]"; break;
			case "noGlobalPerms":
			case "invalidWorld": replace = "[worldName]"; break;
			case "invalidList": replace = "[list]"; break;
			case "invalidMsg": replace = "[msg]"; break;
			case "noPerms":
			case "badArg": replace = "[arg]"; break;
			default: replace = "[placeholder]";
		}

		return fillPlaceHolders(msg.replace(replace, placeholder),getServerName());
	}

	public Integer getStartTime (String worldName) {
		return this.worlds.getConfigurationSection(worldName).getInt("startTime", 12010);
	}

	public Integer getStopTime (String worldName) {
		return this.worlds.getConfigurationSection(worldName).getInt("stopTime", 23992);
	}

	public Boolean getCancelBedExplode(String worldName) {
		return this.worlds.getConfigurationSection(worldName).getBoolean("cancelBedExplode", false);
	}

	public Object getConfigValue(String name, Object def) {
		Object o = this.config.get(name, def);
		if(o.getClass().equals(def.getClass())) return o;
		return def;
	}

	public Set<String> getMessageListNames() {
		return this.messages.getConfigurationSection("messages").getKeys(false);
	}

	public Boolean checkWorldExists(String worldName) {
		Boolean bukkitWorldExists = Bukkit.getWorld(worldName)!=null;
		Boolean worldKnown = this.worlds.contains(worldName);
		if( !bukkitWorldExists ) {
			SendMessage.sendMessage(Bukkit.getConsoleSender(),this.getLog("invalidWorld", worldName));
			return false;
		}
		else if( !worldKnown ) {
			this.fillWorldsFile(); //not optimal but it works
		}
		return true;
	}

	private static String Hex2Color(String text) {
		Pattern pattern = Pattern.compile("(&?#[0-9a-fA-F]{6})");
		Matcher matcher = pattern.matcher(text);
		while (matcher.find()) {
			String hexColor = text.substring(matcher.start(), matcher.end());
			hexColor = hexColor.replace("&", "");
			StringBuilder bukkitColorCode = new StringBuilder('\u00A7' + "x");
			for (int i = 1; i < hexColor.length(); i++) {
				bukkitColorCode.append(String.valueOf('\u00A7') + hexColor.charAt(i));
			}
			String bukkitColor = bukkitColorCode.toString().toLowerCase();
			text = text.replaceAll(hexColor, bukkitColor);
			matcher.reset(text);
		}
		return text;
	}
}
