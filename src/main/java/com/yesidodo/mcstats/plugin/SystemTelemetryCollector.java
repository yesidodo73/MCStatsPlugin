package com.yesidodo.mcstats.plugin;

import com.sun.management.OperatingSystemMXBean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SystemTelemetryCollector {
    private final String serverId;

    public SystemTelemetryCollector(String serverId) {
        this.serverId = serverId;
    }

    public TelemetrySample collect() {
        long ts = Instant.now().getEpochSecond();
        Double tps = readTps();
        Double mspt = readMspt();
        Double cpuUsage = readCpuUsagePercent();
        Double ramTotalMb = readTotalMemoryMb();
        Double ramUsedMb = readUsedMemoryMb(ramTotalMb);

        List<Integer> pings = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            pings.add(player.getPing());
        }
        pings.sort(Comparator.naturalOrder());

        return new TelemetrySample(
                serverId,
                ts,
                tps,
                mspt,
                cpuUsage,
                ramUsedMb,
                ramTotalMb,
                null,
                null,
                Bukkit.getOnlinePlayers().size(),
                percentile(pings, 0.50),
                percentile(pings, 0.95),
                percentile(pings, 0.99)
        );
    }

    private static Double readTps() {
        try {
            Object value = Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer());
            if (value instanceof double[] tpsArray && tpsArray.length > 0) {
                return tpsArray[0];
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Double readMspt() {
        try {
            Object value = Bukkit.getServer().getClass().getMethod("getAverageTickTime").invoke(Bukkit.getServer());
            if (value instanceof Double d) {
                return d;
            }
            if (value instanceof Number n) {
                return n.doubleValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Double readCpuUsagePercent() {
        var osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof OperatingSystemMXBean sunOsBean) {
            double cpuLoad = sunOsBean.getCpuLoad();
            if (cpuLoad >= 0) {
                return cpuLoad * 100.0;
            }
        }
        return null;
    }

    private static Double readTotalMemoryMb() {
        var osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof OperatingSystemMXBean sunOsBean) {
            return bytesToMb(sunOsBean.getTotalMemorySize());
        }
        return null;
    }

    private static Double readUsedMemoryMb(Double totalMemoryMb) {
        var osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof OperatingSystemMXBean sunOsBean && totalMemoryMb != null) {
            double freeMb = bytesToMb(sunOsBean.getFreeMemorySize());
            return Math.max(0.0, totalMemoryMb - freeMb);
        }
        return null;
    }

    private static double bytesToMb(long bytes) {
        return bytes / 1024d / 1024d;
    }

    private static Double percentile(List<Integer> sortedValues, double q) {
        if (sortedValues.isEmpty()) {
            return null;
        }
        int idx = (int) Math.ceil(q * sortedValues.size()) - 1;
        idx = Math.max(0, Math.min(sortedValues.size() - 1, idx));
        return (double) sortedValues.get(idx);
    }
}
