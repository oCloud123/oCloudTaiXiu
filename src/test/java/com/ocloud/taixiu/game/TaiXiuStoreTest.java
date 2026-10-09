package com.ocloud.taixiu.game;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaiXiuStoreTest {
    @TempDir Path directory;

    private TaiXiuStore store() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var store = new TaiXiuStore(plugin);
        store.open();
        return store;
    }

    @Test void settlesLargeBetsWithoutAnyTreasuryAndKeepsPendingPayoutAcrossRestart() throws Exception {
        UUID winner = UUID.randomUUID(), loser = UUID.randomUUID();
        long round;
        try (var store = store()) {
            round = store.createRound();
            store.addBet(round, winner, "winner", TaiXiuMath.Side.TAI, 1_000_000_000L);
            store.addBet(round, winner, "winner", TaiXiuMath.Side.TAI, 1_000_000_000L);
            store.addBet(round, loser, "loser", TaiXiuMath.Side.XIU, 1_000_000_000L);
            store.settleRound(round, new TaiXiuMath.DiceRoll(2, 4, 6, TaiXiuMath.Side.TAI), 10,
                    2_000_000_000L, 1_000_000_000L);
            assertTrue(store.allActiveBets().isEmpty());
            assertEquals(3_800_000_000L, store.betsByStatus("PAYOUT_PENDING").getFirst().payout());
            assertEquals(loser, store.betsByStatus("LOST").getFirst().playerId());
            assertEquals(12, store.history(1).getFirst().total());
        }
        try (var store = store()) {
            store.queueInterruptedRefunds();
            assertEquals(1, store.betsByStatus("PAYOUT_PENDING").size());
            assertTrue(store.betsByStatus("REFUND_PENDING").isEmpty());
            store.markBet(round, winner, "WON", 3_800_000_000L, 200_000_000L);
            assertTrue(store.betsByStatus("PAYOUT_PENDING").isEmpty());
            assertEquals(1, store.playerStats(winner).wins());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("data/taixiu.db"));
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name='tx_ledger'")) {
            assertTrue(result.next());
            assertEquals(0, result.getInt(1));
        }
    }

    @Test void recoveryQueuesRefundsDurablyWithoutRefundingSettledWinners() throws Exception {
        UUID player = UUID.randomUUID();
        try (var store = store()) {
            long round = store.createRound();
            store.addBet(round, player, "player", TaiXiuMath.Side.XIU, 500_000L);
        }
        try (var store = store()) {
            store.queueInterruptedRefunds();
            store.queueInterruptedRefunds();
            assertTrue(store.allActiveBets().isEmpty());
            assertEquals(1, store.betsByStatus("REFUND_PENDING").size());
            assertEquals(500_000L, store.betsByStatus("REFUND_PENDING").getFirst().payout());
        }
        try (var store = store()) { assertEquals(1, store.betsByStatus("REFUND_PENDING").size()); }
    }

    @Test void settlementFailureRollsBackEveryBetAndRound() throws Exception {
        try (var store = store()) {
            long round = store.createRound();
            store.addBet(round, UUID.randomUUID(), "loser", TaiXiuMath.Side.XIU, 100);
            store.addBet(round, UUID.randomUUID(), "overflow", TaiXiuMath.Side.TAI, Long.MAX_VALUE);
            assertThrows(ArithmeticException.class, () -> store.settleRound(round,
                    new TaiXiuMath.DiceRoll(4, 5, 6, TaiXiuMath.Side.TAI), 10, 0, 0));
            assertEquals(2, store.allActiveBets().size());
            assertTrue(store.betsByStatus("LOST").isEmpty());
            assertTrue(store.history(20).isEmpty());
        }
    }

    @Test void triplesLoseBothSidesAndNotificationsAreDrainedOnlyOnce() throws Exception {
        UUID player = UUID.randomUUID();
        try (var store = store()) {
            long round = store.createRound();
            store.addBet(round, player, "tai", TaiXiuMath.Side.TAI, 100);
            store.addBet(round, UUID.randomUUID(), "xiu", TaiXiuMath.Side.XIU, 100);
            store.settleRound(round, new TaiXiuMath.DiceRoll(3, 3, 3, null), 10, 100, 100);
            assertEquals(2, store.betsByStatus("LOST").size());
            assertTrue(store.history(1).getFirst().triple());
            store.addNotification(player, "test");
            assertEquals(java.util.List.of("test"), store.drainNotifications(player));
            assertTrue(store.drainNotifications(player).isEmpty());
        }
    }

    @Test void nextOutcomeSurvivesRestartAndCanBeCleared() throws Exception {
        try (var store = store()) { store.setMetadata("next_outcome", "TRIPLE"); }
        try (var store = store()) {
            assertEquals(NextOutcome.TRIPLE, NextOutcome.parse(store.metadata("next_outcome")));
            store.setMetadata("next_outcome", "");
        }
        try (var store = store()) { assertNull(NextOutcome.parse(store.metadata("next_outcome"))); }
    }
}
