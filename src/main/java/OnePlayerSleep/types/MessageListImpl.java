package OnePlayerSleep.types;

import OnePlayerSleep.API.types.Message;
import OnePlayerSleep.API.types.MessageList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;

public class MessageListImpl implements MessageList {
    private final String name;
    private boolean isConstant;
    private final List<Message> messages;
    private ConcurrentSkipListMap<Double,Message> messageTable;
    private final ConcurrentHashMap<String,Message> messageNameTable;

    public MessageListImpl(String name) {
        this.name = name;
        messageTable = new ConcurrentSkipListMap<>();
        messageNameTable = new ConcurrentHashMap<>();
        messages = new ArrayList<>();
        isConstant = true;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean add(Message message) {
        if(messageNameTable.containsKey(message.getName())) return false;

        boolean success = messages.add(message);
        success &= messageNameTable.put(message.getName(),message) != null;

        Map.Entry<Double,Message> lastEntry = messageTable.lastEntry();
        double curr = lastEntry != null ? lastEntry.getKey() : 0;
        isConstant = lastEntry == null || lastEntry.getValue().getChance().equals(message.getChance());

        success &= messageTable.put(curr + message.getChance(),message) != null;

        if(!success) { //cleanup on failure
            messages.remove(message);
            messageNameTable.remove(message.getName());
            messageTable.remove(curr);
        }
        return success;
    }

    @Override
    public boolean remove(String name) {
        if(!messageNameTable.containsKey(name)) return true;
        boolean success = messages.removeIf(message -> message.getName().equals(name));
        success = success && messageNameTable.remove(name)!=null;

        //reconstruct message table
        ConcurrentSkipListMap<Double,Message> newMessageTable = new ConcurrentSkipListMap<>();
        isConstant = true;
        messageTable.forEach((aDouble, message) -> {
            if(!message.getName().equals(name)) {
                Map.Entry<Double,Message> lastEntry = newMessageTable.lastEntry();
                double curr = lastEntry != null ? lastEntry.getKey() : 0;
                isConstant = lastEntry == null || lastEntry.getValue().getChance().equals(message.getChance());
                newMessageTable.put(curr + message.getChance(),message);
            }
        });
        success &= newMessageTable.size() == messageTable.size()-1;
        messageTable = newMessageTable;
        return success;
    }

    @Override
    public @Nullable Message get(String name) {
        return null;
    }

    @Override
    public @Nullable Message pickRandomMessage() {

        return null;
    }

    private Message pickConstant() {
        int range = messages.size();
        int select = ThreadLocalRandom.current().nextInt(range);
        return messages.get(select);
    }

    private Message pickLogarithmic() {
        Map.Entry<Double,Message> lastEntry = messageTable.lastEntry();
        Double range = lastEntry.getKey() + lastEntry.getValue().getChance();
        double select = ThreadLocalRandom.current().nextDouble(range);
        return messageTable.floorEntry(select).getValue();
    }
}
