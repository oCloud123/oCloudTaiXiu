package com.ocloud.taixiu.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class ConsoleBanner {
    private static final String RESET = "\u001B[0m";
    private static final String GRAY = "\u001B[90m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String DISCORD = "https://discord.gg/WVNrYzQfhX";

    private ConsoleBanner() {}

    public static void started(JavaPlugin plugin, boolean nativeGui, long startedAt) {
        StringBuilder banner = header(plugin, "Starting");
        line(banner, GREEN, "│ ✓ Config loaded");
        line(banner, GREEN, "│ ✓ SQLite ready");
        line(banner, nativeGui ? GREEN : GRAY, nativeGui
                ? "│ ✓ Native Dialog ready (inventory fallback)"
                : "│ ➜ Inventory GUI (Native Dialog unavailable)");
        line(banner, GREEN, "│ ✓ Commands, listeners & scheduler ready");
        line(banner, GREEN, "│ ✓ TaiXiu started - unlimited house funds");
        finish(banner, GREEN, startedAt);
        plugin.getLogger().info("\n" + banner);
    }

    public static void stopped(JavaPlugin plugin, long startedAt) {
        StringBuilder banner = header(plugin, "Stopping");
        line(banner, GREEN, "│ ✓ Game shutdown completed");
        line(banner, GREEN, "│ ✓ TaiXiu stopped");
        finish(banner, GREEN, startedAt);
        plugin.getLogger().info("\n" + banner);
    }

    public static void failed(JavaPlugin plugin, String action, long startedAt, Throwable error) {
        StringBuilder banner = header(plugin, action.equals("start") ? "Starting" : "Stopping");
        line(banner, RED, "│ ✗ TaiXiu failed to " + action + " - see exception below");
        finish(banner, RED, startedAt);
        plugin.getLogger().log(Level.SEVERE, "\n" + banner, error);
    }

    private static StringBuilder header(JavaPlugin plugin, String action) {
        StringBuilder banner = new StringBuilder();
        line(banner, CYAN, "   ___   ____ _                 _");
        line(banner, CYAN, "  / _ \\ / ___| | ___  _   _  __| |");
        line(banner, CYAN, " | | | | |   | |/ _ \\| | | |/ _` |");
        line(banner, CYAN, " | |_| | |___| | (_) | |_| | (_| |");
        line(banner, CYAN, "  \\___/ \\____|_|\\___/ \\__,_|\\__,_|");
        line(banner, CYAN, "  oCloud Studio - TaiXiu");
        line(banner, GRAY, "┌ " + action + " oCloudTaiXiu v" + plugin.getPluginMeta().getVersion());
        return banner;
    }

    private static void finish(StringBuilder banner, String color, long startedAt) {
        line(banner, CYAN, "│ Discord: " + DISCORD);
        long elapsed = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        line(banner, color, "└ " + (color.equals(RED) ? "✗ Finished in " : "✓ Done in ") + elapsed + "ms");
    }

    private static void line(StringBuilder banner, String color, String text) {
        if (!banner.isEmpty()) banner.append('\n');
        banner.append(color).append(text).append(RESET);
    }
}
