package com.ocloud.taixiu.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentViewRegistryTest {
    @Test
    void staleCallbackCannotRemoveReplacementView() {
        CurrentViewRegistry<Object> registry = new CurrentViewRegistry<>();
        UUID playerId = UUID.randomUUID();
        Object oldView = new Object();
        Object newView = new Object();

        registry.put(playerId, oldView);
        assertSame(oldView, registry.put(playerId, newView));
        assertFalse(registry.remove(playerId, oldView));
        assertTrue(registry.isCurrent(playerId, newView));
        assertTrue(registry.remove(playerId, newView));
    }
}
