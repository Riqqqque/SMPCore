package me.rique.smpcore.game;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.ItemEscrowService;
import me.rique.smpcore.util.ItemEscrowService.EscrowedItem;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SpinBetManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long INVITE_TTL_MS = 60_000L;
    private static final long CONFIRM_TTL_TICKS = 20L * 30L;
    private static final int SPIN_STEPS = 60;
    private static final int SPIN_PERIOD_TICKS = 2;
    private static final int INVENTORY_SIZE = 45;
    private static final int[] SPIN_SLOTS = {18, 19, 20, 21, 22, 23, 24, 25, 26};
    private static final int SPIN_CENTER_SLOT = 22;
    private static final int TITLE_SLOT = 4;
    private static final int LEFT_HEAD_SLOT = 10;
    private static final int LEFT_WAGER_SLOT = 11;
    private static final int RIGHT_WAGER_SLOT = 15;
    private static final int RIGHT_HEAD_SLOT = 16;
    private static final int RESULT_SLOT = 31;
    private static final int CONFIRM_SLOT = 30;
    private static final int DENY_SLOT = 32;

    private final SMPCore plugin;
    private final ItemEscrowService escrow;
    private final Map<UUID, SpinInvite> invitesByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> outgoingInviteByChallenger = new ConcurrentHashMap<>();
    private final Map<UUID, SpinMatch> matches = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerMatch = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingInventoryRefreshes = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingInventoryReopens = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;
    private boolean shuttingDown;

    public SpinBetManager(SMPCore plugin) {
        this.plugin = plugin;
        this.escrow = new ItemEscrowService(plugin, "spinbet", "spinbet-escrow.yml");
    }

    public void start() {
        escrow.start(Bukkit.getOnlinePlayers());
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredInvites, 20L * 10L, 20L * 10L);
    }

    public void shutdown() {
        shuttingDown = true;
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        cancelPendingUiTasks();
        for (SpinMatch match : new ArrayList<>(matches.values())) {
            cancelMatch(match, "Plugin shut down.", false);
        }
        invitesByTarget.clear();
        outgoingInviteByChallenger.clear();
        escrow.shutdown();
    }

    public boolean createInvite(Player challenger, String targetName) {
        restorePendingRecovery(challenger, false);
        sanitizeOrphanedEscrowMarkers(challenger);
        if (isCombatTagged(challenger)) {
            challenger.sendMessage(MessageUtil.warn("You cannot start a spin bet while in combat."));
            return false;
        }
        Player target = findOnlinePlayer(targetName);
        if (target == null) {
            challenger.sendMessage(MessageUtil.error("That player is not online."));
            return false;
        }
        if (target.getUniqueId().equals(challenger.getUniqueId())) {
            challenger.sendMessage(MessageUtil.warn("You cannot spin bet yourself."));
            return false;
        }
        if (!target.hasPermission("smpcore.spinbet")) {
            challenger.sendMessage(MessageUtil.warn("That player cannot use /spinbet."));
            return false;
        }
        if (isCombatTagged(target)) {
            challenger.sendMessage(MessageUtil.warn("That player is in combat right now."));
            return false;
        }
        if (isBusy(challenger.getUniqueId()) || isBusy(target.getUniqueId())) {
            challenger.sendMessage(MessageUtil.warn("One of you already has a spin bet open."));
            return false;
        }
        if (!hasValidHeldWager(challenger, true)) {
            return false;
        }

        SpinInvite invite = new SpinInvite(
            challenger.getUniqueId(),
            challenger.getName(),
            target.getUniqueId(),
            target.getName(),
            System.currentTimeMillis() + INVITE_TTL_MS
        );
        invitesByTarget.put(target.getUniqueId(), invite);
        outgoingInviteByChallenger.put(challenger.getUniqueId(), target.getUniqueId());

        challenger.sendMessage(MessageUtil.success("Spin bet sent to <white>" + miniEscape(target.getName()) + "</white>."));
        challenger.sendMessage(MessageUtil.info("Keep your bet item in your main hand."));
        target.sendMessage(MessageUtil.info("<white>" + miniEscape(challenger.getName()) + "</white> wants to spin bet."));
        target.sendMessage(MessageUtil.info("Hold your bet item, then use <white>/spinbet accept " + miniEscape(challenger.getName()) + "</white>."));
        target.playSound(target.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.25f);
        return true;
    }

    public boolean acceptInvite(Player target, String challengerName) {
        restorePendingRecovery(target, false);
        sanitizeOrphanedEscrowMarkers(target);
        if (isCombatTagged(target)) {
            target.sendMessage(MessageUtil.warn("You cannot accept a spin bet while in combat."));
            return false;
        }
        SpinInvite invite = inviteFor(target.getUniqueId(), challengerName);
        if (invite == null) {
            target.sendMessage(MessageUtil.warn("No spin bet invite from that player."));
            return false;
        }
        if (invite.expired()) {
            removeInvite(invite);
            target.sendMessage(MessageUtil.warn("That spin bet invite expired."));
            return false;
        }
        Player challenger = Bukkit.getPlayer(invite.challengerId());
        if (challenger == null || !challenger.isOnline()) {
            removeInvite(invite);
            target.sendMessage(MessageUtil.warn("That player is not online anymore."));
            return false;
        }
        if (isCombatTagged(challenger)) {
            target.sendMessage(MessageUtil.warn("That player is in combat right now."));
            return false;
        }
        if (isInMatch(challenger.getUniqueId()) || isInMatch(target.getUniqueId())) {
            target.sendMessage(MessageUtil.warn("One of you is already spinning."));
            return false;
        }

        removeInvite(invite);
        return startMatch(challenger, target);
    }

    public boolean denyInvite(Player target, String challengerName) {
        SpinInvite invite = inviteFor(target.getUniqueId(), challengerName);
        if (invite == null) {
            target.sendMessage(MessageUtil.warn("No spin bet invite from that player."));
            return false;
        }
        removeInvite(invite);
        target.sendMessage(MessageUtil.info("Spin bet denied."));
        Player challenger = Bukkit.getPlayer(invite.challengerId());
        if (challenger != null && challenger.isOnline()) {
            challenger.sendMessage(MessageUtil.warn("<white>" + miniEscape(target.getName()) + "</white> denied the spin bet."));
        }
        return true;
    }

    public boolean cancelInvite(Player player) {
        UUID targetId = outgoingInviteByChallenger.get(player.getUniqueId());
        if (targetId == null) {
            player.sendMessage(MessageUtil.warn("You have no pending spin bet."));
            return false;
        }
        SpinInvite invite = invitesByTarget.get(targetId);
        if (invite == null) {
            outgoingInviteByChallenger.remove(player.getUniqueId());
            player.sendMessage(MessageUtil.info("Spin bet canceled."));
            return true;
        }
        removeInvite(invite);
        player.sendMessage(MessageUtil.info("Spin bet canceled."));
        Player target = Bukkit.getPlayer(invite.targetId());
        if (target != null && target.isOnline()) {
            target.sendMessage(MessageUtil.warn("<white>" + miniEscape(player.getName()) + "</white> canceled the spin bet."));
        }
        return true;
    }

    public boolean claim(Player player, boolean noisy) {
        boolean hadPending = escrow.hasPendingRecovery(player);
        boolean restored = restorePendingRecovery(player, noisy);
        sanitizeOrphanedEscrowMarkers(player);
        if (!restored && noisy && !hadPending) {
            player.sendMessage(MessageUtil.info("No spin bet items are waiting."));
        }
        return restored;
    }

    public List<String> pendingChallengerNames(Player target) {
        List<String> names = new ArrayList<>();
        SpinInvite invite = invitesByTarget.get(target.getUniqueId());
        if (invite != null && !invite.expired()) {
            names.add(invite.challengerName());
        }
        return names;
    }

    private boolean startMatch(Player challenger, Player target) {
        sanitizeOrphanedEscrowMarkers(challenger);
        sanitizeOrphanedEscrowMarkers(target);
        if (isCombatTagged(challenger) || isCombatTagged(target)) {
            challenger.sendMessage(MessageUtil.warn("Spin bets cannot start while either player is in combat."));
            target.sendMessage(MessageUtil.warn("Spin bets cannot start while either player is in combat."));
            return false;
        }
        if (!isReadyForWager(challenger) || !isReadyForWager(target)) {
            return false;
        }

        int challengerSlot = challenger.getInventory().getHeldItemSlot();
        int targetSlot = target.getInventory().getHeldItemSlot();
        ItemStack challengerRawItem = challenger.getInventory().getItem(challengerSlot);
        ItemStack targetRawItem = target.getInventory().getItem(targetSlot);
        if (escrow.hasAnyEscrowMarker(challengerRawItem) || escrow.hasAnyEscrowMarker(targetRawItem)) {
            challenger.sendMessage(MessageUtil.warn("That bet is still locked. Try <white>/spinbet claim</white> or <white>/blackjack claim</white>."));
            target.sendMessage(MessageUtil.warn("That bet is still locked. Try <white>/spinbet claim</white> or <white>/blackjack claim</white>."));
            return false;
        }
        ItemStack challengerItem = escrow.cleanEscrowMarkers(challengerRawItem);
        ItemStack targetItem = escrow.cleanEscrowMarkers(targetRawItem);
        if (!isValidWager(challengerItem) || !isValidWager(targetItem)) {
            challenger.sendMessage(MessageUtil.warn("Both players need a bet item in main hand."));
            target.sendMessage(MessageUtil.warn("Both players need a bet item in main hand."));
            return false;
        }
        if (isUnsafeWager(challengerItem) || isUnsafeWager(targetItem)) {
            challenger.sendMessage(MessageUtil.warn("Storage and protected unique items cannot be bet."));
            target.sendMessage(MessageUtil.warn("Storage and protected unique items cannot be bet."));
            return false;
        }
        if (escrow.hasMenuPreviewMarker(challengerItem) || escrow.hasMenuPreviewMarker(targetItem)) {
            challenger.sendMessage(MessageUtil.warn("Menu preview items cannot be bet."));
            target.sendMessage(MessageUtil.warn("Menu preview items cannot be bet."));
            return false;
        }

        List<ItemStack> winnings = List.of(challengerItem.clone(), targetItem.clone());
        if (!escrow.hasRoomForItems(challenger, challengerSlot, winnings) || !escrow.hasRoomForItems(target, targetSlot, winnings)) {
            challenger.sendMessage(MessageUtil.warn("Both players need room for both bets."));
            target.sendMessage(MessageUtil.warn("Both players need room for both bets."));
            return false;
        }

        UUID matchId = UUID.randomUUID();
        playerMatch.put(challenger.getUniqueId(), matchId);
        playerMatch.put(target.getUniqueId(), matchId);

        EscrowedItem left = escrow.capture(matchId, challenger, challengerSlot, challengerItem);
        if (left == null) {
            playerMatch.remove(challenger.getUniqueId(), matchId);
            playerMatch.remove(target.getUniqueId(), matchId);
            challenger.sendMessage(MessageUtil.error("Could not lock your bet item."));
            target.sendMessage(MessageUtil.warn("Spin bet could not start."));
            return false;
        }

        EscrowedItem right = escrow.capture(matchId, target, targetSlot, targetItem);
        if (right == null) {
            returnEscrowToOwner(left, false);
            playerMatch.remove(challenger.getUniqueId(), matchId);
            playerMatch.remove(target.getUniqueId(), matchId);
            challenger.sendMessage(MessageUtil.warn("Spin bet canceled. Item returned."));
            target.sendMessage(MessageUtil.error("Could not lock your bet item."));
            return false;
        }

        boolean challengerWins = ThreadLocalRandom.current().nextBoolean();
        SpinMatch match = new SpinMatch(matchId, challenger.getUniqueId(), target.getUniqueId(), left, right, challengerWins);
        match.inventory = createConfirmationInventory(match, challenger, target);
        matches.put(matchId, match);

        challenger.openInventory(match.inventory);
        target.openInventory(match.inventory);
        challenger.playSound(challenger.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
        challenger.sendMessage(MessageUtil.info("Review both locked wagers, then confirm or deny the spin bet."));
        target.sendMessage(MessageUtil.info("Review both locked wagers, then confirm or deny the spin bet."));
        match.task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!match.settled && !match.committed) {
                cancelMatch(match, "confirmation expired", true);
            }
        }, CONFIRM_TTL_TICKS);
        return true;
    }

    private Inventory createConfirmationInventory(SpinMatch match, Player challenger, Player target) {
        Inventory inventory = Bukkit.createInventory(
            new SpinBetHolder(match.matchId()),
            INVENTORY_SIZE,
            BedrockCompat.menuTitle(
                BedrockCompat.isBedrockPlayer(challenger) ? challenger : target,
                MM.deserialize("<gold><bold>Review Spin Bet</bold></gold>"),
                "Review Spin Bet"
            )
        );
        fill(inventory);
        inventory.setItem(TITLE_SLOT, menuItem(Material.SPYGLASS, "<gold><bold>Check Both Wagers</bold></gold>", List.of(
            "<gray>Both shown items are already locked.</gray>",
            "<gray>Both players must confirm.</gray>",
            "<dark_gray>Closes automatically in 30 seconds.</dark_gray>"
        )));
        inventory.setItem(LEFT_HEAD_SLOT, playerHead(challenger, "<green><bold>" + miniEscape(challenger.getName()) + "</bold></green>", "<gray>Challenger</gray>"));
        inventory.setItem(RIGHT_HEAD_SLOT, playerHead(target, "<red><bold>" + miniEscape(target.getName()) + "</bold></red>", "<gray>Responding player</gray>"));
        inventory.setItem(LEFT_WAGER_SLOT, previewWager(match.left().item(), challenger.getName(), "<green>"));
        inventory.setItem(RIGHT_WAGER_SLOT, previewWager(match.right().item(), target.getName(), "<red>"));
        inventory.setItem(CONFIRM_SLOT, menuItem(Material.LIME_CONCRETE, "<green><bold>Confirm Spin Bet</bold></green>", List.of(
            "<gray>Accept these exact locked wagers.</gray>",
            "<gray>Waiting for <white>2 players</white>.</gray>",
            "<yellow>Click to start the spin.</yellow>"
        )));
        inventory.setItem(DENY_SLOT, menuItem(Material.RED_CONCRETE, "<red><bold>Deny Spin Bet</bold></red>", List.of(
            "<gray>Return both locked wagers.</gray>",
            "<yellow>Click to cancel.</yellow>"
        )));
        return inventory;
    }

    private void confirmMatch(SpinMatch match, Player player) {
        match.decisionPending.remove(player.getUniqueId());
        if (match.settled || match.committed || !match.hasPlayer(player.getUniqueId())
            || !match.confirmations.add(player.getUniqueId())) {
            return;
        }
        Player target = Bukkit.getPlayer(match.targetId());
        Player challenger = Bukkit.getPlayer(match.challengerId());
        if (challenger == null || !challenger.isOnline() || challenger.isDead()
            || target == null || !target.isOnline() || target.isDead()
            || isCombatTagged(challenger) || isCombatTagged(target)) {
            cancelMatch(match, "players were no longer ready at confirmation", true);
            return;
        }
        if (match.confirmations.size() < 2) {
            String waitingFor = player.getUniqueId().equals(match.challengerId()) ? target.getName() : challenger.getName();
            match.inventory.setItem(CONFIRM_SLOT, menuItem(Material.YELLOW_CONCRETE, "<yellow><bold>One Player Confirmed</bold></yellow>", List.of(
                "<gray>Waiting for <white>" + miniEscape(waitingFor) + "</white>.</gray>",
                "<gray>Either player can still deny.</gray>"
            )));
            player.sendMessage(MessageUtil.success("Wager confirmed. Waiting for <white>" + miniEscape(waitingFor) + "</white>."));
            return;
        }
        if (match.task != null) {
            match.task.cancel();
            match.task = null;
        }
        match.committed = true;
        match.inventory = createMatchInventory(match, challenger, target);
        challenger.openInventory(match.inventory);
        target.openInventory(match.inventory);
        challenger.sendMessage(MessageUtil.success("Spin bet confirmed."));
        target.sendMessage(MessageUtil.success("Spin bet confirmed."));
        startAnimation(match);
    }

    private void denyMatch(SpinMatch match, Player player) {
        match.decisionPending.remove(player.getUniqueId());
        if (match.settled || match.committed || !match.hasPlayer(player.getUniqueId())) {
            return;
        }
        cancelMatch(match, "wagers denied by " + player.getName(), false);
        player.sendMessage(MessageUtil.info("Spin bet denied. Both wagers were returned."));
        UUID otherId = player.getUniqueId().equals(match.challengerId()) ? match.targetId() : match.challengerId();
        Player other = Bukkit.getPlayer(otherId);
        if (other != null && other.isOnline()) {
            other.sendMessage(MessageUtil.warn("<white>" + miniEscape(player.getName()) + "</white> denied the wagers. Both items were returned."));
        }
    }

    private Inventory createMatchInventory(SpinMatch match, Player challenger, Player target) {
        Inventory inventory = Bukkit.createInventory(
            new SpinBetHolder(match.matchId()),
            INVENTORY_SIZE,
            BedrockCompat.menuTitle(
                BedrockCompat.isBedrockPlayer(challenger) ? challenger : target,
                MM.deserialize("<gradient:#22c55e:#ef4444><bold>Spin Bet</bold></gradient>"),
                "Spin Bet"
            )
        );
        fill(inventory);
        inventory.setItem(TITLE_SLOT, menuItem(Material.NETHER_STAR, "<gradient:#22c55e:#ef4444><bold>Spin Bet</bold></gradient>", List.of(
            "<gray>Green is <white>" + miniEscape(challenger.getName()) + "</white>.</gray>",
            "<gray>Red is <white>" + miniEscape(target.getName()) + "</white>.</gray>"
        )));
        inventory.setItem(LEFT_HEAD_SLOT, playerHead(challenger, "<green><bold>" + miniEscape(challenger.getName()) + "</bold></green>", "<gray>Green side</gray>"));
        inventory.setItem(RIGHT_HEAD_SLOT, playerHead(target, "<red><bold>" + miniEscape(target.getName()) + "</bold></red>", "<gray>Red side</gray>"));
        inventory.setItem(LEFT_WAGER_SLOT, previewWager(match.left().item(), challenger.getName(), "<green>"));
        inventory.setItem(RIGHT_WAGER_SLOT, previewWager(match.right().item(), target.getName(), "<red>"));
        inventory.setItem(RESULT_SLOT, menuItem(Material.CLOCK, "<yellow><bold>Spinning...</bold></yellow>", List.of("<gray>Center pane decides it.</gray>")));
        renderSpinRow(match, 0, false);
        return inventory;
    }

    private void startAnimation(SpinMatch match) {
        match.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (match.settled) {
                return;
            }
            Player challenger = Bukkit.getPlayer(match.challengerId());
            if (challenger == null || !challenger.isOnline() || challenger.isDead()) {
                forfeitMatch(match, match.challengerId(), "player left during spin");
                return;
            }
            Player target = Bukkit.getPlayer(match.targetId());
            if (target == null || !target.isOnline() || target.isDead()) {
                forfeitMatch(match, match.targetId(), "player left during spin");
                return;
            }
            match.step++;
            boolean finalFrame = match.step >= SPIN_STEPS;
            renderSpinRow(match, match.step, finalFrame);
            playSpinTick(match, finalFrame);
            if (finalFrame) {
                finishMatch(match);
            }
        }, 0L, SPIN_PERIOD_TICKS);
    }

    private void renderSpinRow(SpinMatch match, int step, boolean finalFrame) {
        if (match.inventory == null) {
            return;
        }
        for (int i = 0; i < SPIN_SLOTS.length; i++) {
            boolean green = finalFrame && SPIN_SLOTS[i] == SPIN_CENTER_SLOT
                ? match.challengerWins()
                : ((step + i + (step / 3)) % 2 == 0);
            Material material = green ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            String label = green ? "<green><bold>Green</bold></green>" : "<red><bold>Red</bold></red>";
            List<String> lore = SPIN_SLOTS[i] == SPIN_CENTER_SLOT
                ? List.of("<gray>Winner slot</gray>")
                : List.of("<dark_gray>Spinning...</dark_gray>");
            match.inventory.setItem(SPIN_SLOTS[i], menuItem(material, label, lore));
        }
        if (finalFrame) {
            String winnerName = match.challengerWins() ? match.left().ownerName() : match.right().ownerName();
            Material result = match.challengerWins() ? Material.LIME_DYE : Material.RED_DYE;
            match.inventory.setItem(RESULT_SLOT, menuItem(result, "<gold><bold>" + miniEscape(winnerName) + " wins</bold></gold>", List.of(
                "<gray>Both bets go to the winner.</gray>"
            )));
        }
    }

    private void playSpinTick(SpinMatch match, boolean finalFrame) {
        if (finalFrame) {
            return;
        }
        Player challenger = Bukkit.getPlayer(match.challengerId());
        Player target = Bukkit.getPlayer(match.targetId());
        float pitch = Math.min(1.7f, 0.75f + (match.step * 0.025f));
        if (challenger != null && challenger.isOnline()) {
            challenger.playSound(challenger.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, pitch);
        }
        if (target != null && target.isOnline()) {
            target.playSound(target.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, pitch);
        }
    }

    private void finishMatch(SpinMatch match) {
        if (match.settled) {
            return;
        }
        match.settled = true;
        if (match.task != null) {
            match.task.cancel();
            match.task = null;
        }
        matches.remove(match.matchId());
        playerMatch.remove(match.challengerId(), match.matchId());
        playerMatch.remove(match.targetId(), match.matchId());
        clearPendingUiTasks(match.challengerId());
        clearPendingUiTasks(match.targetId());

        UUID winnerId = match.challengerWins() ? match.challengerId() : match.targetId();
        UUID loserId = match.challengerWins() ? match.targetId() : match.challengerId();
        String winnerName = match.challengerWins() ? match.left().ownerName() : match.right().ownerName();
        String loserName = match.challengerWins() ? match.right().ownerName() : match.left().ownerName();
        boolean delivered = awardCommittedWagers(match, winnerId, winnerName);

        Player winner = Bukkit.getPlayer(winnerId);
        if (!delivered && winner != null && winner.isOnline()) {
            BedrockCompat.sendGameMessage(winner, MessageUtil.warn("Your winnings are safe. Clear space and use <white>/spinbet claim</white>."));
        }

        Player loser = Bukkit.getPlayer(loserId);
        if (winner != null && winner.isOnline()) {
            BedrockCompat.sendGameMessage(winner, MessageUtil.success("You won the spin bet."));
            playWinnerEffects(winner);
            BedrockCompat.syncGameInventory(winner);
        }
        if (loser != null && loser.isOnline()) {
            BedrockCompat.sendGameMessage(loser, MessageUtil.warn("<white>" + miniEscape(winnerName) + "</white> won the spin bet."));
            playLoserEffects(loser);
            BedrockCompat.syncGameInventory(loser);
        }
        plugin.getLogger().info("Spin bet " + match.matchId() + " finished: " + winnerName + " beat " + loserName + ".");
        scheduleClose(match);
    }

    private boolean awardCommittedWagers(SpinMatch match, UUID winnerId, String winnerName) {
        boolean leftSaved = escrow.retarget(match.left(), winnerId, winnerName, "AWARDING");
        boolean rightSaved = escrow.retarget(match.right(), winnerId, winnerName, "AWARDING");

        Player winner = Bukkit.getPlayer(winnerId);
        List<ItemStack> winnings = List.of(match.left().item().clone(), match.right().item().clone());
        boolean canDeliver = leftSaved
            && rightSaved
            && winner != null
            && winner.isOnline()
            && !winner.isDead()
            && escrow.hasRoomForItems(winner, -1, winnings);
        boolean leftDelivered = canDeliver && escrow.give(winner, match.left());
        boolean rightDelivered = canDeliver && escrow.give(winner, match.right());
        if (!leftDelivered) {
            escrow.queueRecovery(winnerId, winnerName, match.left());
        }
        if (!rightDelivered) {
            escrow.queueRecovery(winnerId, winnerName, match.right());
        }
        if (!leftSaved || !rightSaved) {
            plugin.getLogger().warning("Spin bet " + match.matchId()
                + " needed recovery fallback while saving winnings for " + winnerName + ".");
        }
        return leftDelivered && rightDelivered;
    }

    private void playWinnerEffects(Player winner) {
        var location = winner.getLocation();
        var effect = location.clone().add(0.0, 1.35, 0.0);
        var world = winner.getWorld();
        world.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.1f);
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.75f, 1.35f);
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.9f, 1.15f);
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.85f, 1.35f);
        world.spawnParticle(Particle.FIREWORK, effect, 70, 0.75, 0.75, 0.75, 0.08);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, effect, 24, 0.45, 0.6, 0.45, 0.03);
        world.spawnParticle(Particle.FLASH, effect, 1, 0.0, 0.0, 0.0, 0.0, Color.WHITE);
    }

    private void playLoserEffects(Player loser) {
        var location = loser.getLocation();
        var effect = location.clone().add(0.0, 1.0, 0.0);
        var world = loser.getWorld();
        world.playSound(location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.55f);
        world.playSound(location, Sound.ENTITY_ITEM_BREAK, 0.45f, 0.85f);
        world.spawnParticle(Particle.SMOKE, effect, 26, 0.45, 0.35, 0.45, 0.035);
        world.spawnParticle(Particle.ASH, effect, 18, 0.5, 0.25, 0.5, 0.02);
    }

    private void cancelMatch(SpinMatch match, String reason, boolean notify) {
        if (match.settled) {
            return;
        }
        match.settled = true;
        if (match.task != null) {
            match.task.cancel();
            match.task = null;
        }
        matches.remove(match.matchId());
        playerMatch.remove(match.challengerId(), match.matchId());
        playerMatch.remove(match.targetId(), match.matchId());
        clearPendingUiTasks(match.challengerId());
        clearPendingUiTasks(match.targetId());
        returnEscrowToOwner(match.left(), notify);
        returnEscrowToOwner(match.right(), notify);
        if (notify) {
            Player challenger = Bukkit.getPlayer(match.challengerId());
            Player target = Bukkit.getPlayer(match.targetId());
            if (challenger != null && challenger.isOnline()) {
                challenger.sendMessage(MessageUtil.warn("Spin bet canceled. Items returned."));
            }
            if (target != null && target.isOnline()) {
                target.sendMessage(MessageUtil.warn("Spin bet canceled. Items returned."));
            }
        }
        plugin.getLogger().info("Spin bet " + match.matchId() + " canceled: " + reason);
        scheduleClose(match);
    }

    private void forfeitMatch(SpinMatch match, UUID forfeiterId, String reason) {
        if (match == null || match.settled) {
            return;
        }
        if (!match.committed) {
            cancelMatch(match, reason, true);
            return;
        }
        UUID winnerId;
        String winnerName;
        String forfeiterName;
        if (forfeiterId.equals(match.challengerId())) {
            winnerId = match.targetId();
            winnerName = match.right().ownerName();
            forfeiterName = match.left().ownerName();
        } else if (forfeiterId.equals(match.targetId())) {
            winnerId = match.challengerId();
            winnerName = match.left().ownerName();
            forfeiterName = match.right().ownerName();
        } else {
            plugin.getLogger().severe("Ignored spin bet forfeit for player outside match " + match.matchId() + ".");
            return;
        }

        match.settled = true;
        if (match.task != null) {
            match.task.cancel();
            match.task = null;
        }
        matches.remove(match.matchId(), match);
        playerMatch.remove(match.challengerId(), match.matchId());
        playerMatch.remove(match.targetId(), match.matchId());
        clearPendingUiTasks(match.challengerId());
        clearPendingUiTasks(match.targetId());

        boolean delivered = awardCommittedWagers(match, winnerId, winnerName);
        Player winner = Bukkit.getPlayer(winnerId);
        if (winner != null && winner.isOnline()) {
            BedrockCompat.sendGameMessage(winner, MessageUtil.success("<white>" + miniEscape(forfeiterName)
                + "</white> forfeited. You won both bets."));
            if (!delivered) {
                BedrockCompat.sendGameMessage(winner, MessageUtil.warn("Your winnings are safe. Clear space and use <white>/spinbet claim</white>."));
            }
            playWinnerEffects(winner);
            BedrockCompat.syncGameInventory(winner);
        }
        Player forfeiter = Bukkit.getPlayer(forfeiterId);
        if (forfeiter != null && forfeiter.isOnline()) {
            BedrockCompat.sendGameMessage(forfeiter, MessageUtil.warn("You forfeited the spin bet and lost your wager."));
            BedrockCompat.syncGameInventory(forfeiter);
        }
        plugin.getLogger().info("Spin bet " + match.matchId() + " forfeited by "
            + forfeiterName + "; both wagers awarded to " + winnerName + ": " + reason);
        scheduleClose(match);
    }

    private void scheduleClose(SpinMatch match) {
        if (match.inventory == null || shuttingDown) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player viewer : new ArrayList<>(match.inventory.getViewers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .toList())) {
                if (viewer.isOnline() && viewer.getOpenInventory().getTopInventory().equals(match.inventory)) {
                    viewer.closeInventory();
                }
            }
        }, 20L * 3L);
    }

    private void returnEscrowToOwner(EscrowedItem wager, boolean notify) {
        escrow.retarget(wager, wager.ownerId(), wager.ownerName(), "RETURNING");
        Player owner = Bukkit.getPlayer(wager.ownerId());
        if (owner != null && owner.isOnline() && !owner.isDead() && escrow.give(owner, wager)) {
            if (notify) {
                owner.sendMessage(MessageUtil.info("Your spin bet item was returned."));
            }
            return;
        }
        escrow.queueRecovery(wager.ownerId(), wager.ownerName(), wager);
    }

    private boolean restorePendingRecovery(Player player, boolean noisy) {
        boolean hadPending = escrow.hasPendingRecovery(player);
        boolean restored = escrow.restorePendingRecovery(player);
        if (restored && noisy) {
            player.sendMessage(MessageUtil.success("Recovered your spin bet item."));
        } else if (hadPending && noisy) {
            player.sendMessage(MessageUtil.warn("Clear more space, then use <white>/spinbet claim</white>."));
        }
        return restored;
    }

    private void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        for (SpinInvite invite : new ArrayList<>(invitesByTarget.values())) {
            if (invite.expiresAt() > now) {
                continue;
            }
            removeInvite(invite);
            Player challenger = Bukkit.getPlayer(invite.challengerId());
            Player target = Bukkit.getPlayer(invite.targetId());
            if (challenger != null && challenger.isOnline()) {
                challenger.sendMessage(MessageUtil.warn("Spin bet invite expired."));
            }
            if (target != null && target.isOnline()) {
                target.sendMessage(MessageUtil.warn("Spin bet invite expired."));
            }
        }
    }

    private SpinInvite inviteFor(UUID targetId, String challengerName) {
        SpinInvite invite = invitesByTarget.get(targetId);
        if (invite == null || challengerName == null) {
            return null;
        }
        return invite.challengerName().equalsIgnoreCase(challengerName) ? invite : null;
    }

    private void removeInvite(SpinInvite invite) {
        invitesByTarget.remove(invite.targetId(), invite);
        outgoingInviteByChallenger.remove(invite.challengerId(), invite.targetId());
    }

    private boolean isBusy(UUID playerId) {
        return isInMatch(playerId) || invitesByTarget.containsKey(playerId) || outgoingInviteByChallenger.containsKey(playerId);
    }

    private boolean isInMatch(UUID playerId) {
        return playerMatch.containsKey(playerId);
    }

    private boolean hasValidHeldWager(Player player, boolean noisy) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isValidWager(held)) {
            if (noisy) {
                player.sendMessage(MessageUtil.warn("Hold the item you want to bet."));
            }
            return false;
        }
        if (isUnsafeWager(held)) {
            if (noisy) {
                player.sendMessage(MessageUtil.warn("Storage and protected unique items cannot be bet."));
            }
            return false;
        }
        if (escrow.hasAnyEscrowMarker(held)) {
            if (noisy) {
                player.sendMessage(MessageUtil.warn("That item is still locked. Try <white>/spinbet claim</white> or <white>/blackjack claim</white>."));
            }
            return false;
        }
        if (escrow.hasMenuPreviewMarker(held)) {
            if (noisy) {
                player.sendMessage(MessageUtil.warn("That menu item cannot be bet."));
            }
            return false;
        }
        return true;
    }

    private boolean isReadyForWager(Player player) {
        restorePendingRecovery(player, false);
        if (!player.isOnline() || player.isDead()) {
            player.sendMessage(MessageUtil.warn("You cannot spin bet right now."));
            return false;
        }
        if (player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {
            player.sendMessage(MessageUtil.warn("Close your open menu first."));
            return false;
        }
        if (!escrow.isEmpty(player.getItemOnCursor())) {
            player.sendMessage(MessageUtil.warn("Clear your cursor first."));
            return false;
        }
        return hasValidHeldWager(player, true);
    }

    private void fill(Inventory inventory) {
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack playerHead(Player player, String name, String line) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(MM.deserialize(name));
        meta.lore(CustomLoreUtil.wrapLoreLines(List.of(MM.deserialize(line))));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack previewWager(ItemStack wager, String ownerName, String color) {
        ItemStack item = escrow.cleanEscrowMarkers(wager);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(MM.deserialize(color + miniEscape(ownerName) + "'s bet</" + color.substring(1)));
        meta.lore(CustomLoreUtil.wrapLoreLines(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream().map(MM::deserialize).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void sanitizeOrphanedEscrowMarkers(Player player) {
        escrow.sanitizeOrphanedEscrowMarkers(player);
    }

    private boolean isValidWager(ItemStack item) {
        return !escrow.isEmpty(item) && !escrow.hasMenuPreviewMarker(item);
    }

    private boolean isUnsafeWager(ItemStack item) {
        if (escrow.isEmpty(item)) {
            return true;
        }
        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) {
            return true;
        }
        if (plugin.getLegendaryListener() != null
            && (plugin.getLegendaryListener().isLegendaryItem(item)
                || plugin.getLegendaryListener().isEnderBoneItem(item)
                || plugin.getLegendaryListener().isOrbOfTheMysticsItem(item))) {
            return true;
        }
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSeasonRelic(item)) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta) {
            return true;
        }
        return meta instanceof BlockStateMeta blockStateMeta
            && blockStateMeta.getBlockState() instanceof InventoryHolder;
    }

    private boolean isCombatTagged(Player player) {
        return plugin.getCombatLogListener() != null
            && plugin.getCombatLogListener().isInPlayerCombat(player);
    }

    private Player findOnlinePlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).equals(normalized)) {
                return player;
            }
        }
        return null;
    }

    private String miniEscape(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("<", "\\<");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        UUID matchId = playerMatch.get(player.getUniqueId());
        if (matchId == null) {
            return;
        }
        event.setCancelled(true);
        SpinMatch match = matches.get(matchId);
        if (match != null && !match.committed && !match.settled
            && !match.decisionPending.contains(player.getUniqueId())
            && event.getClickedInventory() != null
            && event.getClickedInventory().equals(match.inventory)) {
            if (event.getRawSlot() == CONFIRM_SLOT && !match.confirmations.contains(player.getUniqueId())) {
                match.decisionPending.add(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> confirmMatch(match, player));
            } else if (event.getRawSlot() == DENY_SLOT) {
                match.decisionPending.add(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> denyMatch(match, player));
            }
        }
        scheduleInventoryRefresh(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !playerMatch.containsKey(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        scheduleInventoryRefresh(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || shuttingDown) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (!(holder instanceof SpinBetHolder spinHolder)) {
            return;
        }
        SpinMatch match = matches.get(spinHolder.matchId());
        if (match == null || match.settled || !playerMatch.containsKey(player.getUniqueId())) {
            return;
        }
        scheduleInventoryReopen(player, match);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (!playerMatch.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn("Finish the spin bet first."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!playerMatch.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn("Finish the spin bet first."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID matchId = playerMatch.get(event.getPlayer().getUniqueId());
        if (matchId == null) {
            return;
        }
        SpinMatch match = matches.get(matchId);
        if (match != null) {
            forfeitMatch(match, event.getPlayer().getUniqueId(), "player died");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !playerMatch.containsKey(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        handleLeave(event.getPlayer(), "player quit");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        handleLeave(event.getPlayer(), "player kicked");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        restorePendingRecovery(event.getPlayer(), true);
        sanitizeOrphanedEscrowMarkers(event.getPlayer());
    }

    private void handleLeave(Player player, String reason) {
        clearPendingUiTasks(player.getUniqueId());
        UUID matchId = playerMatch.get(player.getUniqueId());
        if (matchId != null) {
            SpinMatch match = matches.get(matchId);
            if (match != null) {
                forfeitMatch(match, player.getUniqueId(), reason);
            }
        }
        UUID outgoingTarget = outgoingInviteByChallenger.remove(player.getUniqueId());
        if (outgoingTarget != null) {
            invitesByTarget.remove(outgoingTarget);
        }
        SpinInvite incoming = invitesByTarget.remove(player.getUniqueId());
        if (incoming != null) {
            outgoingInviteByChallenger.remove(incoming.challengerId());
        }
    }

    private void scheduleInventoryRefresh(Player player) {
        UUID playerId = player.getUniqueId();
        if (pendingInventoryRefreshes.containsKey(playerId)) {
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, () -> {
            pendingInventoryRefreshes.remove(playerId);
            if (player.isOnline() && playerMatch.containsKey(playerId)) {
                player.updateInventory();
            }
        });
        pendingInventoryRefreshes.put(playerId, task);
    }

    private void scheduleInventoryReopen(Player player, SpinMatch match) {
        UUID playerId = player.getUniqueId();
        if (pendingInventoryReopens.containsKey(playerId)) {
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, () -> {
            pendingInventoryReopens.remove(playerId);
            if (!player.isOnline() || match.settled || !match.matchId().equals(playerMatch.get(playerId))) {
                return;
            }
            if (!player.getOpenInventory().getTopInventory().equals(match.inventory)) {
                player.openInventory(match.inventory);
            }
        });
        pendingInventoryReopens.put(playerId, task);
    }

    private void clearPendingUiTasks(UUID playerId) {
        BukkitTask refresh = pendingInventoryRefreshes.remove(playerId);
        if (refresh != null) {
            refresh.cancel();
        }
        BukkitTask reopen = pendingInventoryReopens.remove(playerId);
        if (reopen != null) {
            reopen.cancel();
        }
    }

    private void cancelPendingUiTasks() {
        pendingInventoryRefreshes.values().forEach(BukkitTask::cancel);
        pendingInventoryRefreshes.clear();
        pendingInventoryReopens.values().forEach(BukkitTask::cancel);
        pendingInventoryReopens.clear();
    }

    private record SpinInvite(UUID challengerId, String challengerName, UUID targetId, String targetName, long expiresAt) {
        private boolean expired() {
            return expiresAt <= System.currentTimeMillis();
        }
    }

    private record SpinBetHolder(UUID matchId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class SpinMatch {
        private final UUID matchId;
        private final UUID challengerId;
        private final UUID targetId;
        private final EscrowedItem left;
        private final EscrowedItem right;
        private final boolean challengerWins;
        private Inventory inventory;
        private BukkitTask task;
        private int step;
        private boolean settled;
        private boolean committed;
        private final Set<UUID> confirmations = new HashSet<>();
        private final Set<UUID> decisionPending = new HashSet<>();

        private SpinMatch(UUID matchId, UUID challengerId, UUID targetId, EscrowedItem left, EscrowedItem right, boolean challengerWins) {
            this.matchId = matchId;
            this.challengerId = challengerId;
            this.targetId = targetId;
            this.left = left;
            this.right = right;
            this.challengerWins = challengerWins;
        }

        private UUID matchId() {
            return matchId;
        }

        private UUID challengerId() {
            return challengerId;
        }

        private UUID targetId() {
            return targetId;
        }

        private EscrowedItem left() {
            return left;
        }

        private EscrowedItem right() {
            return right;
        }

        private boolean challengerWins() {
            return challengerWins;
        }

        private boolean hasPlayer(UUID playerId) {
            return challengerId.equals(playerId) || targetId.equals(playerId);
        }
    }
}
