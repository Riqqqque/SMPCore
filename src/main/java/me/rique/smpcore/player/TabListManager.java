package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.quest.OverseerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Owns tab-list names, ordering, and the server information panel. */
public final class TabListManager implements Listener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final TextColor BRAND_PURPLE = TextColor.color(0xA78BFA);
    private static final TextColor BRAND_AQUA = TextColor.color(0x67E8F9);
    private static final int MAX_TAB_NAME_LENGTH = 20;

    private final SMPCore plugin;
    private final Map<UUID, Component> lastRows = new HashMap<>();
    private final Map<UUID, Integer> lastOrders = new HashMap<>();
    private final Map<UUID, PlayerPanel> lastPanels = new HashMap<>();
    private BukkitTask refreshTask;
    private boolean refreshQueued;

    public TabListManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        restartTask();
        requestRefresh();
    }

    public void reloadConfig() {
        restartTask();
        requestRefresh();
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        refreshQueued = false;
        lastRows.clear();
        lastOrders.clear();
        lastPanels.clear();
    }

    /** Coalesces any number of state changes into one main-thread refresh. */
    public void requestRefresh() {
        if (!plugin.isEnabled()) return;
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::requestRefresh);
            return;
        }
        if (refreshQueued) return;
        refreshQueued = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshQueued = false;
            refreshNow();
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        requestRefresh();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastRows.remove(playerId);
        lastOrders.remove(playerId);
        lastPanels.remove(playerId);
        requestRefresh();
    }

    private void restartTask() {
        if (refreshTask != null) refreshTask.cancel();
        int period = plugin.getConfigManager().tabListRefreshTicks;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshNow, 1L, period);
    }

    private void refreshNow() {
        if (!plugin.isEnabled()) return;

        List<TabEntry> entries = Bukkit.getOnlinePlayers().stream()
            .filter(Player::isOnline)
            .map(this::entryFor)
            .sorted(ENTRY_ORDER)
            .toList();
        Set<UUID> onlineIds = new HashSet<>();
        Component header = header(entries.size());

        for (int index = 0; index < entries.size(); index++) {
            TabEntry entry = entries.get(index);
            Player player = entry.player();
            UUID playerId = player.getUniqueId();
            onlineIds.add(playerId);

            Component row = row(entry);
            if (!Objects.equals(row, lastRows.put(playerId, row))) {
                player.playerListName(row);
            }
            if (!Objects.equals(index, lastOrders.put(playerId, index))) {
                player.setPlayerListOrder(index);
            }

            Component footer = footer(entry);
            PlayerPanel panel = new PlayerPanel(header, footer);
            if (!Objects.equals(panel, lastPanels.put(playerId, panel))) {
                player.sendPlayerListHeaderAndFooter(header, footer);
            }
        }

        lastRows.keySet().removeIf(id -> !onlineIds.contains(id));
        lastOrders.keySet().removeIf(id -> !onlineIds.contains(id));
        lastPanels.keySet().removeIf(id -> !onlineIds.contains(id));
    }

    private TabEntry entryFor(Player player) {
        OverseerManager overseer = plugin.getOverseerManager();
        int authority = overseer == null ? 0 : overseer.authority(player);
        OverseerManager.AuthorityRank authorityRank = OverseerManager.authorityRankFor(authority);
        StaffTitle staffTitle = staffTitle(player);
        Component teamLabel = plugin.getTeamManager() == null ? null : plugin.getTeamManager().tabTeamLabel(player.getUniqueId());
        return new TabEntry(player, staffTitle, authorityRank, authority, teamLabel);
    }

    private StaffTitle staffTitle(Player player) {
        Set<UUID> configuredOwners = plugin.getConfigManager().tabListOwnerUuids;
        boolean owner = configuredOwners.contains(player.getUniqueId());
        if (!owner && configuredOwners.isEmpty() && plugin.getLaunchAccessManager() != null) {
            owner = plugin.getLaunchAccessManager().allowedPlayerUuids().contains(player.getUniqueId());
        }
        if (owner || player.hasPermission("smpcore.tab.title.owner")) return StaffTitle.OWNER;
        if (player.hasPermission("smpcore.tab.title.admin")) return StaffTitle.ADMIN;
        if (player.hasPermission("smpcore.tab.title.moderator")) return StaffTitle.MODERATOR;
        if (player.hasPermission("smpcore.tab.title.builder")) return StaffTitle.BUILDER;
        return StaffTitle.MEMBER;
    }

    private Component header(int online) {
        double tps = currentTps();
        NamedTextColor tpsColor = tps >= 19.0D ? NamedTextColor.GREEN : tps >= 17.0D ? NamedTextColor.YELLOW : NamedTextColor.RED;
        return Component.newline()
            .append(Component.text("✦ " + plugin.getConfigManager().tabListServerTitle + " ✦", BRAND_PURPLE, TextDecoration.BOLD))
            .append(Component.newline())
            .append(Component.text(plugin.getConfigManager().tabListSeasonTitle, BRAND_AQUA))
            .append(Component.newline())
            .append(Component.text("Online ", NamedTextColor.GRAY))
            .append(Component.text(online + "/" + Bukkit.getMaxPlayers(), NamedTextColor.WHITE))
            .append(Component.text("  •  TPS ", NamedTextColor.DARK_GRAY))
            .append(Component.text(formatTps(tps), tpsColor))
            .append(Component.newline());
    }

    private Component footer(TabEntry entry) {
        Player player = entry.player();
        Component team = entry.teamLabel() == null ? Component.text("None", NamedTextColor.GRAY) : entry.teamLabel();
        NamedTextColor pingColor = player.getPing() <= 80 ? NamedTextColor.GREEN : player.getPing() <= 160 ? NamedTextColor.YELLOW : NamedTextColor.RED;

        TextComponent.Builder firstLine = Component.text().append(Component.text("Rank ", NamedTextColor.GRAY));
        if (entry.staffTitle() == StaffTitle.MEMBER) {
            firstLine.append(Component.text(entry.authorityRank().displayName(), authorityColor(entry.authorityRank())));
        } else {
            firstLine.append(Component.text(entry.staffTitle().label, entry.staffTitle().color));
            if (hasEarnedAuthorityRank(entry.authority())) {
                firstLine.append(Component.text(" + ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(entry.authorityRank().displayName(), authorityColor(entry.authorityRank())));
            }
        }

        return Component.newline()
            .append(firstLine.build())
            .append(Component.text("  •  Authority ", NamedTextColor.DARK_GRAY))
            .append(Component.text(entry.authority(), NamedTextColor.GOLD))
            .append(Component.text("  •  Team ", NamedTextColor.DARK_GRAY))
            .append(team)
            .append(Component.newline())
            .append(Component.text("Ping ", NamedTextColor.GRAY))
            .append(Component.text(player.getPing() + "ms", pingColor))
            .append(Component.text("  •  " + plugin.getConfigManager().tabListFooterHint, NamedTextColor.DARK_GRAY))
            .append(Component.newline());
    }

    private Component row(TabEntry entry) {
        TextComponent.Builder row = Component.text();
        if (entry.staffTitle() == StaffTitle.MEMBER) {
            row.append(badge(entry.authorityRank().displayName(), authorityColor(entry.authorityRank())));
        } else {
            row.append(badge(entry.staffTitle().label, entry.staffTitle().color));
        }
        row.append(Component.space())
            .append(Component.text(compactPlayerName(entry.player()), NamedTextColor.WHITE));
        if (entry.staffTitle() != StaffTitle.MEMBER && hasEarnedAuthorityRank(entry.authority())) {
            row.append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(entry.authorityRank().displayName(), authorityColor(entry.authorityRank())));
        }
        if (entry.teamLabel() != null) {
            row.append(Component.text("  •  ", NamedTextColor.DARK_GRAY)).append(entry.teamLabel());
        }
        return row.build();
    }

    private static Component badge(String label, TextColor color) {
        return Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text(label.toUpperCase(Locale.ROOT), color, TextDecoration.BOLD))
            .append(Component.text("]", NamedTextColor.DARK_GRAY));
    }

    private static String compactPlayerName(Player player) {
        Component displayName = player.displayName();
        String plain = displayName == null ? player.getName() : PLAIN.serialize(displayName);
        plain = plain.replaceAll("\\s+", " ").trim();
        if (plain.isBlank()) plain = player.getName();
        return plain.codePoints().limit(MAX_TAB_NAME_LENGTH)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }

    static double currentTps() {
        double[] samples = Bukkit.getServer().getTPS();
        return samples.length == 0 || !Double.isFinite(samples[0]) ? 20.0D : Math.max(0.0D, Math.min(20.0D, samples[0]));
    }

    static String formatTps(double tps) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0D, Math.min(20.0D, tps)));
    }

    static int authoritySort(OverseerManager.AuthorityRank rank) {
        return switch (rank) {
            case SEASON_WARDEN -> 0;
            case VEIL_MARSHAL -> 1;
            case VEIL_DEPUTY -> 2;
            case VEILMARKED -> 3;
        };
    }

    static boolean hasEarnedAuthorityRank(int authority) {
        return authority > 0;
    }

    private static TextColor authorityColor(OverseerManager.AuthorityRank rank) {
        return switch (rank) {
            case SEASON_WARDEN -> NamedTextColor.GOLD;
            case VEIL_MARSHAL -> NamedTextColor.AQUA;
            case VEIL_DEPUTY -> NamedTextColor.GREEN;
            case VEILMARKED -> NamedTextColor.GRAY;
        };
    }

    private static final Comparator<TabEntry> ENTRY_ORDER = Comparator
        .comparingInt((TabEntry entry) -> entry.staffTitle().sortOrder)
        .thenComparingInt(entry -> authoritySort(entry.authorityRank()))
        .thenComparing(Comparator.comparingInt(TabEntry::authority).reversed())
        .thenComparing(entry -> entry.player().getName(), String.CASE_INSENSITIVE_ORDER);

    private enum StaffTitle {
        OWNER("Owner", NamedTextColor.GOLD, 0),
        ADMIN("Admin", NamedTextColor.RED, 10),
        MODERATOR("Moderator", NamedTextColor.LIGHT_PURPLE, 20),
        BUILDER("Builder", NamedTextColor.AQUA, 30),
        MEMBER("Member", NamedTextColor.WHITE, 100);

        private final String label;
        private final NamedTextColor color;
        private final int sortOrder;

        StaffTitle(String label, NamedTextColor color, int sortOrder) {
            this.label = label;
            this.color = color;
            this.sortOrder = sortOrder;
        }
    }

    private record TabEntry(
        Player player,
        StaffTitle staffTitle,
        OverseerManager.AuthorityRank authorityRank,
        int authority,
        Component teamLabel
    ) { }

    private record PlayerPanel(Component header, Component footer) { }
}
