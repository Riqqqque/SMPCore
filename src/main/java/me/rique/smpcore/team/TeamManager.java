package me.rique.smpcore.team;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
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

    private static final long INVITE_DURATION_MS = 120_000L;
    private static final int TEAM_VAULT_SIZE = 54;
    private static final String SCOREBOARD_TEAM_PREFIX = "smpct_";
    private static final String TEAMS_LOADING_MESSAGE = "Teams are still loading. Try again in a moment.";
    private static final int CROWN_MIN_TEAM_MEMBERS = 3;

    private final SMPCore plugin;
    private final NamespacedKey keyTeamCrown;
    private final NamespacedKey keyTeamCrownOwner;
    private final NamespacedKey keyTeamCrownTeam;
    private final Enchantment enchantProtection;
    private final Enchantment enchantAquaAffinity;
    private final Enchantment enchantRespiration;
    private final Map<String, TeamData> teamsByKey = new ConcurrentHashMap<>();
    private final Map<UUID, String> teamByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, InviteData> invitesByTarget = new ConcurrentHashMap<>();
    private final Map<String, String> scoreboardIdByTeamKey = new ConcurrentHashMap<>();
    private final Map<String, TeamVaultSession> teamVaultsByKey = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<TeamVaultSession>> vaultLoadingByTeamKey = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> vaultSaveChainsByTeamKey = new ConcurrentHashMap<>();
    private final Set<UUID> pendingCrownReturns = ConcurrentHashMap.newKeySet();
    private volatile boolean teamsLoaded;
    private volatile boolean teamsLoading;

    public TeamManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyTeamCrown = new NamespacedKey(plugin, "team_crown");
        this.keyTeamCrownOwner = new NamespacedKey(plugin, "team_crown_owner");
        this.keyTeamCrownTeam = new NamespacedKey(plugin, "team_crown_team");
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
        scoreboardIdByTeamKey.clear();

        for (DatabaseManager.TeamRecord row : rows) {
            String displayName = row.name();
            String key = key(displayName);
            if (key.isEmpty()) continue;

            TeamColor color = TeamColor.fromId(row.color());
            TeamData data = new TeamData(key, displayName, row.ownerUuid(), color);
            data.members.addAll(row.members());
            if (!data.members.contains(row.ownerUuid())) {
                data.members.add(row.ownerUuid());
                plugin.getDatabase().addTeamMember(displayName, row.ownerUuid())
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrownDeath(PlayerDeathEvent event) {
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
    public void onQuit(PlayerQuitEvent event) {
        invitesByTarget.remove(event.getPlayer().getUniqueId());

        if (!(event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof TeamVaultHolder holder)) {
            return;
        }

        TeamVaultSession session = teamVaultsByKey.get(holder.teamKey());
        if (session == null) {
            return;
        }

        auditTeamVault(event.getPlayer(), session, "quit");
        saveTeamVault(session).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to save team vault for " + session.displayName() + " on quit: " + ex.getMessage());
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
        saveTeamVault(session).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to save team vault for " + session.displayName() + ": " + ex.getMessage());
            return null;
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeamVaultClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TeamVaultHolder)) {
            return;
        }
        if (event.getAction() == InventoryAction.CLONE_STACK || event.getClick() == ClickType.CREATIVE) {
            event.setCancelled(true);
        }
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
        if (!(event.getView().getTopInventory().getHolder() instanceof TeamVaultHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || player.getGameMode() != GameMode.CREATIVE) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
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

    public void shutdown() {
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (TeamVaultSession session : teamVaultsByKey.values()) {
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
        teamVaultsByKey.clear();
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
            .handle((created, ex) -> {
                if (ex != null) {
                    plugin.getLogger().severe("createTeam failed: " + ex.getMessage());
                    return null;
                }
                return created;
            })
            .thenCompose(created -> {
                if (created == null) return CompletableFuture.completedFuture("Could not create team right now.");
                if (!created) return CompletableFuture.completedFuture("That team already exists.");
                return plugin.getDatabase().addTeamMember(displayName, creatorId)
                    .handle((ignored, ex) -> {
                        if (ex == null) return null;
                        plugin.getLogger().severe("addTeamMember (owner) failed: " + ex.getMessage());
                        plugin.getDatabase().deleteTeam(displayName);
                        return "Could not create team right now.";
                    });
            })
            .thenCompose(error -> {
                if (error != null) return CompletableFuture.completedFuture(error);
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

    public String invite(Player inviter, Player target) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return unavailable;

        TeamData team = teamOf(inviter.getUniqueId());
        if (team == null) return "You are not in a team.";
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

        team.members.add(targetId);
        teamByPlayer.put(targetId, team.key);
        applyTeamTag(target);

        return plugin.getDatabase().addTeamMember(team.displayName, targetId)
            .handle((ignored, ex) -> {
                if (ex == null) return null;
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

        String teamName = team.displayName;
        String teamKey = team.key;
        return plugin.getDatabase().removeTeamMember(teamName, playerId)
            .handle((ignored, ex) -> {
                if (ex == null) return null;
                plugin.getLogger().severe("removeTeamMember failed: " + ex.getMessage());
                return "Could not leave team right now.";
            })
            .thenCompose(error -> {
                if (error != null) return CompletableFuture.completedFuture(error);

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

                    if (current.members.isEmpty()) {
                        discardTeamVault(current.key);
                        teamsByKey.remove(current.key);
                        unregisterScoreboardTeam(current.key);
                        plugin.getDatabase().deleteTeam(current.displayName)
                            .exceptionally(ex -> {
                                plugin.getLogger().severe("deleteTeam failed: " + ex.getMessage());
                                return null;
                            });
                        invitesByTarget.entrySet().removeIf(entry -> entry.getValue().teamKey().equals(current.key));
                        return null;
                    }

                    closeVaultIfViewing(player, current.key);

                    if (current.owner.equals(playerId)) {
                        UUID newOwner = current.members.stream().findFirst().orElse(null);
                        if (newOwner != null) {
                            current.owner = newOwner;
                            plugin.getDatabase().setTeamOwner(current.displayName, newOwner)
                                .exceptionally(ex -> {
                                    plugin.getLogger().severe("setTeamOwner failed: " + ex.getMessage());
                                    return null;
                                });

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

        String teamName = team.displayName;
        String teamKey = team.key;
        UUID ownerId = owner.getUniqueId();
        return plugin.getDatabase().deleteTeam(teamName)
            .handle((ignored, ex) -> {
                if (ex == null) return null;
                plugin.getLogger().severe("deleteTeam failed: " + ex.getMessage());
                return "Could not disband team right now.";
            })
            .thenCompose(error -> {
                if (error != null) return CompletableFuture.completedFuture(error);

                return runOnMainThread(() -> {
                    discardTeamVault(teamKey);
                    TeamData current = teamsByKey.remove(teamKey);
                    if (current == null) return null;

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
                    unregisterScoreboardTeam(teamKey);
                    return (String) null;
                });
            });
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
                "| List: <white>" + String.join(", ", memberNames) + "</white></gray>"
        );
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

        loadTeamVault(team).thenAccept(session ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                TeamData current = teamOf(player.getUniqueId());
                if (current == null || !current.key.equals(team.key)) {
                    player.sendMessage(MessageUtil.error("You are not in that team anymore."));
                    return;
                }

                auditTeamVault(player, session, "open");
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

    public boolean inTeam(UUID playerId) {
        if (!teamsLoaded) return false;
        return teamByPlayer.containsKey(playerId);
    }

    public boolean sameTeam(UUID firstPlayerId, UUID secondPlayerId) {
        if (firstPlayerId == null || secondPlayerId == null) return false;
        if (firstPlayerId.equals(secondPlayerId)) return true;
        if (!teamsLoaded) return false;

        String firstTeam = teamByPlayer.get(firstPlayerId);
        if (firstTeam == null) return false;
        return firstTeam.equals(teamByPlayer.get(secondPlayerId));
    }

    public List<String> teamHelpLines() {
        return List.of(
            "<gold><bold>Team Commands</bold></gold>",
            "<gray><white>/team create \"name\" [color]</white> - Create a team</gray>",
            "<gray><white>/team colors</white> - View team colors</gray>",
            "<gray><white>/team invite <player></white> - Invite a player</gray>",
            "<gray><white>/team accept</white> / <white>/team deny</white> - Handle invites</gray>",
            "<gray><white>/team leave</white> - Leave your team</gray>",
            "<gray><white>/team disband</white> - Disband your team (owner)</gray>",
            "<gray><white>/team info</white> - View your team info</gray>",
            "<gray><white>/tvault</white> (<white>/teamvault</white>) - Open your team storage</gray>"
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

    private TeamData teamOf(UUID playerId) {
        String teamKey = teamByPlayer.get(playerId);
        return teamKey == null ? null : teamsByKey.get(teamKey);
    }

    private CompletableFuture<TeamVaultSession> loadTeamVault(TeamData team) {
        TeamVaultSession cached = teamVaultsByKey.get(team.key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
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
                    if (ex != null) {
                        future.completeExceptionally(ex);
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
                return plugin.getDatabase().saveTeamVault(session.displayName(), snapshot);
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

    private String teamsUnavailableMessage() {
        if (teamsLoaded) return null;
        return teamsLoading ? TEAMS_LOADING_MESSAGE : "Teams are unavailable right now.";
    }

    private void applyTeamTag(Player player) {
        removePlayerFromPluginTeams(player.getName());
        TeamData team = teamOf(player.getUniqueId());
        if (team == null) return;

        Team scoreboardTeam = getOrCreateScoreboardTeam(team);
        if (!scoreboardTeam.hasEntry(player.getName())) {
            scoreboardTeam.addEntry(player.getName());
        }
    }

    private Team getOrCreateScoreboardTeam(TeamData data) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String id = scoreboardIdByTeamKey.computeIfAbsent(data.key, this::scoreboardIdForTeam);
        Team team = board.getTeam(id);
        if (team == null) {
            team = board.registerNewTeam(id);
        }
        team.prefix(Component.text("[" + data.displayName + "] ", data.color.textColor));
        team.color(data.color.textColor);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        return team;
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

    private void closeVaultIfViewing(Player player, String teamKey) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof TeamVaultHolder holder)) return;
        if (!holder.teamKey().equals(teamKey)) return;
        player.closeInventory();
    }

    private void discardTeamVault(String teamKey) {
        vaultLoadingByTeamKey.remove(teamKey);
        TeamVaultSession session = teamVaultsByKey.remove(teamKey);
        if (session == null) return;

        for (var viewer : List.copyOf(session.inventory().getViewers())) {
            viewer.closeInventory();
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

            if (!shouldHaveCrown || !isCrownFor(item, player.getUniqueId(), team.key) || validCrowns++ > 0) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);

        ItemStack helmet = player.getInventory().getHelmet();
        if (isTeamCrown(helmet)) {
            if (!shouldHaveCrown || !isCrownFor(helmet, player.getUniqueId(), team.key) || validCrowns++ > 0) {
                player.getInventory().setHelmet(null);
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
        meta.setUnbreakable(true);
        meta.addEnchant(enchantProtection, 4, true);
        meta.addEnchant(enchantAquaAffinity, 1, true);
        meta.addEnchant(enchantRespiration, 3, true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        CustomLoreUtil.applyStyledItemFlags(meta);
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

    private static final class TeamData {
        private final String key;
        private final String displayName;
        private final TeamColor color;
        private UUID owner;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();

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

    private record TeamVaultHolder(String teamKey) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record TeamVaultInspectorHolder(String teamKey) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
