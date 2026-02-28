package com.yesidodo.mcstats.plugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerEventListener implements Listener {
    private final StatsQueue queue;

    public PlayerEventListener(StatsQueue queue) {
        this.queue = queue;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        queue.offer(event.getPlayer().getUniqueId(), "joins", 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        queue.offer(event.getPlayer().getUniqueId(), "quits", 1);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        queue.offer(event.getEntity().getUniqueId(), "deaths", 1);
    }
}
