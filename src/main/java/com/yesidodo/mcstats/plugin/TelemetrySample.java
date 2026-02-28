package com.yesidodo.mcstats.plugin;

public record TelemetrySample(
        String serverId,
        long timestampUtc,
        Double tps,
        Double mspt,
        Double cpuUsagePercent,
        Double ramUsedMb,
        Double ramTotalMb,
        Double networkRxKbps,
        Double networkTxKbps,
        Double diskReadKbps,
        Double diskWriteKbps,
        Double gcCollectionsPerMinute,
        Integer threadCount,
        Integer onlinePlayers,
        Double pingP50Ms,
        Double pingP95Ms,
        Double pingP99Ms
) {
}
