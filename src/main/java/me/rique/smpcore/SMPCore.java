package me.rique.smpcore;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.rique.smpcore.audit.ItemAuditManager;
import me.rique.smpcore.audit.PluginActivityLogger;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.backpack.BackpackListener;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.boss.BossDungeonManager;
import me.rique.smpcore.boss.BossMusicManager;
import me.rique.smpcore.boss.BossTestLoadoutManager;
import me.rique.smpcore.changelog.ChangelogManager;
import me.rique.smpcore.combat.CombatLogListener;
import me.rique.smpcore.combat.DamageNumberListener;
import me.rique.smpcore.compat.CrossplayManager;
import me.rique.smpcore.compat.BedrockFamiliarVisibilityManager;
import me.rique.smpcore.compat.BedrockHologramVisibilityManager;
import me.rique.smpcore.compat.BedrockSkullManager;
import me.rique.smpcore.crafting.CraftingRulesListener;
import me.rique.smpcore.command.AdminCommands;
import me.rique.smpcore.command.BlackjackCommand;
import me.rique.smpcore.command.BedrockSkullCommand;
import me.rique.smpcore.command.BeastwardenCommand;
import me.rique.smpcore.command.BossCommands;
import me.rique.smpcore.command.BossDungeonCommand;
import me.rique.smpcore.command.BossMasteryCommand;
import me.rique.smpcore.command.BossPotionCommands;
import me.rique.smpcore.command.BossTestLoadoutCommand;
import me.rique.smpcore.command.ChangelogCommand;
import me.rique.smpcore.command.CorruptionCommand;
import me.rique.smpcore.command.DeathInventoryCommand;
import me.rique.smpcore.command.DuelCommand;
import me.rique.smpcore.command.EssenceCommand;
import me.rique.smpcore.command.FamiliarAdminCommand;
import me.rique.smpcore.command.GamemodeCommands;
import me.rique.smpcore.command.GuideNpcCommand;
import me.rique.smpcore.command.GoblinHuntCommand;
import me.rique.smpcore.command.HomeCommands;
import me.rique.smpcore.command.LeaderboardCommands;
import me.rique.smpcore.command.LaunchAccessCommand;
import me.rique.smpcore.command.LegendaryCommands;
import me.rique.smpcore.command.MainMenuCommand;
import me.rique.smpcore.command.MarketStallCommand;
import me.rique.smpcore.command.MayorPetCommand;
import me.rique.smpcore.command.PlayerCommands;
import me.rique.smpcore.command.PriestCommand;
import me.rique.smpcore.command.ReforgeCommand;
import me.rique.smpcore.command.RouletteCommand;
import me.rique.smpcore.command.SMPCoreCommand;
import me.rique.smpcore.command.ServerUtilityCommands;
import me.rique.smpcore.command.ShopCommands;
import me.rique.smpcore.command.SpinBetCommand;
import me.rique.smpcore.command.SpawnerAdminCommand;
import me.rique.smpcore.command.SpawnProtectionCommand;
import me.rique.smpcore.command.SpawnLifeCommand;
import me.rique.smpcore.command.SmpStartCommand;
import me.rique.smpcore.command.StoryCommand;
import me.rique.smpcore.command.TeamCommands;
import me.rique.smpcore.command.TavernCommand;
import me.rique.smpcore.command.WikiCommand;
import me.rique.smpcore.config.ConfigManager;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.death.DeathChestListener;
import me.rique.smpcore.death.DeathInventoryManager;
import me.rique.smpcore.duel.DuelManager;
import me.rique.smpcore.essence.EssenceManager;
import me.rique.smpcore.essence.PriestManager;
import me.rique.smpcore.game.BlackjackManager;
import me.rique.smpcore.game.RouletteManager;
import me.rique.smpcore.game.SpinBetManager;
import me.rique.smpcore.home.HomeManager;
import me.rique.smpcore.item.BossPotionListener;
import me.rique.smpcore.item.AgriculturalPylonListener;
import me.rique.smpcore.item.CorruptionManager;
import me.rique.smpcore.item.CustomEnchantListener;
import me.rique.smpcore.item.CustomToolListener;
import me.rique.smpcore.item.RareDropVisualListener;
import me.rique.smpcore.item.ReforgeManager;
import me.rique.smpcore.item.ReplenishListener;
import me.rique.smpcore.item.RewardLanternListener;
import me.rique.smpcore.item.SalvagingDepotListener;
import me.rique.smpcore.item.SustenanceTalismanListener;
import me.rique.smpcore.item.VeilOrbManager;
import me.rique.smpcore.item.VeinMinerListener;
import me.rique.smpcore.item.XpLecternListener;
import me.rique.smpcore.legendary.LegendaryAltarManager;
import me.rique.smpcore.legendary.LegendaryListener;
import me.rique.smpcore.legendary.LegendaryStorageGuardListener;
import me.rique.smpcore.legendary.MythicForgeListener;
import me.rique.smpcore.leaderboard.LeaderboardManager;
import me.rique.smpcore.launch.LaunchAccessManager;
import me.rique.smpcore.npc.GuideNpcManager;
import me.rique.smpcore.npc.NpcHologramManager;
import me.rique.smpcore.power.SuperpowerManager;
import me.rique.smpcore.player.DragonEggListener;
import me.rique.smpcore.player.ExactSpawnListener;
import me.rique.smpcore.player.JoinListener;
import me.rique.smpcore.player.PlayerControlListener;
import me.rique.smpcore.player.PlayerManager;
import me.rique.smpcore.player.PlayerSettingsManager;
import me.rique.smpcore.player.PlayerVisualListener;
import me.rique.smpcore.player.SpawnProtectionListener;
import me.rique.smpcore.player.TabListManager;
import me.rique.smpcore.player.WorldRulesListener;
import me.rique.smpcore.quest.MayorQuestManager;
import me.rique.smpcore.quest.GoblinHuntManager;
import me.rique.smpcore.quest.FarmerManager;
import me.rique.smpcore.quest.FisherManager;
import me.rique.smpcore.quest.BeastwardenManager;
import me.rique.smpcore.quest.BlackMarketManager;
import me.rique.smpcore.quest.BossMasteryManager;
import me.rique.smpcore.quest.MinerManager;
import me.rique.smpcore.quest.OverseerManager;
import me.rique.smpcore.quest.WitchManager;
import me.rique.smpcore.motd.MotdListener;
import me.rique.smpcore.season.SeasonRelicManager;
import me.rique.smpcore.shop.PlayerShopListener;
import me.rique.smpcore.shop.MarketStallManager;
import me.rique.smpcore.spawner.SpawnerListener;
import me.rique.smpcore.spawner.SpawnerManager;
import me.rique.smpcore.smp.SmpStartManager;
import me.rique.smpcore.spawn.SpawnAmbienceManager;
import me.rique.smpcore.spawn.SpawnLifeManager;
import me.rique.smpcore.story.StoryService;
import me.rique.smpcore.team.TeamManager;
import me.rique.smpcore.tavern.TavernManager;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.ItemModelMigrationListener;
import me.rique.smpcore.waystone.WaystoneListener;
import me.rique.smpcore.waystone.WaystoneManager;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SMPCore - Paper 26.2 core plugin.
 * Author: Rique
 */
@SuppressWarnings("UnstableApiUsage")
public final class SMPCore extends JavaPlugin {

    private ConfigManager configManager;
    private ChangelogManager changelogManager;
    private DatabaseManager databaseManager;
    private SpawnerManager spawnerManager;
    private HomeManager homeManager;
    private PlayerManager playerManager;
    private TeamManager teamManager;
    private PlayerVisualListener playerVisualListener;
    private TabListManager tabListManager;
    private WaystoneManager waystoneManager;
    private BackpackListener backpackListener;
    private CombatLogListener combatLogListener;
    private DamageNumberListener damageNumberListener;
    private DeathChestListener deathChestListener;
    private DeathInventoryManager deathInventoryManager;
    private DragonEggListener dragonEggListener;
    private AwakeningTableListener awakeningTableListener;
    private SuperpowerManager superpowerManager;
    private LegendaryListener legendaryListener;
    private LegendaryAltarManager legendaryAltarManager;
    private MythicForgeListener mythicForgeListener;
    private SeasonRelicManager seasonRelicManager;
    private BossManager bossManager;
    private BossDungeonManager bossDungeonManager;
    private BossMusicManager bossMusicManager;
    private BossTestLoadoutManager bossTestLoadoutManager;
    private ReplenishListener replenishListener;
    private CustomEnchantListener customEnchantListener;
    private CustomToolListener customToolListener;
    private ItemAuditManager itemAuditManager;
    private RareDropVisualListener rareDropVisualListener;
    private RewardLanternListener rewardLanternListener;
    private AgriculturalPylonListener agriculturalPylonListener;
    private SalvagingDepotListener salvagingDepotListener;
    private XpLecternListener xpLecternListener;
    private BossPotionListener bossPotionListener;
    private SustenanceTalismanListener sustenanceTalismanListener;
    private VeinMinerListener veinMinerListener;
    private CraftingRulesListener craftingRulesListener;
    private WorldRulesListener worldRulesListener;
    private SmpStartManager smpStartManager;
    private LeaderboardManager leaderboardManager;
    private PlayerSettingsManager playerSettingsManager;
    private PlayerControlListener playerControlListener;
    private PlayerShopListener playerShopListener;
    private MarketStallManager marketStallManager;
    private ExactSpawnListener exactSpawnListener;
    private SpawnAmbienceManager spawnAmbienceManager;
    private SpawnLifeManager spawnLifeManager;
    private SpawnProtectionListener spawnProtectionListener;
    private BlackjackManager blackjackManager;
    private RouletteManager rouletteManager;
    private SpinBetManager spinBetManager;
    private ReforgeManager reforgeManager;
    private CorruptionManager corruptionManager;
    private VeilOrbManager veilOrbManager;
    private EssenceManager essenceManager;
    private DuelManager duelManager;
    private PriestManager priestManager;
    private GuideNpcManager guideNpcManager;
    private MayorQuestManager mayorQuestManager;
    private NpcHologramManager npcHologramManager;
    private CrossplayManager crossplayManager;
    private BedrockFamiliarVisibilityManager bedrockFamiliarVisibilityManager;
    private BedrockHologramVisibilityManager bedrockHologramVisibilityManager;
    private BedrockSkullManager bedrockSkullManager;
    private TavernManager tavernManager;
    private GoblinHuntManager goblinHuntManager;
    private MinerManager minerManager;
    private FarmerManager farmerManager;
    private FisherManager fisherManager;
    private WitchManager witchManager;
    private OverseerManager overseerManager;
    private BeastwardenManager beastwardenManager;
    private BlackMarketManager blackMarketManager;
    private BossMasteryManager bossMasteryManager;
    private StoryService storyService;
    private LaunchAccessManager launchAccessManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        changelogManager = new ChangelogManager(this);

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

        try {
            spawnerManager.loadAllBlocking();
        } catch (Exception e) {
            getLogger().severe("Failed to load spawner data: " + e.getMessage());
            getLogger().severe("Disabling SMPCore to prevent spawner data loss.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        teamManager.loadFromDatabaseBlocking();
        waystoneManager.loadAll();

        registerListeners();
        registerCommands();

        getLogger().info("SMPCore enabled! Authored by Rique.");
    }

    @Override
    public void onDisable() {
        if (deathChestListener != null) deathChestListener.shutdown();
        if (sustenanceTalismanListener != null) sustenanceTalismanListener.shutdown();
        if (dragonEggListener != null) {
            HandlerList.unregisterAll(dragonEggListener);
            dragonEggListener.cancel();
        }
        if (legendaryListener != null) legendaryListener.shutdown();
        if (legendaryAltarManager != null) legendaryAltarManager.shutdown();
        if (mythicForgeListener != null) mythicForgeListener.shutdown();
        if (bossTestLoadoutManager != null) bossTestLoadoutManager.shutdown();
        if (seasonRelicManager != null) seasonRelicManager.shutdown();
        if (bossMusicManager != null) bossMusicManager.shutdown();
        if (bossDungeonManager != null) bossDungeonManager.shutdown();
        if (bossManager != null) bossManager.shutdown();
        if (damageNumberListener != null) damageNumberListener.shutdown();
        if (veinMinerListener != null) veinMinerListener.shutdown();
        if (customEnchantListener != null) customEnchantListener.shutdown();
        if (customToolListener != null) customToolListener.shutdown();
        if (backpackListener != null) backpackListener.shutdown();
        if (spawnerManager != null) spawnerManager.shutdown();
        if (awakeningTableListener != null) awakeningTableListener.shutdown();
        if (superpowerManager != null) superpowerManager.shutdown();
        if (rewardLanternListener != null) rewardLanternListener.shutdown();
        if (agriculturalPylonListener != null) agriculturalPylonListener.shutdown();
        if (salvagingDepotListener != null) salvagingDepotListener.shutdown();
        if (xpLecternListener != null) xpLecternListener.shutdown();
        if (bossPotionListener != null) bossPotionListener.shutdown();
        if (rareDropVisualListener != null) rareDropVisualListener.shutdown();
        if (combatLogListener != null) combatLogListener.shutdown();
        if (deathInventoryManager != null) deathInventoryManager.shutdown();
        if (itemAuditManager != null) itemAuditManager.shutdown();
        if (smpStartManager != null) smpStartManager.shutdown();
        if (leaderboardManager != null) leaderboardManager.shutdown();
        if (playerSettingsManager != null) playerSettingsManager.shutdown();
        if (playerManager != null) playerManager.shutdown();
        if (playerVisualListener != null) playerVisualListener.shutdown();
        if (spawnAmbienceManager != null) spawnAmbienceManager.shutdown();
        if (spawnProtectionListener != null) spawnProtectionListener.shutdown();
        if (marketStallManager != null) marketStallManager.shutdown();
        if (playerShopListener != null) playerShopListener.shutdown();
        if (blackjackManager != null) blackjackManager.shutdown();
        if (rouletteManager != null) rouletteManager.shutdown();
        if (spinBetManager != null) spinBetManager.shutdown();
        if (reforgeManager != null) reforgeManager.shutdown();
        if (corruptionManager != null) corruptionManager.shutdown();
        if (veilOrbManager != null) veilOrbManager.shutdown();
        if (priestManager != null) priestManager.shutdown();
        if (spawnLifeManager != null) spawnLifeManager.shutdown();
        if (guideNpcManager != null) guideNpcManager.shutdown();
        if (mayorQuestManager != null) mayorQuestManager.shutdown();
        if (npcHologramManager != null) npcHologramManager.shutdown();
        if (crossplayManager != null) crossplayManager.shutdown();
        if (bedrockHologramVisibilityManager != null) bedrockHologramVisibilityManager.shutdown();
        if (tavernManager != null) tavernManager.shutdown();
        if (goblinHuntManager != null) goblinHuntManager.shutdown();
        if (minerManager != null) minerManager.shutdown();
        if (farmerManager != null) farmerManager.shutdown();
        if (fisherManager != null) fisherManager.shutdown();
        if (witchManager != null) witchManager.shutdown();
        if (bedrockFamiliarVisibilityManager != null) bedrockFamiliarVisibilityManager.shutdown();
        if (tabListManager != null) tabListManager.shutdown();
        if (overseerManager != null) overseerManager.shutdown();
        if (beastwardenManager != null) beastwardenManager.shutdown();
        if (blackMarketManager != null) blackMarketManager.shutdown();
        if (bossMasteryManager != null) bossMasteryManager.shutdown();
        if (storyService != null) storyService.shutdown();
        if (duelManager != null) duelManager.shutdown();
        if (essenceManager != null) essenceManager.shutdown();
        if (teamManager != null) teamManager.shutdown();
        getServer().getScheduler().cancelTasks(this);
        if (databaseManager != null) databaseManager.close();
        getLogger().info("SMPCore disabled.");
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        bedrockFamiliarVisibilityManager = new BedrockFamiliarVisibilityManager(this);
        pm.registerEvents(bedrockFamiliarVisibilityManager, this);
        launchAccessManager = new LaunchAccessManager(this);
        pm.registerEvents(launchAccessManager, this);
        launchAccessManager.start();
        pm.registerEvents(new SpawnerListener(this), this);
        pm.registerEvents(new MotdListener(this), this);
        pm.registerEvents(new PluginActivityLogger(this), this);
        pm.registerEvents(new MainMenuCommand(this), this);
        playerSettingsManager = new PlayerSettingsManager(this);
        pm.registerEvents(playerSettingsManager, this);
        playerControlListener = new PlayerControlListener(this);
        pm.registerEvents(playerControlListener, this);
        craftingRulesListener = new CraftingRulesListener(this);
        pm.registerEvents(craftingRulesListener, this);
        combatLogListener = new CombatLogListener(this);
        pm.registerEvents(combatLogListener, this);
        combatLogListener.start();
        damageNumberListener = new DamageNumberListener(this);
        pm.registerEvents(damageNumberListener, this);
        damageNumberListener.start();
        worldRulesListener = new WorldRulesListener(this);
        pm.registerEvents(worldRulesListener, this);
        worldRulesListener.applyConfiguredWorldRules();
        exactSpawnListener = new ExactSpawnListener(this);
        pm.registerEvents(exactSpawnListener, this);
        exactSpawnListener.applyConfiguredSpawn();
        spawnAmbienceManager = new SpawnAmbienceManager(this);
        spawnAmbienceManager.start();
        spawnProtectionListener = new SpawnProtectionListener(this);
        pm.registerEvents(spawnProtectionListener, this);
        spawnProtectionListener.start();
        smpStartManager = new SmpStartManager(this);
        pm.registerEvents(smpStartManager, this);
        smpStartManager.applyConfiguredState();
        leaderboardManager = new LeaderboardManager(this);
        pm.registerEvents(leaderboardManager, this);
        leaderboardManager.start();
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
        deathInventoryManager = new DeathInventoryManager(this);
        pm.registerEvents(deathInventoryManager, this);
        deathChestListener = new DeathChestListener(this);
        pm.registerEvents(deathChestListener, this);
        legendaryListener = new LegendaryListener(this);
        pm.registerEvents(legendaryListener, this);
        legendaryAltarManager = new LegendaryAltarManager(this);
        pm.registerEvents(legendaryAltarManager, this);
        mythicForgeListener = new MythicForgeListener(this);
        pm.registerEvents(mythicForgeListener, this);
        mythicForgeListener.start();
        seasonRelicManager = new SeasonRelicManager(this);
        pm.registerEvents(seasonRelicManager, this);
        seasonRelicManager.start();
        veilOrbManager = new VeilOrbManager(this);
        pm.registerEvents(veilOrbManager, this);
        veilOrbManager.start();
        bossPotionListener = new BossPotionListener(this);
        pm.registerEvents(bossPotionListener, this);
        bossPotionListener.start();
        customEnchantListener.registerCraftOnlyRecipes();
        bossManager = new BossManager(this);
        pm.registerEvents(bossManager, this);
        bossManager.start();
        bossTestLoadoutManager = new BossTestLoadoutManager(this);
        pm.registerEvents(bossTestLoadoutManager, this);
        customToolListener = new CustomToolListener(this);
        pm.registerEvents(customToolListener, this);
        agriculturalPylonListener = new AgriculturalPylonListener(this);
        pm.registerEvents(agriculturalPylonListener, this);
        agriculturalPylonListener.start();
        salvagingDepotListener = new SalvagingDepotListener(this);
        pm.registerEvents(salvagingDepotListener, this);
        salvagingDepotListener.start();
        xpLecternListener = new XpLecternListener(this);
        pm.registerEvents(xpLecternListener, this);
        xpLecternListener.start();
        playerShopListener = new PlayerShopListener(this);
        pm.registerEvents(playerShopListener, this);
        playerShopListener.start();
        awakeningTableListener = new AwakeningTableListener(this);
        pm.registerEvents(awakeningTableListener, this);
        awakeningTableListener.start();
        rareDropVisualListener = new RareDropVisualListener(this);
        pm.registerEvents(rareDropVisualListener, this);
        rareDropVisualListener.start();
        itemAuditManager = new ItemAuditManager(this);
        pm.registerEvents(itemAuditManager, this);
        itemAuditManager.start();
        pm.registerEvents(teamManager, this);
        pm.registerEvents(new LegendaryStorageGuardListener(this), this);
        playerVisualListener = new PlayerVisualListener(this);
        pm.registerEvents(playerVisualListener, this);
        playerVisualListener.start();
        pm.registerEvents(new ItemModelMigrationListener(), this);
        pm.registerEvents(new JoinListener(this), this);
        blackjackManager = new BlackjackManager(this);
        pm.registerEvents(blackjackManager, this);
        blackjackManager.start();
        spinBetManager = new SpinBetManager(this);
        pm.registerEvents(spinBetManager, this);
        spinBetManager.start();
        tavernManager = new TavernManager(this);
        pm.registerEvents(tavernManager, this);
        tavernManager.start();
        goblinHuntManager = new GoblinHuntManager(this);
        pm.registerEvents(goblinHuntManager, this);
        goblinHuntManager.start();
        npcHologramManager = new NpcHologramManager(this);
        npcHologramManager.start();
        reforgeManager = new ReforgeManager(this);
        pm.registerEvents(reforgeManager, this);
        reforgeManager.start();
        corruptionManager = new CorruptionManager(this);
        pm.registerEvents(corruptionManager, this);
        corruptionManager.start();
        essenceManager = new EssenceManager(this);
        pm.registerEvents(essenceManager, this);
        essenceManager.start();
        rouletteManager = new RouletteManager(this);
        pm.registerEvents(rouletteManager, this);
        rouletteManager.start();
        marketStallManager = new MarketStallManager(this);
        pm.registerEvents(marketStallManager, this);
        marketStallManager.start();
        duelManager = new DuelManager(this);
        pm.registerEvents(duelManager, this);
        duelManager.start();
        bossMasteryManager = new BossMasteryManager(this);
        pm.registerEvents(bossMasteryManager, this);
        bossMasteryManager.start();
        blackMarketManager = new BlackMarketManager(this);
        pm.registerEvents(blackMarketManager, this);
        blackMarketManager.start();
        priestManager = new PriestManager(this);
        pm.registerEvents(priestManager, this);
        priestManager.start();
        guideNpcManager = new GuideNpcManager(this);
        pm.registerEvents(guideNpcManager, this);
        guideNpcManager.start();
        spawnLifeManager = new SpawnLifeManager(this);
        pm.registerEvents(spawnLifeManager, this);
        spawnLifeManager.start();
        bossDungeonManager = new BossDungeonManager(this);
        pm.registerEvents(bossDungeonManager, this);
        bossDungeonManager.start();
        bossMusicManager = new BossMusicManager(this);
        bossMusicManager.start();
        mayorQuestManager = new MayorQuestManager(this);
        pm.registerEvents(mayorQuestManager, this);
        mayorQuestManager.start();
        minerManager = new MinerManager(this);
        pm.registerEvents(minerManager, this);
        minerManager.start();
        farmerManager = new FarmerManager(this);
        pm.registerEvents(farmerManager, this);
        farmerManager.start();
        fisherManager = new FisherManager(this);
        pm.registerEvents(fisherManager, this);
        witchManager = new WitchManager(this);
        pm.registerEvents(witchManager, this);
        witchManager.start();
        overseerManager = new OverseerManager(this);
        pm.registerEvents(overseerManager, this);
        tabListManager = new TabListManager(this);
        pm.registerEvents(tabListManager, this);
        tabListManager.start();
        beastwardenManager = new BeastwardenManager(this);
        pm.registerEvents(beastwardenManager, this);
        beastwardenManager.start();
        storyService = new StoryService(this);
        pm.registerEvents(storyService, this);
        storyService.start();
        crossplayManager = new CrossplayManager(this);
        pm.registerEvents(crossplayManager, this);
        bedrockSkullManager = new BedrockSkullManager(this);
        pm.registerEvents(bedrockSkullManager, this);
        bedrockSkullManager.start();
        bedrockHologramVisibilityManager = new BedrockHologramVisibilityManager(this);
        pm.registerEvents(bedrockHologramVisibilityManager, this);
        bedrockHologramVisibilityManager.start();
        pm.registerEvents(new MenuDupeGuardListener(this), this);
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
            SmpStartCommand.register(commands, this);
            LaunchAccessCommand.register(commands, this);

            AdminCommands.register(commands, this);
            GamemodeCommands.register(commands, this);
            ServerUtilityCommands.register(commands, this);
            SpawnerAdminCommand.register(commands, this);
            LegendaryCommands.register(commands, this);
            BossCommands.register(commands, this);
            BossDungeonCommand.register(commands, this);
            BossMasteryCommand.register(commands, this);
            BossTestLoadoutCommand.register(commands, this);
            BossPotionCommands.register(commands, this);
            LeaderboardCommands.register(commands, this);
            ShopCommands.register(commands, this);
            MarketStallCommand.register(commands, this);
            WikiCommand.register(commands, this);
            ChangelogCommand.register(commands, this);
            MainMenuCommand.register(commands, this);
            SpawnProtectionCommand.register(commands, this);
            BlackjackCommand.register(commands, this);
            RouletteCommand.register(commands, this);
            SpinBetCommand.register(commands, this);
            ReforgeCommand.register(commands, this);
            CorruptionCommand.register(commands, this);
            DeathInventoryCommand.register(commands, this);
            DuelCommand.register(commands, this);
            EssenceCommand.register(commands, this);
            PriestCommand.register(commands, this);
            GuideNpcCommand.register(commands, this);
            SpawnLifeCommand.register(commands, this);
            TavernCommand.register(commands, this);
            GoblinHuntCommand.register(commands, this);
            MayorPetCommand.register(commands, this);
            FamiliarAdminCommand.register(commands, this);
            BeastwardenCommand.register(commands, this);
            CrossplayManager.registerCommands(commands, this);
            BedrockSkullCommand.register(commands, this);
            StoryCommand.register(commands, this);

            SMPCoreCommand.register(commands, this);
        });
    }

    public ConfigManager getConfigManager() { return configManager; }
    public ChangelogManager getChangelogManager() { return changelogManager; }
    public DatabaseManager getDatabase() { return databaseManager; }
    public SpawnerManager getSpawnerManager() { return spawnerManager; }
    public HomeManager getHomeManager() { return homeManager; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public PlayerVisualListener getPlayerVisualListener() { return playerVisualListener; }
    public TabListManager getTabListManager() { return tabListManager; }
    public WaystoneManager getWaystoneManager() { return waystoneManager; }
    public BackpackListener getBackpackListener() { return backpackListener; }
    public TavernManager getTavernManager() { return tavernManager; }
    public GoblinHuntManager getGoblinHuntManager() { return goblinHuntManager; }
    public MinerManager getMinerManager() { return minerManager; }
    public FarmerManager getFarmerManager() { return farmerManager; }
    public FisherManager getFisherManager() { return fisherManager; }
    public WitchManager getWitchManager() { return witchManager; }
    public OverseerManager getOverseerManager() { return overseerManager; }
    public BeastwardenManager getBeastwardenManager() { return beastwardenManager; }
    public BlackMarketManager getBlackMarketManager() { return blackMarketManager; }
    public BossMasteryManager getBossMasteryManager() { return bossMasteryManager; }
    public BedrockSkullManager getBedrockSkullManager() { return bedrockSkullManager; }
    public BedrockFamiliarVisibilityManager getBedrockFamiliarVisibilityManager() { return bedrockFamiliarVisibilityManager; }
    public CombatLogListener getCombatLogListener() { return combatLogListener; }
    public DeathChestListener getDeathChestListener() { return deathChestListener; }
    public DeathInventoryManager getDeathInventoryManager() { return deathInventoryManager; }
    public DamageNumberListener getDamageNumberListener() { return damageNumberListener; }
    public AwakeningTableListener getAwakeningTableListener() { return awakeningTableListener; }
    public LegendaryListener getLegendaryListener() { return legendaryListener; }
    public LegendaryAltarManager getLegendaryAltarManager() { return legendaryAltarManager; }
    public MythicForgeListener getMythicForgeListener() { return mythicForgeListener; }
    public SeasonRelicManager getSeasonRelicManager() { return seasonRelicManager; }
    public BossManager getBossManager() { return bossManager; }
    public BossDungeonManager getBossDungeonManager() { return bossDungeonManager; }
    public BossMusicManager getBossMusicManager() { return bossMusicManager; }
    public BossTestLoadoutManager getBossTestLoadoutManager() { return bossTestLoadoutManager; }
    public SuperpowerManager getSuperpowerManager() { return superpowerManager; }
    public ReplenishListener getReplenishListener() { return replenishListener; }
    public CustomEnchantListener getCustomEnchantListener() { return customEnchantListener; }
    public CustomToolListener getCustomToolListener() { return customToolListener; }
    public ItemAuditManager getItemAuditManager() { return itemAuditManager; }
    public RareDropVisualListener getRareDropVisualListener() { return rareDropVisualListener; }
    public RewardLanternListener getRewardLanternListener() { return rewardLanternListener; }
    public AgriculturalPylonListener getAgriculturalPylonListener() { return agriculturalPylonListener; }
    public SalvagingDepotListener getSalvagingDepotListener() { return salvagingDepotListener; }
    public XpLecternListener getXpLecternListener() { return xpLecternListener; }
    public BossPotionListener getBossPotionListener() { return bossPotionListener; }
    public SustenanceTalismanListener getSustenanceTalismanListener() { return sustenanceTalismanListener; }
    public VeinMinerListener getVeinMinerListener() { return veinMinerListener; }
    public CraftingRulesListener getCraftingRulesListener() { return craftingRulesListener; }
    public WorldRulesListener getWorldRulesListener() { return worldRulesListener; }
    public SmpStartManager getSmpStartManager() { return smpStartManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public PlayerSettingsManager getPlayerSettingsManager() { return playerSettingsManager; }
    public PlayerControlListener getPlayerControlListener() { return playerControlListener; }
    public PlayerShopListener getPlayerShopListener() { return playerShopListener; }
    public MarketStallManager getMarketStallManager() { return marketStallManager; }
    public ExactSpawnListener getExactSpawnListener() { return exactSpawnListener; }
    public SpawnAmbienceManager getSpawnAmbienceManager() { return spawnAmbienceManager; }
    public SpawnLifeManager getSpawnLifeManager() { return spawnLifeManager; }
    public SpawnProtectionListener getSpawnProtectionListener() { return spawnProtectionListener; }
    public BlackjackManager getBlackjackManager() { return blackjackManager; }
    public RouletteManager getRouletteManager() { return rouletteManager; }
    public SpinBetManager getSpinBetManager() { return spinBetManager; }
    public ReforgeManager getReforgeManager() { return reforgeManager; }
    public CorruptionManager getCorruptionManager() { return corruptionManager; }
    public VeilOrbManager getVeilOrbManager() { return veilOrbManager; }
    public EssenceManager getEssenceManager() { return essenceManager; }
    public DuelManager getDuelManager() { return duelManager; }
    public PriestManager getPriestManager() { return priestManager; }
    public GuideNpcManager getGuideNpcManager() { return guideNpcManager; }
    public MayorQuestManager getMayorQuestManager() { return mayorQuestManager; }
    public NpcHologramManager getNpcHologramManager() { return npcHologramManager; }
    public CrossplayManager getCrossplayManager() { return crossplayManager; }
    public StoryService getStoryService() { return storyService; }
    public LaunchAccessManager getLaunchAccessManager() { return launchAccessManager; }
}
