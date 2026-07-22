package me.rique.smpcore.death;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.combat.CombatLogListener;
import me.rique.smpcore.death.DeathInventoryCodec.InventoryPayload;
import me.rique.smpcore.death.DeathInventoryRepository.ResolvedPlayer;
import me.rique.smpcore.death.DeathInventoryRepository.SnapshotHandle;
import me.rique.smpcore.death.DeathInventoryRepository.SnapshotLookup;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathInventoryManager implements Listener {

    private static final int LIST_PAGE_SIZE = 8;
    private static final long CONFIRMATION_LIFETIME_MILLIS = 60_000L;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final SMPCore plugin;
    private final DeathInventoryRepository repository;
    private final Map<UUID, PendingDeath> pendingDeaths = new ConcurrentHashMap<>();
    private final Map<String, PendingRestore> pendingRestores = new ConcurrentHashMap<>();

    public DeathInventoryManager(SMPCore plugin) {
        this.plugin = plugin;
        this.repository = new DeathInventoryRepository(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void captureBeforeDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isDuelParticipant(player)) {
            pendingDeaths.remove(player.getUniqueId());
            return;
        }
        try {
            pendingDeaths.put(
                player.getUniqueId(),
                new PendingDeath(
                    DeathInventoryCodec.capture(player),
                    DeathInventoryCodec.countStacks(event.getDrops()),
                    DeathInventoryCodec.countItems(event.getDrops()),
                    event.getKeepInventory(),
                    DeathInventoryCodec.countItems(event.getItemsToKeep())
                )
            );
        } catch (RuntimeException ex) {
            pendingDeaths.remove(player.getUniqueId());
            plugin.getLogger().severe("Could not capture the pre-death inventory for " + player.getName() + ": " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void persistConfirmedDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        PendingDeath pending = pendingDeaths.remove(player.getUniqueId());
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isDuelParticipant(player)) {
            clearRecentChestAudit(player.getUniqueId());
            return;
        }
        if (event.isCancelled()) {
            clearRecentChestAudit(player.getUniqueId());
            return;
        }

        try {
            InventoryPayload inventory = pending == null ? DeathInventoryCodec.capture(player) : pending.inventory();
            saveDeathSnapshot(event, pending, inventory);
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not save the death inventory record for " + player.getName() + ": " + ex.getMessage());
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Death inventory record failure", ex);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void reconcileOnJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> reconcileInterruptedRestores(player));
    }

    public void shutdown() {
        pendingDeaths.clear();
        pendingRestores.clear();
    }

    public void sendHelp(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(MessageUtil.info("Death inventory recovery:"));
        sender.sendMessage(MessageUtil.info("/deathinventory list <player> [page]"));
        sender.sendMessage(MessageUtil.info("/deathinventory view <player> [latest|id]"));
        sender.sendMessage(MessageUtil.info("/deathinventory restore <player> [latest|id]"));
        sender.sendMessage(MessageUtil.info("/deathinventory confirm or /deathinventory cancel"));
    }

    public void listSnapshots(org.bukkit.command.CommandSender sender, String playerInput, int requestedPage) {
        ResolvedPlayer resolved = repository.resolvePlayer(playerInput);
        if (resolved == null) {
            sender.sendMessage(MessageUtil.error("Player not found. Use an exact name or UUID."));
            return;
        }

        List<SnapshotHandle> snapshots = repository.loadDeathSnapshots(resolved.uuid());
        if (snapshots.isEmpty()) {
            sender.sendMessage(MessageUtil.info("No death inventory records exist for <white>" + resolved.name() + "</white>."));
            return;
        }

        int pages = Math.max(1, (snapshots.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int from = (page - 1) * LIST_PAGE_SIZE;
        int to = Math.min(snapshots.size(), from + LIST_PAGE_SIZE);
        sender.sendMessage(MessageUtil.info(
            "Death inventories for <white>" + resolved.name() + "</white> — page <white>" + page + "/" + pages + "</white>:"
        ));
        for (SnapshotHandle snapshot : snapshots.subList(from, to)) {
            sender.sendMessage(MessageUtil.info(
                "<white>" + DeathInventoryPolicy.shortId(snapshot.id()) + "</white>  " + snapshot.createdAtUtc()
                    + "  <yellow>" + snapshot.state() + "</yellow>  " + snapshot.itemCount() + " items  " + snapshot.cause()
            ));
        }
        sender.sendMessage(MessageUtil.info("Use <white>/deathinventory view " + resolved.name() + " <id></white> for details."));
    }

    public void viewSnapshot(org.bukkit.command.CommandSender sender, String playerInput, String selector) {
        ResolvedPlayer resolved = repository.resolvePlayer(playerInput);
        if (resolved == null) {
            sender.sendMessage(MessageUtil.error("Player not found. Use an exact name or UUID."));
            return;
        }
        SnapshotLookup lookup = repository.findDeathSnapshot(resolved.uuid(), selector);
        if (lookup.snapshot() == null) {
            sender.sendMessage(MessageUtil.error(lookup.error()));
            return;
        }

        SnapshotHandle snapshot = lookup.snapshot();
        YamlConfiguration data = snapshot.data();
        sender.sendMessage(MessageUtil.info("Death inventory <white>" + DeathInventoryPolicy.shortId(snapshot.id()) + "</white>:"));
        sender.sendMessage(MessageUtil.info(
            "Player: <white>" + data.getString("player.name", resolved.name()) + "</white>  State: <yellow>" + snapshot.state() + "</yellow>"
        ));
        sender.sendMessage(MessageUtil.info("When: <white>" + snapshot.createdAtUtc() + "</white>"));
        sender.sendMessage(MessageUtil.info(
            "Where: <white>" + data.getString("death.location.world-name", "unknown") + " "
                + formatCoordinate(data.getDouble("death.location.x")) + ", "
                + formatCoordinate(data.getDouble("death.location.y")) + ", "
                + formatCoordinate(data.getDouble("death.location.z")) + "</white>"
        ));
        sender.sendMessage(MessageUtil.info(
            "Cause: <white>" + snapshot.cause() + "</white>  Damage: <white>"
                + data.getString("death.damage-type", "unknown") + "</white>"
        ));
        sender.sendMessage(MessageUtil.info(
            "By: <white>" + MM.escapeTags(data.getString("death.causing-entity.summary", "none")) + "</white>"
        ));
        sender.sendMessage(MessageUtil.info(
            "Inventory: <white>" + snapshot.stackCount() + " stacks / " + snapshot.itemCount() + " items</white>  Integrity: <white>"
                + shortHash(data.getString("inventory.fingerprint-sha256")) + "</white>"
        ));
        sender.sendMessage(MessageUtil.info(
            "Keep inventory: <white>" + data.getBoolean("death.keep-inventory") + "</white>  Kept items: <white>"
                + data.getInt("death.items-to-keep.items") + "</white>"
        ));
        if (data.getBoolean("death.death-chest.created")) {
            sender.sendMessage(MessageUtil.warn(
                "Linked death chest: " + data.getString("death.death-chest.world-name", "unknown") + " "
                    + data.getInt("death.death-chest.x") + ", " + data.getInt("death.death-chest.y") + ", "
                    + data.getInt("death.death-chest.z")
            ));
        }
        sender.sendMessage(MessageUtil.info("File: <white>" + repository.relativePath(snapshot.file()) + "</white>"));
    }

    public void requestRestore(org.bukkit.command.CommandSender sender, String playerInput, String selector) {
        cleanupExpiredConfirmations();
        ResolvedPlayer resolved = repository.resolvePlayer(playerInput);
        if (resolved == null) {
            sender.sendMessage(MessageUtil.error("Player not found. Use an exact name or UUID."));
            return;
        }
        Player target = Bukkit.getPlayer(resolved.uuid());
        if (target == null || !target.isOnline()) {
            sender.sendMessage(MessageUtil.error("The player must be online to restore an inventory safely."));
            return;
        }

        SnapshotLookup lookup = repository.findDeathSnapshot(resolved.uuid(), selector);
        if (lookup.snapshot() == null) {
            sender.sendMessage(MessageUtil.error(lookup.error()));
            return;
        }
        SnapshotHandle snapshot = lookup.snapshot();
        DeathInventoryPolicy.RestoreEligibility eligibility = eligibility(snapshot);
        if (!eligibility.allowed()) {
            sender.sendMessage(MessageUtil.error(eligibility.reason()));
            return;
        }

        String unsafeReason = targetSafetyFailure(target);
        if (unsafeReason != null) {
            sender.sendMessage(MessageUtil.error(unsafeReason));
            return;
        }
        if (hasPendingRestoreFor(target.getUniqueId())) {
            sender.sendMessage(MessageUtil.error("Another admin already has a pending restore for this player."));
            return;
        }

        try {
            InventoryPayload desired = DeathInventoryCodec.read(snapshot.data());
            InventoryPayload current = DeathInventoryCodec.capture(target);
            if (!DeathInventoryCodec.slotCountsMatch(desired, current)) {
                sender.sendMessage(MessageUtil.error("The saved slot layout does not match this server version. No items were changed."));
                return;
            }
            DeathChestListener.DeathChestInspection inspection = inspectLinkedDeathChest(snapshot.data());
            String chestFailure = chestSafetyFailure(inspection);
            if (chestFailure != null) {
                sender.sendMessage(MessageUtil.error(chestFailure));
                return;
            }

            pendingRestores.put(
                senderKey(sender),
                new PendingRestore(
                    target.getUniqueId(),
                    target.getName(),
                    snapshot.file(),
                    snapshot.id(),
                    desired.fingerprint(),
                    current.fingerprint(),
                    snapshot.file().lastModified(),
                    System.currentTimeMillis() + CONFIRMATION_LIFETIME_MILLIS
                )
            );
            sender.sendMessage(MessageUtil.warn(
                "Ready to replace all inventory slots for " + target.getName() + " with death record "
                    + DeathInventoryPolicy.shortId(snapshot.id()) + "."
            ));
            sendRecoveryWarning(sender, snapshot.data(), inspection);
            sender.sendMessage(MessageUtil.warn(
                "The player's current inventory will be backed up first. Run /deathinventory confirm within 60 seconds, or /deathinventory cancel."
            ));
        } catch (Exception ex) {
            sender.sendMessage(MessageUtil.error("The snapshot failed its integrity check. No items were changed."));
            plugin.getLogger().warning("Could not prepare death inventory restore " + snapshot.id() + ": " + ex.getMessage());
        }
    }

    public void confirmRestore(org.bukkit.command.CommandSender sender) {
        cleanupExpiredConfirmations();
        PendingRestore pending = pendingRestores.remove(senderKey(sender));
        if (pending == null) {
            sender.sendMessage(MessageUtil.error("You do not have a pending death inventory restore."));
            return;
        }
        if (pending.expiresAt() <= System.currentTimeMillis()) {
            sender.sendMessage(MessageUtil.error("That restore confirmation expired. Start it again."));
            return;
        }

        Player target = Bukkit.getPlayer(pending.targetUuid());
        if (target == null || !target.isOnline()) {
            sender.sendMessage(MessageUtil.error("The target went offline. Nothing was changed."));
            return;
        }
        String unsafeReason = targetSafetyFailure(target);
        if (unsafeReason != null) {
            sender.sendMessage(MessageUtil.error(unsafeReason + " Nothing was changed."));
            return;
        }

        try {
            if (pending.snapshotFile().lastModified() != pending.snapshotModifiedAt()) {
                throw new IOException("Snapshot file changed between request and confirmation.");
            }
            SnapshotHandle snapshot = repository.load(pending.snapshotFile());
            if (!pending.snapshotId().equals(snapshot.id())) {
                throw new IOException("Snapshot ID changed between request and confirmation.");
            }
            DeathInventoryPolicy.RestoreEligibility eligibility = eligibility(snapshot);
            if (!eligibility.allowed()) {
                sender.sendMessage(MessageUtil.error(eligibility.reason() + " Nothing was changed."));
                return;
            }

            InventoryPayload desired = DeathInventoryCodec.read(snapshot.data());
            if (!desired.fingerprint().equals(pending.desiredFingerprint())) {
                throw new IOException("Snapshot inventory changed between request and confirmation.");
            }
            InventoryPayload current = DeathInventoryCodec.capture(target);
            if (!current.fingerprint().equals(pending.currentFingerprint())) {
                sender.sendMessage(MessageUtil.error("The player's inventory changed after the preview. Nothing was changed; start again."));
                return;
            }
            if (!DeathInventoryCodec.slotCountsMatch(desired, current)) {
                sender.sendMessage(MessageUtil.error("The saved slot layout no longer matches. Nothing was changed."));
                return;
            }

            String chestFailure = chestSafetyFailure(inspectLinkedDeathChest(snapshot.data()));
            if (chestFailure != null) {
                sender.sendMessage(MessageUtil.error(chestFailure + " Nothing was changed."));
                return;
            }
            performRestore(sender, target, snapshot, current, desired);
        } catch (Exception ex) {
            sender.sendMessage(MessageUtil.error("The restore was stopped before applying items: " + ex.getMessage()));
            plugin.getLogger().warning("Could not confirm death inventory restore for " + pending.targetName() + ": " + ex.getMessage());
        }
    }

    public void cancelRestore(org.bukkit.command.CommandSender sender) {
        PendingRestore removed = pendingRestores.remove(senderKey(sender));
        sender.sendMessage(removed == null
            ? MessageUtil.info("You did not have a pending restore.")
            : MessageUtil.success("Death inventory restore cancelled. No items were changed."));
    }

    public List<String> suggestSnapshotIds(String playerInput) {
        ResolvedPlayer resolved = repository.resolvePlayer(playerInput);
        if (resolved == null) {
            return List.of("latest");
        }
        List<String> suggestions = new ArrayList<>();
        suggestions.add("latest");
        repository.loadDeathSnapshots(resolved.uuid()).stream()
            .limit(12)
            .map(SnapshotHandle::id)
            .map(DeathInventoryPolicy::shortId)
            .forEach(suggestions::add);
        return suggestions;
    }

    private void performRestore(
        org.bukkit.command.CommandSender sender,
        Player target,
        SnapshotHandle snapshot,
        InventoryPayload current,
        InventoryPayload desired
    ) throws IOException {
        SnapshotHandle backup = createPreRestoreBackup(target, current, snapshot, sender.getName());
        YamlConfiguration state = snapshot.data();
        state.set("snapshot.state", DeathInventoryPolicy.STATE_RESTORING);
        state.set("restore.status", DeathInventoryPolicy.STATE_RESTORING);
        state.set("restore.requested-by", sender.getName());
        state.set("restore.requested-at-utc", Instant.now().toString());
        state.set("restore.backup-id", backup.id());
        state.set("restore.backup-file", backup.file().getName());
        DeathInventoryRepository.appendAudit(
            state, "RESTORE_STARTED", sender.getName(), "Pre-restore backup " + backup.id() + " saved."
        );
        repository.save(state, snapshot.file());

        try {
            DeathInventoryCodec.apply(target, desired);
            target.saveData();
            if (!desired.fingerprint().equals(DeathInventoryCodec.capture(target).fingerprint())) {
                throw new IOException("The server did not retain the exact saved inventory after applying it.");
            }

            state.set("snapshot.state", DeathInventoryPolicy.STATE_RESTORED);
            state.set("restore.status", DeathInventoryPolicy.STATE_RESTORED);
            state.set("restore.completed-by", sender.getName());
            state.set("restore.completed-at-utc", Instant.now().toString());
            DeathInventoryRepository.appendAudit(
                state, "RESTORE_COMPLETED", sender.getName(), "Exact inventory applied and player data saved."
            );
            repository.save(state, snapshot.file());

            plugin.getLogger().warning(
                sender.getName() + " restored death inventory " + snapshot.id() + " to " + target.getName()
                    + "; backup " + backup.id() + " was retained."
            );
            sender.sendMessage(MessageUtil.success(
                "Restored inventory " + DeathInventoryPolicy.shortId(snapshot.id()) + " to " + target.getName()
                    + ". Backup: " + DeathInventoryPolicy.shortId(backup.id()) + "."
            ));
            target.sendMessage(MessageUtil.success("An admin restored your inventory from your death on " + snapshot.createdAtUtc() + "."));
        } catch (Exception restoreFailure) {
            handleRestoreFailure(sender, target, snapshot, backup, current, state, restoreFailure);
        }
    }

    private void handleRestoreFailure(
        org.bukkit.command.CommandSender sender,
        Player target,
        SnapshotHandle snapshot,
        SnapshotHandle backup,
        InventoryPayload current,
        YamlConfiguration state,
        Exception restoreFailure
    ) {
        boolean rollbackSucceeded = false;
        String rollbackFailure = null;
        try {
            DeathInventoryCodec.apply(target, current);
            target.saveData();
            rollbackSucceeded = current.fingerprint().equals(DeathInventoryCodec.capture(target).fingerprint());
            if (!rollbackSucceeded) {
                rollbackFailure = "the rollback fingerprint did not match";
            }
        } catch (Exception ex) {
            rollbackFailure = ex.getMessage();
        }

        state.set("snapshot.state", rollbackSucceeded
            ? DeathInventoryPolicy.STATE_AVAILABLE
            : DeathInventoryPolicy.STATE_REVIEW_REQUIRED);
        state.set("restore.status", rollbackSucceeded ? "FAILED_ROLLED_BACK" : DeathInventoryPolicy.STATE_REVIEW_REQUIRED);
        state.set("restore.last-failure", restoreFailure.getMessage());
        state.set("restore.last-failure-at-utc", Instant.now().toString());
        DeathInventoryRepository.appendAudit(
            state,
            rollbackSucceeded ? "RESTORE_FAILED_ROLLED_BACK" : "RESTORE_FAILED_REVIEW_REQUIRED",
            sender.getName(),
            rollbackSucceeded ? restoreFailure.getMessage() : restoreFailure.getMessage() + "; rollback: " + rollbackFailure
        );
        try {
            repository.save(state, snapshot.file());
        } catch (IOException stateFailure) {
            plugin.getLogger().severe("Could not persist the failed restore state for " + snapshot.id() + ": " + stateFailure.getMessage());
        }

        if (rollbackSucceeded) {
            sender.sendMessage(MessageUtil.error("Restore failed, so the player's original inventory was put back. No restore was consumed."));
            return;
        }
        plugin.getLogger().severe(
            "Death inventory restore and rollback both failed for " + target.getName() + ". Review "
                + repository.relativePath(snapshot.file()) + " and " + repository.relativePath(backup.file()) + "."
        );
        sender.sendMessage(MessageUtil.error(
            "Restore failed and the automatic rollback could not be verified. Stop inventory changes and review the two YAML files."
        ));
    }

    private SnapshotHandle createPreRestoreBackup(
        Player player,
        InventoryPayload inventory,
        SnapshotHandle linkedDeath,
        String admin
    ) throws IOException {
        long now = System.currentTimeMillis();
        UUID id = UUID.randomUUID();
        File file = new File(repository.playerDirectory(player.getUniqueId()), DeathInventoryPolicy.snapshotFileName(now, id));
        YamlConfiguration data = repository.baseSnapshot(
            player, id, DeathInventoryPolicy.KIND_PRE_RESTORE_BACKUP, DeathInventoryPolicy.STATE_ARCHIVED, now
        );
        DeathInventoryRepository.writeLocation(data, "backup.location", player.getLocation());
        DeathInventoryCodec.write(data, inventory);
        data.set("backup.reason", "Automatic inventory backup before an admin death restore.");
        data.set("backup.created-by", admin);
        data.set("backup.linked-death-id", linkedDeath.id());
        data.set("backup.linked-death-file", linkedDeath.file().getName());
        DeathInventoryRepository.appendAudit(
            data, "PRE_RESTORE_BACKUP_CREATED", admin, "Saved before restoring " + linkedDeath.id() + "."
        );
        repository.save(data, file);
        return repository.load(file);
    }

    private void saveDeathSnapshot(PlayerDeathEvent event, PendingDeath pending, InventoryPayload inventory) throws IOException {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        UUID id = UUID.randomUUID();
        File file = new File(repository.playerDirectory(player.getUniqueId()), DeathInventoryPolicy.snapshotFileName(now, id));
        YamlConfiguration data = repository.baseSnapshot(
            player, id, DeathInventoryPolicy.KIND_DEATH, DeathInventoryPolicy.STATE_AVAILABLE, now
        );
        DeathInventoryCodec.write(data, inventory);
        writeDeathDetails(data, event, pending);
        writeDeathChestAudit(data, player.getUniqueId());
        DeathInventoryRepository.appendAudit(data, "DEATH_CAPTURED", "SERVER", "Exact pre-death inventory saved.");
        repository.save(data, file);
        plugin.getLogger().info(
            "Saved death inventory " + id + " for " + player.getName() + " at "
                + data.getString("death.location.world-name") + " "
                + data.getInt("death.location.block-x") + "," + data.getInt("death.location.block-y") + ","
                + data.getInt("death.location.block-z") + "."
        );
    }

    private void writeDeathDetails(YamlConfiguration data, PlayerDeathEvent event, PendingDeath pending) {
        Player player = event.getPlayer();
        DeathInventoryRepository.writeLocation(data, "death.location", player.getLocation());
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        data.set("death.bukkit-cause", lastDamage == null ? "UNKNOWN" : lastDamage.getCause().name());

        DamageSource source = event.getDamageSource();
        data.set("death.damage-type", source.getDamageType().getKey().toString());
        data.set("death.indirect", source.isIndirect());
        data.set("death.scales-with-difficulty", source.scalesWithDifficulty());
        writeEntity(data, "death.causing-entity", source.getCausingEntity());
        writeEntity(data, "death.direct-entity", source.getDirectEntity());
        DeathInventoryRepository.writeLocation(data, "death.damage-location", source.getDamageLocation());
        DeathInventoryRepository.writeLocation(data, "death.source-location", source.getSourceLocation());

        Component deathMessage = event.deathMessage();
        data.set("death.message", deathMessage == null ? "" : PLAIN.serialize(deathMessage));
        data.set("death.keep-inventory", event.getKeepInventory());
        data.set("death.keep-level", event.getKeepLevel());
        data.set("death.should-drop-experience", event.shouldDropExperience());
        data.set("death.dropped-experience", event.getDroppedExp());
        data.set("death.respawn-experience", event.getNewExp());
        data.set("death.respawn-level", event.getNewLevel());
        data.set("death.respawn-total-experience", event.getNewTotalExp());
        data.set("death.player-level-before", player.getLevel());
        data.set("death.player-total-experience-before", player.getTotalExperience());
        data.set("death.open-inventory-type", player.getOpenInventory().getType().name());
        data.set("death.open-top-inventory-type", player.getOpenInventory().getTopInventory().getType().name());
        data.set("death.initial-drop-stacks", pending == null
            ? DeathInventoryCodec.countStacks(event.getDrops()) : pending.initialDropStacks());
        data.set("death.initial-drop-items", pending == null
            ? DeathInventoryCodec.countItems(event.getDrops()) : pending.initialDropItems());
        data.set("death.final-drop-stacks", DeathInventoryCodec.countStacks(event.getDrops()));
        data.set("death.final-drop-items", DeathInventoryCodec.countItems(event.getDrops()));
        data.set("death.items-to-keep.stacks", DeathInventoryCodec.countStacks(event.getItemsToKeep()));
        data.set("death.items-to-keep.items", DeathInventoryCodec.countItems(event.getItemsToKeep()));
        data.set("death.keep-inventory-at-lowest", pending != null && pending.keepInventoryAtCapture());
        data.set("death.items-to-keep-at-lowest", pending == null ? 0 : pending.itemsToKeepAtCapture());
    }

    private void writeEntity(YamlConfiguration data, String path, Entity entity) {
        if (entity == null) {
            data.set(path + ".present", false);
            data.set(path + ".summary", "none");
            return;
        }
        data.set(path + ".present", true);
        data.set(path + ".uuid", entity.getUniqueId().toString());
        data.set(path + ".type", entity.getType().getKey().toString());
        data.set(path + ".name", entity.getName());
        Component customName = entity.customName();
        String customNameText = customName == null ? "" : PLAIN.serialize(customName);
        data.set(path + ".custom-name", customNameText);
        BossManager bosses = plugin.getBossManager();
        String bossId = bosses == null ? null : bosses.customBossId(entity);
        data.set(path + ".custom-boss-id", bossId);
        data.set(path + ".summary", bossId != null
            ? entity.getName() + " (boss " + bossId + ")"
            : customNameText.isBlank() ? entity.getName() + " (" + entity.getType().name() + ")" : customNameText);
    }

    private void writeDeathChestAudit(YamlConfiguration data, UUID playerId) {
        DeathChestListener deathChest = plugin.getDeathChestListener();
        DeathChestListener.DeathChestAudit audit = deathChest == null ? null : deathChest.consumeRecentDeathChest(playerId);
        data.set("death.death-chest.created", audit != null);
        if (audit == null) {
            return;
        }
        data.set("death.death-chest.id", audit.chestId());
        data.set("death.death-chest.world-uuid", audit.worldUuid().toString());
        data.set("death.death-chest.world-name", audit.worldName());
        data.set("death.death-chest.x", audit.x());
        data.set("death.death-chest.y", audit.y());
        data.set("death.death-chest.z", audit.z());
        data.set("death.death-chest.block-count", audit.blockCount());
        data.set("death.death-chest.expires-at-epoch-ms", audit.expiresAt());
    }

    private void clearRecentChestAudit(UUID playerId) {
        DeathChestListener deathChest = plugin.getDeathChestListener();
        if (deathChest != null) {
            deathChest.consumeRecentDeathChest(playerId);
        }
    }

    private String targetSafetyFailure(Player target) {
        if (!target.isOnline() || target.isDead()) {
            return "The target must be online, alive, and respawned.";
        }
        if (target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) {
            return "Put the target in Survival or Adventure before restoring items.";
        }
        if (target.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {
            return "The target must close every chest, menu, and container before restoring.";
        }
        if (!DeathInventoryCodec.isEmpty(target.getItemOnCursor())) {
            return "The target must put down the item on their cursor before restoring.";
        }
        for (ItemStack item : DeathInventoryCodec.personalCraftingInputs(target)) {
            if (!DeathInventoryCodec.isEmpty(item)) {
                return "The target must empty their personal crafting grid before restoring.";
            }
        }
        CombatLogListener combat = plugin.getCombatLogListener();
        if (combat != null && combat.isInPlayerCombat(target)) {
            return "Wait until the target leaves player combat before restoring.";
        }
        BossManager bosses = plugin.getBossManager();
        return bosses != null && bosses.isActiveBossFight(target)
            ? "Wait until the target leaves the boss fight before restoring."
            : null;
    }

    private DeathChestListener.DeathChestInspection inspectLinkedDeathChest(YamlConfiguration data) {
        if (!data.getBoolean("death.death-chest.created")) {
            return null;
        }
        DeathChestListener deathChest = plugin.getDeathChestListener();
        String chestId = data.getString("death.death-chest.id");
        String worldId = data.getString("death.death-chest.world-uuid");
        if (deathChest == null || chestId == null || worldId == null) {
            return new DeathChestListener.DeathChestInspection(DeathChestListener.DeathChestState.ERROR, "unknown", 0);
        }
        try {
            return deathChest.inspectDeathChest(
                chestId,
                UUID.fromString(worldId),
                data.getInt("death.death-chest.x"),
                data.getInt("death.death-chest.y"),
                data.getInt("death.death-chest.z")
            );
        } catch (IllegalArgumentException ex) {
            return new DeathChestListener.DeathChestInspection(DeathChestListener.DeathChestState.ERROR, "unknown", 0);
        }
    }

    private String chestSafetyFailure(DeathChestListener.DeathChestInspection inspection) {
        if (inspection == null) {
            return null;
        }
        if (inspection.state() == DeathChestListener.DeathChestState.HAS_ITEMS) {
            return "The linked death chest still contains items at " + inspection.location() + ". Recover it normally first.";
        }
        if (inspection.state() == DeathChestListener.DeathChestState.ERROR
            || inspection.state() == DeathChestListener.DeathChestState.WORLD_UNAVAILABLE) {
            return "The linked death chest could not be verified.";
        }
        return null;
    }

    private void sendRecoveryWarning(
        org.bukkit.command.CommandSender sender,
        YamlConfiguration data,
        DeathChestListener.DeathChestInspection inspection
    ) {
        if (inspection != null && inspection.state() == DeathChestListener.DeathChestState.EMPTY) {
            sender.sendMessage(MessageUtil.warn("The linked death chest is empty. Confirm only if its contents were not already recovered."));
        } else if (data.getBoolean("death.death-chest.created")) {
            sender.sendMessage(MessageUtil.warn("A death chest was created for this death. Confirm only after checking it was not looted."));
        } else {
            sender.sendMessage(MessageUtil.warn("World drops are not removed by this tool. Confirm only if the original items were not collected."));
        }
    }

    private void reconcileInterruptedRestores(Player player) {
        for (SnapshotHandle snapshot : repository.loadDeathSnapshots(player.getUniqueId())) {
            if (DeathInventoryPolicy.STATE_RESTORING.equalsIgnoreCase(snapshot.state())) {
                reconcileInterruptedRestore(player, snapshot);
            }
        }
    }

    private void reconcileInterruptedRestore(Player player, SnapshotHandle snapshot) {
        YamlConfiguration state = snapshot.data();
        try {
            InventoryPayload current = DeathInventoryCodec.capture(player);
            InventoryPayload desired = DeathInventoryCodec.read(state);
            if (current.fingerprint().equals(desired.fingerprint())) {
                state.set("snapshot.state", DeathInventoryPolicy.STATE_RESTORED);
                state.set("restore.status", DeathInventoryPolicy.STATE_RESTORED);
                state.set("restore.completed-at-utc", Instant.now().toString());
                DeathInventoryRepository.appendAudit(
                    state, "RESTORE_RECONCILED_AS_COMPLETED", "SERVER", "Live inventory matched the requested death snapshot."
                );
                repository.save(state, snapshot.file());
                plugin.getLogger().warning("Finalized interrupted death inventory restore " + snapshot.id() + " for " + player.getName() + ".");
                return;
            }

            File backupFile = repository.safeSibling(snapshot.file(), state.getString("restore.backup-file"));
            if (backupFile != null && backupFile.isFile()) {
                InventoryPayload beforeRestore = DeathInventoryCodec.read(repository.load(backupFile).data());
                if (current.fingerprint().equals(beforeRestore.fingerprint())) {
                    state.set("snapshot.state", DeathInventoryPolicy.STATE_AVAILABLE);
                    state.set("restore.status", "INTERRUPTED_BEFORE_APPLY");
                    DeathInventoryRepository.appendAudit(
                        state, "RESTORE_RECONCILED_AS_NOT_APPLIED", "SERVER", "Live inventory still matched the pre-restore backup."
                    );
                    repository.save(state, snapshot.file());
                    plugin.getLogger().warning("Reset interrupted, unapplied death inventory restore " + snapshot.id() + " for " + player.getName() + ".");
                    return;
                }
            }
            markReviewRequired(snapshot, state, "Live inventory matched neither the death snapshot nor its pre-restore backup.");
        } catch (Exception ex) {
            markReviewRequired(snapshot, state, "Automatic reconciliation failed: " + ex.getMessage());
        }
    }

    private void markReviewRequired(SnapshotHandle snapshot, YamlConfiguration state, String detail) {
        state.set("snapshot.state", DeathInventoryPolicy.STATE_REVIEW_REQUIRED);
        state.set("restore.status", DeathInventoryPolicy.STATE_REVIEW_REQUIRED);
        DeathInventoryRepository.appendAudit(state, "RESTORE_REVIEW_REQUIRED", "SERVER", detail);
        try {
            repository.save(state, snapshot.file());
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not mark death inventory " + snapshot.id() + " for review: " + ex.getMessage());
        }
        plugin.getLogger().severe("Death inventory " + snapshot.id() + " requires manual review: " + detail);
    }

    private DeathInventoryPolicy.RestoreEligibility eligibility(SnapshotHandle snapshot) {
        return DeathInventoryPolicy.restoreEligibility(
            snapshot.kind(),
            snapshot.state(),
            snapshot.data().getBoolean("death.keep-inventory"),
            snapshot.data().getInt("death.items-to-keep.items")
        );
    }

    private boolean hasPendingRestoreFor(UUID playerId) {
        long now = System.currentTimeMillis();
        return pendingRestores.values().stream()
            .anyMatch(pending -> pending.expiresAt() > now && pending.targetUuid().equals(playerId));
    }

    private void cleanupExpiredConfirmations() {
        long now = System.currentTimeMillis();
        pendingRestores.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private static String senderKey(org.bukkit.command.CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : "CONSOLE:" + sender.getName();
    }

    private static String shortHash(String hash) {
        return hash == null || hash.isBlank() ? "missing" : hash.substring(0, Math.min(12, hash.length()));
    }

    private static String formatCoordinate(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private record PendingDeath(
        InventoryPayload inventory,
        int initialDropStacks,
        int initialDropItems,
        boolean keepInventoryAtCapture,
        int itemsToKeepAtCapture
    ) {
    }

    private record PendingRestore(
        UUID targetUuid,
        String targetName,
        File snapshotFile,
        String snapshotId,
        String desiredFingerprint,
        String currentFingerprint,
        long snapshotModifiedAt,
        long expiresAt
    ) {
    }
}
