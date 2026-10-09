package com.ocloud.taixiu.game;

import com.ocloud.taixiu.economy.EconomyBridge;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaiXiuBettingTest {
    @TempDir Path directory;

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }

    private Player player() {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("player");
        when(player.isOnline()).thenReturn(true);
        var scheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(scheduler);
        when(scheduler.run(any(), any(), any())).thenAnswer(invocation -> {
            var task = mock(ScheduledTask.class);
            invocation.getArgument(1, Consumer.class).accept(task);
            return task;
        });
        return player;
    }

    @Test void acceptsAllInWithoutHouseFundsAndRejectsSwitchingSidesOrNegativeAmounts() throws Exception {
        var plugin = TaiXiuConfigTest.plugin(directory);
        var service = new TaiXiuService(plugin);
        var config = TaiXiuConfig.load(plugin);
        var economy = mock(EconomyBridge.class);
        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(any())).thenReturn(1_000_000_000d);
        when(economy.has(any(), anyDouble())).thenReturn(true);
        when(economy.withdraw(any(), anyDouble())).thenReturn(true);
        set(service, "config", config); set(service, "economy", economy);
        var storeField = TaiXiuService.class.getDeclaredField("store"); storeField.setAccessible(true);
        try (var store = (TaiXiuStore) storeField.get(service)) {
            store.open(); set(service, "roundId", store.createRound());
            set(service, "state", TaiXiuService.State.OPEN); set(service, "secondsRemaining", 90); set(service, "roundTaxPercent", 10);
            var player = player();
            assertEquals(1_000_000_000, service.resolveBetAmount(player, TaiXiuMath.Side.TAI, "all-in"));
            service.placeBet(player, TaiXiuMath.Side.TAI, "all-in");
            assertEquals(1_000_000_000, service.snapshot(player.getUniqueId()).ownBet().amount());
            service.placeBet(player, TaiXiuMath.Side.XIU, 100_000);
            service.placeBet(player, TaiXiuMath.Side.TAI, -1);
            service.placeBetForRound(player, TaiXiuMath.Side.TAI, 100_000, service.snapshot(null).roundId() - 1);
            verify(economy, times(1)).withdraw(any(), anyDouble());
            assertEquals(TaiXiuMath.Side.TAI, service.snapshot(player.getUniqueId()).ownBet().side());
        }
    }

    @Test void betsFromConcurrentRegionsKeepCorrectTotalsAndOneRecordedStakePerPlayer() throws Exception {
        var plugin = TaiXiuConfigTest.plugin(directory);
        var service = new TaiXiuService(plugin);
        set(service, "config", TaiXiuConfig.load(plugin));
        var economy = mock(EconomyBridge.class);
        when(economy.isAvailable()).thenReturn(true);
        when(economy.has(any(), anyDouble())).thenReturn(true);
        when(economy.withdraw(any(), anyDouble())).thenReturn(true);
        set(service, "economy", economy);
        var storeField = TaiXiuService.class.getDeclaredField("store"); storeField.setAccessible(true);
        try (var store = (TaiXiuStore) storeField.get(service); var executor = Executors.newFixedThreadPool(4)) {
            store.open(); long round = store.createRound(); set(service, "roundId", round);
            set(service, "state", TaiXiuService.State.OPEN); set(service, "secondsRemaining", 90); set(service, "roundTaxPercent", 10);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 20; i++) {
                var player = player();
                futures.add(executor.submit(() -> { for (int j = 0; j < 10; j++) service.placeBet(player, TaiXiuMath.Side.TAI, 100_000); }));
            }
            for (var future : futures) future.get();
            assertEquals(20_000_000, service.snapshot(null).taiTotal());
            assertEquals(20, store.activeBets(round).size());
            assertTrue(store.activeBets(round).stream().allMatch(bet -> bet.amount() == 1_000_000));
            verify(economy, times(200)).withdraw(any(), anyDouble());
        }
    }
}
