package com.ocloud.taixiu.game;

import com.ocloud.taixiu.util.BedrockPlatform;
import com.ocloud.taixiu.util.FoliaScheduler;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
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

public final class NativeTaiXiuUi implements PlayerMenu {
    private static final String SIMPLE_FORM = "org.geysermc.cumulus.form.SimpleForm";
    private static final String CUSTOM_FORM = "org.geysermc.cumulus.form.CustomForm";
    private final JavaPlugin plugin;
    private final TaiXiuService service;

    public NativeTaiXiuUi(JavaPlugin plugin, TaiXiuService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void open(Player player) { openJavaMain(player); }

    private void openJavaMain(Player player) {
        TaiXiuService.RoundSnapshot data = service.snapshot(player.getUniqueId());
        List<DialogBody> body = new ArrayList<>();
        body.add(line(TaiXiuService.replacements(service.guiText("main.round", "PHIÊN #%round%  •  %state%"),
                java.util.Map.of("%round%", data.roundId() <= 0 ? "--" : String.valueOf(data.roundId()), "%state%", state(data))), 0xF6C453));
        body.add(line(Component.text(service.guiText("main.tai", "TÀI  "), TextColor.color(0xFF6B6B))
                .append(Component.text(TaiXiuService.money(data.taiTotal()) + "$", TextColor.color(0xF8FAFC)))
                .append(Component.text("       │       ", TextColor.color(0x475569)))
                .append(Component.text(service.guiText("main.xiu", "XỈU  "), TextColor.color(0x56C7FF)))
                .append(Component.text(TaiXiuService.money(data.xiuTotal()) + "$", TextColor.color(0xF8FAFC)))));
        body.add(line("────────────────────────────", 0x334155));
        body.add(line(Component.text(service.guiText("main.balance", "Số dư khả dụng  "), TextColor.color(0x94A3B8))
                .append(Component.text(TaiXiuService.money(service.playerBalance(player)) + "$", TextColor.color(0xF8FAFC)))));
        long balance = service.playerBalance(player);
        long taiMaximum = Math.min(balance, service.maximumAdditionalBet(player.getUniqueId(), TaiXiuMath.Side.TAI));
        long xiuMaximum = Math.min(balance, service.maximumAdditionalBet(player.getUniqueId(), TaiXiuMath.Side.XIU));
        body.add(line(Component.text(service.guiText("main.limits", "Nhận thêm  "), TextColor.color(0x94A3B8))
                .append(Component.text("Tài " + TaiXiuService.money(taiMaximum) + "$", TextColor.color(0xFF6B6B)))
                .append(Component.text("  •  ", TextColor.color(0x475569)))
                .append(Component.text("Xỉu " + TaiXiuService.money(xiuMaximum) + "$", TextColor.color(0x56C7FF)))));
        if (data.ownBet() != null) {
            int sideColor = data.ownBet().side() == TaiXiuMath.Side.TAI ? 0xFF6B6B : 0x56C7FF;
            body.add(line(Component.text(service.guiText("main.own-bet", "Cược của bạn  "), TextColor.color(0x94A3B8))
                    .append(Component.text(data.ownBet().side().displayName().toUpperCase(Locale.ROOT), TextColor.color(sideColor)))
                    .append(Component.text("  •  " + TaiXiuService.money(data.ownBet().amount()) + "$", TextColor.color(0x67E8A5)))));
        } else {
            body.add(line(service.guiText("main.instruction", "Chọn một cửa bên dưới. Cược đã xác nhận không thể đổi hoặc rút."), 0x94A3B8));
        }
        if (data.lastResult() != null) {
            TaiXiuStore.RoundHistory last = data.lastResult();
            int resultColor = last.triple() ? 0xC084FC : last.side() == TaiXiuMath.Side.TAI ? 0xFF6B6B : 0x56C7FF;
            body.add(line(Component.text(service.guiText("main.last-result", "Kết quả gần nhất  "), TextColor.color(0x94A3B8))
                    .append(Component.text(dice(last.first()) + " " + dice(last.second()) + " " + dice(last.third()), TextColor.color(0xF8FAFC)))
                    .append(Component.text("  •  Tổng " + last.total() + "  •  ", TextColor.color(0xCBD5E1)))
                    .append(Component.text(result(last), TextColor.color(resultColor)))));
        }

        String suggestedAmount = suggestedAmount(player);
        DialogInput amount = DialogInput.text("tx_amount", plain(service.guiText("native.amount-label", "Số tiền: 100k, 1m, all-in hoặc max"), 0xFDE68A))
                .initial(suggestedAmount).width(service.guiInt("native.input-width", 260)).maxLength(service.guiInt("native.input-max-length", 24)).build();
        ActionButton tai = betButton(service.guiText("buttons.tai", "CƯỢC TÀI"), 0xF87171, TaiXiuMath.Side.TAI);
        ActionButton xiu = betButton(service.guiText("buttons.xiu", "CƯỢC XỈU"), 0x38BDF8, TaiXiuMath.Side.XIU);
        ActionButton table = button(service.guiText("buttons.table", "BÀN CƯỢC 3D"), 0xF6C453, service::openBettingDisplay);
        ActionButton history = button(service.guiText("buttons.history", "CẦU 3D"), 0xC084FC, service::openHistory);
        ActionButton stats = button(service.guiText("buttons.stats", "THỐNG KÊ"), 0xA7F3D0, this::openJavaStats);
        ActionButton refresh = button(service.guiText("buttons.refresh", "LÀM MỚI"), 0xCBD5E1, this::openJavaMain);
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(plain(service.guiText("title", "TÀI XỈU"), 0xF8FAFC))
                        .canCloseWithEscape(true).pause(false).afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(body).inputs(List.of(amount)).build())
                .type(DialogType.multiAction(List.of(tai, xiu, table, history, stats, refresh)).columns(service.guiInt("native.columns", 2)).build()));
        player.showDialog(dialog);
    }

    private ActionButton betButton(String label, int color, TaiXiuMath.Side side) {
        return ActionButton.builder(plain(label, color)).width(service.guiInt("native.button-width", 150))
                .action(DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player player)) return;
                    String amount = response.getText("tx_amount");
                    FoliaScheduler.runForPlayer(plugin, player, () -> openJavaConfirm(player, side, amount));
                }, ClickCallback.Options.builder().uses(1).build())).build();
    }

    private void openJavaConfirm(Player player, TaiXiuMath.Side side, String rawAmount) {
        long amount = service.resolveBetAmount(player, side, rawAmount);
        if (amount <= 0L) {
            service.placeBet(player, side, rawAmount);
            return;
        }
        long expectedRound = service.snapshot(player.getUniqueId()).roundId();
        ActionButton confirm = button(service.guiText("buttons.confirm", "XÁC NHẬN"), 0x4ADE80,
                target -> service.placeBetForRound(target, side, amount, expectedRound));
        ActionButton back = button(service.guiText("buttons.back", "QUAY LẠI"), 0x94A3B8, this::openJavaMain);
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(plain(service.guiText("confirm.title", "XÁC NHẬN CƯỢC"), 0xF8FAFC))
                        .canCloseWithEscape(true).pause(false).afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(line(TaiXiuService.replacements(service.guiText("confirm.amount", "Cửa %side%: %amount%$"),
                                java.util.Map.of("%side%", side.displayName(), "%amount%", TaiXiuService.money(amount))), 0xFDE68A),
                                line(service.guiText("confirm.warning", "Cược đã xác nhận không thể rút hoặc đổi cửa."), 0xFB7185))).build())
                .type(DialogType.multiAction(List.of(confirm, back)).columns(service.guiInt("native.columns", 2)).build())));
    }

    private void openJavaStats(Player player) {
        TaiXiuStore.PlayerStats stats = service.stats(player.getUniqueId());
        long profit = stats.totalPayout() - stats.totalBet();
        long losses = Math.max(0L, stats.rounds() - stats.wins());
        double winRate = stats.rounds() == 0L ? 0.0d : stats.wins() * 100.0d / stats.rounds();
        List<DialogBody> body = List.of(
                line(service.guiText("stats.achievement", "THÀNH TÍCH"), 0xF6C453),
                line(Component.text(service.guiText("stats.played", "Đã chơi  "), TextColor.color(0x94A3B8))
                        .append(Component.text(stats.rounds() + " phiên", TextColor.color(0xF8FAFC)))
                        .append(Component.text("   •   ", TextColor.color(0x475569)))
                        .append(Component.text(stats.wins() + " thắng", TextColor.color(0x67E8A5)))
                        .append(Component.text("   •   ", TextColor.color(0x475569)))
                        .append(Component.text(losses + " thua", TextColor.color(0xFF7A7A)))),
                line(Component.text(service.guiText("stats.win-rate", "Tỷ lệ thắng  "), TextColor.color(0x94A3B8))
                        .append(Component.text(String.format(Locale.US, "%.1f%%", winRate), TextColor.color(0xF8FAFC)))),
                line("────────────────────────────", 0x334155),
                line(service.guiText("stats.cash-flow", "DÒNG TIỀN"), 0xF6C453),
                line(Component.text(service.guiText("stats.total-bet", "Tổng đã cược  "), TextColor.color(0x94A3B8))
                        .append(Component.text(TaiXiuService.money(stats.totalBet()) + "$", TextColor.color(0xF8FAFC)))),
                line(Component.text(service.guiText("stats.total-payout", "Tổng đã nhận  "), TextColor.color(0x94A3B8))
                        .append(Component.text(TaiXiuService.money(stats.totalPayout()) + "$", TextColor.color(0xF8FAFC)))),
                line(Component.text(service.guiText("stats.profit", "Lãi ròng  "), TextColor.color(0x94A3B8))
                        .append(Component.text((profit >= 0 ? "+" : "") + String.format(Locale.US, "%,d", profit) + "$",
                                TextColor.color(profit >= 0 ? 0x67E8A5 : 0xFF7A7A)))),
                line(service.guiText("stats.explanation", "Lãi ròng = tổng thực nhận − tổng tiền đã cược."), 0x64748B));
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(plain(service.guiText("stats.title", "THỐNG KÊ TÀI XỈU"), 0xF8FAFC)).canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE).body(body).build())
                .type(DialogType.multiAction(List.of(button(service.guiText("buttons.back", "QUAY LẠI"), 0x94A3B8, this::openJavaMain))).columns(1).build())));
    }

    private String suggestedAmount(Player player) {
        int percent = service.config().bettingDisplayPercentages.get(0);
        long amount = TaiXiuMath.percentageOf(service.playerBalance(player), percent);
        return String.valueOf(Math.max(service.config().minimumBet, amount));
    }

    private ActionButton button(String label, int color, Consumer<Player> action) {
        return ActionButton.builder(plain(label, color)).width(service.guiInt("native.button-width", 150))
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player player) FoliaScheduler.runForPlayer(plugin, player, () -> action.accept(player));
                }, ClickCallback.Options.builder().uses(1).build())).build();
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

    private DialogBody line(String value, int color) {
        return DialogBody.plainMessage(plain(value, color), service.guiInt("native.body-width", 360));
    }

    private DialogBody line(Component value) {
        return DialogBody.plainMessage(value.decoration(TextDecoration.BOLD, false), service.guiInt("native.body-width", 360));
    }

    private Component plain(String value, int rgb) {
        return TaiXiuService.component(value).colorIfAbsent(TextColor.color(rgb)).decoration(TextDecoration.BOLD, false);
    }

}
