package com.ocloud.taixiu.game;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class TaiXiuConfig {
    private final YamlConfiguration yaml;
    private final YamlConfiguration bundled;
    private YamlConfiguration gui;
    private YamlConfiguration bedrock;
    final boolean announcementsEnabled;
    final String guiMode;
    final boolean bedrockForms;
    final boolean enabled;
    final int bettingSeconds;
    final int cooldownSeconds;
    final int muteInviteAfterEmptyRounds;
    final long minimumBet;
    final long maximumBet;
    final int taxPercent;
    final boolean triplesHouseWins;
    final int taiOutcomeWeight;
    final int xiuOutcomeWeight;
    final int tripleOutcomeWeight;
    final int historySize;
    final int historyLifetimeSeconds;
    final double historyMaxDistance;
    final int historyAnimationTicksPerPoint;
    final int historyCloseTicks;
    final double bettingDisplayMaxDistance;
    final double historyDisplayDistance;
    final double historyDisplayVerticalOffset;
    final double bettingDisplayDistance;
    final double bettingDisplayVerticalOffset;
    final List<Integer> bettingDisplayPercentages;
    final boolean bettingDisplayAllIn;
    final boolean bettingDisplayMaxSafe;
    final String openMessage;
    final String actionBar;
    final String bossBar;
    final String rollingActionBar;
    final String diceFont;
    final String rollingWaitingDie;
    final String rollingActiveDie;
    final String rollingLockedDie;
    final String countdownTitle;
    final String countdownSubtitle;
    final String resultTitleTai;
    final String resultTitleXiu;
    final String resultTitleTriple;
    final String resultSubtitle;
    final String resultTripleSubtitle;
    final String betting3dTitle;
    final String betting3dTotals;
    final String betting3dLimits;
    final String betting3dOwnBet;
    final String betting3dSelected;
    final String betting3dButtonTai;
    final String betting3dButtonXiu;
    final String betting3dClose;
    final String history3dTitle;
    final String history3dLegend;
    final String history3dSummary;
    private final List<String> diceIcons;
    private final String unknownDieIcon;
    final Sound inviteSound;
    final Sound countdownSound;
    final Sound diceRollSound;
    final Sound diceLockSound;
    final Sound winSound;
    final Sound loseSound;
    final Sound historyOpenSound;
    final Sound historyPointSound;
    final Sound historyCompleteSound;
    final Sound menuOpenSound;
    final Sound menuHoverSound;
    final Sound menuClickSound;
    final float soundVolume;

    private TaiXiuConfig(YamlConfiguration yaml, YamlConfiguration bundled) {
        this.yaml = yaml;
        this.bundled = bundled;
        validate(yaml);
        announcementsEnabled = yaml.getBoolean("announcements.enabled", true);
        guiMode = yaml.getString("gui.mode", "auto").toLowerCase(java.util.Locale.ROOT);
        if (!List.of("auto", "native", "inventory", "3d").contains(guiMode)) {
            throw new IllegalArgumentException("gui.mode phải là auto, native, inventory hoặc 3d");
        }
        bedrockForms = yaml.getBoolean("gui.bedrock-forms", true);
        enabled = yaml.getBoolean("enabled", true);
        bettingSeconds = Math.max(10, yaml.getInt("round.betting-seconds", 90));
        cooldownSeconds = Math.max(1, yaml.getInt("round.cooldown-seconds", 10));
        muteInviteAfterEmptyRounds = Math.max(1, yaml.getInt("round.mute-invite-after-empty-rounds", 10));
        minimumBet = Math.max(1L, yaml.getLong("bet.minimum", 100_000L));
        long configuredMaximum = yaml.getLong("bet.maximum", 0L);
        maximumBet = configuredMaximum <= 0L ? Long.MAX_VALUE / 2L : configuredMaximum;
        taxPercent = Math.max(0, Math.min(100, yaml.getInt("bet.tax-percent", 10)));
        triplesHouseWins = yaml.getBoolean("rules.triples-house-wins", true);
        int taiWeight = outcomeWeight(yaml, "rules.outcome-weights.tai", 40);
        int xiuWeight = outcomeWeight(yaml, "rules.outcome-weights.xiu", 40);
        int tripleWeight = outcomeWeight(yaml, "rules.outcome-weights.triple", 20);
        if (taiWeight + xiuWeight + tripleWeight == 0) {
            taiWeight = 40;
            xiuWeight = 40;
            tripleWeight = 20;
        }
        taiOutcomeWeight = taiWeight;
        xiuOutcomeWeight = xiuWeight;
        tripleOutcomeWeight = tripleWeight;
        historySize = Math.max(5, Math.min(30, yaml.getInt("history-display.rounds", 20)));
        historyLifetimeSeconds = Math.max(5, yaml.getInt("history-display.lifetime-seconds", 60));
        historyMaxDistance = Math.max(2.0d, yaml.getDouble("history-display.max-distance", 10.0d));
        historyAnimationTicksPerPoint = Math.max(3, yaml.getInt("history-display.ticks-per-point", 4));
        historyCloseTicks = Math.max(1, yaml.getInt("history-display.close-animation-ticks", 8));
        bettingDisplayMaxDistance = Math.max(3.0d, yaml.getDouble("betting-display.max-distance", 10.0d));
        historyDisplayDistance = Math.max(2.0d, yaml.getDouble("history-display.spawn-distance", 3.8d));
        historyDisplayVerticalOffset = yaml.getDouble("history-display.vertical-offset", 0.08d);
        bettingDisplayDistance = Math.max(2.5d, yaml.getDouble("betting-display.spawn-distance", 4.1d));
        bettingDisplayVerticalOffset = yaml.getDouble("betting-display.vertical-offset", 0.08d);
        List<Integer> percentages = yaml.getIntegerList("betting-display.smart-amounts.percentages").stream()
                .filter(percent -> percent > 0 && percent <= 100).distinct().limit(5).toList();
        bettingDisplayPercentages = percentages.isEmpty() ? List.of(10, 25, 50) : percentages;
        bettingDisplayAllIn = yaml.getBoolean("betting-display.smart-amounts.all-in", true);
        bettingDisplayMaxSafe = yaml.getBoolean("betting-display.smart-amounts.max-safe", true);
        openMessage = configured("messages.round-open", "&6Một phiên Tài Xỉu vừa mở. &fGõ &e/tx &fđể đặt cược.");
        actionBar = configured("messages.actionbar", "&cTÀI &f%tai_total%$ &8• &e%time% &8• &9XỈU &f%xiu_total%$");
        bossBar = configured("messages.bossbar", "&6TÀI XỈU &8• &fNhận cược &8• &eCòn %time%");
        diceIcons = List.of(
                configured("display.dice-icons.one", "⚀"),
                configured("display.dice-icons.two", "⚁"),
                configured("display.dice-icons.three", "⚂"),
                configured("display.dice-icons.four", "⚃"),
                configured("display.dice-icons.five", "⚄"),
                configured("display.dice-icons.six", "⚅")
        );
        unknownDieIcon = configured("display.dice-icons.unknown", "?");
        diceFont = configured("display.dice-font", "minecraft:default");
        rollingActionBar = configured("display.rolling-actionbar.format",
                "&8[ %dice_1% &8│ %dice_2% &8│ %dice_3% &8]");
        rollingWaitingDie = configured("display.rolling-actionbar.waiting", "&8?");
        rollingActiveDie = configured("display.rolling-actionbar.rolling", "&f%icon%");
        rollingLockedDie = configured("display.rolling-actionbar.locked", "&f%icon%");
        countdownTitle = configured("display.countdown-title.title", "&#FF7A7A&l%seconds%");
        countdownSubtitle = configured("display.countdown-title.subtitle", "&7Cổng cược sắp đóng");
        resultTitleTai = configured("display.result-title.tai", "&#FF5C5C&lTÀI");
        resultTitleXiu = configured("display.result-title.xiu", "&#56C7FF&lXỈU");
        resultTitleTriple = configured("display.result-title.triple", "&#C084FC&lBỘ BA");
        resultSubtitle = configured("display.result-title.subtitle",
                "&f%dice_1%  %dice_2%  %dice_3% &8• &7Tổng &f&l%total%");
        resultTripleSubtitle = configured("display.result-title.triple-subtitle",
                "&f%dice_1%  %dice_2%  %dice_3% &8• &7Nhà cái thắng, Tài và Xỉu đều thua");
        betting3dTitle = configured("display.betting-3d.title",
                "&#FF5C5C&lTÀI XỈU 3D &f• Phiên #%round% • %time%");
        betting3dTotals = configured("display.betting-3d.totals",
                "&#FF6B6B&lTÀI &f%tai_total%$ &8│ &#56C7FF&lXỈU &f%xiu_total%$");
        betting3dLimits = configured("display.betting-3d.limits",
                "&7Nhận thêm: &#FF6B6BTài %tai_max%$ &8│ &#56C7FFXỉu %xiu_max%$");
        betting3dOwnBet = configured("display.betting-3d.own-bet",
                "&#67E8A5Bạn đã cược %side% • %amount%$");
        betting3dSelected = configured("display.betting-3d.selected",
                "&7MỨC ĐANG CHỌN  &#F6C453&l%selection% &8• &8Ngắm vào cửa rồi nhấp chuột");
        betting3dButtonTai = configured("display.betting-3d.button-tai", "&#FF6B6B&lCƯỢC TÀI");
        betting3dButtonXiu = configured("display.betting-3d.button-xiu", "&#56C7FF&lCƯỢC XỈU");
        betting3dClose = configured("display.betting-3d.close", "&f&l×");
        history3dTitle = configured("display.history-3d.title", "&#F6C453&lCẦU TÀI XỈU");
        history3dLegend = configured("display.history-3d.legend",
                "&#FF5C5C■ TÀI     &#55C7FF■ XỈU     &#C084FC◆ BỘ BA");
        history3dSummary = configured("display.history-3d.summary",
                "&7PHIÊN #%round%   &f%dice_1% %dice_2% %dice_3%   &7=   %total%   %result_color%&l%result%");
        inviteSound = sound(configured("sounds.invite", "BLOCK_NOTE_BLOCK_BELL"), Sound.BLOCK_NOTE_BLOCK_BELL);
        countdownSound = sound(configured("sounds.countdown", "BLOCK_NOTE_BLOCK_HAT"), Sound.BLOCK_NOTE_BLOCK_HAT);
        diceRollSound = sound(configured("sounds.dice-roll", "BLOCK_NOTE_BLOCK_SNARE"), Sound.BLOCK_NOTE_BLOCK_SNARE);
        diceLockSound = sound(configured("sounds.dice-lock", "BLOCK_NOTE_BLOCK_PLING"), Sound.BLOCK_NOTE_BLOCK_PLING);
        winSound = sound(configured("sounds.win", "ENTITY_PLAYER_LEVELUP"), Sound.ENTITY_PLAYER_LEVELUP);
        loseSound = sound(configured("sounds.lose", "BLOCK_RESPAWN_ANCHOR_DEPLETE"), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE);
        historyOpenSound = sound(configured("sounds.history-open", "BLOCK_BEACON_ACTIVATE"), Sound.BLOCK_BEACON_ACTIVATE);
        historyPointSound = sound(configured("sounds.history-point", "BLOCK_NOTE_BLOCK_XYLOPHONE"), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE);
        historyCompleteSound = sound(configured("sounds.history-complete", "BLOCK_AMETHYST_BLOCK_CHIME"), Sound.BLOCK_AMETHYST_BLOCK_CHIME);
        menuOpenSound = sound(configured("sounds.menu-open", "BLOCK_BEACON_ACTIVATE"), Sound.BLOCK_BEACON_ACTIVATE);
        menuHoverSound = sound(configured("sounds.menu-hover", "UI_BUTTON_CLICK"), Sound.UI_BUTTON_CLICK);
        menuClickSound = sound(configured("sounds.menu-click", "BLOCK_NOTE_BLOCK_PLING"), Sound.BLOCK_NOTE_BLOCK_PLING);
        soundVolume = (float) Math.max(0.0d, Math.min(4.0d, yaml.getDouble("sounds.volume", 1.0d)));
    }

    static TaiXiuConfig load(JavaPlugin plugin) {
        YamlConfiguration yaml = loadFile(plugin, "taixiu.yml");
        TaiXiuConfig result = new TaiXiuConfig(yaml, new YamlConfiguration());
        result.gui = loadFile(plugin, "gui.yml");
        result.bedrock = loadFile(plugin, "bedrock/taixiu.yml");
        int columns = result.gui.getInt("native.columns", 2);
        int buttonWidth = result.gui.getInt("native.button-width", 150);
        int bodyWidth = result.gui.getInt("native.body-width", 360);
        int inputWidth = result.gui.getInt("native.input-width", 260);
        int inputLength = result.gui.getInt("native.input-max-length", 24);
        if (columns < 1 || columns > 4 || buttonWidth < 40 || buttonWidth > 300 || bodyWidth < 100 || bodyWidth > 800
                || inputWidth < 40 || inputWidth > 300 || inputLength < 1 || inputLength > 256) {
            throw new IllegalArgumentException("Kích thước Native GUI không hợp lệ trong gui.yml");
        }
        return result;
    }

    static YamlConfiguration loadFile(JavaPlugin plugin, String resource) {
        try {
            YamlConfiguration defaults;
            try (InputStream stream = plugin.getResource(resource)) {
                if (stream == null) throw new IllegalArgumentException("Thiếu resource " + resource);
                defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
            File file = new File(plugin.getDataFolder(), resource);
            if (!file.exists()) plugin.saveResource(resource, false);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            for (String key : defaults.getKeys(true)) {
                Object expected = defaults.get(key);
                Object actual = yaml.get(key);
                if (expected instanceof org.bukkit.configuration.ConfigurationSection) continue;
                if (actual == null) { yaml.set(key, expected); continue; }
                boolean valid = expected instanceof Number ? actual instanceof Number
                        : expected instanceof Boolean ? actual instanceof Boolean
                        : expected instanceof List ? actual instanceof List : actual instanceof String;
                if (!valid) throw new IllegalArgumentException(resource + ": sai kiểu dữ liệu tại " + key);
            }
            yaml.setDefaults(defaults);
            return yaml;
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            throw new IllegalArgumentException("Không đọc được " + resource + ": " + ex.getMessage(), ex);
        }
    }

    static void validate(YamlConfiguration yaml) {
        long minimum = integer(yaml, "bet.minimum", 100_000);
        long maximum = integer(yaml, "bet.maximum", 0);
        if (minimum < 1 || minimum > Long.MAX_VALUE / 2 || maximum < 0
                || maximum > Long.MAX_VALUE / 2 || (maximum > 0 && maximum < minimum)) {
            throw new IllegalArgumentException("bet.minimum / bet.maximum không hợp lệ (maximum: 0 = không giới hạn cấu hình)");
        }
        long tax = integer(yaml, "bet.tax-percent", 10);
        if (tax < 0 || tax > 100) {
            throw new IllegalArgumentException("bet.tax-percent phải từ 0 đến 100");
        }
        long bettingSeconds = integer(yaml, "round.betting-seconds", 90);
        long cooldownSeconds = integer(yaml, "round.cooldown-seconds", 10);
        if (bettingSeconds < 10 || bettingSeconds > Integer.MAX_VALUE || cooldownSeconds < 1 || cooldownSeconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Thời gian cược phải >= 10 giây, nghỉ phải >= 1 giây");
        }
        long sum = 0;
        for (String side : List.of("tai", "xiu", "triple")) {
            long weight = integer(yaml, "rules.outcome-weights." + side, side.equals("triple") ? 20 : 40);
            if (weight < 0 || weight > 10_000) throw new IllegalArgumentException("Trọng số phải từ 0 đến 10000");
            sum += weight;
        }
        if (sum == 0) throw new IllegalArgumentException("Ít nhất một trọng số kết quả phải > 0");
        for (String key : List.of("history-display.max-distance", "history-display.spawn-distance",
                "history-display.vertical-offset", "betting-display.max-distance", "betting-display.spawn-distance",
                "betting-display.vertical-offset", "sounds.volume")) {
            if (yaml.contains(key) && !Double.isFinite(yaml.getDouble(key))) {
                throw new IllegalArgumentException(key + " phải là số hữu hạn");
            }
        }
    }

    private static long integer(YamlConfiguration yaml, String key, long fallback) {
        Object value = yaml.get(key, fallback);
        if (!(value instanceof Number)) throw new IllegalArgumentException(key + " phải là số nguyên");
        try { return new java.math.BigDecimal(value.toString()).longValueExact(); }
        catch (NumberFormatException | ArithmeticException ex) { throw new IllegalArgumentException(key + " phải là số nguyên trong phạm vi long", ex); }
    }

    String guiText(String path, String fallback) { return gui.getString(path, fallback); }
    int guiInt(String path, int fallback) { return gui.getInt(path, fallback); }
    YamlConfiguration bedrock() { return bedrock; }

    List<Integer> reminderSeconds() {
        return List.of(60, 30, 15, 10, 5, 4, 3, 2, 1);
    }

    String text(String path, String fallback) {
        return configured(path, fallback);
    }

    String dieIcon(int value) {
        return value >= 1 && value <= diceIcons.size() ? diceIcons.get(value - 1) : unknownDieIcon;
    }

    private String configured(String path, String fallback) {
        return yaml.getString(path, fallback);
    }

    private static Sound sound(String raw, Sound fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {

            return (Sound) Sound.class.getMethod("valueOf", String.class)
                    .invoke(null, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static int outcomeWeight(YamlConfiguration yaml, String path, int fallback) {
        return Math.max(0, Math.min(10_000, yaml.getInt(path, fallback)));
    }
}
