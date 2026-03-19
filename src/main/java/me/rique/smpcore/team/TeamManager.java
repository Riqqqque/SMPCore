package me.rique.smpcore.team;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

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
    private static final String SCOREBOARD_TEAM_PREFIX = "smpct_";
    private static final String TEAMS_LOADING_MESSAGE = "Teams are still loading. Try again in a moment.";

    private final SMPCore plugin;
    private final Map<String, TeamData> teamsByKey = new ConcurrentHashMap<>();
    private final Map<UUID, String> teamByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, InviteData> invitesByTarget = new ConcurrentHashMap<>();
    private final Map<String, String> scoreboardIdByTeamKey = new ConcurrentHashMap<>();
    private volatile boolean teamsLoaded;
    private volatile boolean teamsLoading;

    public TeamManager(SMPCore plugin) {
        this.plugin = plugin;
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

            TeamData data = new TeamData(key, displayName, row.ownerUuid());
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
        }

        teamsLoaded = true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyTeamTag(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        invitesByTarget.remove(event.getPlayer().getUniqueId());
    }

    public CompletableFuture<String> createTeam(Player creator, String rawName) {
        String unavailable = teamsUnavailableMessage();
        if (unavailable != null) return CompletableFuture.completedFuture(unavailable);

        String displayName = normalizeDisplayName(rawName);
        String validationError = validateTeamName(displayName);
        if (validationError != null) return CompletableFuture.completedFuture(validationError);

        UUID creatorId = creator.getUniqueId();
        if (teamByPlayer.containsKey(creatorId)) {
            return CompletableFuture.completedFuture("Leave your current team first.");
        }

        String teamKey = key(displayName);
        if (teamsByKey.containsKey(teamKey)) {
            return CompletableFuture.completedFuture("That team already exists.");
        }

        return plugin.getDatabase().createTeam(displayName, creatorId)
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

                    TeamData data = new TeamData(teamKey, displayName, creatorId);
                    data.members.add(creatorId);
                    teamsByKey.put(teamKey, data);
                    teamByPlayer.put(creatorId, teamKey);
                    Player online = Bukkit.getPlayer(creatorId);
                    if (online != null && online.isOnline()) {
                        applyTeamTag(online);
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

                    if (current.members.isEmpty()) {
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
                    TeamData current = teamsByKey.remove(teamKey);
                    if (current == null) return null;

                    for (UUID member : current.members) {
                        teamByPlayer.remove(member);
                        Player online = Bukkit.getPlayer(member);
                        if (online != null && online.isOnline()) {
                            applyTeamTag(online);
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
                "| Members: <white>" + team.members.size() + "</white> " +
                "| List: <white>" + String.join(", ", memberNames) + "</white></gray>"
        );
    }

    public boolean inTeam(UUID playerId) {
        if (!teamsLoaded) return false;
        return teamByPlayer.containsKey(playerId);
    }

    public boolean sameTeam(UUID firstPlayerId, UUID secondPlayerId) {
        if (!teamsLoaded) return false;
        if (firstPlayerId == null || secondPlayerId == null) return false;
        if (firstPlayerId.equals(secondPlayerId)) return true;

        String firstTeam = teamByPlayer.get(firstPlayerId);
        if (firstTeam == null) return false;
        return firstTeam.equals(teamByPlayer.get(secondPlayerId));
    }

    public List<String> teamHelpLines() {
        return List.of(
            "<gold><bold>Team Commands</bold></gold>",
            "<gray><white>/team create \"name\"</white> - Create a team</gray>",
            "<gray><white>/team invite <player></white> - Invite a player</gray>",
            "<gray><white>/team accept</white> / <white>/team deny</white> - Handle invites</gray>",
            "<gray><white>/team leave</white> - Leave your team</gray>",
            "<gray><white>/team disband</white> - Disband your team (owner)</gray>",
            "<gray><white>/team info</white> - View your team info</gray>"
        );
    }

    private TeamData teamOf(UUID playerId) {
        String teamKey = teamByPlayer.get(playerId);
        return teamKey == null ? null : teamsByKey.get(teamKey);
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
        team.prefix(Component.text("[" + data.displayName + "] ", NamedTextColor.GOLD));
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

    private static final class TeamData {
        private final String key;
        private final String displayName;
        private UUID owner;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();

        private TeamData(String key, String displayName, UUID owner) {
            this.key = key;
            this.displayName = displayName;
            this.owner = owner;
        }
    }

    private record InviteData(String teamKey, UUID inviter, long expiresAt) {}
}
