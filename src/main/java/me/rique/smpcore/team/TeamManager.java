package me.rique.smpcore.team;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.command.MainMenuCommand;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple persistent team system backed by SQLite + scoreboard prefixes.
 */
public final class TeamManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long INVITE_DURATION_MS = 120_000L;
    private static final long ALLY_INVITE_DURATION_MS = 300_000L;
    private static final int TEAM_VAULT_SIZE = 54;
    private static final int TEAM_BROWSER_SIZE = 54;
    private static final String SCOREBOARD_TEAM_PREFIX = "smpct_";
    private static final String SCOREBOARD_NO_TEAM_ID = SCOREBOARD_TEAM_PREFIX + "none";
    private static final String TEAMS_LOADING_MESSAGE = "Teams are still loading. Try again in a moment.";
    private static final int CROWN_MIN_TEAM_MEMBERS = 3;
    private static final int TEAM_BROWSER_BACK_SLOT = 45;
    private static final int TEAM_BROWSER_VAULT_SLOT = 49;
    private static final int TEAM_BROWSER_REFRESH_SLOT = 53;
    private static final int[] TEAM_BROWSER_TEAM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final TeamStats EMPTY_TEAM_STATS = new TeamStats(0L, 0L, 0L, 0L, 0L, 0L, 0L);

    private final SMPCore plugin;
    private final NamespacedKey keyTeamCrown;
    private final NamespacedKey keyTeamCrownOwner;
    private final NamespacedKey keyTeamCrownTeam;
    private final NamespacedKey keyTeamCrownEquipmentModel;
    private final Enchantment enchantProtection;
    private final Enchantment enchantAquaAffinity;
    private final Enchantment enchantRespiration;
    private final Map<String, TeamData> teamsByKey = new ConcurrentHashMap<>();
    private final Map<UUID, String> teamByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, InviteData> invitesByTarget = new ConcurrentHashMap<>();
    private final Map<String, AllyInviteData> allyInvitesByTargetTeam = new ConcurrentHashMap<>();
    private final Map<String, String> scoreboardIdByTeamKey = new ConcurrentHashMap<>();
    private final Map<String, TeamVaultSession> teamVaultsByKey = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<TeamVaultSession>> vaultLoadingByTeamKey = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> vaultSaveChainsByTeamKey = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> vaultAutosaveTasksByTeamKey = new ConcurrentHashMap<>();
    private final Set<UUID> pendingCrownReturns = ConcurrentHashMap.newKeySet();
    private final Set<String> renamingTeamKeys = ConcurrentHashMap.newKeySet();
    private volatile boolean teamsLoaded;
    private volatile boolean teamsLoading;

    public TeamManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyTeamCrown = new NamespacedKey(plugin, "team_crown");
        this.keyTeamCrownOwner = new NamespacedKey(plugin, "team_crown_owner");
        this.keyTeamCrownTeam = new NamespacedKey(plugin, "team_crown_team");
        this.keyTeamCrownEquipmentModel = new NamespacedKey(plugin, "team_leader_crown");
        this.enchantProtection = requireEnchantment("protection");
        this.enchantAquaAffinity = requireEnchantment("aqua_affinity");
        this.enchantRespiration = requireEnchantment("respiration");
    }

    public void loadFromDatabaseBlocking() {
        if (teamsLoading) return;

        teamsLoading = true;
        teamsLoaded = false;
        try {
            finishTeamLoad(plugin.getDatabase().loadTeams().join(), null);
        } catch (CompletionException ex) {
            finishTeamLoad(List.of(), ex);
        }
    }

    public void loadFromDatabase() {
        if (teamsLoading) return;

        teamsLoading = true;
        teamsLoaded = false;
        plugin.getDatabase().loadTeams().whenComplete((rows, ex) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> finishTeamLoad(rows, ex));
        });
    }

    private void finishTeamLoad(List<DatabaseManager.TeamRecord> rows, Throwable throwable) {
        teamsLoading = false;
        if (throwable != null) {
            Throwable root = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
            plugin.getLogger().severe("Failed to load teams: " + root.getMessage());
            return;
        }

        teamsByKey.clear();
        teamByPlayer.clear();
        invitesByTarget.clear();
        allyInvitesByTargetTeam.clear();
        scoreboardIdByTeamKey.clear();

        for (DatabaseManager.TeamRecord row : rows) {
            String displayName = row.name();
            String key = key(displayName);
            if (key.isEmpty()) continue;

            TeamColor color = TeamColor.fromId(row.color());
            TeamData data = new TeamData(key, displayName, row.ownerUuid(), color);
            data.members.addAll(row.members());
            for (String allyName : row.allies()) {
                String allyKey = key(allyName);
                if (!allyKey.isEmpty() && !allyKey.equals(key)) {
                    data.allies.add(allyKey);
                }
            }
            if (!data.members.contains(row.ownerUuid())) {
                data.members.add(row.ownerUuid());
                plugin.getDatabase().addTeamMember(displayName, row.ownerUuid())
                    .thenAccept(added -> {
                        if (!added) {
                            plugin.getLogger().severe("Could not repair owner membership for team " + displayName + ".");
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().severe("Failed to repair owner membership for team " + displayName + ": " + ex.getMessage());
                        return null;
                    });
            }

            teamsByKey.put(key, data);
            for (UUID member : data.members) {
                teamByPlayer.put(member, key);
            }
            getOrCreateScoreboardTeam(data);
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            applyTeamTag(online);
            reconcileCrowns(online);
        }

        teamsLoaded = true;
        if (plugin.getLegendaryAltarManager() != null) {
            resyncAllTeamVaultLegendaryClaims();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyTeamTag(event.getPlayer());
        reconcileCrowns(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrownDrop(PlayerDropItemEvent event) {
        if (!isTeamCrown(event.getItemDrop().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn("Team crowns cannot be dropped."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCrownDeath(PlayerDeathEvent event) {
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isDuelParticipant(event.getPlayer())) return;
        if (event.getKeepInventory()) {
            return;
        }

        boolean removed = false;
        List<ItemStack> drops = event.getDrops();
        for (int i = drops.size() - 1; i >= 0; i--) {
            if (!isTeamCrown(drops.get(i))) {
                continue;
            }
            drops.remove(i);
            removed = true;
        }

        if (removed) {
            pendingCrownReturns.add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onCrownRespawn(PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!pendingCrownReturns.remove(playerId)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                reconcileCrowns(event.getPlayer());
            }
        });
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        flushOpenTeamVault(event.getPlayer(), "kick");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        invitesByTarget.remove(event.getPlayer().getUniqueId());
        flushOpenTeamVault(event.getPlayer(), "quit");
    }

    private void flushOpenTeamVault(Player player, String reason) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof TeamVaultHolder holder)) {
            return;
        }

        TeamVaultSession session = teamVaultsByKey.get(holder.teamKey());
        if (session == null) {
            return;
        }

        auditTeamVault(player, session, reason);
        cancelVaultAutosave(session.teamKey());
        syncTeamVaultLegendaryClaims(session);
        syncPlayerLegendaryClaims(player);
        saveTeamVault(session).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to save team vault for " + session.displayName() + " on " + reason + ": " + ex.getMessage());
            return null;
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TeamVaultHolder holder)) return;

        TeamVaultSession session = teamVaultsByKey.get(holder.teamKey());
        if (session == null || session.inventory() != event.getInventory()) return;

        if (event.getPlayer() instanceof Player player) {
            auditTeamVault(player, session, "close");
        }
        cancelVaultAutosave(session.teamKey());
        syncTeamVaultLegendaryClaims(session);
        if (event.getPlayer() instanceof Player player) {
            syncPlayerLegendaryClaims(player);
        }
        saveTeamVault(session).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to save team vault for " + session.displayName() + ": " + ex.getMessage());
            return null;
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeamVaultClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TeamVaultHolder holder)) {
            return;
        }
        if (isUnsafeTeamVaultClick(event)) {
            event.setCancelled(true);
            return;
        }
        scheduleTeamVaultSyncAndSave(holder.teamKey(), "click");
    }

    private boolean isUnsafeTeamVaultClick(InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        String actionName = action == null ? "" : action.name();
        return action == InventoryAction.CLONE_STACK
            || action == InventoryAction.COLLECT_TO_CURSOR
            || "HOTBAR_MOVE_AND_READD".equals(actionName)
            || action == InventoryAction.HOTBAR_SWAP
            || action == InventoryAction.UNKNOWN
            || event.getClick() == ClickType.CREATIVE
            || event.getClick() == ClickType.DOUBLE_CLICK
            || event.getClick() == ClickType.MIDDLE
            || event.getClick() == ClickType.NUMBER_KEY
            || event.getClick() == ClickType.SWAP_OFFHAND
            || event.getClick() == ClickType.UNKNOWN;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrownInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack hotbar = null;
        if (event.getHotbarButton() >= 0) {
            hotbar = player.getInventory().getItem(event.getHotbarButton());
        }

        if (!isTeamCrown(event.getCurrentItem())
            && !isTeamCrown(event.getCursor())
            && !isTeamCrown(hotbar)) {
            return;
        }

        if (event.getRawSlot() < 0
            || event.getAction() == InventoryAction.DROP_ALL_CURSOR
            || event.getAction() == InventoryAction.DROP_ONE_CURSOR
            || event.getAction() == InventoryAction.DROP_ALL_SLOT
            || event.getAction() == InventoryAction.DROP_ONE_SLOT
            || event.getClick() == ClickType.DROP
            || event.getClick() == ClickType.CONTROL_DROP) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("Team crowns cannot be dropped."));
            return;
        }

        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
        if (clickedTop || event.isShiftClick() || isTeamCrown(event.getCursor()) || isTeamCrown(hotbar)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.warn("Team crowns cannot be stored in containers."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeamVaultDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TeamVaultHolder holder)) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
        scheduleTeamVaultSyncAndSave(holder.teamKey(), "drag");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrownInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            return;
        }
        if (!isTeamCrown(event.getOldCursor())) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                player.sendMessage(MessageUtil.warn("Team crowns cannot be stored in containers."));
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInspectorClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TeamVaultInspectorHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInspectorDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TeamVaultInspectorHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeamBrowserClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof TeamBrowserHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> handleTeamBrowserClick(player, holder, slot));
    }

    private void handleTeamBrowserClick(Player player, TeamBrowserHolder holder, int slot) {
        if (!player.isOnline()) {
            return;
        }
        if (slot == TEAM_BROWSER_BACK_SLOT) {
            if (holder.fromMainMenu()) {
                MainMenuCommand.openMenu(plugin, player);
            } else {
                player.closeInventory();
            }
            return;
        }
        if (slot == TEAM_BROWSER_VAULT_SLOT) {
            openTeamVault(player);
            return;
        }
        if (slot == TEAM_BROWSER_REFRESH_SLOT) {
            openTeamsMenu(player, holder.search(), holder.fromMainMenu());
            return;
        }

        String teamKey = holder.teamBySlot(slot);
        if (teamKey == null) {
            return;
        }
        TeamData team = teamsByKey.get(teamKey);
        if (team == null) {
            player.sendMessage(MessageUtil.warn("That team no longer exists."));
            openTeamsMenu(player, holder.search(), holder.fromMainMenu());
            return;
        }
        player.sendMessage(teamSummaryMessage(team));
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.45f, 1.25f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeamBrowserDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof TeamBrowserHolder) {
            event.setCancelled(true);
        }
    }

    public void shutdown() {
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (TeamVaultSession session : teamVaultsByKey.values()) {
            cancelVaultAutosave(session.teamKey());
            syncTeamVaultLegendaryClaims(session);
            saves.add(saveTeamVault(session).exceptionally(ex -> {
                plugin.getLogger().severe("Failed to save team vault for " + session.displayName() + ": " + ex.getMessage());
                return null;
            }));
        }

        try {
            CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException ex) {
            plugin.getLogger().severe("Failed to flush team vaults during shutdown: " + ex.getMessage());
        }

        vaultLoadingByTeamKey.clear();
        vaultSaveChainsByTeamKey.clear();
        vaultAutosaveTasksByTeamKey.values().forEach(BukkitTask::cancel);
        vaultAutosaveTasksByTeamKey.clear();
        teamVaultsByKey.clear();
        unregisterAllScoreboardTeams();
    }

    public CompletableFuture<String> createTeam(Player creator, String rawName, String rawColor) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return CompletableFuture.completedFuture(unavailable);

        String displayName = normalizeDisplayName(rawName);
        String validationError = validateTeamName(displayName);
        if (validationError != null) return CompletableFuture.completedFuture(validationError);
        TeamColor color = TeamColor.fromId(rawColor);
        if (rawColor != null && !rawColor.isBlank() && color == null) {
            return CompletableFuture.completedFuture("Unknown team color. Use /team colors.");
        }
        if (color == null) {
            color = TeamColor.GOLD;
        }

        UUID creatorId = creator.getUniqueId();
        if (teamByPlayer.containsKey(creatorId)) {
            return CompletableFuture.completedFuture("Leave your current team first.");
        }

        String teamKey = key(displayName);
        if (teamsByKey.containsKey(teamKey)) {
            return CompletableFuture.completedFuture("That team already exists.");
        }

        TeamColor finalColor = color;
        return plugin.getDatabase().createTeam(displayName, creatorId, finalColor.id)
            .handle((result, ex) -> {
                if (ex != null) {
                    plugin.getLogger().severe("createTeam failed: " + ex.getMessage());
                    return null;
                }
                return result;
            })
            .thenCompose(result -> {
                if (result == null || result == DatabaseManager.TeamCreateResult.CONFLICT) {
                    return CompletableFuture.completedFuture("Could not create team right now.");
                }
                if (result == DatabaseManager.TeamCreateResult.NAME_EXISTS) {
                    return CompletableFuture.completedFuture("That team already exists.");
                }
                if (result == DatabaseManager.TeamCreateResult.PLAYER_ALREADY_MEMBER) {
                    return CompletableFuture.completedFuture("Leave your current team first.");
                }
                return runOnMainThread(() -> {
                    if (teamsByKey.containsKey(teamKey) || teamByPlayer.containsKey(creatorId)) {
                        plugin.getDatabase().deleteTeam(displayName);
                        return "Could not create team right now.";
                    }

                    TeamData data = new TeamData(teamKey, displayName, creatorId, finalColor);
                    data.members.add(creatorId);
                    teamsByKey.put(teamKey, data);
                    teamByPlayer.put(creatorId, teamKey);
                    Player online = Bukkit.getPlayer(creatorId);
                    if (online != null && online.isOnline()) {
                        applyTeamTag(online);
                        reconcileCrowns(online);
                    }
                    return (String) null;
                });
            });
    }

    public CompletableFuture<String> changeTeamColor(Player owner, String rawColor) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return CompletableFuture.completedFuture(unavailable);

        TeamData team = teamOf(owner.getUniqueId());
        if (team == null) return CompletableFuture.completedFuture("You are not in a team.");
        if (!team.owner.equals(owner.getUniqueId())) {
            return CompletableFuture.completedFuture("Only the team owner can change the team color.");
        }
        if (renamingTeamKeys.contains(team.key)) {
            return CompletableFuture.completedFuture("Team settings are already updating. Try again in a moment.");
        }

        TeamColor color = TeamColor.fromId(rawColor);
        if (color == null) {
            return CompletableFuture.completedFuture("Unknown team color. Use /team colors.");
        }
        if (team.color == color) {
            return CompletableFuture.completedFuture("Your team already uses that color.");
        }

        String teamKey = team.key;
        UUID ownerId = owner.getUniqueId();
        TeamColor finalColor = color;
        return plugin.getDatabase().setTeamColor(team.displayName, finalColor.id)
            .handle((ignored, ex) -> {
                if (ex == null) return null;
                plugin.getLogger().severe("setTeamColor failed: " + ex.getMessage());
                return "Could not change team color right now.";
            })
            .thenCompose(error -> runOnMainThread(() -> {
                if (error != null) return error;
                TeamData current = teamsByKey.get(teamKey);
                if (current == null) return "That team no longer exists.";
                if (!current.owner.equals(ownerId)) return "Only the team owner can change the team color.";

                current.color = finalColor;
                refreshTeamVisuals(current);
                return (String) null;
            }));
    }

    public CompletableFuture<String> renameTeam(Player owner, String rawName) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return CompletableFuture.completedFuture(unavailable);

        TeamData team = teamOf(owner.getUniqueId());
        if (team == null) return CompletableFuture.completedFuture("You are not in a team.");
        if (!team.owner.equals(owner.getUniqueId())) {
            return CompletableFuture.completedFuture("Only the team owner can rename the team.");
        }

        String displayName = normalizeDisplayName(rawName);
        String validationError = validateTeamName(displayName);
        if (validationError != null) return CompletableFuture.completedFuture(validationError);

        String oldName = team.displayName;
        String oldKey = team.key;
        String newKey = key(displayName);
        if (oldName.equals(displayName)) {
            return CompletableFuture.completedFuture("Your team already uses that name.");
        }
        if (!oldKey.equals(newKey) && teamsByKey.containsKey(newKey)) {
            return CompletableFuture.completedFuture("That team already exists.");
        }
        if (!renamingTeamKeys.add(oldKey)) {
            return CompletableFuture.completedFuture("Team settings are already updating. Try again in a moment.");
        }

        UUID ownerId = owner.getUniqueId();
        return saveAndDiscardVaultForRename(team)
            .thenCompose(ignored -> plugin.getDatabase().renameTeam(oldName, displayName)
                .handle((renamed, ex) -> {
                    if (ex != null) {
                        plugin.getLogger().severe("renameTeam failed: " + ex.getMessage());
                        return "Could not rename team right now.";
                    }
                    if (!renamed) {
                        return "That team already exists.";
                    }
                    return null;
                }))
            .exceptionally(ex -> {
                plugin.getLogger().severe("renameTeam failed: " + ex.getMessage());
                return "Could not rename team right now.";
            })
            .thenCompose(error -> runOnMainThread(() -> {
                renamingTeamKeys.remove(oldKey);
                if (error != null) return error;

                TeamData current = teamsByKey.get(oldKey);
                if (current == null) return "That team no longer exists.";
                if (!current.owner.equals(ownerId)) return "Only the team owner can rename the team.";
                if (!oldKey.equals(newKey) && teamsByKey.containsKey(newKey)) {
                    return "That team already exists.";
                }

                teamsByKey.remove(oldKey);
                unregisterScoreboardTeam(oldKey);
                current.key = newKey;
                current.displayName = displayName;
                teamsByKey.put(newKey, current);
                for (UUID member : current.members) {
                    teamByPlayer.put(member, newKey);
                }
                invitesByTarget.replaceAll((targetId, invite) -> invite.teamKey().equals(oldKey)
                    ? new InviteData(newKey, invite.inviter(), invite.expiresAt())
                    : invite);
                for (TeamData other : teamsByKey.values()) {
                    if (other.allies.remove(oldKey)) {
                        other.allies.add(newKey);
                    }
                }
                current.allies.remove(oldKey);
                allyInvitesByTargetTeam.entrySet().removeIf(entry ->
                    entry.getKey().equals(oldKey) || entry.getValue().teamKey().equals(oldKey));
                refreshTeamVisuals(current);
                loadTeamVault(current).thenAccept(this::syncTeamVaultLegendaryClaims)
                    .exceptionally(ex -> {
                        plugin.getLogger().severe("Failed to sync legendary claims after renaming team " + current.displayName + ": " + ex.getMessage());
                        return null;
                    });
                return (String) null;
            }));
    }

    public String invite(Player inviter, Player target) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return unavailable;

        TeamData team = teamOf(inviter.getUniqueId());
        if (team == null) return "You are not in a team.";
        if (renamingTeamKeys.contains(team.key)) return "Team settings are updating. Try again in a moment.";
        if (!team.owner.equals(inviter.getUniqueId())) {
            return "Only the team owner can invite players.";
        }
        if (target.getUniqueId().equals(inviter.getUniqueId())) {
            return "You cannot invite yourself.";
        }
        if (teamByPlayer.containsKey(target.getUniqueId())) {
            return "<white>" + target.getName() + "</white> is already in a team.";
        }

        invitesByTarget.put(target.getUniqueId(), new InviteData(team.key, inviter.getUniqueId(), System.currentTimeMillis() + INVITE_DURATION_MS));
        target.sendMessage(MessageUtil.info(
            "<white>" + inviter.getName() + "</white> invited you to <white>" + team.displayName +
                "</white>. Use <white>/team accept</white> or <white>/team deny</white>."));
        return null;
    }

    public CompletableFuture<String> acceptInvite(Player target) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return CompletableFuture.completedFuture(unavailable);

        UUID targetId = target.getUniqueId();
        if (teamByPlayer.containsKey(targetId)) {
            return CompletableFuture.completedFuture("Leave your current team first.");
        }

        InviteData invite = invitesByTarget.remove(targetId);
        if (invite == null) return CompletableFuture.completedFuture("You do not have a pending team invite.");
        if (invite.expiresAt < System.currentTimeMillis()) return CompletableFuture.completedFuture("That team invite expired.");

        TeamData team = teamsByKey.get(invite.teamKey);
        if (team == null) return CompletableFuture.completedFuture("That team no longer exists.");
        if (renamingTeamKeys.contains(team.key)) {
            invitesByTarget.put(targetId, invite);
            return CompletableFuture.completedFuture("Team settings are updating. Try again in a moment.");
        }

        team.members.add(targetId);
        teamByPlayer.put(targetId, team.key);
        applyTeamTag(target);

        return plugin.getDatabase().addTeamMember(team.displayName, targetId)
            .handle((added, ex) -> {
                if (ex == null && Boolean.TRUE.equals(added)) return null;
                if (ex == null) return "Could not join team right now.";
                plugin.getLogger().severe("addTeamMember failed: " + ex.getMessage());
                return "Could not join team right now.";
            })
            .thenCompose(error -> runOnMainThread(() -> {
                if (error != null) {
                    team.members.remove(targetId);
                    teamByPlayer.remove(targetId);
                    applyTeamTag(target);
                    return error;
                }

                Player inviter = Bukkit.getPlayer(invite.inviter);
                if (inviter != null && inviter.isOnline()) {
                    inviter.sendMessage(MessageUtil.success(
                        "<white>" + target.getName() + "</white> joined <white>" + team.displayName + "</white>."));
                }
                reconcileTeamCrowns(team);
                return (String) null;
            }));
    }

    public String denyInvite(Player target) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return unavailable;

        InviteData invite = invitesByTarget.remove(target.getUniqueId());
        if (invite == null) return "You do not have a pending team invite.";

        Player inviter = Bukkit.getPlayer(invite.inviter);
        if (inviter != null && inviter.isOnline()) {
            inviter.sendMessage(MessageUtil.warn(
                "<white>" + target.getName() + "</white> denied your team invite."));
        }
        return null;
    }

    public CompletableFuture<String> leaveTeam(Player player) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return CompletableFuture.completedFuture(unavailable);

        UUID playerId = player.getUniqueId();
        TeamData team = teamOf(playerId);
        if (team == null) return CompletableFuture.completedFuture("You are not in a team.");
        if (renamingTeamKeys.contains(team.key)) {
            return CompletableFuture.completedFuture("Team settings are updating. Try again in a moment.");
        }

        String teamName = team.displayName;
        String teamKey = team.key;
        CompletableFuture<String> vaultCheck = team.members.size() == 1
            ? ensureVaultEmptyBeforeDeletion(team)
            : CompletableFuture.completedFuture(null);
        return vaultCheck.thenCompose(vaultError -> {
            if (vaultError != null) {
                return CompletableFuture.<DatabaseManager.TeamLeaveRecord>failedFuture(new IllegalStateException(vaultError));
            }
            return plugin.getDatabase().leaveTeam(teamName, playerId);
        })
            .handle((result, ex) -> {
                if (ex == null) return result;
                Throwable root = ex.getCause() == null ? ex : ex.getCause();
                if (root.getMessage() != null && root.getMessage().startsWith("Empty the team vault")) {
                    return new DatabaseManager.TeamLeaveRecord(false, false, null);
                }
                plugin.getLogger().severe("leaveTeam failed: " + root.getMessage());
                return null;
            })
            .thenCompose(result -> {
                if (result == null || !result.membershipRemoved()) {
                    return CompletableFuture.completedFuture(result == null
                        ? "Could not leave team right now."
                        : "Empty the team vault before deleting the team so its contents are not lost.");
                }

                return runOnMainThread(() -> {
                    TeamData current = teamsByKey.get(teamKey);
                    if (current == null) {
                        teamByPlayer.remove(playerId);
                        applyTeamTag(player);
                        return null;
                    }

                    current.members.remove(playerId);
                    teamByPlayer.remove(playerId);
                    applyTeamTag(player);
                    removeCrownsFromPlayer(player);

                    if (result.teamDeleted()) {
                        discardTeamVault(current.key);
                        teamsByKey.remove(current.key);
                        removeTeamFromAllies(current.key);
                        unregisterScoreboardTeam(current.key);
                        invitesByTarget.entrySet().removeIf(entry -> entry.getValue().teamKey().equals(current.key));
                        allyInvitesByTargetTeam.entrySet().removeIf(entry ->
                            entry.getKey().equals(current.key) || entry.getValue().teamKey().equals(current.key));
                        return null;
                    }

                    closeVaultIfViewing(player, current.key);

                    boolean ownerLeft = current.owner.equals(playerId);
                    UUID newOwner = result.ownerUuid();
                    if (newOwner != null) {
                        current.owner = newOwner;
                        if (ownerLeft) {
                            Player ownerOnline = Bukkit.getPlayer(newOwner);
                            if (ownerOnline != null && ownerOnline.isOnline()) {
                                ownerOnline.sendMessage(MessageUtil.info("You are now the owner of <white>" + current.displayName + "</white>."));
                            }
                        }
                    }
                    reconcileTeamCrowns(current);
                    return (String) null;
                });
            });
    }

    public CompletableFuture<String> disbandTeam(Player owner) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return CompletableFuture.completedFuture(unavailable);

        TeamData team = teamOf(owner.getUniqueId());
        if (team == null) return CompletableFuture.completedFuture("You are not in a team.");
        if (!team.owner.equals(owner.getUniqueId())) {
            return CompletableFuture.completedFuture("Only the team owner can disband the team.");
        }
        if (renamingTeamKeys.contains(team.key)) {
            return CompletableFuture.completedFuture("Team settings are updating. Try again in a moment.");
        }

        String teamName = team.displayName;
        String teamKey = team.key;
        UUID ownerId = owner.getUniqueId();
        return ensureVaultEmptyBeforeDeletion(team)
            .thenCompose(vaultError -> vaultError != null
                ? CompletableFuture.<Void>failedFuture(new IllegalStateException(vaultError))
                : plugin.getDatabase().deleteTeam(teamName))
            .handle((ignored, ex) -> {
                if (ex == null) return null;
                Throwable root = ex.getCause() == null ? ex : ex.getCause();
                if (root.getMessage() != null && root.getMessage().startsWith("Empty the team vault")) {
                    return "Empty the team vault before deleting the team so its contents are not lost.";
                }
                plugin.getLogger().severe("deleteTeam failed: " + root.getMessage());
                return "Could not disband team right now.";
            })
            .thenCompose(error -> {
                if (error != null) return CompletableFuture.completedFuture(error);

                return runOnMainThread(() -> {
                    discardTeamVault(teamKey);
                    TeamData current = teamsByKey.remove(teamKey);
                    if (current == null) return null;
                    removeTeamFromAllies(teamKey);

                    for (UUID member : current.members) {
                        teamByPlayer.remove(member);
                        Player online = Bukkit.getPlayer(member);
                        if (online != null && online.isOnline()) {
                            applyTeamTag(online);
                            removeCrownsFromPlayer(online);
                            if (!online.getUniqueId().equals(ownerId)) {
                                online.sendMessage(MessageUtil.warn("Your team <white>" + current.displayName + "</white> was disbanded."));
                            }
                        }
                    }

                    invitesByTarget.entrySet().removeIf(entry -> entry.getValue().teamKey().equals(teamKey));
                    allyInvitesByTargetTeam.entrySet().removeIf(entry ->
                        entry.getKey().equals(teamKey) || entry.getValue().teamKey().equals(teamKey));
                    unregisterScoreboardTeam(teamKey);
                    return (String) null;
                });
            });
    }

    public String requestAlly(Player owner, String rawTeamName) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return unavailable;

        TeamData team = teamOf(owner.getUniqueId());
        if (team == null) return "You are not in a team.";
        if (!team.owner.equals(owner.getUniqueId())) return "Only the team owner can manage allies.";
        if (renamingTeamKeys.contains(team.key)) return "Team settings are updating. Try again in a moment.";

        TeamData target = teamsByKey.get(key(normalizeDisplayName(rawTeamName)));
        if (target == null) return "That team does not exist.";
        if (target.key.equals(team.key)) return "You cannot ally your own team.";
        if (team.allies.contains(target.key)) return "Your team is already allied with <white>" + target.displayName + "</white>.";
        if (renamingTeamKeys.contains(target.key)) return "That team is updating settings. Try again in a moment.";

        allyInvitesByTargetTeam.put(target.key, new AllyInviteData(team.key, owner.getUniqueId(), System.currentTimeMillis() + ALLY_INVITE_DURATION_MS));
        notifyTeam(target, MessageUtil.info(
            "<white>" + team.displayName + "</white> requested an alliance. The owner can use <white>/team ally accept "
                + team.displayName + "</white> or <white>/team ally deny " + team.displayName + "</white>."));
        return null;
    }

    public CompletableFuture<String> acceptAlly(Player owner, String rawTeamName) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return CompletableFuture.completedFuture(unavailable);

        TeamData target = teamOf(owner.getUniqueId());
        if (target == null) return CompletableFuture.completedFuture("You are not in a team.");
        if (!target.owner.equals(owner.getUniqueId())) return CompletableFuture.completedFuture("Only the team owner can manage allies.");
        if (renamingTeamKeys.contains(target.key)) return CompletableFuture.completedFuture("Team settings are updating. Try again in a moment.");

        String sourceKey = key(normalizeDisplayName(rawTeamName));
        AllyInviteData invite = allyInvitesByTargetTeam.get(target.key);
        if (invite == null || !invite.teamKey().equals(sourceKey)) {
            return CompletableFuture.completedFuture("No pending alliance request from that team.");
        }
        if (invite.expiresAt() < System.currentTimeMillis()) {
            allyInvitesByTargetTeam.remove(target.key, invite);
            return CompletableFuture.completedFuture("That alliance request expired.");
        }

        TeamData source = teamsByKey.get(sourceKey);
        if (source == null) {
            allyInvitesByTargetTeam.remove(target.key, invite);
            return CompletableFuture.completedFuture("That team no longer exists.");
        }
        if (source.key.equals(target.key)) {
            allyInvitesByTargetTeam.remove(target.key, invite);
            return CompletableFuture.completedFuture("You cannot ally your own team.");
        }
        if (source.allies.contains(target.key)) {
            allyInvitesByTargetTeam.remove(target.key, invite);
            return CompletableFuture.completedFuture("Your teams are already allied.");
        }

        return plugin.getDatabase().addTeamAlliance(source.displayName, target.displayName)
            .handle((ignored, ex) -> {
                if (ex == null) return null;
                plugin.getLogger().severe("addTeamAlliance failed: " + ex.getMessage());
                return "Could not create that alliance right now.";
            })
            .thenCompose(error -> runOnMainThread(() -> {
                if (error != null) return error;
                TeamData currentTarget = teamsByKey.get(target.key);
                TeamData currentSource = teamsByKey.get(source.key);
                if (currentTarget == null || currentSource == null) {
                    return "One of those teams no longer exists.";
                }
                currentTarget.allies.add(currentSource.key);
                currentSource.allies.add(currentTarget.key);
                allyInvitesByTargetTeam.remove(currentTarget.key, invite);
                notifyTeam(currentTarget, MessageUtil.success("Your team allied with <white>" + currentSource.displayName + "</white>."));
                notifyTeam(currentSource, MessageUtil.success("<white>" + currentTarget.displayName + "</white> accepted your alliance."));
                return (String) null;
            }));
    }

    public String denyAlly(Player owner, String rawTeamName) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return unavailable;

        TeamData target = teamOf(owner.getUniqueId());
        if (target == null) return "You are not in a team.";
        if (!target.owner.equals(owner.getUniqueId())) return "Only the team owner can manage allies.";

        String sourceKey = key(normalizeDisplayName(rawTeamName));
        AllyInviteData invite = allyInvitesByTargetTeam.get(target.key);
        if (invite == null || !invite.teamKey().equals(sourceKey)) {
            return "No pending alliance request from that team.";
        }

        allyInvitesByTargetTeam.remove(target.key, invite);
        TeamData source = teamsByKey.get(sourceKey);
        if (source != null) {
            notifyTeam(source, MessageUtil.warn("<white>" + target.displayName + "</white> denied your alliance request."));
        }
        return null;
    }

    public CompletableFuture<String> removeAlly(Player owner, String rawTeamName) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return CompletableFuture.completedFuture(unavailable);

        TeamData team = teamOf(owner.getUniqueId());
        if (team == null) return CompletableFuture.completedFuture("You are not in a team.");
        if (!team.owner.equals(owner.getUniqueId())) return CompletableFuture.completedFuture("Only the team owner can manage allies.");
        if (renamingTeamKeys.contains(team.key)) return CompletableFuture.completedFuture("Team settings are updating. Try again in a moment.");

        TeamData ally = teamsByKey.get(key(normalizeDisplayName(rawTeamName)));
        if (ally == null) return CompletableFuture.completedFuture("That team does not exist.");
        if (!team.allies.contains(ally.key)) return CompletableFuture.completedFuture("Your team is not allied with <white>" + ally.displayName + "</white>.");

        return plugin.getDatabase().removeTeamAlliance(team.displayName, ally.displayName)
            .handle((ignored, ex) -> {
                if (ex == null) return null;
                plugin.getLogger().severe("removeTeamAlliance failed: " + ex.getMessage());
                return "Could not remove that alliance right now.";
            })
            .thenCompose(error -> runOnMainThread(() -> {
                if (error != null) return error;
                TeamData currentTeam = teamsByKey.get(team.key);
                TeamData currentAlly = teamsByKey.get(ally.key);
                if (currentTeam != null) {
                    currentTeam.allies.remove(ally.key);
                }
                if (currentAlly != null) {
                    currentAlly.allies.remove(team.key);
                }
                if (currentTeam != null) {
                    notifyTeam(currentTeam, MessageUtil.warn("Your alliance with <white>" + ally.displayName + "</white> ended."));
                }
                if (currentAlly != null) {
                    notifyTeam(currentAlly, MessageUtil.warn("<white>" + team.displayName + "</white> ended your alliance."));
                }
                return (String) null;
            }));
    }

    public Component alliesMessage(UUID playerId) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) {
            return MessageUtil.info(unavailable);
        }

        TeamData team = teamOf(playerId);
        if (team == null) {
            return MessageUtil.info("You are not in a team.");
        }

        String allies = allyDisplayNames(team);
        AllyInviteData pending = allyInvitesByTargetTeam.get(team.key);
        String pendingText = "";
        if (pending != null && pending.expiresAt() >= System.currentTimeMillis()) {
            TeamData source = teamsByKey.get(pending.teamKey());
            if (source != null) {
                pendingText = " <gray>| Pending from: <white>" + source.displayName + "</white></gray>";
            }
        }

        return MessageUtil.prefixedRaw(
            "<gold>Allies</gold> <gray>| Current: <white>" + allies + "</white></gray>" + pendingText
        );
    }

    public Component infoMessage(UUID playerId) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) {
            return MessageUtil.info(unavailable);
        }

        TeamData team = teamOf(playerId);
        if (team == null) {
            return MessageUtil.info("You are not in a team.");
        }

        List<String> memberNames = new ArrayList<>();
        for (UUID member : team.members) {
            String name = Bukkit.getOfflinePlayer(member).getName();
            memberNames.add(name == null ? member.toString() : name);
        }
        memberNames.sort(String::compareToIgnoreCase);

        String ownerName = Bukkit.getOfflinePlayer(team.owner).getName();
        if (ownerName == null) ownerName = team.owner.toString();

        return MessageUtil.prefixedRaw(
                "<gold>Team</gold> <white>" + team.displayName + "</white> " +
                "<gray>| Owner: <white>" + ownerName + "</white> " +
                "| Color: <" + team.color.id + ">" + team.color.display + "</" + team.color.id + "> " +
                "| Members: <white>" + team.members.size() + "</white> " +
                "| Allies: <white>" + allyDisplayNames(team) + "</white> " +
            "| List: <white>" + String.join(", ", memberNames) + "</white></gray>"
        );
    }

    public void openTeamsMenu(Player player) {
        openTeamsMenu(player, null, false);
    }

    public void openTeamsMenu(Player player, String rawSearch, boolean fromMainMenu) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) {
            player.sendMessage(MessageUtil.error(unavailable));
            return;
        }

        String search = normalizeTeamSearch(rawSearch);
        List<TeamSnapshot> snapshots = teamBrowserSnapshots(search);
        Set<UUID> memberIds = new LinkedHashSet<>();
        for (TeamSnapshot snapshot : snapshots) {
            memberIds.addAll(snapshot.members());
        }

        TeamBrowserHolder holder = new TeamBrowserHolder(search, fromMainMenu);
        Inventory inventory = Bukkit.createInventory(
            holder,
            TEAM_BROWSER_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#22d3ee:#facc15><bold>Teams</bold></gradient>"), "Teams")
        );
        renderTeamBrowserLoading(inventory, search, fromMainMenu);
        player.openInventory(inventory);

        plugin.getDatabase().loadLeaderboardStats(memberIds).whenComplete((records, ex) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof TeamBrowserHolder current)
                    || current != holder) {
                    return;
                }
                if (ex != null) {
                    plugin.getLogger().severe("Failed to load team browser stats: " + ex.getMessage());
                    renderTeamBrowserError(inventory, search, fromMainMenu);
                    return;
                }
                renderTeamBrowser(player, inventory, holder, snapshots, records == null ? Map.of() : records);
            })
        );
    }

    private void renderTeamBrowserLoading(Inventory inventory, String search, boolean fromMainMenu) {
        decorateTeamBrowser(inventory);
        inventory.setItem(4, teamBrowserButton(
            Material.NETHER_STAR,
            MM.deserialize("<gradient:#22d3ee:#facc15><bold>Teams</bold></gradient>"),
            List.of(
                muted("Loading team stats..."),
                muted(search.isBlank() ? "Use /teams <name> to search." : "Search: " + search)
            )
        ));
        inventory.setItem(22, teamBrowserButton(
            Material.CLOCK,
            MM.deserialize("<yellow><bold>Loading</bold></yellow>"),
            List.of(muted("Pulling team totals from the database."))
        ));
        applyTeamBrowserFooter(inventory, search, fromMainMenu);
    }

    private void renderTeamBrowserError(Inventory inventory, String search, boolean fromMainMenu) {
        decorateTeamBrowser(inventory);
        inventory.setItem(22, teamBrowserButton(
            Material.BARRIER,
            MM.deserialize("<red><bold>Stats unavailable</bold></red>"),
            List.of(
                muted("Teams loaded, but stat totals failed."),
                muted("Try again in a moment.")
            )
        ));
        applyTeamBrowserFooter(inventory, search, fromMainMenu);
    }

    private void renderTeamBrowser(
        Player viewer,
        Inventory inventory,
        TeamBrowserHolder holder,
        List<TeamSnapshot> snapshots,
        Map<UUID, DatabaseManager.LeaderboardStatsRecord> records
    ) {
        holder.clearSlots();
        decorateTeamBrowser(inventory);

        long totalMembers = snapshots.stream().mapToLong(snapshot -> snapshot.members().size()).sum();
        long onlineMembers = snapshots.stream().mapToLong(TeamSnapshot::onlineMembers).sum();
        inventory.setItem(4, teamBrowserButton(
            Material.NETHER_STAR,
            MM.deserialize("<gradient:#22d3ee:#facc15><bold>Teams</bold></gradient>"),
            List.of(
                muted("Browse every team and compare server stats."),
                statLine("Teams", snapshots.size()),
                statLine("Members shown", totalMembers),
                statLine("Online shown", onlineMembers),
                muted(holder.search().isBlank() ? "Search with /teams <name>." : "Search: " + holder.search())
            )
        ));

        if (snapshots.isEmpty()) {
            inventory.setItem(22, teamBrowserButton(
                Material.OAK_SIGN,
                MM.deserialize("<yellow><bold>No teams found</bold></yellow>"),
                List.of(
                    muted(holder.search().isBlank()
                        ? "No teams exist yet."
                        : "No team matched that search."),
                    muted("Use /team create \"name\" [color].")
                )
            ));
        } else {
            int count = Math.min(snapshots.size(), TEAM_BROWSER_TEAM_SLOTS.length);
            for (int i = 0; i < count; i++) {
                TeamSnapshot snapshot = snapshots.get(i);
                TeamStats stats = teamStats(snapshot.members(), records, System.currentTimeMillis());
                int slot = TEAM_BROWSER_TEAM_SLOTS[i];
                holder.putTeam(slot, snapshot.key());
                inventory.setItem(slot, teamBrowserTeamItem(snapshot, stats, viewer.getUniqueId()));
            }
        }

        applyTeamBrowserFooter(inventory, holder.search(), holder.fromMainMenu());
    }

    private void applyTeamBrowserFooter(Inventory inventory, String search, boolean fromMainMenu) {
        inventory.setItem(TEAM_BROWSER_BACK_SLOT, teamBrowserButton(
            Material.ARROW,
            Component.text(fromMainMenu ? "Back to Menu" : "Close", NamedTextColor.YELLOW, TextDecoration.BOLD),
            List.of(muted(fromMainMenu ? "Return to /menu." : "Close this browser."))
        ));
        inventory.setItem(TEAM_BROWSER_VAULT_SLOT, teamBrowserButton(
            Material.BARREL,
            MM.deserialize("<green><bold>Your Team Vault</bold></green>"),
            List.of(
                muted("Open your team's shared double chest."),
                muted("Requires you to be in a team.")
            )
        ));
        inventory.setItem(TEAM_BROWSER_REFRESH_SLOT, teamBrowserButton(
            Material.COMPASS,
            MM.deserialize("<aqua><bold>Refresh</bold></aqua>"),
            List.of(
                muted(search.isBlank() ? "Reload all teams." : "Reload search: " + search),
                muted("Stats update as players earn them.")
            )
        ));
    }

    private List<TeamSnapshot> teamBrowserSnapshots(String search) {
        List<TeamSnapshot> all = new ArrayList<>();
        for (TeamData team : teamsByKey.values()) {
            List<UUID> members = new ArrayList<>(team.members);
            members.sort((a, b) -> playerName(a).compareToIgnoreCase(playerName(b)));
            int online = 0;
            for (UUID member : members) {
                Player player = Bukkit.getPlayer(member);
                if (player != null && player.isOnline()) {
                    online++;
                }
            }
            all.add(new TeamSnapshot(
                team.key,
                team.displayName,
                team.color,
                team.owner,
                List.copyOf(members),
                online,
                teamSearchScore(team.displayName, search)
            ));
        }

        if (search.isBlank()) {
            all.sort((a, b) -> {
                int memberCompare = Integer.compare(b.members().size(), a.members().size());
                if (memberCompare != 0) return memberCompare;
                return a.displayName().compareToIgnoreCase(b.displayName());
            });
            return limitTeamSnapshots(all);
        }

        List<TeamSnapshot> matches = new ArrayList<>();
        for (TeamSnapshot snapshot : all) {
            if (snapshot.searchScore() < 1000) {
                matches.add(snapshot);
            }
        }
        List<TeamSnapshot> result = matches.isEmpty() ? all : matches;
        result.sort((a, b) -> {
            int scoreCompare = Integer.compare(a.searchScore(), b.searchScore());
            if (scoreCompare != 0) return scoreCompare;
            return a.displayName().compareToIgnoreCase(b.displayName());
        });
        return limitTeamSnapshots(result);
    }

    private List<TeamSnapshot> limitTeamSnapshots(List<TeamSnapshot> snapshots) {
        if (snapshots.size() <= TEAM_BROWSER_TEAM_SLOTS.length) {
            return snapshots;
        }
        return new ArrayList<>(snapshots.subList(0, TEAM_BROWSER_TEAM_SLOTS.length));
    }

    private int teamSearchScore(String teamName, String search) {
        if (search == null || search.isBlank()) {
            return 0;
        }
        String name = normalizeTeamSearch(teamName);
        if (name.equals(search)) return 0;
        if (name.startsWith(search)) return 10 + Math.max(0, name.length() - search.length());
        int contains = name.indexOf(search);
        if (contains >= 0) return 100 + contains;
        int ordered = orderedSearchScore(name, search);
        return ordered >= 0 ? 300 + ordered : 1000 + Math.abs(name.length() - search.length());
    }

    private int orderedSearchScore(String name, String search) {
        int lastIndex = -1;
        int score = 0;
        for (int i = 0; i < search.length(); i++) {
            int index = name.indexOf(search.charAt(i), lastIndex + 1);
            if (index < 0) {
                return -1;
            }
            score += Math.max(0, index - lastIndex - 1);
            lastIndex = index;
        }
        return score;
    }

    private String normalizeTeamSearch(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private TeamStats teamStats(List<UUID> members, Map<UUID, DatabaseManager.LeaderboardStatsRecord> records, long now) {
        if (members == null || members.isEmpty()) {
            return EMPTY_TEAM_STATS;
        }
        long kills = 0L;
        long deaths = 0L;
        long bossKills = 0L;
        long mobKills = 0L;
        long bossDamage = 0L;
        long bossFights = 0L;
        long playtime = 0L;
        for (UUID member : members) {
            DatabaseManager.LeaderboardStatsRecord record = records == null ? null : records.get(member);
            if (record != null) {
                kills += Math.max(0L, record.playerKills());
                deaths += Math.max(0L, record.deaths());
                bossKills += Math.max(0L, record.bossKills());
                mobKills += Math.max(0L, record.mobKills());
                bossDamage += Math.max(0L, record.bossDamage());
                bossFights += Math.max(0L, record.bossFights());
                playtime += Math.max(0L, record.playtimeSeconds());
            }
            if (plugin.getLeaderboardManager() != null) {
                playtime += plugin.getLeaderboardManager().liveSessionSeconds(member, now);
            }
        }
        return new TeamStats(kills, deaths, bossKills, mobKills, bossDamage, bossFights, playtime);
    }

    private ItemStack teamBrowserTeamItem(TeamSnapshot snapshot, TeamStats stats, UUID viewerId) {
        List<Component> lore = new ArrayList<>();
        lore.add(muted("Owner: ").append(Component.text(playerName(snapshot.owner()), NamedTextColor.WHITE)));
        lore.add(muted("Members: ").append(Component.text(snapshot.members().size(), NamedTextColor.WHITE))
            .append(muted(" | Online: ")).append(Component.text(snapshot.onlineMembers(), NamedTextColor.GREEN)));
        lore.add(muted("Color: ").append(Component.text(snapshot.color().display, snapshot.color().textColor)));
        lore.add(Component.empty());
        lore.add(statLine("Player Kills", stats.playerKills()));
        lore.add(statLine("Deaths", stats.deaths()));
        lore.add(statLine("K/D", formatRatio(stats.playerKills(), stats.deaths())));
        lore.add(statLine("Boss Kills", stats.bossKills()));
        lore.add(statLine("Boss Damage", stats.bossDamage()));
        lore.add(statLine("Playtime", formatPlaytime(stats.playtimeSeconds())));
        lore.add(Component.empty());
        lore.add(muted("Members: ").append(Component.text(memberPreview(snapshot.members()), NamedTextColor.WHITE)));
        if (snapshot.members().contains(viewerId)) {
            lore.add(Component.empty());
            lore.add(Component.text("This is your team.", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(muted("Click for a chat summary."));

        return teamBrowserButton(
            Material.SHIELD,
            Component.text(snapshot.displayName(), snapshot.color().textColor, TextDecoration.BOLD),
            lore
        );
    }

    private Component teamSummaryMessage(TeamData team) {
        List<String> members = new ArrayList<>();
        for (UUID member : team.members) {
            members.add(playerName(member));
        }
        members.sort(String.CASE_INSENSITIVE_ORDER);
        return MessageUtil.prefixedRaw(
            "<gold>Team</gold> <" + team.color.id + ">" + team.displayName + "</" + team.color.id + "> " +
                "<gray>| Owner: <white>" + playerName(team.owner) + "</white> " +
                "| Members: <white>" + team.members.size() + "</white> " +
                "| List: <white>" + String.join(", ", members) + "</white></gray>"
        );
    }

    private String memberPreview(List<UUID> members) {
        if (members == null || members.isEmpty()) {
            return "none";
        }
        List<String> names = new ArrayList<>();
        int limit = Math.min(5, members.size());
        for (int i = 0; i < limit; i++) {
            names.add(playerName(members.get(i)));
        }
        if (members.size() > limit) {
            names.add("+" + (members.size() - limit) + " more");
        }
        return String.join(", ", names);
    }

    private String playerName(UUID playerId) {
        if (playerId == null) {
            return "Unknown";
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null || name.isBlank() ? playerId.toString().substring(0, 8) : name;
    }

    private String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }

    private String formatRatio(long kills, long deaths) {
        if (deaths <= 0L) {
            return kills <= 0L ? "0.00" : formatNumber(kills);
        }
        return String.format(Locale.ROOT, "%.2f", kills / (double) deaths);
    }

    private String formatPlaytime(long seconds) {
        long clamped = Math.max(0L, seconds);
        long days = clamped / 86_400L;
        long hours = (clamped % 86_400L) / 3_600L;
        long minutes = (clamped % 3_600L) / 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private Component statLine(String label, long value) {
        return statLine(label, formatNumber(value));
    }

    private Component statLine(String label, String value) {
        return muted(label + ": ").append(Component.text(value, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
    }

    private Component muted(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack teamBrowserButton(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        Component visibleName = MenuItemUtil.visibleName(name);
        meta.displayName(visibleName.decoration(TextDecoration.ITALIC, false));
        meta.lore(MenuItemUtil.visibleLore(name, lore).stream()
            .map(line -> line.decoration(TextDecoration.ITALIC, false))
            .toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    private void decorateTeamBrowser(Inventory inventory) {
        inventory.clear();
        ItemStack filler = teamBrowserPane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFrameSlot(slot, inventory.getSize())) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private boolean isFrameSlot(int slot, int size) {
        return slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8;
    }

    private ItemStack teamBrowserPane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MenuItemUtil.visibleName(Component.empty()));
            meta.lore(MenuItemUtil.visibleLore(Component.empty(), List.of()));
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openTeamVault(Player player) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) {
            player.sendMessage(MessageUtil.error(unavailable));
            return;
        }

        TeamData team = teamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(MessageUtil.error("You are not in a team."));
            return;
        }
        if (renamingTeamKeys.contains(team.key)) {
            player.sendMessage(MessageUtil.error("Team settings are updating. Try again in a moment."));
            return;
        }

        loadTeamVault(team).thenAccept(session ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                TeamData current = teamOf(player.getUniqueId());
                if (current == null || !current.key.equals(team.key)) {
                    player.sendMessage(MessageUtil.error("You are not in that team anymore."));
                    return;
                }

                auditTeamVault(player, session, "open");
                syncTeamVaultLegendaryClaims(session);
                player.openInventory(session.inventory());
            })
        ).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to open team vault for " + team.displayName + ": " + ex.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(MessageUtil.error("Team vault is unavailable right now."));
                }
            });
            return null;
        });
    }

    public void openTeamVaultInspector(Player viewer, String rawTeamName) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) {
            viewer.sendMessage(MessageUtil.error(unavailable));
            return;
        }

        String displayName = normalizeDisplayName(rawTeamName);
        if (displayName.isBlank()) {
            viewer.sendMessage(MessageUtil.error("Enter a team name."));
            return;
        }

        TeamData team = teamsByKey.get(key(displayName));
        if (team == null) {
            viewer.sendMessage(MessageUtil.error("That team does not exist."));
            return;
        }

        loadTeamVault(team).thenAccept(session ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) return;
                Inventory snapshot = Bukkit.createInventory(
                    new TeamVaultInspectorHolder(team.key),
                    TEAM_VAULT_SIZE,
                    Component.text(team.displayName + " Vault View")
                );
                snapshot.setContents(cloneContents(session.inventory().getContents()));
                viewer.openInventory(snapshot);
            })
        ).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to inspect team vault for " + team.displayName + ": " + ex.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline()) {
                    viewer.sendMessage(MessageUtil.error("Team vault is unavailable right now."));
                }
            });
            return null;
        });
    }

    public List<String> teamNames() {
        if (!teamsLoaded) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (TeamData team : teamsByKey.values()) {
            names.add(team.displayName);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public boolean isTeamVaultInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof TeamVaultHolder;
    }

    public void requestTeamVaultSave(Inventory inventory, String reason) {
        if (!(inventory != null && inventory.getHolder() instanceof TeamVaultHolder holder)) {
            return;
        }
        scheduleTeamVaultSyncAndSave(holder.teamKey(), reason == null || reason.isBlank() ? "external" : reason);
    }

    public CompletableFuture<Void> resyncAllTeamVaultLegendaryClaims() {
        if (!teamsLoaded) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Void>> syncs = new ArrayList<>();
        for (TeamData team : List.copyOf(teamsByKey.values())) {
            CompletableFuture<Void> sync = loadTeamVault(team).thenAccept(this::syncTeamVaultLegendaryClaims);
            sync.whenComplete((ignored, ex) -> {
                if (ex != null) {
                    plugin.getLogger().severe("Failed to sync legendary claims for team vault " + team.displayName + ": " + ex.getMessage());
                }
            });
            syncs.add(sync);
        }
        return CompletableFuture.allOf(syncs.toArray(CompletableFuture[]::new));
    }

    public boolean inTeam(UUID playerId) {
        if (!teamsLoaded) return false;
        return teamByPlayer.containsKey(playerId);
    }

    public String teamDisplayName(UUID playerId) {
        TeamData team = teamOf(playerId);
        return team == null ? null : team.displayName;
    }

    public Component tabTeamLabel(UUID playerId) {
        TeamData team = teamOf(playerId);
        return team == null ? null : Component.text(team.displayName, team.color.textColor);
    }

    public Component nameplateText(Player player) {
        Component playerName = player.displayName();
        if (playerName == null) {
            playerName = Component.text(player.getName(), NamedTextColor.WHITE);
        }

        TeamData team = teamOf(player.getUniqueId());
        if (team == null) {
            return playerName;
        }

        Component nameplate = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text(team.displayName, team.color.textColor))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(playerName);
        if (!team.owner.equals(player.getUniqueId())) {
            return nameplate;
        }

        return Component.text("Team Leader", NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .append(Component.newline())
            .append(nameplate);
    }

    public boolean sameTeam(UUID firstPlayerId, UUID secondPlayerId) {
        if (firstPlayerId == null || secondPlayerId == null) return false;
        if (firstPlayerId.equals(secondPlayerId)) return true;
        if (!teamsLoaded) return false;

        String firstTeam = teamByPlayer.get(firstPlayerId);
        if (firstTeam == null) return false;
        String secondTeam = teamByPlayer.get(secondPlayerId);
        if (secondTeam == null) return false;
        if (firstTeam.equals(secondTeam)) return true;

        TeamData first = teamsByKey.get(firstTeam);
        return first != null && first.allies.contains(secondTeam);
    }

    public void notifyPlayerTeam(UUID playerId, Component message) {
        if (playerId == null || message == null || !teamsLoaded) {
            return;
        }
        String teamKey = teamByPlayer.get(playerId);
        if (teamKey == null) {
            return;
        }
        notifyTeam(teamsByKey.get(teamKey), message);
    }

    public List<String> teamHelpLines() {
        return List.of(
            "<gold><bold>Team Commands</bold></gold>",
            "<gray><white>/team create \"name\" [color]</white> - Create a team</gray>",
            "<gray><white>/teams [search]</white> - Browse every team and compare stats</gray>",
            "<gray><white>/team list</white> - Open the team browser</gray>",
            "<gray><white>/team search <name></white> - Search teams by best match</gray>",
            "<gray><white>/team colors</white> - View team colors</gray>",
            "<gray><white>/team color <color></white> - Change team color (owner)</gray>",
            "<gray><white>/team rename \"name\"</white> - Rename your team (owner)</gray>",
            "<gray><white>/team invite <player></white> - Invite a player</gray>",
            "<gray><white>/team accept</white> / <white>/team deny</white> - Handle invites</gray>",
            "<gray><white>/team ally add <team></white> - Request an alliance (owner)</gray>",
            "<gray><white>/team ally accept <team></white> / <white>/team ally deny <team></white> - Handle alliance requests</gray>",
            "<gray><white>/team ally remove <team></white> - End an alliance (owner)</gray>",
            "<gray><white>/team allies</white> - View your team's allies</gray>",
            "<gray><white>/team leave</white> - Leave your team</gray>",
            "<gray><white>/team disband</white> - Disband your team (owner)</gray>",
            "<gray><white>/team info</white> - View your team info</gray>",
            "<gray><white>/tvault</white> (<white>/teamvault</white>) - Open your team storage</gray>",
            "<gray><white>/teamglow</white> - Privately outline teammates through walls</gray>"
        );
    }

    public List<String> teamColorLines() {
        List<String> lines = new ArrayList<>();
        lines.add("<gold><bold>Team Colors</bold></gold>");
        for (TeamColor color : TeamColor.values()) {
            lines.add("<gray><white>" + color.id + "</white> - <" + color.id + ">" + color.display + "</" + color.id + "></gray>");
        }
        return lines;
    }

    public boolean isTeamColor(String raw) {
        return TeamColor.fromId(raw) != null;
    }

    public List<String> teamColorIds() {
        List<String> ids = new ArrayList<>();
        for (TeamColor color : TeamColor.values()) {
            ids.add(color.id);
        }
        return ids;
    }

    private void notifyTeam(TeamData team, Component message) {
        if (team == null || message == null) {
            return;
        }
        for (UUID memberId : team.members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }

    private void removeTeamFromAllies(String teamKey) {
        if (teamKey == null || teamKey.isBlank()) {
            return;
        }
        for (TeamData other : teamsByKey.values()) {
            other.allies.remove(teamKey);
        }
    }

    private String allyDisplayNames(TeamData team) {
        if (team == null || team.allies.isEmpty()) {
            return "none";
        }

        List<String> names = new ArrayList<>();
        for (String allyKey : team.allies) {
            TeamData ally = teamsByKey.get(allyKey);
            if (ally != null) {
                names.add(ally.displayName);
            }
        }
        if (names.isEmpty()) {
            return "none";
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", names);
    }

    private TeamData teamOf(UUID playerId) {
        String teamKey = teamByPlayer.get(playerId);
        return teamKey == null ? null : teamsByKey.get(teamKey);
    }

    private CompletableFuture<TeamVaultSession> loadTeamVault(TeamData team) {
        TeamVaultSession cached = teamVaultsByKey.get(team.key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        if (renamingTeamKeys.contains(team.key)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Team settings are updating."));
        }

        CompletableFuture<TeamVaultSession> inFlight = vaultLoadingByTeamKey.get(team.key);
        if (inFlight != null) {
            return inFlight;
        }

        CompletableFuture<TeamVaultSession> future = new CompletableFuture<>();
        CompletableFuture<TeamVaultSession> existing = vaultLoadingByTeamKey.putIfAbsent(team.key, future);
        if (existing != null) {
            return existing;
        }

        plugin.getDatabase().loadTeamVault(team.displayName).whenComplete((raw, ex) -> {
            if (!plugin.isEnabled()) {
                vaultLoadingByTeamKey.remove(team.key, future);
                future.completeExceptionally(new IllegalStateException("Plugin is disabled."));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (future.isDone()) {
                        return;
                    }
                    if (ex != null) {
                        future.completeExceptionally(ex);
                        return;
                    }
                    if (renamingTeamKeys.contains(team.key)) {
                        future.completeExceptionally(new IllegalStateException("Team settings are updating."));
                        return;
                    }

                    TeamData current = teamsByKey.get(team.key);
                    if (current == null) {
                        future.completeExceptionally(new IllegalStateException("Team no longer exists."));
                        return;
                    }

                    TeamVaultSession loaded = teamVaultsByKey.computeIfAbsent(current.key, ignored ->
                        new TeamVaultSession(
                            current.key,
                            current.displayName,
                            createTeamVaultInventory(current.key, current.displayName, raw)
                        )
                    );
                    syncTeamVaultLegendaryClaims(loaded);
                    future.complete(loaded);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                } finally {
                    vaultLoadingByTeamKey.remove(team.key, future);
                }
            });
        });

        return future;
    }

    private CompletableFuture<String> ensureVaultEmptyBeforeDeletion(TeamData team) {
        return loadTeamVault(team)
            .thenCompose(session -> runOnMainThread(() -> {
                boolean hasItems = false;
                for (ItemStack item : session.inventory().getContents()) {
                    if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                        hasItems = true;
                        break;
                    }
                }
                if (hasItems) {
                    return CompletableFuture.completedFuture(
                        "Empty the team vault before deleting the team so its contents are not lost."
                    );
                }
                for (var viewer : List.copyOf(session.inventory().getViewers())) {
                    viewer.closeInventory();
                }
                return saveTeamVault(session)
                    .thenApply(ignored -> (String) null);
            }))
            .thenCompose(future -> future)
            .exceptionally(ex -> {
                plugin.getLogger().severe("Failed to verify team vault before deletion: " + ex.getMessage());
                return "Could not verify the team vault right now.";
            });
    }

    private Inventory createTeamVaultInventory(String teamKey, String displayName, byte[] rawData) {
        Inventory inventory = Bukkit.createInventory(
            new TeamVaultHolder(teamKey),
            TEAM_VAULT_SIZE,
            Component.text(displayName + " Vault")
        );
        inventory.setContents(deserialize(rawData, TEAM_VAULT_SIZE));
        return inventory;
    }

    private CompletableFuture<Void> saveTeamVault(TeamVaultSession session) {
        byte[] snapshot = serialize(session.inventory().getContents());
        CompletableFuture<Void> save = vaultSaveChainsByTeamKey.compute(session.teamKey(), (teamKey, previous) -> {
            CompletableFuture<Void> base = previous == null
                ? CompletableFuture.completedFuture(null)
                : previous.handle((ignored, ignoredEx) -> (Void) null);

            return base.thenCompose(ignored -> {
                if (teamVaultsByKey.get(teamKey) != session) {
                    return CompletableFuture.completedFuture(null);
                }
                TeamData current = teamsByKey.get(teamKey);
                if (current == null) {
                    return CompletableFuture.completedFuture(null);
                }
                return plugin.getDatabase().saveTeamVault(current.displayName, snapshot);
            });
        });
        save.whenComplete((ignored, ignoredEx) -> vaultSaveChainsByTeamKey.remove(session.teamKey(), save));
        return save;
    }

    private void auditTeamVault(Player observer, TeamVaultSession session, String reason) {
        if (observer == null || session == null || plugin.getItemAuditManager() == null) {
            return;
        }
        plugin.getItemAuditManager().auditSharedInventory(
            observer,
            session.inventory(),
            "team_vault:" + session.displayName() + ":" + reason
        );
    }

    private void scheduleTeamVaultLegendaryClaimSync(String teamKey) {
        if (teamKey == null || teamKey.isBlank()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            TeamVaultSession session = teamVaultsByKey.get(teamKey);
            if (session != null) {
                syncTeamVaultLegendaryClaims(session);
                for (var viewer : List.copyOf(session.inventory().getViewers())) {
                    if (viewer instanceof Player player) {
                        syncPlayerLegendaryClaims(player);
                    }
                }
            }
        });
    }

    private void scheduleTeamVaultSyncAndSave(String teamKey, String reason) {
        if (teamKey == null || teamKey.isBlank()) {
            return;
        }

        BukkitTask previous = vaultAutosaveTasksByTeamKey.remove(teamKey);
        if (previous != null) {
            previous.cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            vaultAutosaveTasksByTeamKey.remove(teamKey);
            TeamVaultSession session = teamVaultsByKey.get(teamKey);
            if (session == null) {
                return;
            }

            syncTeamVaultLegendaryClaims(session);
            for (var viewer : List.copyOf(session.inventory().getViewers())) {
                if (viewer instanceof Player player) {
                    syncPlayerLegendaryClaims(player);
                }
            }
            saveTeamVault(session).exceptionally(ex -> {
                plugin.getLogger().severe("Failed to autosave team vault for " + session.displayName()
                    + " after " + reason + ": " + ex.getMessage());
                return null;
            });
        }, 10L);
        vaultAutosaveTasksByTeamKey.put(teamKey, task);
    }

    private void cancelVaultAutosave(String teamKey) {
        if (teamKey == null || teamKey.isBlank()) {
            return;
        }
        BukkitTask task = vaultAutosaveTasksByTeamKey.remove(teamKey);
        if (task != null) {
            task.cancel();
        }
    }

    private void syncTeamVaultLegendaryClaims(TeamVaultSession session) {
        if (session == null || plugin.getLegendaryListener() == null) {
            return;
        }
        plugin.getLegendaryListener().syncStoredLegendaryOwnership(
            teamVaultLegendarySourceKey(session.teamKey()),
            session.inventory()
        );
    }

    private void clearTeamVaultLegendaryClaims(String teamKey) {
        if (teamKey == null || teamKey.isBlank() || plugin.getLegendaryListener() == null) {
            return;
        }
        plugin.getLegendaryListener().clearStoredLegendaryOwnership(teamVaultLegendarySourceKey(teamKey));
    }

    private String teamVaultLegendarySourceKey(String teamKey) {
        return "team_vault:" + teamKey;
    }

    private void syncPlayerLegendaryClaims(Player player) {
        if (player == null || plugin.getLegendaryListener() == null) {
            return;
        }
        plugin.getLegendaryListener().resyncLegendaryOwnership(player);
    }

    private CompletableFuture<Void> saveAndDiscardVaultForRename(TeamData team) {
        cancelVaultAutosave(team.key);
        cancelVaultLoad(team.key, "Team is being renamed.");
        TeamVaultSession session = teamVaultsByKey.get(team.key);
        if (session == null) {
            clearTeamVaultLegendaryClaims(team.key);
            return CompletableFuture.completedFuture(null);
        }

        syncTeamVaultLegendaryClaims(session);
        for (var viewer : List.copyOf(session.inventory().getViewers())) {
            viewer.closeInventory();
        }

        return saveTeamVault(session)
            .thenCompose(ignored -> runOnMainThread(() -> {
                clearTeamVaultLegendaryClaims(team.key);
                teamVaultsByKey.remove(team.key, session);
                return null;
            }));
    }

    private void refreshTeamVisuals(TeamData team) {
        getOrCreateScoreboardTeam(team);
        for (UUID memberId : team.members) {
            Player online = Bukkit.getPlayer(memberId);
            if (online == null || !online.isOnline()) {
                continue;
            }
            applyTeamTag(online);
            removeCrownsFromPlayer(online);
            reconcileCrowns(online);
        }
    }

    private String teamsUnavailableMessage() {
        if (teamsLoaded) return null;
        return teamsLoading ? TEAMS_LOADING_MESSAGE : "Teams are unavailable right now.";
    }

    private void applyTeamTag(Player player) {
        removePlayerFromPluginTeams(player.getName());
        TeamData team = teamOf(player.getUniqueId());
        if (team == null) {
            Team noTeam = getOrCreateNoTeamScoreboardTeam();
            if (!noTeam.hasEntry(player.getName())) {
                noTeam.addEntry(player.getName());
            }
            requestTabRefresh();
            return;
        }

        Team scoreboardTeam = getOrCreateScoreboardTeam(team);
        if (!scoreboardTeam.hasEntry(player.getName())) {
            scoreboardTeam.addEntry(player.getName());
        }
        requestTabRefresh();
    }

    private Team getOrCreateNoTeamScoreboardTeam() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(SCOREBOARD_NO_TEAM_ID);
        if (team == null) {
            team = board.registerNewTeam(SCOREBOARD_NO_TEAM_ID);
        }
        team.prefix(Component.empty());
        team.suffix(Component.empty());
        team.color(NamedTextColor.WHITE);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        return team;
    }

    private Team getOrCreateScoreboardTeam(TeamData data) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String id = scoreboardIdByTeamKey.computeIfAbsent(data.key, this::scoreboardIdForTeam);
        Team team = board.getTeam(id);
        if (team == null) {
            team = board.registerNewTeam(id);
        }
        // The unified tab controller renders the team once. Keeping this neutral prevents
        // scoreboard formatting from duplicating or recoloring the custom tab row.
        team.prefix(Component.empty());
        team.suffix(Component.empty());
        team.color(NamedTextColor.WHITE);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        return team;
    }

    private void requestTabRefresh() {
        if (plugin.getTabListManager() != null) {
            plugin.getTabListManager().requestRefresh();
        }
        if (plugin.getPlayerVisualListener() != null) {
            plugin.getPlayerVisualListener().requestTeamGlowRefresh();
        }
    }

    private void removePlayerFromPluginTeams(String playerName) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : board.getTeams()) {
            if (!team.getName().startsWith(SCOREBOARD_TEAM_PREFIX)) continue;
            if (team.hasEntry(playerName)) {
                team.removeEntry(playerName);
            }
        }
    }

    private void unregisterScoreboardTeam(String teamKey) {
        String scoreboardId = scoreboardIdByTeamKey.remove(teamKey);
        if (scoreboardId == null) return;

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(scoreboardId);
        if (team != null) {
            team.unregister();
        }
    }

    private void unregisterAllScoreboardTeams() {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : new ArrayList<>(board.getTeams())) {
            if (team.getName().startsWith(SCOREBOARD_TEAM_PREFIX)) {
                team.unregister();
            }
        }
        scoreboardIdByTeamKey.clear();
    }

    private void closeVaultIfViewing(Player player, String teamKey) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof TeamVaultHolder holder)) return;
        if (!holder.teamKey().equals(teamKey)) return;
        player.closeInventory();
    }

    private void discardTeamVault(String teamKey) {
        cancelVaultAutosave(teamKey);
        cancelVaultLoad(teamKey, "Team vault was discarded.");
        clearTeamVaultLegendaryClaims(teamKey);
        TeamVaultSession session = teamVaultsByKey.remove(teamKey);
        if (session == null) return;

        for (var viewer : List.copyOf(session.inventory().getViewers())) {
            viewer.closeInventory();
        }
    }

    private void cancelVaultLoad(String teamKey, String reason) {
        CompletableFuture<TeamVaultSession> loading = vaultLoadingByTeamKey.remove(teamKey);
        if (loading != null) {
            loading.completeExceptionally(new IllegalStateException(reason));
        }
    }

    private String normalizeDisplayName(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() >= 2) {
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }

    private String validateTeamName(String name) {
        if (name.isBlank()) return "Team name cannot be empty.";
        if (name.length() < 2 || name.length() > 16) return "Team name must be between 2 and 16 characters.";
        if (!name.matches("[A-Za-z0-9 _-]+")) {
            return "Team name can only use letters, numbers, spaces, '_' and '-'.";
        }
        return null;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private String scoreboardIdForTeam(String teamKey) {
        int hash = Math.abs(teamKey.hashCode());
        String suffix = Integer.toHexString(hash);
        String base = SCOREBOARD_TEAM_PREFIX + suffix;
        if (base.length() > 16) {
            base = base.substring(0, 16);
        }

        Set<String> used = new java.util.HashSet<>(scoreboardIdByTeamKey.values());
        if (!used.contains(base)) {
            return base;
        }

        int salt = 1;
        while (true) {
            String extra = Integer.toHexString(salt++);
            int keep = Math.max(1, 16 - extra.length());
            String candidate = base.substring(0, Math.min(base.length(), keep)) + extra;
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
    }

    private void reconcileTeamCrowns(TeamData team) {
        if (team == null) {
            return;
        }
        for (UUID memberId : team.members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                reconcileCrowns(member);
            }
        }
    }

    private void reconcileCrowns(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        TeamData team = teamOf(player.getUniqueId());
        boolean shouldHaveCrown = team != null
            && team.owner.equals(player.getUniqueId())
            && team.members.size() >= CROWN_MIN_TEAM_MEMBERS;

        int validCrowns = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isTeamCrown(item)) {
                continue;
            }

            if (!shouldHaveCrown || !isCrownFor(item, player.getUniqueId(), team.key) || validCrowns > 0) {
                contents[i] = null;
                continue;
            }
            contents[i] = createTeamCrown(player, team);
            validCrowns++;
        }
        player.getInventory().setStorageContents(contents);

        ItemStack helmet = player.getInventory().getHelmet();
        if (isTeamCrown(helmet)) {
            if (!shouldHaveCrown || !isCrownFor(helmet, player.getUniqueId(), team.key) || validCrowns > 0) {
                player.getInventory().setHelmet(null);
            } else {
                player.getInventory().setHelmet(createTeamCrown(player, team));
                validCrowns++;
            }
        }

        if (!shouldHaveCrown || validCrowns > 0) {
            pendingCrownReturns.remove(player.getUniqueId());
            return;
        }

        ItemStack crown = createTeamCrown(player, team);
        int emptySlot = player.getInventory().firstEmpty();
        if (emptySlot == -1) {
            queueCrownRetry(player);
            return;
        }

        player.getInventory().setItem(emptySlot, crown);
        pendingCrownReturns.remove(player.getUniqueId());
        player.sendMessage(MessageUtil.success("Your team reached <white>" + CROWN_MIN_TEAM_MEMBERS + "</white> members. You received your crown."));
    }

    private void queueCrownRetry(Player player) {
        UUID playerId = player.getUniqueId();
        if (pendingCrownReturns.add(playerId)) {
            player.sendMessage(MessageUtil.warn("Your team crown is waiting. Clear one inventory slot and it will be returned automatically."));
            scheduleCrownRetry(playerId);
        }
    }

    private void scheduleCrownRetry(UUID playerId) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!pendingCrownReturns.contains(playerId)) {
                return;
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }

            reconcileCrowns(player);
            if (pendingCrownReturns.contains(playerId)) {
                scheduleCrownRetry(playerId);
            }
        }, 20L * 15L);
    }

    private void removeCrownsFromPlayer(Player player) {
        if (player == null) {
            return;
        }

        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (isTeamCrown(contents[i])) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
        if (isTeamCrown(player.getInventory().getHelmet())) {
            player.getInventory().setHelmet(null);
        }
    }

    private ItemStack createTeamCrown(Player owner, TeamData team) {
        ItemStack crown = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = crown.getItemMeta();
        if (meta == null) {
            return crown;
        }

        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.LEGENDARY, owner.getName() + "'s Crown"));
        meta.setItemModel(keyTeamCrownEquipmentModel);
        meta.setUnbreakable(true);
        meta.addEnchant(enchantProtection, 4, true);
        meta.addEnchant(enchantAquaAffinity, 1, true);
        meta.addEnchant(enchantRespiration, 3, true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        CustomLoreUtil.applyStyledItemFlags(meta);
        EquippableComponent equippable = meta.getEquippable();
        equippable.setSlot(EquipmentSlot.HEAD);
        equippable.setModel(keyTeamCrownEquipmentModel);
        equippable.setDispensable(false);
        meta.setEquippable(equippable);
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.NETHERITE_HELMET,
            CustomLoreUtil.Rarity.LEGENDARY.label(),
            "CROWN",
            List.of(
                "<gray>Team: <" + team.color.id + ">" + team.displayName + "</" + team.color.id + "></gray>",
                "<gray>Owner: <white>" + owner.getName() + "</white></gray>",
                "<gray>Requires <white>" + CROWN_MIN_TEAM_MEMBERS + "</white> team members.</gray>",
                "<gray>Undroppable</gray>"
            ),
            List.of(CustomLoreUtil.section(
                "Team Relic",
                "Founder's Crown",
                "<gray>Awarded to the creator of a team once the team has enough members.</gray>",
                "<gray>If ownership changes, the crown follows the new owner.</gray>"
            ))
        ));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyTeamCrown, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyTeamCrownOwner, PersistentDataType.STRING, owner.getUniqueId().toString());
        pdc.set(keyTeamCrownTeam, PersistentDataType.STRING, team.key);
        crown.setItemMeta(meta);
        return crown;
    }

    private boolean isTeamCrown(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_HELMET) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte tagged = meta.getPersistentDataContainer().get(keyTeamCrown, PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1;
    }

    private boolean isCrownFor(ItemStack item, UUID ownerId, String teamKey) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String owner = pdc.get(keyTeamCrownOwner, PersistentDataType.STRING);
        String team = pdc.get(keyTeamCrownTeam, PersistentDataType.STRING);
        return ownerId.toString().equals(owner) && teamKey.equals(team);
    }

    private Enchantment requireEnchantment(String key) {
        return RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ENCHANTMENT)
            .getOrThrow(NamespacedKey.minecraft(key));
    }

    private <T> CompletableFuture<T> runOnMainThread(java.util.function.Supplier<T> action) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(action.get());
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(action.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private byte[] serialize(ItemStack[] contents) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                    out.writeInt(0);
                    continue;
                }
                byte[] raw = item.serializeAsBytes();
                out.writeInt(raw.length);
                out.write(raw);
            }
            out.flush();
            return baos.toByteArray();
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to serialize team vault data: " + ex.getMessage());
            return new byte[0];
        }
    }

    private ItemStack[] deserialize(byte[] data, int size) {
        ItemStack[] out = new ItemStack[size];
        if (data == null || data.length == 0) return out;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream in = new DataInputStream(bais)) {
            int stored = in.readInt();
            for (int i = 0; i < stored; i++) {
                int length = in.readInt();
                if (length < 0) {
                    throw new IOException("Negative team vault item length");
                }
                if (length == 0) {
                    continue;
                }

                byte[] raw = in.readNBytes(length);
                if (raw.length != length) {
                    throw new IOException("Unexpected end of team vault data");
                }

                if (i < size) {
                    out[i] = ItemStack.deserializeBytes(raw);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Team vault data was invalid and has been reset: " + ex.getMessage());
        }
        return out;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private record TeamSnapshot(
        String key,
        String displayName,
        TeamColor color,
        UUID owner,
        List<UUID> members,
        int onlineMembers,
        int searchScore
    ) {}

    private record TeamStats(
        long playerKills,
        long deaths,
        long bossKills,
        long mobKills,
        long bossDamage,
        long bossFights,
        long playtimeSeconds
    ) {}

    private record AllyInviteData(String teamKey, UUID requester, long expiresAt) {}

    private static final class TeamData {
        private String key;
        private String displayName;
        private TeamColor color;
        private UUID owner;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();
        private final Set<String> allies = ConcurrentHashMap.newKeySet();

        private TeamData(String key, String displayName, UUID owner, TeamColor color) {
            this.key = key;
            this.displayName = displayName;
            this.owner = owner;
            this.color = color == null ? TeamColor.GOLD : color;
        }
    }

    private enum TeamColor {
        BLACK("black", "Black", NamedTextColor.BLACK),
        DARK_BLUE("dark_blue", "Dark Blue", NamedTextColor.DARK_BLUE),
        DARK_GREEN("dark_green", "Dark Green", NamedTextColor.DARK_GREEN),
        DARK_AQUA("dark_aqua", "Dark Aqua", NamedTextColor.DARK_AQUA),
        DARK_RED("dark_red", "Dark Red", NamedTextColor.DARK_RED),
        DARK_PURPLE("dark_purple", "Dark Purple", NamedTextColor.DARK_PURPLE),
        GOLD("gold", "Gold", NamedTextColor.GOLD),
        GRAY("gray", "Gray", NamedTextColor.GRAY),
        DARK_GRAY("dark_gray", "Dark Gray", NamedTextColor.DARK_GRAY),
        BLUE("blue", "Blue", NamedTextColor.BLUE),
        GREEN("green", "Green", NamedTextColor.GREEN),
        AQUA("aqua", "Aqua", NamedTextColor.AQUA),
        RED("red", "Red", NamedTextColor.RED),
        LIGHT_PURPLE("light_purple", "Light Purple", NamedTextColor.LIGHT_PURPLE),
        YELLOW("yellow", "Yellow", NamedTextColor.YELLOW),
        WHITE("white", "White", NamedTextColor.WHITE);

        private final String id;
        private final String display;
        private final NamedTextColor textColor;

        TeamColor(String id, String display, NamedTextColor textColor) {
            this.id = id;
            this.display = display;
            this.textColor = textColor;
        }

        private static TeamColor fromId(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            for (TeamColor color : values()) {
                if (color.id.equals(normalized)) {
                    return color;
                }
            }
            return null;
        }
    }

    private record InviteData(String teamKey, UUID inviter, long expiresAt) {}
    private record TeamVaultSession(String teamKey, String displayName, Inventory inventory) {}

    private static final class TeamBrowserHolder implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        private final String search;
        private final boolean fromMainMenu;
        private final Map<Integer, String> teamKeysBySlot = new HashMap<>();

        private TeamBrowserHolder(String search, boolean fromMainMenu) {
            this.search = search == null ? "" : search;
            this.fromMainMenu = fromMainMenu;
        }

        private String search() {
            return search;
        }

        private boolean fromMainMenu() {
            return fromMainMenu;
        }

        private void putTeam(int slot, String teamKey) {
            teamKeysBySlot.put(slot, teamKey);
        }

        private String teamBySlot(int slot) {
            return teamKeysBySlot.get(slot);
        }

        private void clearSlots() {
            teamKeysBySlot.clear();
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record TeamVaultHolder(String teamKey) implements InventoryHolder, MenuDupeGuardListener.MutableMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record TeamVaultInspectorHolder(String teamKey) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
