package OnePlayerSleep.API;

import OnePlayerSleep.API.types.MessageList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SleepAPI {
    /**
     * current api instance, set on plugin startup
     */
    private static SleepAPI instance;
    private final Map<String, MessageList> messageLists; //message lists by their names
    private final Map<UUID, String> worldMessageLists; //world to list mapping

    /**
     * @return current api instance
     */
    @NotNull
    public static SleepAPI getInstance() {
        return instance;
    }

    /**
     * instantiate and reset lists
     */
    public SleepAPI(){
        instance = this;
        messageLists = new HashMap<>();
        worldMessageLists = new HashMap<>();
    }

    /**
     * @param messageList reference to list of messages
     * @return false if list by that name already exists, true otherwise
     */
    public boolean putMessageList(MessageList messageList) {
        if(messageLists.containsKey(messageList.getName())) return false;
        messageLists.put(messageList.getName(),messageList);
        return true;
    }

    /**
     * @param worldId - world to map
     * @param name - message list to use
     * @return false if list by that name does not exist, true otherwise
     */
    public boolean mapMessageList(UUID worldId, String name) {
        if(!messageLists.containsKey(name)) return false;
        worldMessageLists.put(worldId,name);
        return true;
    }

    public MessageList getMessageList(String name) {
        return messageLists.get(name);
    }

    public MessageList getMessageList(UUID worldId) {
        return messageLists.get(worldMessageLists.get(worldId));
    }
}
