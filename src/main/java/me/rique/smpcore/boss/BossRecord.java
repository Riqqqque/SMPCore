package me.rique.smpcore.boss;

import java.util.UUID;

/**
 * Persisted custom boss record used for reliable despawn and restart recovery.
 */
public record BossRecord(
    UUID entityUuid,
    String bossId,
    String world,
    double x,
    double y,
    double z,
    int chunkX,
    int chunkZ,
    long spawnedAt
) {
}
