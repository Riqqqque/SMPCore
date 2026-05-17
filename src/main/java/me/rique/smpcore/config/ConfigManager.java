package me.rique.smpcore.config;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * Typed wrapper around config.yml - reloadable via /smpcore reload.
 */
public final class ConfigManager {

    private static final String DEFAULT_JOIN_FIRST_MESSAGE = "<gold>Welcome, <white>{player}</white>!</gold>";
    private static final String LEGACY_JOIN_FIRST_MESSAGE =
        "<gold><bold>* Welcome to the server, <white>{player}</white>! *</bold></gold> "
            + "<gray>You are player number <yellow>{count}</yellow> to join!</gray>";
    private static final List<String> DEFAULT_MOTD_LINES = List.of(
        "<gradient:#00e5ff:#22c55e><bold>SMPCore</bold></gradient> <dark_gray>|</dark_gray> <white>{online}</white><gray>/<white>{max}</white>",
        "<gray>Custom powers, bosses, relics, and waystones.</gray>"
    );

    private final SMPCore plugin;

    public String joinFirst;
    public String joinReturn;
    public String quit;
    public boolean motdEnabled;
    public boolean motdLegacyColorCodes;
    public List<String> motdLines;

    public boolean smpStartEnabled;
    public String smpStartWorld;
    public boolean smpStarted;
    public long smpStartedAt;
    public double smpPreStartBorderDiameter;
    public double smpStartedBorderDiameter;
    public int smpBorderExpandSeconds;
    public int smpPostStartGraceMinutes;
    public String smpStartBroadcast;
    public String smpGraceDenyMessage;

    public int combatTagSeconds;

    public int homeDefaultMax;
    public int homeMultipleMax;

    public String spawnWorld;
    public boolean forceHardDifficulty;

    public boolean backOnDeath;
    public boolean backOnTeleport;

    public boolean deathChestEnabled;
    public boolean deathChestDisableInPlayerCombat;
    public int deathChestLifetimeMinutes;
    public int deathChestSearchRadius;
    public int deathChestVerticalSearchRadius;
    public boolean deathChestRequireSupportingBlock;
    public boolean deathChestRequireClearAbove;
    public boolean deathChestAllowWaterPlacement;
    public boolean deathChestLargeChestEnabled;
    public boolean deathChestNotifyWhenNoSpace;
    public boolean deathChestDropOverflowItems;
    public boolean deathChestRemoveWhenEmpty;
    public boolean deathChestNotifyChat;
    public boolean deathChestNoteEnabled;
    public boolean deathChestNoteDropIfInventoryFull;
    public String deathChestChestName;
    public String deathChestChatMessage;
    public String deathChestNoSpaceMessage;
    public String deathChestNoteTitle;
    public List<String> deathChestNoteLore;

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
    public int frostScytheAbilityCooldownSeconds;

    public boolean awakeningTableEnabled;
    public double awakeningTableLootChance;
    public double awakeningTableSuccessChance;
    public double awakeningTableFailureDurabilityLossFraction;
    public double awakeningTableDestroyThreshold;
    public double awakeningTableWeaponDamageMultiplier;
    public double awakeningTableAttackSpeedMultiplier;
    public double awakeningTableArmorMultiplier;
    public double awakeningTableArmorToughnessMultiplier;
    public double awakeningTableKnockbackResistanceMultiplier;
    public boolean awakeningTableAnnounceSuccess;
    public boolean awakeningTableRepairVanillaOnSuccess;
    public boolean awakeningTableHologramEnabled;
    public double awakeningTableHologramHeight;
    public double awakeningTableHologramViewRange;

    public double thorsHammerBonusDamage;

    public double wiseLevelOneBonus;
    public double wiseLevelTwoBonus;
    public double wiseLevelThreeBonus;
    public int wiseCropXp;
    public double doubleJumpAncientCityChestChance;
    public double doubleJumpVerticalBoost;
    public double doubleJumpForwardBoost;
    public int doubleJumpHungerCost;
    public int dashEnchantCooldownSeconds;
    public double dashEnchantHorizontalBoost;
    public double dashEnchantVerticalBoost;

    public boolean advancedPickaxeEnabled;
    public boolean advancedPickaxeDisableBonusWithSilkTouch;
    public double advancedPickaxeLuckyDropChance;
    public double advancedPickaxeCoalChance;
    public double advancedPickaxeIronChance;
    public double advancedPickaxeRedstoneChance;
    public double advancedPickaxeGoldChance;
    public double advancedPickaxeLapisChance;
    public double advancedPickaxeCopperChance;
    public double advancedPickaxeDiamondChance;
    public double advancedPickaxeEmeraldChance;
    public int grappleHookCooldownSeconds;
    public int grappleHookMaxUses;

    public int sustenanceTalismanIntervalSeconds;
    public int sustenanceTalismanHungerGain;
    public double sustenanceTalismanHealHearts;
    public int rewardLanternCooldownSeconds;

    public ConfigManager(SMPCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        var c = plugin.getConfig();

        MessageUtil.setPrefix(c.getString("messages.prefix", MessageUtil.defaultPrefix()));

        joinFirst = c.getString("messages.join-first", DEFAULT_JOIN_FIRST_MESSAGE);
        if (joinFirst == null || joinFirst.isBlank()) {
            joinFirst = DEFAULT_JOIN_FIRST_MESSAGE;
        } else if (LEGACY_JOIN_FIRST_MESSAGE.equals(joinFirst)) {
            joinFirst = DEFAULT_JOIN_FIRST_MESSAGE;
            c.set("messages.join-first", joinFirst);
            plugin.saveConfig();
        }
        joinReturn = c.getString("messages.join-return", "<aqua>{player} joined.</aqua>");
        quit = c.getString("messages.quit", "<gray>{player} left.</gray>");

        boolean motdConfigAdded = false;
        if (!c.contains("motd.enabled")) {
            c.set("motd.enabled", true);
            motdConfigAdded = true;
        }
        if (!c.contains("motd.legacy-color-codes")) {
            c.set("motd.legacy-color-codes", true);
            motdConfigAdded = true;
        }
        if (!c.contains("motd.lines")) {
            c.set("motd.lines", DEFAULT_MOTD_LINES);
            motdConfigAdded = true;
        }
        if (motdConfigAdded) {
            plugin.saveConfig();
        }

        motdEnabled = c.getBoolean("motd.enabled", true);
        motdLegacyColorCodes = c.getBoolean("motd.legacy-color-codes", true);
        motdLines = c.getStringList("motd.lines");
        if (motdLines.isEmpty()) {
            motdLines = DEFAULT_MOTD_LINES;
        }

        boolean smpStartConfigAdded = false;
        if (!c.contains("smp-start.enabled")) {
            c.set("smp-start.enabled", true);
            smpStartConfigAdded = true;
        }
        if (!c.contains("smp-start.world")) {
            c.set("smp-start.world", c.getString("spawn.world", "world"));
            smpStartConfigAdded = true;
        }
        if (!c.contains("smp-start.started")) {
            c.set("smp-start.started", false);
            smpStartConfigAdded = true;
        }
        if (!c.contains("smp-start.started-at")) {
            c.set("smp-start.started-at", 0L);
            smpStartConfigAdded = true;
        }
        if (!c.contains("smp-start.pre-start-border-diameter")) {
            c.set("smp-start.pre-start-border-diameter", 75.0);
            smpStartConfigAdded = true;
        }
        if (!c.contains("smp-start.started-border-diameter")) {
            c.set("smp-start.started-border-diameter", 10000.0);
            smpStartConfigAdded = true;
        }
        if (!c.contains("smp-start.border-expand-seconds")) {
            c.set("smp-start.border-expand-seconds", 0);
            smpStartConfigAdded = true;
        }
        if (!c.contains("smp-start.post-start-grace-minutes")) {
            c.set("smp-start.post-start-grace-minutes", 60);
            smpStartConfigAdded = true;
        }
        if (!c.contains("smp-start.start-broadcast")) {
            c.set("smp-start.start-broadcast", "<gold><bold>The SMP has started!</bold></gold> <gray>The world border is now <white>{border}</white> blocks wide. PvP unlocks in <white>{grace}</white>.</gray>");
            smpStartConfigAdded = true;
        }
        if (!c.contains("smp-start.grace-deny-message")) {
            c.set("smp-start.grace-deny-message", "<yellow>PvP is protected for <white>{time}</white>.</yellow>");
            smpStartConfigAdded = true;
        }
        if (smpStartConfigAdded) {
            plugin.saveConfig();
        }

        smpStartEnabled = c.getBoolean("smp-start.enabled", true);
        smpStartWorld = c.getString("smp-start.world", c.getString("spawn.world", "world"));
        if (smpStartWorld == null || smpStartWorld.isBlank()) {
            smpStartWorld = "world";
        }
        smpStarted = c.getBoolean("smp-start.started", false);
        smpStartedAt = Math.max(0L, c.getLong("smp-start.started-at", 0L));
        smpPreStartBorderDiameter = clamp(c.getDouble("smp-start.pre-start-border-diameter", 75.0), 1.0, 60_000_000.0);
        smpStartedBorderDiameter = clamp(c.getDouble("smp-start.started-border-diameter", 10000.0), 1.0, 60_000_000.0);
        smpBorderExpandSeconds = clamp(c.getInt("smp-start.border-expand-seconds", 0), 0, 86_400);
        smpPostStartGraceMinutes = clamp(c.getInt("smp-start.post-start-grace-minutes", 60), 0, 7 * 24 * 60);
        smpStartBroadcast = c.getString(
            "smp-start.start-broadcast",
            "<gold><bold>The SMP has started!</bold></gold> <gray>The world border is now <white>{border}</white> blocks wide. PvP unlocks in <white>{grace}</white>.</gray>"
        );
        smpGraceDenyMessage = c.getString(
            "smp-start.grace-deny-message",
            "<yellow>PvP is protected for <white>{time}</white>.</yellow>"
        );

        combatTagSeconds = clamp(c.getInt("combat.tag-seconds", 45), 45, 60);

        homeDefaultMax = Math.max(1, c.getInt("homes.default-max", 1));
        homeMultipleMax = Math.max(homeDefaultMax, c.getInt("homes.multiple-max", 5));

        spawnWorld = c.getString("spawn.world", "world");
        if (spawnWorld == null || spawnWorld.isBlank()) {
            spawnWorld = "world";
        }
        forceHardDifficulty = c.getBoolean("world-rules.force-hard-difficulty", true);

        backOnDeath = c.getBoolean("back.on-death", true);
        backOnTeleport = c.getBoolean("back.on-teleport", true);

        deathChestEnabled = c.getBoolean("death-chest.enabled", true);
        deathChestDisableInPlayerCombat = c.getBoolean("death-chest.disable-in-player-combat", true);
        deathChestLifetimeMinutes = clamp(c.getInt("death-chest.lifetime-minutes", 90), 1, 24 * 60);
        deathChestSearchRadius = clamp(c.getInt("death-chest.search-radius", 4), 0, 16);
        deathChestVerticalSearchRadius = clamp(c.getInt("death-chest.vertical-search-radius", 4), 0, 16);
        deathChestRequireSupportingBlock = c.getBoolean("death-chest.require-supporting-block", true);
        deathChestRequireClearAbove = c.getBoolean("death-chest.require-clear-above", true);
        deathChestAllowWaterPlacement = c.getBoolean("death-chest.allow-water-placement", false);
        deathChestLargeChestEnabled = c.getBoolean("death-chest.large-chest-enabled", true);
        deathChestNotifyWhenNoSpace = c.getBoolean("death-chest.notify-when-no-space", true);
        deathChestDropOverflowItems = c.getBoolean("death-chest.drop-overflow-items", true);
        deathChestRemoveWhenEmpty = c.getBoolean("death-chest.remove-when-empty", true);
        deathChestNotifyChat = c.getBoolean("death-chest.notify-chat", true);
        deathChestNoteEnabled = c.getBoolean("death-chest.note.enabled", true);
        deathChestNoteDropIfInventoryFull = c.getBoolean("death-chest.note.drop-if-inventory-full", true);
        deathChestChestName = c.getString("death-chest.chest-name", "<gold><bold>{player}'s Death Chest</bold></gold>");
        deathChestChatMessage = c.getString(
            "death-chest.chat-message",
            "<yellow>Your death chest is at <white>{world} {x}, {y}, {z}</white> and expires in <white>{minutes} minutes</white>.</yellow>"
        );
        deathChestNoSpaceMessage = c.getString(
            "death-chest.no-space-message",
            "<red>No space was found for a death chest, so your items dropped normally.</red>"
        );
        deathChestNoteTitle = c.getString("death-chest.note.title", "<gold><bold>Death Chest</bold></gold>");
        deathChestNoteLore = c.getStringList("death-chest.note.lore");
        if (deathChestNoteLore.isEmpty()) {
            deathChestNoteLore = List.of(
                "<gray>World: <white>{world}</white>",
                "<gray>Coords: <white>{x}, {y}, {z}</white>",
                "<gray>Expires in: <white>{minutes} minutes</white>"
            );
        }

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
        legendaryAltarExpirationHours = Math.max(1, c.getInt("legendary-altar.expiration-hours", 1));
        legendaryAltarMinDistanceFromSpawn = Math.max(0, c.getInt("legendary-altar.min-distance-from-spawn", 256));
        legendaryAltarMaxDistanceFromSpawn = Math.max(legendaryAltarMinDistanceFromSpawn + 16, c.getInt("legendary-altar.max-distance-from-spawn", 2500));
        legendaryAltarSearchAttempts = Math.max(10, c.getInt("legendary-altar.location-search-attempts", 96));
        legendaryAltarBeaconViewRange = Math.max(8, c.getInt("legendary-altar.beacon-view-range", 96));
        legendaryAltarBossBarEnabled = c.getBoolean("legendary-altar.bossbar.enabled", true);

        enderBoneDropCount = Math.max(1, c.getInt("ender-sword.ender-bone-drop-count", 8));
        enderSwordSummonCooldownSeconds = Math.max(1, c.getInt("ender-sword.summon-cooldown-seconds", 30));
        enderSwordKilledCooldownSeconds = Math.max(
            enderSwordSummonCooldownSeconds,
            c.getInt("ender-sword.killed-cooldown-seconds", 1800)
        );
        enderSwordDragonScale = clamp(c.getDouble("ender-sword.dragon.scale", 0.45), 0.2, 1.0);
        enderSwordDragonHealth = Math.max(10.0, c.getDouble("ender-sword.dragon.max-health", 80.0));
        enderSwordDragonSpeed = clamp(c.getDouble("ender-sword.dragon.horizontal-speed", 1.15), 0.2, 3.0);
        enderSwordDragonVerticalSpeed = clamp(c.getDouble("ender-sword.dragon.vertical-speed", 0.75), 0.1, 2.0);
        enderSwordDismountDespawnSeconds = Math.max(0, c.getInt("ender-sword.dragon.dismount-despawn-seconds", 10));
        frostScytheAbilityCooldownSeconds = Math.max(0, c.getInt("frost-scythe.ability-cooldown-seconds", 15));
        enderSwordRequireOpenSky = c.getBoolean("ender-sword.require-open-sky", true);

        awakeningTableEnabled = c.getBoolean("awakening-table.enabled", true);
        awakeningTableLootChance = clamp(c.getDouble("awakening-table.loot-chance", 0.05), 0.0, 1.0);
        awakeningTableSuccessChance = clamp(c.getDouble("awakening-table.success-chance", 0.05), 0.0, 1.0);
        awakeningTableFailureDurabilityLossFraction = clamp(
            c.getDouble("awakening-table.failure-durability-loss-fraction", 0.50),
            0.01,
            1.0
        );
        awakeningTableDestroyThreshold = clamp(c.getDouble("awakening-table.destroy-threshold", 0.15), 0.0, 1.0);
        awakeningTableWeaponDamageMultiplier = clamp(c.getDouble("awakening-table.weapon-damage-multiplier", 2.0), 0.0, 10.0);
        awakeningTableAttackSpeedMultiplier = clamp(c.getDouble("awakening-table.attack-speed-multiplier", 1.5), 0.1, 10.0);
        awakeningTableArmorMultiplier = clamp(c.getDouble("awakening-table.armor-multiplier", 2.0), 0.0, 10.0);
        awakeningTableArmorToughnessMultiplier = clamp(c.getDouble("awakening-table.armor-toughness-multiplier", 2.0), 0.0, 10.0);
        awakeningTableKnockbackResistanceMultiplier = clamp(
            c.getDouble("awakening-table.knockback-resistance-multiplier", 2.0),
            0.0,
            10.0
        );
        awakeningTableAnnounceSuccess = c.getBoolean("awakening-table.announce-success", true);
        awakeningTableRepairVanillaOnSuccess = c.getBoolean("awakening-table.repair-vanilla-on-success", true);
        awakeningTableHologramEnabled = c.getBoolean("awakening-table.hologram.enabled", true);
        awakeningTableHologramHeight = clamp(c.getDouble("awakening-table.hologram.height", 1.9), 0.5, 6.0);
        awakeningTableHologramViewRange = clamp(c.getDouble("awakening-table.hologram.view-range", 32.0), 1.0, 96.0);

        thorsHammerBonusDamage = clamp(c.getDouble("thors-hammer.bonus-damage", 1.0), 0.0, 1.0);

        wiseLevelOneBonus = clamp(c.getDouble("custom-enchants.wise.level-bonus.1", 0.15), 0.0, 10.0);
        wiseLevelTwoBonus = clamp(c.getDouble("custom-enchants.wise.level-bonus.2", 0.30), 0.0, 10.0);
        wiseLevelThreeBonus = clamp(c.getDouble("custom-enchants.wise.level-bonus.3", 0.40), 0.0, 10.0);
        wiseCropXp = Math.max(0, c.getInt("custom-enchants.wise.crop-xp", 2));
        doubleJumpAncientCityChestChance = clamp(c.getDouble("custom-enchants.double-jump.ancient-city-chest-chance", 0.23), 0.0, 1.0);
        doubleJumpVerticalBoost = clamp(c.getDouble("custom-enchants.double-jump.vertical-boost", 0.82), 0.1, 3.0);
        doubleJumpForwardBoost = clamp(c.getDouble("custom-enchants.double-jump.forward-boost", 0.75), 0.0, 3.0);
        doubleJumpHungerCost = clamp(c.getInt("custom-enchants.double-jump.hunger-cost", 4), 0, 20);
        dashEnchantCooldownSeconds = clamp(c.getInt("custom-enchants.dash.cooldown-seconds", 15), 0, 3600);
        dashEnchantHorizontalBoost = clamp(c.getDouble("custom-enchants.dash.horizontal-boost", 1.85), 0.1, 6.0);
        dashEnchantVerticalBoost = clamp(c.getDouble("custom-enchants.dash.vertical-boost", 0.42), 0.0, 3.0);

        advancedPickaxeEnabled = c.getBoolean("custom-tools.advanced-pickaxe.enabled", true);
        advancedPickaxeDisableBonusWithSilkTouch = c.getBoolean(
            "custom-tools.advanced-pickaxe.disable-bonus-with-silk-touch",
            true
        );
        advancedPickaxeLuckyDropChance = clamp(
            c.getDouble("custom-tools.advanced-pickaxe.lucky-drop-chance", 0.25),
            0.0,
            1.0
        );
        advancedPickaxeCoalChance = advancedPickaxeWeight(c, "coal", 1.0);
        advancedPickaxeIronChance = advancedPickaxeWeight(c, "iron", 0.5);
        advancedPickaxeRedstoneChance = advancedPickaxeWeight(c, "redstone", 0.5);
        advancedPickaxeGoldChance = advancedPickaxeWeight(c, "gold", 1.0 / 3.0);
        advancedPickaxeLapisChance = advancedPickaxeWeight(c, "lapis", 0.5);
        advancedPickaxeCopperChance = advancedPickaxeWeight(c, "copper", 1.0);
        advancedPickaxeDiamondChance = advancedPickaxeWeight(c, "diamond", 0.25);
        advancedPickaxeEmeraldChance = advancedPickaxeWeight(c, "emerald", 0.25);
        grappleHookCooldownSeconds = Math.max(0, c.getInt("custom-tools.grapple-hook.cooldown-seconds", 3));
        grappleHookMaxUses = Math.max(1, c.getInt("custom-tools.grapple-hook.max-uses", 50));

        sustenanceTalismanIntervalSeconds = Math.max(1, c.getInt("talisman-of-sustenance.interval-seconds", 7));
        sustenanceTalismanHungerGain = clamp(c.getInt("talisman-of-sustenance.hunger-gain", 1), 0, 20);
        sustenanceTalismanHealHearts = clamp(c.getDouble("talisman-of-sustenance.heal-hearts", 1.0), 0.0, 20.0);

        rewardLanternCooldownSeconds = Math.max(1, c.getInt("reward-soul-lantern.cooldown-seconds", 1800));
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

    private double advancedPickaxeWeight(org.bukkit.configuration.file.FileConfiguration config, String id, double fallback) {
        String weightPath = "custom-tools.advanced-pickaxe.bonus-weights." + id;
        if (config.contains(weightPath)) {
            return clamp(config.getDouble(weightPath, fallback), 0.0, 1.0);
        }
        return clamp(config.getDouble("custom-tools.advanced-pickaxe.bonus-chances." + id, fallback), 0.0, 1.0);
    }
}
