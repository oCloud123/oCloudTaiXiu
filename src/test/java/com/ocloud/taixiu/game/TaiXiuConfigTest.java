package com.ocloud.taixiu.game;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaiXiuConfigTest {
    @TempDir Path directory;

    static JavaPlugin plugin(Path directory) throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.getResource(anyString())).thenAnswer(invocation ->
                Files.newInputStream(Path.of("src/main/resources", invocation.getArgument(0, String.class))));
        doAnswer(invocation -> {
            String name = invocation.getArgument(0);
            Path destination = directory.resolve(name);
            Files.createDirectories(destination.getParent());
            Files.copy(Path.of("src/main/resources", name), destination);
            return null;
        }).when(plugin).saveResource(anyString(), eq(false));
        return plugin;
    }

    @Test void createsCompleteDefaultsWithoutTreasuryAndPreservesCustomText() throws Exception {
        var plugin = plugin(directory);
        var config = TaiXiuConfig.load(plugin);
        assertEquals(Long.MAX_VALUE / 2, config.maximumBet);
        assertEquals(40, config.taiOutcomeWeight);
        assertEquals("auto", config.guiMode);
        Path settings = directory.resolve("taixiu.yml");
        Files.writeString(settings, "style-version: 0\nmessages:\n  round-open: 'Custom'\n");
        Files.writeString(directory.resolve("gui.yml"), "title: 'Custom menu'\n");
        config = TaiXiuConfig.load(plugin);
        assertEquals("Custom", config.openMessage);
        assertEquals("Custom menu", config.guiText("title", "fallback"));
        assertEquals("CƯỢC TÀI", config.guiText("buttons.tai", "fallback"));
        assertFalse(Files.readString(settings).contains("house:"));
        assertEquals("style-version: 0\nmessages:\n  round-open: 'Custom'\n", Files.readString(settings));
    }

    @Test void invalidReloadKeepsTheRunningConfig() throws Exception {
        var plugin = plugin(directory);
        var service = new TaiXiuService(plugin);
        var config = TaiXiuConfig.load(plugin);
        var field = TaiXiuService.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(service, config);
        Files.writeString(directory.resolve("taixiu.yml"), "bet: [broken\n");
        assertThrows(IllegalArgumentException.class, service::reload);
        assertSame(config, service.config());
        Files.writeString(directory.resolve("taixiu.yml"), "bet:\n  tax-percent: 101\n");
        assertThrows(IllegalArgumentException.class, service::reload);
        assertSame(config, service.config());
    }

    @Test void rejectsInvalidTypesWidthsAndPayoutOverflow() throws Exception {
        var plugin = plugin(directory);
        TaiXiuConfig.load(plugin);
        Files.writeString(directory.resolve("taixiu.yml"), "enabled: 'false'\n");
        assertThrows(IllegalArgumentException.class, () -> TaiXiuConfig.load(plugin));
        Files.writeString(directory.resolve("taixiu.yml"), "bet:\n  maximum: " + Long.MAX_VALUE + "\n");
        assertThrows(IllegalArgumentException.class, () -> TaiXiuConfig.load(plugin));
        Files.writeString(directory.resolve("taixiu.yml"), "{}\n");
        Files.writeString(directory.resolve("gui.yml"), "native:\n  columns: 0\n");
        assertThrows(IllegalArgumentException.class, () -> TaiXiuConfig.load(plugin));
    }

    @Test void moneyParsingDoesNotRoundLargeAmountsThroughFloatingPoint() {
        assertEquals(9_007_199_254_740_993L, TaiXiuService.parseAmount("9007199254740993"));
        assertEquals(1_500_000L, TaiXiuService.parseAmount("1.5m"));
        assertEquals(100_000L, TaiXiuService.parseAmount("100k"));
        assertEquals(-1, TaiXiuService.parseAmount("9223372036854775808"));
        assertEquals(-1, TaiXiuService.parseAmount("NaN"));
        assertEquals(-1, TaiXiuService.parseAmount("-1m"));
    }

    @Test void rejectsFractionalAndOverflowingRuleValuesInsteadOfWrappingThem() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.set("rules.outcome-weights.tai", 4_294_967_336L);
        assertThrows(IllegalArgumentException.class, () -> TaiXiuConfig.validate(yaml));
        yaml.set("rules.outcome-weights.tai", 0.5);
        assertThrows(IllegalArgumentException.class, () -> TaiXiuConfig.validate(yaml));
        yaml.set("rules.outcome-weights.tai", 40);
        yaml.set("bet.minimum", 0.5);
        assertThrows(IllegalArgumentException.class, () -> TaiXiuConfig.validate(yaml));
    }
}
