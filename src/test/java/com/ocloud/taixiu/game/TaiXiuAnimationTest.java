package com.ocloud.taixiu.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaiXiuAnimationTest {
    @Test
    void revealsDiceOneAtATime() {
        assertEquals("§8[ §f⚃ §8│ §8? §8│ §8? §8]",
                TaiXiuService.rollDisplay(4, 0, 0, true, false, false));
        assertEquals("§8[ §f⚁ §8│ §f⚄ §8│ §8? §8]",
                TaiXiuService.rollDisplay(2, 5, 0, false, true, false));
        assertEquals("§8[ §f⚁ §8│ §f⚄ §8│ §f⚂ §8]",
                TaiXiuService.rollDisplay(2, 5, 3, false, false, true));
        assertEquals("§8[ §f⚁ §8│ §f⚄ §8│ §f⚂ §8]",
                TaiXiuService.rollDisplay(2, 5, 3, false, false, false));
    }

    @Test
    void usesRequestedUnicodeDiceIcons() {
        assertEquals("⚀", TaiXiuService.dieIcon(1));
        assertEquals("⚁", TaiXiuService.dieIcon(2));
        assertEquals("⚂", TaiXiuService.dieIcon(3));
        assertEquals("⚃", TaiXiuService.dieIcon(4));
        assertEquals("⚄", TaiXiuService.dieIcon(5));
        assertEquals("⚅", TaiXiuService.dieIcon(6));
        assertEquals("?", TaiXiuService.dieIcon(0));
        assertEquals("⚄", TaiXiuService.dieFace(5));
    }

    @Test
    void rendersTheCorrectNumberOfPhysicalPipsOnEvery3dDie() {
        for (int value = 1; value <= 6; value++) {
            int visiblePips = 0;
            for (int index = 0; index < 7; index++) {
                if (TaiXiuBettingDisplay.pipVisible(value, index)) visiblePips++;
            }
            assertEquals(value, visiblePips, "Sai số chấm cho mặt xúc xắc " + value);
        }
        for (int index = 0; index < 7; index++) {
            assertFalse(TaiXiuBettingDisplay.pipVisible(0, index));
        }
    }

    @Test
    void mutesOnlyAfterTenConsecutiveEmptyRounds() {
        assertTrue(TaiXiuService.shouldPlayInviteSound(0, 10));
        assertTrue(TaiXiuService.shouldPlayInviteSound(9, 10));
        assertFalse(TaiXiuService.shouldPlayInviteSound(10, 10));
        assertFalse(TaiXiuService.shouldPlayInviteSound(25, 10));
    }
}
