package com.ocloud.taixiu.game;

import com.ocloud.taixiu.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

final class InventoryTaiXiuUi implements Listener, PlayerMenu {
    private final JavaPlugin plugin;
    private final TaiXiuService service;

    InventoryTaiXiuUi(JavaPlugin plugin, TaiXiuService service) { this.plugin = plugin; this.service = service; }

    @Override public void open(Player player) { main(player, 0); }

    private List<String> amounts() {
        String csv = service.guiText("inventory.amounts", "100k,500k,1m,10%,25%,50%,all-in,max");
        List<String> result = java.util.Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).limit(20).toList();
        return result.isEmpty() ? List.of("max") : result;
    }

    private void main(Player player, int selection) {
        List<String> amounts = amounts();
        var data = service.snapshot(player.getUniqueId());
        Screen screen = new Screen(player.getUniqueId(), null, amounts.get(selection % amounts.size()), selection, 0, data.roundId());
        screen.inventory = Bukkit.createInventory(screen, 36, TaiXiuService.component(service.guiText("title", "TÀI XỈU")));
        screen.inventory.setItem(4, item(Material.CLOCK, "&ePhiên #" + data.roundId(),
                data.state() + " • " + data.secondsRemaining() + "s", "Số dư: " + TaiXiuService.money(service.playerBalance(player)) + "$"));
        screen.inventory.setItem(11, item(Material.RED_CONCRETE, service.guiText("buttons.tai", "CƯỢC TÀI"),
                "Tổng: " + TaiXiuService.money(data.taiTotal()) + "$"));
        screen.inventory.setItem(15, item(Material.BLUE_CONCRETE, service.guiText("buttons.xiu", "CƯỢC XỈU"),
                "Tổng: " + TaiXiuService.money(data.xiuTotal()) + "$"));
        screen.inventory.setItem(22, item(Material.GOLD_INGOT, "&e" + screen.rawAmount, "Nhấp để đổi mức cược", "Tiền tùy ý: /tx cuoc <tai|xiu> <tiền>"));
        screen.inventory.setItem(29, item(Material.PAPER, service.guiText("buttons.history", "CẦU 3D")));
        screen.inventory.setItem(31, item(Material.EMERALD, service.guiText("buttons.stats", "THỐNG KÊ")));
        screen.inventory.setItem(33, item(Material.COMPASS, service.guiText("buttons.refresh", "LÀM MỚI")));
        player.openInventory(screen.inventory);
    }

    private void confirm(Player player, TaiXiuMath.Side side, Screen previous) {
        long amount = service.resolveBetAmount(player, side, previous.rawAmount);
        if (amount <= 0) { service.placeBet(player, side, previous.rawAmount); return; }
        Screen screen = new Screen(player.getUniqueId(), side, previous.rawAmount, previous.selection, amount, previous.roundId);
        screen.inventory = Bukkit.createInventory(screen, 27, TaiXiuService.component(service.guiText("confirm.title", "XÁC NHẬN CƯỢC")));
        screen.inventory.setItem(4, item(Material.GOLD_INGOT, side.displayName() + " • " + TaiXiuService.money(amount) + "$",
                service.guiText("confirm.warning", "Cược đã xác nhận không thể rút hoặc đổi cửa.")));
        screen.inventory.setItem(11, item(Material.LIME_CONCRETE, service.guiText("buttons.confirm", "XÁC NHẬN")));
        screen.inventory.setItem(15, item(Material.ARROW, service.guiText("buttons.back", "QUAY LẠI")));
        player.openInventory(screen.inventory);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Screen screen)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(screen.owner)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= screen.inventory.getSize() || screen.used) return;
        screen.used = true;
        FoliaScheduler.runForPlayer(plugin, player, () -> {
            if (player.getOpenInventory().getTopInventory() != screen.inventory) return;
            if (screen.side != null) {
                if (slot == 11) { player.closeInventory(); service.placeBetForRound(player, screen.side, screen.amount, screen.roundId); }
                else if (slot == 15) main(player, screen.selection);
                else screen.used = false;
                return;
            }
            switch (slot) {
                case 11 -> confirm(player, TaiXiuMath.Side.TAI, screen);
                case 15 -> confirm(player, TaiXiuMath.Side.XIU, screen);
                case 22 -> main(player, screen.selection + 1);
                case 29 -> { player.closeInventory(); service.openHistory(player); }
                case 31 -> {
                    player.closeInventory();
                    var stats = service.stats(player.getUniqueId());
                    player.sendMessage(TaiXiuService.component("&6Đã chơi " + stats.rounds() + " phiên • " + stats.wins()
                            + " thắng • Tổng cược " + TaiXiuService.money(stats.totalBet()) + "$"));
                }
                case 33 -> main(player, screen.selection);
                default -> screen.used = false;
            }
        });
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Screen) event.setCancelled(true);
    }

    private ItemStack item(Material material, String name, String... lore) {
        var item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(TaiXiuService.component(name));
        meta.lore(java.util.Arrays.stream(lore).map(TaiXiuService::component).toList());
        item.setItemMeta(meta);
        return item;
    }

    void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaScheduler.runForPlayer(plugin, player, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof Screen) player.closeInventory();
            });
        }
    }

    private static final class Screen implements InventoryHolder {
        private final UUID owner;
        private final TaiXiuMath.Side side;
        private final String rawAmount;
        private final int selection;
        private final long amount;
        private final long roundId;
        private Inventory inventory;
        private boolean used;
        private Screen(UUID owner, TaiXiuMath.Side side, String rawAmount, int selection, long amount, long roundId) {
            this.owner = owner; this.side = side; this.rawAmount = rawAmount; this.selection = selection; this.amount = amount;
            this.roundId = roundId;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
