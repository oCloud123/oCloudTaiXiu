package com.ocloud.taixiu;

import com.ocloud.taixiu.game.TaiXiuCommand;
import com.ocloud.taixiu.game.TaiXiuListener;
import com.ocloud.taixiu.game.TaiXiuService;
import com.ocloud.taixiu.util.ConsoleBanner;
import org.bukkit.plugin.java.JavaPlugin;

public final class OCloudTaiXiuPlugin extends JavaPlugin {
    private TaiXiuService service;
    private boolean started;

    @Override public void onEnable() {
        long startedAt = System.nanoTime();
        try {
            service = new TaiXiuService(this);
            service.start();
            getServer().getPluginManager().registerEvents(new TaiXiuListener(this, service), this);
            var executor = new TaiXiuCommand(service);
            var command = java.util.Objects.requireNonNull(getCommand("taixiu"));
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            started = true;
            ConsoleBanner.started(this, service.hasNativeGui(), startedAt);
        } catch (RuntimeException | LinkageError ex) {
            started = false;
            ConsoleBanner.failed(this, "start", startedAt, ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() {
        if (service == null) return;
        long startedAt = System.nanoTime();
        try {
            service.shutdown();
            if (started) ConsoleBanner.stopped(this, startedAt);
        } catch (RuntimeException | LinkageError ex) {
            ConsoleBanner.failed(this, "stop", startedAt, ex);
        } finally {
            service = null;
            started = false;
        }
    }
}
