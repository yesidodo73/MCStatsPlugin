package com.yesidodo.mcstats.plugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public final class ServerSchedulerCompat {
    private final Plugin plugin;
    private final Object globalRegionScheduler;
    private final Object asyncScheduler;
    private final Method globalRunAtFixedRateMethod;
    private final Method asyncRunAtFixedRateMethod;

    public ServerSchedulerCompat(Plugin plugin) {
        this.plugin = plugin;

        Object detectedGlobalScheduler = null;
        Object detectedAsyncScheduler = null;
        Method detectedGlobalRunMethod = null;
        Method detectedAsyncRunMethod = null;

        try {
            Method getGlobalRegionScheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
            detectedGlobalScheduler = getGlobalRegionScheduler.invoke(Bukkit.getServer());
            detectedGlobalRunMethod = detectedGlobalScheduler.getClass().getMethod(
                    "runAtFixedRate",
                    Plugin.class,
                    java.util.function.Consumer.class,
                    long.class,
                    long.class
            );
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            detectedGlobalScheduler = null;
            detectedGlobalRunMethod = null;
        }

        try {
            Method getAsyncScheduler = Bukkit.getServer().getClass().getMethod("getAsyncScheduler");
            detectedAsyncScheduler = getAsyncScheduler.invoke(Bukkit.getServer());
            detectedAsyncRunMethod = detectedAsyncScheduler.getClass().getMethod(
                    "runAtFixedRate",
                    Plugin.class,
                    java.util.function.Consumer.class,
                    long.class,
                    long.class,
                    TimeUnit.class
            );
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            detectedAsyncScheduler = null;
            detectedAsyncRunMethod = null;
        }

        this.globalRegionScheduler = detectedGlobalScheduler;
        this.asyncScheduler = detectedAsyncScheduler;
        this.globalRunAtFixedRateMethod = detectedGlobalRunMethod;
        this.asyncRunAtFixedRateMethod = detectedAsyncRunMethod;
    }

    public ScheduledHandle scheduleSyncRepeating(Runnable task, long delayTicks, long periodTicks) {
        if (globalRegionScheduler != null && globalRunAtFixedRateMethod != null) {
            try {
                Object scheduled = globalRunAtFixedRateMethod.invoke(
                        globalRegionScheduler,
                        plugin,
                        (java.util.function.Consumer<Object>) ignored -> task.run(),
                        delayTicks,
                        periodTicks
                );
                return new ReflectiveHandle(scheduled);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // Fall through to Bukkit scheduler.
            }
        }

        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return new BukkitTaskHandle(bukkitTask);
    }

    public ScheduledHandle scheduleAsyncRepeating(Runnable task, long delayTicks, long periodTicks) {
        if (asyncScheduler != null && asyncRunAtFixedRateMethod != null) {
            try {
                long delayMs = ticksToMillis(delayTicks);
                long periodMs = ticksToMillis(periodTicks);
                Object scheduled = asyncRunAtFixedRateMethod.invoke(
                        asyncScheduler,
                        plugin,
                        (java.util.function.Consumer<Object>) ignored -> task.run(),
                        delayMs,
                        periodMs,
                        TimeUnit.MILLISECONDS
                );
                return new ReflectiveHandle(scheduled);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // Fall through to Bukkit scheduler.
            }
        }

        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        return new BukkitTaskHandle(bukkitTask);
    }

    private static long ticksToMillis(long ticks) {
        return ticks * 50L;
    }

    public interface ScheduledHandle {
        void cancel();
    }

    private static final class BukkitTaskHandle implements ScheduledHandle {
        private final BukkitTask task;

        private BukkitTaskHandle(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task.cancel();
        }
    }

    private static final class ReflectiveHandle implements ScheduledHandle {
        private final Object scheduledTask;

        private ReflectiveHandle(Object scheduledTask) {
            this.scheduledTask = scheduledTask;
        }

        @Override
        public void cancel() {
            try {
                Method cancel = scheduledTask.getClass().getMethod("cancel");
                cancel.invoke(scheduledTask);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            }
        }
    }
}
