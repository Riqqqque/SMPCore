package me.rique.smpcore.game;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.tavern.TavernManager.TavernGame;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RouletteManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final UUID HOUSE_ESCROW_OWNER_ID = UUID.fromString("91d20dc2-d639-42af-9d40-73565aa1ccbe");
    private static final String HOUSE_ESCROW_OWNER_NAME = "SMPCore Roulette House";
    private static final int MAX_WAGER = 64;
    private static final double ANNOUNCEMENT_RADIUS = 16.0D;
    private static final int[] CURRENCY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28};
    private static final int[] WHEEL_DISPLAY_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final List<Integer> WHEEL_ORDER = List.of(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
        5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    );
    private static final Set<Integer> RED_NUMBERS = Set.of(
        1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36
    );

    private final SMPCore plugin;
    private final ItemEscrowService escrow;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, WagerSelection> selections = new ConcurrentHashMap<>();
    private final Map<UUID, RouletteGame> games = new ConcurrentHashMap<>();
    private final Set<UUID> pendingStarts = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingMenuActions = ConcurrentHashMap.newKeySet();
    private boolean shuttingDown;

    public RouletteManager(SMPCore plugin) {
        this.plugin = plugin;
        this.escrow = new ItemEscrowService(plugin, "roulette", "roulette-escrow.yml");
    }

    public void start() {
        escrow.start(Bukkit.getOnlinePlayers());
    }

    public void shutdown() {
        shuttingDown = true;
        for (RouletteGame game : new ArrayList<>(games.values())) {
            finishGame(game, true);
        }
        games.clear();
        selections.clear();
        pendingStarts.clear();
        pendingMenuActions.clear();
        escrow.shutdown();
    }

    public void openFromNpc(Player player) {
        if (player == null || !player.isOnline()) return;
        restorePendingRecovery(player, false);
        escrow.sanitizeOrphanedEscrowMarkers(player);
        if (games.containsKey(player.getUniqueId()) || pendingStarts.contains(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Your roulette wheel is still spinning."));
            return;
        }
        openSetup(player);
    }

    public boolean claim(Player player, boolean noisy) {
        if (player == null) return false;
        boolean hadPending = escrow.hasPendingRecovery(player);
        boolean restored = restorePendingRecovery(player, noisy);
        escrow.sanitizeOrphanedEscrowMarkers(player);
        if (!restored && noisy && !hadPending) {
            player.sendMessage(MessageUtil.info("No roulette payout is waiting."));
        }
        return restored;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof RouletteMenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holderPlayerId(holder).equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        if (!MenuItemUtil.isVisibleItem(event.getCurrentItem())) return;

        UUID playerId = player.getUniqueId();
        if (!canQueueMenuAction(pendingMenuActions.contains(playerId))) return;
        pendingMenuActions.add(playerId);
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                handleClick(player, holder, slot);
            } finally {
                pendingMenuActions.remove(playerId);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof RouletteMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            restorePendingRecovery(player, false);
            escrow.sanitizeOrphanedEscrowMarkers(player);
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
        pendingStarts.remove(event.getPlayer().getUniqueId());
        pendingMenuActions.remove(event.getPlayer().getUniqueId());
    }

    static boolean canQueueMenuAction(boolean actionPending) {
        return !actionPending;
    }

    private void handleClick(Player player, InventoryHolder holder, int slot) {
        if (!player.isOnline()) return;
        if (holder instanceof SetupHolder) {
            handleSetupClick(player, slot);
        } else if (holder instanceof NumberHolder) {
            handleNumberClick(player, slot);
        } else if (holder instanceof OutsideHolder) {
            handleOutsideClick(player, slot);
        } else if (holder instanceof ConfirmHolder confirmHolder) {
            handleConfirmClick(player, confirmHolder.bet(), slot);
        } else if (holder instanceof SpinHolder spinHolder && slot == 49) {
            RouletteGame game = games.get(player.getUniqueId());
            if (game == null || !game.gameId.equals(spinHolder.gameId())) openSetup(player);
        }
    }

    private void handleSetupClick(Player player, int slot) {
        WagerCurrency currency = currencyAt(slot);
        if (currency != null) {
            WagerSelection current = selection(player);
            selections.put(player.getUniqueId(), new WagerSelection(currency, current.amount()));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, 1.25f);
            openSetup(player);
            return;
        }
        int delta = switch (slot) {
            case 36 -> -8;
            case 37 -> -1;
            case 43 -> 1;
            case 44 -> 8;
            default -> 0;
        };
        if (delta != 0) {
            WagerSelection current = selection(player);
            int amount = Math.max(1, Math.min(MAX_WAGER, current.amount() + delta));
            selections.put(player.getUniqueId(), new WagerSelection(current.currency(), amount));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, delta > 0 ? 1.35f : 0.85f);
            openSetup(player);
        } else if (slot == 49) {
            openNumberBets(player);
        } else if (slot == 53) {
            claim(player, true);
            openSetup(player);
        }
    }

    private void handleNumberClick(Player player, int slot) {
        if (slot == 45) {
            openSetup(player);
            return;
        }
        if (slot == 49) {
            openOutsideBets(player);
            return;
        }
        int number = slot == 4 ? 0 : slot >= 9 && slot <= 44 ? slot - 8 : -1;
        if (number >= 0 && number <= 36) openConfirmation(player, RouletteBet.straight(number));
    }

    private void handleOutsideClick(Player player, int slot) {
        if (slot == 45) {
            openNumberBets(player);
            return;
        }
        if (slot == 49) {
            openSetup(player);
            return;
        }
        BetKind kind = switch (slot) {
            case 10 -> BetKind.RED;
            case 12 -> BetKind.BLACK;
            case 14 -> BetKind.ODD;
            case 16 -> BetKind.EVEN;
            case 19 -> BetKind.LOW;
            case 21 -> BetKind.HIGH;
            case 28 -> BetKind.FIRST_DOZEN;
            case 30 -> BetKind.SECOND_DOZEN;
            case 32 -> BetKind.THIRD_DOZEN;
            case 37 -> BetKind.FIRST_COLUMN;
            case 39 -> BetKind.SECOND_COLUMN;
            case 41 -> BetKind.THIRD_COLUMN;
            default -> null;
        };
        if (kind != null) openConfirmation(player, RouletteBet.outside(kind));
    }

    private void handleConfirmClick(Player player, RouletteBet bet, int slot) {
        if (slot == 11) {
            startGame(player, bet);
        } else if (slot == 15) {
            if (bet.kind() == BetKind.STRAIGHT) openNumberBets(player);
            else openOutsideBets(player);
        }
    }

    private void openSetup(Player player) {
        WagerSelection selected = selection(player);
        Inventory inventory = Bukkit.createInventory(
            new SetupHolder(player.getUniqueId()),
            54,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#dc2626:#facc15><bold>Renn's Roulette</bold></gradient>"), "Roulette")
        );
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(4, menuItem(Material.RECOVERY_COMPASS, "<gold><bold>European Roulette</bold></gold>", List.of(
            "<gray>One green zero and 36 numbered pockets.</gray>",
            "<gray>Every pocket has the same <white>1 in 37</white> chance.</gray>",
            "<gray>Choose currency, amount, then your bet.</gray>",
            "<dark_gray>House edge: 2.70% on every offered bet.</dark_gray>"
        )));
        WagerCurrency[] currencies = WagerCurrency.values();
        for (int index = 0; index < currencies.length; index++) {
            WagerCurrency currency = currencies[index];
            boolean active = selected.currency() == currency;
            inventory.setItem(CURRENCY_SLOTS[index], menuItem(currency.icon(),
                active ? "<green><bold>" + currency.display() + "</bold></green>" : "<yellow>" + currency.display() + "</yellow>",
                List.of(active ? "<green>Selected</green>" : "<gray>Click to use this currency.</gray>")));
        }
        inventory.setItem(36, menuItem(Material.RED_CONCRETE, "<red><bold>-8</bold></red>", List.of("<gray>Lower the wager by 8.</gray>")));
        inventory.setItem(37, menuItem(Material.RED_STAINED_GLASS_PANE, "<red><bold>-1</bold></red>", List.of("<gray>Lower the wager by 1.</gray>")));
        inventory.setItem(40, amountItem(selected));
        inventory.setItem(43, menuItem(Material.LIME_STAINED_GLASS_PANE, "<green><bold>+1</bold></green>", List.of("<gray>Raise the wager by 1.</gray>")));
        inventory.setItem(44, menuItem(Material.LIME_CONCRETE, "<green><bold>+8</bold></green>", List.of("<gray>Raise the wager by 8.</gray>")));
        inventory.setItem(49, menuItem(Material.SUNFLOWER, "<gold><bold>Choose Your Bet</bold></gold>", List.of(
            "<gray>Selected: <white>" + selected.amount() + " " + currencyName(selected.currency(), selected.amount()) + "</white></gray>",
            "<yellow>Click to view the roulette table.</yellow>"
        )));
        inventory.setItem(53, menuItem(Material.CHEST, "<aqua><bold>Recover Payout</bold></aqua>", List.of(
            "<gray>Claims material winnings from an interrupted spin.</gray>"
        )));
        player.openInventory(inventory);
    }

    private void openNumberBets(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new NumberHolder(player.getUniqueId()), 54,
            BedrockCompat.menuTitle(player, MM.deserialize("<red><bold>Roulette Numbers</bold></red>"), "Roulette Numbers")
        );
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(0, menuItem(Material.BOOK, "<gold><bold>Straight Bets</bold></gold>", List.of(
            "<gray>Pick one exact number.</gray>",
            "<gray>Chance: <white>1 in 37 (2.70%)</white>.</gray>",
            "<gray>Payout: <green>35:1 profit</green> / <white>36x returned</white>.</gray>"
        )));
        inventory.setItem(4, numberItem(0));
        for (int number = 1; number <= 36; number++) inventory.setItem(number + 8, numberItem(number));
        inventory.setItem(45, menuItem(Material.ARROW, "<yellow><bold>Change Wager</bold></yellow>", List.of("<gray>Return to currency and amount.</gray>")));
        inventory.setItem(49, menuItem(Material.OAK_SIGN, "<aqua><bold>Outside Bets</bold></aqua>", List.of("<gray>Colors, ranges, dozens, and columns.</gray>")));
        inventory.setItem(53, wagerSummary(player));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.55f, 1.2f);
    }

    private void openOutsideBets(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new OutsideHolder(player.getUniqueId()), 54,
            BedrockCompat.menuTitle(player, MM.deserialize("<gold><bold>Roulette Outside Bets</bold></gold>"), "Roulette Bets")
        );
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(4, menuItem(Material.BOOK, "<gold><bold>Outside Bets</bold></gold>", List.of(
            "<gray>Green zero loses every outside bet.</gray>",
            "<gray>All listed payouts include your original wager.</gray>",
            "<dark_gray>Every offered bet has the same 2.70% edge.</dark_gray>"
        )));
        putOutside(inventory, 10, BetKind.RED, Material.RED_CONCRETE);
        putOutside(inventory, 12, BetKind.BLACK, Material.BLACK_CONCRETE);
        putOutside(inventory, 14, BetKind.ODD, Material.PURPLE_CONCRETE);
        putOutside(inventory, 16, BetKind.EVEN, Material.LIGHT_BLUE_CONCRETE);
        putOutside(inventory, 19, BetKind.LOW, Material.IRON_NUGGET);
        putOutside(inventory, 21, BetKind.HIGH, Material.GOLD_NUGGET);
        putOutside(inventory, 28, BetKind.FIRST_DOZEN, Material.WHITE_CONCRETE);
        putOutside(inventory, 30, BetKind.SECOND_DOZEN, Material.LIGHT_GRAY_CONCRETE);
        putOutside(inventory, 32, BetKind.THIRD_DOZEN, Material.GRAY_CONCRETE);
        putOutside(inventory, 37, BetKind.FIRST_COLUMN, Material.RED_BANNER);
        putOutside(inventory, 39, BetKind.SECOND_COLUMN, Material.WHITE_BANNER);
        putOutside(inventory, 41, BetKind.THIRD_COLUMN, Material.BLACK_BANNER);
        inventory.setItem(45, menuItem(Material.TARGET, "<yellow><bold>Straight Numbers</bold></yellow>", List.of("<gray>Return to exact-number bets.</gray>")));
        inventory.setItem(49, menuItem(Material.ARROW, "<yellow><bold>Change Wager</bold></yellow>", List.of("<gray>Return to currency and amount.</gray>")));
        inventory.setItem(53, wagerSummary(player));
        player.openInventory(inventory);
    }

    private void openConfirmation(Player player, RouletteBet bet) {
        WagerSelection selected = selection(player);
        Inventory inventory = Bukkit.createInventory(
            new ConfirmHolder(player.getUniqueId(), bet), 27,
            BedrockCompat.menuTitle(player, MM.deserialize("<gold><bold>Confirm Roulette Bet</bold></gold>"), "Confirm Bet")
        );
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(4, menuItem(Material.RECOVERY_COMPASS, "<gold><bold>Final Check</bold></gold>", List.of(
            "<gray>The wager is taken only after confirmation.</gray>",
            "<gray>The result cannot change after the wheel starts.</gray>"
        )));
        inventory.setItem(11, menuItem(Material.LIME_CONCRETE, "<green><bold>Place Bet</bold></green>", List.of(
            "<gray>Wager: <white>" + selected.amount() + " " + currencyName(selected.currency(), selected.amount()) + "</white></gray>",
            "<green>Click once to confirm.</green>"
        )));
        inventory.setItem(13, betItem(bet, selected, 0L));
        inventory.setItem(15, menuItem(Material.RED_CONCRETE, "<red><bold>Go Back</bold></red>", List.of("<gray>Nothing has been taken.</gray>")));
        inventory.setItem(22, menuItem(Material.CLOCK, "<yellow><bold>Real European Odds</bold></yellow>", List.of(
            "<gray>Winning pockets: <white>" + bet.winningPockets() + "/37</white>.</gray>",
            "<gray>Chance: <white>" + formatChance(bet.winningPockets()) + "%</white>.</gray>",
            "<dark_gray>Green zero is a normal, equally likely result.</dark_gray>"
        )));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.55f, 1.05f);
    }

    private void startGame(Player player, RouletteBet bet) {
        UUID playerId = player.getUniqueId();
        if (!pendingStarts.add(playerId)) return;
        try {
            if (shuttingDown || !player.isOnline() || player.isDead()) {
                player.sendMessage(MessageUtil.warn("You cannot spin roulette right now."));
                return;
            }
            if (games.containsKey(playerId)) {
                player.sendMessage(MessageUtil.warn("Your roulette wheel is already spinning."));
                return;
            }
            if (isCombatTagged(player)) {
                player.sendMessage(MessageUtil.warn("You cannot gamble while in combat."));
                return;
            }
            if (!escrow.isEmpty(player.getItemOnCursor())) {
                player.sendMessage(MessageUtil.warn("Clear your cursor before placing the bet."));
                return;
            }

            WagerSelection selected = selection(player);
            int result = drawResult(random.nextInt(37));
            boolean won = bet.wins(result);
            long totalPayout = won ? Math.multiplyExact((long) selected.amount(), bet.payoutMultiplier()) : 0L;
            UUID gameId = UUID.randomUUID();
            List<EscrowedItem> payouts;

            if (selected.currency() == WagerCurrency.ESSENCE) {
                if (!prepareEssenceWager(player, selected.amount(), totalPayout)) return;
                payouts = List.of();
            } else {
                BetSlot betSlot = findBetSlot(player, selected.currency().material(), selected.amount());
                if (betSlot == null) {
                    player.sendMessage(MessageUtil.warn("Put the full wager in one plain stack first."));
                    return;
                }
                EscrowedItem wager = escrow.capturePartial(gameId, player, betSlot.slot(), betSlot.item(), selected.amount());
                if (wager == null) {
                    player.sendMessage(MessageUtil.error("The roulette wager could not be locked safely."));
                    return;
                }
                if (won) {
                    payouts = escrow.replaceWithRecoveries(
                        wager, playerId, player.getName(), splitPayoutStacks(selected.currency().material(), Math.toIntExact(totalPayout)), "PAYOUT"
                    );
                    if (payouts.isEmpty()) {
                        escrow.queueRecovery(playerId, player.getName(), wager);
                        player.sendMessage(MessageUtil.error("The payout could not be journaled. Your wager was returned to recovery."));
                        return;
                    }
                } else {
                    consumeHouseWager(wager, "settling roulette game " + gameId);
                    payouts = List.of();
                }
            }

            RouletteGame game = new RouletteGame(
                gameId, playerId, player.getName(), selected, bet, result, won, totalPayout,
                List.copyOf(payouts), player.getLocation().clone()
            );
            game.inventory = createSpinInventory(player, game);
            games.put(playerId, game);
            player.openInventory(game.inventory);
            player.sendMessage(MessageUtil.info("Bet locked. The wheel is spinning..."));
            startAnimation(game);
        } finally {
            pendingStarts.remove(playerId);
        }
    }

    private boolean prepareEssenceWager(Player player, int wager, long totalPayout) {
        if (plugin.getEssenceManager() == null || !plugin.getEssenceManager().isLoaded(player)) {
            player.sendMessage(MessageUtil.warn("Your Essence balance is still loading."));
            return false;
        }
        long profit = Math.max(0L, totalPayout - wager);
        if (profit > 0L && !plugin.getEssenceManager().canCreditFully(player, profit)) {
            player.sendMessage(MessageUtil.warn("This win could exceed the Essence balance cap. Lower the wager first."));
            return false;
        }
        if (!plugin.getEssenceManager().spend(player, wager, "roulette_wager")) {
            player.sendMessage(MessageUtil.warn("You do not have enough Essence for that wager."));
            return false;
        }
        if (totalPayout > 0L && !plugin.getEssenceManager().credit(player, totalPayout, "roulette_payout")) {
            long refunded = plugin.getEssenceManager().refund(player, wager, "roulette_settlement_failure");
            plugin.getLogger().severe("Roulette Essence payout failed for " + player.getName() + "; wager refund=" + refunded + ".");
            player.sendMessage(MessageUtil.error("Roulette could not settle safely. Your wager was refunded."));
            return false;
        }
        return true;
    }

    private Inventory createSpinInventory(Player player, RouletteGame game) {
        Inventory inventory = Bukkit.createInventory(
            new SpinHolder(player.getUniqueId(), game.gameId), 54,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#dc2626:#facc15><bold>Roulette Wheel</bold></gradient>"), "Roulette Wheel")
        );
        game.currentWheelIndex = random.nextInt(WHEEL_ORDER.size());
        int minimumSteps = 48 + random.nextInt(10);
        int targetIndex = WHEEL_ORDER.indexOf(game.result);
        game.totalSteps = minimumSteps + Math.floorMod(targetIndex - game.currentWheelIndex - minimumSteps, WHEEL_ORDER.size());
        renderSpin(game, false);
        return inventory;
    }

    private void startAnimation(RouletteGame game) {
        scheduleNextStep(game, 1L);
    }

    private void scheduleNextStep(RouletteGame game, long delay) {
        if (game.finished || shuttingDown) return;
        game.task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (game.finished) return;
            game.step++;
            game.currentWheelIndex = (game.currentWheelIndex + 1) % WHEEL_ORDER.size();
            Player player = Bukkit.getPlayer(game.playerId);
            if (player != null && player.isOnline()) {
                float pitch = 0.65f + (float) game.step / Math.max(1, game.totalSteps) * 0.9f;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.42f, pitch);
            }
            renderSpin(game, false);
            if (game.step >= game.totalSteps) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> finishGame(game, false), 8L);
                return;
            }
            scheduleNextStep(game, animationDelay(game.step, game.totalSteps));
        }, delay);
    }

    private void renderSpin(RouletteGame game, boolean revealed) {
        Inventory inventory = game.inventory;
        if (inventory == null) return;
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(4, menuItem(revealed ? numberMaterial(game.result) : Material.RECOVERY_COMPASS,
            revealed ? numberName(game.result) : "<gold><bold>Wheel Spinning...</bold></gold>",
            revealed ? List.of(
                game.won ? "<green>Your bet won.</green>" : "<red>Your bet lost.</red>",
                "<gray>Result:</gray> " + numberName(game.result)
            ) : List.of("<gray>The result was locked before this animation began.</gray>")));
        for (int offset = -3; offset <= 3; offset++) {
            int wheelIndex = Math.floorMod(game.currentWheelIndex + offset, WHEEL_ORDER.size());
            int number = WHEEL_ORDER.get(wheelIndex);
            int slot = WHEEL_DISPLAY_SLOTS[offset + 3];
            ItemStack item = numberItem(number);
            if (offset == 0) {
                ItemMeta meta = item.getItemMeta();
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(MM.deserialize("<gold><bold>▲ BALL ▲</bold></gold>"));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
        }
        inventory.setItem(40, betItem(game.bet, game.selection, game.won ? game.totalPayout : 0L));
        inventory.setItem(49, revealed
            ? menuItem(Material.SUNFLOWER, "<gold><bold>Spin Again</bold></gold>", List.of("<gray>Return to wager setup.</gray>"))
            : menuItem(Material.CLOCK, "<yellow><bold>Do Not Close?</bold></yellow>", List.of(
                "<gray>You may close this safely.</gray>",
                "<gray>The result and payout will still finish.</gray>"
            )));
    }

    private void finishGame(RouletteGame game, boolean shutdown) {
        if (game == null || game.finished) return;
        game.finished = true;
        if (game.task != null) {
            game.task.cancel();
            game.task = null;
        }
        games.remove(game.playerId, game);
        if (plugin.getTavernManager() != null) {
            long seconds = Math.max(1L, (System.currentTimeMillis() - game.startedAt) / 1000L);
            plugin.getTavernManager().recordExternalGameStat(
                game.playerId, game.playerName, TavernGame.ROULETTE, game.won ? 1 : 0, seconds
            );
        }

        Player player = Bukkit.getPlayer(game.playerId);
        boolean online = player != null && player.isOnline() && !player.isDead();
        boolean claimNeeded = false;
        if (!game.payouts.isEmpty()) {
            if (online) {
                for (EscrowedItem payout : game.payouts) {
                    if (!escrow.give(player, payout)) {
                        escrow.queueRecovery(game.playerId, game.playerName, payout);
                        claimNeeded = true;
                    }
                }
                player.updateInventory();
            } else {
                for (EscrowedItem payout : game.payouts) escrow.queueRecovery(game.playerId, game.playerName, payout);
                claimNeeded = true;
            }
        }

        if (shutdown) return;
        renderSpin(game, true);
        if (online) {
            if (game.won) {
                String payout = game.totalPayout + " " + currencyName(game.selection.currency(), Math.toIntExact(game.totalPayout));
                BedrockCompat.sendGameMessage(player, claimNeeded
                    ? MessageUtil.success("Roulette hit " + game.result + ". Use <white>/roulette claim</white> for the payout.")
                    : MessageUtil.success("Roulette hit " + game.result + ". You received <white>" + payout + "</white>."));
                playWinEffects(player, game.bet.payoutMultiplier());
                announceWin(game);
            } else {
                BedrockCompat.sendGameMessage(player, MessageUtil.warn("Roulette hit " + game.result + ". Your " + game.bet.display() + " bet lost."));
                playLoseEffects(player);
            }
        } else if (game.won) {
            announceWin(game);
        }
        if (online) BedrockCompat.syncGameInventory(player);
    }

    private void announceWin(RouletteGame game) {
        Location location = game.tableLocation;
        World world = location.getWorld();
        if (world == null) return;
        String payout = game.totalPayout + " " + currencyName(game.selection.currency(), Math.toIntExact(game.totalPayout));
        Component message = MessageUtil.success(
            "<white>" + game.playerName + "</white> won <white>" + payout + "</white> on " + game.bet.display() + " in roulette."
        );
        for (Player nearby : world.getNearbyPlayers(location, ANNOUNCEMENT_RADIUS)) {
            if (!nearby.getUniqueId().equals(game.playerId)) {
                BedrockCompat.sendGameMessage(nearby, message);
                nearby.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.55F, 1.25F);
            }
        }
    }

    private void playWinEffects(Player player, int multiplier) {
        Location location = player.getLocation();
        World world = player.getWorld();
        world.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, multiplier >= 36 ? 1.35f : 1.1f);
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.65f, 1.3f);
        Location effect = location.clone().add(0.0D, 1.2D, 0.0D);
        world.spawnParticle(Particle.FIREWORK, effect, multiplier >= 36 ? 70 : 38, 0.6D, 0.55D, 0.6D, 0.06D);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, effect, multiplier >= 36 ? 32 : 14, 0.4D, 0.45D, 0.4D, 0.03D);
        if (multiplier >= 36) world.spawnParticle(Particle.FLASH, effect, 1, 0.0D, 0.0D, 0.0D, 0.0D, Color.WHITE);
    }

    private void playLoseEffects(Player player) {
        Location location = player.getLocation();
        player.playSound(location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.65f, 0.55f);
        player.getWorld().spawnParticle(Particle.SMOKE, location.clone().add(0.0D, 1.0D, 0.0D), 18, 0.35D, 0.3D, 0.35D, 0.025D);
    }

    private void putOutside(Inventory inventory, int slot, BetKind kind, Material material) {
        RouletteBet bet = RouletteBet.outside(kind);
        inventory.setItem(slot, menuItem(material, "<yellow><bold>" + kind.display + "</bold></yellow>", List.of(
            "<gray>Chance: <white>" + bet.winningPockets() + "/37 (" + formatChance(bet.winningPockets()) + "%)</white>.</gray>",
            "<gray>Payout: <green>" + (bet.payoutMultiplier() - 1) + ":1 profit</green> / <white>" + bet.payoutMultiplier() + "x returned</white>.</gray>",
            "<yellow>Click to select.</yellow>"
        )));
    }

    private ItemStack amountItem(WagerSelection selected) {
        ItemStack item = menuItem(selected.currency().icon(), "<gold><bold>Wager: " + selected.amount() + "</bold></gold>", List.of(
            "<gray>Currency: <white>" + selected.currency().display() + "</white></gray>",
            "<dark_gray>Minimum 1. Maximum 64.</dark_gray>"
        ));
        item.setAmount(Math.min(selected.amount(), item.getMaxStackSize()));
        return item;
    }

    private ItemStack wagerSummary(Player player) {
        WagerSelection selected = selection(player);
        return menuItem(selected.currency().icon(), "<gold><bold>Current Wager</bold></gold>", List.of(
            "<white>" + selected.amount() + " " + currencyName(selected.currency(), selected.amount()) + "</white>"
        ));
    }

    private ItemStack betItem(RouletteBet bet, WagerSelection selected, long payout) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Bet: <white>" + bet.display() + "</white></gray>");
        lore.add("<gray>Wager: <white>" + selected.amount() + " " + currencyName(selected.currency(), selected.amount()) + "</white></gray>");
        lore.add("<gray>Chance: <white>" + bet.winningPockets() + "/37 (" + formatChance(bet.winningPockets()) + "%)</white></gray>");
        lore.add("<gray>Winning return: <green>" + ((long) selected.amount() * bet.payoutMultiplier()) + "</green></gray>");
        if (payout > 0L) lore.add("<green>Settled payout: " + payout + "</green>");
        return menuItem(bet.kind() == BetKind.STRAIGHT ? Material.TARGET : Material.OAK_SIGN, "<gold><bold>" + bet.display() + "</bold></gold>", lore);
    }

    private ItemStack numberItem(int number) {
        ItemStack item = menuItem(numberMaterial(number), numberName(number), List.of(
            number == 0 ? "<green>Green zero</green>" : isRed(number) ? "<red>Red</red>" : "<gray>Black</gray>",
            "<gray>Straight payout: <white>36x returned</white>.</gray>"
        ));
        if (number > 0) item.setAmount(number);
        return item;
    }

    private static Material numberMaterial(int number) {
        if (number == 0) return Material.LIME_CONCRETE;
        return isRed(number) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
    }

    private static String numberName(int number) {
        return numberColor(number) + "<bold>" + number + "</bold>" + (number == 0 ? "</green>" : isRed(number) ? "</red>" : "</gray>");
    }

    private static String numberColor(int number) {
        if (number == 0) return "<green>";
        return isRed(number) ? "<red>" : "<gray>";
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

    private void fill(Inventory inventory, Material material) {
        ItemStack filler = menuItem(material, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private WagerSelection selection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), ignored -> new WagerSelection(WagerCurrency.ESSENCE, 1));
    }

    private WagerCurrency currencyAt(int slot) {
        for (int index = 0; index < CURRENCY_SLOTS.length; index++) {
            if (CURRENCY_SLOTS[index] == slot) return WagerCurrency.values()[index];
        }
        return null;
    }

    private BetSlot findBetSlot(Player player, Material material, int amount) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (escrow.isEmpty(item) || item.getType() != material || item.getAmount() < amount) continue;
            if (!isPlainCurrencyStack(item)) continue;
            return new BetSlot(slot, item.clone());
        }
        return null;
    }

    private boolean isPlainCurrencyStack(ItemStack item) {
        if (escrow.isEmpty(item) || WagerCurrency.fromMaterial(item.getType()) == null) return false;
        if (escrow.hasAnyEscrowMarker(item) || escrow.hasMenuPreviewMarker(item)) return false;
        return item.isSimilar(new ItemStack(item.getType()));
    }

    private boolean restorePendingRecovery(Player player, boolean noisy) {
        boolean hadPending = escrow.hasPendingRecovery(player);
        boolean restored = escrow.restorePendingRecovery(player);
        if (restored && noisy) {
            player.sendMessage(MessageUtil.success("Recovered your roulette payout."));
        } else if (hadPending && noisy) {
            player.sendMessage(MessageUtil.warn("Clear inventory space, then use <white>/roulette claim</white>."));
        }
        return restored;
    }

    private boolean consumeHouseWager(EscrowedItem wager, String reason) {
        if (escrow.consume(wager)) return true;
        boolean quarantined = escrow.retarget(wager, HOUSE_ESCROW_OWNER_ID, HOUSE_ESCROW_OWNER_NAME, "FORFEITED");
        if (!quarantined) {
            escrow.queueRecovery(HOUSE_ESCROW_OWNER_ID, HOUSE_ESCROW_OWNER_NAME, wager);
            plugin.getLogger().severe("Could not quarantine roulette wager " + wager.escrowId() + " while " + reason + ".");
        }
        return quarantined;
    }

    private boolean isCombatTagged(Player player) {
        return plugin.getCombatLogListener() != null && plugin.getCombatLogListener().isInPlayerCombat(player);
    }

    private UUID holderPlayerId(InventoryHolder holder) {
        if (holder instanceof SetupHolder value) return value.playerId();
        if (holder instanceof NumberHolder value) return value.playerId();
        if (holder instanceof OutsideHolder value) return value.playerId();
        if (holder instanceof ConfirmHolder value) return value.playerId();
        if (holder instanceof SpinHolder value) return value.playerId();
        return new UUID(0L, 0L);
    }

    static int drawResult(int uniformIndex) {
        if (uniformIndex < 0 || uniformIndex >= 37) throw new IllegalArgumentException("Roulette index must be 0-36.");
        return uniformIndex;
    }

    static long animationDelay(int step, int totalSteps) {
        if (totalSteps <= 0) return 1L;
        double progress = (double) step / totalSteps;
        if (progress < 0.56D) return 1L;
        if (progress < 0.80D) return 2L;
        if (progress < 0.92D) return 3L;
        return 5L;
    }

    static boolean isRed(int number) {
        return RED_NUMBERS.contains(number);
    }

    static boolean isBlack(int number) {
        return number >= 1 && number <= 36 && !isRed(number);
    }

    static List<Integer> wheelOrder() {
        return WHEEL_ORDER;
    }

    static List<Integer> splitPayoutStackAmounts(int amount, int maxStackSize) {
        List<Integer> amounts = new ArrayList<>();
        int remaining = Math.max(0, amount);
        int maximum = Math.max(1, maxStackSize);
        while (remaining > 0) {
            int moved = Math.min(maximum, remaining);
            amounts.add(moved);
            remaining -= moved;
        }
        return amounts;
    }

    static List<ItemStack> splitPayoutStacks(Material material, int amount) {
        return splitPayoutStackAmounts(amount, material.getMaxStackSize()).stream()
            .map(stackAmount -> new ItemStack(material, stackAmount))
            .toList();
    }

    private static String formatChance(int winningPockets) {
        return String.format(Locale.US, "%.2f", winningPockets * 100.0D / 37.0D);
    }

    private static String currencyName(WagerCurrency currency, int amount) {
        if (currency == WagerCurrency.ESSENCE) return "Essence";
        String name = currency.display().toLowerCase(Locale.ROOT);
        return amount == 1 ? name : name + (name.endsWith("s") ? "" : "s");
    }

    public enum BetKind {
        STRAIGHT("Straight Number", 36, 1),
        RED("Red", 2, 18),
        BLACK("Black", 2, 18),
        ODD("Odd", 2, 18),
        EVEN("Even", 2, 18),
        LOW("1-18", 2, 18),
        HIGH("19-36", 2, 18),
        FIRST_DOZEN("1st Dozen (1-12)", 3, 12),
        SECOND_DOZEN("2nd Dozen (13-24)", 3, 12),
        THIRD_DOZEN("3rd Dozen (25-36)", 3, 12),
        FIRST_COLUMN("1st Column", 3, 12),
        SECOND_COLUMN("2nd Column", 3, 12),
        THIRD_COLUMN("3rd Column", 3, 12);

        private final String display;
        private final int payoutMultiplier;
        private final int winningPockets;

        BetKind(String display, int payoutMultiplier, int winningPockets) {
            this.display = display;
            this.payoutMultiplier = payoutMultiplier;
            this.winningPockets = winningPockets;
        }
    }

    public record RouletteBet(BetKind kind, int number) {
        public RouletteBet {
            if (kind == null) throw new IllegalArgumentException("Bet kind is required.");
            if (kind == BetKind.STRAIGHT && (number < 0 || number > 36)) {
                throw new IllegalArgumentException("Straight roulette number must be 0-36.");
            }
            if (kind != BetKind.STRAIGHT && number != -1) {
                throw new IllegalArgumentException("Outside roulette bets cannot include a number.");
            }
        }

        public static RouletteBet straight(int number) {
            return new RouletteBet(BetKind.STRAIGHT, number);
        }

        public static RouletteBet outside(BetKind kind) {
            if (kind == BetKind.STRAIGHT) throw new IllegalArgumentException("Use straight(number) for a number bet.");
            return new RouletteBet(kind, -1);
        }

        public String display() {
            return kind == BetKind.STRAIGHT ? "Straight " + number : kind.display;
        }

        public int payoutMultiplier() {
            return kind.payoutMultiplier;
        }

        public int winningPockets() {
            return kind.winningPockets;
        }

        public boolean wins(int result) {
            if (result < 0 || result > 36) return false;
            return switch (kind) {
                case STRAIGHT -> result == number;
                case RED -> isRed(result);
                case BLACK -> isBlack(result);
                case ODD -> result != 0 && result % 2 == 1;
                case EVEN -> result != 0 && result % 2 == 0;
                case LOW -> result >= 1 && result <= 18;
                case HIGH -> result >= 19 && result <= 36;
                case FIRST_DOZEN -> result >= 1 && result <= 12;
                case SECOND_DOZEN -> result >= 13 && result <= 24;
                case THIRD_DOZEN -> result >= 25 && result <= 36;
                case FIRST_COLUMN -> result != 0 && result % 3 == 1;
                case SECOND_COLUMN -> result != 0 && result % 3 == 2;
                case THIRD_COLUMN -> result != 0 && result % 3 == 0;
            };
        }
    }

    private enum WagerCurrency {
        COAL(Material.COAL, "Coal"),
        RAW_COPPER(Material.RAW_COPPER, "Raw Copper"),
        COPPER_INGOT(Material.COPPER_INGOT, "Copper Ingot"),
        RAW_IRON(Material.RAW_IRON, "Raw Iron"),
        IRON_INGOT(Material.IRON_INGOT, "Iron Ingot"),
        RAW_GOLD(Material.RAW_GOLD, "Raw Gold"),
        GOLD_INGOT(Material.GOLD_INGOT, "Gold Ingot"),
        REDSTONE(Material.REDSTONE, "Redstone"),
        LAPIS_LAZULI(Material.LAPIS_LAZULI, "Lapis Lazuli"),
        EMERALD(Material.EMERALD, "Emerald"),
        DIAMOND(Material.DIAMOND, "Diamond"),
        NETHER_QUARTZ(Material.QUARTZ, "Nether Quartz"),
        NETHERITE_SCRAP(Material.NETHERITE_SCRAP, "Netherite Scrap"),
        NETHERITE_INGOT(Material.NETHERITE_INGOT, "Netherite Ingot"),
        ESSENCE(null, "Essence");

        private final Material material;
        private final String display;

        WagerCurrency(Material material, String display) {
            this.material = material;
            this.display = display;
        }

        public Material material() {
            return material;
        }

        public Material icon() {
            return material == null ? Material.ECHO_SHARD : material;
        }

        public String display() {
            return display;
        }

        static WagerCurrency fromMaterial(Material material) {
            if (material == null) return null;
            for (WagerCurrency currency : values()) {
                if (currency.material == material) return currency;
            }
            return null;
        }
    }

    private record WagerSelection(WagerCurrency currency, int amount) {
    }

    private record BetSlot(int slot, ItemStack item) {
    }

    private static final class RouletteGame {
        private final UUID gameId;
        private final UUID playerId;
        private final String playerName;
        private final WagerSelection selection;
        private final RouletteBet bet;
        private final int result;
        private final boolean won;
        private final long totalPayout;
        private final List<EscrowedItem> payouts;
        private final Location tableLocation;
        private final long startedAt = System.currentTimeMillis();
        private Inventory inventory;
        private BukkitTask task;
        private int currentWheelIndex;
        private int totalSteps;
        private int step;
        private boolean finished;

        private RouletteGame(
            UUID gameId,
            UUID playerId,
            String playerName,
            WagerSelection selection,
            RouletteBet bet,
            int result,
            boolean won,
            long totalPayout,
            List<EscrowedItem> payouts,
            Location tableLocation
        ) {
            this.gameId = gameId;
            this.playerId = playerId;
            this.playerName = playerName;
            this.selection = selection;
            this.bet = bet;
            this.result = result;
            this.won = won;
            this.totalPayout = totalPayout;
            this.payouts = payouts;
            this.tableLocation = tableLocation;
        }
    }

    private interface RouletteMenuHolder extends InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        default Inventory getInventory() {
            return null;
        }
    }

    private record SetupHolder(UUID playerId) implements RouletteMenuHolder {
    }

    private record NumberHolder(UUID playerId) implements RouletteMenuHolder {
    }

    private record OutsideHolder(UUID playerId) implements RouletteMenuHolder {
    }

    private record ConfirmHolder(UUID playerId, RouletteBet bet) implements RouletteMenuHolder {
    }

    private record SpinHolder(UUID playerId, UUID gameId) implements RouletteMenuHolder {
    }
}
