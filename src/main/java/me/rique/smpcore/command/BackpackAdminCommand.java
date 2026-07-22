package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.backpack.BackpackListener;
import me.rique.smpcore.backpack.BackpackListener.BackpackRestoreResult;
import me.rique.smpcore.backpack.BackpackListener.BackpackSnapshotInfo;
import me.rique.smpcore.backpack.BackpackListener.BackpackSnapshotLookup;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("UnstableApiUsage")
public final class BackpackAdminCommand {

    private static final int PAGE_SIZE = 8;
    private static final int VIEW_ITEM_TYPES = 20;
    private static final long CONFIRMATION_MILLIS = 60_000L;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<String, PendingRestore> PENDING_RESTORES = new ConcurrentHashMap<>();

    private BackpackAdminCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("backpackadmin")
                .requires(source -> source.getSender().hasPermission("smpcore.backpack.admin"))
                .executes(context -> help(context.getSource().getSender()))
                .then(Commands.literal("list")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .executes(context -> list(
                            plugin,
                            context.getSource().getSender(),
                            StringArgumentType.getString(context, "player"),
                            1
                        ))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(context -> list(
                                plugin,
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "player"),
                                IntegerArgumentType.getInteger(context, "page")
                            )))))
                .then(Commands.literal("view")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .executes(context -> view(
                            plugin,
                            context.getSource().getSender(),
                            StringArgumentType.getString(context, "player"),
                            "latest"
                        ))
                        .then(Commands.argument("snapshot", StringArgumentType.word())
                            .suggests((context, builder) -> suggestSnapshots(
                                plugin,
                                StringArgumentType.getString(context, "player"),
                                builder
                            ))
                            .executes(context -> view(
                                plugin,
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "player"),
                                StringArgumentType.getString(context, "snapshot")
                            )))))
                .then(Commands.literal("restore")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .executes(context -> requestRestore(
                            plugin,
                            context.getSource().getSender(),
                            StringArgumentType.getString(context, "player"),
                            "latest"
                        ))
                        .then(Commands.argument("snapshot", StringArgumentType.word())
                            .suggests((context, builder) -> suggestSnapshots(
                                plugin,
                                StringArgumentType.getString(context, "player"),
                                builder
                            ))
                            .executes(context -> requestRestore(
                                plugin,
                                context.getSource().getSender(),
                                StringArgumentType.getString(context, "player"),
                                StringArgumentType.getString(context, "snapshot")
                            )))))
                .then(Commands.literal("confirm")
                    .executes(context -> confirm(plugin, context.getSource().getSender())))
                .then(Commands.literal("cancel")
                    .executes(context -> cancel(context.getSource().getSender())))
                .build(),
            "Inspect and safely restore bounded backpack snapshots",
            List.of("backpackrestore", "bprestore")
        );
    }

    private static int help(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("Backpack recovery snapshots:"));
        sender.sendMessage(MessageUtil.info("/backpackadmin list <player> [page]"));
        sender.sendMessage(MessageUtil.info("/backpackadmin view <player> [latest|id]"));
        sender.sendMessage(MessageUtil.info("/backpackadmin restore <player> [latest|id]"));
        sender.sendMessage(MessageUtil.info("/backpackadmin confirm or /backpackadmin cancel"));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(SMPCore plugin, CommandSender sender, String input, int requestedPage) {
        ResolvedPlayer resolved = resolvePlayer(plugin, input);
        if (resolved == null) {
            sender.sendMessage(MessageUtil.error("Player not found. Use an exact name or UUID."));
            return 0;
        }
        List<BackpackSnapshotInfo> snapshots = plugin.getBackpackListener().listRecoverySnapshots(resolved.uuid());
        if (snapshots.isEmpty()) {
            sender.sendMessage(MessageUtil.info("No backpack recovery snapshots exist for <white>" + resolved.name() + "</white>."));
            return Command.SINGLE_SUCCESS;
        }
        int pages = Math.max(1, (snapshots.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(snapshots.size(), from + PAGE_SIZE);
        sender.sendMessage(MessageUtil.info(
            "Backpack snapshots for <white>" + resolved.name() + "</white>, page <white>" + page + "/" + pages + "</white>:"
        ));
        for (BackpackSnapshotInfo snapshot : snapshots.subList(from, to)) {
            sender.sendMessage(MessageUtil.info(
                "<white>" + snapshot.id() + "</white>  " + Instant.ofEpochMilli(snapshot.savedAt())
                    + "  <yellow>" + snapshot.occupiedSlots() + " slots / " + snapshot.totalItems() + " items</yellow>"
                    + "  bag <gray>" + shortBackpackId(snapshot.backpackId()) + "</gray>"
            ));
        }
        sender.sendMessage(MessageUtil.info("Use <white>/backpackadmin view " + resolved.name() + " <id></white> for contents."));
        return Command.SINGLE_SUCCESS;
    }

    private static int view(SMPCore plugin, CommandSender sender, String input, String selector) {
        ResolvedPlayer resolved = resolvePlayer(plugin, input);
        if (resolved == null) {
            sender.sendMessage(MessageUtil.error("Player not found. Use an exact name or UUID."));
            return 0;
        }
        BackpackSnapshotLookup lookup = plugin.getBackpackListener().findRecoverySnapshot(resolved.uuid(), selector);
        BackpackSnapshotInfo snapshot = lookup.snapshot();
        if (snapshot == null) {
            sender.sendMessage(MessageUtil.error(lookup.error()));
            return 0;
        }
        sender.sendMessage(MessageUtil.info(
            "Snapshot <white>" + snapshot.id() + "</white> for <white>" + resolved.name() + "</white>:"
        ));
        sender.sendMessage(MessageUtil.info(
            "Saved: <white>" + Instant.ofEpochMilli(snapshot.savedAt()) + "</white>  Backpack: <white>"
                + snapshot.backpackId() + "</white>"
        ));
        sender.sendMessage(MessageUtil.info(
            "Contents: <white>" + snapshot.occupiedSlots() + " occupied slots / " + snapshot.totalItems() + " total items</white>"
        ));
        List<String> itemLines = snapshot.itemLines();
        int shown = Math.min(VIEW_ITEM_TYPES, itemLines.size());
        for (int index = 0; index < shown; index++) {
            sender.sendMessage(MessageUtil.info(" - <white>" + MM.escapeTags(itemLines.get(index)) + "</white>"));
        }
        if (itemLines.size() > shown) {
            sender.sendMessage(MessageUtil.info(" - and <white>" + (itemLines.size() - shown) + "</white> more item types"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int requestRestore(SMPCore plugin, CommandSender sender, String input, String selector) {
        cleanupExpired();
        ResolvedPlayer resolved = resolvePlayer(plugin, input);
        if (resolved == null) {
            sender.sendMessage(MessageUtil.error("Player not found. Use an exact name or UUID."));
            return 0;
        }
        Player target = Bukkit.getPlayer(resolved.uuid());
        if (target == null || !target.isOnline()) {
            sender.sendMessage(MessageUtil.error("The player must be online to restore a backpack safely."));
            return 0;
        }
        BackpackSnapshotLookup lookup = plugin.getBackpackListener().findRecoverySnapshot(resolved.uuid(), selector);
        BackpackSnapshotInfo snapshot = lookup.snapshot();
        if (snapshot == null) {
            sender.sendMessage(MessageUtil.error(lookup.error()));
            return 0;
        }
        if (PENDING_RESTORES.values().stream().anyMatch(pending -> pending.targetId().equals(resolved.uuid()))) {
            sender.sendMessage(MessageUtil.error("Another staff member already has a pending restore for this player."));
            return 0;
        }
        PENDING_RESTORES.put(
            senderKey(sender),
            new PendingRestore(
                resolved.uuid(),
                resolved.name(),
                snapshot.id(),
                snapshot.backpackId(),
                System.currentTimeMillis() + CONFIRMATION_MILLIS
            )
        );
        sender.sendMessage(MessageUtil.warn(
            "Ready to restore backpack " + shortBackpackId(snapshot.backpackId()) + " for " + resolved.name()
                + " from snapshot " + snapshot.id() + " (" + snapshot.occupiedSlots() + " slots / "
                + snapshot.totalItems() + " items)."
        ));
        sender.sendMessage(MessageUtil.warn(
            "The matching backpack must remain in their inventory or ender chest. Its current state will be backed up first."
        ));
        sender.sendMessage(MessageUtil.warn("Run /backpackadmin confirm within 60 seconds, or /backpackadmin cancel."));
        return Command.SINGLE_SUCCESS;
    }

    private static int confirm(SMPCore plugin, CommandSender sender) {
        cleanupExpired();
        PendingRestore pending = PENDING_RESTORES.remove(senderKey(sender));
        if (pending == null) {
            sender.sendMessage(MessageUtil.error("You do not have a pending backpack restore."));
            return 0;
        }
        if (pending.expiresAt() <= System.currentTimeMillis()) {
            sender.sendMessage(MessageUtil.error("That backpack restore expired. Start it again."));
            return 0;
        }
        Player target = Bukkit.getPlayer(pending.targetId());
        if (target == null || !target.isOnline()) {
            sender.sendMessage(MessageUtil.error("The player went offline. Nothing was changed."));
            return 0;
        }
        BackpackSnapshotLookup current = plugin.getBackpackListener()
            .findRecoverySnapshot(pending.targetId(), pending.snapshotId());
        if (current.snapshot() == null || !pending.backpackId().equals(current.snapshot().backpackId())) {
            sender.sendMessage(MessageUtil.error("The selected snapshot changed or no longer exists. Nothing was changed."));
            return 0;
        }
        BackpackRestoreResult result = plugin.getBackpackListener().restoreRecoverySnapshot(target, pending.snapshotId());
        if (!result.restored()) {
            sender.sendMessage(MessageUtil.error(result.message()));
            return 0;
        }
        target.sendMessage(MessageUtil.success("Staff restored your backpack contents from a verified recovery snapshot."));
        sender.sendMessage(MessageUtil.success(
            "Restored <white>" + pending.targetName() + "</white>'s backpack. " + result.message()
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int cancel(CommandSender sender) {
        PendingRestore removed = PENDING_RESTORES.remove(senderKey(sender));
        sender.sendMessage(removed == null
            ? MessageUtil.info("You did not have a pending backpack restore.")
            : MessageUtil.success("Backpack restore cancelled. Nothing was changed."));
        return Command.SINGLE_SUCCESS;
    }

    private static ResolvedPlayer resolvePlayer(SMPCore plugin, String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        try {
            UUID id = UUID.fromString(trimmed);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
            return new ResolvedPlayer(id, offline.getName() == null ? id.toString() : offline.getName());
        } catch (IllegalArgumentException ignored) {
            // Name lookup below.
        }
        Player online = Bukkit.getPlayerExact(trimmed);
        if (online == null) {
            online = Bukkit.getPlayer(trimmed);
        }
        if (online != null) {
            return new ResolvedPlayer(online.getUniqueId(), online.getName());
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(trimmed);
        if (!offline.hasPlayedBefore() && plugin.getBackpackListener().listRecoverySnapshots(offline.getUniqueId()).isEmpty()) {
            return null;
        }
        return new ResolvedPlayer(offline.getUniqueId(), offline.getName() == null ? trimmed : offline.getName());
    }

    private static CompletableFuture<Suggestions> suggestPlayers(SuggestionsBuilder builder) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            builder.suggest(player.getName());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestSnapshots(
        SMPCore plugin,
        String playerInput,
        SuggestionsBuilder builder
    ) {
        ResolvedPlayer player = resolvePlayer(plugin, playerInput);
        if (player == null) {
            return builder.suggest("latest").buildFuture();
        }
        builder.suggest("latest");
        plugin.getBackpackListener().listRecoverySnapshots(player.uuid()).stream()
            .limit(12)
            .map(BackpackSnapshotInfo::id)
            .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        PENDING_RESTORES.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private static String senderKey(CommandSender sender) {
        if (sender instanceof Player player) {
            return "player:" + player.getUniqueId();
        }
        return "sender:" + sender.getName().toLowerCase(Locale.ROOT);
    }

    private static String shortBackpackId(String id) {
        return id == null ? "unknown" : id.substring(0, Math.min(8, id.length()));
    }

    private record ResolvedPlayer(UUID uuid, String name) { }

    private record PendingRestore(
        UUID targetId,
        String targetName,
        String snapshotId,
        String backpackId,
        long expiresAt
    ) { }
}
