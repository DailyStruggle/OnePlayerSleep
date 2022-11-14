package OnePlayerSleep.API.customEvents;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class AnnounceSleepEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS_LIST = new HandlerList();
    private boolean isCancelled;

    private final Player sleepingPlayer;
    private final Block bed;
    private final String message;

    public AnnounceSleepEvent(Player sleepingPlayer, Block bed, String message) {
        this.sleepingPlayer = sleepingPlayer;
        this.bed = bed;
        this.message = message;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        isCancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS_LIST;
    }

    public Player getSleepingPlayer() {
        return sleepingPlayer;
    }

    public Block getBed() {
        return bed;
    }

    public String getMessage() {
        return message;
    }
}
