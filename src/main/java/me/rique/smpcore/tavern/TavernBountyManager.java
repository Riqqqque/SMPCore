package me.rique.smpcore.tavern;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.ItemEscrowService;
import me.rique.smpcore.util.ItemEscrowService.EscrowedItem;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
    private static final int MENU_SIZE = 54;
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
        Set<UUID> retained = loadBounties();
        escrow.start(Bukkit.getOnlinePlayers(), retained);
        bounties.values().removeIf(bounty -> bounty.kind == BountyKind.ITEM && escrow.retainedEscrow(bounty.escrowId) == null);
        saveBounties();
    }

    void shutdown() {
        saveBounties();
        escrow.shutdown();
    }

    void open(Player player) {
        openMain(player);
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
                "<gray>Offer Essence or your held item stack.</gray>"
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
                Material.PLAYER_HEAD,
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
        String heldDescription = held.getType().isAir()
            ? "<red>Hold the item stack you want to post.</red>"
            : "<gray>Escrow: <white>" + held.getAmount() + "x " + pretty(held.getType()) + "</white></gray>";
        inv.setItem(31, button(
            held.getType().isAir() ? Material.BARRIER : held.getType(),
            "<aqua>Held Item Stack</aqua>",
            List.of(heldDescription, "<gray>Custom items remain intact.</gray>"),
            "bounty:offer:item"
        ));
        inv.setItem(49, button(Material.ARROW, "<yellow>Back</yellow>", List.of(), "bounty:targets"));
        player.openInventory(inv);
    }

    private void openConfirm(Player player, PendingOffer offer) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(offer.targetId);
        Inventory inv = menu(player, "Confirm Bounty", "Confirm Bounty");
        String escrowDescription = offer.kind == BountyKind.ESSENCE
            ? "<gray>Escrow: <white>" + offer.amount + " Essence</white></gray>"
            : "<gray>Escrow: <white>" + offer.preview.getAmount() + "x "
                + pretty(offer.preview.getType()) + "</white></gray>";
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
        Inventory inv = menu(
            player,
            mineOnly ? "My Posted Bounties" : "Active Bounties",
            mineOnly ? "My Bounties" : "Active Bounties"
        );
        int slot = 0;
        for (PlayerBounty bounty : bounties.values().stream().sorted(Comparator.comparingLong(PlayerBounty::createdAt)).toList()) {
            if ((mineOnly && !bounty.creatorId.equals(player.getUniqueId())) || slot >= 45) continue;
            String reward = bounty.kind == BountyKind.ESSENCE ? bounty.amount + " Essence" : bounty.itemAmount + "x " + bounty.itemName;
            boolean creator = bounty.creatorId.equals(player.getUniqueId());
            inv.setItem(slot++, button(
                bounty.kind == BountyKind.ESSENCE ? Material.AMETHYST_SHARD : bounty.itemMaterial,
                "<red>" + bounty.targetName + "</red>",
                List.of(
                    "<gray>Posted by: <white>" + bounty.creatorName + "</white></gray>",
                    "<gray>Reward: <white>" + reward + "</white></gray>",
                    creator
                        ? "<yellow>Click to cancel and refund.</yellow>"
                        : "<dark_gray>Defeat this player to claim.</dark_gray>"
                ),
                creator ? "bounty:cancel:" + bounty.id : "close"
            ));
        }
        if (slot == 0) inv.setItem(22, button(Material.PAPER, "<gray>No matching bounties</gray>", List.of(), "close"));
        inv.setItem(49, button(Material.ARROW, "<yellow>Back</yellow>", List.of(), "main"));
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
        else if (action.equals("main")) openMain(player);
        else if (action.equals("daily:open")) openDailies(player);
        else if (action.equals("bounty:list")) openBounties(player, false);
        else if (action.equals("bounty:mine")) openBounties(player, true);
        else if (action.equals("bounty:targets")) openTargets(player);
        else if (action.equals("bounty:offer")) openOffer(player);
        else if (action.equals("bounty:confirm")) confirm(player);
        else if (p.length == 3 && p[0].equals("daily") && p[1].equals("claim")) {
            claimDaily(player, Integer.parseInt(p[2]));
        } else if (p.length == 3 && p[0].equals("bounty") && p[1].equals("target")) {
            selectedTargets.put(player.getUniqueId(), UUID.fromString(p[2]));
            openOffer(player);
        } else if (p.length == 4 && p[0].equals("bounty") && p[1].equals("offer") && p[2].equals("essence")) {
            PendingOffer offer = new PendingOffer(
                selectedTargets.get(player.getUniqueId()),
                BountyKind.ESSENCE,
                Long.parseLong(p[3]),
                null
            );
            pendingOffers.put(player.getUniqueId(), offer);
            openConfirm(player, offer);
        }
        else if (action.equals("bounty:offer:item")) prepareItemOffer(player);
        else if (p.length == 3 && p[0].equals("bounty") && p[1].equals("cancel")) openCancelConfirm(player, UUID.fromString(p[2]));
        else if (action.equals("bounty:cancel:confirm")) confirmCancel(player);
    }

    private void prepareItemOffer(Player player) {
        UUID target = selectedTargets.get(player.getUniqueId());
        ItemStack held = player.getInventory().getItemInMainHand();
        if (target == null || held.getType().isAir() || escrow.hasAnyEscrowMarker(held) || escrow.hasMenuPreviewMarker(held)) {
            player.sendMessage(MessageUtil.warn("Hold a valid item stack first."));
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
        UUID id = UUID.randomUUID();
        PlayerBounty bounty;
        if (offer.kind == BountyKind.ESSENCE) {
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
                pretty(captured.item().getType()),
                System.currentTimeMillis()
            );
        }
        bounties.put(id, bounty);
        if (!saveBounties()) {
            bounties.remove(id);
            refund(player, bounty);
            player.sendMessage(MessageUtil.error("The bounty could not be saved; your offer was returned."));
            return;
        }
        player.closeInventory();
        Bukkit.broadcast(MessageUtil.info("<white>" + player.getName() + "</white> posted a bounty on <red>" + bounty.targetName + "</red>: <gold>" + rewardText(bounty) + "</gold>."));
    }

    private void openCancelConfirm(Player player, UUID id) {
        PlayerBounty bounty = bounties.get(id);
        if (bounty == null || !bounty.creatorId.equals(player.getUniqueId())) return;
        pendingCancels.put(player.getUniqueId(), id);
        Inventory inv = menu(player, "Cancel Bounty?", "Cancel Bounty");
        inv.setItem(13, button(
            bounty.kind == BountyKind.ESSENCE ? Material.AMETHYST_SHARD : bounty.itemMaterial,
            "<gold>Return " + rewardText(bounty) + "</gold>",
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
        if (!bounties.remove(id, bounty)) return;
        if (!saveBounties()) {
            bounties.putIfAbsent(id, bounty);
            player.sendMessage(MessageUtil.error("The bounty could not be cancelled safely. Nothing was refunded or removed."));
            openBounties(player, true);
            return;
        }
        refund(player, bounty);
        player.sendMessage(MessageUtil.success("Bounty cancelled. The full offer was returned."));
        openBounties(player, true);
    }

    private void refund(Player player, PlayerBounty bounty) {
        if (bounty.kind == BountyKind.ESSENCE) {
            plugin.getEssenceManager().credit(player, bounty.amount, "player_bounty_refund");
        } else {
            deliverEscrow(bounty, player.getUniqueId(), player.getName());
        }
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
                plugin.getEssenceManager().credit(killer, bounty.amount, "player_bounty_claim");
            } else {
                deliverEscrow(bounty, killer.getUniqueId(), killer.getName());
            }
        }
        Bukkit.broadcast(MessageUtil.success("<white>" + killer.getName() + "</white> claimed the bounty on <red>" + victim.getName() + "</red>."));
    }

    private void restoreCooldown(String key, Long previous, long attempted) {
        if (previous == null) claimCooldowns.remove(key, attempted);
        else claimCooldowns.replace(key, attempted, previous);
    }

    private void deliverEscrow(PlayerBounty bounty, UUID recipientId, String recipientName) {
        EscrowedItem item = escrow.retainedEscrow(bounty.escrowId);
        if (item == null) return;
        escrow.retarget(item, recipientId, recipientName, "AWARDING");
        Player recipient = Bukkit.getPlayer(recipientId);
        if (recipient == null || !escrow.give(recipient, item)) {
            escrow.queueRecovery(recipientId, recipientName, item);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
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
        pdc.set(dailyClaimMaskKey, PersistentDataType.INTEGER, mask | (1 << bit));
        plugin.getEssenceManager().credit(player, reward, "tavern_daily");
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

    private String rewardText(PlayerBounty bounty) {
        return bounty.kind == BountyKind.ESSENCE
            ? bounty.amount + " Essence"
            : bounty.itemAmount + "x " + bounty.itemName;
    }

    private String pretty(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
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
        ItemStack item = new ItemStack(material);
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
    private UUID parseUuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private enum BountyKind { ESSENCE, ITEM }
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
