package OnePlayerSleep.API.types;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * MessageList
 *
 * a group of messages to select from.
 * Each world should map to at least one of these
 */
public interface MessageList {
    String getName();

    /**
     * @param message - message to add
     * @return successful addition
     */
    boolean add(Message message);

    /**
     * @param name - message to remove, by name
     * @return successful removal
     */
    boolean remove(String name);

    /**
     * @param name - message to get, by name
     * @return message found, or null if doesn't exist
     */
    @Nullable
    Message get(String name);


    /**
     * @return message picked, or null if no messages to pick from
     *
     * local implementation is O(1) if all chance values are equal, or O(log(n)) if unequal
     */
    @Nullable
    Message pickRandomMessage();
}
