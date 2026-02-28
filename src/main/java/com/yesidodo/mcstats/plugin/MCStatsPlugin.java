package com.yesidodo.mcstats.plugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MCStatsPlugin extends JavaPlugin {
    private StatsQueue queue;
    private McStatsApiClient client;
    private int batchSize;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.queue = new StatsQueue();
        this.batchSize = Math.max(1, getConfig().getInt("api.batch-size", 200));

        String baseUrl = getConfig().getString("api.base-url", "http://127.0.0.1:5000");
        int timeoutMs = getConfig().getInt("api.timeout-ms", 5000);
        String secret = getConfig().getString("api.secret", "");
        this.client = new McStatsApiClient(baseUrl, timeoutMs, secret);

        Bukkit.getPluginManager().registerEvents(new PlayerEventListener(queue), this);

        int playtimeTick = Math.max(1, getConfig().getInt("collect.playtime-tick-seconds", 60));
        Bukkit.getScheduler().runTaskTimer(this, this::collectPlaytime, 20L * playtimeTick, 20L * playtimeTick);

        int flushInterval = Math.max(1, getConfig().getInt("api.flush-interval-seconds", 5));
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::flushBatch, 20L * flushInterval, 20L * flushInterval);

        getLogger().info("MCStatsPlugin enabled. Target API: " + baseUrl);
    }

    @Override
    public void onDisable() {
        flushBatch();
    }

    private void collectPlaytime() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            queue.offer(player.getUniqueId(), "play_time_seconds", 60);
        }
    }

    private void flushBatch() {
        List<StatEvent> batch = queue.drain(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        boolean success = client.sendBatch(batch);
        if (!success) {
            queue.requeueFront(batch);
        }
    }
}
