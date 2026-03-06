package me.rique.smpcore.config;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;

/**
 * Typed wrapper around config.yml — reloadable via /smpcore reload.
 */
public final class ConfigManager {

    private final SMPCore plugin;

    // ── Join / quit messages ──────────────────────────────────────────────────
    public String joinFirst;
    public String joinReturn;
    public String quit;

    // ── TPA ──────────────────────────────────────────────────────────────────
    public int tpaTimeout;
    public int tpaCooldown;
    public int tpaTeleportDelay;
    public boolean tpaMoveCancel;
    public int combatTagSeconds;

    // ── Homes ─────────────────────────────────────────────────────────────────
    public int homeDefaultMax;
    public int homeMultipleMax;

    // ── Spawn ─────────────────────────────────────────────────────────────────
    public String spawnWorld;

    // ── Back ─────────────────────────────────────────────────────────────────
    public boolean backOnDeath;
    public boolean backOnTeleport;

    // ── Spawner ───────────────────────────────────────────────────────────────
    public boolean spawnerSilkTouchEnabled;
    public int spawnerMaxStack;
    public float spawnerHologramViewRange;
    public int spawnerMaxSugar;
    public double spawnerMaxMultiplier;
    public boolean spawnerAiNerfEnabled;
    public boolean spawnerRedstoneDisables;

    // ── Dragon egg ────────────────────────────────────────────────────────────
    public int dragonEggSpeedAmplifier;
    public int dragonEggCheckInterval;
    public boolean blockNetheriteArmorUpgrade;

    public ConfigManager(SMPCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        var c = plugin.getConfig();

        MessageUtil.setPrefix(c.getString("messages.prefix", MessageUtil.defaultPrefix()));

        joinFirst  = c.getString("messages.join-first",  "<gold>Welcome, <white>{player}</white>!</gold>");
        joinReturn = c.getString("messages.join-return", "<aqua>{player} joined.</aqua>");
        quit       = c.getString("messages.quit",        "<gray>{player} left.</gray>");

        tpaTimeout       = Math.max(1, c.getInt("tpa.timeout", 120));
        tpaCooldown      = Math.max(0, c.getInt("tpa.cooldown", 30));
        tpaTeleportDelay = Math.max(0, c.getInt("tpa.teleport-delay", 3));
        tpaMoveCancel    = c.getBoolean("tpa.move-cancels", true);
        combatTagSeconds = Math.max(1, c.getInt("combat.tag-seconds", 15));

        homeDefaultMax  = Math.max(1, c.getInt("homes.default-max", 1));
        homeMultipleMax = Math.max(homeDefaultMax, c.getInt("homes.multiple-max", 5));

        spawnWorld = c.getString("spawn.world", "world");
        if (spawnWorld == null || spawnWorld.isBlank()) {
            spawnWorld = "world";
        }

        backOnDeath     = c.getBoolean("back.on-death", true);
        backOnTeleport  = c.getBoolean("back.on-teleport", true);

        spawnerSilkTouchEnabled  = c.getBoolean("spawner.silk-touch-enabled", true);
        spawnerMaxStack          = Math.max(1, c.getInt("spawner.max-stack", 64));
        spawnerHologramViewRange = (float) Math.max(0.05, c.getDouble("spawner.hologram-view-range", 0.3));
        spawnerMaxSugar          = Math.max(1, c.getInt("spawner.speed.max-sugar", 32));
        spawnerMaxMultiplier     = Math.min(16.0, Math.max(1.0, c.getDouble("spawner.speed.max-multiplier", 16.0)));
        spawnerAiNerfEnabled     = c.getBoolean("spawner.ai-nerf.enabled", true);
        spawnerRedstoneDisables  = c.getBoolean("spawner.redstone.powered-disables", true);

        dragonEggSpeedAmplifier  = Math.max(0, c.getInt("dragon-egg.speed-amplifier", 1));
        dragonEggCheckInterval   = Math.max(1, c.getInt("dragon-egg.check-interval", 10));
        blockNetheriteArmorUpgrade = c.getBoolean("crafting.block-netherite-armor-upgrade", true);
    }

    public void setBlockNetheriteArmorUpgrade(boolean value) {
        blockNetheriteArmorUpgrade = value;
        plugin.getConfig().set("crafting.block-netherite-armor-upgrade", value);
        plugin.saveConfig();
    }

    public boolean toggleBlockNetheriteArmorUpgrade() {
        boolean next = !blockNetheriteArmorUpgrade;
        setBlockNetheriteArmorUpgrade(next);
        return next;
    }
}
