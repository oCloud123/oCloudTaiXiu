package com.ocloud.taixiu.game;

import com.ocloud.taixiu.util.BedrockPlatform;
import com.ocloud.taixiu.util.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class BedrockTaiXiuUi {
    private static final String SIMPLE_FORM = "org.geysermc.cumulus.form.SimpleForm";
    private static final String CUSTOM_FORM = "org.geysermc.cumulus.form.CustomForm";
    private final JavaPlugin plugin;
    private final TaiXiuService service;

    BedrockTaiXiuUi(JavaPlugin plugin, TaiXiuService service) {
        this.plugin = plugin;
        this.service = service;
    }

    boolean tryOpen(Player player) { return openBedrockMain(player); }

    private boolean openBedrockMain(Player player) {
        TaiXiuService.RoundSnapshot data = service.snapshot(player.getUniqueId());
        YamlConfiguration yaml = bedrockConfig();
        StringBuilder content = new StringBuilder()
                .append("Phiên #").append(data.roundId() <= 0 ? "--" : data.roundId()).append(" • ").append(state(data))
                .append("\n\n§cTÀI §f").append(TaiXiuService.money(data.taiTotal())).append("$")
                .append("\n§9XỈU §f").append(TaiXiuService.money(data.xiuTotal())).append("$")
                .append("\n\nSố dư: ").append(TaiXiuService.money(service.playerBalance(player))).append("$")
                .append("\nNhận thêm Tài: ").append(TaiXiuService.money(Math.min(service.playerBalance(player),
                        service.maximumAdditionalBet(player.getUniqueId(), TaiXiuMath.Side.TAI)))).append("$")
                .append("\nNhận thêm Xỉu: ").append(TaiXiuService.money(Math.min(service.playerBalance(player),
                        service.maximumAdditionalBet(player.getUniqueId(), TaiXiuMath.Side.XIU)))).append("$");
        if (data.ownBet() != null) content.append("\n\n§aĐã cược ").append(data.ownBet().side().displayName())
                .append(": ").append(TaiXiuService.money(data.ownBet().amount())).append("$");
        List<BedrockButton> buttons = List.of(
                new BedrockButton(text(yaml, "button-tai", "§cCƯỢC TÀI"), () -> openBedrockAmount(player, TaiXiuMath.Side.TAI)),
                new BedrockButton(text(yaml, "button-xiu", "§9CƯỢC XỈU"), () -> openBedrockAmount(player, TaiXiuMath.Side.XIU)),
                new BedrockButton(text(yaml, "button-table", "§6BÀN CƯỢC 3D"), () -> service.openBettingDisplay(player)),
                new BedrockButton(text(yaml, "button-history", "§dCẦU TÀI XỈU"), () -> openBedrockHistory(player)),
                new BedrockButton(text(yaml, "button-stats", "§aTHỐNG KÊ"), () -> openBedrockStats(player)),
                new BedrockButton(text(yaml, "button-refresh", "§7LÀM MỚI"), () -> openBedrockMain(player)));
        return simple(player, text(yaml, "title", "TÀI XỈU"), content.toString(), buttons);
    }

    private void openBedrockAmount(Player player, TaiXiuMath.Side side) {
        YamlConfiguration yaml = bedrockConfig();
        try {
            Class<?> formClass = BedrockPlatform.formClass(CUSTOM_FORM, player.getUniqueId());
            Object builder = formClass.getMethod("builder").invoke(null);
            invoke(builder, "title", "CƯỢC " + side.displayName().toUpperCase(Locale.ROOT));
            String suggestedAmount = suggestedAmount(player);
            invoke(builder, "input", text(yaml, "amount-label", "Số tiền (100k, 1m, all-in hoặc max)"),
                    suggestedAmount, suggestedAmount);
            Consumer<Object> handler = response -> {
                String value = responseInput(response);
                FoliaScheduler.runForPlayer(plugin, player, () -> openBedrockConfirm(player, side, value));
            };
            invoke(builder, "validResultHandler", handler);
            if (!BedrockPlatform.sendForm(player.getUniqueId(), invoke(builder, "build"))) openBedrockMain(player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            plugin.getLogger().fine("Không thể mở Bedrock Tài Xỉu CustomForm: " + ex.getMessage());
            openBedrockMain(player);
        }
    }

    private String suggestedAmount(Player player) {
        int percent = service.config().bettingDisplayPercentages.get(0);
        long amount = TaiXiuMath.percentageOf(service.playerBalance(player), percent);
        return String.valueOf(Math.max(service.config().minimumBet, amount));
    }

    private void openBedrockConfirm(Player player, TaiXiuMath.Side side, String rawAmount) {
        long amount = service.resolveBetAmount(player, side, rawAmount);
        if (amount <= 0L) {
            service.placeBet(player, side, rawAmount);
            return;
        }
        long expectedRound = service.snapshot(player.getUniqueId()).roundId();
        simple(player, service.guiText("confirm.title", "XÁC NHẬN CƯỢC"),
                TaiXiuService.replacements(service.guiText("confirm.amount", "Cửa %side%: %amount%$"),
                        java.util.Map.of("%side%", side.displayName(), "%amount%", TaiXiuService.money(amount)))
                        + "\n\n" + service.guiText("confirm.warning", "Cược không thể rút hoặc đổi cửa."), List.of(
                new BedrockButton(service.guiText("buttons.confirm", "XÁC NHẬN"), () -> service.placeBetForRound(player, side, amount, expectedRound)),
                new BedrockButton(text(bedrockConfig(), "button-back", "QUAY LẠI"), () -> openBedrockMain(player))));
    }

    private void openBedrockStats(Player player) {
        TaiXiuStore.PlayerStats stats = service.stats(player.getUniqueId());
        long profit = stats.totalPayout() - stats.totalBet();
        long losses = Math.max(0L, stats.rounds() - stats.wins());
        double winRate = stats.rounds() == 0L ? 0.0d : stats.wins() * 100.0d / stats.rounds();
        String content = "§6§lTHÀNH TÍCH§r"
                + "\n§7Đã chơi: §f" + stats.rounds() + " phiên"
                + "\n§aThắng: " + stats.wins() + "§8  •  §cThua: " + losses
                + "\n§7Tỷ lệ thắng: §f" + String.format(Locale.US, "%.1f%%", winRate)
                + "\n\n§6§lDÒNG TIỀN§r"
                + "\n§7Tổng đã cược: §f" + TaiXiuService.money(stats.totalBet()) + "$"
                + "\n§7Tổng đã nhận: §f" + TaiXiuService.money(stats.totalPayout()) + "$"
                + "\n§7Lãi ròng: " + (profit >= 0 ? "§a+" : "§c") + String.format(Locale.US, "%,d", profit) + "$"
                + "\n\n§8Lãi ròng = tổng nhận − tổng cược.";
        simple(player, "THỐNG KÊ TÀI XỈU", content,
                List.of(new BedrockButton("§7QUAY LẠI", () -> openBedrockMain(player))));
    }

    private void openBedrockHistory(Player player) {
        StringBuilder content = new StringBuilder();
        for (TaiXiuStore.RoundHistory row : service.history()) {
            content.append("#").append(row.id()).append(" • ").append(row.first()).append('-')
                    .append(row.second()).append('-').append(row.third()).append(" = ").append(row.total())
                    .append(" • ").append(result(row)).append('\n');
        }
        if (content.isEmpty()) content.append("Chưa có phiên đã kết thúc.");
        simple(player, text(bedrockConfig(), "history-title", "CẦU TÀI XỈU"), content.toString(),
                List.of(new BedrockButton(text(bedrockConfig(), "button-back", "QUAY LẠI"), () -> openBedrockMain(player))));
    }

    private boolean simple(Player player, String title, String content, List<BedrockButton> buttons) {
        try {
            Class<?> formClass = BedrockPlatform.formClass(SIMPLE_FORM, player.getUniqueId());
            Object builder = formClass.getMethod("builder").invoke(null);
            invoke(builder, "title", title);
            invoke(builder, "content", content);
            BedrockPlatform.configureSimpleButtons(builder, buttons.stream()
                    .map(button -> new BedrockPlatform.SimpleButton(button.label,
                            () -> FoliaScheduler.runForPlayer(plugin, player, button.action))).toList());
            return BedrockPlatform.sendForm(player.getUniqueId(), invoke(builder, "build"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            plugin.getLogger().fine("Không thể mở Bedrock Tài Xỉu SimpleForm: " + ex.getMessage());
            return false;
        }
    }

    private String state(TaiXiuService.RoundSnapshot data) {
        return switch (data.state()) {
            case OPEN -> "Còn " + String.format("%02d:%02d", data.secondsRemaining() / 60, data.secondsRemaining() % 60);
            case ROLLING -> "Đang lắc xúc xắc";
            case PAUSED -> "Đang tạm dừng";
            case WAITING -> "Chuẩn bị phiên mới";
        };
    }

    private String result(TaiXiuStore.RoundHistory row) {
        return row.triple() ? "BỘ BA" : row.side() == null ? "--" : row.side().displayName().toUpperCase(Locale.ROOT);
    }

    private String dice(int value) {
        return service.configuredDieIcon(value);
    }

    private Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        return BedrockPlatform.invoke(target, name, args);
    }
    private String responseInput(Object response) { return BedrockPlatform.firstFormInput(response); }

    private YamlConfiguration bedrockConfig() {
        return service.config().bedrock();
    }

    private String text(YamlConfiguration yaml, String path, String fallback) {
        return yaml.getString(path, fallback);
    }

    private record BedrockButton(String label, Runnable action) { }
}
