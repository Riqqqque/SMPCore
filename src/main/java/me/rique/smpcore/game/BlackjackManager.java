package me.rique.smpcore.game;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlackjackManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int INVENTORY_SIZE = 54;
    private static final int[] DEALER_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int[] PLAYER_SLOTS = {27, 28, 29, 30, 31, 32, 33, 34, 35};
    private static final int INFO_SLOT = 4;
    private static final int DEALER_TOTAL_SLOT = 22;
    private static final int STATUS_SLOT = 23;
    private static final int PLAYER_TOTAL_SLOT = 40;
    private static final int PLAYER_HEAD_SLOT = 48;
    private static final int BET_SLOT = 49;
    private static final int DEALER_HEAD_SLOT = 50;
    private static final int HIT_SLOT = 45;
    private static final int STAND_SLOT = 53;
    private static final int DEAL_TICKS = 9;
    private static final int DEALER_TICKS = 13;
    private static final UUID HOUSE_ESCROW_OWNER_ID = new UUID(0L, 0L);
    private static final String HOUSE_ESCROW_OWNER_NAME = "Blackjack House";

    private final SMPCore plugin;
    private final ItemEscrowService escrow;
    private final Map<UUID, BlackjackGame> games = new ConcurrentHashMap<>();
    private final Set<UUID> pendingDealerStarts = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingGameActions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> pendingInventoryRefreshes = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingInventoryReopens = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;
    private boolean shuttingDown;

    public BlackjackManager(SMPCore plugin) {
        this.plugin = plugin;
        this.escrow = new ItemEscrowService(plugin, "blackjack", "blackjack-escrow.yml");
    }

    public void start() {
        escrow.start(Bukkit.getOnlinePlayers());
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupOfflineGames, 20L * 20L, 20L * 20L);
    }

    public void shutdown() {
        shuttingDown = true;
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        for (BlackjackGame game : new ArrayList<>(games.values())) {
            cancelGame(game, "Plugin shut down.", false);
        }
        pendingInventoryRefreshes.values().forEach(BukkitTask::cancel);
        pendingInventoryRefreshes.clear();
        pendingInventoryReopens.values().forEach(BukkitTask::cancel);
        pendingInventoryReopens.clear();
        pendingDealerStarts.clear();
        pendingGameActions.clear();
        escrow.shutdown();
    }

    public boolean startGame(Player player, Material material, int amount) {
        restorePendingRecovery(player, false);
        sanitizeOrphanedEscrowMarkers(player);
        if (isCombatTagged(player)) {
            player.sendMessage(MessageUtil.warn("You cannot start blackjack while in combat."));
            return false;
        }
        if (!isAllowedCurrency(material)) {
            player.sendMessage(MessageUtil.warn("Use iron, gold, diamonds, or netherite scrap."));
            return false;
        }
        if (amount <= 0 || amount > material.getMaxStackSize()) {
            player.sendMessage(MessageUtil.warn("Bet between 1 and " + material.getMaxStackSize() + "."));
            return false;
        }
        if (games.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Finish your blackjack hand first."));
            return false;
        }
        if (!isReady(player)) {
            return false;
        }

        BetSlot betSlot = findBetSlot(player, material, amount);
        if (betSlot == null) {
            player.sendMessage(MessageUtil.warn("Put the full bet in one plain stack first."));
            return false;
        }

        UUID gameId = UUID.randomUUID();
        EscrowedItem wager = escrow.capturePartial(gameId, player, betSlot.slot(), betSlot.item(), amount);
        if (wager == null) {
            player.sendMessage(MessageUtil.error("Could not lock your blackjack bet."));
            return false;
        }

        BlackjackGame game = new BlackjackGame(gameId, player.getUniqueId(), player.getName(), wager, shuffledDeck());
        game.inventory = createInventory(game, player);
        games.put(player.getUniqueId(), game);
        player.openInventory(game.inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.75f, 1.2f);
        game.committed = true;
        startDeal(game);
        return true;
    }

    public void openDealer(Player player) {
        if (player == null || !player.isOnline()) return;
        if (games.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Finish your blackjack hand first."));
            return;
        }
        Inventory inventory = Bukkit.createInventory(
            new DealerMenuHolder(player.getUniqueId()),
            45,
            BedrockCompat.menuTitle(player, MM.deserialize("<gold><bold>Silas's Blackjack</bold></gold>"), "Blackjack Dealer")
        );
        fill(inventory);
        inventory.setItem(4, menuItem(Material.BOOK, "<gold><bold>Choose a Bet</bold></gold>", List.of(
            "<gray>Get closer to 21 than Silas.</gray>",
            "<gray>Win pays 2x. Ties return the bet.</gray>",
            "<dark_gray>The full bet must be one plain stack.</dark_gray>"
        )));
        Material[] materials = {Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.NETHERITE_SCRAP};
        int[] amounts = {1, 4, 8, 16, 32, 64};
        for (int row = 0; row < materials.length; row++) {
            for (int column = 0; column < amounts.length; column++) {
                int amount = amounts[column];
                Material material = materials[row];
                inventory.setItem(10 + row * 9 + column, menuItem(material,
                    "<yellow><bold>Bet " + amount + "</bold></yellow>",
                    List.of("<gray>Wager: <white>" + amount + " " + currencyName(material, amount) + "</white></gray>", "<green>Win: " + (amount * 2) + "</green>")));
            }
        }
        inventory.setItem(44, menuItem(Material.CHEST, "<aqua>Recover Pending Items</aqua>", List.of("<gray>Claims an interrupted blackjack payout.</gray>")));
        player.openInventory(inventory);
    }

    private DealerBet dealerBet(int slot) {
        if (slot == 44) return null;
        int row = slot / 9 - 1;
        int column = slot % 9 - 1;
        if (row < 0 || row > 3 || column < 0 || column > 5) return null;
        Material[] materials = {Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.NETHERITE_SCRAP};
        int[] amounts = {1, 4, 8, 16, 32, 64};
        return new DealerBet(materials[row], amounts[column]);
    }

    public boolean claim(Player player, boolean noisy) {
        boolean hadPending = escrow.hasPendingRecovery(player);
        boolean restored = restorePendingRecovery(player, noisy);
        sanitizeOrphanedEscrowMarkers(player);
        if (!restored && noisy && !hadPending) {
            player.sendMessage(MessageUtil.info("No blackjack items are waiting."));
        }
        return restored;
    }

    private Inventory createInventory(BlackjackGame game, Player player) {
        Inventory inventory = Bukkit.createInventory(
            new BlackjackHolder(game.gameId()),
            INVENTORY_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gold><bold>Blackjack</bold></gold>"), "Blackjack")
        );
        game.inventory = inventory;
        game.phase = Phase.DEALING;
        render(game, player);
        return inventory;
    }

    private void startDeal(BlackjackGame game) {
        game.phase = Phase.DEALING;
        game.dealStep = 0;
        game.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player player = Bukkit.getPlayer(game.playerId());
            if (player == null || !player.isOnline() || player.isDead()) {
                forfeitGame(game, "player left during deal", true);
                return;
            }
            switch (game.dealStep) {
                case 0 -> game.playerCards().add(draw(game));
                case 1 -> game.dealerCards().add(draw(game));
                case 2 -> game.playerCards().add(draw(game));
                case 3 -> game.dealerCards().add(draw(game));
                default -> {
                    stopTask(game);
                    finishInitialDeal(game, player);
                    return;
                }
            }
            game.dealStep++;
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.65f, 1.1f + (game.dealStep * 0.08f));
            render(game, player);
        }, 0L, DEAL_TICKS);
    }

    private void finishInitialDeal(BlackjackGame game, Player player) {
        game.revealDealerHole = false;
        boolean playerBlackjack = isBlackjack(game.playerCards());
        boolean dealerBlackjack = isBlackjack(game.dealerCards());
        if (playerBlackjack || dealerBlackjack) {
            game.revealDealerHole = true;
            if (playerBlackjack && dealerBlackjack) {
                settle(game, Result.PUSH, "Both hit blackjack.");
            } else if (playerBlackjack) {
                settle(game, Result.WIN, "Blackjack.");
            } else {
                settle(game, Result.LOSE, "Dealer blackjack.");
            }
            return;
        }
        game.phase = Phase.PLAYER_TURN;
        render(game, player);
        player.sendMessage(MessageUtil.info("Hit or stand."));
    }

    private void hit(BlackjackGame game, Player player) {
        if (game.phase() != Phase.PLAYER_TURN) {
            return;
        }
        game.playerCards().add(draw(game));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.35f);
        int total = handValue(game.playerCards());
        if (total > 21) {
            game.phase = Phase.DEALER_TURN;
            game.revealDealerHole = true;
            render(game, player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> settle(game, Result.LOSE, "Bust."), 10L);
        } else if (total == 21) {
            render(game, player);
            stand(game, player);
        } else {
            render(game, player);
        }
    }

    private void stand(BlackjackGame game, Player player) {
        if (game.phase() != Phase.PLAYER_TURN) {
            return;
        }
        game.phase = Phase.DEALER_TURN;
        game.revealDealerHole = true;
        render(game, player);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.85f);
        startDealerTurn(game);
    }

    private void startDealerTurn(BlackjackGame game) {
        stopTask(game);
        game.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player player = Bukkit.getPlayer(game.playerId());
            if (player == null || !player.isOnline() || player.isDead()) {
                forfeitGame(game, "player left during dealer turn", true);
                return;
            }
            if (dealerShouldHit(game.dealerCards())) {
                game.dealerCards().add(draw(game));
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.65f, 0.9f);
                render(game, player);
                return;
            }
            stopTask(game);
            settle(game, compareHands(game), resultReason(game));
        }, DEALER_TICKS, DEALER_TICKS);
    }

    private void settle(BlackjackGame game, Result result, String reason) {
        if (game.settled) {
            return;
        }
        game.settled = true;
        game.phase = Phase.SETTLED;
        game.revealDealerHole = true;
        stopTask(game);
        games.remove(game.playerId(), game);
        clearPlayerTasks(game.playerId());

        Player player = Bukkit.getPlayer(game.playerId());
        boolean canNotify = player != null && player.isOnline();
        if (canNotify) {
            render(game, player);
        }
        int betAmount = game.wager().item().getAmount();
        int payout = payoutAmount(result, betAmount);
        Material material = game.wager().item().getType();
        boolean claimNeeded = false;
        if (payout <= 0) {
            consumeHouseWager(game.wager(), "settling losing hand " + game.gameId());
        } else {
            List<EscrowedItem> payoutRecords = escrow.replaceWithRecoveries(
                game.wager(),
                game.playerId(),
                game.playerName(),
                splitPayoutStacks(material, payout),
                "PAYOUT"
            );
            if (payoutRecords.isEmpty()) {
                escrow.queueRecovery(game.playerId(), game.playerName(), game.wager());
                claimNeeded = true;
            } else if (!canNotify || !deliverPayout(player, payoutRecords)) {
                if (!canNotify) {
                    queuePayout(game.playerId(), game.playerName(), payoutRecords);
                }
                claimNeeded = true;
            }
        }

        if (!canNotify) {
            plugin.getLogger().info("Blackjack " + game.gameId() + " settled while " + game.playerName()
                + " was offline: " + result.name().toLowerCase(Locale.ROOT) + ".");
            scheduleClose(game);
            return;
        }

        switch (result) {
            case WIN -> {
                BedrockCompat.sendGameMessage(player, claimNeeded
                    ? MessageUtil.success("You won blackjack. Use <white>/blackjack claim</white> for the payout.")
                    : MessageUtil.success("You won blackjack: " + payout + " " + currencyName(material, payout) + "."));
                playWinEffects(player);
                announceTeamResult(player, material, result, payout);
            }
            case PUSH -> {
                BedrockCompat.sendGameMessage(player, claimNeeded
                    ? MessageUtil.info("Push. Use <white>/blackjack claim</white> for your bet.")
                    : MessageUtil.info("Push. Your bet was returned."));
                announceTeamResult(player, material, result, betAmount);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.65f, 1.0f);
            }
            case LOSE -> {
                BedrockCompat.sendGameMessage(player, MessageUtil.warn("Dealer wins. " + reason));
                announceTeamResult(player, material, result, betAmount);
                playLoseEffects(player);
            }
        }
        scheduleClose(game);
    }

    private Result compareHands(BlackjackGame game) {
        return compareTotals(handValue(game.playerCards()), handValue(game.dealerCards()));
    }

    private String resultReason(BlackjackGame game) {
        int dealerTotal = handValue(game.dealerCards());
        if (dealerTotal > 21) {
            return "Dealer bust.";
        }
        return "Dealer had " + dealerTotal + ".";
    }

    private void render(BlackjackGame game, Player player) {
        Inventory inventory = game.inventory();
        if (inventory == null) {
            return;
        }
        fill(inventory);
        inventory.setItem(INFO_SLOT, menuItem(Material.BOOK, "<gold><bold>How to win</bold></gold>", List.of(
            "<gray>Get closer to <white>21</white> than the dealer.</gray>",
            "<gray>Over <white>21</white> busts.</gray>",
            "<gray>J, Q, and K count as <white>10</white>.</gray>",
            "<gray>Aces count as <white>11</white>, or <white>1</white> when needed.</gray>",
            "<gray>Ties return the bet.</gray>"
        )));
        inventory.setItem(DEALER_TOTAL_SLOT, menuItem(Material.SKELETON_SKULL, "<red><bold>Dealer</bold></red>", dealerLore(game)));
        inventory.setItem(PLAYER_TOTAL_SLOT, menuItem(Material.PLAYER_HEAD, "<green><bold>Your hand</bold></green>", handLore(game.playerCards())));
        inventory.setItem(PLAYER_HEAD_SLOT, playerHead(player));
        inventory.setItem(DEALER_HEAD_SLOT, menuItem(Material.SKELETON_SKULL, "<red><bold>Dealer</bold></red>", List.of(
            "<gray>Draws below 17.</gray>",
            "<gray>Stands on every 17, including soft 17.</gray>"
        )));
        inventory.setItem(BET_SLOT, betItem(game));
        inventory.setItem(STATUS_SLOT, statusItem(game));
        renderCards(inventory, DEALER_SLOTS, game.dealerCards(), !game.revealDealerHole());
        renderCards(inventory, PLAYER_SLOTS, game.playerCards(), false);
        renderButtons(inventory, game.phase() == Phase.PLAYER_TURN);
    }

    private List<String> dealerLore(BlackjackGame game) {
        if (!game.revealDealerHole() && game.dealerCards().size() > 1) {
            return List.of(
                "<gray>Visible card: <white>" + cardValue(game.dealerCards().get(0)) + "</white></gray>",
                "<dark_gray>The hidden card is not included.</dark_gray>"
            );
        }
        return handLore(game.dealerCards());
    }

    private List<String> handLore(List<Card> cards) {
        HandScore score = scoreHand(cards);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Total: <white>" + score.total() + "</white></gray>");
        if (score.soft()) {
            lore.add("<aqua>Soft hand: an Ace currently counts as 11.</aqua>");
        } else if (cards.stream().anyMatch(card -> card.rank() == Rank.ACE)) {
            lore.add("<gray>Aces have adjusted to 1 where needed.</gray>");
        }
        return lore;
    }

    private ItemStack betItem(BlackjackGame game) {
        Material material = game.wager().item().getType();
        int amount = game.wager().item().getAmount();
        return menuItem(material, "<yellow><bold>Bet</bold></yellow>", List.of(
            "<gray>" + amount + " " + currencyName(material, amount) + "</gray>",
            "<gray>Win pays <white>" + (amount * 2) + "</white>.</gray>"
        ));
    }

    private ItemStack statusItem(BlackjackGame game) {
        return switch (game.phase()) {
            case DEALING -> menuItem(Material.CLOCK, "<yellow><bold>Dealing...</bold></yellow>", List.of("<gray>Cards are coming out.</gray>"));
            case PLAYER_TURN -> menuItem(Material.LIME_DYE, "<green><bold>Your move</bold></green>", List.of("<gray>Hit or stand.</gray>"));
            case DEALER_TURN -> handValue(game.playerCards()) > 21
                ? menuItem(Material.RED_DYE, "<red><bold>Bust</bold></red>", List.of("<gray>Your hand went over 21.</gray>"))
                : menuItem(Material.YELLOW_DYE, "<yellow><bold>Dealer turn</bold></yellow>", List.of("<gray>Dealer draws below 17.</gray>"));
            case SETTLED -> {
                int playerTotal = handValue(game.playerCards());
                int dealerTotal = handValue(game.dealerCards());
                yield menuItem(Material.NETHER_STAR, "<gold><bold>Result</bold></gold>", List.of(
                    "<gray>You: <white>" + playerTotal + "</white></gray>",
                    "<gray>Dealer: <white>" + dealerTotal + "</white></gray>"
                ));
            }
        };
    }

    private void renderCards(Inventory inventory, int[] slots, List<Card> cards, boolean hideHole) {
        HandScore score = scoreHand(cards);
        boolean hasOverflow = cards.size() > slots.length;
        int directCardSlots = hasOverflow ? slots.length - 1 : slots.length;
        for (int i = 0; i < slots.length; i++) {
            if (hasOverflow && i == slots.length - 1) {
                inventory.setItem(slots[i], overflowCardsItem(cards, score, directCardSlots));
                continue;
            }
            if (i >= cards.size()) {
                inventory.setItem(slots[i], menuItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Empty</dark_gray>", List.of()));
                continue;
            }
            if (hideHole && i == 1) {
                inventory.setItem(slots[i], menuItem(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray><bold>Hidden Card</bold></dark_gray>", List.of(
                    "<gray>Dealer reveals this after you stand.</gray>"
                )));
                continue;
            }
            boolean revealCountedValue = !hideHole;
            int countedValue = revealCountedValue ? score.countedValues().get(i) : cardValue(cards.get(i));
            inventory.setItem(slots[i], cardItem(cards.get(i), countedValue, revealCountedValue));
        }
    }

    private ItemStack cardItem(Card card, int countedValue, boolean revealCountedValue) {
        String color = card.red() ? "<red>" : "<white>";
        List<String> lore = new ArrayList<>();
        if (card.rank() == Rank.ACE) {
            lore.add("<gray>Ace value: <white>11 or 1</white>.</gray>");
            if (revealCountedValue) lore.add("<aqua>Counts as " + countedValue + " in this hand.</aqua>");
        } else if (cardValue(card) == 10 && card.rank() != Rank.TEN) {
            lore.add("<gray>Face card: counts as <white>10</white>.</gray>");
        } else {
            lore.add("<gray>Counts as <white>" + countedValue + "</white>.</gray>");
        }
        return menuItem(Material.PAPER, color + "<bold>" + card.rank().display() + " " + card.suit().display() + "</bold></" + color.substring(1), lore);
    }

    private ItemStack overflowCardsItem(List<Card> cards, HandScore score, int startIndex) {
        int hidden = cards.size() - startIndex;
        List<String> lore = new ArrayList<>();
        for (int i = startIndex; i < cards.size(); i++) {
            Card card = cards.get(i);
            lore.add("<gray>" + card.rank().display() + " " + card.suit().display() + ": <white>"
                + score.countedValues().get(i) + "</white></gray>");
        }
        lore.add("<dark_gray>All listed cards are included in the total.</dark_gray>");
        return menuItem(Material.BUNDLE, "<yellow><bold>+" + hidden + " More " + (hidden == 1 ? "Card" : "Cards") + "</bold></yellow>", lore);
    }

    private void renderButtons(Inventory inventory, boolean active) {
        if (active) {
            inventory.setItem(HIT_SLOT, menuItem(Material.LIME_CONCRETE, "<green><bold>Hit</bold></green>", List.of("<gray>Take one more card.</gray>")));
            inventory.setItem(STAND_SLOT, menuItem(Material.RED_CONCRETE, "<red><bold>Stand</bold></red>", List.of("<gray>Keep this hand.</gray>")));
            return;
        }
        inventory.setItem(HIT_SLOT, menuItem(Material.GRAY_CONCRETE, "<dark_gray>Hit</dark_gray>", List.of("<gray>Wait for your turn.</gray>")));
        inventory.setItem(STAND_SLOT, menuItem(Material.GRAY_CONCRETE, "<dark_gray>Stand</dark_gray>", List.of("<gray>Wait for your turn.</gray>")));
    }

    private void fill(Inventory inventory) {
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack playerHead(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(MM.deserialize("<green><bold>" + miniEscape(player.getName()) + "</bold></green>"));
        meta.lore(List.of(MM.deserialize("<gray>Playing blackjack</gray>")));
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

    private boolean deliverPayout(Player player, List<EscrowedItem> payoutRecords) {
        boolean deliveredAll = true;
        for (EscrowedItem payoutRecord : payoutRecords) {
            if (!escrow.give(player, payoutRecord)) {
                escrow.queueRecovery(player.getUniqueId(), player.getName(), payoutRecord);
                deliveredAll = false;
            }
        }
        player.updateInventory();
        return deliveredAll;
    }

    private void queuePayout(UUID playerId, String playerName, List<EscrowedItem> payoutRecords) {
        for (EscrowedItem payoutRecord : payoutRecords) {
            escrow.queueRecovery(playerId, playerName, payoutRecord);
        }
    }

    static List<ItemStack> splitPayoutStacks(Material material, int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int moved : splitStackAmounts(amount, material.getMaxStackSize())) {
            stacks.add(new ItemStack(material, moved));
        }
        return stacks;
    }

    static List<Integer> splitStackAmounts(int amount, int maxStackSize) {
        List<Integer> amounts = new ArrayList<>();
        int remaining = amount;
        int max = Math.max(1, maxStackSize);
        while (remaining > 0) {
            int moved = Math.min(max, remaining);
            amounts.add(moved);
            remaining -= moved;
        }
        return amounts;
    }

    private void playWinEffects(Player player) {
        var location = player.getLocation();
        var effect = location.clone().add(0.0, 1.25, 0.0);
        var world = player.getWorld();
        world.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, 1.15f);
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.65f, 1.4f);
        world.spawnParticle(Particle.FIREWORK, effect, 48, 0.55, 0.55, 0.55, 0.06);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, effect, 20, 0.35, 0.45, 0.35, 0.03);
        world.spawnParticle(Particle.FLASH, effect, 1, 0.0, 0.0, 0.0, 0.0, Color.WHITE);
    }

    private void playLoseEffects(Player player) {
        var location = player.getLocation();
        var effect = location.clone().add(0.0, 1.0, 0.0);
        var world = player.getWorld();
        world.playSound(location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.65f, 0.55f);
        world.spawnParticle(Particle.SMOKE, effect, 22, 0.35, 0.35, 0.35, 0.03);
        world.spawnParticle(Particle.ASH, effect, 12, 0.35, 0.2, 0.35, 0.02);
    }

    private void announceTeamResult(Player player, Material material, Result result, int amount) {
        if (plugin.getTeamManager() == null) {
            return;
        }
        String playerName = "<white>" + miniEscape(player.getName()) + "</white>";
        String value = "<white>" + amount + " " + currencyName(material, amount) + "</white>";
        Component message = switch (result) {
            case WIN -> MessageUtil.success(playerName + " won " + value + " in blackjack.");
            case PUSH -> MessageUtil.info(playerName + " pushed in blackjack and got " + value + " back.");
            case LOSE -> MessageUtil.warn(playerName + " lost " + value + " in blackjack.");
        };
        plugin.getTeamManager().notifyPlayerTeamGameResult(player.getUniqueId(), message);
    }

    private boolean isReady(Player player) {
        if (!player.isOnline() || player.isDead()) {
            player.sendMessage(MessageUtil.warn("You cannot play blackjack right now."));
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
        return true;
    }

    private boolean isCombatTagged(Player player) {
        return plugin.getCombatLogListener() != null
            && plugin.getCombatLogListener().isInPlayerCombat(player);
    }

    private BetSlot findBetSlot(Player player, Material material, int amount) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (escrow.isEmpty(item) || item.getType() != material || item.getAmount() < amount) {
                continue;
            }
            if (!isPlainCurrencyStack(item)) {
                continue;
            }
            return new BetSlot(slot, item.clone());
        }
        return null;
    }

    private boolean isPlainCurrencyStack(ItemStack item) {
        if (escrow.isEmpty(item) || !isAllowedCurrency(item.getType())) {
            return false;
        }
        if (escrow.hasAnyEscrowMarker(item) || escrow.hasMenuPreviewMarker(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        return !meta.hasDisplayName()
            && !meta.hasLore()
            && !meta.hasEnchants()
            && meta.getPersistentDataContainer().getKeys().isEmpty();
    }

    private boolean isAllowedCurrency(Material material) {
        return material == Material.IRON_INGOT
            || material == Material.GOLD_INGOT
            || material == Material.DIAMOND
            || material == Material.NETHERITE_SCRAP;
    }

    private boolean restorePendingRecovery(Player player, boolean noisy) {
        boolean hadPending = escrow.hasPendingRecovery(player);
        boolean restored = escrow.restorePendingRecovery(player);
        if (restored && noisy) {
            player.sendMessage(MessageUtil.success("Recovered your blackjack item."));
        } else if (hadPending && noisy) {
            player.sendMessage(MessageUtil.warn("Clear more space, then use <white>/blackjack claim</white>."));
        }
        return restored;
    }

    private void sanitizeOrphanedEscrowMarkers(Player player) {
        escrow.sanitizeOrphanedEscrowMarkers(player);
    }

    private boolean returnEscrowToOwner(EscrowedItem wager, boolean notify) {
        escrow.retarget(wager, wager.ownerId(), wager.ownerName(), "RETURNING");
        Player owner = Bukkit.getPlayer(wager.ownerId());
        if (owner != null && owner.isOnline() && !owner.isDead() && escrow.give(owner, wager)) {
            if (notify) {
                owner.sendMessage(MessageUtil.info("Your blackjack bet was returned."));
            }
            return true;
        }
        escrow.queueRecovery(wager.ownerId(), wager.ownerName(), wager);
        return false;
    }

    private boolean consumeHouseWager(EscrowedItem wager, String reason) {
        if (escrow.consume(wager)) {
            return true;
        }
        boolean quarantined = escrow.retarget(
            wager,
            HOUSE_ESCROW_OWNER_ID,
            HOUSE_ESCROW_OWNER_NAME,
            "FORFEITED"
        );
        if (!quarantined) {
            escrow.queueRecovery(HOUSE_ESCROW_OWNER_ID, HOUSE_ESCROW_OWNER_NAME, wager);
            plugin.getLogger().severe("Could not confirm quarantine of blackjack wager "
                + wager.escrowId() + " while " + reason + "; queued it to the house recovery sink.");
        }
        return quarantined;
    }

    private void forfeitGame(BlackjackGame game, String reason, boolean notify) {
        if (game == null || game.settled) {
            return;
        }
        if (!game.committed) {
            cancelGame(game, reason, notify);
            return;
        }
        game.settled = true;
        game.phase = Phase.SETTLED;
        game.revealDealerHole = true;
        stopTask(game);
        games.remove(game.playerId(), game);
        clearPlayerTasks(game.playerId());
        consumeHouseWager(game.wager(), "recording forfeit for " + game.playerName());

        Player player = Bukkit.getPlayer(game.playerId());
        if (notify && player != null && player.isOnline()) {
            render(game, player);
            player.sendMessage(MessageUtil.warn("Blackjack forfeited. Your bet was lost."));
            playLoseEffects(player);
        }
        plugin.getLogger().info("Blackjack " + game.gameId() + " forfeited by "
            + game.playerName() + ": " + reason);
        scheduleClose(game);
    }

    private void cancelGame(BlackjackGame game, String reason, boolean notify) {
        if (game == null || game.settled) {
            return;
        }
        game.settled = true;
        stopTask(game);
        games.remove(game.playerId(), game);
        clearPlayerTasks(game.playerId());
        boolean returned = returnEscrowToOwner(game.wager(), false);
        if (notify) {
            Player player = Bukkit.getPlayer(game.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(MessageUtil.warn(returned
                    ? "Blackjack canceled. Bet returned."
                    : "Blackjack canceled. Use <white>/blackjack claim</white> when you have space."));
            }
        }
        plugin.getLogger().info("Blackjack " + game.gameId() + " canceled: " + reason);
        scheduleClose(game);
    }

    private void cleanupOfflineGames() {
        for (BlackjackGame game : new ArrayList<>(games.values())) {
            Player player = Bukkit.getPlayer(game.playerId());
            if (player == null || !player.isOnline()) {
                forfeitGame(game, "offline cleanup", false);
            }
        }
    }

    private void scheduleClose(BlackjackGame game) {
        if (game.inventory() == null || shuttingDown) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player viewer : new ArrayList<>(game.inventory().getViewers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .toList())) {
                if (viewer.isOnline() && viewer.getOpenInventory().getTopInventory().equals(game.inventory())) {
                    viewer.closeInventory();
                }
            }
        }, 20L * 4L);
    }

    private void stopTask(BlackjackGame game) {
        if (game.task != null) {
            game.task.cancel();
            game.task = null;
        }
    }

    private Card draw(BlackjackGame game) {
        if (game.deck().isEmpty()) {
            game.deck().addAll(shuffledDeck());
        }
        return game.deck().remove(0);
    }

    static List<Card> standardDeck() {
        List<Card> deck = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(rank, suit));
            }
        }
        return deck;
    }

    private List<Card> shuffledDeck() {
        List<Card> deck = standardDeck();
        Collections.shuffle(deck);
        return deck;
    }

    static HandScore scoreHand(List<Card> cards) {
        return scoreBaseValues(cards.stream().map(BlackjackManager::cardValue).toList());
    }

    static HandScore scoreBaseValues(List<Integer> baseValues) {
        List<Integer> countedValues = new ArrayList<>(baseValues);
        int total = 0;
        List<Integer> aceIndexes = new ArrayList<>();
        for (int index = 0; index < countedValues.size(); index++) {
            int value = countedValues.get(index);
            total += value;
            if (value == 11) aceIndexes.add(index);
        }
        int adjustedAces = 0;
        while (total > 21 && adjustedAces < aceIndexes.size()) {
            countedValues.set(aceIndexes.get(adjustedAces), 1);
            total -= 10;
            adjustedAces++;
        }
        boolean soft = aceIndexes.stream().anyMatch(index -> countedValues.get(index) == 11);
        return new HandScore(total, soft, List.copyOf(countedValues));
    }

    static int handValue(List<Card> cards) {
        return scoreHand(cards).total();
    }

    static int cardValue(Card card) {
        return switch (card.rank()) {
            case ACE -> 11;
            case KING, QUEEN, JACK, TEN -> 10;
            case NINE -> 9;
            case EIGHT -> 8;
            case SEVEN -> 7;
            case SIX -> 6;
            case FIVE -> 5;
            case FOUR -> 4;
            case THREE -> 3;
            case TWO -> 2;
        };
    }

    static boolean isBlackjack(List<Card> cards) {
        return cards.size() == 2 && handValue(cards) == 21;
    }

    static boolean dealerShouldHit(List<Card> cards) {
        return handValue(cards) < 17;
    }

    static Result compareTotals(int playerTotal, int dealerTotal) {
        if (playerTotal > 21) return Result.LOSE;
        if (dealerTotal > 21 || playerTotal > dealerTotal) return Result.WIN;
        if (playerTotal == dealerTotal) return Result.PUSH;
        return Result.LOSE;
    }

    static int payoutAmount(Result result, int betAmount) {
        if (result == null || betAmount < 0) throw new IllegalArgumentException("invalid blackjack payout");
        return switch (result) {
            case WIN -> betAmount * 2;
            case PUSH -> betAmount;
            case LOSE -> 0;
        };
    }

    private String currencyName(Material material, int amount) {
        String base = switch (material) {
            case IRON_INGOT -> "iron ingot";
            case GOLD_INGOT -> "gold ingot";
            case DIAMOND -> "diamond";
            case NETHERITE_SCRAP -> "netherite scrap";
            default -> material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
        if (amount == 1 || base.endsWith("scrap")) {
            return base;
        }
        return base + "s";
    }

    private String miniEscape(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("<", "\\<");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (holder instanceof DealerMenuHolder dealerHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)
                || !dealerHolder.playerId.equals(player.getUniqueId())
                || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)
                || !MenuItemUtil.isVisibleItem(event.getCurrentItem())) return;
            if (event.getRawSlot() == 44) {
                claim(player, true);
                return;
            }
            DealerBet bet = dealerBet(event.getRawSlot());
            if (bet == null || !pendingDealerStarts.add(player.getUniqueId())) return;
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try { startGame(player, bet.material, bet.amount); }
                finally { pendingDealerStarts.remove(player.getUniqueId()); }
            });
            return;
        }
        if (!(holder instanceof BlackjackHolder blackjackHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        BlackjackGame game = games.get(player.getUniqueId());
        if (game == null || !game.gameId().equals(blackjackHolder.gameId())) {
            scheduleInventoryRefresh(player);
            return;
        }
        if (event.getRawSlot() < 0
            || event.getRawSlot() >= top.getSize()
            || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)
            || !MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            scheduleInventoryRefresh(player);
            return;
        }
        if ((event.getRawSlot() == HIT_SLOT || event.getRawSlot() == STAND_SLOT)
            && acceptsGameAction(game.phase() == Phase.PLAYER_TURN, pendingGameActions.contains(player.getUniqueId()))) {
            UUID playerId = player.getUniqueId();
            pendingGameActions.add(playerId);
            if (event.getRawSlot() == HIT_SLOT) {
                hit(game, player);
            } else {
                stand(game, player);
            }
            Bukkit.getScheduler().runTask(plugin, () -> pendingGameActions.remove(playerId));
        }
        scheduleInventoryRefresh(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof BlackjackHolder) && !(top.getHolder(false) instanceof DealerMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            scheduleInventoryRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || shuttingDown) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (!(holder instanceof BlackjackHolder blackjackHolder)) {
            return;
        }
        BlackjackGame game = games.get(player.getUniqueId());
        if (game == null || game.settled || !game.gameId().equals(blackjackHolder.gameId())) {
            return;
        }
        scheduleInventoryReopen(player, game);
    }

    private void scheduleInventoryRefresh(Player player) {
        UUID playerId = player.getUniqueId();
        if (pendingInventoryRefreshes.containsKey(playerId)) {
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, () -> {
            pendingInventoryRefreshes.remove(playerId);
            if (player.isOnline()) {
                player.updateInventory();
            }
        });
        pendingInventoryRefreshes.put(playerId, task);
    }

    private void scheduleInventoryReopen(Player player, BlackjackGame game) {
        UUID playerId = player.getUniqueId();
        if (pendingInventoryReopens.containsKey(playerId)) {
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTask(plugin, () -> {
            pendingInventoryReopens.remove(playerId);
            if (player.isOnline() && !game.settled) {
                player.openInventory(game.inventory());
            }
        });
        pendingInventoryReopens.put(playerId, task);
    }

    private void clearPlayerTasks(UUID playerId) {
        pendingGameActions.remove(playerId);
        BukkitTask refresh = pendingInventoryRefreshes.remove(playerId);
        if (refresh != null) refresh.cancel();
        BukkitTask reopen = pendingInventoryReopens.remove(playerId);
        if (reopen != null) reopen.cancel();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (!games.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn("Finish blackjack first."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!games.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(MessageUtil.warn("Finish blackjack first."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !games.containsKey(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        BlackjackGame game = games.get(event.getPlayer().getUniqueId());
        if (game != null) {
            forfeitGame(game, "player died", true);
        }
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
        pendingDealerStarts.remove(player.getUniqueId());
        clearPlayerTasks(player.getUniqueId());
        BlackjackGame game = games.get(player.getUniqueId());
        if (game != null) {
            forfeitGame(game, reason, true);
        }
    }

    static boolean acceptsGameAction(boolean playerTurn, boolean actionPending) {
        return playerTurn && !actionPending;
    }

    private record BetSlot(int slot, ItemStack item) {}

    private record DealerBet(Material material, int amount) {}

    private record DealerMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record BlackjackHolder(UUID gameId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class BlackjackGame {
        private final UUID gameId;
        private final UUID playerId;
        private final String playerName;
        private final EscrowedItem wager;
        private final List<Card> deck;
        private final List<Card> playerCards = new ArrayList<>();
        private final List<Card> dealerCards = new ArrayList<>();
        private Inventory inventory;
        private BukkitTask task;
        private int dealStep;
        private Phase phase = Phase.DEALING;
        private boolean revealDealerHole;
        private boolean settled;
        private boolean committed;

        private BlackjackGame(UUID gameId, UUID playerId, String playerName, EscrowedItem wager, List<Card> deck) {
            this.gameId = gameId;
            this.playerId = playerId;
            this.playerName = playerName;
            this.wager = wager;
            this.deck = deck;
        }

        private UUID gameId() {
            return gameId;
        }

        private UUID playerId() {
            return playerId;
        }

        private String playerName() {
            return playerName;
        }

        private EscrowedItem wager() {
            return wager;
        }

        private List<Card> deck() {
            return deck;
        }

        private List<Card> playerCards() {
            return playerCards;
        }

        private List<Card> dealerCards() {
            return dealerCards;
        }

        private Inventory inventory() {
            return inventory;
        }

        private Phase phase() {
            return phase;
        }

        private boolean revealDealerHole() {
            return revealDealerHole;
        }
    }

    private enum Phase {
        DEALING,
        PLAYER_TURN,
        DEALER_TURN,
        SETTLED
    }

    enum Result {
        WIN,
        PUSH,
        LOSE
    }

    record HandScore(int total, boolean soft, List<Integer> countedValues) {}

    record Card(Rank rank, Suit suit) {
        private boolean red() {
            return suit == Suit.HEARTS || suit == Suit.DIAMONDS;
        }
    }

    enum Suit {
        HEARTS("Hearts"),
        DIAMONDS("Diamonds"),
        CLUBS("Clubs"),
        SPADES("Spades");

        private final String display;

        Suit(String display) {
            this.display = display;
        }

        private String display() {
            return display;
        }
    }

    enum Rank {
        ACE("A"),
        TWO("2"),
        THREE("3"),
        FOUR("4"),
        FIVE("5"),
        SIX("6"),
        SEVEN("7"),
        EIGHT("8"),
        NINE("9"),
        TEN("10"),
        JACK("J"),
        QUEEN("Q"),
        KING("K");

        private final String display;

        Rank(String display) {
            this.display = display;
        }

        private String display() {
            return display;
        }
    }
}
