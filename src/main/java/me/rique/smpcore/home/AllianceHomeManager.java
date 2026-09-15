package me.rique.smpcore.home;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.team.TeamManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Persistent homes shared by one exact pair of allied teams.
 */
public final class AllianceHomeManager {

    private static final int MAX_HOME_NAME_LENGTH = 24;

    private final SMPCore plugin;
    private final Map<AllianceKey, Map<String, HomeEntry>> homesByAlliance = new ConcurrentHashMap<>();
    private final Set<MutationKey> mutationsInProgress = ConcurrentHashMap.newKeySet();

    public AllianceHomeManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void loadAllBlocking() {
        List<DatabaseManager.AllianceHomeRecord> records = plugin.getDatabase().loadAllAllianceHomes().join();
        Map<AllianceKey, Map<String, HomeEntry>> loaded = new ConcurrentHashMap<>();
        for (DatabaseManager.AllianceHomeRecord record : records) {
            if (record == null || record.home() == null) {
                continue;
            }
            AllianceKey key = AllianceKey.of(record.firstTeam(), record.secondTeam());
            if (!key.valid()) {
                continue;
            }
            loaded.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>())
                .put(normalizeHomeName(record.home().name()), record.home());
        }
        homesByAlliance.clear();
        homesByAlliance.putAll(loaded);
    }

    public CompletableFuture<ActionResult> setHome(Player player, String rawName, String rawAllyTeam) {
        if (player == null) {
            return CompletableFuture.completedFuture(ActionResult.error("Only players can set alliance homes."));
        }
        TeamManager teams = plugin.getTeamManager();
        if (!teams.isTeamOwner(player.getUniqueId())) {
            return CompletableFuture.completedFuture(ActionResult.error(
                "Only your team leader can set alliance homes."
            ));
        }

        String name = normalizeHomeName(rawName);
        if (!isValidHomeName(name)) {
            return CompletableFuture.completedFuture(ActionResult.error(
                "Use 1-24 letters, numbers, underscores, or hyphens for the home name."
            ));
        }
        SelectionResult resolved = resolveAlliance(player.getUniqueId(), rawAllyTeam, true);
        if (resolved.error() != null) {
            return CompletableFuture.completedFuture(ActionResult.error(resolved.error()));
        }

        AllianceSelection alliance = resolved.selection();
        MutationKey mutation = new MutationKey(alliance.key(), name);
        if (!mutationsInProgress.add(mutation)) {
            return CompletableFuture.completedFuture(ActionResult.error(
                "That alliance home is already being updated."
            ));
        }
        if (!teams.beginAllianceHomeMutation(alliance.firstTeam(), alliance.secondTeam())) {
            mutationsInProgress.remove(mutation);
            return CompletableFuture.completedFuture(ActionResult.error(
                "One of those teams is already updating. Try again in a moment."
            ));
        }

        Location location = player.getLocation().clone();
        HomeEntry home = new HomeEntry(
            name,
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
        return plugin.getDatabase()
            .saveAllianceHome(alliance.firstTeam(), alliance.secondTeam(), home)
            .handle((saved, error) -> new DatabaseOutcome(Boolean.TRUE.equals(saved), error))
            .thenCompose(outcome -> onMainThread(() -> {
                if (outcome.error() != null) {
                    plugin.getLogger().severe("Could not save alliance home: " + rootMessage(outcome.error()));
                    return ActionResult.error("Could not save that alliance home right now.");
                }
                if (!outcome.success()
                    || !teams.areTeamsAllied(alliance.firstTeam(), alliance.secondTeam())) {
                    return ActionResult.error("That alliance ended before the home could be saved.");
                }
                homesByAlliance.computeIfAbsent(alliance.key(), ignored -> new ConcurrentHashMap<>())
                    .put(name, home);
                return ActionResult.success(
                    "Alliance home <white>" + name + "</white> set for <white>"
                        + alliance.allyTeam() + "</white>."
                );
            }))
            .whenComplete((ignored, error) -> {
                mutationsInProgress.remove(mutation);
                teams.endAllianceHomeMutation(alliance.firstTeam(), alliance.secondTeam());
            });
    }

    public CompletableFuture<ActionResult> deleteHome(Player player, String rawName, String rawAllyTeam) {
        if (player == null) {
            return CompletableFuture.completedFuture(ActionResult.error("Only players can delete alliance homes."));
        }
        TeamManager teams = plugin.getTeamManager();
        if (!teams.isTeamOwner(player.getUniqueId())) {
            return CompletableFuture.completedFuture(ActionResult.error(
                "Only your team leader can delete alliance homes."
            ));
        }

        String name = normalizeHomeName(rawName);
        HomeSelectionResult resolved = resolveHome(player.getUniqueId(), name, rawAllyTeam);
        if (resolved.error() != null) {
            return CompletableFuture.completedFuture(ActionResult.error(resolved.error()));
        }

        AllianceSelection alliance = resolved.selection();
        MutationKey mutation = new MutationKey(alliance.key(), name);
        if (!mutationsInProgress.add(mutation)) {
            return CompletableFuture.completedFuture(ActionResult.error(
                "That alliance home is already being updated."
            ));
        }
        if (!teams.beginAllianceHomeMutation(alliance.firstTeam(), alliance.secondTeam())) {
            mutationsInProgress.remove(mutation);
            return CompletableFuture.completedFuture(ActionResult.error(
                "One of those teams is already updating. Try again in a moment."
            ));
        }

        return plugin.getDatabase()
            .deleteAllianceHome(alliance.firstTeam(), alliance.secondTeam(), name)
            .handle((deleted, error) -> new DatabaseOutcome(Boolean.TRUE.equals(deleted), error))
            .thenCompose(outcome -> onMainThread(() -> {
                if (outcome.error() != null) {
                    plugin.getLogger().severe("Could not delete alliance home: " + rootMessage(outcome.error()));
                    return ActionResult.error("Could not delete that alliance home right now.");
                }
                if (!outcome.success()) {
                    removeCachedHome(alliance.key(), name);
                    return ActionResult.error("That alliance home no longer exists.");
                }
                removeCachedHome(alliance.key(), name);
                return ActionResult.success(
                    "Alliance home <white>" + name + "</white> deleted."
                );
            }))
            .whenComplete((ignored, error) -> {
                mutationsInProgress.remove(mutation);
                teams.endAllianceHomeMutation(alliance.firstTeam(), alliance.secondTeam());
            });
    }

    public void teleportHome(Player player, String rawName, String rawAllyTeam) {
        if (player == null) {
            return;
        }
        String name = normalizeHomeName(rawName);
        HomeSelectionResult resolved = resolveHome(player.getUniqueId(), name, rawAllyTeam);
        if (resolved.error() != null) {
            player.sendMessage(MessageUtil.error(resolved.error()));
            return;
        }

        AllianceSelection alliance = resolved.selection();
        HomeEntry home = resolved.home();
        UUID playerId = player.getUniqueId();
        plugin.getHomeManager().teleportSharedHome(
            player,
            home,
            "Teleported to alliance home <white>" + name + "</white> shared with <white>"
                + alliance.allyTeam() + "</white>.",
            () -> hasAccessToExactHome(playerId, alliance.key(), name, home)
        );
    }

    public void listHomes(Player player, String rawAllyTeam) {
        if (player == null) {
            return;
        }
        TeamManager teams = plugin.getTeamManager();
        String ownTeam = teams.teamDisplayName(player.getUniqueId());
        if (ownTeam == null) {
            player.sendMessage(MessageUtil.error("You are not in a team."));
            return;
        }

        List<String> allies;
        if (rawAllyTeam != null && !rawAllyTeam.isBlank()) {
            SelectionResult resolved = resolveAlliance(player.getUniqueId(), rawAllyTeam, false);
            if (resolved.error() != null) {
                player.sendMessage(MessageUtil.error(resolved.error()));
                return;
            }
            allies = List.of(resolved.selection().allyTeam());
        } else {
            allies = teams.alliedTeamNames(player.getUniqueId());
        }

        if (allies.isEmpty()) {
            player.sendMessage(MessageUtil.info("Your team has no allies."));
            return;
        }

        List<String> lines = new ArrayList<>();
        for (String ally : allies) {
            Map<String, HomeEntry> homes = homesByAlliance.get(AllianceKey.of(ownTeam, ally));
            if (homes == null || homes.isEmpty()) {
                continue;
            }
            List<String> names = new ArrayList<>(homes.keySet());
            names.sort(String.CASE_INSENSITIVE_ORDER);
            lines.add("<white>" + ally + "</white>: <aqua>" + String.join(", ", names) + "</aqua>");
        }
        if (lines.isEmpty()) {
            player.sendMessage(MessageUtil.info("Your active alliances have no shared homes."));
            return;
        }

        player.sendMessage(MessageUtil.info("<gold><bold>Alliance Homes</bold></gold>"));
        for (String line : lines) {
            player.sendMessage(MessageUtil.info(line));
        }
    }

    public List<String> cachedHomeNames(UUID playerId) {
        String ownTeam = plugin.getTeamManager().teamDisplayName(playerId);
        if (ownTeam == null) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String ally : plugin.getTeamManager().alliedTeamNames(playerId)) {
            Map<String, HomeEntry> homes = homesByAlliance.get(AllianceKey.of(ownTeam, ally));
            if (homes != null) {
                names.addAll(homes.keySet());
            }
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(sorted);
    }

    public void removeAllianceLocal(String firstTeam, String secondTeam) {
        AllianceKey key = AllianceKey.of(firstTeam, secondTeam);
        homesByAlliance.remove(key);
        mutationsInProgress.removeIf(mutation -> mutation.alliance().equals(key));
    }

    public void removeTeamLocal(String teamName) {
        String teamKey = normalizeTeam(teamName);
        homesByAlliance.keySet().removeIf(key -> key.contains(teamKey));
        mutationsInProgress.removeIf(mutation -> mutation.alliance().contains(teamKey));
    }

    public void renameTeamLocal(String oldName, String newName) {
        String oldKey = normalizeTeam(oldName);
        List<Map.Entry<AllianceKey, Map<String, HomeEntry>>> affected = homesByAlliance.entrySet().stream()
            .filter(entry -> entry.getKey().contains(oldKey))
            .toList();
        for (Map.Entry<AllianceKey, Map<String, HomeEntry>> entry : affected) {
            if (!homesByAlliance.remove(entry.getKey(), entry.getValue())) {
                continue;
            }
            String otherTeam = entry.getKey().other(oldKey);
            AllianceKey renamed = AllianceKey.of(newName, otherTeam);
            homesByAlliance.computeIfAbsent(renamed, ignored -> new ConcurrentHashMap<>())
                .putAll(entry.getValue());
        }
        mutationsInProgress.removeIf(mutation -> mutation.alliance().contains(oldKey));
    }

    public void shutdown() {
        homesByAlliance.clear();
        mutationsInProgress.clear();
    }

    static String normalizeHomeName(String rawName) {
        return rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
    }

    static boolean isValidHomeName(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_HOME_NAME_LENGTH) {
            return false;
        }
        return name.matches("[a-z0-9][a-z0-9_-]*");
    }

    private SelectionResult resolveAlliance(UUID playerId, String rawAllyTeam, boolean requireUnambiguous) {
        TeamManager teams = plugin.getTeamManager();
        String ownTeam = teams.teamDisplayName(playerId);
        if (ownTeam == null) {
            return SelectionResult.error("You are not in a team.");
        }
        List<String> allies = teams.alliedTeamNames(playerId);
        if (allies.isEmpty()) {
            return SelectionResult.error("Your team has no allies.");
        }

        String requested = rawAllyTeam == null ? "" : rawAllyTeam.trim();
        String allyTeam = null;
        if (!requested.isEmpty()) {
            for (String ally : allies) {
                if (ally.equalsIgnoreCase(requested)) {
                    allyTeam = ally;
                    break;
                }
            }
            if (allyTeam == null) {
                return SelectionResult.error("Your team is not allied with <white>" + requested + "</white>.");
            }
        } else if (allies.size() == 1) {
            allyTeam = allies.getFirst();
        } else if (requireUnambiguous) {
            return SelectionResult.error("Choose an allied team: <white>" + String.join(", ", allies) + "</white>.");
        } else {
            return SelectionResult.error("Choose which allied team to use.");
        }

        return SelectionResult.success(new AllianceSelection(
            ownTeam,
            allyTeam,
            allyTeam,
            AllianceKey.of(ownTeam, allyTeam)
        ));
    }

    private HomeSelectionResult resolveHome(UUID playerId, String name, String rawAllyTeam) {
        if (!isValidHomeName(name)) {
            return HomeSelectionResult.error("That alliance home name is invalid.");
        }

        if (rawAllyTeam != null && !rawAllyTeam.isBlank()) {
            SelectionResult selection = resolveAlliance(playerId, rawAllyTeam, true);
            if (selection.error() != null) {
                return HomeSelectionResult.error(selection.error());
            }
            HomeEntry home = home(selection.selection().key(), name);
            if (home == null) {
                return HomeSelectionResult.error(
                    "No alliance home named <white>" + name + "</white> is shared with <white>"
                        + selection.selection().allyTeam() + "</white>."
                );
            }
            return HomeSelectionResult.success(selection.selection(), home);
        }

        TeamManager teams = plugin.getTeamManager();
        String ownTeam = teams.teamDisplayName(playerId);
        if (ownTeam == null) {
            return HomeSelectionResult.error("You are not in a team.");
        }
        List<AllianceSelection> matches = new ArrayList<>();
        for (String ally : teams.alliedTeamNames(playerId)) {
            AllianceKey key = AllianceKey.of(ownTeam, ally);
            if (home(key, name) != null) {
                matches.add(new AllianceSelection(ownTeam, ally, ally, key));
            }
        }
        if (matches.isEmpty()) {
            return HomeSelectionResult.error("No active alliance home named <white>" + name + "</white>.");
        }
        if (matches.size() > 1) {
            List<String> matchingAllies = matches.stream()
                .map(AllianceSelection::allyTeam)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
            return HomeSelectionResult.error(
                "That name is shared by multiple allies. Choose one: <white>"
                    + String.join(", ", matchingAllies) + "</white>."
            );
        }
        AllianceSelection selection = matches.getFirst();
        return HomeSelectionResult.success(selection, home(selection.key(), name));
    }

    private boolean hasAccessToExactHome(UUID playerId, AllianceKey alliance, String name, HomeEntry expected) {
        String currentTeam = plugin.getTeamManager().teamDisplayName(playerId);
        if (currentTeam == null || !alliance.contains(normalizeTeam(currentTeam))) {
            return false;
        }
        String otherTeam = alliance.other(normalizeTeam(currentTeam));
        if (!plugin.getTeamManager().areTeamsAllied(currentTeam, otherTeam)) {
            return false;
        }
        return expected.equals(home(alliance, name));
    }

    private HomeEntry home(AllianceKey alliance, String name) {
        Map<String, HomeEntry> homes = homesByAlliance.get(alliance);
        return homes == null ? null : homes.get(name);
    }

    private void removeCachedHome(AllianceKey alliance, String name) {
        Map<String, HomeEntry> homes = homesByAlliance.get(alliance);
        if (homes == null) {
            return;
        }
        homes.remove(name);
        if (homes.isEmpty()) {
            homesByAlliance.remove(alliance, homes);
        }
    }

    private <T> CompletableFuture<T> onMainThread(Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                result.complete(action.get());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else {
            result.completeExceptionally(new IllegalStateException("SMPCore is disabling"));
        }
        return result;
    }

    private static String normalizeTeam(String teamName) {
        return teamName == null ? "" : teamName.trim().toLowerCase(Locale.ROOT);
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult error(String message) {
            return new ActionResult(false, message);
        }
    }

    record AllianceKey(String firstTeam, String secondTeam) {
        static AllianceKey of(String firstTeam, String secondTeam) {
            String first = normalizeTeam(firstTeam);
            String second = normalizeTeam(secondTeam);
            return first.compareTo(second) <= 0
                ? new AllianceKey(first, second)
                : new AllianceKey(second, first);
        }

        boolean valid() {
            return !firstTeam.isBlank() && !secondTeam.isBlank() && !firstTeam.equals(secondTeam);
        }

        boolean contains(String teamName) {
            return firstTeam.equals(teamName) || secondTeam.equals(teamName);
        }

        String other(String teamName) {
            if (firstTeam.equals(teamName)) {
                return secondTeam;
            }
            return firstTeam;
        }
    }

    private record MutationKey(AllianceKey alliance, String homeName) {}

    private record AllianceSelection(
        String firstTeam,
        String secondTeam,
        String allyTeam,
        AllianceKey key
    ) {}

    private record SelectionResult(AllianceSelection selection, String error) {
        static SelectionResult success(AllianceSelection selection) {
            return new SelectionResult(selection, null);
        }

        static SelectionResult error(String error) {
            return new SelectionResult(null, error);
        }
    }

    private record HomeSelectionResult(AllianceSelection selection, HomeEntry home, String error) {
        static HomeSelectionResult success(AllianceSelection selection, HomeEntry home) {
            return new HomeSelectionResult(selection, home, null);
        }

        static HomeSelectionResult error(String error) {
            return new HomeSelectionResult(null, null, error);
        }
    }

    private record DatabaseOutcome(boolean success, Throwable error) {}
}
