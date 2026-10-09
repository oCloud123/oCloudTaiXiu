package com.ocloud.taixiu.game;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionBarPriority {
    private static final Set<UUID> CLAIMED = ConcurrentHashMap.newKeySet();

    private ActionBarPriority() {
    }

    public static boolean isClaimed(UUID playerId) {
        return playerId != null && CLAIMED.contains(playerId);
    }

    static void claim(UUID playerId) {
        if (playerId != null) CLAIMED.add(playerId);
    }

    static void release(UUID playerId) {
        if (playerId != null) CLAIMED.remove(playerId);
    }

    static void clear() {
        CLAIMED.clear();
    }
}
