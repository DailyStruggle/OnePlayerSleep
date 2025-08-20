package com.minekartastudio.kartasoloslumber.bukkitTasks;

import com.minekartastudio.kartasoloslumber.KartaSoloSlumber;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class ClearWeather extends BukkitRunnable{
	private final World world;
	private Long duration = 0L;
	
	public ClearWeather(World world) {
		this.world = world;
	}
	
	public ClearWeather(World world, Long duration) {
		this.world = world;
		this.duration = duration;
	}

	@Override
	public void run() {
		if(world.isClearWeather()) return;

		this.world.setStorm(false);
		this.world.setThundering(false);
		if(this.duration == 0) {
			Integer clearDuration = KartaSoloSlumber.getConfigs().config.getClearDuration();
			Integer clearRandomDuration = KartaSoloSlumber.getConfigs().config.getClearRandomDuration();
			long randomFactor = ThreadLocalRandom.current().nextLong(clearRandomDuration);
			this.duration = (clearDuration + randomFactor) * 20;
		}

		this.duration = Math.max(this.duration,world.getWeatherDuration());

		this.world.setWeatherDuration(this.duration.intValue());
		this.cancel();
		long l = System.currentTimeMillis();
		KartaSoloSlumber.getInstance().clearWeatherTime.putIfAbsent(world.getUID(),new AtomicLong(l));
		KartaSoloSlumber.getInstance().clearWeatherTime.get(world.getUID()).set(l);
	}
}
