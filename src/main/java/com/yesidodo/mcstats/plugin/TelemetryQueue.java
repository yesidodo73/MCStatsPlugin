package com.yesidodo.mcstats.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TelemetryQueue {
    private final ConcurrentLinkedQueue<TelemetrySample> queue = new ConcurrentLinkedQueue<>();

    public void offer(TelemetrySample sample) {
        if (sample == null || sample.serverId() == null || sample.serverId().isBlank()) {
            return;
        }
        queue.offer(sample);
    }

    public List<TelemetrySample> drain(int maxItems) {
        List<TelemetrySample> drained = new ArrayList<>(Math.max(1, maxItems));
        for (int i = 0; i < maxItems; i++) {
            TelemetrySample sample = queue.poll();
            if (sample == null) {
                break;
            }
            drained.add(sample);
        }
        return drained;
    }

    public void requeue(List<TelemetrySample> failedBatch) {
        for (TelemetrySample sample : failedBatch) {
            queue.offer(sample);
        }
    }
}
