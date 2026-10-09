package com.ocloud.taixiu.game;

import com.ocloud.taixiu.util.BedrockPlatform;
import com.ocloud.taixiu.util.ClientCapabilities;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class TaiXiuUi {
    private final JavaPlugin plugin;
    private final TaiXiuService service;
    private final BedrockTaiXiuUi bedrock;
    private final InventoryTaiXiuUi inventory;
    private PlayerMenu nativeMenu;

    TaiXiuUi(JavaPlugin plugin, TaiXiuService service) {
        this.plugin = plugin;
        this.service = service;
        bedrock = new BedrockTaiXiuUi(plugin, service);
        inventory = new InventoryTaiXiuUi(plugin, service);
        plugin.getServer().getPluginManager().registerEvents(inventory, plugin);
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            nativeMenu = (PlayerMenu) Class.forName("com.ocloud.taixiu.game.NativeTaiXiuUi")
                    .getConstructor(JavaPlugin.class, TaiXiuService.class).newInstance(plugin, service);
        } catch (ReflectiveOperationException | LinkageError ignored) {

        }
    }

    boolean hasNativeMenu() { return nativeMenu != null; }

    void open(Player player) {
        if (service.config().bedrockForms && BedrockPlatform.isBedrock(player)) {
            if (bedrock.tryOpen(player)) return;
            plugin.getLogger().warning("Không gửi được form cho " + player.getName() + ": "
                    + BedrockPlatform.takeLastFormFailure());
            inventory.open(player);
            return;
        }
        switch (service.config().guiMode) {
            case "inventory" -> inventory.open(player);
            case "3d" -> service.openBettingDisplay(player);
            default -> {
                if (nativeMenu != null && ClientCapabilities.supportsDialogs(player)) {
                    try { nativeMenu.open(player); return; }
                    catch (LinkageError | RuntimeException ex) {
                        plugin.getLogger().warning("Native Dialog không khả dụng: " + ex.getMessage());
                    }
                }
                inventory.open(player);
            }
        }
    }

    void close() { inventory.close(); }
}
