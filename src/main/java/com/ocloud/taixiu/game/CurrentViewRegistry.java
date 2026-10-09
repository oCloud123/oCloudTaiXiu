package com.ocloud.taixiu.game;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class CurrentViewRegistry<T> {
    private final ConcurrentMap<UUID, T> current = new ConcurrentHashMap<>();

    T put(UUID playerId, T view) {
        return current.put(playerId, view);
    }

    T remove(UUID playerId) {
        return current.remove(playerId);
    }

    boolean remove(UUID playerId, T expected) {
        return current.remove(playerId, expected);
    }

    boolean isCurrent(UUID playerId, T expected) {
        return current.get(playerId) == expected;
    }

    T get(UUID playerId) {
        return current.get(playerId);
    }

    List<UUID> playerIds() {
        return List.copyOf(current.keySet());
    }
}
