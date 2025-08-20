package com.minekartastudio.kartasoloslumber.Listeners.spigotEventListeners;

import com.minekartastudio.kartasoloslumber.KartaSoloSlumber;
import com.minekartastudio.kartasoloslumber.tools.Config.Config;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.world.TimeSkipEvent;

public class OnNightSkip implements Listener {
    private final KartaSoloSlumber plugin;

    public OnNightSkip(KartaSoloSlumber plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNightSkip(TimeSkipEvent event) {
        if(!event.getSkipReason().equals(TimeSkipEvent.SkipReason.NIGHT_SKIP)) return;
        plugin.wakeupCommandTime.set(System.currentTimeMillis());
    }
}
