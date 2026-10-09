package com.ocloud.taixiu.game;

import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaiXiuListenerTest {
    @Test
    void acceptsBothMouseButtonsForDisplayControls() {
        assertTrue(TaiXiuListener.isBettingDisplayClick(Action.RIGHT_CLICK_AIR));
        assertTrue(TaiXiuListener.isBettingDisplayClick(Action.RIGHT_CLICK_BLOCK));
        assertTrue(TaiXiuListener.isBettingDisplayClick(Action.LEFT_CLICK_AIR));
        assertTrue(TaiXiuListener.isBettingDisplayClick(Action.LEFT_CLICK_BLOCK));
        assertFalse(TaiXiuListener.isBettingDisplayClick(Action.PHYSICAL));
    }
}
