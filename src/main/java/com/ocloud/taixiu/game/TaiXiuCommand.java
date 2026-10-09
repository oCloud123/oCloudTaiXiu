package com.ocloud.taixiu.game;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TaiXiuCommand implements CommandExecutor, TabCompleter {
    private final TaiXiuService service;

    public TaiXiuCommand(TaiXiuService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) service.open(player);
            else status(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("setnext") || sub.equals("next")) {
            if (!sender.hasPermission("ocloudtaixiu.setnext")) {
                sender.sendMessage("§cBạn không có quyền đặt kết quả phiên tiếp theo.");
                return true;
            }
            if (sub.equals("next")) {
                sender.sendMessage("§ePhiên tiếp theo: " + (service.nextOutcome() == null ? "ngẫu nhiên" : service.nextOutcome()));
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage("§e/tx setnext <tai|xiu|boba|random>");
                return true;
            }
            NextOutcome outcome = NextOutcome.parse(args[1]);
            if (outcome == null && !args[1].equalsIgnoreCase("random") && !args[1].equalsIgnoreCase("clear")) {
                sender.sendMessage("§cKết quả phải là tai, xiu, boba hoặc random.");
                return true;
            }
            try {
                service.setNextOutcome(outcome, sender.getName());
                sender.sendMessage("§aĐã đặt phiên nhận cược tiếp theo: " + (outcome == null ? "ngẫu nhiên" : outcome));
            } catch (SQLException ex) {
                sender.sendMessage("§cKhông thể lưu kết quả phiên kế: " + ex.getMessage());
            }
            return true;
        }
        if (sub.equals("ban") || sub.equals("table") || sub.equals("3d")) {
            Player player = requirePlayer(sender);
            if (player != null) service.openBettingDisplay(player);
            return true;
        }
        if (sub.equals("cau") || sub.equals("history") || sub.equals("lichsu")) {
            Player player = requirePlayer(sender);
            if (player != null) service.openHistory(player);
            return true;
        }
        if (sub.equals("stats") || sub.equals("thongke")) {
            Player player = requirePlayer(sender);
            if (player != null) showStats(player);
            return true;
        }
        if (sub.equals("cuoc") || sub.equals("bet")) {
            Player player = requirePlayer(sender);
            if (player == null) return true;
            if (args.length < 3) {
                sender.sendMessage("§eDùng: /tx cuoc <tai|xiu> <số tiền>");
                return true;
            }
            TaiXiuMath.Side side = TaiXiuMath.Side.parse(args[1]);
            if (side == null) {
                sender.sendMessage("§cCửa phải là Tài hoặc Xỉu.");
                return true;
            }
            service.placeBet(player, side, args[2]);
            return true;
        }
        if (isAdminSubcommand(sub)) return admin(sender, sub, args);
        help(sender);
        return true;
    }

    private boolean admin(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("ocloudtaixiu.admin")) {
            sender.sendMessage("§cBạn không có quyền dùng lệnh quản trị Tài Xỉu.");
            return true;
        }
        switch (sub) {
            case "status" -> status(sender);
            case "pause" -> sender.sendMessage(service.setPaused(true)
                    ? "§eĐã tạm dừng Tài Xỉu và hoàn mọi cược đang nhận."
                    : "§7Tài Xỉu đã tạm dừng từ trước.");
            case "resume" -> sender.sendMessage(service.setPaused(false)
                    ? "§aĐã tiếp tục Tài Xỉu."
                    : "§7Tài Xỉu hiện không bị tạm dừng.");
            case "cancel" -> {
                service.cancelByAdmin();
                sender.sendMessage("§eĐã hủy phiên hiện tại và hoàn cược.");
            }
            case "reload" -> {
                try {
                    service.reload();
                    sender.sendMessage("§aĐã tải lại taixiu.yml, gui.yml và bedrock/taixiu.yml.");
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage("§cConfig không hợp lệ, giữ cấu hình đang chạy: " + ex.getMessage());
                }
            }

            default -> help(sender);
        }
        return true;
    }

    private void status(CommandSender sender) {
        TaiXiuService.RoundSnapshot data = service.snapshot(sender instanceof Player player ? player.getUniqueId() : null);
        sender.sendMessage("§6Tài Xỉu §8• §f" + data.state() + " §8• §fPhiên #" + data.roundId()
                + " §8• §e" + data.secondsRemaining() + "s");
        sender.sendMessage("§cTài §f" + TaiXiuService.money(data.taiTotal()) + "$ §8• §9Xỉu §f"
                + TaiXiuService.money(data.xiuTotal()) + "$");
    }

    private void showStats(Player player) {
        TaiXiuStore.PlayerStats stats = service.stats(player.getUniqueId());
        long profit = stats.totalPayout() - stats.totalBet();
        player.sendMessage("§6Thống kê Tài Xỉu §8• §f" + stats.rounds() + " phiên §8• §a" + stats.wins() + " thắng");
        player.sendMessage("§7Tổng cược: §f" + TaiXiuService.money(stats.totalBet()) + "$ §8• §7Lãi/lỗ: "
                + (profit >= 0 ? "§a+" : "§c") + String.format(Locale.US, "%,d", profit) + "$");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage("§cLệnh này chỉ dành cho người chơi.");
        return null;
    }

    private boolean isAdminSubcommand(String sub) {
        return List.of("status", "pause", "resume", "cancel", "reload").contains(sub);
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§6/tx §7- mở giao diện");
        sender.sendMessage("§6/tx cuoc <tai|xiu> <tiền> §7- đặt cược nhanh");
        sender.sendMessage("§6/tx ban §7- mở bàn cược 3D tương tác");
        sender.sendMessage("§6/tx cau §7- xem cầu 3D riêng tư");
        sender.sendMessage("§6/tx stats §7- xem thống kê cá nhân");
        if (sender.hasPermission("ocloudtaixiu.admin")) {
            sender.sendMessage("§c/tx <status|pause|resume|cancel|reload|next|setnext>");
        }
        if (sender.hasPermission("ocloudtaixiu.setnext")) sender.sendMessage("§c/tx setnext <tai|xiu|boba|random> §7- đặt phiên kế");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("cuoc", "ban", "cau", "stats"));
            if (sender.hasPermission("ocloudtaixiu.admin")) options.addAll(List.of("status", "pause", "resume", "cancel", "reload"));
            if (sender.hasPermission("ocloudtaixiu.setnext")) options.addAll(List.of("setnext", "next"));
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setnext") && sender.hasPermission("ocloudtaixiu.setnext")) {
            return filter(List.of("tai", "xiu", "boba", "random"), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("cuoc") || args[0].equalsIgnoreCase("bet"))) {
            return filter(List.of("tai", "xiu"), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("cuoc") || args[0].equalsIgnoreCase("bet"))) {
            return filter(List.of("100k", "500k", "1m", "5m", "10m"), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(lower)).toList();
    }
}
