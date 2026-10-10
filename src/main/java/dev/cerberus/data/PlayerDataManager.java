package dev.cerberus.data;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerDataManager {
    private final ConcurrentMap<UUID, PlayerData> playerData = new ConcurrentHashMap<>();

    public PlayerData getOrCreate(UUID playerId) {
        return playerData.computeIfAbsent(playerId, ignored -> new PlayerData());
    }

    public void remove(UUID playerId) {
        playerData.remove(playerId);
    }

    public Iterable<PlayerData> all() {
        return playerData.values();
    }
}
