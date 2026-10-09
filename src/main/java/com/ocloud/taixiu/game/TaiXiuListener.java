package com.ocloud.taixiu.game;

import com.ocloud.taixiu.util.FoliaScheduler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class TaiXiuListener implements Listener {
    private final JavaPlugin plugin;
    private final TaiXiuService service;

    public TaiXiuListener(JavaPlugin plugin, TaiXiuService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FoliaScheduler.runLaterForPlayer(plugin, player, () -> service.handleJoin(player), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        service.closeHistory(event.getPlayer(), false);
        service.closeBettingDisplay(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.closeHistory(event.getPlayer(), false);
        service.closeBettingDisplay(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        service.closeHistory(event.getEntity(), false);
        service.closeBettingDisplay(event.getEntity(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!isBettingDisplayClick(event.getAction())) return;
        if (service.handleBettingDisplayClick(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    static boolean isBettingDisplayClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK
                || action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }
}
