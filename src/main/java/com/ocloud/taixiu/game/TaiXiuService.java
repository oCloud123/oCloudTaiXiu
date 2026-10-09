package com.ocloud.taixiu.game;

import com.ocloud.taixiu.economy.EconomyBridge;
import com.ocloud.taixiu.util.ColorUtil;
import com.ocloud.taixiu.util.FoliaScheduler;
import com.ocloud.taixiu.util.PluginSound;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TaiXiuService {

    public enum State { WAITING, OPEN, ROLLING, PAUSED }

    private final JavaPlugin plugin;
    private final EconomyBridge economy;
    private final TaiXiuStore store;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, PlayerBet> bets = new LinkedHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Set<UUID> processingBets = ConcurrentHashMap.newKeySet();
    private volatile TaiXiuConfig config;
    private TaiXiuUi ui;
    private TaiXiuHistoryDisplay historyDisplay;
    private TaiXiuBettingDisplay bettingDisplay;
    private FoliaScheduler.TaskHandle tickTask;
    private FoliaScheduler.TaskHandle rollingTask;
    private volatile State state = State.WAITING;
    private boolean adminPaused;
    private long roundId;
    private int secondsRemaining;
    private int cooldownRemaining = 3;
    private int consecutiveEmptyRounds;
    private long taiTotal;
    private long xiuTotal;
    private int roundTaxPercent;
    private boolean roundTriplesHouseWins;
    private int roundTaiOutcomeWeight;
    private int roundXiuOutcomeWeight;
    private int roundTripleOutcomeWeight;
    private int resultFirst;
    private int resultSecond;
    private int resultThird;
    private TaiXiuMath.Side resultSide;
    private TaiXiuStore.RoundHistory lastResult;
    private NextOutcome nextOutcome;
    private NextOutcome roundForcedOutcome;
    private boolean shuttingDown;
    private int pendingRetrySeconds;

    public TaiXiuService(JavaPlugin plugin) {
        this.plugin = plugin;

        this.economy = new EconomyBridge(plugin);
        this.store = new TaiXiuStore(plugin);
    }

    public void start() {
        config = TaiXiuConfig.load(plugin);
        try {
            store.open();
            nextOutcome = NextOutcome.parse(store.metadata("next_outcome"));
            recoverInterruptedBets();
            retryPendingPayouts(null);
            List<TaiXiuStore.RoundHistory> history = store.history(1);
            lastResult = history.isEmpty() ? null : history.get(0);
        } catch (SQLException ex) {
            plugin.getLogger().severe("Không thể mở dữ liệu Tài Xỉu: " + ex.getMessage());
            throw new IllegalStateException("Không thể mở dữ liệu Tài Xỉu", ex);
        }
        ui = new TaiXiuUi(plugin, this);
        historyDisplay = new TaiXiuHistoryDisplay(plugin, this);
        bettingDisplay = new TaiXiuBettingDisplay(plugin, this);
        tickTask = FoliaScheduler.runTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean hasNativeGui() { return ui != null && ui.hasNativeMenu(); }

    public synchronized void reload() {
        TaiXiuConfig reloaded = TaiXiuConfig.load(plugin);
        config = reloaded;
        if (!reloaded.enabled && (state == State.OPEN || state == State.ROLLING)) {
            cancelCurrentRound("RELOAD_DISABLED", true);
        }
    }

    public synchronized void shutdown() {
        shuttingDown = true;
        if (tickTask != null) tickTask.cancel();
        if (rollingTask != null) rollingTask.cancel();
        cancelCurrentRound("PLUGIN_DISABLE", true);
        for (UUID playerId : new ArrayList<>(bossBars.keySet())) removeParticipantView(playerId);
        if (historyDisplay != null) historyDisplay.closeAll(false);
        if (bettingDisplay != null) bettingDisplay.closeAll(false);
        if (ui != null) ui.close();
        ActionBarPriority.clear();
        store.close();
    }

    public void open(Player player) {
        if (player == null || !player.isOnline()) return;
        ui.open(player);
    }

    public void openHistory(Player player) {
        if (player == null || !player.isOnline()) return;
        closeBettingDisplay(player, false);
        historyDisplay.open(player);
    }

    public void closeHistory(Player player, boolean animate) {
        if (historyDisplay != null && player != null) historyDisplay.close(player.getUniqueId(), animate);
    }

    public void openBettingDisplay(Player player) {
        if (player == null || !player.isOnline()) return;
        closeHistory(player, false);
        bettingDisplay.open(player);
    }

    public void closeBettingDisplay(Player player, boolean animate) {
        if (bettingDisplay != null && player != null) bettingDisplay.close(player.getUniqueId(), animate);
    }

    public boolean handleBettingDisplayClick(Player player) {
        return bettingDisplay != null && bettingDisplay.handleClick(player);
    }

    public synchronized RoundSnapshot snapshot(UUID playerId) {
        PlayerBet own = bets.get(playerId);
        return new RoundSnapshot(state, roundId, secondsRemaining, taiTotal, xiuTotal,
                currentMaximumBet(own), own, lastResult, adminPaused);
    }

    public synchronized List<TaiXiuStore.RoundHistory> history() {
        try {
            return store.history(config.historySize);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Không thể đọc lịch sử Tài Xỉu: " + ex.getMessage());
            return List.of();
        }
    }

    public TaiXiuConfig config() {
        return config;
    }

    public TaiXiuStore.PlayerStats stats(UUID playerId) {
        try {
            return store.playerStats(playerId);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Không thể đọc thống kê Tài Xỉu: " + ex.getMessage());
            return new TaiXiuStore.PlayerStats(0L, 0L, 0L, 0L);
        }
    }

    public long playerBalance(Player player) {
        if (player == null) return 0L;
        double balance = economy.getBalance(player);
        if (!Double.isFinite(balance) || balance <= 0.0d) return 0L;
        return balance >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.floor(balance);
    }

    public void placeBet(Player player, TaiXiuMath.Side side, String rawAmount) {
        long amount = resolveBetAmount(player, side, rawAmount);
        if (amount <= 0L) {
            if (isSmartAmount(rawAmount)) {
                if (playerBalance(player) <= 0L) {
                    send(player, config.text("messages.insufficient-money", "&cSố dư của bạn không đủ."));
                    return;
                }
                send(player, replacements(config.text("messages.maximum",
                                "&cCửa %side% hiện chỉ nhận tối đa &f%maximum%$&c."),
                        Map.of("%maximum%", money(maximumAdditionalBet(
                                player == null ? null : player.getUniqueId(), side)),
                                "%side%", side == null ? "--" : side.displayName())));
            } else {
                send(player, config.text("messages.invalid-amount", "&cSố tiền cược không hợp lệ."));
            }
            return;
        }
        placeBet(player, side, amount);
    }

    public synchronized void placeBet(Player player, TaiXiuMath.Side side, long amount) {
        if (player == null || side == null || !player.isOnline()) return;
        if (amount <= 0) {
            send(player, config.text("messages.invalid-amount", "&cSố tiền cược không hợp lệ."));
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!processingBets.add(playerId)) {
            send(player, config.text("messages.already-processing", "&cGiao dịch đang được xử lý, vui lòng chờ."));
            return;
        }
        try {
            if (!config.enabled || adminPaused || state != State.OPEN || secondsRemaining <= 0) {
                send(player, config.text("messages.betting-closed", "&cCổng cược hiện đang đóng."));
                return;
            }
            if (!economy.isAvailable()) {
                send(player, config.text("messages.economy-unavailable", "&cHệ thống economy hiện không khả dụng."));
                return;
            }
            PlayerBet existing = bets.get(playerId);
            if (existing != null && existing.side() != side) {
                send(player, config.text("messages.opposite-side", "&cBạn không thể đổi cửa trong cùng một phiên."));
                return;
            }
            long currentAmount = existing == null ? 0L : existing.amount();
            long combined;
            try {
                combined = Math.addExact(currentAmount, amount);
            } catch (ArithmeticException ex) {
                send(player, config.text("messages.invalid-amount", "&cSố tiền cược không hợp lệ."));
                return;
            }
            if (combined < config.minimumBet) {
                send(player, replace(config.text("messages.minimum", "&cCược tối thiểu là &f%minimum%$&c."),
                        "%minimum%", money(config.minimumBet)));
                return;
            }
            long allowedMaximum = currentMaximumBet(side, existing);
            if (combined > allowedMaximum) {
                send(player, replacements(config.text("messages.maximum",
                                "&cCửa %side% hiện nhận tối đa &f%maximum%$&c."),
                        Map.of("%maximum%", money(Math.max(0L, allowedMaximum - currentAmount)),
                                "%side%", side.displayName())));
                return;
            }
            long projectedTaiTotal;
            long projectedXiuTotal;
            try {
                projectedTaiTotal = side == TaiXiuMath.Side.TAI ? Math.addExact(taiTotal, amount) : taiTotal;
                projectedXiuTotal = side == TaiXiuMath.Side.XIU ? Math.addExact(xiuTotal, amount) : xiuTotal;
                Math.addExact(projectedTaiTotal, projectedXiuTotal);
            } catch (ArithmeticException ex) {
                send(player, config.text("messages.invalid-amount", "&cSố tiền cược không hợp lệ."));
                return;
            }
            if (!economy.has(player, amount)) {
                send(player, config.text("messages.insufficient-money", "&cSố dư của bạn không đủ."));
                return;
            }
            if (!economy.withdraw(player, amount)) {
                send(player, config.text("messages.withdraw-failed", "&cKhông thể trừ tiền cược. Tiền của bạn không bị mất."));
                return;
            }
            try {
                store.addBet(roundId, playerId, player.getName(), side, amount);
            } catch (SQLException ex) {
                boolean refunded = economy.deposit(player, amount);
                if (!refunded) {
                    plugin.getLogger().severe("HOÀN TIỀN THẤT BẠI cho " + playerId + ": " + amount + "; cần đối soát thủ công.");
                }
                plugin.getLogger().severe("Không thể lưu cược Tài Xỉu; hoàn tiền=" + refunded + ": " + ex.getMessage());
                send(player, refunded ? config.text("messages.internal-error", "&cKhông thể lưu cược. Tiền đã được hoàn lại.")
                        : config.text("messages.refund-failed", "&cKhông thể hoàn tiền, vui lòng báo quản trị viên để đối soát."));
                return;
            }
            PlayerBet updated = new PlayerBet(playerId, player.getName(), side, combined);
            bets.put(playerId, updated);
            consecutiveEmptyRounds = 0;
            taiTotal = projectedTaiTotal;
            xiuTotal = projectedXiuTotal;
            ensureParticipantView(player);
            long payout = TaiXiuMath.payout(combined, roundTaxPercent);
            String message = existing == null
                    ? config.text("messages.bet-success", "&aĐã ghi nhận &f%amount%$ &acho cửa %side%. &7Nếu thắng nhận &a%payout%$&7.")
                    : config.text("messages.bet-added", "&aĐã cộng thêm &f%added%$ &avào cửa %side%. &7Tổng cược: &f%amount%$&7.");
            message = replacements(message, Map.of("%amount%", money(combined), "%added%", money(amount),
                    "%side%", side.displayName(), "%payout%", money(payout)));
            send(player, message);
            updateParticipant(player);
        } finally {
            processingBets.remove(playerId);
        }
    }

    public synchronized long maximumAdditionalBet(UUID playerId, TaiXiuMath.Side side) {
        if (side == null || state != State.OPEN || secondsRemaining <= 0 || adminPaused) return 0L;
        PlayerBet existing = playerId == null ? null : bets.get(playerId);
        if (existing != null && existing.side() != side) return 0L;
        long current = existing == null ? 0L : existing.amount();
        return Math.max(0L, currentMaximumBet(side, existing) - current);
    }

    public synchronized void placeBetForRound(Player player, TaiXiuMath.Side side, long amount, long expectedRound) {
        if (roundId != expectedRound) {
            send(player, config.text("messages.betting-closed", "&cPhiên cược đã kết thúc; hãy mở lại giao diện."));
            return;
        }
        placeBet(player, side, amount);
    }

    public long resolveBetAmount(Player player, TaiXiuMath.Side side, String rawAmount) {
        return smartAmount(player, side, rawAmount);
    }

    private long smartAmount(Player player, TaiXiuMath.Side side, String rawAmount) {
        if (rawAmount == null) return -1L;
        String normalized = rawAmount.trim().toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "");
        long balance = playerBalance(player);
        long safeMaximum = maximumAdditionalBet(player == null ? null : player.getUniqueId(), side);
        if (normalized.equals("all") || normalized.equals("allin") || normalized.equals("max")
                || normalized.equals("maxsafe")) {
            return Math.min(balance, safeMaximum);
        }
        if (normalized.endsWith("%")) {
            try {
                int percent = Integer.parseInt(normalized.substring(0, normalized.length() - 1));
                if (percent <= 0 || percent > 100) return -1L;
                long percentageAmount = TaiXiuMath.percentageOf(balance, percent);
                return Math.min(percentageAmount, safeMaximum);
            } catch (NumberFormatException ignored) {
                return -1L;
            }
        }
        return parseAmount(rawAmount);
    }

    private static boolean isSmartAmount(String rawAmount) {
        if (rawAmount == null) return false;
        String normalized = rawAmount.trim().toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "");
        if (normalized.endsWith("%")) return true;
        return normalized.equals("all") || normalized.equals("allin")
                || normalized.equals("max") || normalized.equals("maxsafe");
    }

    public synchronized boolean setPaused(boolean paused) {
        if (adminPaused == paused) return false;
        adminPaused = paused;
        if (paused) {
            cancelCurrentRound("ADMIN_PAUSE", true);
            state = State.PAUSED;
        } else {
            state = State.WAITING;
            cooldownRemaining = 1;
        }
        return true;
    }

    public synchronized void cancelByAdmin() {
        cancelCurrentRound("ADMIN_CANCEL", true);
    }

    public synchronized void handleJoin(Player player) {
        if (player == null) return;
        try {
            retryInterruptedRefunds(player.getUniqueId());
            retryPendingPayouts(player.getUniqueId());
            for (String message : store.drainNotifications(player.getUniqueId())) send(player, message);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Không thể đọc thông báo Tài Xỉu offline: " + ex.getMessage());
        }
        if (bets.containsKey(player.getUniqueId()) && (state == State.OPEN || state == State.ROLLING)) {
            ensureParticipantView(player);
            updateParticipant(player);
        }
    }

    public void handleQuit(Player player) {
        if (player == null) return;
        closeHistory(player, false);
        closeBettingDisplay(player, false);
        removeParticipantView(player.getUniqueId());
    }

    private synchronized void tick() {
        if (++pendingRetrySeconds >= 30) {
            pendingRetrySeconds = 0;
            try { retryPendingPayouts(null); } catch (SQLException ex) {
                plugin.getLogger().warning("Không thể thử lại thanh toán: " + ex.getMessage());
            }
        }
        if (!config.enabled || adminPaused) {
            state = State.PAUSED;
            return;
        }
        if (state == State.PAUSED) {
            state = State.WAITING;
            cooldownRemaining = 1;
        }
        if (state == State.WAITING) {
            if (--cooldownRemaining <= 0) attemptOpenRound();
            return;
        }
        if (state != State.OPEN) return;
        updateAllParticipants();
        if (config.reminderSeconds().contains(secondsRemaining)) notifyReminder(secondsRemaining);
        if (--secondsRemaining <= 0) closeBetting();
    }

    private void attemptOpenRound() {
        if (!economy.isAvailable()) {
            cooldownRemaining = 30;
            return;
        }
        try {
            roundId = store.createRound();
        } catch (SQLException ex) {
            plugin.getLogger().severe("Không thể tạo phiên Tài Xỉu: " + ex.getMessage());
            cooldownRemaining = 30;
            return;
        }
        bets.clear();
        taiTotal = 0L;
        xiuTotal = 0L;
        roundTaxPercent = config.taxPercent;
        roundTriplesHouseWins = config.triplesHouseWins;
        roundTaiOutcomeWeight = config.taiOutcomeWeight;
        roundXiuOutcomeWeight = config.xiuOutcomeWeight;
        roundTripleOutcomeWeight = config.tripleOutcomeWeight;
        roundForcedOutcome = nextOutcome;
        if (nextOutcome != null) {
            try { store.setMetadata("next_outcome", ""); nextOutcome = null; }
            catch (SQLException ex) {
                plugin.getLogger().severe("Không thể chốt kết quả phiên kế: " + ex.getMessage());
                cancelUnopenedRound();
                return;
            }
        }
        secondsRemaining = config.bettingSeconds;
        state = State.OPEN;
        broadcastOpen();
    }

    private void broadcastOpen() {
        String message = replacements(config.openMessage, Map.of("%round%", String.valueOf(roundId),
                "%time%", time(secondsRemaining)));
        boolean playInviteSound = shouldPlayInviteSound(consecutiveEmptyRounds, config.muteInviteAfterEmptyRounds);
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaScheduler.runForPlayer(plugin, player, () -> {
                if (!player.isOnline() || !config.announcementsEnabled) {
                    return;
                }
                send(player, message);
                if (playInviteSound) {
                    PluginSound.play(player, config.inviteSound, config.soundVolume, 1.1f);
                }
            });
        }
    }

    static boolean shouldPlayInviteSound(int consecutiveEmptyRounds, int muteAfterEmptyRounds) {
        return muteAfterEmptyRounds <= 0 || consecutiveEmptyRounds < muteAfterEmptyRounds;
    }

    private void notifyReminder(int seconds) {
        if (bets.isEmpty()) return;
        String reminder = replacements(config.text("messages.reminder", "&ePhiên #%round% còn %seconds% giây để nhận cược."),
                Map.of("%round%", String.valueOf(roundId), "%seconds%", String.valueOf(seconds)));
        for (UUID playerId : List.copyOf(bets.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            FoliaScheduler.runForPlayer(plugin, player, () -> {
                if (seconds <= 5) {
                    Component title = component(replacements(config.countdownTitle,
                            Map.of("%seconds%", String.valueOf(seconds), "%round%", String.valueOf(roundId))));
                    Component subtitle = component(replacements(config.countdownSubtitle,
                            Map.of("%seconds%", String.valueOf(seconds), "%round%", String.valueOf(roundId))));
                    player.showTitle(Title.title(title, subtitle,
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(150))));
                    PluginSound.play(player, config.countdownSound, config.soundVolume * 0.65f,
                            0.8f + (5 - seconds) * 0.1f);
                } else {
                    send(player, reminder);
                }
            });
        }
    }

    private synchronized void closeBetting() {
        if (state != State.OPEN) return;
        if (bets.isEmpty()) {
            if (consecutiveEmptyRounds < Integer.MAX_VALUE) consecutiveEmptyRounds++;
            try {
                store.finishRound(roundId, 0, 0, 0, "", 0L, 0L, 0, "SKIPPED");
            } catch (SQLException ex) {
                plugin.getLogger().warning("Không thể lưu phiên Tài Xỉu bỏ qua: " + ex.getMessage());
            }
            resetForNextRound();
            return;
        }
        state = State.ROLLING;
        TaiXiuMath.DiceRoll roll = roundForcedOutcome != null ? roundForcedOutcome.roll(random, roundTriplesHouseWins) : roundTriplesHouseWins
                ? TaiXiuMath.rollWeighted(random, roundTaiOutcomeWeight, roundXiuOutcomeWeight,
                roundTripleOutcomeWeight)
                : TaiXiuMath.rollFair(random, false);
        resultFirst = roll.first();
        resultSecond = roll.second();
        resultThird = roll.third();
        resultSide = roll.side();
        startRollingAnimation();
    }

    private void startRollingAnimation() {
        scheduleRollFrame(1);
    }

    private void scheduleRollFrame(int current) {
        rollingTask = FoliaScheduler.runLater(plugin, () -> {
            synchronized (TaiXiuService.this) {
            if (state != State.ROLLING) return;
            int first = current < 13 ? random.nextInt(1, 7) : resultFirst;
            int second = current < 17 ? 0 : current < 29 ? random.nextInt(1, 7) : resultSecond;
            int third = current < 33 ? 0 : current < 47 ? random.nextInt(1, 7) : resultThird;
            boolean firstRolling = current < 13;
            boolean secondRolling = current >= 17 && current < 29;
            boolean thirdRolling = current >= 33 && current < 47;
            boolean rolling = firstRolling || secondRolling || thirdRolling;
            boolean locked = current == 13 || current == 29 || current == 47;
            String dice = configuredRollDisplay(first, second, third, firstRolling, secondRolling, thirdRolling);
            for (UUID playerId : List.copyOf(bets.keySet())) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) continue;
                FoliaScheduler.runForPlayer(plugin, player, () -> {
                    player.sendActionBar(diceComponent(dice));
                    if (locked) {
                        float pitch = current == 13 ? 0.85f : current == 29 ? 1.05f : 1.3f;
                        PluginSound.play(player, config.diceLockSound, config.soundVolume, pitch);
                    } else if (rolling && current % 2 == 0) {
                        float pitch = firstRolling ? 0.75f + current * 0.018f
                                : secondRolling ? 0.85f + (current - 17) * 0.018f
                                : 0.95f + (current - 33) * 0.018f;
                        PluginSound.play(player, config.diceRollSound,
                                config.soundVolume * 0.45f, pitch);
                    }
                });
            }
            if (current >= 47) {
                rollingTask = FoliaScheduler.runLater(plugin, this::settleRound, 14L);
            } else {
                scheduleRollFrame(current + 1);
            }
            }
        }, rollDelay(current));
    }

    private long rollDelay(int frame) {
        if (frame == 1) return 1L;
        if (frame < 13) return frame >= 10 ? 4L : 2L;
        if (frame < 17) return 4L;
        if (frame < 29) return frame >= 26 ? 4L : 2L;
        if (frame < 33) return 4L;
        if (frame < 47) return frame >= 43 ? 4L : 2L;
        return 6L;
    }

    static String rollDisplay(int first, int second, int third,
                              boolean firstRolling, boolean secondRolling, boolean thirdRolling) {
        return formatRollDisplay("§8[ %dice_1% §8│ %dice_2% §8│ %dice_3% §8]",
                "§8?", "§f%icon%", "§f%icon%",
                first, second, third, firstRolling, secondRolling, thirdRolling,
                TaiXiuService::dieIcon);
    }

    private String configuredRollDisplay(int first, int second, int third,
                                         boolean firstRolling, boolean secondRolling, boolean thirdRolling) {
        return formatRollDisplay(config.rollingActionBar, config.rollingWaitingDie,
                config.rollingActiveDie, config.rollingLockedDie,
                first, second, third, firstRolling, secondRolling, thirdRolling, config::dieIcon);
    }

    private static String formatRollDisplay(String format, String waiting, String active, String locked,
                                            int first, int second, int third,
                                            boolean firstRolling, boolean secondRolling, boolean thirdRolling,
                                            java.util.function.IntFunction<String> icon) {
        return replacements(format, Map.of(
                "%dice_1%", rollSlot(first, firstRolling, waiting, active, locked, icon),
                "%dice_2%", rollSlot(second, secondRolling, waiting, active, locked, icon),
                "%dice_3%", rollSlot(third, thirdRolling, waiting, active, locked, icon)
        ));
    }

    private static String rollSlot(int value, boolean rolling, String waiting, String active, String locked,
                                   java.util.function.IntFunction<String> icon) {
        if (value <= 0) return waiting;
        return replace(rolling ? active : locked, "%icon%", icon.apply(value));
    }

    static String dieIcon(int value) {
        return switch (value) {
            case 1 -> "⚀";
            case 2 -> "⚁";
            case 3 -> "⚂";
            case 4 -> "⚃";
            case 5 -> "⚄";
            case 6 -> "⚅";
            default -> "?";
        };
    }

    static String dieFace(int value) {
        return dieIcon(value);
    }

    String configuredDieIcon(int value) {
        return config.dieIcon(value);
    }

    private synchronized void settleRound() {
        if (state != State.ROLLING) return;
        int total = resultFirst + resultSecond + resultThird;
        boolean triple = resultSide == null;
        try {
            store.settleRound(roundId, new TaiXiuMath.DiceRoll(resultFirst, resultSecond, resultThird, resultSide),
                    roundTaxPercent, taiTotal, xiuTotal);
            lastResult = new TaiXiuStore.RoundHistory(roundId, System.currentTimeMillis(), resultFirst, resultSecond,
                    resultThird, total, resultSide == null ? "TRIPLE" : resultSide.name(), taiTotal, xiuTotal, bets.size());
        } catch (SQLException ex) {
            plugin.getLogger().severe("Không thể lưu thanh toán phiên #" + roundId + ": " + ex.getMessage());
            adminPaused = true;
            cancelCurrentRound("SETTLEMENT_FAILED", true);
            return;
        }
        for (PlayerBet bet : List.copyOf(bets.values())) {
            if (resultSide != null && bet.side() == resultSide) {
                try {
                    for (TaiXiuStore.Bet pending : store.betsByStatus(bet.playerId(), "PAYOUT_PENDING")) {
                        if (pending.roundId() == roundId) {
                            String message = replacements(config.text("messages.win", "&aThắng phiên #%round%: %payout%$"),
                                    Map.of("%round%", String.valueOf(roundId), "%payout%", money(pending.payout()),
                                            "%tax%", money(pending.tax()), "%amount%", money(pending.amount()),
                                            "%side%", pending.side().displayName()));
                            queuePayment(pending, message, resultNotification(bet, true, pending.payout(), pending.tax(), total, triple));
                        }
                    }
                } catch (SQLException ex) { plugin.getLogger().severe(ex.getMessage()); }
            } else resultNotification(bet, false, 0, 0, total, triple).run();
        }
        if (triple) broadcastTripleResult(total);
        for (UUID playerId : List.copyOf(bets.keySet())) removeParticipantView(playerId);
        resetForNextRound();
    }

    private void broadcastTripleResult(int total) {
        String message = replacements(config.text("messages.triple-announcement",
                "&#C084FC&lBỘ BA &8• &7Phiên &f#%round% &8• &f%dice_raw% &8(&7tổng %total%&8)"
                        + " &8• &7Nhà cái thắng, cả Tài và Xỉu đều thua."),
                Map.of("%round%", String.valueOf(roundId), "%dice_raw%", resultFirst + "-" + resultSecond + "-" + resultThird,
                        "%total%", String.valueOf(total)));
        if (message.isBlank()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaScheduler.runForPlayer(plugin, player, () -> {
                if (player.isOnline()) send(player, message);
            });
        }
    }

    private Runnable resultNotification(PlayerBet bet, boolean won, long payout, long tax, int total, boolean triple) {
        String result = triple ? "BỘ BA" : resultSide.displayName().toUpperCase(Locale.ROOT);
        String titleTemplate = triple ? config.resultTitleTriple
                : resultSide == TaiXiuMath.Side.TAI ? config.resultTitleTai : config.resultTitleXiu;
        Map<String, String> resultValues = Map.of(
                "%round%", String.valueOf(roundId),
                "%result%", result,
                "%total%", String.valueOf(total),
                "%dice_1%", config.dieIcon(resultFirst),
                "%dice_2%", config.dieIcon(resultSecond),
                "%dice_3%", config.dieIcon(resultThird),
                "%dice_raw%", resultFirst + "-" + resultSecond + "-" + resultThird
        );
        Component resultTitle = component(replacements(titleTemplate, resultValues));
        Component resultSubtitle = diceComponent(replacements(
                triple ? config.resultTripleSubtitle : config.resultSubtitle, resultValues));
        String message = won
                ? config.text("messages.win", "&aBạn thắng phiên #%round%. &fThực nhận: &a%payout%$ &7(thuế %tax%$)")
                : triple ? config.text("messages.triple-loss",
                "&cTHUA &8• &7Phiên &f#%round% &8• &f%dice_raw% &7là bộ ba: nhà cái thắng,"
                        + " cả Tài và Xỉu đều thua. &7Bạn mất &f%amount%$&7.")
                : config.text("messages.lose", "&cBạn không thắng trong phiên #%round%. &7Khoản cược %amount%$ đã được xử lý.");
        message = replacements(message, resultValues);
        message = replacements(message, Map.of("%round%", String.valueOf(roundId), "%payout%", money(payout),
                "%tax%", money(tax), "%amount%", money(bet.amount()), "%side%", bet.side().displayName()));
        String finalMessage = message;
        TaiXiuConfig resultConfig = config;
        return () -> {
        Player player = Bukkit.getPlayer(bet.playerId());
        if (player != null && player.isOnline()) {
            FoliaScheduler.runForPlayer(plugin, player, () -> {
                player.showTitle(Title.title(resultTitle, resultSubtitle,
                        Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(2), Duration.ofMillis(400))));
                PluginSound.play(player, won ? resultConfig.winSound : resultConfig.loseSound,
                        resultConfig.soundVolume, won ? 1.2f : 0.85f);
                send(player, finalMessage);
            });
        } else {
            try {
                store.addNotification(bet.playerId(), color(finalMessage));
            } catch (SQLException ex) {
                plugin.getLogger().warning("Không thể lưu thông báo Tài Xỉu offline: " + ex.getMessage());
            }
        }
        };
    }

    private synchronized void cancelCurrentRound(String reason, boolean refund) {
        if (state != State.OPEN && state != State.ROLLING) return;
        if (rollingTask != null) rollingTask.cancel();
        for (PlayerBet bet : List.copyOf(bets.values())) {
            if (refund) refundBet(roundId, bet, reason);
            removeParticipantView(bet.playerId());
        }
        try {
            store.finishRound(roundId, 0, 0, 0, "", taiTotal, xiuTotal, bets.size(), "CANCELLED");
        } catch (SQLException ex) {
            plugin.getLogger().warning("Không thể lưu trạng thái hủy Tài Xỉu: " + ex.getMessage());
        }
        resetForNextRound();
    }

    private void refundBet(long affectedRound, PlayerBet bet, String reason) {
        try {
            store.markBet(affectedRound, bet.playerId(), "REFUND_PENDING", bet.amount(), 0);
            if (!shuttingDown) retryPendingPayouts(bet.playerId());
        } catch (SQLException ex) { plugin.getLogger().severe("Không thể ghi hoàn cược: " + ex.getMessage()); }
    }

    private void recoverInterruptedBets() throws SQLException { store.queueInterruptedRefunds(); }

    private void retryPendingPayouts(UUID onlyPlayer) throws SQLException {
        if (shuttingDown || !economy.isAvailable()) return;
        for (String status : List.of("PAYOUT_PENDING", "REFUND_PENDING")) {
            List<TaiXiuStore.Bet> pending = onlyPlayer == null
                    ? store.betsByStatus(status) : store.betsByStatus(onlyPlayer, status);
            for (TaiXiuStore.Bet bet : pending) {
                String message = status.equals("REFUND_PENDING")
                        ? replacements(config.text("messages.cancelled", "&eĐã hoàn %amount%$ phiên #%round%."),
                        Map.of("%round%", String.valueOf(bet.roundId()), "%amount%", money(bet.payout()), "%reason%", "REFUND"))
                        : "&aĐã trả thưởng Tài Xỉu phiên #" + bet.roundId() + ": &f" + money(bet.payout()) + "$";
                queuePayment(bet, message);
            }
        }
    }

    private void retryInterruptedRefunds(UUID playerId) throws SQLException { retryPendingPayouts(playerId); }

    private final Set<String> queuedPayments = ConcurrentHashMap.newKeySet();

    private void queuePayment(TaiXiuStore.Bet bet, String message) {
        queuePayment(bet, message, null);
    }

    private void queuePayment(TaiXiuStore.Bet bet, String message, Runnable onSuccess) {
        if (shuttingDown) return;
        String key = bet.roundId() + ":" + bet.playerId();
        if (!queuedPayments.add(key)) return;
        Player online = Bukkit.getPlayer(bet.playerId());
        Runnable pay = () -> {
            synchronized (TaiXiuService.this) {
                try {
                    if (shuttingDown || store.betsByStatus(bet.playerId(), bet.status()).stream()
                            .noneMatch(row -> row.roundId() == bet.roundId())) return;
                    OfflinePlayer account = Bukkit.getOfflinePlayer(bet.playerId());
                    if (!economy.deposit(account, bet.payout())) {
                        send(Bukkit.getPlayer(bet.playerId()), config.text("messages.payout-pending",
                                "&eThanh toán phiên #%round% đang chờ economy.").replace("%round%", String.valueOf(bet.roundId())));
                        return;
                    }
                    try {
                        store.markBet(bet.roundId(), bet.playerId(), bet.status().equals("REFUND_PENDING") ? "REFUNDED" : "WON",
                                bet.payout(), bet.tax());
                    } catch (SQLException ex) {

                        plugin.getLogger().severe("ĐÃ TRẢ TIỀN nhưng không lưu được trạng thái " + key + ": " + ex.getMessage());
                        uncertainPayments.add(key);
                        try { store.markBet(bet.roundId(), bet.playerId(), "PAYMENT_UNCERTAIN", bet.payout(), bet.tax()); }
                        catch (SQLException quarantineFailure) { plugin.getLogger().severe("Không thể cách ly thanh toán " + key); }
                        return;
                    }
                    if (onSuccess != null) onSuccess.run();
                    else {
                        Player recipient = Bukkit.getPlayer(bet.playerId());
                        if (recipient != null && recipient.isOnline()) send(recipient, message);
                        else store.addNotification(bet.playerId(), color(message));
                    }
                } catch (SQLException ex) { plugin.getLogger().severe("Lỗi thanh toán " + key + ": " + ex.getMessage()); }
                finally { queuedPayments.remove(key); }
            }
        };
        if (uncertainPayments.contains(key)) { queuedPayments.remove(key); return; }
        if (online != null && online.isOnline()) {
            var task = online.getScheduler().run(plugin, ignored -> pay.run(), () -> queuedPayments.remove(key));
            if (task == null) queuedPayments.remove(key);
        } else {
            FoliaScheduler.runNow(plugin, pay);
        }
    }

    private final Set<String> uncertainPayments = ConcurrentHashMap.newKeySet();
    private void resetForNextRound() {
        bets.clear();
        taiTotal = 0L;
        xiuTotal = 0L;
        secondsRemaining = 0;
        state = adminPaused ? State.PAUSED : State.WAITING;
        cooldownRemaining = config.cooldownSeconds;
        roundForcedOutcome = null;
    }

    private void cancelUnopenedRound() {
        try { store.finishRound(roundId, 0, 0, 0, "", 0, 0, 0, "CANCELLED"); }
        catch (SQLException ex) { plugin.getLogger().warning(ex.getMessage()); }
        resetForNextRound();
    }

    public synchronized void setNextOutcome(NextOutcome outcome, String actor) throws SQLException {
        store.setMetadata("next_outcome", outcome == null ? "" : outcome.name());
        nextOutcome = outcome;
        plugin.getLogger().info(actor + " đặt kết quả phiên tiếp theo: " + (outcome == null ? "ngẫu nhiên" : outcome));
    }

    public synchronized NextOutcome nextOutcome() { return nextOutcome; }

    public String guiText(String path, String fallback) { return config.guiText(path, fallback); }
    public int guiInt(String path, int fallback) { return config.guiInt(path, fallback); }

    private long currentMaximumBet(PlayerBet existing) {
        if (existing != null) return currentMaximumBet(existing.side(), existing);
        return Math.max(currentMaximumBet(TaiXiuMath.Side.TAI, null),
                currentMaximumBet(TaiXiuMath.Side.XIU, null));
    }

    private long currentMaximumBet(TaiXiuMath.Side side, PlayerBet existing) {
        long current = existing == null ? 0L : existing.amount();
        return Math.max(current, config.maximumBet);
    }

    private void ensureParticipantView(Player player) {
        UUID playerId = player.getUniqueId();
        ActionBarPriority.claim(playerId);
        BossBar bar = bossBars.computeIfAbsent(playerId, ignored -> BossBar.bossBar(Component.empty(), 1.0f,
                BossBar.Color.GREEN, BossBar.Overlay.PROGRESS));
        FoliaScheduler.runForPlayer(plugin, player, () -> player.showBossBar(bar));
    }

    private void updateAllParticipants() {
        for (UUID playerId : List.copyOf(bets.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) updateParticipant(player);
        }
    }

    private void updateParticipant(Player player) {
        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) return;
        int currentSeconds = Math.max(0, secondsRemaining);
        float progress = Math.max(0.0f, Math.min(1.0f, currentSeconds / (float) config.bettingSeconds));
        String boss = replacements(config.bossBar, Map.of("%time%", time(currentSeconds), "%round%", String.valueOf(roundId)));
        String action = replacements(config.actionBar, Map.of("%tai_total%", money(taiTotal), "%xiu_total%", money(xiuTotal),
                "%time%", time(currentSeconds), "%round%", String.valueOf(roundId)));
        FoliaScheduler.runForPlayer(plugin, player, () -> {
            bar.name(component(boss));
            bar.progress(progress);
            bar.color(currentSeconds <= 5 ? BossBar.Color.RED : currentSeconds <= 15 ? BossBar.Color.YELLOW : BossBar.Color.GREEN);
            player.sendActionBar(component(action));
        });
    }

    private void removeParticipantView(UUID playerId) {
        ActionBarPriority.release(playerId);
        BossBar bar = bossBars.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (bar != null && player != null && player.isOnline()) {
            FoliaScheduler.runForPlayer(plugin, player, () -> player.hideBossBar(bar));
        }
    }

    private void send(Player player, String raw) {
        if (player != null && player.isOnline()) {
            Component message = component(raw);
            FoliaScheduler.runForPlayer(plugin, player, () -> player.sendMessage(message));
        }
    }

    static Component component(String raw) {
        return LegacyComponentSerializer.legacySection().deserialize(color(raw));
    }

    Component diceComponent(String raw) {
        Component rendered = component(raw);
        try {
            return rendered.font(Key.key(config.diceFont));
        } catch (IllegalArgumentException ignored) {
            return rendered.font(Key.key("minecraft:default"));
        }
    }

    static String color(String raw) {
        return ColorUtil.colorize(raw == null ? "" : raw);
    }

    public static String money(long amount) {
        return String.format(Locale.US, "%,d", Math.max(0L, amount));
    }

    public static long parseAmount(String raw) {
        if (raw == null) return -1L;
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
        if (normalized.isBlank()) return -1L;
        long multiplier = 1L;
        char suffix = normalized.charAt(normalized.length() - 1);
        if (suffix == 'k' || suffix == 'm' || suffix == 'b') {
            multiplier = suffix == 'k' ? 1_000L : suffix == 'm' ? 1_000_000L : 1_000_000_000L;
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            var value = new java.math.BigDecimal(normalized).multiply(java.math.BigDecimal.valueOf(multiplier));
            if (value.signum() <= 0) return -1L;
            return value.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            return -1L;
        }
    }

    private static String time(int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
    }

    private static String replace(String input, String token, String value) {
        return input == null ? "" : input.replace(token, value == null ? "" : value);
    }

    static String replacements(String input, Map<String, String> values) {
        String output = input == null ? "" : input;
        for (Map.Entry<String, String> entry : values.entrySet()) output = output.replace(entry.getKey(), entry.getValue());
        return output;
    }

    public record PlayerBet(UUID playerId, String playerName, TaiXiuMath.Side side, long amount) {
    }

    public record RoundSnapshot(State state, long roundId, int secondsRemaining, long taiTotal, long xiuTotal,
                                long maximumBet, PlayerBet ownBet,
                                TaiXiuStore.RoundHistory lastResult, boolean paused) {
        public boolean open() {
            return state == State.OPEN;
        }
    }
}
