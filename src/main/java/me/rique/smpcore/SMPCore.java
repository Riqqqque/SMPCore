package me.rique.smpcore;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.backpack.BackpackListener;
import me.rique.smpcore.combat.CombatLogListener;
import me.rique.smpcore.crafting.CraftingRulesListener;
import me.rique.smpcore.command.AdminCommands;
import me.rique.smpcore.command.GamemodeCommands;
import me.rique.smpcore.command.HomeCommands;
import me.rique.smpcore.command.LegendaryCommands;
import me.rique.smpcore.command.PlayerCommands;
import me.rique.smpcore.command.SMPCoreCommand;
import me.rique.smpcore.command.SpawnerAdminCommand;
import me.rique.smpcore.command.TeamCommands;
import me.rique.smpcore.config.ConfigManager;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.death.DeathChestListener;
import me.rique.smpcore.home.HomeManager;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.item.CustomToolListener;
import me.rique.smpcore.item.ReplenishListener;
import me.rique.smpcore.item.RewardLanternListener;
import me.rique.smpcore.item.SustenanceTalismanListener;
import me.rique.smpcore.item.VeinMinerListener;
import me.rique.smpcore.legendary.LegendaryAltarManager;
import me.rique.smpcore.legendary.LegendaryListener;
import me.rique.smpcore.power.SuperpowerManager;
import me.rique.smpcore.player.DragonEggListener;
import me.rique.smpcore.player.JoinListener;
import me.rique.smpcore.player.PlayerManager;
import me.rique.smpcore.player.WorldRulesListener;
import me.rique.smpcore.spawner.SpawnerListener;
import me.rique.smpcore.spawner.SpawnerManager;
import me.rique.smpcore.team.TeamManager;
import me.rique.smpcore.waystone.WaystoneListener;
import me.rique.smpcore.waystone.WaystoneManager;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SMPCore - Paper 1.21.11 core plugin.
 * Author: Rique
 */
@SuppressWarnings("UnstableApiUsage")
public final class SMPCore extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private SpawnerManager spawnerManager;
    private HomeManager homeManager;
    private PlayerManager playerManager;
    private TeamManager teamManager;
    private WaystoneManager waystoneManager;
    private BackpackListener backpackListener;
    private CombatLogListener combatLogListener;
    private DeathChestListener deathChestListener;
    private DragonEggListener dragonEggListener;
    private AwakeningTableListener awakeningTableListener;
    private SuperpowerManager superpowerManager;
    private LegendaryListener legendaryListener;
    private LegendaryAltarManager legendaryAltarManager;
    private ReplenishListener replenishListener;
    private CustomEnchantListener customEnchantListener;
    private CustomToolListener customToolListener;
    private RewardLanternListener rewardLanternListener;
    private SustenanceTalismanListener sustenanceTalismanListener;
    private VeinMinerListener veinMinerListener;
    private CraftingRulesListener craftingRulesListener;
    private WorldRulesListener worldRulesListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);

        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.init();
        } catch (Exception e) {
            getLogger().severe("Failed to initialise database: " + e.getMessage());
            getLogger().severe("Disabling SMPCore due to database init failure.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        playerManager = new PlayerManager(this);
        spawnerManager = new SpawnerManager(this);
        homeManager = new HomeManager(this);
        teamManager = new TeamManager(this);
        waystoneManager = new WaystoneManager(this);

        spawnerManager.loadAll();
        teamManager.loadFromDatabaseBlocking();
        waystoneManager.loadAll();

        registerListeners();
        registerCommands();

        getLogger().info("SMPCore enabled! Authored by Rique.");
    }

    @Override
    public void onDisable() {
        if (dragonEggListener != null) {
            HandlerList.unregisterAll(dragonEggListener);
            dragonEggListener.cancel();
        }
        if (legendaryListener != null) legendaryListener.shutdown();
        if (legendaryAltarManager != null) legendaryAltarManager.shutdown();
        if (veinMinerListener != null) veinMinerListener.shutdown();
        if (backpackListener != null) backpackListener.shutdown();
        if (spawnerManager != null) spawnerManager.shutdown();
        if (awakeningTableListener != null) awakeningTableListener.shutdown();
        if (superpowerManager != null) superpowerManager.shutdown();
        if (rewardLanternListener != null) rewardLanternListener.shutdown();
        if (teamManager != null) teamManager.shutdown();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("SMPCore disabled.");
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new SpawnerListener(this), this);
        craftingRulesListener = new CraftingRulesListener(this);
        pm.registerEvents(craftingRulesListener, this);
        combatLogListener = new CombatLogListener(this);
        pm.registerEvents(combatLogListener, this);
        worldRulesListener = new WorldRulesListener(this);
        pm.registerEvents(worldRulesListener, this);
        worldRulesListener.applyConfiguredWorldRules();
        veinMinerListener = new VeinMinerListener(this);
        pm.registerEvents(veinMinerListener, this);
        replenishListener = new ReplenishListener(this);
        pm.registerEvents(replenishListener, this);
        backpackListener = new BackpackListener(this);
        pm.registerEvents(backpackListener, this);
        pm.registerEvents(new WaystoneListener(this), this);
        customEnchantListener = new CustomEnchantListener(this);
        pm.registerEvents(customEnchantListener, this);
        rewardLanternListener = new RewardLanternListener(this);
        pm.registerEvents(rewardLanternListener, this);
        rewardLanternListener.start();
        superpowerManager = new SuperpowerManager(this);
        pm.registerEvents(superpowerManager, this);
        superpowerManager.start();
        sustenanceTalismanListener = new SustenanceTalismanListener(this);
        pm.registerEvents(sustenanceTalismanListener, this);
        deathChestListener = new DeathChestListener(this);
        pm.registerEvents(deathChestListener, this);
        legendaryListener = new LegendaryListener(this);
        pm.registerEvents(legendaryListener, this);
        legendaryAltarManager = new LegendaryAltarManager(this);
        pm.registerEvents(legendaryAltarManager, this);
        customToolListener = new CustomToolListener(this);
        pm.registerEvents(customToolListener, this);
        awakeningTableListener = new AwakeningTableListener(this);
        pm.registerEvents(awakeningTableListener, this);
        awakeningTableListener.start();
        pm.registerEvents(teamManager, this);
        pm.registerEvents(new JoinListener(this), this);
        restartDragonEggListener();
    }

    public void restartDragonEggListener() {
        if (dragonEggListener != null) {
            HandlerList.unregisterAll(dragonEggListener);
            dragonEggListener.cancel();
        }
        dragonEggListener = new DragonEggListener(this);
        getServer().getPluginManager().registerEvents(dragonEggListener, this);
        dragonEggListener.start();
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = event.registrar();

            HomeCommands.register(commands, this);
            PlayerCommands.register(commands, this);
            TeamCommands.register(commands, this);

            AdminCommands.register(commands, this);
            GamemodeCommands.register(commands, this);
            SpawnerAdminCommand.register(commands, this);
            LegendaryCommands.register(commands, this);

            SMPCoreCommand.register(commands, this);
        });
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabase() { return databaseManager; }
    public SpawnerManager getSpawnerManager() { return spawnerManager; }
    public HomeManager getHomeManager() { return homeManager; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public WaystoneManager getWaystoneManager() { return waystoneManager; }
    public BackpackListener getBackpackListener() { return backpackListener; }
    public CombatLogListener getCombatLogListener() { return combatLogListener; }
    public AwakeningTableListener getAwakeningTableListener() { return awakeningTableListener; }
    public LegendaryListener getLegendaryListener() { return legendaryListener; }
    public LegendaryAltarManager getLegendaryAltarManager() { return legendaryAltarManager; }
    public SuperpowerManager getSuperpowerManager() { return superpowerManager; }
    public ReplenishListener getReplenishListener() { return replenishListener; }
    public CustomEnchantListener getCustomEnchantListener() { return customEnchantListener; }
    public CustomToolListener getCustomToolListener() { return customToolListener; }
    public RewardLanternListener getRewardLanternListener() { return rewardLanternListener; }
    public SustenanceTalismanListener getSustenanceTalismanListener() { return sustenanceTalismanListener; }
    public VeinMinerListener getVeinMinerListener() { return veinMinerListener; }
    public CraftingRulesListener getCraftingRulesListener() { return craftingRulesListener; }
    public WorldRulesListener getWorldRulesListener() { return worldRulesListener; }
}
