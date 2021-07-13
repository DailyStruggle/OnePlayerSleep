package OnePlayerSleep.tools;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import OnePlayerSleep.types.Message;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Config {
	public FileConfiguration config;
	public FileConfiguration messages;
	public FileConfiguration worlds;
	public String version;

	private static final Pattern dims = Pattern.compile("_nether|_the_end", Pattern.CASE_INSENSITIVE);

	private Map<String,List<String>> messageNames = new HashMap<>();
	private Map<String,ArrayList<Double>> chanceRanges = new HashMap<>();
	private Map<String,ArrayList<Message>> messageArray = new HashMap<>();
	private Map<String,Double> totalChance = new HashMap<>();
	private final OnePlayerSleep plugin;

	public Config(OnePlayerSleep plugin) {
		this.plugin = plugin;
		String s = this.plugin.getServer().getClass().getPackage().getName();
		this.version = s.substring(s.lastIndexOf('.')+1);
	}
	
	
	public void refreshConfigs() {
		//load config.yml file
		File f = new File(this.plugin.getDataFolder(), "config.yml");
		if(!f.exists())
		{
			plugin.saveResource("config.yml", false);
		}
		this.config = YamlConfiguration.loadConfiguration(f);

		if( 	(this.config.getDouble("version") < 2.0) ) {
			Bukkit.getConsoleSender().sendMessage("�b[OnePlayerSleep] old config.yml detected. Updating");

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

		if( 	(this.messages.getDouble("version") < 2.0) ) {
			Bukkit.getConsoleSender().sendMessage("�b[OnePlayerSleep] old messages.yml detected. Getting a newer version");
			this.renameFileInPluginDir("messages.yml","messages.old.yml");
			
			this.plugin.saveResource("messages.yml", false);
			this.messages = YamlConfiguration.loadConfiguration(f);
		}
		this.messages.set("onNoPlayersSleeping", ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.messages.getString("onNoPlayersSleeping"))));
		this.messages.set("cooldownMessage", ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.messages.getString("cooldownMessage"))));

		//load worlds.yml file
		f = new File(this.plugin.getDataFolder(), "worlds.yml");
		if(!f.exists())
		{
			plugin.saveResource("worlds.yml", false);
		}
		this.worlds = YamlConfiguration.loadConfiguration(f);

		if( 	(this.worlds.getDouble("version") < 1.0) ) {
			Bukkit.getConsoleSender().sendMessage("�b[OnePlayerSleep] old worlds.yml detected. Getting a newer version");
			this.renameFileInPluginDir("worlds.yml","worlds.old.yml");

			this.plugin.saveResource("worlds.yml", false);
			this.messages = YamlConfiguration.loadConfiguration(f);
		}

		//update world list and save
		this.fillWorldsFile();
		this.worlds = YamlConfiguration.loadConfiguration(f);

		//load up message map for quick selection later
		Set<String> messageListNames = this.messages.getConfigurationSection("messages").getKeys(false);
		for(String messageListName : messageListNames) {
			Set<String> messageNames = this.messages.getConfigurationSection("messages").getConfigurationSection(messageListName).getKeys(false);


			Double totalChance = 0.0;
			ArrayList<Message> messageArray = new ArrayList<>();
			ArrayList<Double> chanceRanges = new ArrayList<>();
			chanceRanges.add(0, 0.0);
			int i = 0;
			for ( String messageName : messageNames) {
				ConfigurationSection message = this.messages.getConfigurationSection("messages")
						.getConfigurationSection(messageListName).getConfigurationSection(messageName);
				String msg 			= ChatColor.translateAlternateColorCodes('&', message.getString("global", "[player] &bis sleeping"));
				String hover_msg 	= ChatColor.translateAlternateColorCodes('&', message.getString("hover", "&eWake up!"));
				String response 	= ChatColor.translateAlternateColorCodes('&', message.getString("wakeup", "[player] says &cWake up!"));
				String cantWakeup 	= ChatColor.translateAlternateColorCodes('&', message.getString("cantWakeup", "&csomeone's a deep sleeper"));
				Double chance = message.getDouble("chance");
				messageArray.add(new Message(new String(), message.getName(), msg, hover_msg, response, cantWakeup, chance) );
				totalChance += chance;
				chanceRanges.add(chanceRanges.get(i) + chance);
				messageNames.add(message.getName());
				i++;
			}
			this.messageArray.putIfAbsent(messageListName,messageArray);
			this.chanceRanges.putIfAbsent(messageListName,chanceRanges);
			this.totalChance.putIfAbsent(messageListName,totalChance);
			this.messageNames.putIfAbsent(messageListName,messageNames.stream().toList());
		}

		//table of worlds ordered by dimension for quick lookup
		Map<World.Environment,List<String>> dimWorldList = new HashMap<>();
		for(World.Environment e : World.Environment.values()) {
			dimWorldList.put(e, new ArrayList<>());
		}
		for(String worldName : this.worlds.getKeys(false)) {
			if(worldName.equals("default") || worldName.equals("version")) continue;
			dimWorldList.get(Bukkit.getWorld(worldName).getEnvironment()).add(worldName);
		}

		//reconfigure placeholders as world names
		for(String worldName : this.worlds.getKeys(false)) {
			if(worldName.equals("default") || worldName.equals("version")) continue;
			List<String> sendTo = this.worlds.getConfigurationSection(worldName).getStringList("sendTo");
			List<String> timeSync = this.worlds.getConfigurationSection(worldName).getStringList("timeSync");
			for(String p : sendTo) {
				if(this.worlds.getKeys(false).contains(p)) continue;
				if(p.equals("ALL")) {
					sendTo.addAll(Bukkit.getWorlds().stream().map(world -> world.getName()).collect(Collectors.toList()));
					continue;
				}
				World.Environment env = World.Environment.valueOf(p);
				if(env != null) {
					sendTo.addAll(dimWorldList.get(env));
				}
			}
			for(String p : timeSync) {
				if(this.worlds.getKeys(false).contains(p)) continue;
				if(p.equals("ALL")) {
					timeSync.addAll(Bukkit.getWorlds().stream().map(world -> world.getName()).collect(Collectors.toList()));
					continue;
				}
				World.Environment env = World.Environment.valueOf(p);
				if(env != null) {
					timeSync.addAll(dimWorldList.get(env));
				}
			}
			this.worlds.getConfigurationSection(worldName).set("sendTo", sendTo);
			this.worlds.getConfigurationSection(worldName).set("sendTo", timeSync);
		}

		String msg = ChatColor.translateAlternateColorCodes('&', this.messages.getString("onNoPlayersSleeping"));
		this.messages.set("onNoPlayersSleeping", msg);
	}

	public Message pickRandomMessage(World world, String playerName) {
		Message res;
		String worldName = world.getName();
		if(!this.worlds.contains(worldName))
			return null;
		String listName = this.worlds.getConfigurationSection(worldName).getString("msgGroup");
		res = pickRandomMessage(listName, playerName);
		res.setWorld(world.getName());
		return res;
	}

	public Message pickRandomMessage(String listName, String playerName) {
		if(!this.messages.getConfigurationSection("messages").contains(listName))
			return null;
		ArrayList<Double> chanceRanges = this.chanceRanges.get(listName);
		int numMessages = this.messageArray.get(listName).size();
		if(numMessages == 1) return messageArray.get(listName).get(0);

		//pick a random float
		Random r = new Random();
		Double randomValue = (this.totalChance.get(listName)) * r.nextDouble();

		//lookup iterator by binary search of ranges
		int iter_low = 0;
		int iter_high = chanceRanges.size();

		int i = iter_high/2;
		int j;
		for(j = 0; j < 1000; j++) { //cap at 1k in case of infinite loop
			double range_low = chanceRanges.get(i);
			double range_high = chanceRanges.get(i+1);
			if(randomValue <= range_low) iter_high = i;
			else if(randomValue > range_high) iter_low = i;
			else break;
			i = (iter_high - iter_low)/2 + iter_low;
		}
		if(j>=1000) {
			Bukkit.getLogger().log(Level.SEVERE, "[sleep] Failed to find a message within 1000 iterations. Using first message");
			i = 0;
		}
		Message res = messageArray.get(listName).get(i);
		ConfigurationSection cfg = this.messages.getConfigurationSection("messages").getConfigurationSection(listName).getConfigurationSection(res.name);
		if(cfg != null) {
			String msg = fillPlaceHolders(cfg.getString("global", "[player] &bis sleeping"), playerName);
			String hover_msg = fillPlaceHolders(cfg.getString("hover", "&eWake up!"), playerName);
			String response = fillPlaceHolders(cfg.getString("wakeup", "[player] says &cWake up!"), playerName);
			String cantWakeup = fillPlaceHolders(cfg.getString("cantWakeup", "&csomeone's a deep sleeper"), playerName);
			Double chance = cfg.getDouble("chance");
			res = new Message(new String(), (listName+"."+res.name), msg, hover_msg, response, cantWakeup, chance);
		} else res = null;
		return res;
	}

	public Message getMessage(String listName, String messageName, String playerName) {
		Message res;

		ConfigurationSection cfg = this.messages.getConfigurationSection("messages").getConfigurationSection(listName).getConfigurationSection(messageName);
		if(cfg != null) {
			String msg = fillPlaceHolders(cfg.getString("global", "[player] &bis sleeping"), playerName);
			String hover_msg = fillPlaceHolders(cfg.getString("hover", "&eWake up!"), playerName);
			String response = fillPlaceHolders(cfg.getString("wakeup", "[player] says &cWake up!"), playerName);
			String cantWakeup = fillPlaceHolders(cfg.getString("cantWakeup", "&csomeone's a deep sleeper"), playerName);
			Double chance = cfg.getDouble("chance");
			res = new Message(new String(), messageName, msg, hover_msg, response, cantWakeup, chance);
		} else res = null;

		return res;
	}
	
	//update config files based on version number
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
				newline = "version: 2.0";
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

	private void fillWorldsFile() {
		renameFileInPluginDir("worlds.yml","worlds.temp.yml");

		ArrayList<String> linesInWorlds = new ArrayList<>();
		String defaultMessageGroup = this.worlds.getConfigurationSection("default").getString("msgGroup");
		List<String> defaultTarget = this.worlds.getConfigurationSection("default").getStringList("sendTo");
		List<String> defaultSync = this.worlds.getConfigurationSection("default").getStringList("timeSync");

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
						if(this.worlds.contains(w.getName())) continue;
						String worldName = w.getName();
						if(linesInWorlds.get(linesInWorlds.size()-1).length() < 4)
							linesInWorlds.set(linesInWorlds.size()-1,"    " + worldName + ":");
						else linesInWorlds.add(worldName + ":");
						linesInWorlds.add("    placeholder: \"" + worldName + "\"");
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

						if(Bukkit.getWorld(worldName).getEnvironment() != World.Environment.NORMAL)
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
			listName = this.worlds.getConfigurationSection("world").getString("msgGroup");
		}
		res.addAll( messageNames.get( listName ) );
		return res;
	}

	public List<String> getMsgToWorlds(String worldName) {
		if(worldName == null || worldName.isEmpty()) {
			worldName = this.messages.getConfigurationSection("server").getString("world");
		}
		return worlds.getConfigurationSection(worldName).getStringList("sendTo");
	}

	public List<String> getSyncWorlds(String worldName) {
		if(worldName == null || worldName.isEmpty()) {
			worldName = this.messages.getConfigurationSection("server").getString("world");
		}
		return worlds.getConfigurationSection(worldName).getStringList("timeSync");
	}

	public String fillPlaceHolders(String res, String playerName) {
		if(res.isEmpty()) return res;

		Boolean isPlayer = !playerName.equals(this.getServerName());

		playerName = (isPlayer)
				? playerName
				: this.messages.getConfigurationSection("server").getString("name");
		String playerDisplayName = (isPlayer)
				? Bukkit.getPlayer(playerName).getDisplayName()
				: playerName;
		World world = (isPlayer)
				? Bukkit.getPlayer(playerName).getWorld()
				: Bukkit.getWorld( messages.getConfigurationSection("server").getString("world"));

		String worldName = dims.matcher(world.getName()).replaceAll("");
		String dimName = messages.getConfigurationSection("dimensions").getString(world.getEnvironment().name());

		res = res.replace("[username]", playerName);
		res = res.replace("[displayname]", playerDisplayName);
		if(worldName != null) res = res.replace("[world]", worldName);
		if(dimName!=null) res = res.replace("[dimension]", dimName);

		//bukkit color codes
		res = ChatColor.translateAlternateColorCodes('&',res);
		return res;
	}

	public String getDimensionPlaceholder(World.Environment environment) {
		return this.messages.getConfigurationSection("dimensions").getString(environment.name());
	}

	public String getServerName() {
		return this.messages.getConfigurationSection("server").getString("name");
	}

	public String getServerWorldName() {
		return this.messages.getConfigurationSection("server").getString("world");
	}

	public Boolean logMessages() {
		return this.config.getBoolean("logMessages");
	}

	public Integer getMinPlayers() {
		return this.config.getInt("minPlayers", 2);
	}
}
