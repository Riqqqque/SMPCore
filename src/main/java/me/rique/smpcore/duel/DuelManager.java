package me.rique.smpcore.duel;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.leaderboard.LeaderboardManager;
import me.rique.smpcore.util.AtomicYamlFile;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.ItemEscrowService;
import me.rique.smpcore.util.ItemEscrowService.EscrowPayout;
import me.rique.smpcore.util.ItemEscrowService.EscrowedItem;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DuelManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int ROUND_SECONDS = 150;
    private static final int BETTING_SECONDS = 15;
    private static final int COUNTDOWN_SECONDS = 5;
    private static final int BETWEEN_ROUNDS_SECONDS = 5;
    private static final double SPECTATOR_RADIUS = 29.0D;
    private static final double SPECTATOR_MIN_Y_OFFSET = -0.25D;
    private static final double SPECTATOR_MAX_Y_OFFSET = 8.0D;
    private static final long MAX_ESSENCE_BET = 9_999_999_999L;
    private static final Set<EntityType> CLEANUP_ENTITY_TYPES = Set.of(
        EntityType.TNT,
        EntityType.END_CRYSTAL,
        EntityType.ITEM,
        EntityType.ARROW,
        EntityType.SPECTRAL_ARROW,
        EntityType.TRIDENT,
        EntityType.WIND_CHARGE,
        EntityType.BREEZE_WIND_CHARGE,
        EntityType.FIREWORK_ROCKET,
        EntityType.AREA_EFFECT_CLOUD,
        EntityType.SNOWBALL,
        EntityType.EGG,
        EntityType.ENDER_PEARL,
        EntityType.SPLASH_POTION,
        EntityType.EXPERIENCE_BOTTLE,
        EntityType.EXPERIENCE_ORB
    );

    private final SMPCore plugin;
    private final File arenaFile;
    private final File recoveryFile;
    private final File escrowFile;
    private final File boardsFile;
    private final ItemEscrowService itemWagerEscrow;
    private final NamespacedKey menuActionKey;
    private final NamespacedKey boardKey;
    private final NamespacedKey normalizedHealthKey;
    private final Map<QueueKey, ArrayDeque<QueueEntry>> queues = new LinkedHashMap<>();
    private final Map<UUID, QueueEntry> queuedPlayers = new HashMap<>();
    private final Map<UUID, Challenge> challenges = new HashMap<>();
    private final Map<UUID, PartyInvite> partyInvites = new HashMap<>();
    private final Map<UUID, DuelParty> partiesByMember = new HashMap<>();
    private final Map<UUID, DuelMode> preferredModes = new HashMap<>();
    private final Map<UUID, Integer> preferredTeamSizes = new HashMap<>();
    private final Map<UUID, Integer> preferredRounds = new HashMap<>();
    private final Map<UUID, UUID> selectedBetSides = new HashMap<>();
    private final Map<UUID, Long> selectedBetAmounts = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> recoveries = new LinkedHashMap<>();
    private final Map<UUID, PendingCredit> pendingCredits = new LinkedHashMap<>();
    private final Map<BlockKey, BlockData> temporaryBlocks = new LinkedHashMap<>();
    private final Set<UUID> spawnedEntities = new LinkedHashSet<>();
    private final Map<UUID, UUID> spawnedEntityOwners = new HashMap<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    private final Map<UUID, Board> boards = new LinkedHashMap<>();
    private final Map<UUID, UUID> boardDisplays = new HashMap<>();
    private Arena arena;
    private Match activeMatch;
    private BukkitTask clockTask;
    private BukkitTask boardTask;
    private long nextCreditSweepAt;

    public DuelManager(SMPCore plugin) {
        this.plugin = plugin;
        this.arenaFile = new File(plugin.getDataFolder(), "duel-arena.yml");
        this.recoveryFile = new File(plugin.getDataFolder(), "duel-recoveries.yml");
        this.escrowFile = new File(plugin.getDataFolder(), "duel-escrow.yml");
        this.boardsFile = new File(plugin.getDataFolder(), "duel-leaderboards.yml");
        this.itemWagerEscrow = new ItemEscrowService(plugin, "duel_wager", "duel-item-wagers.yml");
        this.menuActionKey = new NamespacedKey(plugin, "duel_menu_action");
        this.boardKey = new NamespacedKey(plugin, "duel_leaderboard_id");
        this.normalizedHealthKey = new NamespacedKey(plugin, "duel_normalized_health");
    }

    public void start() {
        loadArena();
        loadRecoveries();
        loadPendingCredits();
        loadBoards();
        itemWagerEscrow.start(Bukkit.getOnlinePlayers());
        cleanupStaleArenaEntities();
        int staleFluids = cleanupLooseArenaFluids();
        if (staleFluids > 0) {
            plugin.getLogger().warning("Removed " + staleFluids + " stale duel-arena fluid block(s).");
        }
        clockTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        boardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshBoards, 40L, 20L * 300L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduleRecovery(player, 20L);
            schedulePendingCredit(player, 40L, 0);
        }
    }

    public void shutdown() {
        if (clockTask != null) clockTask.cancel();
        if (boardTask != null) boardTask.cancel();
        clockTask = null;
        boardTask = null;
        if (activeMatch != null) {
            endMatch(null, EndReason.SHUTDOWN);
        }
        for (UUID spectatorId : new ArrayList<>(spectators())) {
            leaveSpectator(spectatorId);
        }
        saveRecoveries();
        savePendingCredits();
        saveBoards();
        removeAllBoardDisplays();
        queues.clear();
        queuedPlayers.clear();
        challenges.clear();
        partyInvites.clear();
        partiesByMember.clear();
        preferredModes.clear();
        preferredTeamSizes.clear();
        preferredRounds.clear();
        selectedBetSides.clear();
        selectedBetAmounts.clear();
        temporaryBlocks.clear();
        spawnedEntities.clear();
        spawnedEntityOwners.clear();
        internalTeleports.clear();
        itemWagerEscrow.shutdown();
    }

    public boolean isDuelParticipant(Player player) {
        return player != null && activeMatch != null && activeMatch.isFighter(player.getUniqueId());
    }

    public boolean isDuelParticipant(UUID playerId) {
        return playerId != null && activeMatch != null && activeMatch.isFighter(playerId);
    }

    public boolean blocksExternalTeleport(Player player) {
        if (player == null || activeMatch == null) return false;
        UUID playerId = player.getUniqueId();
        return activeMatch.isFighter(playerId) || activeMatch.spectators.contains(playerId);
    }

    public boolean areOpponents(UUID first, UUID second) {
        return first != null && second != null && activeMatch != null
            && activeMatch.isFighter(first) && activeMatch.isFighter(second)
            && !Objects.equals(activeMatch.sideOf(first), activeMatch.sideOf(second));
    }

    public boolean isActiveArena(Location location) {
        return activeMatch != null && arena != null && arena.contains(location);
    }

    public boolean isArenaExplosion(Location location) {
        return activeMatch != null && arena != null && arena.containsExpanded(location, 2.0D);
    }

    public boolean allowsArenaBlockPlacement(Player player, Block block) {
        return player != null
            && block != null
            && isFighting(player)
            && arena.contains(block.getLocation())
            && !isInventoryBlock(block.getState())
            && !isRestrictedArenaBlock(block.getType());
    }

    public boolean allowsArenaBlockBreak(Player player, Block block) {
        return player != null
            && block != null
            && isFighting(player)
            && temporaryBlocks.containsKey(BlockKey.of(block));
    }

    public boolean allowsArenaBucket(Player player, Block block) {
        return player != null && block != null && isFighting(player) && arena.contains(block.getLocation());
    }

    public boolean allowsArenaInteraction(PlayerInteractEvent event) {
        if (event == null || event.getClickedBlock() == null || !isFighting(event.getPlayer())
            || !arena.contains(event.getClickedBlock().getLocation())) {
            return false;
        }
        Material held = event.getItem() == null ? Material.AIR : event.getItem().getType();
        return temporaryBlocks.containsKey(BlockKey.of(event.getClickedBlock()))
            || (held.isBlock() && !isInventoryMaterial(held))
            || held == Material.FLINT_AND_STEEL
            || held == Material.FIRE_CHARGE
            || held == Material.END_CRYSTAL
            || held == Material.GLOWSTONE
            || held == Material.WATER_BUCKET
            || held == Material.LAVA_BUCKET
            || held == Material.WIND_CHARGE;
    }

    public boolean allowsArenaFluid(Block from, Block to) {
        if (activeMatch == null || arena == null || from == null || to == null) return false;
        return activeMatch.phase == MatchPhase.FIGHTING
            && arena.contains(from.getLocation())
            && arena.contains(to.getLocation());
    }

    public boolean allowsArenaEntityPlacement(Player player, Entity entity) {
        return player != null && entity != null && isFighting(player)
            && arena.contains(entity.getLocation())
            && entity.getType() == EntityType.END_CRYSTAL;
    }

    public boolean allowsArenaEntitySpawn(Entity entity) {
        return entity != null && activeMatch != null && arena != null
            && arena.containsExpanded(entity.getLocation(), 2.0D)
            && CLEANUP_ENTITY_TYPES.contains(entity.getType());
    }

    public boolean shouldBypassSpawnDamage(EntityDamageEvent event) {
        if (event == null || activeMatch == null) return false;
        if (event.getEntity() instanceof Player player && activeMatch.isFighter(player.getUniqueId())) return true;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = attackingPlayer(byEntity.getDamager());
            return attacker != null && activeMatch.isFighter(attacker.getUniqueId());
        }
        return false;
    }

    public void filterArenaExplosion(List<Block> blocks) {
        if (blocks == null || arena == null || activeMatch == null) return;
        blocks.clear();
    }

    public void openMainMenu(Player player) {
        if (player == null) return;
        DuelMode selected = preferredModes.getOrDefault(player.getUniqueId(), DuelMode.OPEN);
        int selectedTeamSize = preferredTeamSizes.getOrDefault(player.getUniqueId(), rosterFor(player).size());
        if (DuelRules.normalizeTeamSize(selectedTeamSize) < 0) selectedTeamSize = 1;
        int selectedRounds = preferredRounds.getOrDefault(player.getUniqueId(), 1);
        if (DuelRules.normalizeRoundsToWin(selectedRounds) < 0) selectedRounds = 1;
        Inventory inventory = Bukkit.createInventory(
            new DuelMenuHolder(MenuView.MAIN),
            54,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#ef4444:#f59e0b><bold>Veilward Duels</bold></gradient>"), "Veilward Duels")
        );
        decorate(inventory);
        inventory.setItem(4, actionItem(Material.NETHERITE_SWORD, "none", "<gradient:#ef4444:#f59e0b><bold>Veilward Duels</bold></gradient>", List.of(
            "<gray>Low-risk PvP. Your exact inventory,</gray>",
            "<gray>durability, effects, and location return.</gray>"
        )));
        inventory.setItem(10, roundsItem(1, selectedRounds));
        inventory.setItem(11, roundsItem(2, selectedRounds));
        inventory.setItem(12, roundsItem(3, selectedRounds));
        inventory.setItem(14, modeItem(DuelMode.OPEN, selected));
        inventory.setItem(15, modeItem(DuelMode.NO_HEAL, selected));
        inventory.setItem(16, modeItem(DuelMode.MELEE, selected));
        inventory.setItem(19, teamSizeItem(1, selectedTeamSize));
        inventory.setItem(20, teamSizeItem(2, selectedTeamSize));
        inventory.setItem(21, teamSizeItem(3, selectedTeamSize));

        boolean queued = queuedPlayers.containsKey(player.getUniqueId());
        inventory.setItem(23, actionItem(queued ? Material.BARRIER : Material.LIME_CONCRETE, queued ? "leave" : "find",
            queued ? "<red><bold>Leave Queue</bold></red>" : "<green><bold>Find Duel</bold></green>", List.of(
                queued ? "<gray>Stop searching for an opponent.</gray>" : "<gray>Search with the selected rounds,</gray>",
                queued ? "<yellow>Click to leave matchmaking.</yellow>" : "<gray>mode, and team size.</gray>",
                queued ? "" : "<yellow>Click to enter matchmaking.</yellow>"
            ).stream().filter(line -> !line.isEmpty()).toList()));
        inventory.setItem(25, actionItem(Material.NAME_TAG, "challenge", "<yellow><bold>Challenge Player</bold></yellow>", List.of(
            "<gray>Choose an online player or party.</gray>",
            "<gray>Uses the setup selected above.</gray>",
            "<yellow>Click to choose an opponent.</yellow>"
        )));
        inventory.setItem(28, actionItem(Material.PLAYER_HEAD, "party", "<aqua><bold>Duel Party</bold></aqua>", List.of(
            "<gray>Current roster: <white>" + rosterFor(player).size() + "/3</white>.</gray>",
            "<gray>Invite teammates for 2v2 or 3v3.</gray>",
            "<yellow>Click to manage your party.</yellow>"
        )));
        inventory.setItem(30, actionItem(Material.ENDER_EYE, "spectate", "<aqua><bold>Spectate</bold></aqua>", List.of(
            activeMatch == null ? "<gray>No duel is active.</gray>" : "<gray>Watch " + safeName(activeMatch.firstName) + " vs " + safeName(activeMatch.secondName) + ".</gray>",
            "<yellow>Click to enter spectator mode.</yellow>"
        )));
        inventory.setItem(32, actionItem(Material.GOLD_INGOT, "bet", "<gold><bold>Fight Betting</bold></gold>", List.of(
            "<gray>Stake any Essence amount or a held stack.</gray>",
            "<gray>Matching pools split by stake.</gray>",
            "<dark_gray>Fighters may only back their own team.</dark_gray>",
            "<yellow>Click to view the current pool.</yellow>"
        )));
        boolean involved = isDuelParticipant(player) || spectators().contains(player.getUniqueId());
        inventory.setItem(34, actionItem(involved ? Material.BARRIER : Material.BOOK, involved ? "leave" : "info",
            involved ? "<red><bold>Leave Duel</bold></red>" : "<aqua><bold>How It Works</bold></aqua>",
            involved ? List.of("<gray>Leave your duel or spectator seat.</gray>") : List.of(
                "<gray>Rounds last 2m 30s.</gray>",
                "<gray>Eliminate the whole enemy team.</gray>",
                "<gray>Timeouts use team damage, then health.</gray>",
                "<gray>TNT damages players, never the arena.</gray>"
            )));

        Challenge pending = challenges.get(player.getUniqueId());
        if (pending != null && pending.expiresAt >= System.currentTimeMillis()) {
            inventory.setItem(47, actionItem(Material.LIME_CONCRETE, "challengeaccept:" + pending.challenger,
                "<green><bold>Accept " + safeName(playerName(pending.challenger)) + "</bold></green>", List.of(
                    "<gray>" + pending.teamSize + "v" + pending.teamSize + " • First to " + pending.roundsToWin + " • " + pending.mode.display + "</gray>",
                    "<yellow>Click to accept.</yellow>"
                )));
            inventory.setItem(51, actionItem(Material.RED_CONCRETE, "challengedeny", "<red><bold>Decline Challenge</bold></red>", List.of(
                "<gray>Reject this duel request.</gray>"
            )));
        }
        player.openInventory(inventory);
    }

    public void openPartyMenu(Player player) {
        if (player == null) return;
        DuelParty party = partiesByMember.get(player.getUniqueId());
        List<UUID> roster = rosterFor(player);
        Inventory inventory = Bukkit.createInventory(
            new DuelMenuHolder(MenuView.PARTY),
            54,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#38bdf8:#a78bfa><bold>Duel Party</bold></gradient>"), "Duel Party")
        );
        decorate(inventory);
        inventory.setItem(4, actionItem(Material.TOTEM_OF_UNDYING, "none", "<aqua><bold>Your Duel Roster</bold></aqua>", List.of(
            "<gray>Parties hold up to three fighters.</gray>",
            "<gray>The captain queues and challenges.</gray>"
        )));
        int[] memberSlots = {11, 13, 15};
        for (int index = 0; index < memberSlots.length; index++) {
            if (index >= roster.size()) {
                inventory.setItem(memberSlots[index], actionItem(Material.GRAY_DYE, "none", "<gray>Open Slot</gray>", List.of("<dark_gray>Invite an online player below.</dark_gray>")));
                continue;
            }
            UUID memberId = roster.get(index);
            String name = playerName(memberId);
            boolean captain = party == null ? memberId.equals(player.getUniqueId()) : party.captain.equals(memberId);
            boolean removable = party != null && party.captain.equals(player.getUniqueId()) && !memberId.equals(player.getUniqueId());
            inventory.setItem(memberSlots[index], actionItem(captain ? Material.GOLDEN_HELMET : Material.PLAYER_HEAD,
                removable ? "partykick:" + memberId : "none",
                "<white><bold>" + safeName(name) + "</bold></white>", removable
                    ? List.of("<gray>Party member</gray>", "<red>Click to remove.</red>")
                    : List.of(captain ? "<gold>Party captain</gold>" : "<gray>Party member</gray>")));
        }

        if (party == null || party.captain.equals(player.getUniqueId())) {
            List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .map(candidate -> (Player) candidate)
                .filter(candidate -> !candidate.equals(player))
                .filter(candidate -> !roster.contains(candidate.getUniqueId()))
                .filter(candidate -> partiesByMember.get(candidate.getUniqueId()) == null)
                .filter(candidate -> !queuedPlayers.containsKey(candidate.getUniqueId()))
                .filter(candidate -> activeMatch == null || (!activeMatch.isFighter(candidate.getUniqueId()) && !activeMatch.spectators.contains(candidate.getUniqueId())))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(14)
                .toList();
            int[] inviteSlots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
            for (int index = 0; index < candidates.size(); index++) {
                Player candidate = candidates.get(index);
                inventory.setItem(inviteSlots[index], actionItem(Material.LIME_DYE, "partyinvite:" + candidate.getUniqueId(),
                    "<green>Invite " + safeName(candidate.getName()) + "</green>", List.of("<gray>Invitation lasts 60 seconds.</gray>")));
            }
        } else {
            inventory.setItem(31, actionItem(Material.PAPER, "none", "<yellow>Captain Controls Invites</yellow>", List.of(
                "<gray>Ask <white>" + safeName(playerName(party.captain)) + "</white> to invite teammates.</gray>"
            )));
        }

        String leaveAction = party != null && party.captain.equals(player.getUniqueId()) && party.members.size() > 1 ? "partydisband" : "partyleave";
        String leaveName = leaveAction.equals("partydisband") ? "<red><bold>Disband Party</bold></red>" : "<red><bold>Leave Party</bold></red>";
        inventory.setItem(49, actionItem(Material.BARRIER, leaveAction, leaveName, List.of(
            party == null ? "<gray>You are already playing solo.</gray>" : "<gray>This cannot be changed while queued.</gray>"
        )));
        PartyInvite pendingInvite = partyInvites.get(player.getUniqueId());
        if (pendingInvite != null && pendingInvite.expiresAt >= System.currentTimeMillis()) {
            inventory.setItem(47, actionItem(Material.LIME_CONCRETE, "partyaccept:" + pendingInvite.captain,
                "<green><bold>Accept " + safeName(playerName(pendingInvite.captain)) + "</bold></green>", List.of("<gray>Join this duel party.</gray>")));
            inventory.setItem(51, actionItem(Material.RED_CONCRETE, "partydeny", "<red><bold>Decline Invite</bold></red>", List.of("<gray>Reject the pending invitation.</gray>")));
        }
        inventory.setItem(45, actionItem(Material.ARROW, "back", "<yellow>Back</yellow>", List.of("<gray>Return to duels.</gray>")));
        player.openInventory(inventory);
    }

    public void openChallengeMenu(Player player) {
        if (player == null) return;
        if (!isPartyCaptain(player)) {
            player.sendMessage(MessageUtil.warn("Only your duel party captain can challenge another team."));
            return;
        }
        int rounds = preferredRounds.getOrDefault(player.getUniqueId(), 1);
        DuelMode mode = preferredModes.getOrDefault(player.getUniqueId(), DuelMode.OPEN);
        int teamSize = rosterFor(player).size();
        Inventory inventory = Bukkit.createInventory(
            new DuelMenuHolder(MenuView.CHALLENGE),
            54,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#f59e0b:#ef4444><bold>Challenge Player</bold></gradient>"), "Challenge Player")
        );
        decorate(inventory);
        inventory.setItem(4, actionItem(Material.NAME_TAG, "none", "<yellow><bold>Choose an Opponent</bold></yellow>", List.of(
            "<gray>" + teamSize + "v" + teamSize + " • First to " + rounds + " • " + mode.display + "</gray>",
            "<gray>The other captain must accept.</gray>"
        )));

        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
            .map(candidate -> (Player) candidate)
            .filter(candidate -> !candidate.equals(player))
            .filter(this::isPartyCaptain)
            .filter(candidate -> rosterFor(candidate).size() == teamSize)
            .filter(candidate -> !queuedPlayers.containsKey(candidate.getUniqueId()))
            .filter(candidate -> activeMatch == null || (!activeMatch.isFighter(candidate.getUniqueId()) && !activeMatch.spectators.contains(candidate.getUniqueId())))
            .filter(this::canEnterSilently)
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .limit(28)
            .toList();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int index = 0; index < candidates.size(); index++) {
            Player candidate = candidates.get(index);
            inventory.setItem(slots[index], actionItem(Material.PLAYER_HEAD, "challenge:" + candidate.getUniqueId(),
                "<white><bold>" + safeName(candidate.getName()) + "</bold></white>", List.of(
                    teamSize == 1 ? "<gray>Solo opponent</gray>" : "<gray>Party: " + rosterNames(rosterFor(candidate)) + "</gray>",
                    "<yellow>Click to send the challenge.</yellow>"
                )));
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, actionItem(Material.GRAY_DYE, "none", "<gray>No Matching Opponents</gray>", List.of(
                "<gray>They must be online with a matching party size.</gray>",
                "<gray>You can still use matchmaking.</gray>"
            )));
        }
        inventory.setItem(45, actionItem(Material.ARROW, "back", "<yellow>Back</yellow>", List.of("<gray>Return to duel setup.</gray>")));
        inventory.setItem(49, actionItem(Material.LIME_CONCRETE, "find", "<green><bold>Use Matchmaking</bold></green>", List.of(
            "<gray>Search for any matching opponent.</gray>"
        )));
        player.openInventory(inventory);
    }

    public void openBetMenu(Player player) {
        if (player == null) return;
        Match match = activeMatch;
        if (match == null) {
            player.sendMessage(MessageUtil.warn("There is no active duel to bet on."));
            return;
        }
        Inventory inventory = Bukkit.createInventory(
            new DuelMenuHolder(MenuView.BET),
            54,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#facc15:#22c55e><bold>Duel Betting</bold></gradient>"), "Duel Betting")
        );
        decorate(inventory);
        boolean bettingOpen = match.phase == MatchPhase.BETTING;
        boolean essenceOpen = bettingOpen && !match.wagers.containsKey(player.getUniqueId());
        boolean hasRestorableArenaState = match.isFighter(player.getUniqueId()) || match.spectators.contains(player.getUniqueId());
        boolean itemOpen = bettingOpen && !hasRestorableArenaState && !match.itemWagers.containsKey(player.getUniqueId());
        UUID selected = selectedBetSides.get(player.getUniqueId());
        if (selected != null && !match.isSide(selected)) {
            selectedBetSides.remove(player.getUniqueId());
            selected = null;
        }
        long balance = plugin.getEssenceManager().balance(player);
        long selectedAmount = Math.max(1L, Math.min(balance > 0L ? balance : 1L,
            selectedBetAmounts.getOrDefault(player.getUniqueId(), 1L)));
        selectedBetAmounts.put(player.getUniqueId(), selectedAmount);
        inventory.setItem(4, actionItem(Material.GOLD_BLOCK, "none", "<gold><bold>Parimutuel Betting</bold></gold>", List.of(
            "<gray>Essence and each exact item form separate pools.</gray>",
            "<gray>Winning bettors split matching pools by stake.</gray>",
            "<gray>Current total: <white>" + totalPool(match) + " Essence</white>.</gray>",
            bettingOpen ? "<green>Betting is open.</green>" : "<red>Betting is locked.</red>"
        )));
        inventory.setItem(20, fighterBetItem(match, match.first, match.firstName, selected));
        inventory.setItem(24, fighterBetItem(match, match.second, match.secondName, selected));

        inventory.setItem(28, amountButton(Material.RED_DYE, -64L, "-64"));
        inventory.setItem(29, amountButton(Material.RED_DYE, -10L, "-10"));
        inventory.setItem(30, amountButton(Material.RED_DYE, -1L, "-1"));
        inventory.setItem(31, actionItem(Material.AMETHYST_SHARD, "none", "<light_purple><bold>" + selectedAmount + " Essence</bold></light_purple>", List.of(
            "<gray>Balance: <white>" + balance + "</white>.</gray>",
            "<gray>Exact amount: <white>/duel bet essence &lt;amount&gt;</white></gray>"
        )));
        inventory.setItem(32, amountButton(Material.LIME_DYE, 1L, "+1"));
        inventory.setItem(33, amountButton(Material.LIME_DYE, 10L, "+10"));
        inventory.setItem(34, amountButton(Material.LIME_DYE, 64L, "+64"));
        inventory.setItem(35, actionItem(Material.GOLD_NUGGET, "stakemax", "<gold><bold>Max Balance</bold></gold>", List.of(
            "<gray>Set the stake to your current Essence balance.</gray>"
        )));

        DuelRules.Wager own = match.wagers.get(player.getUniqueId());
        if (own != null) {
            inventory.setItem(38, actionItem(Material.LIME_DYE, "none", "<green><bold>Essence Bet Locked</bold></green>", List.of(
                "<gray>" + own.amount() + " Essence on <white>" + fighterName(match, own.side()) + "</white>.</gray>"
            )));
        } else {
            inventory.setItem(38, actionItem(essenceOpen ? Material.LIME_CONCRETE : Material.RED_CONCRETE, "stakeessence",
                essenceOpen ? "<green><bold>Bet " + selectedAmount + " Essence</bold></green>" : "<red><bold>Essence Betting Closed</bold></red>", List.of(
                    selected == null ? "<gray>Choose a team first.</gray>" : "<gray>Back <white>" + fighterName(match, selected) + "</white>.</gray>",
                    essenceOpen ? "<yellow>Click once to lock the bet.</yellow>" : "<gray>Wait for the next duel.</gray>"
                )));
        }

        ItemWager itemWager = match.itemWagers.get(player.getUniqueId());
        ItemStack held = player.getInventory().getItemInMainHand();
        if (itemWager != null) {
            inventory.setItem(42, actionItem(itemWager.escrowed.item().getType(), "none", "<green><bold>Item Bet Locked</bold></green>", List.of(
                "<gray>" + itemWager.escrowed.item().getAmount() + "x " + readableItemName(itemWager.escrowed.item()) + "</gray>",
                "<gray>Backed <white>" + fighterName(match, itemWager.side) + "</white>.</gray>"
            )));
        } else {
            boolean validHeld = !isUnsafeItemWager(held);
            inventory.setItem(42, actionItem(validHeld ? held.getType() : Material.HOPPER, "stakeitem",
                itemOpen && validHeld ? "<aqua><bold>Bet Held Stack</bold></aqua>" : "<gray><bold>Hold a Safe Item Stack</bold></gray>", List.of(
                    validHeld ? "<gray>Stake <white>" + held.getAmount() + "x " + readableItemName(held) + "</white>.</gray>" : "<gray>Backpacks, custom relics, bundles,</gray>",
                    validHeld ? "<gray>Use <white>/duel bet item &lt;amount&gt;</white> for part.</gray>" : "<gray>and filled containers cannot be wagered.</gray>",
                    hasRestorableArenaState ? "<red>Arena fighters and spectators use Essence only.</red>"
                        : itemOpen && validHeld ? "<yellow>Click once to lock the stack.</yellow>" : "<red>Item wager unavailable.</red>"
                )));
        }
        inventory.setItem(40, actionItem(Material.BOOK, "none", "<aqua><bold>Fair Item Pools</bold></aqua>", List.of(
            "<gray>Only identical items compete together.</gray>",
            "<gray>Unmatched item bets are refunded.</gray>",
            "<gray>Full inventories receive a safe pending payout.</gray>"
        )));
        inventory.setItem(49, actionItem(Material.ARROW, "back", "<yellow>Back</yellow>", List.of("<gray>Return to duels.</gray>")));
        player.openInventory(inventory);
    }

    public void joinQueue(Player player, int roundsToWin, DuelMode mode) {
        int teamSize = preferredTeamSizes.getOrDefault(player.getUniqueId(), rosterFor(player).size());
        joinQueue(player, roundsToWin, mode, teamSize);
    }

    public void joinSelectedQueue(Player player) {
        if (player == null) return;
        joinQueue(
            player,
            preferredRounds.getOrDefault(player.getUniqueId(), 1),
            preferredModes.getOrDefault(player.getUniqueId(), DuelMode.OPEN),
            preferredTeamSizes.getOrDefault(player.getUniqueId(), rosterFor(player).size())
        );
    }

    public void challengeSelected(Player challenger, Player target) {
        if (challenger == null) return;
        challenge(
            challenger,
            target,
            preferredRounds.getOrDefault(challenger.getUniqueId(), 1),
            preferredModes.getOrDefault(challenger.getUniqueId(), DuelMode.OPEN)
        );
    }

    public void acceptPendingChallenge(Player target) {
        Challenge challenge = target == null ? null : challenges.get(target.getUniqueId());
        Player challenger = challenge == null ? null : Bukkit.getPlayer(challenge.challenger);
        if (challenge == null || challenger == null) {
            if (target != null) target.sendMessage(MessageUtil.warn("You have no active duel challenge."));
            return;
        }
        acceptChallenge(target, challenger);
    }

    public void placeSelectedEssenceBet(Player player, long amount) {
        placeBet(player, amount);
    }

    public void placeSelectedItemBet(Player player, int amount) {
        placeItemBet(player, amount);
    }

    public void joinQueue(Player player, int roundsToWin, DuelMode mode, int teamSize) {
        if (player == null) return;
        int normalized = DuelRules.normalizeRoundsToWin(roundsToWin);
        int normalizedTeamSize = DuelRules.normalizeTeamSize(teamSize);
        if (normalized < 0 || normalizedTeamSize < 0 || mode == null) {
            player.sendMessage(MessageUtil.error("Choose 1-3 round wins, a 1v1-3v3 size, and a valid mode."));
            return;
        }
        if (!arenaReady(player)) return;
        List<Player> roster = onlineRoster(player, normalizedTeamSize, true);
        if (roster == null || !canEnterRoster(roster, player)) return;
        leaveQueue(player.getUniqueId(), false);
        QueueKey key = new QueueKey(normalizedTeamSize, normalized, mode);
        ArrayDeque<QueueEntry> queue = queues.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        QueueEntry entry = QueueEntry.capture(key, player.getUniqueId(), roster);
        QueueEntry opponent = activeMatch == null ? pollEligibleTeam(queue, entry.members) : null;
        if (opponent != null) {
            List<Player> opposingRoster = onlinePlayers(opponent.members);
            if (opposingRoster != null && canEnterRoster(opposingRoster, player)) {
                removeQueuedEntry(opponent, false);
                preferredTeamSizes.put(player.getUniqueId(), normalizedTeamSize);
                startMatch(opposingRoster, roster, normalized, mode);
                return;
            }
        }
        queue.addLast(entry);
        for (UUID memberId : entry.members) queuedPlayers.put(memberId, entry);
        preferredTeamSizes.put(player.getUniqueId(), normalizedTeamSize);
        preferredRounds.put(player.getUniqueId(), normalized);
        preferredModes.put(player.getUniqueId(), mode);
        for (Player member : roster) {
            member.sendMessage(MessageUtil.success("Queued for <white>" + normalizedTeamSize + "v" + normalizedTeamSize + "</white>, first to <white>" + normalized + "</white> in <white>" + mode.display + "</white>."));
            member.closeInventory();
        }
    }

    public void challenge(Player challenger, Player target, int roundsToWin, DuelMode mode) {
        if (challenger == null || target == null || challenger.equals(target)) {
            if (challenger != null) challenger.sendMessage(MessageUtil.error("Choose another online player."));
            return;
        }
        if (!arenaReady(challenger)) return;
        int normalized = DuelRules.normalizeRoundsToWin(roundsToWin);
        if (normalized < 0 || mode == null) {
            challenger.sendMessage(MessageUtil.error("Choose 1, 2, or 3 round wins and a valid mode."));
            return;
        }
        List<UUID> challengerIds = rosterFor(challenger);
        List<UUID> targetIds = rosterFor(target);
        if (!isPartyCaptain(challenger) || !isPartyCaptain(target)) {
            challenger.sendMessage(MessageUtil.warn("Both challengers must be their duel party captain."));
            return;
        }
        if (challengerIds.size() != targetIds.size()) {
            challenger.sendMessage(MessageUtil.warn("Both duel parties must have the same number of fighters."));
            return;
        }
        List<Player> challengerRoster = onlinePlayers(challengerIds);
        List<Player> targetRoster = onlinePlayers(targetIds);
        if (challengerRoster == null || targetRoster == null || !canEnterRoster(challengerRoster, challenger) || !canEnterRoster(targetRoster, challenger)) return;
        Challenge challenge = new Challenge(challenger.getUniqueId(), List.copyOf(challengerIds), challengerIds.size(), normalized, mode, System.currentTimeMillis() + 60_000L);
        challenges.put(target.getUniqueId(), challenge);
        preferredRounds.put(challenger.getUniqueId(), normalized);
        preferredModes.put(challenger.getUniqueId(), mode);
        String format = challengerIds.size() + "v" + challengerIds.size();
        sendToRoster(challengerIds, MessageUtil.success("Challenged <white>" + target.getName() + "'s party</white> to " + format + " " + mode.display + ", first to " + normalized + "."));
        sendToRoster(targetIds, MessageUtil.info("<white>" + challenger.getName() + "'s party</white> challenged you to " + format + " " + mode.display + ", first to " + normalized + "."));
        target.sendMessage(MessageUtil.info("Use <white>/duel accept " + challenger.getName() + "</white> or <white>/duel deny</white> within 60 seconds."));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.25f);
    }

    public void acceptChallenge(Player target, Player challenger) {
        if (target == null || challenger == null) return;
        Challenge challenge = challenges.get(target.getUniqueId());
        if (challenge == null || !challenge.challenger.equals(challenger.getUniqueId()) || challenge.expiresAt < System.currentTimeMillis()) {
            target.sendMessage(MessageUtil.error("That duel challenge is no longer active."));
            challenges.remove(target.getUniqueId());
            return;
        }
        if (activeMatch != null) {
            target.sendMessage(MessageUtil.warn("The arena is busy. Try again after the current duel."));
            return;
        }
        if (!isPartyCaptain(target) || !isPartyCaptain(challenger)) {
            target.sendMessage(MessageUtil.warn("Only each duel party captain can accept this challenge."));
            return;
        }
        List<UUID> challengerIds = rosterFor(challenger);
        List<UUID> targetIds = rosterFor(target);
        if (!challenge.challengerTeam.equals(challengerIds) || targetIds.size() != challenge.teamSize) {
            target.sendMessage(MessageUtil.warn("A party roster changed. Send a new challenge with matching teams."));
            challenges.remove(target.getUniqueId());
            return;
        }
        List<Player> challengerRoster = onlinePlayers(challengerIds);
        List<Player> targetRoster = onlinePlayers(targetIds);
        if (challengerRoster == null || targetRoster == null || !canEnterRoster(challengerRoster, target)
            || !canEnterRoster(targetRoster, target) || !arenaReady(target)) return;
        challenges.remove(target.getUniqueId());
        startMatch(challengerRoster, targetRoster, challenge.roundsToWin, challenge.mode);
    }

    public void denyChallenge(Player target) {
        Challenge challenge = target == null ? null : challenges.remove(target.getUniqueId());
        if (challenge == null) {
            if (target != null) target.sendMessage(MessageUtil.info("You have no active duel challenge."));
            return;
        }
        target.sendMessage(MessageUtil.info("Duel challenge declined."));
        Player challenger = Bukkit.getPlayer(challenge.challenger);
        if (challenger != null) challenger.sendMessage(MessageUtil.warn(target.getName() + " declined your duel challenge."));
    }

    public void inviteToParty(Player captain, Player target) {
        if (captain == null || target == null || captain.equals(target)) {
            if (captain != null) captain.sendMessage(MessageUtil.error("Choose another online player."));
            return;
        }
        if (!canChangeParty(captain) || !canChangeParty(target)) return;
        DuelParty party = partiesByMember.get(captain.getUniqueId());
        if (party != null && !party.captain.equals(captain.getUniqueId())) {
            captain.sendMessage(MessageUtil.warn("Only your duel party captain can invite players."));
            return;
        }
        if (partiesByMember.containsKey(target.getUniqueId())) {
            captain.sendMessage(MessageUtil.warn(target.getName() + " is already in a duel party."));
            return;
        }
        if (party != null && party.members.size() >= 3) {
            captain.sendMessage(MessageUtil.warn("Your duel party is already full."));
            return;
        }
        partyInvites.put(target.getUniqueId(), new PartyInvite(captain.getUniqueId(), System.currentTimeMillis() + 60_000L));
        captain.sendMessage(MessageUtil.success("Invited <white>" + target.getName() + "</white> to your duel party."));
        target.sendMessage(MessageUtil.info("<white>" + captain.getName() + "</white> invited you to a duel party."));
        target.sendMessage(MessageUtil.info("Use <white>/duel party accept " + captain.getName() + "</white> within 60 seconds."));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.35F);
    }

    public void acceptPartyInvite(Player player, Player captain) {
        if (player == null || captain == null) return;
        PartyInvite invite = partyInvites.get(player.getUniqueId());
        if (invite == null || !invite.captain.equals(captain.getUniqueId()) || invite.expiresAt < System.currentTimeMillis()) {
            player.sendMessage(MessageUtil.error("That duel party invitation is no longer active."));
            partyInvites.remove(player.getUniqueId());
            return;
        }
        if (!canChangeParty(player) || !canChangeParty(captain)) return;
        if (partiesByMember.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Leave your current duel party first."));
            return;
        }
        DuelParty party = partiesByMember.get(captain.getUniqueId());
        if (party != null && (!party.captain.equals(captain.getUniqueId()) || party.members.size() >= 3)) {
            player.sendMessage(MessageUtil.warn("That duel party is no longer available."));
            partyInvites.remove(player.getUniqueId());
            return;
        }
        if (party == null) {
            party = new DuelParty(captain.getUniqueId());
            partiesByMember.put(captain.getUniqueId(), party);
        }
        party.members.add(player.getUniqueId());
        partiesByMember.put(player.getUniqueId(), party);
        partyInvites.remove(player.getUniqueId());
        updatePartyPreferredSize(party);
        sendToRoster(party.members, MessageUtil.success("<white>" + player.getName() + "</white> joined the duel party. Roster: <white>" + party.members.size() + "/3</white>."));
    }

    public void declinePartyInvite(Player player) {
        PartyInvite invite = player == null ? null : partyInvites.remove(player.getUniqueId());
        if (invite == null) {
            if (player != null) player.sendMessage(MessageUtil.info("You have no active duel party invitation."));
            return;
        }
        player.sendMessage(MessageUtil.info("Duel party invitation declined."));
        Player captain = Bukkit.getPlayer(invite.captain);
        if (captain != null) captain.sendMessage(MessageUtil.warn(player.getName() + " declined your duel party invitation."));
    }

    public void leaveParty(Player player) {
        if (player == null || !canChangeParty(player)) return;
        DuelParty party = partiesByMember.get(player.getUniqueId());
        if (party == null) {
            player.sendMessage(MessageUtil.info("You are already playing solo."));
            return;
        }
        party.members.remove(player.getUniqueId());
        partiesByMember.remove(player.getUniqueId());
        preferredTeamSizes.put(player.getUniqueId(), 1);
        if (party.members.isEmpty()) {
            player.sendMessage(MessageUtil.info("You left your duel party."));
            return;
        }
        if (party.captain.equals(player.getUniqueId())) party.captain = party.members.iterator().next();
        if (party.members.size() == 1) {
            UUID remaining = party.members.iterator().next();
            partiesByMember.remove(remaining);
            preferredTeamSizes.put(remaining, 1);
            Player remainingPlayer = Bukkit.getPlayer(remaining);
            if (remainingPlayer != null) remainingPlayer.sendMessage(MessageUtil.info("The duel party disbanded; you are playing solo again."));
        } else {
            updatePartyPreferredSize(party);
            sendToRoster(party.members, MessageUtil.info("<white>" + player.getName() + "</white> left the party. <white>" + playerName(party.captain) + "</white> is captain."));
        }
        player.sendMessage(MessageUtil.info("You left your duel party."));
    }

    public void disbandParty(Player player) {
        if (player == null || !canChangeParty(player)) return;
        DuelParty party = partiesByMember.get(player.getUniqueId());
        if (party == null || !party.captain.equals(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Only the duel party captain can disband it."));
            return;
        }
        List<UUID> members = List.copyOf(party.members);
        for (UUID memberId : members) {
            partiesByMember.remove(memberId);
            preferredTeamSizes.put(memberId, 1);
        }
        sendToRoster(members, MessageUtil.info("The duel party was disbanded."));
    }

    public void kickFromParty(Player captain, Player target) {
        if (captain == null || target == null || !canChangeParty(captain) || !canChangeParty(target)) return;
        DuelParty party = partiesByMember.get(captain.getUniqueId());
        if (party == null || !party.captain.equals(captain.getUniqueId())) {
            captain.sendMessage(MessageUtil.warn("Only the duel party captain can remove members."));
            return;
        }
        if (target.equals(captain) || !party.members.remove(target.getUniqueId())) {
            captain.sendMessage(MessageUtil.warn("That player is not a removable member of your party."));
            return;
        }
        partiesByMember.remove(target.getUniqueId());
        preferredTeamSizes.put(target.getUniqueId(), 1);
        target.sendMessage(MessageUtil.warn("You were removed from " + captain.getName() + "'s duel party."));
        if (party.members.size() == 1) {
            partiesByMember.remove(captain.getUniqueId());
            preferredTeamSizes.put(captain.getUniqueId(), 1);
            captain.sendMessage(MessageUtil.info("Your duel party is now solo and was closed."));
        } else {
            updatePartyPreferredSize(party);
            sendToRoster(party.members, MessageUtil.info("<white>" + target.getName() + "</white> was removed from the duel party."));
        }
    }

    public void sendPartyStatus(Player player) {
        if (player == null) return;
        List<UUID> roster = rosterFor(player);
        DuelParty party = partiesByMember.get(player.getUniqueId());
        player.sendMessage(MessageUtil.info("Duel party <white>" + roster.size() + "/3</white>: <white>" + rosterNames(roster) + "</white>."));
        if (party != null) player.sendMessage(MessageUtil.info("Captain: <white>" + playerName(party.captain) + "</white>."));
    }

    public void leave(Player player) {
        if (player == null) return;
        if (leaveQueue(player.getUniqueId(), true)) return;
        if (spectators().contains(player.getUniqueId())) {
            leaveSpectator(player.getUniqueId());
            player.sendMessage(MessageUtil.info("You left the duel spectators."));
            return;
        }
        if (isDuelParticipant(player)) {
            UUID winner = activeMatch.opponent(player.getUniqueId());
            endMatch(winner, EndReason.FORFEIT);
            return;
        }
        player.sendMessage(MessageUtil.info("You are not queued, fighting, or spectating."));
    }

    public void spectate(Player player) {
        if (player == null || activeMatch == null || !arenaReady(player)) {
            if (player != null && activeMatch == null) player.sendMessage(MessageUtil.warn("There is no active duel to spectate."));
            return;
        }
        if (activeMatch.isFighter(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("You are already fighting."));
            return;
        }
        if (spectators().contains(player.getUniqueId())) return;
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        recoveries.put(player.getUniqueId(), snapshot);
        saveRecoveries();
        activeMatch.spectators.add(player.getUniqueId());
        player.closeInventory();
        player.setGameMode(GameMode.SPECTATOR);
        teleportInternal(player, arena.spectatorView());
        player.sendMessage(MessageUtil.info("Spectating <white>" + activeMatch.firstName + "</white> vs <white>" + activeMatch.secondName + "</white>. Use <white>/duel leave</white> to return."));
    }

    public void setArenaPoint(Player player, String input) {
        if (player == null || input == null) return;
        String key = input.toLowerCase(Locale.ROOT);
        Location value = player.getLocation().clone();
        arena = arena == null ? new Arena() : arena;
        switch (key) {
            case "lobby" -> arena.lobby = value;
            case "fighter1", "first", "one" -> arena.first = value;
            case "fighter1b", "firstb", "oneb" -> arena.firstTwo = value;
            case "fighter1c", "firstc", "onec" -> arena.firstThree = value;
            case "fighter2", "second", "two" -> arena.second = value;
            case "fighter2b", "secondb", "twob" -> arena.secondTwo = value;
            case "fighter2c", "secondc", "twoc" -> arena.secondThree = value;
            case "spectator", "spectate" -> arena.spectator = value;
            case "corner1", "pos1" -> arena.cornerOne = value;
            case "corner2", "pos2" -> arena.cornerTwo = value;
            default -> {
                player.sendMessage(MessageUtil.error("Point must be lobby, fighter1/1b/1c, fighter2/2b/2c, spectator, corner1, or corner2."));
                return;
            }
        }
        saveArena();
        player.sendMessage(MessageUtil.success("Set duel arena <white>" + key + "</white> at your location."));
    }

    public void sendAdminStatus(Player player) {
        if (player == null) return;
        player.sendMessage(MessageUtil.info("Duel arena: <white>" + (arena != null && arena.ready() ? "ready" : "incomplete") + "</white>."));
        player.sendMessage(MessageUtil.info("Active match: <white>" + (activeMatch == null ? "none" : activeMatch.firstName + " vs " + activeMatch.secondName) + "</white>."));
        player.sendMessage(MessageUtil.info("Queued players: <white>" + queuedPlayers.size() + "</white>. Pending credits: <white>" + pendingCredits.size() + "</white>."));
        if (arena != null && arena.ready()) player.sendMessage(MessageUtil.info("Extra team spawns: <white>" + arena.configuredExtraSpawns() + "/4</white>; missing spots use safe arena-relative offsets."));
    }

    public void forceStop(Player actor) {
        if (activeMatch == null) {
            actor.sendMessage(MessageUtil.info("There is no active duel."));
            return;
        }
        endMatch(null, EndReason.ADMIN_CANCEL);
        actor.sendMessage(MessageUtil.success("Stopped the duel and refunded all bets."));
    }

    public void spawnLeaderboard(Player player, BoardType type) {
        if (player == null || type == null) return;
        UUID id = UUID.randomUUID();
        Location location = player.getLocation().clone().add(0.0D, 2.2D, 0.0D);
        boards.put(id, new Board(id, location, type));
        saveBoards();
        refreshBoard(id);
        player.sendMessage(MessageUtil.success("Spawned the <white>" + type.display + "</white> duel leaderboard."));
    }

    public void removeNearestLeaderboard(Player player) {
        if (player == null) return;
        Board nearest = boards.values().stream()
            .filter(board -> Objects.equals(board.location.getWorld(), player.getWorld()))
            .filter(board -> board.location.distanceSquared(player.getLocation()) <= 64.0D)
            .min(Comparator.comparingDouble(board -> board.location.distanceSquared(player.getLocation())))
            .orElse(null);
        if (nearest == null) {
            player.sendMessage(MessageUtil.warn("No duel leaderboard is within 8 blocks."));
            return;
        }
        removeBoardDisplay(nearest.id);
        boards.remove(nearest.id);
        saveBoards();
        player.sendMessage(MessageUtil.success("Removed the nearest duel leaderboard."));
    }

    public static DuelMode modeByInput(String input) {
        return DuelMode.byInput(input);
    }

    private void startMatch(List<Player> firstTeam, List<Player> secondTeam, int roundsToWin, DuelMode mode) {
        if (activeMatch != null || firstTeam == null || secondTeam == null || firstTeam.isEmpty() || firstTeam.size() != secondTeam.size()) return;
        for (Player fighter : combined(firstTeam, secondTeam)) {
            leaveQueue(fighter.getUniqueId(), false);
            fighter.closeInventory();
        }
        Map<UUID, PlayerSnapshot> snapshots = new LinkedHashMap<>();
        for (Player fighter : combined(firstTeam, secondTeam)) {
            PlayerSnapshot snapshot = PlayerSnapshot.capture(fighter);
            snapshots.put(fighter.getUniqueId(), snapshot);
            recoveries.put(fighter.getUniqueId(), snapshot);
        }
        saveRecoveries();
        activeMatch = new Match(firstTeam, secondTeam, snapshots, roundsToWin, mode);
        cleanupArena();
        prepareAllFighters(activeMatch);
        activeMatch.phase = MatchPhase.BETTING;
        activeMatch.phaseEndsAt = System.currentTimeMillis() + BETTING_SECONDS * 1000L;
        announceMatch("<gold><bold>" + activeMatch.teamSize + "v" + activeMatch.teamSize + " STARTING</bold></gold>", "<white>" + activeMatch.firstName + "</white> vs <white>" + activeMatch.secondName + "</white>");
        for (UUID fighterId : activeMatch.fighters()) {
            Player fighter = Bukkit.getPlayer(fighterId);
            if (fighter == null) continue;
            fighter.sendMessage(MessageUtil.info("Betting is open for 15 seconds. You may only back your own team."));
            fighter.sendMessage(MessageUtil.info("First to <white>" + roundsToWin + "</white> in <white>" + mode.display + "</white>. Each round lasts 2m 30s."));
            fighter.sendMessage(MessageUtil.info("Every round starts at <white>20 health</white> with normal hunger and saturation."));
        }
    }

    private void prepareAllFighters(Match match) {
        match.eliminated.clear();
        List<UUID> firstTeam = match.firstTeam;
        List<UUID> secondTeam = match.secondTeam;
        for (int index = 0; index < firstTeam.size(); index++) {
            UUID playerId = firstTeam.get(index);
            Player player = Bukkit.getPlayer(playerId);
            PlayerSnapshot snapshot = match.snapshots.get(playerId);
            if (player != null && snapshot != null) prepareFighter(player, snapshot, arena.spawnFor(true, index, match.teamSize));
        }
        for (int index = 0; index < secondTeam.size(); index++) {
            UUID playerId = secondTeam.get(index);
            Player player = Bukkit.getPlayer(playerId);
            PlayerSnapshot snapshot = match.snapshots.get(playerId);
            if (player != null && snapshot != null) prepareFighter(player, snapshot, arena.spawnFor(false, index, match.teamSize));
        }
    }

    private void prepareFighter(Player player, PlayerSnapshot snapshot, Location spawn) {
        player.closeInventory();
        removeDuelHealthNormalization(player);
        snapshot.applyCombatReset(player);
        normalizeDuelHealth(player, true);
        resetDuelHunger(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setGliding(false);
        teleportInternal(player, spawn);
        player.setFallDistance(0.0F);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (now >= nextCreditSweepAt) {
            nextCreditSweepAt = now + 5_000L;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (pendingCredits.containsKey(player.getUniqueId())) schedulePendingCredit(player, 1L, 0);
            }
        }
        cleanupExpiredChallenges();
        if (activeMatch == null) {
            startNextQueuedMatch();
            return;
        }
        Match match = activeMatch;
        for (UUID fighterId : match.fighters()) {
            Player fighter = Bukkit.getPlayer(fighterId);
            if (fighter == null || !fighter.isOnline()) {
                endMatch(match.opponent(fighterId), EndReason.FORFEIT);
                return;
            }
            normalizeDuelHealth(fighter, false);
            if (fighter.isInvulnerable()) fighter.setInvulnerable(false);
        }
        long remainingMillis = match.phaseEndsAt - System.currentTimeMillis();
        if (remainingMillis > 0L) {
            long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
            if (match.phase == MatchPhase.BETTING || match.phase == MatchPhase.ROUND_COUNTDOWN) {
                Component action = MM.deserialize(match.phase == MatchPhase.BETTING
                    ? "<gold>Betting closes in <white>" + seconds + "s</white></gold>"
                    : "<yellow>Round " + match.roundNumber() + " begins in <white>" + seconds + "s</white></yellow>");
                sendActionBarToFighters(match, action);
            } else if (match.phase == MatchPhase.FIGHTING) {
                Component action = MM.deserialize("<red>Round " + match.roundNumber() + "</red> <dark_gray>|</dark_gray> <white>" + formatClock(seconds) + "</white> <dark_gray>|</dark_gray> <yellow>" + match.firstScore + "-" + match.secondScore + "</yellow>");
                sendActionBarToFighters(match, action);
            }
            return;
        }
        switch (match.phase) {
            case BETTING -> beginRoundCountdown();
            case ROUND_COUNTDOWN -> beginFight();
            case FIGHTING -> resolveTimeout();
            case BETWEEN_ROUNDS -> beginRoundCountdown();
            case ENDING -> { }
        }
    }

    private void beginRoundCountdown() {
        if (activeMatch == null) return;
        cleanupArena();
        if (onlinePlayers(activeMatch.fighters()) == null) return;
        prepareAllFighters(activeMatch);
        activeMatch.firstRoundDamage = 0.0D;
        activeMatch.secondRoundDamage = 0.0D;
        activeMatch.phase = MatchPhase.ROUND_COUNTDOWN;
        activeMatch.phaseEndsAt = System.currentTimeMillis() + COUNTDOWN_SECONDS * 1000L;
        for (UUID playerId : activeMatch.fighters()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) player.showTitle(net.kyori.adventure.title.Title.title(
                MM.deserialize("<yellow><bold>ROUND " + activeMatch.roundNumber() + "</bold></yellow>"),
                MM.deserialize("<gray>Eliminate the other team</gray>")
            ));
        }
    }

    private void beginFight() {
        if (activeMatch == null) return;
        activeMatch.phase = MatchPhase.FIGHTING;
        activeMatch.phaseEndsAt = System.currentTimeMillis() + ROUND_SECONDS * 1000L;
        for (UUID playerId : activeMatch.fighters()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            player.showTitle(net.kyori.adventure.title.Title.title(MM.deserialize("<red><bold>FIGHT!</bold></red>"), Component.empty()));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.65F, 1.45F);
            player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0.0D, 1.0D, 0.0D), 24, 0.5D, 0.7D, 0.5D, 0.03D);
        }
    }

    private void resolveTimeout() {
        if (activeMatch == null) return;
        DuelRules.TimeoutResult result = DuelRules.timeoutWinner(
            activeMatch.firstRoundDamage,
            activeMatch.secondRoundDamage,
            activeMatch.teamHealthRatio(activeMatch.first),
            activeMatch.teamHealthRatio(activeMatch.second)
        );
        if (result == DuelRules.TimeoutResult.FIRST) finishRound(activeMatch.first, "timeout decision");
        else if (result == DuelRules.TimeoutResult.SECOND) finishRound(activeMatch.second, "timeout decision");
        else drawRound();
    }

    private void finishRound(UUID winnerId, String reason) {
        if (activeMatch == null || activeMatch.phase != MatchPhase.FIGHTING || winnerId == null) return;
        int finishedRound = activeMatch.roundNumber();
        if (winnerId.equals(activeMatch.first)) activeMatch.firstScore++;
        else if (winnerId.equals(activeMatch.second)) activeMatch.secondScore++;
        else return;
        activeMatch.consecutiveDraws = 0;
        String winnerName = fighterName(activeMatch, winnerId);
        announceToMatch("<green><bold>" + safeName(winnerName) + " wins round " + finishedRound + "!</bold></green> <gray>(" + reason + ")</gray>");
        playMatchSound(Sound.ENTITY_PLAYER_LEVELUP, 0.9F, 1.2F);
        if (activeMatch.scoreFor(winnerId) >= activeMatch.roundsToWin) {
            endMatch(winnerId, EndReason.WIN);
            return;
        }
        activeMatch.phase = MatchPhase.BETWEEN_ROUNDS;
        activeMatch.phaseEndsAt = System.currentTimeMillis() + BETWEEN_ROUNDS_SECONDS * 1000L;
        cleanupArena();
    }

    private void drawRound() {
        if (activeMatch == null) return;
        activeMatch.consecutiveDraws++;
        announceToMatch("<yellow>The round is a draw.</yellow> <gray>No score was awarded.</gray>");
        if (activeMatch.consecutiveDraws >= 2) {
            endMatch(null, EndReason.DRAW);
            return;
        }
        activeMatch.phase = MatchPhase.BETWEEN_ROUNDS;
        activeMatch.phaseEndsAt = System.currentTimeMillis() + BETWEEN_ROUNDS_SECONDS * 1000L;
        cleanupArena();
    }

    private void endMatch(UUID winnerId, EndReason reason) {
        Match match = activeMatch;
        if (match == null) return;
        match.phase = MatchPhase.ENDING;
        cleanupArena();
        settleWagers(match, reason == EndReason.WIN || reason == EndReason.FORFEIT ? winnerId : null);
        if (winnerId != null && (reason == EndReason.WIN || reason == EndReason.FORFEIT)) {
            String winnerName = fighterName(match, winnerId);
            Bukkit.broadcast(MessageUtil.success("<white>" + safeName(winnerName) + "</white> won a Veilward " + match.teamSize + "v" + match.teamSize + " duel"
                + (reason == EndReason.FORFEIT ? " by forfeit." : " <white>" + match.firstScore + "-" + match.secondScore + "</white>.")));
            if (plugin.getLeaderboardManager() != null) {
                for (UUID memberId : match.team(winnerId)) {
                    Player winner = Bukkit.getPlayer(memberId);
                    if (winner != null) plugin.getLeaderboardManager().recordDuelWin(winner);
                }
            }
        } else {
            announceToMatch(reason == EndReason.SHUTDOWN ? "<yellow>The duel was safely cancelled for a server shutdown.</yellow>" : "<yellow>The duel ended without a winner. Bets were refunded.</yellow>");
        }
        for (UUID fighterId : match.fighters()) restoreFighter(fighterId, match.snapshots.get(fighterId));
        for (UUID spectatorId : new ArrayList<>(match.spectators)) leaveSpectator(spectatorId);
        activeMatch = null;
        saveRecoveries();
        savePendingCredits();
        refreshBoards();
        Bukkit.getScheduler().runTaskLater(plugin, this::startNextQueuedMatch, 40L);
    }

    private void restoreFighter(UUID playerId, PlayerSnapshot snapshot) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;
        removeDuelHealthNormalization(player);
        snapshot.apply(player, false, true);
        teleportInternal(player, snapshot.location);
        recoveries.remove(playerId);
        player.sendMessage(MessageUtil.success("Your exact pre-duel state was restored."));
    }

    private void settleWagers(Match match, UUID winnerId) {
        settleItemWagers(match, winnerId);
        if (match.wagers.isEmpty()) {
            clearEscrowFile();
            return;
        }
        Map<UUID, Long> payouts = winnerId == null
            ? DuelRules.refunds(match.wagers)
            : DuelRules.settleParimutuel(match.wagers, winnerId);
        boolean refunded = winnerId == null
            || DuelRules.poolFor(match.wagers, winnerId) <= 0L
            || DuelRules.poolFor(match.wagers, match.opponent(winnerId)) <= 0L;
        for (Map.Entry<UUID, Long> payout : payouts.entrySet()) {
            DuelRules.Wager wager = match.wagers.get(payout.getKey());
            long amount = payout.getValue();
            boolean betWin = !refunded && wager != null && winnerId.equals(wager.side()) && amount > wager.amount();
            queueOrApplyCredit(payout.getKey(), amount, refunded ? "duel bet refund" : "duel bet payout", betWin);
        }
        match.wagers.clear();
        clearEscrowFile();
    }

    private void settleItemWagers(Match match, UUID winnerId) {
        if (match.itemWagers.isEmpty()) return;

        List<ItemWagerGroup> groups = new ArrayList<>();
        List<EscrowedItem> consumed = new ArrayList<>();
        for (Map.Entry<UUID, ItemWager> entry : match.itemWagers.entrySet()) {
            ItemWager wager = entry.getValue();
            consumed.add(wager.escrowed);
            ItemWagerGroup group = groups.stream()
                .filter(candidate -> candidate.prototype.isSimilar(wager.escrowed.item()))
                .findFirst()
                .orElseGet(() -> {
                    ItemStack prototype = wager.escrowed.item().clone();
                    prototype.setAmount(1);
                    ItemWagerGroup created = new ItemWagerGroup(prototype);
                    groups.add(created);
                    return created;
                });
            group.wagers.put(entry.getKey(), wager);
        }

        List<EscrowPayout> payouts = new ArrayList<>();
        Set<UUID> recipients = new LinkedHashSet<>();
        Set<UUID> itemBetWinners = new LinkedHashSet<>();
        for (ItemWagerGroup group : groups) {
            Map<UUID, DuelRules.Wager> counts = new LinkedHashMap<>();
            for (Map.Entry<UUID, ItemWager> entry : group.wagers.entrySet()) {
                counts.put(entry.getKey(), new DuelRules.Wager(entry.getValue().side, entry.getValue().escrowed.item().getAmount()));
            }
            Map<UUID, Long> settled = winnerId == null
                ? DuelRules.refunds(counts)
                : DuelRules.settleParimutuel(counts, winnerId);
            boolean contested = winnerId != null
                && DuelRules.poolFor(counts, winnerId) > 0L
                && DuelRules.poolFor(counts, match.opponent(winnerId)) > 0L;
            for (Map.Entry<UUID, Long> payout : settled.entrySet()) {
                UUID recipient = payout.getKey();
                String name = match.wagerNames.getOrDefault(recipient, playerName(recipient));
                for (ItemStack stack : splitItemAmount(group.prototype, payout.getValue())) {
                    payouts.add(new EscrowPayout(recipient, name, stack));
                }
                recipients.add(recipient);
                DuelRules.Wager original = counts.get(recipient);
                if (contested && original != null && winnerId.equals(original.side()) && payout.getValue() > original.amount()) {
                    itemBetWinners.add(recipient);
                }
            }
        }

        boolean replaced = !payouts.isEmpty()
            && itemWagerEscrow.replaceEscrowsWithRecoveries(consumed, payouts, winnerId == null ? "DUEL_REFUND" : "DUEL_PAYOUT");
        if (!replaced) {
            plugin.getLogger().severe("Could not settle duel item wagers atomically; returning the original wagers instead.");
            recipients.clear();
            for (ItemWager wager : match.itemWagers.values()) {
                itemWagerEscrow.queueRecovery(wager.escrowed.ownerId(), wager.escrowed.ownerName(), wager.escrowed);
                recipients.add(wager.escrowed.ownerId());
            }
            itemBetWinners.clear();
        }

        for (UUID recipientId : recipients) {
            Player recipient = Bukkit.getPlayer(recipientId);
            if (recipient == null || !recipient.isOnline()) continue;
            itemWagerEscrow.restorePendingRecovery(recipient);
            if (itemWagerEscrow.hasPendingRecovery(recipient)) {
                recipient.sendMessage(MessageUtil.warn("Your duel item payout is safely pending. Make one empty inventory slot to receive it."));
            } else {
                recipient.sendMessage(MessageUtil.success("Your duel item wager payout was delivered."));
            }
        }
        if (plugin.getLeaderboardManager() != null) {
            for (UUID itemWinnerId : itemBetWinners) {
                plugin.getLeaderboardManager().recordDuelBetWins(
                    itemWinnerId,
                    match.wagerNames.getOrDefault(itemWinnerId, playerName(itemWinnerId)),
                    1
                );
            }
        }
        match.itemWagers.clear();
    }

    private void queueOrApplyCredit(UUID playerId, long amount, String reason, boolean betWin) {
        if (playerId == null || amount <= 0L) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && plugin.getEssenceManager().isLoaded(player)
            && plugin.getEssenceManager().canCreditFully(player, amount)
            && plugin.getEssenceManager().credit(player, amount, reason)) {
            player.sendMessage(MessageUtil.success("Received <white>" + amount + " Essence</white> from duel betting."));
            if (betWin && plugin.getLeaderboardManager() != null) plugin.getLeaderboardManager().recordDuelBetWin(player);
            return;
        }
        pendingCredits.merge(playerId, new PendingCredit(amount, reason, betWin ? 1 : 0), PendingCredit::merge);
        savePendingCredits();
        if (player != null) player.sendMessage(MessageUtil.warn("Your " + amount + " Essence duel payout is safely pending until your balance has room."));
    }

    private void placeBet(Player player, long amount) {
        Match match = activeMatch;
        if (player == null || match == null || match.phase != MatchPhase.BETTING) {
            if (player != null) player.sendMessage(MessageUtil.warn("Betting is closed."));
            return;
        }
        if (amount <= 0L || amount > MAX_ESSENCE_BET || match.wagers.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.error(match.wagers.containsKey(player.getUniqueId()) ? "You already have a locked bet." : "Invalid bet amount."));
            return;
        }
        UUID side = selectedBetSides.get(player.getUniqueId());
        if (side == null || !match.isSide(side)) {
            player.sendMessage(MessageUtil.warn("Choose a team first."));
            return;
        }
        if (match.isFighter(player.getUniqueId()) && !side.equals(match.sideOf(player.getUniqueId()))) {
            player.sendMessage(MessageUtil.warn("Fighters may only bet on their own team."));
            return;
        }
        if (!plugin.getEssenceManager().isLoaded(player)) {
            player.sendMessage(MessageUtil.warn("Your Essence account is still loading."));
            return;
        }
        if (!plugin.getEssenceManager().spend(player, amount, "duel bet escrow")) {
            player.sendMessage(MessageUtil.error("You do not have enough Essence."));
            return;
        }
        match.wagers.put(player.getUniqueId(), new DuelRules.Wager(side, amount));
        match.wagerNames.put(player.getUniqueId(), player.getName());
        saveActiveEscrow(match);
        player.sendMessage(MessageUtil.success("Locked <white>" + amount + " Essence</white> on <white>" + fighterName(match, side) + "</white>."));
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.8F, 1.2F);
        openBetMenu(player);
    }

    private void placeItemBet(Player player, int amount) {
        Match match = activeMatch;
        if (player == null || match == null || match.phase != MatchPhase.BETTING) {
            if (player != null) player.sendMessage(MessageUtil.warn("Betting is closed."));
            return;
        }
        if (match.itemWagers.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("You already have a locked item wager."));
            return;
        }
        if (match.isFighter(player.getUniqueId()) || match.spectators.contains(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Fighters and arena spectators can bet any Essence amount, but item wagers stay outside the restorable arena inventory."));
            return;
        }
        UUID side = selectedBetSides.get(player.getUniqueId());
        if (side == null || !match.isSide(side)) {
            player.sendMessage(MessageUtil.warn("Choose a team in the betting menu first."));
            return;
        }
        if (match.isFighter(player.getUniqueId()) && !side.equals(match.sideOf(player.getUniqueId()))) {
            player.sendMessage(MessageUtil.warn("Fighters may only bet on their own team."));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (isUnsafeItemWager(held)) {
            player.sendMessage(MessageUtil.warn("Hold a normal item or material. Backpacks, custom relics, bundles, and filled containers cannot be wagered."));
            return;
        }
        if (amount <= 0 || amount > held.getAmount()) {
            player.sendMessage(MessageUtil.warn("Choose between 1 and " + held.getAmount() + " items from your held stack."));
            return;
        }

        EscrowedItem escrowed = itemWagerEscrow.capturePartial(
            match.matchId,
            player,
            player.getInventory().getHeldItemSlot(),
            held.clone(),
            amount
        );
        if (escrowed == null) {
            player.sendMessage(MessageUtil.error("The item wager could not be secured. Nothing was taken."));
            return;
        }
        match.itemWagers.put(player.getUniqueId(), new ItemWager(side, escrowed));
        match.wagerNames.put(player.getUniqueId(), player.getName());
        player.sendMessage(MessageUtil.success("Locked <white>" + amount + "x " + readableItemName(escrowed.item()) + "</white> on <white>" + fighterName(match, side) + "</white>."));
        player.playSound(player.getLocation(), Sound.BLOCK_VAULT_INSERT_ITEM, 0.8F, 1.15F);
        openBetMenu(player);
    }

    private void startNextQueuedMatch() {
        if (activeMatch != null || arena == null || !arena.ready()) return;
        for (Map.Entry<QueueKey, ArrayDeque<QueueEntry>> queueEntry : queues.entrySet()) {
            QueueEntry firstEntry = pollEligibleTeam(queueEntry.getValue(), Set.of());
            QueueEntry secondEntry = pollEligibleTeam(queueEntry.getValue(), firstEntry == null ? Set.of() : firstEntry.members);
            if (firstEntry == null) continue;
            if (secondEntry == null) {
                queueEntry.getValue().addFirst(firstEntry);
                continue;
            }
            List<Player> firstTeam = onlinePlayers(firstEntry.members);
            List<Player> secondTeam = onlinePlayers(secondEntry.members);
            removeQueuedEntry(firstEntry, false);
            removeQueuedEntry(secondEntry, false);
            if (firstTeam != null && secondTeam != null && canEnterRoster(firstTeam, firstTeam.getFirst()) && canEnterRoster(secondTeam, secondTeam.getFirst())) {
                startMatch(firstTeam, secondTeam, queueEntry.getKey().roundsToWin, queueEntry.getKey().mode);
                return;
            }
        }
    }

    private QueueEntry pollEligibleTeam(ArrayDeque<QueueEntry> queue, Collection<UUID> excluded) {
        while (queue != null && !queue.isEmpty()) {
            QueueEntry candidate = queue.removeFirst();
            if (candidate.members.stream().anyMatch(excluded::contains)) continue;
            if (candidate.members.stream().allMatch(id -> queuedPlayers.get(id) == candidate && Bukkit.getPlayer(id) != null && !isDuelParticipant(id))) return candidate;
            removeQueuedEntry(candidate, false);
        }
        return null;
    }

    private boolean leaveQueue(UUID playerId, boolean notify) {
        QueueEntry entry = queuedPlayers.get(playerId);
        if (entry == null) return false;
        removeQueuedEntry(entry, notify);
        return true;
    }

    private void removeQueuedEntry(QueueEntry entry, boolean notify) {
        if (entry == null) return;
        ArrayDeque<QueueEntry> queue = queues.get(entry.key);
        if (queue != null) queue.remove(entry);
        for (UUID memberId : entry.members) {
            if (queuedPlayers.get(memberId) == entry) queuedPlayers.remove(memberId);
        }
        if (notify) sendToRoster(entry.members, MessageUtil.info("Your party left the duel queue."));
    }

    private boolean canEnter(Player player) {
        if (player == null || !player.isOnline()) return false;
        if (activeMatch != null && (activeMatch.isFighter(player.getUniqueId()) || activeMatch.spectators.contains(player.getUniqueId()))) {
            player.sendMessage(MessageUtil.warn("You are already involved in the current duel."));
            return false;
        }
        if (plugin.getBossManager() != null && plugin.getBossManager().isActiveBossFight(player)) {
            player.sendMessage(MessageUtil.warn("Finish your boss fight before entering a duel."));
            return false;
        }
        if (plugin.getCombatLogListener() != null && plugin.getCombatLogListener().isInPlayerCombat(player)) {
            player.sendMessage(MessageUtil.warn("Wait for your normal PvP combat tag to expire before entering a duel."));
            return false;
        }
        if (player.isDead()) {
            player.sendMessage(MessageUtil.warn("Respawn before entering a duel."));
            return false;
        }
        return true;
    }

    private boolean canEnterSilently(Player player) {
        return player != null
            && player.isOnline()
            && (activeMatch == null || (!activeMatch.isFighter(player.getUniqueId()) && !activeMatch.spectators.contains(player.getUniqueId())))
            && (plugin.getBossManager() == null || !plugin.getBossManager().isActiveBossFight(player))
            && (plugin.getCombatLogListener() == null || !plugin.getCombatLogListener().isInPlayerCombat(player))
            && !player.isDead();
    }

    private boolean canEnterRoster(List<Player> roster, Player requester) {
        if (roster == null || roster.isEmpty() || roster.size() > 3 || roster.stream().map(Player::getUniqueId).distinct().count() != roster.size()) return false;
        for (Player member : roster) {
            if (canEnter(member)) continue;
            if (requester != null && !requester.equals(member)) requester.sendMessage(MessageUtil.warn(member.getName() + " cannot enter the duel right now."));
            return false;
        }
        return true;
    }

    private boolean canChangeParty(Player player) {
        if (player == null || !player.isOnline()) return false;
        if (queuedPlayers.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("Leave the duel queue before changing your party."));
            return false;
        }
        if (activeMatch != null && (activeMatch.isFighter(player.getUniqueId()) || activeMatch.spectators.contains(player.getUniqueId()))) {
            player.sendMessage(MessageUtil.warn("Finish or leave the active duel before changing your party."));
            return false;
        }
        return true;
    }

    private boolean isPartyCaptain(Player player) {
        if (player == null) return false;
        DuelParty party = partiesByMember.get(player.getUniqueId());
        return party == null || party.captain.equals(player.getUniqueId());
    }

    private List<UUID> rosterFor(Player player) {
        if (player == null) return List.of();
        DuelParty party = partiesByMember.get(player.getUniqueId());
        return party == null ? List.of(player.getUniqueId()) : List.copyOf(party.members);
    }

    private List<Player> onlineRoster(Player captain, int requestedSize, boolean requireCaptain) {
        if (captain == null) return null;
        if (requireCaptain && !isPartyCaptain(captain)) {
            captain.sendMessage(MessageUtil.warn("Only your duel party captain can queue the party."));
            return null;
        }
        List<UUID> roster = rosterFor(captain);
        if (roster.size() != requestedSize) {
            captain.sendMessage(MessageUtil.warn("Your party has <white>" + roster.size() + "</white> fighter(s). A " + requestedSize + "v" + requestedSize + " queue needs exactly " + requestedSize + "."));
            return null;
        }
        List<Player> online = onlinePlayers(roster);
        if (online == null) captain.sendMessage(MessageUtil.warn("Every duel party member must be online to queue."));
        return online;
    }

    private static List<Player> onlinePlayers(Collection<UUID> playerIds) {
        if (playerIds == null) return null;
        List<Player> players = new ArrayList<>();
        for (UUID playerId : playerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) return null;
            players.add(player);
        }
        return players;
    }

    private static List<Player> combined(List<Player> first, List<Player> second) {
        List<Player> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return combined;
    }

    private static void sendToRoster(Collection<UUID> roster, Component message) {
        if (roster == null || message == null) return;
        for (UUID memberId : roster) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) member.sendMessage(message);
        }
    }

    private static void sendActionBarToFighters(Match match, Component message) {
        if (match == null || message == null) return;
        for (UUID fighterId : match.fighters()) {
            Player fighter = Bukkit.getPlayer(fighterId);
            if (fighter != null) fighter.sendActionBar(message);
        }
    }

    private void updatePartyPreferredSize(DuelParty party) {
        if (party == null) return;
        for (UUID memberId : party.members) preferredTeamSizes.put(memberId, party.members.size());
    }

    private static String rosterNames(Collection<UUID> roster) {
        return roster == null ? "none" : roster.stream().map(DuelManager::playerName).map(DuelManager::safeName).reduce((left, right) -> left + ", " + right).orElse("none");
    }

    private static String playerName(UUID playerId) {
        if (playerId == null) return "Unknown";
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) return online.getName();
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null || name.isBlank() ? playerId.toString().substring(0, 8) : name;
    }

    private boolean arenaReady(Player player) {
        if (arena != null && arena.ready()) return true;
        if (player != null) player.sendMessage(MessageUtil.warn("The duel arena is not configured yet."));
        return false;
    }

    private boolean isFighting(Player player) {
        return player != null && activeMatch != null && activeMatch.phase == MatchPhase.FIGHTING
            && activeMatch.isFighter(player.getUniqueId()) && !activeMatch.eliminated.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Match match = activeMatch;
        if (match == null) return;
        Player attacker = event instanceof EntityDamageByEntityEvent byEntity ? attackingPlayer(byEntity.getDamager()) : null;
        if (!(event.getEntity() instanceof Player victim) || !match.isFighter(victim.getUniqueId())) {
            if (attacker != null && match.isFighter(attacker.getUniqueId()) && !(event.getEntity() instanceof EnderCrystal)) event.setCancelled(true);
            return;
        }
        if (match.phase != MatchPhase.FIGHTING) {
            event.setCancelled(true);
            return;
        }
        if (match.eliminated.contains(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        normalizeDuelHealth(victim, false);
        if (attacker != null && (!match.isFighter(attacker.getUniqueId()) || attacker.equals(victim)
            || Objects.equals(match.sideOf(attacker.getUniqueId()), match.sideOf(victim.getUniqueId())))) {
            event.setCancelled(true);
            return;
        }
        if (match.mode == DuelMode.MELEE && (attacker == null || isRangedDamage(event))) {
            event.setCancelled(true);
            return;
        }
        double damage = Math.max(0.0D, event.getFinalDamage());
        UUID creditedSide = attacker == null ? null : match.sideOf(attacker.getUniqueId());
        if (creditedSide != null) {
            if (creditedSide.equals(match.first)) match.firstRoundDamage += damage;
            else if (creditedSide.equals(match.second)) match.secondRoundDamage += damage;
        }
        if (damage + 0.0001D < victim.getHealth()) return;
        event.setCancelled(true);
        victim.setHealth(Math.min(1.0D, maxHealth(victim)));
        victim.setFireTicks(0);
        eliminateFighter(victim, "knockout");
    }

    private void eliminateFighter(Player victim, String reason) {
        Match match = activeMatch;
        if (victim == null || match == null || match.phase != MatchPhase.FIGHTING || !match.isFighter(victim.getUniqueId())
            || !match.eliminated.add(victim.getUniqueId())) return;
        UUID losingSide = match.sideOf(victim.getUniqueId());
        UUID winningSide = match.opponent(victim.getUniqueId());
        victim.setHealth(Math.min(1.0D, maxHealth(victim)));
        victim.setFireTicks(0);
        victim.setGameMode(GameMode.SPECTATOR);
        teleportInternal(victim, arena.spectatorView());
        victim.showTitle(net.kyori.adventure.title.Title.title(
            MM.deserialize("<red><bold>KNOCKED OUT</bold></red>"),
            MM.deserialize("<gray>Watch your teammates finish the round.</gray>")
        ));
        announceToMatch("<gray><white>" + safeName(victim.getName()) + "</white> was knocked out.</gray>");
        if (losingSide != null && match.team(losingSide).stream().allMatch(match.eliminated::contains)) {
            finishRound(winningSide, reason);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (activeMatch == null || !(event.getEntity() instanceof Player player) || !activeMatch.isFighter(player.getUniqueId())) {
            return;
        }
        normalizeDuelHealth(player, false);
        if (activeMatch.mode == DuelMode.NO_HEAL && activeMatch.phase == MatchPhase.FIGHTING) {
            event.setCancelled(true);
        }
    }

    private void normalizeDuelHealth(Player player, boolean refill) {
        if (player == null) {
            return;
        }
        AttributeInstance maximumHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maximumHealth == null) {
            return;
        }

        double preservedHealth = Math.max(0.5D, Math.min(player.getHealth(), DuelRules.NORMALIZED_MAX_HEALTH));
        maximumHealth.removeModifier(normalizedHealthKey);
        double modifier = DuelRules.healthNormalizationModifier(maximumHealth.getValue());
        if (Math.abs(modifier) > 0.000001D) {
            maximumHealth.addTransientModifier(new AttributeModifier(
                normalizedHealthKey,
                modifier,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1
            ));
        }

        double normalizedMaximum = Math.max(1.0D, maximumHealth.getValue());
        player.setHealth(refill ? normalizedMaximum : Math.min(preservedHealth, normalizedMaximum));
        if (refill) {
            player.setAbsorptionAmount(0.0D);
        }
    }

    private void removeDuelHealthNormalization(Player player) {
        if (player == null) {
            return;
        }
        AttributeInstance maximumHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maximumHealth != null) {
            maximumHealth.removeModifier(normalizedHealthKey);
        }
    }

    private static void resetDuelHunger(Player player) {
        if (player == null) {
            return;
        }
        player.setFoodLevel(DuelRules.ROUND_START_FOOD_LEVEL);
        player.setSaturation(DuelRules.ROUND_START_SATURATION);
        player.setExhaustion(DuelRules.ROUND_START_EXHAUSTION);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player && isDuelParticipant(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player && (isDuelParticipant(player) || spectators().contains(player.getUniqueId()))) {
            event.setCancelled(true);
            player.sendActionBar(MM.deserialize("<red>Elytras are disabled in the duel arena.</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (activeMatch == null || event.getTo() == null) return;
        UUID movingId = event.getPlayer().getUniqueId();
        if (isDuelViewer(movingId)) {
            if (!insideSpectatorZone(event.getTo(), arena.spectator)) {
                event.setCancelled(true);
                event.getPlayer().setSpectatorTarget(null);
                teleportInternal(event.getPlayer(), arena.spectatorView());
                event.getPlayer().sendActionBar(MM.deserialize("<red>Stay in the duel viewing area. Use /duel leave to exit.</red>"));
            }
            return;
        }
        if (!activeMatch.isFighter(movingId)) return;
        if (activeMatch.phase != MatchPhase.FIGHTING && movedPosition(event.getFrom(), event.getTo())) {
            Location locked = event.getFrom().clone();
            locked.setYaw(event.getTo().getYaw());
            locked.setPitch(event.getTo().getPitch());
            event.setTo(locked);
            return;
        }
        if (activeMatch.phase == MatchPhase.FIGHTING && !arena.containsExpanded(event.getTo(), 0.35D)) {
            event.setCancelled(true);
            UUID playerId = event.getPlayer().getUniqueId();
            UUID side = activeMatch.sideOf(playerId);
            Location fallback = activeMatch.eliminated.contains(playerId)
                ? arena.spectatorView()
                : arena.spawnFor(activeMatch.first.equals(side), activeMatch.indexOnSide(playerId), activeMatch.teamSize);
            teleportInternal(event.getPlayer(), fallback);
            event.getPlayer().sendActionBar(MM.deserialize("<red>You cannot leave the duel arena.</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (activeMatch == null || internalTeleports.contains(event.getPlayer().getUniqueId())) return;
        UUID playerId = event.getPlayer().getUniqueId();
        if (!activeMatch.isFighter(playerId) && !activeMatch.spectators.contains(playerId)) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(MM.deserialize("<red>Teleporting is disabled during a duel.</red>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStartSpectatingEntity(PlayerStartSpectatingEntityEvent event) {
        if (!isDuelViewer(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(MM.deserialize("<red>Free-camera spectating is limited to the duel viewing area.</red>"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!allowsArenaBlockPlacement(event.getPlayer(), event.getBlockPlaced())) return;
        temporaryBlocks.putIfAbsent(BlockKey.of(event.getBlockPlaced()), event.getBlockReplacedState().getBlockData().clone());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardArenaBlockPlace(BlockPlaceEvent event) {
        if (activeMatch == null || arena == null || !arena.contains(event.getBlockPlaced().getLocation())) return;
        if (!allowsArenaBlockPlacement(event.getPlayer(), event.getBlockPlaced())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(MM.deserialize("<red>That block cannot be placed in the duel arena.</red>"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!allowsArenaBlockBreak(event.getPlayer(), event.getBlock())) return;
        event.setDropItems(false);
        event.setExpToDrop(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardArenaBlockBreak(BlockBreakEvent event) {
        if (activeMatch == null || arena == null || !arena.contains(event.getBlock().getLocation())) return;
        if (!allowsArenaBlockBreak(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(MM.deserialize("<red>Only temporary duel blocks can be broken.</red>"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlock();
        if (!allowsArenaBucket(event.getPlayer(), target)) return;
        temporaryBlocks.putIfAbsent(BlockKey.of(target), target.getBlockData().clone());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlock();
        Block clicked = event.getBlockClicked();
        if (activeMatch != null && arena != null
            && (arena.contains(clicked.getLocation()) || arena.contains(target.getLocation()))
            && !allowsArenaBucket(event.getPlayer(), target)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!allowsArenaBucket(event.getPlayer(), event.getBlock())) return;
        Block block = event.getBlock();
        temporaryBlocks.putIfAbsent(BlockKey.of(block), block.getBlockData().clone());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardBucketFill(PlayerBucketFillEvent event) {
        if (activeMatch != null && arena != null && arena.contains(event.getBlock().getLocation())
            && !allowsArenaBucket(event.getPlayer(), event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (allowsArenaFluid(event.getBlock(), event.getToBlock())) {
            temporaryBlocks.putIfAbsent(BlockKey.of(event.getToBlock()), event.getToBlock().getBlockData().clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardFluidFlow(BlockFromToEvent event) {
        if (activeMatch == null || arena == null) return;
        boolean touchesArena = arena.contains(event.getBlock().getLocation()) || arena.contains(event.getToBlock().getLocation());
        if (touchesArena && !allowsArenaFluid(event.getBlock(), event.getToBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (activeMatch == null || arena == null || !arena.contains(event.getBlock().getLocation())) return;
        Player player = event.getPlayer();
        if (player == null || !isFighting(player)) return;
        temporaryBlocks.putIfAbsent(BlockKey.of(event.getBlock()), event.getBlock().getBlockData().clone());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (activeMatch == null || arena == null) return;
        boolean sourceInside = arena.contains(event.getSource().getLocation());
        boolean destinationInside = arena.contains(event.getBlock().getLocation());
        if (!sourceInside && !destinationInside) return;
        if (!destinationInside) {
            event.setCancelled(true);
            return;
        }
        temporaryBlocks.putIfAbsent(BlockKey.of(event.getBlock()), event.getBlock().getBlockData().clone());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (activeMatch != null && arena != null && arena.contains(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        if (activeMatch != null && arena != null && (arena.contains(event.getBlock().getLocation())
            || event.getBlocks().stream().anyMatch(state -> arena.contains(state.getLocation())))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (activeMatch == null || arena == null || !arena.contains(event.getBlock().getLocation())) return;
        temporaryBlocks.putIfAbsent(BlockKey.of(event.getBlock()), event.getBlock().getBlockData().clone());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (activeMatch == null || arena == null) return;
        if (event.getBlocks().stream().anyMatch(state -> arena.contains(state.getLocation()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (activeMatch != null && arena != null && (arena.contains(event.getBlock().getLocation())
            || event.getBlocks().stream().anyMatch(block -> arena.contains(block.getLocation())))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (activeMatch != null && arena != null && (arena.contains(event.getBlock().getLocation())
            || event.getBlocks().stream().anyMatch(block -> arena.contains(block.getLocation())))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (isArenaExplosion(event.getLocation())) {
            filterArenaExplosion(event.blockList());
            event.setYield(0.0F);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (isArenaExplosion(event.getBlock().getLocation())) {
            filterArenaExplosion(event.blockList());
            event.setYield(0.0F);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (allowsArenaEntitySpawn(event.getEntity())) spawnedEntities.add(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (allowsArenaEntityPlacement(event.getPlayer(), event.getEntity())) {
            spawnedEntities.add(event.getEntity().getUniqueId());
            spawnedEntityOwners.put(event.getEntity().getUniqueId(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardEntityPlace(EntityPlaceEvent event) {
        if (activeMatch == null || arena == null || !arena.contains(event.getEntity().getLocation())) return;
        if (!allowsArenaEntityPlacement(event.getPlayer(), event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardArenaInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || activeMatch == null || arena == null || !arena.contains(block.getLocation())) return;
        if (!activeMatch.isFighter(event.getPlayer().getUniqueId()) || activeMatch.phase != MatchPhase.FIGHTING) {
            event.setCancelled(true);
            return;
        }
        if (allowsArenaInteraction(event)) return;
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(event.getItem() == null || event.getItem().isEmpty()
            ? org.bukkit.event.Event.Result.DENY
            : org.bukkit.event.Event.Result.ALLOW);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardArenaEntityInteract(PlayerInteractEntityEvent event) {
        if (activeMatch != null && (activeMatch.isFighter(event.getPlayer().getUniqueId())
            || activeMatch.spectators.contains(event.getPlayer().getUniqueId()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardExternalInventories(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
            || (!isDuelParticipant(player) && !spectators().contains(player.getUniqueId()))) return;
        if (event.getInventory().getHolder(false) instanceof DuelMenuHolder) return;
        InventoryType type = event.getInventory().getType();
        if (type != InventoryType.CRAFTING && type != InventoryType.PLAYER) {
            event.setCancelled(true);
            player.sendActionBar(MM.deserialize("<red>External storage is disabled during a duel.</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardCommands(PlayerCommandPreprocessEvent event) {
        if (!isDuelParticipant(event.getPlayer()) && !spectators().contains(event.getPlayer().getUniqueId())) return;
        String root = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (Set.of("duel", "duels", "msg", "tell", "w", "r", "reply").contains(root)) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(MM.deserialize("<red>That command is disabled during a duel.</red>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardNoHealConsumables(PlayerItemConsumeEvent event) {
        if (activeMatch == null || activeMatch.phase != MatchPhase.FIGHTING || activeMatch.mode != DuelMode.NO_HEAL
            || !activeMatch.isFighter(event.getPlayer().getUniqueId())) return;
        Material material = event.getItem().getType();
        if (material == Material.GOLDEN_APPLE || material == Material.ENCHANTED_GOLDEN_APPLE) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(MM.deserialize("<red>Golden apples are disabled in No Healing duels.</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isDuelParticipant(event.getPlayer()) && !spectators().contains(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(MM.deserialize("<red>Items cannot leave your inventory during a duel.</red>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && (isDuelParticipant(player) || spectators().contains(player.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!isDuelParticipant(event.getPlayer())) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        Match match = activeMatch;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (activeMatch == match) eliminateFighter(event.getPlayer(), "knockout");
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        leaveQueue(player.getUniqueId(), false);
        challenges.remove(player.getUniqueId());
        challenges.entrySet().removeIf(entry -> entry.getValue().challenger.equals(player.getUniqueId()));
        partyInvites.remove(player.getUniqueId());
        partyInvites.entrySet().removeIf(entry -> entry.getValue().captain.equals(player.getUniqueId()));
        if (activeMatch != null && activeMatch.isFighter(player.getUniqueId())) {
            UUID winner = activeMatch.opponent(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> endMatch(winner, EndReason.FORFEIT));
        } else if (spectators().contains(player.getUniqueId())) {
            leaveSpectator(player.getUniqueId());
        }
        removeQuittingPartyMember(player.getUniqueId(), player.getName());
        preferredModes.remove(player.getUniqueId());
        preferredTeamSizes.remove(player.getUniqueId());
        preferredRounds.remove(player.getUniqueId());
        selectedBetSides.remove(player.getUniqueId());
        selectedBetAmounts.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleRecovery(event.getPlayer(), 20L);
        schedulePendingCredit(event.getPlayer(), 40L, 0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            itemWagerEscrow.restorePendingRecovery(event.getPlayer());
            itemWagerEscrow.sanitizeOrphanedEscrowMarkers(event.getPlayer());
        }, 60L);
    }

    private void removeQuittingPartyMember(UUID playerId, String name) {
        DuelParty party = partiesByMember.remove(playerId);
        preferredTeamSizes.put(playerId, 1);
        if (party == null) return;
        party.members.remove(playerId);
        if (party.members.isEmpty()) return;
        if (party.captain.equals(playerId)) party.captain = party.members.iterator().next();
        if (party.members.size() == 1) {
            UUID remaining = party.members.iterator().next();
            partiesByMember.remove(remaining);
            preferredTeamSizes.put(remaining, 1);
            Player remainingPlayer = Bukkit.getPlayer(remaining);
            if (remainingPlayer != null) remainingPlayer.sendMessage(MessageUtil.info("The duel party closed because " + safeName(name) + " left the server."));
            return;
        }
        updatePartyPreferredSize(party);
        sendToRoster(party.members, MessageUtil.info("<white>" + safeName(name) + "</white> left the server and was removed from the duel party."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof DuelMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        ItemStack clicked = event.getCurrentItem();
        String action = menuAction(clicked);
        if (action == null || action.equals("none")) return;
        handleMenuAction(player, holder.view, action);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof DuelMenuHolder) event.setCancelled(true);
    }

    private void handleMenuAction(Player player, MenuView view, String action) {
        if (action.equals("back")) {
            openMainMenu(player);
            return;
        }
        if (action.equals("leave")) {
            player.closeInventory();
            leave(player);
            return;
        }
        if (action.equals("spectate")) {
            player.closeInventory();
            spectate(player);
            return;
        }
        if (action.equals("bet")) {
            openBetMenu(player);
            return;
        }
        if (action.equals("find")) {
            player.closeInventory();
            joinSelectedQueue(player);
            return;
        }
        if (action.equals("challenge")) {
            openChallengeMenu(player);
            return;
        }
        if (action.startsWith("challenge:")) {
            try {
                Player target = Bukkit.getPlayer(UUID.fromString(action.substring(10)));
                player.closeInventory();
                if (target == null) player.sendMessage(MessageUtil.warn("That player is no longer online."));
                else challengeSelected(player, target);
            } catch (IllegalArgumentException ignored) { }
            return;
        }
        if (action.startsWith("challengeaccept:")) {
            try {
                Player challenger = Bukkit.getPlayer(UUID.fromString(action.substring(16)));
                player.closeInventory();
                if (challenger == null) player.sendMessage(MessageUtil.warn("That challenger is no longer online."));
                else acceptChallenge(player, challenger);
            } catch (IllegalArgumentException ignored) { }
            return;
        }
        if (action.equals("challengedeny")) {
            denyChallenge(player);
            openMainMenu(player);
            return;
        }
        if (action.equals("party")) {
            openPartyMenu(player);
            return;
        }
        if (action.equals("partyleave")) {
            player.closeInventory();
            leaveParty(player);
            return;
        }
        if (action.equals("partydisband")) {
            player.closeInventory();
            disbandParty(player);
            return;
        }
        if (action.startsWith("partyinvite:")) {
            try {
                Player target = Bukkit.getPlayer(UUID.fromString(action.substring(12)));
                if (target != null) inviteToParty(player, target);
            } catch (IllegalArgumentException ignored) { }
            openPartyMenu(player);
            return;
        }
        if (action.startsWith("partykick:")) {
            try {
                Player target = Bukkit.getPlayer(UUID.fromString(action.substring(10)));
                if (target != null) kickFromParty(player, target);
            } catch (IllegalArgumentException ignored) { }
            openPartyMenu(player);
            return;
        }
        if (action.startsWith("partyaccept:")) {
            try {
                Player captain = Bukkit.getPlayer(UUID.fromString(action.substring(12)));
                if (captain != null) acceptPartyInvite(player, captain);
            } catch (IllegalArgumentException ignored) { }
            openPartyMenu(player);
            return;
        }
        if (action.equals("partydeny")) {
            declinePartyInvite(player);
            openPartyMenu(player);
            return;
        }
        if (action.startsWith("teamsize:")) {
            try {
                int teamSize = Integer.parseInt(action.substring(9));
                if (DuelRules.normalizeTeamSize(teamSize) > 0) preferredTeamSizes.put(player.getUniqueId(), teamSize);
            } catch (NumberFormatException ignored) { }
            openMainMenu(player);
            return;
        }
        if (action.startsWith("rounds:")) {
            try {
                int rounds = Integer.parseInt(action.substring(7));
                if (DuelRules.normalizeRoundsToWin(rounds) > 0) preferredRounds.put(player.getUniqueId(), rounds);
            } catch (NumberFormatException ignored) { }
            openMainMenu(player);
            return;
        }
        if (action.startsWith("mode:")) {
            DuelMode mode = DuelMode.byInput(action.substring(5));
            if (mode != null) preferredModes.put(player.getUniqueId(), mode);
            openMainMenu(player);
            return;
        }
        if (action.startsWith("queue:")) {
            int rounds;
            try { rounds = Integer.parseInt(action.substring(6)); }
            catch (NumberFormatException ignored) { return; }
            joinQueue(player, rounds, preferredModes.getOrDefault(player.getUniqueId(), DuelMode.OPEN),
                preferredTeamSizes.getOrDefault(player.getUniqueId(), rosterFor(player).size()));
            return;
        }
        if (view == MenuView.BET && action.startsWith("side:")) {
            try { selectedBetSides.put(player.getUniqueId(), UUID.fromString(action.substring(5))); }
            catch (IllegalArgumentException ignored) { return; }
            openBetMenu(player);
            return;
        }
        if (view == MenuView.BET && action.startsWith("adjuststake:")) {
            try {
                long delta = Long.parseLong(action.substring(12));
                long current = selectedBetAmounts.getOrDefault(player.getUniqueId(), 1L);
                selectedBetAmounts.put(player.getUniqueId(), clampBetAmount(current, delta));
            } catch (NumberFormatException ignored) { }
            openBetMenu(player);
            return;
        }
        if (view == MenuView.BET && action.equals("stakemax")) {
            selectedBetAmounts.put(player.getUniqueId(), Math.max(1L, Math.min(MAX_ESSENCE_BET, plugin.getEssenceManager().balance(player))));
            openBetMenu(player);
            return;
        }
        if (view == MenuView.BET && action.equals("stakeessence")) {
            placeBet(player, selectedBetAmounts.getOrDefault(player.getUniqueId(), 1L));
            return;
        }
        if (view == MenuView.BET && action.equals("stakeitem")) {
            ItemStack held = player.getInventory().getItemInMainHand();
            placeItemBet(player, held == null ? 0 : held.getAmount());
        }
    }

    private void leaveSpectator(UUID playerId) {
        if (activeMatch != null) activeMatch.spectators.remove(playerId);
        PlayerSnapshot snapshot = recoveries.get(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (snapshot != null && player != null && player.isOnline()) {
            snapshot.apply(player, true, true);
            recoveries.remove(playerId);
            saveRecoveries();
        }
    }

    private Set<UUID> spectators() {
        return activeMatch == null ? Set.of() : activeMatch.spectators;
    }

    private boolean isDuelViewer(UUID playerId) {
        return activeMatch != null && playerId != null
            && (activeMatch.spectators.contains(playerId) || activeMatch.eliminated.contains(playerId));
    }

    private static boolean insideSpectatorZone(Location candidate, Location anchor) {
        if (candidate == null || anchor == null || !Objects.equals(candidate.getWorld(), anchor.getWorld())) return false;
        return spectatorOffsetAllowed(
            candidate.getX() - anchor.getX(),
            candidate.getY() - anchor.getY(),
            candidate.getZ() - anchor.getZ()
        );
    }

    static boolean spectatorOffsetAllowed(double xOffset, double yOffset, double zOffset) {
        if (!Double.isFinite(xOffset) || !Double.isFinite(yOffset) || !Double.isFinite(zOffset)) return false;
        return xOffset * xOffset + zOffset * zOffset <= SPECTATOR_RADIUS * SPECTATOR_RADIUS
            && yOffset >= SPECTATOR_MIN_Y_OFFSET
            && yOffset <= SPECTATOR_MAX_Y_OFFSET;
    }

    private void cleanupArena() {
        Map<BlockKey, BlockData> restore = new LinkedHashMap<>(temporaryBlocks);
        boolean hadFluidChanges = restore.keySet().stream()
            .map(BlockKey::block)
            .filter(Objects::nonNull)
            .map(Block::getType)
            .anyMatch(type -> type == Material.WATER || type == Material.LAVA);
        restoreArenaBlocks(restore);
        if (hadFluidChanges) cleanupLooseArenaFluids();
        temporaryBlocks.clear();
        UUID cleaningMatchId = activeMatch == null ? null : activeMatch.matchId;
        if (!restore.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeMatch != null && (!Objects.equals(activeMatch.matchId, cleaningMatchId) || activeMatch.phase == MatchPhase.FIGHTING)) return;
                restoreArenaBlocks(restore);
                if (hadFluidChanges) cleanupLooseArenaFluids();
            }, 2L);
        }
        for (UUID entityId : new ArrayList<>(spawnedEntities)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) entity.remove();
        }
        spawnedEntities.clear();
        spawnedEntityOwners.clear();
        cleanupStaleArenaEntities();
    }

    private static void restoreArenaBlocks(Map<BlockKey, BlockData> restore) {
        for (Map.Entry<BlockKey, BlockData> entry : restore.entrySet()) {
            Block block = entry.getKey().block();
            if (block != null) block.setBlockData(entry.getValue(), false);
        }
    }

    private void cleanupStaleArenaEntities() {
        if (arena == null || !arena.hasBounds() || arena.world() == null) return;
        for (Entity entity : new ArrayList<>(arena.world().getEntities())) {
            if (arena.containsExpanded(entity.getLocation(), 2.0D) && CLEANUP_ENTITY_TYPES.contains(entity.getType())) entity.remove();
        }
    }

    private int cleanupLooseArenaFluids() {
        if (arena == null || arena.first == null || arena.second == null || arena.world() == null) return 0;
        World world = arena.world();
        Location first = arena.first;
        Location second = arena.second;
        double deltaX = second.getX() - first.getX();
        double deltaZ = second.getZ() - first.getZ();
        double length = Math.max(1.0D, Math.hypot(deltaX, deltaZ));
        double alongX = deltaX / length;
        double alongZ = deltaZ / length;
        double acrossX = -alongZ;
        double acrossZ = alongX;
        double endPadding = 14.0D;
        double sidePadding = Math.max(24.0D, Math.min(48.0D, length * 0.58D));
        double centerX = (first.getX() + second.getX()) * 0.5D;
        double centerZ = (first.getZ() + second.getZ()) * 0.5D;
        double halfAlong = length * 0.5D + endPadding;
        int minX = (int) Math.floor(centerX - Math.abs(alongX) * halfAlong - Math.abs(acrossX) * sidePadding);
        int maxX = (int) Math.ceil(centerX + Math.abs(alongX) * halfAlong + Math.abs(acrossX) * sidePadding);
        int minZ = (int) Math.floor(centerZ - Math.abs(alongZ) * halfAlong - Math.abs(acrossZ) * sidePadding);
        int maxZ = (int) Math.ceil(centerZ + Math.abs(alongZ) * halfAlong + Math.abs(acrossZ) * sidePadding);
        int minY = Math.min(first.getBlockY(), second.getBlockY()) - 1;
        int maxY = Math.max(first.getBlockY(), second.getBlockY()) + 6;
        int removed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type != Material.WATER && type != Material.LAVA) continue;
                    int openSides = 0;
                    for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
                        Block side = block.getRelative(face);
                        if (side.isPassable() || side.getType() == Material.WATER || side.getType() == Material.LAVA) openSides++;
                    }
                    if (!shouldRemoveLooseArenaFluid(
                        type,
                        block.getRelative(BlockFace.DOWN).getType().isSolid(),
                        block.getRelative(BlockFace.UP).isPassable(),
                        openSides
                    )) continue;
                    block.setType(Material.AIR, false);
                    removed++;
                }
            }
        }
        return removed;
    }

    static boolean shouldRemoveLooseArenaFluid(Material type, boolean supported, boolean openAbove, int openSides) {
        return (type == Material.WATER || type == Material.LAVA) && supported && openAbove && openSides >= 3;
    }

    private void teleportInternal(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) return;
        internalTeleports.add(player.getUniqueId());
        try { player.teleport(location); }
        finally { internalTeleports.remove(player.getUniqueId()); }
    }

    private void announceMatch(String title, String subtitle) {
        if (activeMatch == null) return;
        for (UUID playerId : activeMatch.fighters()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            player.showTitle(net.kyori.adventure.title.Title.title(MM.deserialize(title), MM.deserialize(subtitle)));
            player.playSound(player.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 0.9F, 0.8F);
        }
    }

    private void announceToMatch(String message) {
        if (activeMatch == null) return;
        Set<UUID> recipients = new LinkedHashSet<>(activeMatch.spectators);
        recipients.addAll(activeMatch.fighters());
        for (UUID playerId : recipients) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) player.sendMessage(MM.deserialize("<dark_gray>[<red>Duel</red>]</dark_gray> " + message));
        }
    }

    private void playMatchSound(Sound sound, float volume, float pitch) {
        if (activeMatch == null) return;
        Set<UUID> players = new LinkedHashSet<>(activeMatch.spectators);
        players.addAll(activeMatch.fighters());
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private void scheduleRecovery(Player player, long delay) {
        if (player == null || !recoveries.containsKey(player.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || activeMatch != null && (activeMatch.isFighter(player.getUniqueId()) || activeMatch.spectators.contains(player.getUniqueId()))) return;
            PlayerSnapshot snapshot = recoveries.get(player.getUniqueId());
            if (snapshot == null) return;
            removeDuelHealthNormalization(player);
            snapshot.apply(player, true, true);
            recoveries.remove(player.getUniqueId());
            saveRecoveries();
            player.sendMessage(MessageUtil.success("Recovered your exact pre-duel state after an interrupted match."));
        }, delay);
    }

    private void schedulePendingCredit(Player player, long delay, int attempt) {
        if (player == null || !pendingCredits.containsKey(player.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingCredit credit = pendingCredits.get(player.getUniqueId());
            if (credit == null || !player.isOnline()) return;
            if (!plugin.getEssenceManager().isLoaded(player)) {
                if (attempt < 10) schedulePendingCredit(player, 20L, attempt + 1);
                return;
            }
            if (!plugin.getEssenceManager().canCreditFully(player, credit.amount)) {
                player.sendMessage(MessageUtil.warn("A <white>" + credit.amount + " Essence</white> duel payout is pending. Spend some Essence to make room."));
                return;
            }
            if (!plugin.getEssenceManager().credit(player, credit.amount, credit.reason)) return;
            pendingCredits.remove(player.getUniqueId());
            savePendingCredits();
            player.sendMessage(MessageUtil.success("Received your pending <white>" + credit.amount + " Essence</white> duel payout."));
            if (credit.betWins > 0 && plugin.getLeaderboardManager() != null) plugin.getLeaderboardManager().recordDuelBetWins(player, credit.betWins);
        }, delay);
    }

    private void loadArena() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arenaFile);
        Arena loaded = new Arena();
        loaded.lobby = readLocation(yaml, "points.lobby");
        loaded.first = readLocation(yaml, "points.fighter1");
        loaded.firstTwo = readLocation(yaml, "points.fighter1b");
        loaded.firstThree = readLocation(yaml, "points.fighter1c");
        loaded.second = readLocation(yaml, "points.fighter2");
        loaded.secondTwo = readLocation(yaml, "points.fighter2b");
        loaded.secondThree = readLocation(yaml, "points.fighter2c");
        loaded.spectator = readLocation(yaml, "points.spectator");
        loaded.cornerOne = readLocation(yaml, "bounds.corner1");
        loaded.cornerTwo = readLocation(yaml, "bounds.corner2");
        arena = loaded;
    }

    private void saveArena() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (arena != null) {
            writeLocation(yaml, "points.lobby", arena.lobby);
            writeLocation(yaml, "points.fighter1", arena.first);
            writeLocation(yaml, "points.fighter1b", arena.firstTwo);
            writeLocation(yaml, "points.fighter1c", arena.firstThree);
            writeLocation(yaml, "points.fighter2", arena.second);
            writeLocation(yaml, "points.fighter2b", arena.secondTwo);
            writeLocation(yaml, "points.fighter2c", arena.secondThree);
            writeLocation(yaml, "points.spectator", arena.spectator);
            writeLocation(yaml, "bounds.corner1", arena.cornerOne);
            writeLocation(yaml, "bounds.corner2", arena.cornerTwo);
        }
        saveYaml(yaml, arenaFile, "duel arena");
    }

    private void loadRecoveries() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(recoveryFile);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) return;
        for (String rawId : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(rawId);
                PlayerSnapshot snapshot = PlayerSnapshot.read(yaml, "players." + rawId);
                if (snapshot != null) recoveries.put(id, snapshot);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipped malformed duel recovery " + rawId + ": " + ex.getMessage());
            }
        }
    }

    private void saveRecoveries() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerSnapshot> entry : recoveries.entrySet()) entry.getValue().write(yaml, "players." + entry.getKey());
        saveYaml(yaml, recoveryFile, "duel recoveries");
    }

    private void loadPendingCredits() {
        YamlConfiguration credits = YamlConfiguration.loadConfiguration(escrowFile);
        ConfigurationSection pending = credits.getConfigurationSection("pending-credits");
        if (pending != null) {
            for (String rawId : pending.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(rawId);
                    long amount = pending.getLong(rawId + ".amount");
                    int betWins = pending.getInt(rawId + ".bet-wins", pending.getBoolean(rawId + ".bet-win") ? 1 : 0);
                    if (amount > 0L) pendingCredits.put(id, new PendingCredit(amount, pending.getString(rawId + ".reason", "duel recovery"), betWins));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        ConfigurationSection active = credits.getConfigurationSection("active-wagers");
        if (active != null) {
            for (String rawId : active.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(rawId);
                    long amount = active.getLong(rawId + ".amount");
                    if (amount > 0L) pendingCredits.merge(id, new PendingCredit(amount, "interrupted duel bet refund", 0), PendingCredit::merge);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        savePendingCredits();
    }

    private void saveActiveEscrow(Match match) {
        YamlConfiguration yaml = pendingCreditsYaml();
        for (Map.Entry<UUID, DuelRules.Wager> entry : match.wagers.entrySet()) {
            String path = "active-wagers." + entry.getKey();
            yaml.set(path + ".amount", entry.getValue().amount());
            yaml.set(path + ".side", entry.getValue().side().toString());
            yaml.set(path + ".name", match.wagerNames.getOrDefault(entry.getKey(), "Unknown"));
        }
        saveYaml(yaml, escrowFile, "duel escrow");
    }

    private void savePendingCredits() {
        saveYaml(pendingCreditsYaml(), escrowFile, "duel escrow");
    }

    private YamlConfiguration pendingCreditsYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PendingCredit> entry : pendingCredits.entrySet()) {
            String path = "pending-credits." + entry.getKey();
            yaml.set(path + ".amount", entry.getValue().amount);
            yaml.set(path + ".reason", entry.getValue().reason);
            yaml.set(path + ".bet-wins", entry.getValue().betWins);
        }
        return yaml;
    }

    private void clearEscrowFile() {
        savePendingCredits();
    }

    private void loadBoards() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(boardsFile);
        ConfigurationSection section = yaml.getConfigurationSection("boards");
        if (section == null) return;
        for (String rawId : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(rawId);
                Location location = readLocation(yaml, "boards." + rawId + ".location");
                BoardType type = BoardType.byInput(yaml.getString("boards." + rawId + ".type"));
                if (location != null && type != null) boards.put(id, new Board(id, location, type));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveBoards() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Board board : boards.values()) {
            String path = "boards." + board.id;
            yaml.set(path + ".type", board.type.id);
            writeLocation(yaml, path + ".location", board.location);
        }
        saveYaml(yaml, boardsFile, "duel leaderboards");
    }

    private void refreshBoards() {
        for (UUID id : new ArrayList<>(boards.keySet())) refreshBoard(id);
    }

    private void refreshBoard(UUID id) {
        Board board = boards.get(id);
        if (board == null || board.location.getWorld() == null) return;
        plugin.getDatabase().loadLeaderboard(board.type.stat.column(), 10).whenComplete((entries, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!boards.containsKey(id)) return;
            spawnBoardDisplay(board, error == null && entries != null ? entries : List.of());
        }));
    }

    private void spawnBoardDisplay(Board board, List<DatabaseManager.LeaderboardEntry> entries) {
        removeBoardDisplay(board.id);
        removePersistedBoardDisplays(board);
        World world = board.location.getWorld();
        if (world == null) return;
        world.getChunkAt(board.location).load();
        StringBuilder text = new StringBuilder("<gradient:#ef4444:#f59e0b><bold>").append(board.type.display.toUpperCase(Locale.ROOT)).append("</bold></gradient>");
        if (entries.isEmpty()) text.append("<newline><gray>No results yet</gray>");
        for (int index = 0; index < entries.size(); index++) {
            DatabaseManager.LeaderboardEntry entry = entries.get(index);
            text.append("<newline><yellow>").append(index + 1).append(".</yellow> <white>")
                .append(safeName(entry.playerName())).append("</white> <gray>-</gray> <aqua>")
                .append(entry.value()).append("</aqua>");
        }
        TextDisplay display = world.spawn(board.location, TextDisplay.class, entity -> {
            entity.text(MM.deserialize(text.toString()));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setSeeThrough(false);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            entity.setPersistent(true);
            entity.setInvulnerable(true);
            entity.getPersistentDataContainer().set(boardKey, PersistentDataType.STRING, board.id.toString());
            VisualRangeUtil.applyHologramRange(entity, 32.0D);
        });
        boardDisplays.put(board.id, display.getUniqueId());
    }

    private void removePersistedBoardDisplays(Board board) {
        World world = board.location.getWorld();
        if (world == null) return;
        String id = board.id.toString();
        for (Entity entity : world.getNearbyEntities(board.location, 4.0D, 16.0D, 4.0D)) {
            if (id.equals(entity.getPersistentDataContainer().get(boardKey, PersistentDataType.STRING))) entity.remove();
        }
    }

    private void removeBoardDisplay(UUID id) {
        UUID entityId = boardDisplays.remove(id);
        Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
        if (entity != null) entity.remove();
    }

    private void removeAllBoardDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(boardKey, PersistentDataType.STRING)) display.remove();
            }
        }
        boardDisplays.clear();
    }

    private void saveYaml(YamlConfiguration yaml, File file, String description) {
        try { AtomicYamlFile.save(yaml, file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save " + description + ": " + ex.getMessage()); }
    }

    private static void writeLocation(YamlConfiguration yaml, String path, Location location) {
        if (location == null || location.getWorld() == null) return;
        yaml.set(path + ".world", location.getWorld().getUID().toString());
        yaml.set(path + ".world-name", location.getWorld().getName());
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw());
        yaml.set(path + ".pitch", location.getPitch());
    }

    private static Location readLocation(YamlConfiguration yaml, String path) {
        String worldId = yaml.getString(path + ".world");
        World world = null;
        if (worldId != null) {
            try { world = Bukkit.getWorld(UUID.fromString(worldId)); }
            catch (IllegalArgumentException ignored) { }
        }
        if (world == null) world = Bukkit.getWorld(yaml.getString(path + ".world-name", ""));
        if (world == null || !yaml.contains(path + ".x")) return null;
        return new Location(world, yaml.getDouble(path + ".x"), yaml.getDouble(path + ".y"), yaml.getDouble(path + ".z"),
            (float) yaml.getDouble(path + ".yaw"), (float) yaml.getDouble(path + ".pitch"));
    }

    private ItemStack modeItem(DuelMode mode, DuelMode selected) {
        return actionItem(mode.icon, "mode:" + mode.id, (mode == selected ? "<green>" : "<white>") + "<bold>" + mode.display + "</bold>", List.of(
            mode.description,
            mode == selected ? "<green>Selected</green>" : "<yellow>Click to select.</yellow>"
        ));
    }

    private ItemStack roundsItem(int rounds, int selected) {
        Material material = rounds == 1 ? Material.IRON_SWORD : rounds == 2 ? Material.DIAMOND_SWORD : Material.NETHERITE_SWORD;
        return actionItem(material, "rounds:" + rounds, (rounds == selected ? "<green>" : "<white>") + "<bold>First to " + rounds + "</bold>", List.of(
            "<gray>Up to " + (rounds * 2 - 1) + " rounds.</gray>",
            rounds == selected ? "<green>Selected</green>" : "<yellow>Click to select.</yellow>"
        ));
    }

    private ItemStack teamSizeItem(int teamSize, int selected) {
        Material material = teamSize == 1 ? Material.IRON_CHESTPLATE : teamSize == 2 ? Material.DIAMOND_CHESTPLATE : Material.NETHERITE_CHESTPLATE;
        return actionItem(material, "teamsize:" + teamSize, (teamSize == selected ? "<green>" : "<white>") + "<bold>" + teamSize + "v" + teamSize + "</bold>", List.of(
            teamSize == 1 ? "<gray>Solo duel.</gray>" : "<gray>Requires a full party of " + teamSize + ".</gray>",
            teamSize == selected ? "<green>Selected</green>" : "<yellow>Click to select.</yellow>"
        ));
    }

    private ItemStack amountButton(Material material, long delta, String label) {
        return actionItem(material, "adjuststake:" + delta, "<white><bold>" + label + " Essence</bold></white>", List.of(
            "<gray>Adjust the pending Essence stake.</gray>"
        ));
    }

    private ItemStack fighterBetItem(Match match, UUID fighterId, String name, UUID selected) {
        long pool = DuelRules.poolFor(match.wagers, fighterId);
        long itemCount = match.itemWagers.values().stream()
            .filter(wager -> fighterId.equals(wager.side))
            .mapToLong(wager -> wager.escrowed.item().getAmount())
            .sum();
        return actionItem(selected != null && selected.equals(fighterId) ? Material.LIME_CONCRETE : Material.PLAYER_HEAD,
            "side:" + fighterId,
            "<white><bold>" + safeName(name) + "</bold></white>",
            List.of(
                "<gray>Pool: <white>" + pool + " Essence • " + itemCount + " item" + (itemCount == 1L ? "" : "s") + "</white>.</gray>",
                selected != null && selected.equals(fighterId) ? "<green>Selected</green>" : "<yellow>Click to back this team.</yellow>"
            ));
    }

    private long clampBetAmount(long current, long delta) {
        if (delta > 0L && current > MAX_ESSENCE_BET - delta) return MAX_ESSENCE_BET;
        return Math.max(1L, Math.min(MAX_ESSENCE_BET, current + delta));
    }

    private boolean isUnsafeItemWager(ItemStack item) {
        if (itemWagerEscrow.isEmpty(item) || itemWagerEscrow.hasMenuPreviewMarker(item) || itemWagerEscrow.hasAnyEscrowMarker(item)) return true;
        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) return true;
        if (plugin.getLegendaryListener() != null
            && (plugin.getLegendaryListener().isLegendaryItem(item)
                || plugin.getLegendaryListener().isEnderBoneItem(item)
                || plugin.getLegendaryListener().isOrbOfTheMysticsItem(item))) return true;
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().isSeasonRelic(item)) return true;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta) return true;
        return meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof InventoryHolder;
    }

    private static List<ItemStack> splitItemAmount(ItemStack prototype, long amount) {
        if (prototype == null || prototype.getType().isAir() || amount <= 0L) return List.of();
        List<ItemStack> out = new ArrayList<>();
        int maxStack = Math.max(1, prototype.getMaxStackSize());
        long remaining = amount;
        while (remaining > 0L) {
            int count = (int) Math.min(maxStack, remaining);
            ItemStack stack = prototype.clone();
            stack.setAmount(count);
            out.add(stack);
            remaining -= count;
        }
        return out;
    }

    private static String readableItemName(ItemStack item) {
        if (item == null || item.getType().isAir()) return "item";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            String name = PlainTextComponentSerializer.plainText().serialize(meta.displayName()).trim();
            if (!name.isBlank()) return safeName(name);
        }
        String[] words = item.getType().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private ItemStack actionItem(Material material, String action, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        if (lore != null) {
            meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream()
                .map(line -> MM.deserialize("<reset><!italic>" + line))
                .toList());
        }
        meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private void decorate(Inventory inventory) {
        ItemStack pane = actionItem(Material.BLACK_STAINED_GLASS_PANE, "none", "<black>.</black>", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }

    private String menuAction(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
    }

    private static boolean isInventoryBlock(BlockState state) {
        return state instanceof Container || state.getType().name().endsWith("SHULKER_BOX") || state.getType() == Material.ENDER_CHEST;
    }

    private static boolean isInventoryMaterial(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("SHULKER_BOX")
            || name.endsWith("CHEST")
            || name.endsWith("FURNACE")
            || name.endsWith("SMOKER")
            || name.endsWith("BLAST_FURNACE")
            || Set.of(Material.BARREL, Material.HOPPER, Material.DISPENSER, Material.DROPPER, Material.BREWING_STAND).contains(material);
    }

    private static boolean isRestrictedArenaBlock(Material material) {
        if (material == null) return true;
        String name = material.name();
        return isInventoryMaterial(material)
            || name.endsWith("_BED")
            || material == Material.PISTON
            || material == Material.STICKY_PISTON;
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            return source instanceof Player player ? player : null;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) return player;
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player player) return player;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) return player;
        UUID ownerId = damager == null ? null : spawnedEntityOwners.get(damager.getUniqueId());
        return ownerId == null ? null : Bukkit.getPlayer(ownerId);
    }

    private static boolean isRangedDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            return byEntity.getDamager() instanceof Projectile
                || byEntity.getDamager() instanceof TNTPrimed
                || byEntity.getDamager() instanceof EnderCrystal;
        }
        return event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
            || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
            || event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE;
    }

    private static boolean movedPosition(Location from, Location to) {
        return Double.compare(from.getX(), to.getX()) != 0 || Double.compare(from.getY(), to.getY()) != 0 || Double.compare(from.getZ(), to.getZ()) != 0;
    }

    private static double healthRatio(Player player) {
        return player == null ? 0.0D : Math.max(0.0D, player.getHealth()) / Math.max(1.0D, maxHealth(player));
    }

    private static double maxHealth(Player player) {
        return player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0D : player.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    private static String formatClock(long seconds) {
        return (seconds / 60L) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60L);
    }

    private static String fighterName(Match match, UUID playerId) {
        if (match == null || playerId == null) return "Unknown";
        return playerId.equals(match.first) ? match.firstName : playerId.equals(match.second) ? match.secondName : "Unknown";
    }

    private static long totalPool(Match match) {
        return match == null ? 0L : match.wagers.values().stream().mapToLong(DuelRules.Wager::amount).sum();
    }

    private static String safeName(String value) {
        return value == null ? "Unknown" : value.replace("<", "").replace(">", "");
    }

    private void cleanupExpiredChallenges() {
        long now = System.currentTimeMillis();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
        partyInvites.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
    }

    public enum DuelMode {
        OPEN("open", "Open PvP", Material.NETHERITE_SWORD, "<gray>All normal PvP tools and healing.</gray>"),
        NO_HEAL("noheal", "No Healing", Material.GOLDEN_APPLE, "<gray>Healing is disabled during rounds.</gray>"),
        MELEE("melee", "Melee Only", Material.IRON_SWORD, "<gray>Direct melee damage only.</gray>");

        private final String id;
        private final String display;
        private final Material icon;
        private final String description;

        DuelMode(String id, String display, Material icon, String description) {
            this.id = id;
            this.display = display;
            this.icon = icon;
            this.description = description;
        }

        public String id() { return id; }
        public String display() { return display; }

        public static DuelMode byInput(String input) {
            if (input == null) return null;
            String normalized = input.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
            return switch (normalized) {
                case "open", "all", "standard" -> OPEN;
                case "noheal", "nohealing" -> NO_HEAL;
                case "melee", "meleeonly" -> MELEE;
                default -> null;
            };
        }
    }

    public enum BoardType {
        WINS("wins", "Duel Wins", LeaderboardManager.LeaderboardType.DUEL_WINS),
        BETS("bets", "Bets Won", LeaderboardManager.LeaderboardType.DUEL_BET_WINS);

        private final String id;
        private final String display;
        private final LeaderboardManager.LeaderboardType stat;

        BoardType(String id, String display, LeaderboardManager.LeaderboardType stat) {
            this.id = id;
            this.display = display;
            this.stat = stat;
        }

        public static BoardType byInput(String input) {
            if (input == null) return null;
            return input.equalsIgnoreCase("wins") ? WINS : input.equalsIgnoreCase("bets") || input.equalsIgnoreCase("betwins") ? BETS : null;
        }
    }

    private enum MatchPhase { BETTING, ROUND_COUNTDOWN, FIGHTING, BETWEEN_ROUNDS, ENDING }
    private enum EndReason { WIN, FORFEIT, DRAW, ADMIN_CANCEL, SHUTDOWN }
    private enum MenuView { MAIN, BET, PARTY, CHALLENGE }

    private record QueueKey(int teamSize, int roundsToWin, DuelMode mode) { }
    private record QueueEntry(QueueKey key, UUID captain, List<UUID> members) {
        private static QueueEntry capture(QueueKey key, UUID captain, List<Player> roster) {
            return new QueueEntry(key, captain, roster.stream().map(Player::getUniqueId).toList());
        }
    }
    private record Challenge(UUID challenger, List<UUID> challengerTeam, int teamSize, int roundsToWin, DuelMode mode, long expiresAt) { }
    private record PartyInvite(UUID captain, long expiresAt) { }
    private record Board(UUID id, Location location, BoardType type) { }
    private record ItemWager(UUID side, EscrowedItem escrowed) { }
    private record DuelMenuHolder(MenuView view) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final class PendingCredit {
        private final long amount;
        private final String reason;
        private final int betWins;

        private PendingCredit(long amount, String reason, int betWins) {
            this.amount = Math.max(0L, amount);
            this.reason = reason == null || reason.isBlank() ? "duel recovery" : reason;
            this.betWins = Math.max(0, betWins);
        }

        private PendingCredit merge(PendingCredit other) {
            return new PendingCredit(Math.addExact(amount, other.amount), reason, Math.addExact(betWins, other.betWins));
        }
    }

    private static final class DuelParty {
        private UUID captain;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

        private DuelParty(UUID captain) {
            this.captain = captain;
            this.members.add(captain);
        }
    }

    private static final class Match {
        private final UUID matchId = UUID.randomUUID();
        private final UUID first;
        private final UUID second;
        private final List<UUID> firstTeam;
        private final List<UUID> secondTeam;
        private final List<UUID> fighters;
        private final String firstName;
        private final String secondName;
        private final Map<UUID, PlayerSnapshot> snapshots;
        private final int teamSize;
        private final int roundsToWin;
        private final DuelMode mode;
        private final Map<UUID, DuelRules.Wager> wagers = new LinkedHashMap<>();
        private final Map<UUID, ItemWager> itemWagers = new LinkedHashMap<>();
        private final Map<UUID, String> wagerNames = new HashMap<>();
        private final Set<UUID> spectators = new LinkedHashSet<>();
        private final Set<UUID> eliminated = new LinkedHashSet<>();
        private MatchPhase phase = MatchPhase.BETTING;
        private long phaseEndsAt;
        private int firstScore;
        private int secondScore;
        private int consecutiveDraws;
        private double firstRoundDamage;
        private double secondRoundDamage;

        private Match(List<Player> firstTeam, List<Player> secondTeam, Map<UUID, PlayerSnapshot> snapshots, int roundsToWin, DuelMode mode) {
            this.firstTeam = firstTeam.stream().map(Player::getUniqueId).toList();
            this.secondTeam = secondTeam.stream().map(Player::getUniqueId).toList();
            List<UUID> combined = new ArrayList<>(this.firstTeam.size() + this.secondTeam.size());
            combined.addAll(this.firstTeam);
            combined.addAll(this.secondTeam);
            this.fighters = List.copyOf(combined);
            this.first = this.firstTeam.getFirst();
            this.second = this.secondTeam.getFirst();
            this.firstName = teamName(firstTeam);
            this.secondName = teamName(secondTeam);
            this.snapshots = Map.copyOf(snapshots);
            this.teamSize = this.firstTeam.size();
            this.roundsToWin = roundsToWin;
            this.mode = mode;
        }

        private static String teamName(List<Player> roster) {
            if (roster.size() == 1) return roster.getFirst().getName();
            return roster.stream().map(Player::getName).reduce((left, right) -> left + " & " + right).orElse("Team");
        }

        private List<UUID> fighters() { return fighters; }

        private boolean isFighter(UUID id) { return firstTeam.contains(id) || secondTeam.contains(id); }
        private boolean isSide(UUID id) { return first.equals(id) || second.equals(id); }
        private UUID sideOf(UUID id) { return firstTeam.contains(id) || first.equals(id) ? first : secondTeam.contains(id) || second.equals(id) ? second : null; }
        private UUID opponent(UUID id) { return first.equals(sideOf(id)) ? second : second.equals(sideOf(id)) ? first : null; }
        private List<UUID> team(UUID id) { return first.equals(sideOf(id)) ? firstTeam : second.equals(sideOf(id)) ? secondTeam : List.of(); }
        private int indexOnSide(UUID id) { return firstTeam.contains(id) ? firstTeam.indexOf(id) : secondTeam.indexOf(id); }
        private int scoreFor(UUID id) { return first.equals(sideOf(id)) ? firstScore : second.equals(sideOf(id)) ? secondScore : 0; }
        private int roundNumber() { return firstScore + secondScore + 1; }

        private double teamHealthRatio(UUID side) {
            List<UUID> team = team(side);
            if (team.isEmpty()) return 0.0D;
            double health = 0.0D;
            for (UUID memberId : team) {
                if (eliminated.contains(memberId)) continue;
                health += healthRatio(Bukkit.getPlayer(memberId));
            }
            return health / team.size();
        }
    }

    private static final class ItemWagerGroup {
        private final ItemStack prototype;
        private final Map<UUID, ItemWager> wagers = new LinkedHashMap<>();

        private ItemWagerGroup(ItemStack prototype) {
            this.prototype = prototype;
        }
    }

    private static final class Arena {
        private Location lobby;
        private Location first;
        private Location firstTwo;
        private Location firstThree;
        private Location second;
        private Location secondTwo;
        private Location secondThree;
        private Location spectator;
        private Location cornerOne;
        private Location cornerTwo;

        private boolean ready() {
            if (first == null || second == null || spectator == null || !hasBounds()) return false;
            World world = world();
            List<Location> required = List.of(first, second, spectator, cornerOne, cornerTwo);
            List<Location> optional = java.util.stream.Stream.of(firstTwo, firstThree, secondTwo, secondThree).filter(Objects::nonNull).toList();
            return world != null && required.stream().allMatch(location -> world.equals(location.getWorld()))
                && optional.stream().allMatch(location -> world.equals(location.getWorld()))
                && (lobby == null || world.equals(lobby.getWorld()));
        }

        private int configuredExtraSpawns() {
            int count = 0;
            if (firstTwo != null) count++;
            if (firstThree != null) count++;
            if (secondTwo != null) count++;
            if (secondThree != null) count++;
            return count;
        }

        private Location spawnFor(boolean firstSide, int index, int teamSize) {
            Location base = firstSide ? first : second;
            if (base == null) return null;
            Location resolved = base;
            if (index > 0) {
                Location explicit = firstSide
                    ? (index == 1 ? firstTwo : firstThree)
                    : (index == 1 ? secondTwo : secondThree);
                if (explicit != null) {
                    resolved = explicit;
                } else {
                    double deltaX = second.getX() - first.getX();
                    double deltaZ = second.getZ() - first.getZ();
                    double length = Math.hypot(deltaX, deltaZ);
                    if (length >= 0.001D) {
                        double direction = index == 1 ? 1.0D : -1.0D;
                        Location derived = base.clone().add((-deltaZ / length) * 1.75D * direction, 0.0D, (deltaX / length) * 1.75D * direction);
                        if (contains(derived)) resolved = derived;
                    }
                }
            }
            return faceHorizontally(resolved, firstSide ? second : first);
        }

        private Location spectatorView() {
            if (spectator == null) return null;
            Location view = spectator.clone();
            if (first == null || second == null || !Objects.equals(view.getWorld(), first.getWorld())
                || !Objects.equals(view.getWorld(), second.getWorld())) {
                view.setPitch(0.0F);
                return view;
            }
            Location target = new Location(
                view.getWorld(),
                (first.getX() + second.getX()) * 0.5D,
                Math.min(first.getY(), second.getY()) + 1.2D,
                (first.getZ() + second.getZ()) * 0.5D
            );
            Vector direction = target.toVector().subtract(view.toVector());
            if (direction.lengthSquared() > 0.0001D) view.setDirection(direction.normalize());
            return view;
        }

        private static Location faceHorizontally(Location spawn, Location target) {
            Location facing = spawn.clone();
            if (target == null || !Objects.equals(spawn.getWorld(), target.getWorld())) {
                facing.setPitch(0.0F);
                return facing;
            }
            Vector direction = target.toVector().subtract(spawn.toVector()).setY(0.0D);
            if (direction.lengthSquared() < 0.0001D) {
                facing.setPitch(0.0F);
                return facing;
            }
            facing.setDirection(direction.normalize());
            facing.setPitch(0.0F);
            return facing;
        }

        private boolean hasBounds() { return cornerOne != null && cornerTwo != null && Objects.equals(cornerOne.getWorld(), cornerTwo.getWorld()); }
        private World world() { return cornerOne == null ? null : cornerOne.getWorld(); }

        private boolean contains(Location location) { return containsExpanded(location, 0.0D); }

        private boolean containsExpanded(Location location, double expand) {
            if (!hasBounds() || location == null || !Objects.equals(world(), location.getWorld())) return false;
            double minX = Math.min(cornerOne.getX(), cornerTwo.getX()) - expand;
            double maxX = Math.max(cornerOne.getX(), cornerTwo.getX()) + 1.0D + expand;
            double minY = Math.min(cornerOne.getY(), cornerTwo.getY()) - expand;
            double maxY = Math.max(cornerOne.getY(), cornerTwo.getY()) + 1.0D + expand;
            double minZ = Math.min(cornerOne.getZ(), cornerTwo.getZ()) - expand;
            double maxZ = Math.max(cornerOne.getZ(), cornerTwo.getZ()) + 1.0D + expand;
            return location.getX() >= minX && location.getX() <= maxX
                && location.getY() >= minY && location.getY() <= maxY
                && location.getZ() >= minZ && location.getZ() <= maxZ;
        }
    }

    private record BlockKey(UUID world, int x, int y, int z) {
        private static BlockKey of(Block block) { return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
        private Block block() {
            World resolved = Bukkit.getWorld(world);
            return resolved == null ? null : resolved.getBlockAt(x, y, z);
        }
    }

    private static final class PlayerSnapshot {
        private final String storage;
        private final String armor;
        private final String extra;
        private final int heldSlot;
        private final Location location;
        private final GameMode gameMode;
        private final double health;
        private final double absorption;
        private final int food;
        private final float saturation;
        private final float exhaustion;
        private final int level;
        private final float exp;
        private final int totalExperience;
        private final int fireTicks;
        private final Collection<PotionEffect> effects;
        private final boolean allowFlight;
        private final boolean flying;
        private final boolean invulnerable;

        private PlayerSnapshot(String storage, String armor, String extra, int heldSlot, Location location, GameMode gameMode,
                               double health, double absorption, int food, float saturation, float exhaustion, int level, float exp, int totalExperience,
                               int fireTicks, Collection<PotionEffect> effects, boolean allowFlight, boolean flying,
                               boolean invulnerable) {
            this.storage = storage;
            this.armor = armor;
            this.extra = extra;
            this.heldSlot = heldSlot;
            this.location = location;
            this.gameMode = gameMode;
            this.health = health;
            this.absorption = absorption;
            this.food = food;
            this.saturation = saturation;
            this.exhaustion = exhaustion;
            this.level = level;
            this.exp = exp;
            this.totalExperience = totalExperience;
            this.fireTicks = fireTicks;
            this.effects = List.copyOf(effects);
            this.allowFlight = allowFlight;
            this.flying = flying;
            this.invulnerable = invulnerable;
        }

        private static PlayerSnapshot capture(Player player) {
            return new PlayerSnapshot(
                encode(player.getInventory().getStorageContents()),
                encode(player.getInventory().getArmorContents()),
                encode(player.getInventory().getExtraContents()),
                player.getInventory().getHeldItemSlot(),
                player.getLocation().clone(),
                player.getGameMode(),
                player.getHealth(),
                player.getAbsorptionAmount(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getLevel(),
                player.getExp(),
                player.getTotalExperience(),
                player.getFireTicks(),
                new ArrayList<>(player.getActivePotionEffects()),
                player.getAllowFlight(),
                player.isFlying(),
                player.isInvulnerable()
            );
        }

        private void applyCombatReset(Player player) { applyState(player, false, false); }
        private void apply(Player player, boolean restoreLocation, boolean restoreGameMode) { applyState(player, restoreLocation, restoreGameMode); }

        private void applyState(Player player, boolean restoreLocation, boolean restoreGameMode) {
            player.getInventory().setStorageContents(decode(storage));
            player.getInventory().setArmorContents(decode(armor));
            player.getInventory().setExtraContents(decode(extra));
            player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, heldSlot)));
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
            for (PotionEffect effect : effects) player.addPotionEffect(effect);
            player.setHealth(Math.max(0.5D, Math.min(health, maxHealth(player))));
            player.setAbsorptionAmount(Math.max(0.0D, absorption));
            player.setFoodLevel(Math.max(0, Math.min(20, food)));
            player.setSaturation(Math.max(0.0F, Math.min(saturation, player.getFoodLevel())));
            player.setExhaustion(Math.max(0.0F, exhaustion));
            player.setLevel(Math.max(0, level));
            player.setExp(Math.max(0.0F, Math.min(0.999999F, exp)));
            player.setTotalExperience(Math.max(0, totalExperience));
            player.setFireTicks(Math.max(0, fireTicks));
            player.setFallDistance(0.0F);
            if (restoreGameMode) {
                player.setGameMode(gameMode);
                player.setInvulnerable(invulnerable);
                player.setAllowFlight(allowFlight || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
                player.setFlying(flying && player.getAllowFlight());
            }
            if (restoreLocation && location != null && location.getWorld() != null) player.teleport(location);
            player.updateInventory();
        }

        private void write(YamlConfiguration yaml, String path) {
            yaml.set(path + ".storage", storage);
            yaml.set(path + ".armor", armor);
            yaml.set(path + ".extra", extra);
            yaml.set(path + ".held-slot", heldSlot);
            writeLocation(yaml, path + ".location", location);
            yaml.set(path + ".game-mode", gameMode.name());
            yaml.set(path + ".health", health);
            yaml.set(path + ".absorption", absorption);
            yaml.set(path + ".food", food);
            yaml.set(path + ".saturation", saturation);
            yaml.set(path + ".exhaustion", exhaustion);
            yaml.set(path + ".level", level);
            yaml.set(path + ".exp", exp);
            yaml.set(path + ".total-experience", totalExperience);
            yaml.set(path + ".fire-ticks", fireTicks);
            yaml.set(path + ".effects", new ArrayList<>(effects));
            yaml.set(path + ".allow-flight", allowFlight);
            yaml.set(path + ".flying", flying);
            yaml.set(path + ".invulnerable", invulnerable);
        }

        private static PlayerSnapshot read(YamlConfiguration yaml, String path) {
            String storage = yaml.getString(path + ".storage");
            String armor = yaml.getString(path + ".armor");
            String extra = yaml.getString(path + ".extra");
            Location location = readLocation(yaml, path + ".location");
            if (storage == null || armor == null || extra == null || location == null) return null;
            List<PotionEffect> effects = new ArrayList<>();
            for (Object raw : yaml.getList(path + ".effects", List.of())) if (raw instanceof PotionEffect effect) effects.add(effect);
            GameMode gameMode;
            try { gameMode = GameMode.valueOf(yaml.getString(path + ".game-mode", "SURVIVAL")); }
            catch (IllegalArgumentException ex) { gameMode = GameMode.SURVIVAL; }
            return new PlayerSnapshot(storage, armor, extra, yaml.getInt(path + ".held-slot"), location, gameMode,
                yaml.getDouble(path + ".health", 20.0D), yaml.getDouble(path + ".absorption"), yaml.getInt(path + ".food", 20), (float) yaml.getDouble(path + ".saturation", 5.0D),
                (float) yaml.getDouble(path + ".exhaustion"), yaml.getInt(path + ".level"), (float) yaml.getDouble(path + ".exp"),
                yaml.getInt(path + ".total-experience"), yaml.getInt(path + ".fire-ticks"), effects,
                yaml.getBoolean(path + ".allow-flight"), yaml.getBoolean(path + ".flying"),
                yaml.getBoolean(path + ".invulnerable"));
        }

        private static String encode(ItemStack[] items) { return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items)); }
        private static ItemStack[] decode(String encoded) { return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded)); }
    }
}
