package me.rique.smpcore.config;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Material;

import java.util.Locale;

/**
 * Typed wrapper around config.yml - reloadable via /smpcore reload.
 */
public final class ConfigManager {

    private final SMPCore plugin;

    public String joinFirst;
    public String joinReturn;
    public String quit;

    public int tpaTimeout;
    public int tpaCooldown;
    public int tpaTeleportDelay;
    public boolean tpaMoveCancel;
    public int combatTagSeconds;

    public int homeDefaultMax;
    public int homeMultipleMax;

    public String spawnWorld;
    public boolean forceHardDifficulty;

    public boolean backOnDeath;
    public boolean backOnTeleport;

    public boolean spawnerSilkTouchEnabled;
    public int spawnerMaxStack;
    public float spawnerHologramViewRange;
    public int spawnerMaxSugar;
    public double spawnerMaxMultiplier;
    public boolean spawnerAiNerfEnabled;
    public boolean spawnerRedstoneDisables;
    public int spawnerMinDelayFloor;
    public int spawnerStackSpawnCountCap;
    public int spawnerMaxNearbyEntitiesCap;

    public boolean veinMinerEnabled;
    public boolean veinMinerDefaultEnabled;
    public boolean veinMinerRequireSneak;
    public boolean veinMinerSearchDiagonals;
    public int veinMinerMaxBlocksPerChain;
    public boolean veinMinerOresEnabled;
    public boolean veinMinerOresRequirePickaxe;
    public boolean veinMinerTreesEnabled;
    public boolean veinMinerTreesRequireAxe;

    public int dragonEggSpeedAmplifier;
    public int dragonEggCheckInterval;
    public Material goldenAppleSurroundMaterial;
    public boolean blockNetheriteArmorUpgrade;

    public boolean legendaryAltarEnabled;
    public String legendaryAltarWorld;
    public int legendaryAltarRollTimeTicks;
    public double legendaryAltarNightlyChance;
    public boolean legendaryAltarRequirePlayerOnline;
    public int legendaryAltarActivationSeconds;
    public int legendaryAltarExpirationHours;
    public int legendaryAltarMinDistanceFromSpawn;
    public int legendaryAltarMaxDistanceFromSpawn;
    public int legendaryAltarSearchAttempts;
    public int legendaryAltarBeaconViewRange;
    public boolean legendaryAltarBossBarEnabled;

    public int enderBoneDropCount;
    public int enderSwordSummonCooldownSeconds;
    public int enderSwordKilledCooldownSeconds;
    public double enderSwordDragonScale;
    public double enderSwordDragonHealth;
    public double enderSwordDragonSpeed;
    public double enderSwordDragonVerticalSpeed;
    public int enderSwordDismountDespawnSeconds;
    public boolean enderSwordRequireOpenSky;

    public double thorsHammerBonusDamage;

    public boolean amethystPickaxeEnabled;
    public int amethystPickaxeStripWidth;

    public boolean advancedPickaxeEnabled;
    public boolean advancedPickaxeDisableBonusWithSilkTouch;
    public double advancedPickaxeCoalChance;
    public double advancedPickaxeIronChance;
    public double advancedPickaxeRedstoneChance;
    public double advancedPickaxeGoldChance;
    public double advancedPickaxeLapisChance;
    public double advancedPickaxeCopperChance;
    public double advancedPickaxeDiamondChance;
    public double advancedPickaxeEmeraldChance;

    public ConfigManager(SMPCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        var c = plugin.getConfig();

        MessageUtil.setPrefix(c.getString("messages.prefix", MessageUtil.defaultPrefix()));

        joinFirst = c.getString("messages.join-first", "<gold>Welcome, <white>{player}</white>!</gold>");
        joinReturn = c.getString("messages.join-return", "<aqua>{player} joined.</aqua>");
        quit = c.getString("messages.quit", "<gray>{player} left.</gray>");

        tpaTimeout = Math.max(1, c.getInt("tpa.timeout", 120));
        tpaCooldown = Math.max(0, c.getInt("tpa.cooldown", 30));
        tpaTeleportDelay = Math.max(0, c.getInt("tpa.teleport-delay", 3));
        tpaMoveCancel = c.getBoolean("tpa.move-cancels", true);
        combatTagSeconds = Math.max(1, c.getInt("combat.tag-seconds", 15));

        homeDefaultMax = Math.max(1, c.getInt("homes.default-max", 1));
        homeMultipleMax = Math.max(homeDefaultMax, c.getInt("homes.multiple-max", 5));

        spawnWorld = c.getString("spawn.world", "world");
        if (spawnWorld == null || spawnWorld.isBlank()) {
            spawnWorld = "world";
        }
        forceHardDifficulty = c.getBoolean("world-rules.force-hard-difficulty", true);

        backOnDeath = c.getBoolean("back.on-death", true);
        backOnTeleport = c.getBoolean("back.on-teleport", true);

        spawnerSilkTouchEnabled = c.getBoolean("spawner.silk-touch-enabled", true);
        spawnerMaxStack = Math.max(1, c.getInt("spawner.max-stack", 64));
        spawnerHologramViewRange = (float) Math.max(0.05, c.getDouble("spawner.hologram-view-range", 0.3));
        spawnerMaxSugar = Math.max(1, c.getInt("spawner.speed.max-sugar", 32));
        spawnerMaxMultiplier = clamp(c.getDouble("spawner.speed.max-multiplier", 16.0), 1.0, 16.0);
        spawnerAiNerfEnabled = c.getBoolean("spawner.ai-nerf.enabled", true);
        spawnerRedstoneDisables = c.getBoolean("spawner.redstone.powered-disables", true);
        spawnerMinDelayFloor = Math.max(1, c.getInt("spawner.performance.min-delay-floor", 40));
        spawnerStackSpawnCountCap = Math.max(4, c.getInt("spawner.performance.stack-spawn-count-cap", 32));
        spawnerMaxNearbyEntitiesCap = Math.max(16, c.getInt("spawner.performance.max-nearby-entities-cap", 96));

        veinMinerEnabled = c.getBoolean("vein-miner.enabled", true);
        veinMinerDefaultEnabled = c.getBoolean("vein-miner.default-player-enabled", true);
        veinMinerRequireSneak = c.getBoolean("vein-miner.require-sneak", true);
        veinMinerSearchDiagonals = c.getBoolean("vein-miner.search-diagonals", true);
        veinMinerMaxBlocksPerChain = Math.max(2, c.getInt("vein-miner.max-blocks-per-chain", 96));
        veinMinerOresEnabled = c.getBoolean("vein-miner.ores.enabled", true);
        veinMinerOresRequirePickaxe = c.getBoolean("vein-miner.ores.require-pickaxe", true);
        veinMinerTreesEnabled = c.getBoolean("vein-miner.trees.enabled", true);
        veinMinerTreesRequireAxe = c.getBoolean("vein-miner.trees.require-axe", true);

        dragonEggSpeedAmplifier = Math.max(0, c.getInt("dragon-egg.speed-amplifier", 1));
        dragonEggCheckInterval = Math.max(1, c.getInt("dragon-egg.check-interval", 10));
        goldenAppleSurroundMaterial = material(c.getString("crafting.golden-apple-surround-material"), Material.IRON_NUGGET);
        blockNetheriteArmorUpgrade = c.getBoolean("crafting.block-netherite-armor-upgrade", true);

        legendaryAltarEnabled = c.getBoolean("legendary-altar.enabled", true);
        legendaryAltarWorld = c.getString("legendary-altar.world", spawnWorld);
        if (legendaryAltarWorld == null || legendaryAltarWorld.isBlank()) {
            legendaryAltarWorld = spawnWorld;
        }
        legendaryAltarRollTimeTicks = clamp(c.getInt("legendary-altar.roll-time-ticks", 13000), 12000, 14000);
        legendaryAltarNightlyChance = clamp(c.getDouble("legendary-altar.nightly-chance", 0.05), 0.0, 1.0);
        legendaryAltarRequirePlayerOnline = c.getBoolean("legendary-altar.require-player-online", true);
        legendaryAltarActivationSeconds = Math.max(10, c.getInt("legendary-altar.activation-delay-seconds", 360));
        legendaryAltarExpirationHours = Math.max(1, c.getInt("legendary-altar.expiration-hours", 48));
        legendaryAltarMinDistanceFromSpawn = Math.max(0, c.getInt("legendary-altar.min-distance-from-spawn", 256));
        legendaryAltarMaxDistanceFromSpawn = Math.max(legendaryAltarMinDistanceFromSpawn + 16, c.getInt("legendary-altar.max-distance-from-spawn", 2500));
        legendaryAltarSearchAttempts = Math.max(10, c.getInt("legendary-altar.location-search-attempts", 96));
        legendaryAltarBeaconViewRange = Math.max(8, c.getInt("legendary-altar.beacon-view-range", 96));
        legendaryAltarBossBarEnabled = c.getBoolean("legendary-altar.bossbar.enabled", true);

        enderBoneDropCount = Math.max(1, c.getInt("ender-sword.ender-bone-drop-count", 8));
        enderSwordSummonCooldownSeconds = Math.max(1, c.getInt("ender-sword.summon-cooldown-seconds", 300));
        enderSwordKilledCooldownSeconds = Math.max(
            enderSwordSummonCooldownSeconds,
            c.getInt("ender-sword.killed-cooldown-seconds", 1800)
        );
        enderSwordDragonScale = clamp(c.getDouble("ender-sword.dragon.scale", 0.45), 0.2, 1.0);
        enderSwordDragonHealth = Math.max(10.0, c.getDouble("ender-sword.dragon.max-health", 80.0));
        enderSwordDragonSpeed = clamp(c.getDouble("ender-sword.dragon.horizontal-speed", 1.15), 0.2, 3.0);
        enderSwordDragonVerticalSpeed = clamp(c.getDouble("ender-sword.dragon.vertical-speed", 0.75), 0.1, 2.0);
        enderSwordDismountDespawnSeconds = Math.max(0, c.getInt("ender-sword.dragon.dismount-despawn-seconds", 10));
        enderSwordRequireOpenSky = c.getBoolean("ender-sword.require-open-sky", true);

        thorsHammerBonusDamage = clamp(c.getDouble("thors-hammer.bonus-damage", 1.0), 0.0, 1.0);

        amethystPickaxeEnabled = c.getBoolean("custom-tools.amethyst-pickaxe.enabled", true);
        amethystPickaxeStripWidth = clamp(c.getInt("custom-tools.amethyst-pickaxe.strip-width", 3), 1, 5);

        advancedPickaxeEnabled = c.getBoolean("custom-tools.advanced-pickaxe.enabled", true);
        advancedPickaxeDisableBonusWithSilkTouch = c.getBoolean(
            "custom-tools.advanced-pickaxe.disable-bonus-with-silk-touch",
            true
        );
        advancedPickaxeCoalChance = clamp(c.getDouble("custom-tools.advanced-pickaxe.bonus-chances.coal", 1.0), 0.0, 1.0);
        advancedPickaxeIronChance = clamp(c.getDouble("custom-tools.advanced-pickaxe.bonus-chances.iron", 2.0 / 3.0), 0.0, 1.0);
        advancedPickaxeRedstoneChance = clamp(c.getDouble("custom-tools.advanced-pickaxe.bonus-chances.redstone", 0.5), 0.0, 1.0);
        advancedPickaxeGoldChance = clamp(c.getDouble("custom-tools.advanced-pickaxe.bonus-chances.gold", 1.0 / 3.0), 0.0, 1.0);
        advancedPickaxeLapisChance = clamp(c.getDouble("custom-tools.advanced-pickaxe.bonus-chances.lapis", 0.5), 0.0, 1.0);
        advancedPickaxeCopperChance = clamp(c.getDouble("custom-tools.advanced-pickaxe.bonus-chances.copper", 1.0), 0.0, 1.0);
        advancedPickaxeDiamondChance = clamp(c.getDouble("custom-tools.advanced-pickaxe.bonus-chances.diamond", 0.1), 0.0, 1.0);
        advancedPickaxeEmeraldChance = clamp(c.getDouble("custom-tools.advanced-pickaxe.bonus-chances.emerald", 0.1), 0.0, 1.0);
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

    private Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
