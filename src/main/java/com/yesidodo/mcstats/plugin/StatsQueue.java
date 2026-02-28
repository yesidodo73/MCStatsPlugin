package com.yesidodo.mcstats.plugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class StatsQueue {
    private final ConcurrentLinkedQueue<StatEvent> queue = new ConcurrentLinkedQueue<>();

    public void offer(UUID uuid, String metric, long delta) {
        if (delta == 0L || metric == null || metric.isBlank()) {
            return;
        }

        queue.offer(new StatEvent(uuid, metric, delta, Instant.now().getEpochSecond()));
    }

    public List<StatEvent> drain(int maxItems) {
        List<StatEvent> drained = new ArrayList<>(Math.max(1, maxItems));
        for (int i = 0; i < maxItems; i++) {
            StatEvent event = queue.poll();
            if (event == null) {
                break;
            }
            drained.add(event);
        }
        return drained;
    }

    public void requeueFront(List<StatEvent> failedBatch) {
        if (failedBatch.isEmpty()) {
            return;
        }
        for (StatEvent event : failedBatch) {
            queue.offer(event);
        }
    }

    public int size() {
        return queue.size();
    }
}
