package com.ocloud.taixiu.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoliaScheduler {
    private FoliaScheduler() {}
    public interface TaskHandle { void cancel(); }
    private static final TaskHandle NONE = () -> {};

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static void runNow(JavaPlugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public static TaskHandle runLater(JavaPlugin plugin, Runnable task, long delay) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> task.run(), Math.max(1, delay))::cancel;
    }

    public static TaskHandle runTimer(JavaPlugin plugin, Runnable task, long delay, long period) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> task.run(),
                Math.max(1, delay), Math.max(1, period))::cancel;
    }

    public static void runForPlayer(JavaPlugin plugin, Player player, Runnable task) {
        if (player != null) runForEntity(plugin, player, () -> { if (player.isOnline()) task.run(); });
    }

    public static void runForEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        if (entity == null) return;
        if (!plugin.isEnabled()) {
            if (Bukkit.isOwnedByCurrentRegion(entity)) task.run();
            return;
        }
        entity.getScheduler().run(plugin, ignored -> task.run(), null);
    }

    public static void runForLocation(JavaPlugin plugin, org.bukkit.Location location, Runnable task) {
        if (plugin.isEnabled()) Bukkit.getRegionScheduler().execute(plugin, location, task);
    }

    public static TaskHandle runTimerForPlayer(JavaPlugin plugin, Player player, Runnable task, long delay, long period) {
        if (player == null) return NONE;
        var scheduled = player.getScheduler().runAtFixedRate(plugin,
                ignored -> { if (player.isOnline()) task.run(); }, null, Math.max(1, delay), Math.max(1, period));
        return scheduled == null ? NONE : scheduled::cancel;
    }

    public static TaskHandle runLaterForPlayer(JavaPlugin plugin, Player player, Runnable task, long delay) {
        if (player == null) return NONE;
        return runLaterForEntity(plugin, player, () -> { if (player.isOnline()) task.run(); }, delay);
    }

    public static TaskHandle runLaterForEntity(JavaPlugin plugin, Entity entity, Runnable task, long delay) {
        if (entity == null) return NONE;
        var scheduled = entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, Math.max(1, delay));
        return scheduled == null ? NONE : scheduled::cancel;
    }
}
