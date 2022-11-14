package OnePlayerSleep.Listeners.customEventListeners;

import OnePlayerSleep.API.customEvents.AnnounceSleepEvent;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class AnnounceSleepListener implements Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    public void onAnnounceSleep(AnnounceSleepEvent event) {
        World world = event.getBed().getWorld();

    }
}
