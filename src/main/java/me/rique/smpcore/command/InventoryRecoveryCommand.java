package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.recovery.RiskyInventoryRecoveryManager;
import me.rique.smpcore.recovery.RiskyInventoryRecoveryManager.RecoveryItem;
import me.rique.smpcore.recovery.RiskyInventoryRecoveryManager.RestoreResult;
import me.rique.smpcore.recovery.RiskyInventoryRecoveryManager.SnapshotDetail;
import me.rique.smpcore.recovery.RiskyInventoryRecoveryManager.SnapshotInfo;
import me.rique.smpcore.recovery.RiskyInventoryRecoveryManager.SnapshotResult;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("UnstableApiUsage")
public final class InventoryRecoveryCommand {

    private static final int PAGE_SIZE = 8;
    private static final long CONFIRMATION_MILLIS = 60_000L;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<String, PendingRestore> PENDING = new ConcurrentHashMap<>();

    private InventoryRecoveryCommand() { }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("itemrecovery")
                .requires(source -> source.getSender().hasPermission("smpcore.inventoryrecovery.admin"))
                .executes(context -> help(context.getSource().getSender()))
                .then(Commands.literal("list")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .executes(context -> list(plugin, context.getSource().getSender(),
                            StringArgumentType.getString(context, "player"), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(context -> list(plugin, context.getSource().getSender(),
                                StringArgumentType.getString(context, "player"),
                                IntegerArgumentType.getInteger(context, "page"))))))
                .then(Commands.literal("view")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .executes(context -> view(plugin, context.getSource().getSender(),
                            StringArgumentType.getString(context, "player"), "latest"))
                        .then(Commands.argument("snapshot", StringArgumentType.word())
                            .suggests((context, builder) -> suggestSnapshots(plugin,
                                StringArgumentType.getString(context, "player"), builder))
                            .executes(context -> view(plugin, context.getSource().getSender(),
                                StringArgumentType.getString(context, "player"),
                                StringArgumentType.getString(context, "snapshot"))))))
                .then(Commands.literal("restore")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .then(Commands.argument("snapshot", StringArgumentType.word())
                            .suggests((context, builder) -> suggestSnapshots(plugin,
                                StringArgumentType.getString(context, "player"), builder))
                            .then(Commands.argument("item", IntegerArgumentType.integer(1))
                                .executes(context -> requestRestore(plugin, context.getSource().getSender(),
                                    StringArgumentType.getString(context, "player"),
                                    StringArgumentType.getString(context, "snapshot"),
                                    IntegerArgumentType.getInteger(context, "item")))))))
                .then(Commands.literal("confirm")
                    .executes(context -> confirm(plugin, context.getSource().getSender())))
                .then(Commands.literal("cancel")
                    .executes(context -> cancel(context.getSource().getSender())))
                .build(),
            "Inspect and safely restore items recorded around risky custom inventories",
            List.of("invrecovery", "guirecovery")
        );
    }

    private static int help(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("Risky inventory recovery:"));
        sender.sendMessage(MessageUtil.info("/itemrecovery list <player> [page]"));
        sender.sendMessage(MessageUtil.info("/itemrecovery view <player> [latest|id]"));
        sender.sendMessage(MessageUtil.info("/itemrecovery restore <player> <id> <item-number>"));
        sender.sendMessage(MessageUtil.info("/itemrecovery confirm or /itemrecovery cancel"));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(SMPCore plugin, CommandSender sender, String input, int requestedPage) {
        ResolvedPlayer player = resolvePlayer(plugin, input);
        if (player == null) {
            sender.sendMessage(MessageUtil.error("Player not found. Use an exact name or UUID."));
            return 0;
        }
        List<SnapshotInfo> snapshots = plugin.getRiskyInventoryRecoveryManager().listSnapshots(player.uuid());
        if (snapshots.isEmpty()) {
            sender.sendMessage(MessageUtil.info("No risky-inventory snapshots exist for <white>" + player.name() + "</white>."));
            return Command.SINGLE_SUCCESS;
        }
        int pages = Math.max(1, (snapshots.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(snapshots.size(), from + PAGE_SIZE);
        sender.sendMessage(MessageUtil.info("Inventory snapshots for <white>" + player.name() + "</white>, page <white>"
            + page + "/" + pages + "</white>:"));
        for (SnapshotInfo snapshot : snapshots.subList(from, to)) {
            sender.sendMessage(MessageUtil.info("<white>" + snapshot.shortId() + "</white>  "
                + Instant.ofEpochMilli(snapshot.createdAt()) + "  <yellow>" + snapshot.surface() + "</yellow>  "
                + snapshot.availableCount() + "/" + snapshot.itemCount() + " available"));
        }
        sender.sendMessage(MessageUtil.info("Use <white>/itemrecovery view " + player.name() + " <id></white>."));
        return Command.SINGLE_SUCCESS;
    }

    private static int view(SMPCore plugin, CommandSender sender, String input, String selector) {
        ResolvedPlayer player = resolvePlayer(plugin, input);
        if (player == null) {
            sender.sendMessage(MessageUtil.error("Player not found. Use an exact name or UUID."));
            return 0;
        }
        SnapshotResult result = plugin.getRiskyInventoryRecoveryManager().findSnapshot(player.uuid(), selector);
        SnapshotDetail snapshot = result.snapshot();
        if (snapshot == null) {
            sender.sendMessage(MessageUtil.error(result.error()));
            return 0;
        }
        SnapshotInfo info = snapshot.info();
        sender.sendMessage(MessageUtil.info("Snapshot <white>" + info.shortId() + "</white> for <white>" + player.name() + "</white>:"));
        sender.sendMessage(MessageUtil.info("<yellow>" + info.surface() + "</yellow>  "
            + Instant.ofEpochMilli(info.createdAt()) + "  <gray>" + MM.escapeTags(info.reason()) + "</gray>"));
        for (RecoveryItem item : snapshot.items()) {
            String name = item.item() == null ? "invalid item" : RiskyInventoryRecoveryManager.displayName(item.item());
            int amount = item.item() == null ? 0 : item.item().getAmount();
            sender.sendMessage(MessageUtil.info("<white>#" + item.number() + "</white>  " + amount + "x "
                + MM.escapeTags(name) + "  <gray>" + item.source() + " slot " + item.slot() + "</gray>  <yellow>"
                + RiskyInventoryRecoveryManager.stateName(item.state()) + "</yellow>"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int requestRestore(SMPCore plugin, CommandSender sender, String input, String selector, int itemNumber) {
        cleanupExpired();
        ResolvedPlayer resolved = resolvePlayer(plugin, input);
        if (resolved == null) {
            sender.sendMessage(MessageUtil.error("Player not found. Use an exact name or UUID."));
            return 0;
        }
        Player target = Bukkit.getPlayer(resolved.uuid());
        if (target == null || !target.isOnline()) {
            sender.sendMessage(MessageUtil.error("The player must be online for a safe restore."));
            return 0;
        }
        SnapshotResult result = plugin.getRiskyInventoryRecoveryManager().findSnapshot(resolved.uuid(), selector);
        if (result.snapshot() == null) {
            sender.sendMessage(MessageUtil.error(result.error()));
            return 0;
        }
        RecoveryItem selected = result.snapshot().items().stream()
            .filter(item -> item.number() == itemNumber).findFirst().orElse(null);
        if (selected == null || selected.item() == null) {
            sender.sendMessage(MessageUtil.error("That item number is invalid."));
            return 0;
        }
        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(selected.item())) {
            sender.sendMessage(MessageUtil.error("Use /backpackadmin for backpacks so their storage ID stays unique."));
            return 0;
        }
        if (!"available".equals(RiskyInventoryRecoveryManager.stateName(selected.state()))) {
            sender.sendMessage(MessageUtil.error("That recovery item is not available."));
            return 0;
        }
        if (PENDING.values().stream().anyMatch(value -> value.targetId().equals(resolved.uuid()))) {
            sender.sendMessage(MessageUtil.error("Another staff member already has a pending recovery for this player."));
            return 0;
        }
        PENDING.put(senderKey(sender), new PendingRestore(resolved.uuid(), resolved.name(),
            result.snapshot().info().shortId(), itemNumber, System.currentTimeMillis() + CONFIRMATION_MILLIS));
        sender.sendMessage(MessageUtil.warn("Ready to restore <white>" + selected.item().getAmount() + "x "
            + MM.escapeTags(RiskyInventoryRecoveryManager.displayName(selected.item())) + "</white> from <yellow>"
            + result.snapshot().info().surface() + "</yellow> to <white>" + resolved.name() + "</white>."));
        sender.sendMessage(MessageUtil.warn("Confirm only after checking that this item is actually missing. The record is single-use."));
        sender.sendMessage(MessageUtil.warn("Run /itemrecovery confirm within 60 seconds, or /itemrecovery cancel."));
        return Command.SINGLE_SUCCESS;
    }

    private static int confirm(SMPCore plugin, CommandSender sender) {
        cleanupExpired();
        PendingRestore pending = PENDING.remove(senderKey(sender));
        if (pending == null) {
            sender.sendMessage(MessageUtil.error("You do not have a pending item recovery."));
            return 0;
        }
        if (pending.expiresAt() <= System.currentTimeMillis()) {
            sender.sendMessage(MessageUtil.error("That recovery expired. Start it again."));
            return 0;
        }
        Player target = Bukkit.getPlayer(pending.targetId());
        if (target == null || !target.isOnline()) {
            sender.sendMessage(MessageUtil.error("The player went offline. Nothing was changed."));
            return 0;
        }
        RestoreResult result = plugin.getRiskyInventoryRecoveryManager().restore(target, pending.snapshotId(), pending.itemNumber());
        if (!result.restored()) {
            sender.sendMessage(MessageUtil.error(result.message()));
            return 0;
        }
        target.sendMessage(MessageUtil.success("Staff restored a verified missing item from inventory recovery."));
        sender.sendMessage(MessageUtil.success(result.message()));
        return Command.SINGLE_SUCCESS;
    }

    private static int cancel(CommandSender sender) {
        PendingRestore removed = PENDING.remove(senderKey(sender));
        sender.sendMessage(removed == null ? MessageUtil.info("You did not have a pending item recovery.")
            : MessageUtil.success("Item recovery cancelled. Nothing was changed."));
        return Command.SINGLE_SUCCESS;
    }

    private static ResolvedPlayer resolvePlayer(SMPCore plugin, String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        try {
            UUID id = UUID.fromString(trimmed);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
            return new ResolvedPlayer(id, offline.getName() == null ? id.toString() : offline.getName());
        } catch (IllegalArgumentException ignored) { }
        Player online = Bukkit.getPlayerExact(trimmed);
        if (online == null) online = Bukkit.getPlayer(trimmed);
        if (online != null) return new ResolvedPlayer(online.getUniqueId(), online.getName());
        OfflinePlayer offline = Bukkit.getOfflinePlayer(trimmed);
        if (!offline.hasPlayedBefore() && plugin.getRiskyInventoryRecoveryManager().listSnapshots(offline.getUniqueId()).isEmpty()) return null;
        return new ResolvedPlayer(offline.getUniqueId(), offline.getName() == null ? trimmed : offline.getName());
    }

    private static CompletableFuture<Suggestions> suggestPlayers(SuggestionsBuilder builder) {
        Bukkit.getOnlinePlayers().forEach(player -> builder.suggest(player.getName()));
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestSnapshots(SMPCore plugin, String input, SuggestionsBuilder builder) {
        ResolvedPlayer player = resolvePlayer(plugin, input);
        if (player == null) return builder.suggest("latest").buildFuture();
        builder.suggest("latest");
        plugin.getRiskyInventoryRecoveryManager().listSnapshots(player.uuid()).stream().limit(12)
            .map(SnapshotInfo::shortId).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private static String senderKey(CommandSender sender) {
        return sender instanceof Player player ? "player:" + player.getUniqueId()
            : "sender:" + sender.getName().toLowerCase(Locale.ROOT);
    }

    private record ResolvedPlayer(UUID uuid, String name) { }
    private record PendingRestore(UUID targetId, String targetName, String snapshotId, int itemNumber, long expiresAt) { }
}
