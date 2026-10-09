package com.ocloud.taixiu.game;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaiXiuMathTest {
    @Test
    void classifiesBoundariesAndTriples() {
        assertEquals(TaiXiuMath.Side.XIU, TaiXiuMath.result(1, 3, 6, true));
        assertEquals(TaiXiuMath.Side.TAI, TaiXiuMath.result(2, 3, 6, true));
        assertEquals(TaiXiuMath.Side.TAI, TaiXiuMath.result(5, 6, 6, true));
        assertNull(TaiXiuMath.result(1, 1, 1, true));
        assertNull(TaiXiuMath.result(6, 6, 6, true));
        assertEquals(TaiXiuMath.Side.XIU, TaiXiuMath.result(1, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> TaiXiuMath.result(0, 1, 1, true));
    }

    @Test
    void weightedRollKeepsDiceAndOutcomeConsistentAtConfiguredOdds() {
        Random random = new Random(20261002L);
        int tai = 0;
        int xiu = 0;
        int house = 0;
        for (int i = 0; i < 100_000; i++) {
            TaiXiuMath.DiceRoll roll = TaiXiuMath.rollWeighted(random, 40, 40, 20);
            assertEquals(TaiXiuMath.result(roll.first(), roll.second(), roll.third(), true), roll.side());
            if (roll.side() == TaiXiuMath.Side.TAI) tai++;
            else if (roll.side() == TaiXiuMath.Side.XIU) xiu++;
            else {
                assertTrue(TaiXiuMath.isTriple(roll.first(), roll.second(), roll.third()));
                house++;
            }
        }
        assertTrue(Math.abs(tai - 40_000) < 1_000);
        assertTrue(Math.abs(xiu - 40_000) < 1_000);
        assertTrue(Math.abs(house - 20_000) < 1_000);
    }

    @Test
    void disablingTripleHouseWinsKeepsNaturalDiceRolls() {
        Random random = new Random(42L);
        for (int i = 0; i < 1_000; i++) {
            TaiXiuMath.DiceRoll roll = TaiXiuMath.rollFair(random, false);
            assertEquals(TaiXiuMath.result(roll.first(), roll.second(), roll.third(), false), roll.side());
            assertTrue(roll.side() != null);
        }
    }

    @Test
    void paysTwoTimesStakeMinusTenPercentTax() {
        assertEquals(1_000_000L, TaiXiuMath.tax(10_000_000L, 10));
        assertEquals(19_000_000L, TaiXiuMath.payout(10_000_000L, 10));
    }

    @Test
    void calculatesRealPercentageAmountWithoutOverflow() {
        assertEquals(10_000_000L, TaiXiuMath.percentageOf(100_000_000L, 10));
        assertEquals(25_000_000L, TaiXiuMath.percentageOf(100_000_000L, 25));
        assertEquals(Long.MAX_VALUE, TaiXiuMath.percentageOf(Long.MAX_VALUE, 100));
    }

    @Test
    void emptyWalletShowsPercentageInsteadOfThreeIdenticalZeroButtons() {
        assertEquals("10%", TaiXiuBettingDisplay.percentageButtonLabel(0L, 10));
        assertEquals("25%", TaiXiuBettingDisplay.percentageButtonLabel(0L, 25));
        assertEquals("50%", TaiXiuBettingDisplay.percentageButtonLabel(0L, 50));
        assertEquals("10M$", TaiXiuBettingDisplay.percentageButtonLabel(100_000_000L, 10));
    }

}
