package com.yesidodo.mcstats.plugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class MCStatsPlugin extends JavaPlugin {
    private StatsQueue statsQueue;
    private TelemetryQueue telemetryQueue;
    private SystemTelemetryCollector telemetryCollector;
    private McStatsApiClient client;
    private ServerSchedulerCompat schedulerCompat;
    private int statsBatchSize;
    private int telemetryBatchSize;
    private final List<ServerSchedulerCompat.ScheduledHandle> scheduledHandles = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.statsQueue = new StatsQueue();
        this.telemetryQueue = new TelemetryQueue();
        this.statsBatchSize = Math.max(1, getConfig().getInt("api.batch-size", 200));
        this.telemetryBatchSize = Math.max(1, getConfig().getInt("api.telemetry-batch-size", 120));

        String baseUrl = getConfig().getString("api.base-url", "http://127.0.0.1:5000");
        int timeoutMs = getConfig().getInt("api.timeout-ms", 5000);
        String serverId = getConfig().getString("api.server-id", "default-server");
        String apiKey = getConfig().getString("api.api-key", "");
        String secret = getConfig().getString("api.secret", "");
        if (serverId == null || serverId.isBlank() || apiKey == null || apiKey.isBlank() || secret == null || secret.isBlank()) {
            getLogger().severe("api.server-id, api.api-key, api.secret must be configured.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.client = new McStatsApiClient(baseUrl, timeoutMs, serverId, apiKey, secret);
        this.telemetryCollector = new SystemTelemetryCollector(serverId);
        this.schedulerCompat = new ServerSchedulerCompat(this);

        Bukkit.getPluginManager().registerEvents(new PlayerEventListener(statsQueue), this);

        int playtimeTick = Math.max(1, getConfig().getInt("collect.playtime-tick-seconds", 60));
        scheduledHandles.add(
                schedulerCompat.scheduleSyncRepeating(
                        this::collectPlaytime,
                        20L * playtimeTick,
                        20L * playtimeTick
                )
        );

        int flushInterval = Math.max(1, getConfig().getInt("api.flush-interval-seconds", 5));
        scheduledHandles.add(
                schedulerCompat.scheduleAsyncRepeating(
                        this::flushStatsBatch,
                        20L * flushInterval,
                        20L * flushInterval
                )
        );

        int telemetryCollectInterval = Math.max(1, getConfig().getInt("collect.telemetry-sample-seconds", 10));
        scheduledHandles.add(
                schedulerCompat.scheduleAsyncRepeating(
                        this::collectTelemetry,
                        20L * telemetryCollectInterval,
                        20L * telemetryCollectInterval
                )
        );

        int telemetryFlushInterval = Math.max(1, getConfig().getInt("api.telemetry-flush-interval-seconds", 10));
        scheduledHandles.add(
                schedulerCompat.scheduleAsyncRepeating(
                        this::flushTelemetryBatch,
                        20L * telemetryFlushInterval,
                        20L * telemetryFlushInterval
                )
        );

        getLogger().info("MCStatsPlugin enabled. Target API: " + baseUrl);
    }

    @Override
    public void onDisable() {
        for (ServerSchedulerCompat.ScheduledHandle handle : scheduledHandles) {
            handle.cancel();
        }
        scheduledHandles.clear();

        flushStatsBatch();
        flushTelemetryBatch();
    }

    private void collectPlaytime() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            statsQueue.offer(player.getUniqueId(), "play_time_seconds", 60);
        }
    }

    private void collectTelemetry() {
        telemetryQueue.offer(telemetryCollector.collect());
    }

    private void flushStatsBatch() {
        List<StatEvent> batch = statsQueue.drain(statsBatchSize);
        if (batch.isEmpty()) {
            return;
        }

        boolean success = client.sendEventsBatch(batch);
        if (!success) {
            statsQueue.requeueFront(batch);
        }
    }

    private void flushTelemetryBatch() {
        List<TelemetrySample> batch = telemetryQueue.drain(telemetryBatchSize);
        if (batch.isEmpty()) {
            return;
        }

        boolean success = client.sendTelemetryBatch(batch);
        if (!success) {
            telemetryQueue.requeue(batch);
        }
    }
}
