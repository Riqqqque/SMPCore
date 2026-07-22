package me.rique.smpcore.tavern;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.ItemEscrowService;
import me.rique.smpcore.util.ItemEscrowService.EscrowedItem;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class TavernBountyManager implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int MENU_SIZE = 54;
    private static final int BOUNTIES_PER_PAGE = 45;
    private static final int DAILY_KILLS = 12, DAILY_ORES = 24, DAILY_FISH = 6;
    private final SMPCore plugin;
    private final ItemEscrowService escrow;
    private final File file;
    private final NamespacedKey actionKey;
    private final NamespacedKey dailyDateKey;
    private final NamespacedKey dailyKillsKey;
    private final NamespacedKey dailyOresKey;
    private final NamespacedKey dailyFishKey;
    private final NamespacedKey dailyClaimMaskKey;
    private final Map<UUID, PlayerBounty> bounties = new ConcurrentHashMap<>();
    private final Map<String, Long> claimCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> selectedTargets = new ConcurrentHashMap<>();
    private final Map<UUID, PendingOffer> pendingOffers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingCancels = new ConcurrentHashMap<>();
    private final Set<UUID> pendingActions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerProfile> playerProfiles = new ConcurrentHashMap<>();

    TavernBountyManager(SMPCore plugin) {
        this.plugin = plugin;
        this.escrow = new ItemEscrowService(plugin, "tavern_bounty", "tavern-bounty-escrow.yml");
        this.file = new File(plugin.getDataFolder(), "tavern-bounties.yml");
        this.actionKey = new NamespacedKey(plugin, "tavern_bounty_action");
        this.dailyDateKey = new NamespacedKey(plugin, "tavern_daily_date");
        this.dailyKillsKey = new NamespacedKey(plugin, "tavern_daily_kills");
        this.dailyOresKey = new NamespacedKey(plugin, "tavern_daily_ores");
        this.dailyFishKey = new NamespacedKey(plugin, "tavern_daily_fish");
        this.dailyClaimMaskKey = new NamespacedKey(plugin, "tavern_daily_claims");
    }

    void start() {
        Bukkit.getOnlinePlayers().forEach(this::cachePlayerProfile);
        Set<UUID> retained = loadBounties();
        escrow.start(Bukkit.getOnlinePlayers(), retained);
        bounties.values().removeIf(bounty -> {
            boolean missing = bounty.kind == BountyKind.ITEM && escrow.retainedEscrow(bounty.escrowId) == null;
            if (missing) {
                plugin.getLogger().severe("Removed player bounty " + bounty.id
                    + " because its item escrow is missing. Review the escrow backup before compensating the creator.");
            }
            return missing;
        });
        saveBounties();
    }

    void shutdown() {
        saveBounties();
        escrow.shutdown();
    }

    void open(Player player) {
        openMain(player);
    }

    void openActiveBounties(Player player) {
        openBounties(player, false, 0, true);
    }

    private void openMain(Player player) {
        resetDaily(player);
        Inventory inv = menu(player, "Rumor Board", "Rumor Board");
        inv.setItem(11, button(Material.WRITABLE_BOOK, "<gold><bold>DAILY WORK</bold></gold>", dailySummary(player), "daily:open"));
        inv.setItem(13, button(
            Material.TARGET,
            "<red><bold>ACTIVE BOUNTIES</bold></red>",
            List.of(
                "<gray>View every posted player bounty.</gray>",
                "<yellow>" + bounties.size() + " currently active</yellow>"
            ),
            "bounty:list"
        ));
        inv.setItem(15, button(
            Material.EMERALD,
            "<green><bold>POST A BOUNTY</bold></green>",
            List.of(
                "<gray>Choose an online target.</gray>",
                "<gray>Offer Essence, ingots, or a held item.</gray>",
                "<gray>Orbs and Soul Imprints are supported.</gray>"
            ),
            "bounty:targets"
        ));
        inv.setItem(31, button(
            Material.CHEST,
            "<aqua><bold>MY POSTED BOUNTIES</bold></aqua>",
            List.of(
                "<gray>Review or cancel your own postings.</gray>",
                "<gray>Cancellation returns the full escrow.</gray>"
            ),
            "bounty:mine"
        ));
        player.openInventory(inv);
    }

    private void openDailies(Player player) {
        resetDaily(player);
        Inventory inv = menu(player, "Daily Rumors", "Daily Rumors");
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int mask = pdc.getOrDefault(dailyClaimMaskKey, PersistentDataType.INTEGER, 0);
        inv.setItem(11, dailyItem(Material.IRON_SWORD, "Cull the Restless", pdc.getOrDefault(dailyKillsKey, PersistentDataType.INTEGER, 0), DAILY_KILLS, 40, 0, mask));
        inv.setItem(13, dailyItem(Material.IRON_PICKAXE, "Ore Survey", pdc.getOrDefault(dailyOresKey, PersistentDataType.INTEGER, 0), DAILY_ORES, 45, 1, mask));
        inv.setItem(15, dailyItem(Material.FISHING_ROD, "Fresh Catch", pdc.getOrDefault(dailyFishKey, PersistentDataType.INTEGER, 0), DAILY_FISH, 35, 2, mask));
        inv.setItem(49, button(Material.ARROW, "<yellow>Back</yellow>", List.of(), "main"));
        player.openInventory(inv);
    }

    private ItemStack dailyItem(Material material, String name, int progress, int required, int reward, int bit, int mask) {
        boolean claimed = (mask & (1 << bit)) != 0;
        boolean ready = progress >= required;
        String status = claimed
            ? "<green>Claimed today</green>"
            : ready ? "<yellow>Click to claim</yellow>" : "<dark_gray>Come back when complete</dark_gray>";
        return button(
            material,
            "<gold>" + name + "</gold>",
            List.of(
                "<gray>Progress: <white>" + Math.min(progress, required) + "/" + required + "</white></gray>",
                "<gray>Reward: <white>" + reward + " Essence</white></gray>",
                status
            ),
            "daily:claim:" + bit
        );
    }

    private List<String> dailySummary(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return List.of("<gray>Three tasks reset every real UTC day.</gray>",
            "<gray>Hostiles: <white>" + pdc.getOrDefault(dailyKillsKey, PersistentDataType.INTEGER, 0) + "/" + DAILY_KILLS + "</white></gray>",
            "<gray>Ores: <white>" + pdc.getOrDefault(dailyOresKey, PersistentDataType.INTEGER, 0) + "/" + DAILY_ORES + "</white></gray>",
            "<gray>Fish: <white>" + pdc.getOrDefault(dailyFishKey, PersistentDataType.INTEGER, 0) + "/" + DAILY_FISH + "</white></gray>");
    }

    private void openTargets(Player player) {
        Inventory inv = menu(player, "Choose Bounty Target", "Choose Target");
        int slot = 0;
        List<? extends Player> targets = Bukkit.getOnlinePlayers().stream()
            .filter(target -> !target.getUniqueId().equals(player.getUniqueId()))
            .sorted(Comparator.comparing(Player::getName))
            .toList();
        for (Player target : targets) {
            if (slot >= 45) break;
            inv.setItem(slot++, button(
                playerHead(target.getUniqueId(), target.getName()),
                "<red>" + target.getName() + "</red>",
                List.of("<yellow>Click to choose this target.</yellow>"),
                "bounty:target:" + target.getUniqueId()
            ));
        }
        if (targets.isEmpty()) inv.setItem(22, button(Material.BARRIER, "<red>No targets online</red>", List.of("<gray>Players must be online when selected.</gray>"), "close"));
        inv.setItem(49, button(Material.ARROW, "<yellow>Back</yellow>", List.of(), "main"));
        player.openInventory(inv);
    }

    private void openOffer(Player player) {
        UUID targetId = selectedTargets.get(player.getUniqueId());
        OfflinePlayer target = targetId == null ? null : Bukkit.getOfflinePlayer(targetId);
        if (target == null || target.getName() == null) { openTargets(player); return; }
        Inventory inv = menu(player, "Bounty on " + target.getName(), "Choose Bounty");
        int[] amounts = {25, 100, 500, 1000};
        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < amounts.length; i++) {
            inv.setItem(slots[i], button(
                Material.AMETHYST_SHARD,
                "<light_purple>" + amounts[i] + " Essence</light_purple>",
                List.of("<gray>Taken after confirmation.</gray>"),
                "bounty:offer:essence:" + amounts[i]
            ));
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        setItemOffer(inv, player, held, 19, BountyItemChoice.IRON);
        setItemOffer(inv, player, held, 21, BountyItemChoice.GOLD);
        setItemOffer(inv, player, held, 23, BountyItemChoice.DIAMOND);
        setItemOffer(inv, player, held, 25, BountyItemChoice.NETHERITE);
        setItemOffer(inv, player, held, 30, BountyItemChoice.ORB);
        setItemOffer(inv, player, held, 32, BountyItemChoice.SOUL_IMPRINT);

        String heldDescription = held.getType().isAir()
            ? "<red>Hold the exact stack you want to post.</red>"
            : "<gray>Ready: <white>" + held.getAmount() + "x " + bountyItemDisplayName(held, player) + "</white></gray>";
        inv.setItem(40, button(
            held.getType().isAir() ? new ItemStack(Material.BARRIER) : held,
            "<aqua>Other Held Item</aqua>",
            List.of(heldDescription, "<gray>The complete stack is safely escrowed.</gray>"),
            "bounty:offer:item:any"
        ));
        inv.setItem(45, button(
            Material.BOOK,
            "<yellow>Bounty Payments</yellow>",
            List.of(
                "<gray>Hold the amount you want to offer.</gray>",
                "<gray>The whole held stack becomes the reward.</gray>",
                "<gray>Cancelled or unclaimed rewards stay recoverable.</gray>"
            ),
            "close"
        ));
        inv.setItem(49, button(Material.ARROW, "<yellow>Back</yellow>", List.of(), "bounty:targets"));
        player.openInventory(inv);
    }

    private void setItemOffer(Inventory inventory, Player player, ItemStack held, int slot, BountyItemChoice choice) {
        boolean ready = matchesItemChoice(choice, held);
        String status = ready
            ? "<green>Ready: <white>" + held.getAmount() + "x " + bountyItemDisplayName(held, player) + "</white></green>"
            : "<gray>Hold " + choice.heldInstruction + " in your main hand.</gray>";
        ItemStack icon = choice == BountyItemChoice.ORB
            ? relicPreview(player, "veilshift_orb", choice.icon)
            : choice == BountyItemChoice.SOUL_IMPRINT
                ? relicPreview(player, "soul_imprint", choice.icon)
                : new ItemStack(choice.icon);
        String name = choice == BountyItemChoice.SOUL_IMPRINT && plugin.getSeasonRelicManager() != null
            ? plugin.getSeasonRelicManager().soulImprintDisplayName(player)
            : choice.displayName;
        inventory.setItem(slot, button(
            icon,
            "<gold>" + name + "</gold>",
            List.of(status, "<gray>The entire held stack is used.</gray>"),
            "bounty:offer:item:" + choice.token
        ));
    }

    private ItemStack relicPreview(Player player, String relicId, Material fallback) {
        if (plugin.getSeasonRelicManager() == null) return new ItemStack(fallback);
        ItemStack preview = plugin.getSeasonRelicManager().createRelicPreview(player, relicId);
        return preview == null || preview.getType().isAir() ? new ItemStack(fallback) : preview;
    }

    private void openConfirm(Player player, PendingOffer offer) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(offer.targetId);
        Inventory inv = menu(player, "Confirm Bounty", "Confirm Bounty");
        String escrowDescription = offer.kind == BountyKind.ESSENCE
            ? "<gray>Escrow: <white>" + offer.amount + " Essence</white></gray>"
            : "<gray>Escrow: <white>" + offer.preview.getAmount() + "x "
                + bountyItemDisplayName(offer.preview, player) + "</white></gray>";
        inv.setItem(13, button(
            offer.kind == BountyKind.ESSENCE ? Material.AMETHYST_SHARD : offer.preview.getType(),
            "<gold>Bounty on " + (target.getName() == null ? "Player" : target.getName()) + "</gold>",
            List.of(escrowDescription, "<red>The killer receives the entire bounty.</red>"),
            "close"
        ));
        inv.setItem(29, button(Material.LIME_CONCRETE, "<green><bold>CONFIRM POSTING</bold></green>", List.of("<gray>The offer is taken now.</gray>"), "bounty:confirm"));
        inv.setItem(33, button(Material.RED_CONCRETE, "<red><bold>GO BACK</bold></red>", List.of("<gray>Nothing will be taken.</gray>"), "bounty:offer"));
        player.openInventory(inv);
    }

    private void openBounties(Player player, boolean mineOnly) {
        openBounties(player, mineOnly, 0, false);
    }

    private void openBounties(Player player, boolean mineOnly, int requestedPage, boolean readOnly) {
        Inventory inv = menu(
            player,
            mineOnly ? "My Posted Bounties" : "Active Bounties",
            mineOnly ? "My Bounties" : "Active Bounties"
        );
        List<PlayerBounty> visible = bounties.values().stream()
            .filter(bounty -> !mineOnly || bounty.creatorId.equals(player.getUniqueId()))
            .sorted(Comparator.comparingLong(PlayerBounty::createdAt))
            .toList();
        int pageCount = pageCount(visible.size());
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int fromIndex = page * BOUNTIES_PER_PAGE;
        int toIndex = Math.min(visible.size(), fromIndex + BOUNTIES_PER_PAGE);
        int slot = 0;
        for (PlayerBounty bounty : visible.subList(fromIndex, toIndex)) {
            String reward = rewardText(bounty, player);
            boolean creator = bounty.creatorId.equals(player.getUniqueId());
            boolean cancellable = creator && !readOnly;
            inv.setItem(slot++, button(
                playerHead(bounty.targetId, bounty.targetName),
                "<red>" + bounty.targetName + "</red>",
                List.of(
                    "<gray>Posted by: <white>" + bounty.creatorName + "</white></gray>",
                    "<gray>Reward: <white>" + reward + "</white></gray>",
                    cancellable
                        ? "<yellow>Click to cancel and refund.</yellow>"
                        : creator
                            ? "<dark_gray>Your posting. Manage it at the Rumor Board.</dark_gray>"
                        : "<dark_gray>Defeat this player to claim.</dark_gray>"
                ),
                cancellable ? "bounty:cancel:" + bounty.id : "noop"
            ));
        }
        if (visible.isEmpty()) {
            inv.setItem(22, button(Material.PAPER, "<gray>No active bounties</gray>", List.of("<dark_gray>Check again later.</dark_gray>"), "noop"));
        }
        String view = readOnly ? "view" : mineOnly ? "mine" : "list";
        if (page > 0) {
            inv.setItem(45, button(Material.ARROW, "<yellow>Previous Page</yellow>", List.of(), pageAction(view, page - 1)));
        }
        inv.setItem(49, button(
            readOnly ? Material.BARRIER : Material.ARROW,
            readOnly ? "<red>Close</red>" : "<yellow>Back</yellow>",
            List.of("<gray>Page <white>" + (page + 1) + "/" + pageCount + "</white></gray>"),
            readOnly ? "close" : "main"
        ));
        if (page + 1 < pageCount) {
            inv.setItem(53, button(Material.ARROW, "<yellow>Next Page</yellow>", List.of(), pageAction(view, page + 1)));
        }
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof BountyHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId.equals(player.getUniqueId())) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        String action = action(event.getCurrentItem());
        if (action == null || !pendingActions.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                handle(player, action);
            } finally {
                pendingActions.remove(player.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof BountyHolder) {
            event.setCancelled(true);
        }
    }

    private void handle(Player player, String action) {
        String[] p = action.split(":");
        if (action.equals("close")) player.closeInventory();
        else if (action.equals("noop")) return;
        else if (action.equals("main")) openMain(player);
        else if (action.equals("daily:open")) openDailies(player);
        else if (action.equals("bounty:list")) openBounties(player, false);
        else if (action.equals("bounty:mine")) openBounties(player, true);
        else if (action.equals("bounty:targets")) openTargets(player);
        else if (action.equals("bounty:offer")) openOffer(player);
        else if (action.equals("bounty:confirm")) confirm(player);
        else if (p.length == 4 && p[0].equals("bounty") && p[2].equals("page")) {
            Integer page = parseInteger(p[3]);
            if (page == null || page < 0) return;
            switch (p[1]) {
                case "list" -> openBounties(player, false, page, false);
                case "mine" -> openBounties(player, true, page, false);
                case "view" -> openBounties(player, false, page, true);
                default -> { }
            }
        }
        else if (p.length == 3 && p[0].equals("daily") && p[1].equals("claim")) {
            Integer dailyIndex = parseInteger(p[2]);
            if (dailyIndex != null) claimDaily(player, dailyIndex);
        } else if (p.length == 3 && p[0].equals("bounty") && p[1].equals("target")) {
            UUID targetId = parseUuid(p[2]);
            if (targetId != null) {
                selectedTargets.put(player.getUniqueId(), targetId);
                openOffer(player);
            }
        } else if (p.length == 4 && p[0].equals("bounty") && p[1].equals("offer") && p[2].equals("essence")) {
            Long amount = parseLong(p[3]);
            if (amount != null) {
                PendingOffer offer = new PendingOffer(
                    selectedTargets.get(player.getUniqueId()),
                    BountyKind.ESSENCE,
                    amount,
                    null
                );
                pendingOffers.put(player.getUniqueId(), offer);
                openConfirm(player, offer);
            }
        }
        else if (action.equals("bounty:offer:item")) prepareItemOffer(player, BountyItemChoice.ANY);
        else if (p.length == 4 && p[0].equals("bounty") && p[1].equals("offer") && p[2].equals("item")) {
            BountyItemChoice choice = BountyItemChoice.fromToken(p[3]);
            if (choice != null) prepareItemOffer(player, choice);
        }
        else if (action.equals("bounty:cancel:confirm")) confirmCancel(player);
        else {
            UUID bountyId = cancelTargetId(action);
            if (bountyId != null) openCancelConfirm(player, bountyId);
        }
    }

    static UUID cancelTargetId(String action) {
        if (action == null || !action.startsWith("bounty:cancel:") || action.equals("bounty:cancel:confirm")) return null;
        return parseUuid(action.substring("bounty:cancel:".length()));
    }

    static int pageCount(int entryCount) {
        return Math.max(1, (Math.max(0, entryCount) + BOUNTIES_PER_PAGE - 1) / BOUNTIES_PER_PAGE);
    }

    static String pageAction(String view, int page) {
        String safeView = switch (view) {
            case "mine", "view" -> view;
            default -> "list";
        };
        return "bounty:" + safeView + ":page:" + Math.max(0, page);
    }

    private static Integer parseInteger(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long parseLong(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void prepareItemOffer(Player player, BountyItemChoice choice) {
        UUID target = selectedTargets.get(player.getUniqueId());
        ItemStack held = player.getInventory().getItemInMainHand();
        if (target == null || !matchesItemChoice(choice, held)
            || escrow.hasAnyEscrowMarker(held) || escrow.hasMenuPreviewMarker(held)) {
            player.sendMessage(MessageUtil.warn("Hold " + choice.heldInstruction + " in your main hand first."));
            openOffer(player);
            return;
        }
        PendingOffer offer = new PendingOffer(target, BountyKind.ITEM, 0L, held.clone());
        pendingOffers.put(player.getUniqueId(), offer);
        openConfirm(player, offer);
    }

    private void confirm(Player player) {
        PendingOffer offer = pendingOffers.remove(player.getUniqueId());
        if (offer == null || offer.targetId == null || offer.targetId.equals(player.getUniqueId())) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(offer.targetId);
        if (target.getName() == null) return;
        if (offer.kind == BountyKind.ESSENCE && offer.amount <= 0L) {
            player.sendMessage(MessageUtil.error("That Essence bounty amount is invalid."));
            return;
        }
        UUID id = UUID.randomUUID();
        PlayerBounty bounty;
        if (offer.kind == BountyKind.ESSENCE) {
            if (plugin.getEssenceManager() == null || !plugin.getEssenceManager().isLoaded(player)) {
                player.sendMessage(MessageUtil.warn("Your Essence is still loading. Try again in a moment."));
                return;
            }
            if (!plugin.getEssenceManager().spend(player, offer.amount, "player_bounty_post")) {
                player.sendMessage(MessageUtil.warn("You do not have enough Essence."));
                return;
            }
            bounty = new PlayerBounty(
                id,
                player.getUniqueId(),
                player.getName(),
                offer.targetId,
                target.getName(),
                offer.kind,
                offer.amount,
                null,
                Material.AIR,
                0,
                "",
                System.currentTimeMillis()
            );
        } else {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!held.isSimilar(offer.preview) || held.getAmount() < offer.preview.getAmount()) {
                player.sendMessage(MessageUtil.warn("Keep the confirmed item stack in your main hand."));
                return;
            }
            EscrowedItem captured = escrow.capturePartial(
                id,
                player,
                player.getInventory().getHeldItemSlot(),
                held,
                offer.preview.getAmount()
            );
            if (captured == null) {
                player.sendMessage(MessageUtil.error("Could not safely escrow that item."));
                return;
            }
            escrow.retain(captured);
            bounty = new PlayerBounty(
                id,
                player.getUniqueId(),
                player.getName(),
                offer.targetId,
                target.getName(),
                offer.kind,
                0L,
                captured.escrowId(),
                captured.item().getType(),
                captured.item().getAmount(),
                bountyItemName(captured.item()),
                System.currentTimeMillis()
            );
        }
        bounties.put(id, bounty);
        if (!saveBounties()) {
            bounties.remove(id);
            boolean returned = refund(player, bounty);
            if (!returned) {
                plugin.getLogger().severe("Could not return the failed bounty posting " + id + " to " + player.getName() + ".");
            }
            player.sendMessage(returned
                ? MessageUtil.error("The bounty could not be saved; your offer was returned.")
                : MessageUtil.error("The bounty could not be saved and its refund needs staff review."));
            return;
        }
        player.closeInventory();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendMessage(MessageUtil.info("<white>" + player.getName() + "</white> posted a bounty on <red>"
                + bounty.targetName + "</red>: <gold>" + rewardText(bounty, viewer) + "</gold>."));
        }
    }

    private void openCancelConfirm(Player player, UUID id) {
        PlayerBounty bounty = bounties.get(id);
        if (bounty == null || !bounty.creatorId.equals(player.getUniqueId())) return;
        pendingCancels.put(player.getUniqueId(), id);
        Inventory inv = menu(player, "Cancel Bounty?", "Cancel Bounty");
        inv.setItem(13, button(
            bounty.kind == BountyKind.ESSENCE ? Material.AMETHYST_SHARD : bounty.itemMaterial,
            "<gold>Return " + rewardText(bounty, player) + "</gold>",
            List.of("<gray>Target: <white>" + bounty.targetName + "</white></gray>"),
            "close"
        ));
        inv.setItem(29, button(Material.LIME_CONCRETE, "<green><bold>CONFIRM REFUND</bold></green>", List.of("<gray>Remove the bounty and return everything.</gray>"), "bounty:cancel:confirm"));
        inv.setItem(33, button(Material.RED_CONCRETE, "<red>Keep Bounty Active</red>", List.of(), "bounty:mine"));
        player.openInventory(inv);
    }

    private void confirmCancel(Player player) {
        UUID id = pendingCancels.remove(player.getUniqueId());
        if (id != null) cancel(player, id);
    }

    private void cancel(Player player, UUID id) {
        PlayerBounty bounty = bounties.get(id);
        if (bounty == null || !bounty.creatorId.equals(player.getUniqueId())) return;
        if (bounty.kind == BountyKind.ESSENCE && !canReceiveEssence(player, bounty.amount)) {
            player.sendMessage(MessageUtil.warn("Make room below the Essence cap before cancelling this bounty."));
            openBounties(player, true);
            return;
        }
        if (!bounties.remove(id, bounty)) return;
        if (!saveBounties()) {
            bounties.putIfAbsent(id, bounty);
            player.sendMessage(MessageUtil.error("The bounty could not be cancelled safely. Nothing was refunded or removed."));
            openBounties(player, true);
            return;
        }
        if (refund(player, bounty)) {
            player.sendMessage(MessageUtil.success("Bounty cancelled. The full offer was returned."));
        } else {
            plugin.getLogger().severe("Bounty " + bounty.id + " was cancelled but its refund could not be completed for " + player.getName() + ".");
            player.sendMessage(MessageUtil.error("The bounty was cancelled, but its refund needs staff review."));
        }
        openBounties(player, true);
    }

    private boolean refund(Player player, PlayerBounty bounty) {
        if (bounty.kind == BountyKind.ESSENCE) {
            return plugin.getEssenceManager() != null
                && plugin.getEssenceManager().refund(player, bounty.amount, "player_bounty_refund") == bounty.amount;
        }
        return deliverEscrow(bounty, player.getUniqueId(), player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (plugin.getDuelManager() != null && plugin.getDuelManager().isDuelParticipant(victim)) return;
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;
        if (plugin.getTeamManager() != null
            && plugin.getTeamManager().sameTeam(killer.getUniqueId(), victim.getUniqueId())) return;
        List<PlayerBounty> claims = bounties.values().stream()
            .filter(bounty -> bounty.targetId.equals(victim.getUniqueId()))
            .filter(bounty -> !bounty.creatorId.equals(killer.getUniqueId()))
            .toList();
        if (claims.isEmpty()) return;
        long essenceReward = 0L;
        try {
            for (PlayerBounty bounty : claims) {
                if (bounty.kind == BountyKind.ESSENCE) essenceReward = Math.addExact(essenceReward, bounty.amount);
            }
        } catch (ArithmeticException ex) {
            plugin.getLogger().severe("Refused an overflowing bounty payout for " + killer.getName() + ".");
            killer.sendMessage(MessageUtil.error("This bounty payout needs staff review."));
            return;
        }
        if (essenceReward > 0L && !canReceiveEssence(killer, essenceReward)) {
            killer.sendMessage(MessageUtil.warn("Make room below the Essence cap before claiming this bounty."));
            return;
        }
        long now = System.currentTimeMillis();
        String cooldownKey = killer.getUniqueId() + ":" + victim.getUniqueId();
        if (now - claimCooldowns.getOrDefault(cooldownKey, 0L) < 30L * 60L * 1000L) {
            killer.sendMessage(MessageUtil.warn("This victim was claimed too recently for another bounty payout."));
            return;
        }
        Long previousCooldown = claimCooldowns.put(cooldownKey, now);
        List<PlayerBounty> removed = new ArrayList<>();
        for (PlayerBounty bounty : claims) {
            if (bounties.remove(bounty.id, bounty)) removed.add(bounty);
        }
        if (removed.isEmpty()) {
            restoreCooldown(cooldownKey, previousCooldown, now);
            return;
        }
        if (!saveBounties()) {
            removed.forEach(bounty -> bounties.putIfAbsent(bounty.id, bounty));
            restoreCooldown(cooldownKey, previousCooldown, now);
            killer.sendMessage(MessageUtil.error("The bounty claim could not be saved. No rewards were paid and the bounties remain active."));
            return;
        }
        for (PlayerBounty bounty : removed) {
            if (bounty.kind == BountyKind.ESSENCE) {
                if (!plugin.getEssenceManager().credit(killer, bounty.amount, "player_bounty_claim")) {
                    plugin.getLogger().severe("Could not complete Essence payout for claimed bounty " + bounty.id
                        + " to " + killer.getName() + ".");
                }
            } else {
                if (!deliverEscrow(bounty, killer.getUniqueId(), killer.getName())) {
                    plugin.getLogger().severe("Could not locate item escrow for claimed bounty " + bounty.id + ".");
                }
            }
        }
        Bukkit.broadcast(MessageUtil.success("<white>" + killer.getName() + "</white> claimed the bounty on <red>" + victim.getName() + "</red>."));
    }

    private void restoreCooldown(String key, Long previous, long attempted) {
        if (previous == null) claimCooldowns.remove(key, attempted);
        else claimCooldowns.replace(key, attempted, previous);
    }

    private boolean deliverEscrow(PlayerBounty bounty, UUID recipientId, String recipientName) {
        EscrowedItem item = escrow.retainedEscrow(bounty.escrowId);
        if (item == null) return false;
        escrow.retarget(item, recipientId, recipientName, "AWARDING");
        Player recipient = Bukkit.getPlayer(recipientId);
        if (recipient == null || !escrow.give(recipient, item)) {
            escrow.queueRecovery(recipientId, recipientName, item);
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cachePlayerProfile(event.getPlayer());
        escrow.restorePendingRecovery(event.getPlayer());
        resetDaily(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        selectedTargets.remove(playerId);
        pendingOffers.remove(playerId);
        pendingCancels.remove(playerId);
        pendingActions.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isOre(event.getBlock().getType())) return;
        if (plugin.getGoblinHuntManager() == null
            || !plugin.getGoblinHuntManager().isEligibleOreBreak(event.getBlock(), event.getPlayer())) return;
        incrementDaily(event.getPlayer(), dailyOresKey, DAILY_ORES);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) incrementDaily(event.getPlayer(), dailyFishKey, DAILY_FISH);
    }
    void recordHostileKill(Player player) {
        incrementDaily(player, dailyKillsKey, DAILY_KILLS);
    }

    private void incrementDaily(Player player, NamespacedKey key, int cap) {
        resetDaily(player);
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(key, PersistentDataType.INTEGER, Math.min(cap, pdc.getOrDefault(key, PersistentDataType.INTEGER, 0) + 1));
    }

    private void claimDaily(Player player, int bit) {
        resetDaily(player);
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int mask = pdc.getOrDefault(dailyClaimMaskKey, PersistentDataType.INTEGER, 0);
        int progress = switch (bit) {
            case 0 -> pdc.getOrDefault(dailyKillsKey, PersistentDataType.INTEGER, 0);
            case 1 -> pdc.getOrDefault(dailyOresKey, PersistentDataType.INTEGER, 0);
            case 2 -> pdc.getOrDefault(dailyFishKey, PersistentDataType.INTEGER, 0);
            default -> 0;
        };
        int required = bit == 0 ? DAILY_KILLS : bit == 1 ? DAILY_ORES : DAILY_FISH;
        int reward = bit == 0 ? 40 : bit == 1 ? 45 : 35;
        if ((mask & (1 << bit)) != 0 || progress < required) {
            openDailies(player);
            return;
        }
        if (!canReceiveEssence(player, reward)) {
            player.sendMessage(MessageUtil.warn("Make room below the Essence cap before claiming this daily."));
            openDailies(player);
            return;
        }
        if (!plugin.getEssenceManager().credit(player, reward, "tavern_daily")) {
            player.sendMessage(MessageUtil.error("That daily could not be paid. It remains claimable."));
            openDailies(player);
            return;
        }
        pdc.set(dailyClaimMaskKey, PersistentDataType.INTEGER, mask | (1 << bit));
        player.sendMessage(MessageUtil.success("Daily complete. You earned <white>" + reward + " Essence</white>."));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        openDailies(player);
    }

    private void resetDaily(Player player) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (today.equals(pdc.get(dailyDateKey, PersistentDataType.STRING))) return;
        pdc.set(dailyDateKey, PersistentDataType.STRING, today);
        pdc.set(dailyKillsKey, PersistentDataType.INTEGER, 0);
        pdc.set(dailyOresKey, PersistentDataType.INTEGER, 0);
        pdc.set(dailyFishKey, PersistentDataType.INTEGER, 0);
        pdc.set(dailyClaimMaskKey, PersistentDataType.INTEGER, 0);
    }

    private boolean isOre(Material material) {
        return material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }

    private boolean canReceiveEssence(Player player, long amount) {
        return plugin.getEssenceManager() != null
            && plugin.getEssenceManager().isLoaded(player)
            && plugin.getEssenceManager().canCreditFully(player, amount);
    }

    private String rewardText(PlayerBounty bounty, Player viewer) {
        return bounty.kind == BountyKind.ESSENCE
            ? bounty.amount + " Essence"
            : bounty.itemAmount + "x " + bountyDisplayName(bounty, viewer);
    }

    private String bountyDisplayName(PlayerBounty bounty, Player viewer) {
        EscrowedItem retained = bounty.kind == BountyKind.ITEM ? escrow.retainedEscrow(bounty.escrowId) : null;
        if (retained != null && retained.item() != null) {
            return bountyItemDisplayName(retained.item(), viewer);
        }
        return MM.escapeTags(bounty.itemName == null || bounty.itemName.isBlank() ? pretty(bounty.itemMaterial) : bounty.itemName);
    }

    private String bountyItemDisplayName(ItemStack item, Player viewer) {
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSoulImprint(item)) {
            return plugin.getSeasonRelicManager().soulImprintDisplayName(viewer);
        }
        return MM.escapeTags(bountyItemName(item));
    }

    private String bountyItemName(ItemStack item) {
        if (item == null || item.getType().isAir()) return "Item";
        if (plugin.getSeasonRelicManager() != null) {
            String relicName = plugin.getSeasonRelicManager().displayNameFor(plugin.getSeasonRelicManager().relicId(item));
            if (relicName != null && !relicName.isBlank()) return safeItemName(relicName);
        }
        if (plugin.getLegendaryListener() != null && plugin.getLegendaryListener().isOrbOfTheMysticsItem(item)) {
            return "Orb of the Mystics";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            String customName = safeItemName(PLAIN.serialize(meta.displayName()));
            if (!customName.isBlank()) return customName;
        }
        return pretty(item.getType());
    }

    private static String safeItemName(String input) {
        if (input == null) return "";
        String cleaned = input.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 64 ? cleaned : cleaned.substring(0, 64);
    }

    private boolean matchesItemChoice(BountyItemChoice choice, ItemStack item) {
        if (choice == null || item == null || item.getType().isAir()) return false;
        String relicId = plugin.getSeasonRelicManager() == null ? null : plugin.getSeasonRelicManager().relicId(item);
        boolean mysticOrb = plugin.getLegendaryListener() != null && plugin.getLegendaryListener().isOrbOfTheMysticsItem(item);
        return matchesRequestedOffer(choice.token, item.getType(), relicId, mysticOrb);
    }

    static boolean matchesRequestedOffer(String token, Material material, String relicId, boolean mysticOrb) {
        if (token == null || material == null || material == Material.AIR
            || material == Material.CAVE_AIR || material == Material.VOID_AIR) return false;
        return switch (token) {
            case "iron" -> material == Material.IRON_INGOT;
            case "gold" -> material == Material.GOLD_INGOT;
            case "diamond" -> material == Material.DIAMOND;
            case "netherite" -> material == Material.NETHERITE_INGOT;
            case "orb" -> mysticOrb || relicId != null && relicId.endsWith("_orb");
            case "soul_imprint" -> "soul_imprint".equals(relicId);
            case "any" -> true;
            default -> false;
        };
    }

    private String pretty(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private ItemStack playerHead(UUID playerId, String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta itemMeta = head.getItemMeta();
        if (!(itemMeta instanceof SkullMeta skullMeta) || playerId == null) {
            return head;
        }

        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            cachePlayerProfile(online);
        }
        PlayerProfile profile = playerProfiles.get(playerId);
        if (profile == null) {
            String profileName = safeProfileName(playerName);
            profile = profileName == null
                ? Bukkit.createProfile(playerId)
                : Bukkit.createProfile(playerId, profileName);
        }
        skullMeta.setPlayerProfile(profile.clone());
        head.setItemMeta(skullMeta);
        return head;
    }

    private void cachePlayerProfile(Player player) {
        if (player == null) return;
        PlayerProfile profile = player.getPlayerProfile();
        if (profile != null) {
            playerProfiles.put(player.getUniqueId(), profile.clone());
        }
    }

    private static String safeProfileName(String playerName) {
        if (playerName == null || playerName.isBlank()) return null;
        String cleaned = playerName.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.isBlank()) return null;
        return cleaned.substring(0, Math.min(16, cleaned.length()));
    }

    private Inventory menu(Player player, String javaTitle, String bedrockTitle) {
        return Bukkit.createInventory(
            new BountyHolder(player.getUniqueId()),
            MENU_SIZE,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize("<gold><bold>" + javaTitle + "</bold></gold>"),
                bedrockTitle
            )
        );
    }

    private ItemStack button(Material material, String name, List<String> lore, String action) {
        return button(new ItemStack(material), name, lore, action);
    }

    private ItemStack button(ItemStack prototype, String name, List<String> lore, String action) {
        ItemStack item = prototype == null || prototype.getType().isAir()
            ? new ItemStack(Material.BARRIER)
            : prototype.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream().map(MM::deserialize).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private String action(ItemStack item) {
        if (!MenuItemUtil.isVisibleItem(item)) return null;
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private Set<UUID> loadBounties() {
        Set<UUID> retained = new HashSet<>();
        if (!file.isFile()) return retained;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection cooldownRoot = yaml.getConfigurationSection("cooldowns");
        if (cooldownRoot != null) {
            for (String key : cooldownRoot.getKeys(false)) {
                long value = cooldownRoot.getLong(key);
                if (System.currentTimeMillis() - value < 30L * 60L * 1000L) {
                    claimCooldowns.put(key.replace('|', ':'), value);
                }
            }
        }
        ConfigurationSection root = yaml.getConfigurationSection("bounties");
        if (root == null) return retained;
        for (String raw : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(raw);
                String path = "bounties." + raw;
                BountyKind kind = BountyKind.valueOf(yaml.getString(path + ".kind", "ESSENCE"));
                UUID escrowId = parseUuid(yaml.getString(path + ".escrow"));
                Material material = Material.matchMaterial(yaml.getString(path + ".item-material", "AIR"));
                PlayerBounty bounty = new PlayerBounty(
                    id,
                    UUID.fromString(yaml.getString(path + ".creator")),
                    yaml.getString(path + ".creator-name", "Player"),
                    UUID.fromString(yaml.getString(path + ".target")),
                    yaml.getString(path + ".target-name", "Player"),
                    kind,
                    yaml.getLong(path + ".amount"),
                    escrowId,
                    material == null ? Material.AIR : material,
                    yaml.getInt(path + ".item-amount"),
                    yaml.getString(path + ".item-name", "Item"),
                    yaml.getLong(path + ".created")
                );
                if (kind == BountyKind.ESSENCE && bounty.amount <= 0L) {
                    throw new IllegalArgumentException("invalid Essence amount");
                }
                if (kind == BountyKind.ITEM && (escrowId == null || bounty.itemAmount <= 0 || bounty.itemMaterial.isAir())) {
                    throw new IllegalArgumentException("invalid item escrow metadata");
                }
                bounties.put(id, bounty);
                if (escrowId != null) retained.add(escrowId);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipped a malformed player bounty: " + raw);
            }
        }
        return retained;
    }

    private boolean saveBounties() {
        long now = System.currentTimeMillis();
        claimCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= 30L * 60L * 1000L);
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerBounty bounty : bounties.values()) {
            String path = "bounties." + bounty.id;
            yaml.set(path + ".creator", bounty.creatorId.toString());
            yaml.set(path + ".creator-name", bounty.creatorName);
            yaml.set(path + ".target", bounty.targetId.toString());
            yaml.set(path + ".target-name", bounty.targetName);
            yaml.set(path + ".kind", bounty.kind.name());
            yaml.set(path + ".amount", bounty.amount);
            yaml.set(path + ".escrow", bounty.escrowId == null ? null : bounty.escrowId.toString());
            yaml.set(path + ".item-material", bounty.itemMaterial.name());
            yaml.set(path + ".item-amount", bounty.itemAmount);
            yaml.set(path + ".item-name", bounty.itemName);
            yaml.set(path + ".created", bounty.createdAt);
        }
        claimCooldowns.forEach((key, value) ->
            yaml.set("cooldowns." + key.replace(':', '|'), value));
        File temporary = new File(file.getParentFile(), file.getName() + ".next");
        try {
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            try { Files.deleteIfExists(temporary.toPath()); } catch (IOException ignored) { }
            plugin.getLogger().severe("Could not save tavern bounties: " + ex.getMessage());
            return false;
        }
    }
    private static UUID parseUuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private enum BountyKind { ESSENCE, ITEM }

    private enum BountyItemChoice {
        IRON("iron", "Iron Ingots", "an Iron Ingot stack", Material.IRON_INGOT),
        GOLD("gold", "Gold Ingots", "a Gold Ingot stack", Material.GOLD_INGOT),
        DIAMOND("diamond", "Diamonds", "a Diamond stack", Material.DIAMOND),
        NETHERITE("netherite", "Netherite Ingots", "a Netherite Ingot stack", Material.NETHERITE_INGOT),
        ORB("orb", "Any Orb", "any custom Orb stack", Material.ENDER_EYE),
        SOUL_IMPRINT("soul_imprint", "Soul Imprint", "a Soul Imprint stack", Material.END_CRYSTAL),
        ANY("any", "Other Held Item", "a valid item stack", Material.CHEST);

        private final String token;
        private final String displayName;
        private final String heldInstruction;
        private final Material icon;

        BountyItemChoice(String token, String displayName, String heldInstruction, Material icon) {
            this.token = token;
            this.displayName = displayName;
            this.heldInstruction = heldInstruction;
            this.icon = icon;
        }

        private static BountyItemChoice fromToken(String token) {
            for (BountyItemChoice choice : values()) {
                if (choice.token.equals(token)) return choice;
            }
            return null;
        }
    }
    private record PendingOffer(UUID targetId, BountyKind kind, long amount, ItemStack preview) {}
    private record PlayerBounty(
        UUID id,
        UUID creatorId,
        String creatorName,
        UUID targetId,
        String targetName,
        BountyKind kind,
        long amount,
        UUID escrowId,
        Material itemMaterial,
        int itemAmount,
        String itemName,
        long createdAt
    ) {}

    private record BountyHolder(UUID playerId)
        implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
