package OnePlayerSleep.tools.Config;

import OnePlayerSleep.OnePlayerSleep.OnePlayerSleep;
import OnePlayerSleep.tools.SendMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class Lang {
    private FileConfiguration config;

    public Lang() {
        OnePlayerSleep plugin = OnePlayerSleep.getInstance();
        File f = new File(plugin.getDataFolder(), "lang.yml");
        if(!f.exists())
        {
            plugin.saveResource("lang.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(f);

        if( 	(this.config.getDouble("version") < 2.2) ) {
            SendMessage.sendMessage(Bukkit.getConsoleSender(),getLog("oldFile", "lang.yml"));
            update(plugin);

            f = new File(plugin.getDataFolder(), "lang.yml");
            this.config = YamlConfiguration.loadConfiguration(f);
        }
    }

    public String getLog(String key) {
        return this.config.getString(key,"");
    }

    public String getLog(String key, String placeholder) {
        String msg = this.getLog(key);

        String replace;
        switch (key) {
            case "oldFile": replace = "[filename]"; break;
            case "newWorld":
            case "invalidWorld":
            case "noGlobalPerms": replace = "[worldName]"; break;
            case "cooldownMessage":
            case "delayMessage": replace = "[time]"; break;
            case "unsafe":
            case "teleportMessage": replace =  "[numAttempts]"; break;
            case "badArg":
            case "noPerms": replace = "[arg]"; break;
            case "notEnoughMoney": replace = "[money]"; break;
            case "queueUpdate": replace = "[num]"; break;
            default: replace = "[placeholder]"; break;
        }

        return msg.replace(replace, placeholder);
    }

    public List<String> getLogList(String key) {
        return this.config.getStringList(key);
    }

    //update config files based on version number
    private void update(OnePlayerSleep plugin) {
        FileStuff.renameFiles(plugin,"lang");
        Map<String, Object> oldValues = this.config.getValues(false);
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
            StringBuilder newline = new StringBuilder(line);
            if (line.startsWith("version:")) {
                newline = new StringBuilder("version: 2.2");
            } else {
                for (String node : oldValues.keySet()) {
                    if(config.get(node) instanceof List) {
                        Set<Object> duplicateCheck = new HashSet<>();
                        newline = new StringBuilder(node + ": ");
                        for(Object obj : Objects.requireNonNull(config.getList(node))) {
                            if(duplicateCheck.contains(obj)) continue;
                            duplicateCheck.add(obj);
                            if(obj instanceof String) {
                                boolean doQuotes = Material.getMaterial((String) obj) == null;
                                if(doQuotes) newline.append("\n  - " + "\"").append(obj).append("\"");
                                else newline.append("\n  - ").append(obj);
                            }
                            else newline.append("\n  - ").append(obj);
                        }
                    }
                    if (line.startsWith(node + ":")) {
                        String quotes = "\"";
                        newline = new StringBuilder(node + ": " + quotes + oldValues.get(node).toString() + quotes);
                        break;
                    }
                }
            }
            newLines.add(newline.toString());
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
}
