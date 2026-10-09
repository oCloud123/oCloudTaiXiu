package com.ocloud.taixiu.game;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class NextOutcomeTest {
    @Test void overridesAlwaysProduceMatchingRealDice() {
        var random = new Random(42);
        for (NextOutcome outcome : NextOutcome.values()) {
            for (int i = 0; i < 1000; i++) {
                var roll = outcome.roll(random, true);
                assertEquals(TaiXiuMath.result(roll.first(), roll.second(), roll.third(), true), roll.side());
                assertEquals(outcome == NextOutcome.TRIPLE, TaiXiuMath.isTriple(roll.first(), roll.second(), roll.third()));
                if (outcome == NextOutcome.TAI) assertEquals(TaiXiuMath.Side.TAI, roll.side());
                if (outcome == NextOutcome.XIU) assertEquals(TaiXiuMath.Side.XIU, roll.side());
            }
        }
    }

    @Test void forcedTriplesStillRespectConfiguredTripleRule() {
        assertNotNull(NextOutcome.TRIPLE.roll(new Random(1), false).side());
        assertNull(NextOutcome.TRIPLE.roll(new Random(1), true).side());
    }

    @Test void acceptsVietnameseAliasesAndRejectsInvalidOutcomes() {
        assertEquals(NextOutcome.TAI, NextOutcome.parse("TÀI"));
        assertEquals(NextOutcome.XIU, NextOutcome.parse("xỉu"));
        assertEquals(NextOutcome.TRIPLE, NextOutcome.parse("boba"));
        assertNull(NextOutcome.parse("unknown"));
        assertNull(NextOutcome.parse(""));
    }
}
