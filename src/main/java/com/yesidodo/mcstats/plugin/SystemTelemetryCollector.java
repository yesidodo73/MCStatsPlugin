package com.yesidodo.mcstats.plugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import oshi.SystemInfo;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SystemTelemetryCollector {
    private final String serverId;
    private final HardwareAbstractionLayer hardware;
    private long lastIoSampleTs;
    private Long lastDiskReadBytes;
    private Long lastDiskWriteBytes;
    private Long lastNetworkRxBytes;
    private Long lastNetworkTxBytes;
    private Long lastGcCollections;
    private long lastGcSampleTs;

    public SystemTelemetryCollector(String serverId) {
        this.serverId = serverId;
        this.hardware = new SystemInfo().getHardware();
    }

    public TelemetrySample collect() {
        long ts = Instant.now().getEpochSecond();
        Double tps = readTps();
        Double mspt = readMspt();
        Double cpuUsage = readCpuUsagePercent();
        Double ramTotalMb = readTotalMemoryMb();
        Double ramUsedMb = readUsedMemoryMb(ramTotalMb);
        IoRateEstimate ioEstimate = estimateIoRates(ts);
        Double gcCollectionsPerMinute = estimateGcCollectionsPerMinute(ts);
        Integer threadCount = readThreadCount();

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
                ioEstimate.networkRxKbps(),
                ioEstimate.networkTxKbps(),
                ioEstimate.diskReadKbps(),
                ioEstimate.diskWriteKbps(),
                gcCollectionsPerMinute,
                threadCount,
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

    private static Double percentile(List<Integer> sortedValues, double q) {
        if (sortedValues.isEmpty()) {
            return null;
        }
        int idx = (int) Math.ceil(q * sortedValues.size()) - 1;
        idx = Math.max(0, Math.min(sortedValues.size() - 1, idx));
        return (double) sortedValues.get(idx);
    }

    private IoRateEstimate estimateIoRates(long ts) {
        long totalDiskRead = 0L;
        long totalDiskWrite = 0L;
        for (HWDiskStore disk : hardware.getDiskStores()) {
            disk.updateAttributes();
            totalDiskRead += Math.max(0L, disk.getReadBytes());
            totalDiskWrite += Math.max(0L, disk.getWriteBytes());
        }

        long totalNetworkRx = 0L;
        long totalNetworkTx = 0L;
        for (NetworkIF networkIF : hardware.getNetworkIFs()) {
            networkIF.updateAttributes();
            totalNetworkRx += Math.max(0L, networkIF.getBytesRecv());
            totalNetworkTx += Math.max(0L, networkIF.getBytesSent());
        }

        Double diskReadKbps = null;
        Double diskWriteKbps = null;
        Double networkRxKbps = null;
        Double networkTxKbps = null;
        if (lastIoSampleTs > 0 && ts > lastIoSampleTs &&
                lastDiskReadBytes != null && lastDiskWriteBytes != null &&
                lastNetworkRxBytes != null && lastNetworkTxBytes != null) {
            double dt = ts - lastIoSampleTs;
            diskReadKbps = toKbps(totalDiskRead - lastDiskReadBytes, dt);
            diskWriteKbps = toKbps(totalDiskWrite - lastDiskWriteBytes, dt);
            networkRxKbps = toKbps(totalNetworkRx - lastNetworkRxBytes, dt);
            networkTxKbps = toKbps(totalNetworkTx - lastNetworkTxBytes, dt);
        }

        lastIoSampleTs = ts;
        lastDiskReadBytes = totalDiskRead;
        lastDiskWriteBytes = totalDiskWrite;
        lastNetworkRxBytes = totalNetworkRx;
        lastNetworkTxBytes = totalNetworkTx;
        return new IoRateEstimate(diskReadKbps, diskWriteKbps, networkRxKbps, networkTxKbps);
    }

    private Double estimateGcCollectionsPerMinute(long ts) {
        long current = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0L, bean.getCollectionCount()))
                .sum();

        Double perMinute = null;
        if (lastGcCollections != null && lastGcSampleTs > 0 && ts > lastGcSampleTs) {
            long delta = Math.max(0L, current - lastGcCollections);
            double dt = ts - lastGcSampleTs;
            perMinute = (delta / dt) * 60.0;
        }

        lastGcCollections = current;
        lastGcSampleTs = ts;
        return perMinute;
    }

    private static Integer readThreadCount() {
        return ManagementFactory.getThreadMXBean().getThreadCount();
    }

    private static double bytesToMb(long bytes) {
        return bytes / 1024d / 1024d;
    }

    private static Double toKbps(long byteDelta, double dtSeconds) {
        if (dtSeconds <= 0) {
            return null;
        }
        long normalized = Math.max(0L, byteDelta);
        return (normalized / 1024.0) / dtSeconds;
    }

    private record IoRateEstimate(
            Double diskReadKbps,
            Double diskWriteKbps,
            Double networkRxKbps,
            Double networkTxKbps
    ) {
    }
}
