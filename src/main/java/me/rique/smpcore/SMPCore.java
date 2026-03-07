package me.rique.smpcore;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
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
import me.rique.smpcore.command.TpaCommands;
import me.rique.smpcore.config.ConfigManager;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.home.HomeManager;
import me.rique.smpcore.item.MaceLimitListener;
import me.rique.smpcore.legendary.LegendaryListener;
import me.rique.smpcore.player.DragonEggListener;
import me.rique.smpcore.player.JoinListener;
import me.rique.smpcore.player.PlayerManager;
import me.rique.smpcore.spawner.SpawnerListener;
import me.rique.smpcore.spawner.SpawnerManager;
import me.rique.smpcore.tpa.TPAManager;
import me.rique.smpcore.team.TeamManager;
import me.rique.smpcore.waystone.WaystoneListener;
import me.rique.smpcore.waystone.WaystoneManager;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutionException;

/**
 * SMPCore - Paper 1.21.11 core plugin.
 * Author: Rique
 */
@SuppressWarnings("UnstableApiUsage")
public final class SMPCore extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private SpawnerManager spawnerManager;
    private TPAManager tpaManager;
    private HomeManager homeManager;
    private PlayerManager playerManager;
    private TeamManager teamManager;
    private WaystoneManager waystoneManager;
    private BackpackListener backpackListener;
    private DragonEggListener dragonEggListener;
    private LegendaryListener legendaryListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);

        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.initAsync().get();
        } catch (Exception e) {
            Throwable root = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            getLogger().severe("Failed to initialise database: " + root.getMessage());
            getLogger().severe("Disabling SMPCore due to database init failure.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        playerManager = new PlayerManager(this);
        spawnerManager = new SpawnerManager(this);
        tpaManager = new TPAManager(this);
        homeManager = new HomeManager(this);
        teamManager = new TeamManager(this);
        waystoneManager = new WaystoneManager(this);

        spawnerManager.loadAll();
        teamManager.loadFromDatabase();
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
        if (backpackListener != null) backpackListener.shutdown();
        if (spawnerManager != null) spawnerManager.shutdown();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("SMPCore disabled.");
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new SpawnerListener(this), this);
        pm.registerEvents(new CraftingRulesListener(this), this);
        pm.registerEvents(new CombatLogListener(this), this);
        pm.registerEvents(new MaceLimitListener(this), this);
        backpackListener = new BackpackListener(this);
        pm.registerEvents(backpackListener, this);
        pm.registerEvents(new WaystoneListener(this), this);
        legendaryListener = new LegendaryListener(this);
        pm.registerEvents(legendaryListener, this);
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

            TpaCommands.register(commands, this);
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
    public TPAManager getTpaManager() { return tpaManager; }
    public HomeManager getHomeManager() { return homeManager; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public WaystoneManager getWaystoneManager() { return waystoneManager; }
    public BackpackListener getBackpackListener() { return backpackListener; }
    public LegendaryListener getLegendaryListener() { return legendaryListener; }
}
