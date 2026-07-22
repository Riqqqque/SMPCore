package me.rique.smpcore.spawner;

/**
 * Mutable value object that mirrors one row in the `spawners` DB table.
 * Marked dirty whenever a field changes so the manager knows to flush it.
 */
public final class SpawnerData {

    private final String world;
    private final int x, y, z;
    private String entityType;
    private int stackCount;
    private int sugarCount;
    private boolean redstoneControlled;
    private boolean aiNerfed;
    private boolean dirty;

    public SpawnerData(String world, int x, int y, int z,
                       String entityType, int stackCount,
                       int sugarCount, boolean redstoneControlled, boolean aiNerfed) {
        this.world = world;
        this.x = x; this.y = y; this.z = z;
        this.entityType = entityType;
        this.stackCount = stackCount;
        this.sugarCount = sugarCount;
        this.redstoneControlled = redstoneControlled;
        this.aiNerfed = aiNerfed;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String world()              { return world; }
    public int x()                     { return x; }
    public int y()                     { return y; }
    public int z()                     { return z; }
    public String entityType()         { return entityType; }
    public int stackCount()            { return stackCount; }
    public int sugarCount()            { return sugarCount; }
    public boolean redstoneControlled(){ return redstoneControlled; }
    public boolean aiNerfed()          { return aiNerfed; }
    public boolean isDirty()           { return dirty; }
    public void clearDirty()           { dirty = false; }

    /**
     * Immutable-in-practice copy used by asynchronous persistence work.
     */
    public SpawnerData snapshot() {
        return new SpawnerData(
            world, x, y, z, entityType, stackCount, sugarCount, redstoneControlled, aiNerfed
        );
    }

    // ── Mutators (each marks dirty) ───────────────────────────────────────────

    public void setEntityType(String type)    { entityType = type; dirty = true; }
    public void setStackCount(int count)      { stackCount = count; dirty = true; }

    /**
     * Add sugar; returns the actual amount added (capped to max).
     */
    public int addSugar(int amount, int max) {
        int canAdd = Math.max(0, max - sugarCount);
        int added  = Math.min(amount, canAdd);
        if (added > 0) { sugarCount += added; dirty = true; }
        return added;
    }

    public void toggleRedstone() { redstoneControlled = !redstoneControlled; dirty = true; }
    public void toggleAiNerf()   { aiNerfed = !aiNerfed; dirty = true; }

    public void resetModifiers() {
        sugarCount = 0;
        redstoneControlled = false;
        aiNerfed = false;
        dirty = true;
    }

    // ── Derived ───────────────────────────────────────────────────────────────

    /**
     * Speed multiplier: 1.0 at 0 sugar, maxMultiplier at maxSugar.
     */
    public double speedMultiplier(int maxSugar, double maxMultiplier) {
        if (maxSugar <= 0 || sugarCount == 0) return 1.0;
        double ratio = Math.min(sugarCount, maxSugar) / (double) maxSugar;
        // Exponent < 1 boosts early sugar impact so upgrades feel noticeable.
        double boosted = Math.pow(ratio, 0.70);
        return 1.0 + boosted * (maxMultiplier - 1.0);
    }

    /**
     * Adjusted min/max spawn delay (ticks) given original base delays.
     */
    public int adjustedDelay(int baseDelay, int maxSugar, double maxMultiplier) {
        return (int) Math.max(1, Math.round(baseDelay / speedMultiplier(maxSugar, maxMultiplier)));
    }

    /**
     * Effective cycle-rate multiplier after the configured minimum-delay floor is applied.
     */
    public double effectiveSpeedMultiplier(
        int baseMinDelay,
        int baseMaxDelay,
        int maxSugar,
        double maxMultiplier,
        int minDelayFloor
    ) {
        int safeBaseMin = Math.max(1, baseMinDelay);
        int safeBaseMax = Math.max(safeBaseMin, baseMaxDelay);
        int safeFloor = Math.max(1, minDelayFloor);
        int effectiveMin = Math.max(safeFloor, adjustedDelay(safeBaseMin, maxSugar, maxMultiplier));
        int effectiveMax = Math.max(effectiveMin, adjustedDelay(safeBaseMax, maxSugar, maxMultiplier));
        return (safeBaseMin + safeBaseMax) / (double) (effectiveMin + effectiveMax);
    }
}
