package me.rique.smpcore.quest;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.compat.BedrockFamiliarVisibilityManager;
import me.rique.smpcore.command.MainMenuCommand;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MayorQuestManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MENU_SIZE = 45;
    private static final int PET_COLLECTION_MENU_SIZE = 45;
    private static final int PET_MENU_SIZE = 27;
    private static final int[] QUEST_SLOTS = {10, 11, 12, 13, 14, 15};
    private static final int PET_SLOT = 31;
    private static final int CLOSE_SLOT = 40;
    private static final int PET_STATUS_SLOT = 4;
    private static final int PET_SPAWN_SLOT = 11;
    private static final int PET_DESPAWN_SLOT = 15;
    private static final int PET_BACK_SLOT = 22;
    private static final int PET_COLLECTION_STATUS_SLOT = 4;
    private static final int PET_COLLECTION_WISP_SLOT = 10;
    private static final int PET_COLLECTION_MINER_SLOT = 12;
    private static final int PET_COLLECTION_FARMER_SLOT = 14;
    private static final int PET_COLLECTION_WITCH_SLOT = 16;
    private static final int PET_COLLECTION_BACK_SLOT = 40;
    private static final String ACTION_SPAWN_PET = "spawn_pet";
    private static final String ACTION_DESPAWN_PET = "despawn_pet";
    private static final String ACTION_BACK_TO_QUESTS = "back_to_quests";
    private static final String ACTION_BACK_TO_MAIN_MENU = "back_to_main_menu";
    private static final String ACTION_OPEN_MINER_PET = "open_miner_pet";
    private static final String ACTION_OPEN_WISP_PET = "open_wisp_pet";
    private static final String ACTION_OPEN_FARMER_PET = "open_farmer_pet";
    private static final String ACTION_OPEN_WITCH_PET = "open_witch_pet";
    public static final String VEIL_WISP_FAMILIAR_ID = "veil_wisp";
    private static final UUID VEIL_WISP_PROFILE_ID = UUID.fromString("8a64315b-8ef4-4f12-a2cf-51701d0f5170");
    private static final String VEIL_WISP_TEXTURE_VALUE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjQxMmU3MDM3NWVjOTllZTM4YWU5NGIzMGU5YjEwNzUyZDQ1OTY2MmI1NDc5NGRmZTY2ZmU2YTE4M2M2NzJkMyJ9fX0=";
    private static final double PET_LABEL_TITLE_OFFSET = 0.95D;
    private static final double PET_LABEL_SUBTITLE_OFFSET = 0.48D;
    private static final long PET_INTERACTION_COOLDOWN_MS = 2000L;

    private final SMPCore plugin;
    private final NamespacedKey keyCompleted;
    private final NamespacedKey keyPetUnlocked;
    private final NamespacedKey keyPetHidden;
    private final NamespacedKey keyActiveFamiliar;
    private final NamespacedKey keyMenuQuest;
    private final NamespacedKey keyMenuAction;
    private final NamespacedKey keyPetOwner;
    private final Map<UUID, UUID> activePets = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activePetBedrockBodies = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activePetHitboxes = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activePetTitleLabels = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activePetSubtitleLabels = new ConcurrentHashMap<>();
    private final Map<UUID, FamiliarMotion.State> petMotionStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextPetInteractionAt = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> pendingBossCredits = new ConcurrentHashMap<>();
    private final File pendingCreditsFile;
    private final ItemStack veilWispHead;
    private final List<QuestDefinition> quests;
    private BukkitTask petTask;

    public MayorQuestManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyCompleted = new NamespacedKey(plugin, "mayor_completed_quests");
        this.keyPetUnlocked = new NamespacedKey(plugin, "mayor_pet_unlocked");
        this.keyPetHidden = new NamespacedKey(plugin, "mayor_pet_hidden");
        this.keyActiveFamiliar = new NamespacedKey(plugin, "active_familiar");
        this.keyMenuQuest = new NamespacedKey(plugin, "mayor_menu_quest");
        this.keyMenuAction = new NamespacedKey(plugin, "mayor_menu_action");
        this.keyPetOwner = new NamespacedKey(plugin, "mayor_pet_owner");
        this.pendingCreditsFile = new File(plugin.getDataFolder(), "mayor-pending-boss-credits.yml");
        this.veilWispHead = createVeilWispHead();
        this.quests = buildQuests();
    }

    public void start() {
        loadPendingBossCredits();
        removeOrphanedPetEntities();
        if (petTask != null) {
            petTask.cancel();
        }
        petTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPets, 20L, FamiliarMotion.UPDATE_TICKS);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(player -> {
            applyPendingBossCredits(player);
            spawnPetIfUnlocked(player);
        }));
    }

    public void shutdown() {
        if (petTask != null) {
            petTask.cancel();
            petTask = null;
        }
        for (UUID petId : new ArrayList<>(activePets.values())) {
            removeMappedEntity(petId);
        }
        for (UUID hitboxId : new ArrayList<>(activePetHitboxes.values())) {
            removeMappedEntity(hitboxId);
        }
        for (UUID bodyId : new ArrayList<>(activePetBedrockBodies.values())) {
            removeMappedEntity(bodyId);
        }
        for (UUID labelId : new ArrayList<>(activePetTitleLabels.values())) {
            removeMappedEntity(labelId);
        }
        for (UUID labelId : new ArrayList<>(activePetSubtitleLabels.values())) {
            removeMappedEntity(labelId);
        }
        activePets.clear();
        activePetHitboxes.clear();
        activePetBedrockBodies.clear();
        activePetTitleLabels.clear();
        activePetSubtitleLabels.clear();
        petMotionStates.clear();
        nextPetInteractionAt.clear();
        savePendingBossCredits();
    }

    public void openFromMayorNpc(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendMessage(MessageUtil.info("<gold>Mayor Bah:</gold> Pick a job from the list and bring me the requested materials or boss proof."));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.7f, 0.85f);
        openMenu(player);
    }

    public void recordBossDefeat(String bossId, Collection<UUID> playerIds) {
        if (bossId == null || bossId.isBlank() || playerIds == null || playerIds.isEmpty()) {
            return;
        }
        String key = bossKillKey(bossId);
        Set<UUID> unique = new HashSet<>(playerIds);
        boolean pendingChanged = false;
        for (UUID playerId : unique) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                pendingChanged |= queuePendingBossCredit(playerId, key, 1);
                continue;
            }
            applyBossCredit(player, key, 1, true);
        }
        if (pendingChanged) savePendingBossCredits();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applyPendingBossCredits(event.getPlayer());
            spawnPetIfUnlocked(event.getPlayer());
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        removePet(playerId);
        nextPetInteractionAt.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        UUID ownerId = petOwnerId(event.getRightClicked());
        if (ownerId == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (isBossFightNearby(player)) {
            return;
        }
        if (player.isSneaking() && player.getUniqueId().equals(ownerId)) {
            openPetMenu(player);
            return;
        }
        long now = System.currentTimeMillis();
        Long nextAllowed = nextPetInteractionAt.get(player.getUniqueId());
        if (nextAllowed != null && nextAllowed > now) {
            return;
        }
        nextPetInteractionAt.put(player.getUniqueId(), now + PET_INTERACTION_COOLDOWN_MS);
        playPetInteraction(player, event.getRightClicked(), ownerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof MayorQuestMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!holder.playerId().equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
            && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (!MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            return;
        }
        String questId = questId(event.getCurrentItem());
        if (questId == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> tryCompleteQuest(player, questId));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (!(holder instanceof MayorPetMenuHolder) && !(holder instanceof MayorPetCollectionMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!holderBelongsToPlayer(holder, player)) {
            player.closeInventory();
            return;
        }
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
            && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        if (!MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            return;
        }
        String action = menuAction(event.getCurrentItem());
        if (action == null) {
            return;
        }
        if (holder instanceof MayorPetCollectionMenuHolder) {
            Bukkit.getScheduler().runTask(plugin, () -> handlePetCollectionAction(player, action));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> handlePetAction(player, action));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof MayorQuestMenuHolder || holder instanceof MayorPetMenuHolder || holder instanceof MayorPetCollectionMenuHolder) {
            event.setCancelled(true);
        }
    }

    public void openPetCollectionMenu(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
            new MayorPetCollectionMenuHolder(player.getUniqueId()),
            PET_COLLECTION_MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#c084fc:#22d3ee><bold>Familiar Stable</bold></gradient>"), "Familiar Stable")
        );
        fill(inventory);
        inventory.setItem(PET_COLLECTION_STATUS_SLOT, item(Material.LEAD, "<gradient:#c084fc:#22d3ee><bold>Your Familiars</bold></gradient>", List.of(
            "<gray>Manage unlocked familiars here.</gray>",
            "<gray>Right-click an active familiar to interact.</gray>",
            "<dark_gray>Sneak-right-click it to manage. Only one can be active.</dark_gray>"
        ), null));
        inventory.setItem(PET_COLLECTION_WISP_SLOT, petCollectionWispItem(player));
        boolean minerUnlocked = plugin.getMinerManager() != null && plugin.getMinerManager().hasMinerPet(player);
        boolean minerActive = minerUnlocked && plugin.getMinerManager().hasActiveMinerPet(player);
        ItemStack minerIcon = actionItem(
            Material.PLAYER_HEAD,
            minerUnlocked ? "<yellow><bold>Miner Familiar</bold></yellow>" : "<dark_gray><bold>Miner Familiar</bold></dark_gray>",
            minerUnlocked
                ? List.of(minerActive ? "<gray>Status: <green>summoned</green>.</gray>" : "<gray>Status: <red>dismissed</red>.</gray>", "<gray>10% chance for natural ore to triple.</gray>", "<yellow>Click to manage.</yellow>")
                : List.of("<gray>Locked.</gray>", "<dark_gray>Complete Torren's mining trials.</dark_gray>"),
            ACTION_OPEN_MINER_PET
        );
        if (plugin.getMinerManager() != null) plugin.getMinerManager().applyMinerHeadTexture(minerIcon);
        inventory.setItem(PET_COLLECTION_MINER_SLOT, minerIcon);
        boolean farmerUnlocked = plugin.getFarmerManager() != null && plugin.getFarmerManager().hasTiller(player);
        boolean farmerActive = farmerUnlocked && plugin.getFarmerManager().hasActiveTiller(player);
        ItemStack farmerIcon = actionItem(
            Material.PLAYER_HEAD,
            farmerUnlocked ? "<green><bold>Tiller, the Sprout Mole</bold></green>" : "<dark_gray><bold>Tiller, the Sprout Mole</bold></dark_gray>",
            farmerUnlocked
                ? List.of(farmerActive ? "<gray>Status: <green>summoned</green>.</gray>" : "<gray>Status: <red>dismissed</red>.</gray>", "<gray>Crop, cooking, and hearty-food bonuses.</gray>", "<yellow>Click to manage.</yellow>")
                : List.of("<gray>Locked.</gray>", "<dark_gray>Complete Rowan's Harvest Banquet.</dark_gray>"),
            ACTION_OPEN_FARMER_PET
        );
        if (plugin.getFarmerManager() != null) plugin.getFarmerManager().applyTillerHeadTexture(farmerIcon);
        inventory.setItem(PET_COLLECTION_FARMER_SLOT, farmerIcon);
        boolean witchUnlocked = plugin.getWitchManager() != null && plugin.getWitchManager().hasMorrow(player);
        boolean witchActive = witchUnlocked && plugin.getWitchManager().hasActiveMorrow(player);
        ItemStack witchIcon = actionItem(
            Material.PLAYER_HEAD,
            witchUnlocked ? "<light_purple><bold>Morrow, the Rosy Moonmoth</bold></light_purple>" : "<dark_gray><bold>Morrow, the Rosy Moonmoth</bold></dark_gray>",
            witchUnlocked
                ? List.of(witchActive ? "<gray>Status: <green>summoned</green>.</gray>" : "<gray>Status: <red>dismissed</red>.</gray>", "<gray>Longer brews and rare valid potion upgrades.</gray>", "<yellow>Click to manage.</yellow>")
                : List.of("<gray>Locked.</gray>", "<dark_gray>Complete Vespera's Moonlit Thesis.</dark_gray>"),
            ACTION_OPEN_WITCH_PET
        );
        if (plugin.getWitchManager() != null) plugin.getWitchManager().applyMorrowHeadTexture(witchIcon);
        inventory.setItem(PET_COLLECTION_WITCH_SLOT, witchIcon);
        inventory.setItem(PET_COLLECTION_BACK_SLOT, actionItem(Material.ARROW, "<yellow><bold>Back</bold></yellow>", List.of("<gray>Return to /menu.</gray>"), ACTION_BACK_TO_MAIN_MENU));
        player.openInventory(inventory);
    }

    public void openPetMenu(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
            new MayorPetMenuHolder(player.getUniqueId()),
            PET_MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<light_purple><bold>Veil Wisp</bold></light_purple>"), "Veil Wisp")
        );
        fill(inventory);
        inventory.setItem(PET_STATUS_SLOT, petStatusItem(player));
        if (hasPetUnlocked(player)) {
            FamiliarMenuState state = FamiliarMenuState.from(hasActiveVeilWisp(player));
            inventory.setItem(PET_SPAWN_SLOT, actionItem(
                state.summonIcon(),
                state.canSummon() ? "<green><bold>Summon</bold></green>" : "<green><bold>Currently Summoned</bold></green>",
                List.of(
                    state.canSummon() ? "<gray>Bring your Veil Wisp back.</gray>" : "<gray>Your Veil Wisp is following you.</gray>",
                    "<dark_gray>Activates its Essence and boss-drop perks.</dark_gray>"
                ),
                state.summonAction(ACTION_SPAWN_PET)
            ));
            inventory.setItem(PET_DESPAWN_SLOT, actionItem(
                state.dismissIcon(),
                state.canDismiss() ? "<red><bold>Dismiss</bold></red>" : "<dark_gray><bold>Already Dismissed</bold></dark_gray>",
                List.of(
                    state.canDismiss() ? "<gray>Hide your Veil Wisp for now.</gray>" : "<gray>Your Veil Wisp is already resting.</gray>",
                    "<dark_gray>Pauses both perks until summoned again.</dark_gray>"
                ),
                state.dismissAction(ACTION_DESPAWN_PET)
            ));
        }
        inventory.setItem(PET_BACK_SLOT, actionItem(Material.ARROW, "<yellow><bold>Back</bold></yellow>", List.of("<gray>Return to your familiars.</gray>"), ACTION_BACK_TO_QUESTS));
        player.openInventory(inventory);
    }

    private void openMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new MayorQuestMenuHolder(player.getUniqueId()),
            MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#facc15:#38bdf8><bold>Mayor Bah</bold></gradient>"), "Mayor Bah")
        );
        fill(inventory);
        inventory.setItem(4, item(Material.EMERALD, "<gradient:#facc15:#38bdf8><bold>Season Orders</bold></gradient>", List.of(
            "<gray>Finish these in order.</gray>",
            "<gray>Boss kills count for fight participants.</gray>",
            "<dark_gray>Rewards build toward the Veil Wisp familiar.</dark_gray>"
        ), null));
        Set<String> completed = completedQuests(player);
        for (int i = 0; i < quests.size() && i < QUEST_SLOTS.length; i++) {
            inventory.setItem(QUEST_SLOTS[i], questIcon(player, quests.get(i), i, completed));
        }
        inventory.setItem(PET_SLOT, petInfoItem(player));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "<red><bold>Close</bold></red>", List.of("<gray>Close this menu.</gray>"), null));
        player.openInventory(inventory);
    }

    private String familiarSubtitle(Player player) {
        return player.getName() + "'s Familiar";
    }

    private ItemStack questIcon(Player player, QuestDefinition quest, int index, Set<String> completed) {
        boolean done = completed.contains(quest.id());
        boolean available = index == 0 || completed.contains(quests.get(index - 1).id());
        Material material = done ? Material.LIME_CONCRETE : available ? quest.icon() : Material.GRAY_STAINED_GLASS_PANE;
        String name = done
            ? "<green><bold>" + quest.title() + "</bold></green>"
            : available ? "<gold><bold>" + quest.title() + "</bold></gold>" : "<dark_gray><bold>Locked</bold></dark_gray>";
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + quest.summary() + "</gray>");
        lore.add("<dark_gray> ");
        lore.add("<aqua><bold>Requirements</bold></aqua>");
        for (Requirement requirement : quest.requirements()) {
            lore.add(requirement.progressLine(plugin, player));
        }
        lore.add("<dark_gray> ");
        if (done) {
            lore.add("<green>Completed.</green>");
        } else if (!available) {
            lore.add("<yellow>Finish the previous order first.</yellow>");
        } else if (quest.ready(plugin, player)) {
            lore.add("<green>Click to turn this in.</green>");
        } else {
            lore.add("<yellow>Come back when every line is complete.</yellow>");
        }
        return item(material, name, lore, done || !available ? null : quest.id());
    }

    private ItemStack petInfoItem(Player player) {
        boolean unlocked = hasPetUnlocked(player);
        boolean hidden = isPetHidden(player);
        List<String> lore = new ArrayList<>();
        lore.add(unlocked ? "<gray>" + familiarSubtitle(player) + "</gray>" : "<dark_gray>Mayor Bah's final reward.</dark_gray>");
        lore.add(unlocked
            ? (hidden ? "<gray>Status: <red>dismissed</red>.</gray>" : "<gray>Status: <green>summoned</green>.</gray>")
            : "<gray>Complete all mayor quests to unlock it.</gray>");
        lore.addAll(veilWispPerkLore());
        lore.add(unlocked ? "<dark_gray>Use /familiar to manage it.</dark_gray>" : "<dark_gray>Perks work only while the Wisp is summoned.</dark_gray>");
        ItemStack icon = item(
            Material.PLAYER_HEAD,
            unlocked ? "<light_purple><bold>Veil Wisp</bold></light_purple>" : "<dark_purple><bold>Locked Familiar</bold></dark_purple>",
            lore,
            null
        );
        return applyVeilWispHeadTexture(icon);
    }

    private ItemStack petCollectionWispItem(Player player) {
        boolean unlocked = hasPetUnlocked(player);
        boolean active = unlocked && hasActiveVeilWisp(player);
        List<String> lore = new ArrayList<>();
        if (unlocked) {
            lore.add("<gray>" + familiarSubtitle(player) + "</gray>");
            lore.add(active ? "<gray>Status: <green>summoned</green>.</gray>" : "<gray>Status: <red>dismissed</red>.</gray>");
            lore.addAll(veilWispPerkLore());
            lore.add("<yellow>Click to manage.</yellow>");
        } else {
            lore.add("<gray>Locked.</gray>");
            lore.add("<dark_gray>Complete Mayor Bah's last order.</dark_gray>");
        }
        ItemStack icon = item(
            Material.PLAYER_HEAD,
            unlocked ? "<light_purple><bold>Veil Wisp</bold></light_purple>" : "<dark_purple><bold>Locked Familiar</bold></dark_purple>",
            lore,
            ACTION_OPEN_WISP_PET
        );
        return applyVeilWispHeadTexture(icon);
    }

    private ItemStack petStatusItem(Player player) {
        boolean unlocked = hasPetUnlocked(player);
        List<String> lore = new ArrayList<>();
        if (!unlocked) {
            lore.add("<gray>Complete every Mayor Bah order to unlock this.</gray>");
            lore.add("<dark_gray>Admins can also use /mayorfamiliar give.</dark_gray>");
        } else if (isPetHidden(player)) {
            lore.add("<gray>" + familiarSubtitle(player) + "</gray>");
            lore.add("<gray>Status: <red>dismissed</red>.</gray>");
            lore.add("<gray>Summon it to activate both perks below.</gray>");
            lore.addAll(veilWispPerkLore());
        } else {
            lore.add("<gray>" + familiarSubtitle(player) + "</gray>");
            lore.add("<gray>Status: <green>summoned</green>.</gray>");
            lore.addAll(veilWispPerkLore());
        }
        lore.add("<dark_gray>Only one familiar can be active per player.</dark_gray>");
        ItemStack icon = item(
            Material.PLAYER_HEAD,
            unlocked ? "<light_purple><bold>Veil Wisp</bold></light_purple>" : "<dark_purple><bold>Locked Familiar</bold></dark_purple>",
            lore,
            null
        );
        return applyVeilWispHeadTexture(icon);
    }

    private List<String> veilWispPerkLore() {
        return List.of(
            "<light_purple><bold>While Summoned</bold></light_purple>",
            "<gray>Essence: <white>earn 50% faster</white>.</gray>",
            "<dark_gray>Applies to mining, mobs, XP, and PvP.</dark_gray>",
            "<gray>Boss drops: <white>51% shared chance to double</white>.</gray>",
            "<dark_gray>One roll per fight; +1% per extra active Wisp.</dark_gray>"
        );
    }

    private void tryCompleteQuest(Player player, String questId) {
        QuestDefinition quest = questById(questId);
        if (quest == null) {
            return;
        }
        int index = quests.indexOf(quest);
        Set<String> completed = completedQuests(player);
        if (completed.contains(quest.id())) {
            player.sendMessage(MessageUtil.info("That order is already complete."));
            openMenu(player);
            return;
        }
        if (index > 0 && !completed.contains(quests.get(index - 1).id())) {
            player.sendMessage(MessageUtil.warn("Finish the previous order first."));
            openMenu(player);
            return;
        }
        if (!quest.ready(plugin, player)) {
            player.sendMessage(MessageUtil.warn("You are still missing something for that order."));
            openMenu(player);
            return;
        }
        if (!quest.consume(plugin, player)) {
            player.sendMessage(MessageUtil.error("Turn-in failed because the items changed. Try again."));
            openMenu(player);
            return;
        }
        completed.add(quest.id());
        saveCompleted(player, completed);
        if (plugin.getEssenceManager() != null && quest.essenceReward() > 0) {
            plugin.getEssenceManager().addByAdmin(player, quest.essenceReward(), null);
        }
        if (quest.finalQuest()) {
            unlockPet(player);
        }
        if (plugin.getStoryService() != null) {
            plugin.getStoryService().onQuestStage(player, "mayor", quest.id(), index + 1);
            if (quest.finalQuest()) plugin.getStoryService().onFamiliarUnlocked(player, "veil_wisp");
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.05f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0, 1.0, 0.0), 28, 0.4, 0.5, 0.4, 0.02);
        player.sendMessage(MessageUtil.success("Mayor order complete: <white>" + quest.title() + "</white>."));
        openMenu(player);
    }

    private void tickPets() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hasPetUnlocked(player) || isPetHidden(player) || player.isDead()) {
                removePet(player.getUniqueId());
                continue;
            }
            Entity pet = currentPet(player);
            if (pet == null) {
                spawnPetIfUnlocked(player);
                pet = currentPet(player);
            }
            if (pet == null) {
                continue;
            }
            FamiliarMotion.State motion = petMotionStates.computeIfAbsent(
                player.getUniqueId(),
                ignored -> new FamiliarMotion.State(player.getLocation().getYaw())
            );
            Location target = FamiliarMotion.target(player, motion, pet.getLocation());
            FamiliarMotion.move(pet, target);
            movePetBedrockBody(player, target);
            movePetHitbox(player, target);
            movePetLabels(player, target);
            motion.advance();
            if (motion.ticks() % FamiliarMotion.PARTICLE_INTERVAL_TICKS == 0) {
                player.spawnParticle(Particle.END_ROD, target, 1, 0.07, 0.07, 0.07, 0.0);
            }
        }
    }

    private void spawnPetIfUnlocked(Player player) {
        if (player == null || !player.isOnline() || !hasPetUnlocked(player) || isPetHidden(player)) {
            return;
        }
        if (currentPet(player) != null) {
            return;
        }
        removePet(player.getUniqueId());
        FamiliarMotion.State motion = petMotionStates.compute(player.getUniqueId(), (ignored, existing) -> {
            if (existing == null) {
                return new FamiliarMotion.State(player.getLocation().getYaw());
            }
            existing.reset(player.getLocation().getYaw());
            return existing;
        });
        Location location = FamiliarMotion.target(player, motion, null);
        ItemDisplay pet = location.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(petHead());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(VisualRangeUtil.blocksToDisplayViewRange(32.0D));
            FamiliarMotion.configureDisplay(display, FamiliarMotion.TELEPORT_DURATION_TICKS);
            display.setGravity(false);
            display.setSilent(true);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.customName(Component.empty());
            display.setCustomNameVisible(false);
            display.getPersistentDataContainer().set(keyPetOwner, PersistentDataType.STRING, player.getUniqueId().toString());
        });
        activePets.put(player.getUniqueId(), pet.getUniqueId());
        registerJavaVisual(pet);
        spawnPetBedrockBody(player, location);
        Interaction hitbox = spawnPetHitbox(player, location);
        activePetHitboxes.put(player.getUniqueId(), hitbox.getUniqueId());
        spawnPetLabels(player, location);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.55f);
    }

    private Entity currentPet(Player player) {
        UUID petId = activePets.get(player.getUniqueId());
        Entity entity = petId == null ? null : Bukkit.getEntity(petId);
        if (entity != null && entity.isValid()) {
            return entity;
        }
        activePets.remove(player.getUniqueId());
        removePetHitbox(player.getUniqueId());
        removePetBedrockBody(player.getUniqueId());
        removePetLabels(player.getUniqueId());
        return null;
    }

    private void removePet(UUID playerId) {
        UUID petId = activePets.remove(playerId);
        removeMappedEntity(petId);
        removePetBedrockBody(playerId);
        removePetHitbox(playerId);
        removePetLabels(playerId);
        petMotionStates.remove(playerId);
    }

    private void removePetHitbox(UUID playerId) {
        UUID hitboxId = activePetHitboxes.remove(playerId);
        removeMappedEntity(hitboxId);
    }

    private void removePetBedrockBody(UUID playerId) {
        removeMappedEntity(activePetBedrockBodies.remove(playerId));
    }

    private void removePetLabels(UUID playerId) {
        removeMappedEntity(activePetTitleLabels.remove(playerId));
        removeMappedEntity(activePetSubtitleLabels.remove(playerId));
    }

    private void removeMappedEntity(UUID entityId) {
        BedrockFamiliarVisibilityManager visibility = plugin.getBedrockFamiliarVisibilityManager();
        if (visibility != null) visibility.unregisterVisual(entityId);
        Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
        if (entity != null) {
            entity.remove();
        }
    }

    private void removeOrphanedPetEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String stored = entity.getPersistentDataContainer().get(keyPetOwner, PersistentDataType.STRING);
                if (stored != null) entity.remove();
            }
        }
        activePets.clear();
        activePetBedrockBodies.clear();
        activePetHitboxes.clear();
        activePetTitleLabels.clear();
        activePetSubtitleLabels.clear();
        petMotionStates.clear();
    }

    private ItemStack petHead() {
        return veilWispHead.clone();
    }

    private void spawnPetBedrockBody(Player owner, Location visualLocation) {
        BedrockFamiliarVisibilityManager visibility = plugin.getBedrockFamiliarVisibilityManager();
        if (visibility == null) return;
        ArmorStand body = FamiliarBedrockBody.spawn(
            visualLocation,
            petHead(),
            Component.text(owner.getName() + "'s Veil Wisp", NamedTextColor.LIGHT_PURPLE),
            entity -> entity.getPersistentDataContainer().set(
                keyPetOwner,
                PersistentDataType.STRING,
                owner.getUniqueId().toString()
            )
        );
        activePetBedrockBodies.put(owner.getUniqueId(), body.getUniqueId());
        visibility.registerBedrockVisual(body);
    }

    private void movePetBedrockBody(Player owner, Location visualTarget) {
        BedrockFamiliarVisibilityManager visibility = plugin.getBedrockFamiliarVisibilityManager();
        if (visibility == null) {
            removePetBedrockBody(owner.getUniqueId());
            return;
        }
        UUID bodyId = activePetBedrockBodies.get(owner.getUniqueId());
        Entity body = bodyId == null ? null : Bukkit.getEntity(bodyId);
        if (!(body instanceof ArmorStand) || !body.isValid()) {
            removeMappedEntity(bodyId);
            spawnPetBedrockBody(owner, visualTarget);
            return;
        }
        FamiliarBedrockBody.move(body, visualTarget);
    }

    private void registerJavaVisual(Entity entity) {
        BedrockFamiliarVisibilityManager visibility = plugin.getBedrockFamiliarVisibilityManager();
        if (visibility != null) visibility.registerJavaVisual(entity);
    }

    private ItemStack createVeilWispHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        applyVeilWispHeadTexture(head);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Veil Wisp", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            head.setItemMeta(meta);
        }
        return head;
    }

    public ItemStack applyVeilWispHeadTexture(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = Bukkit.createProfile(VEIL_WISP_PROFILE_ID, "VeilWisp");
            profile.setProperty(new ProfileProperty("textures", VEIL_WISP_TEXTURE_VALUE));
            skullMeta.setPlayerProfile(profile);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private Interaction spawnPetHitbox(Player owner, Location visualLocation) {
        Location hitboxLocation = petHitboxLocation(visualLocation);
        return hitboxLocation.getWorld().spawn(hitboxLocation, Interaction.class, interaction -> {
            interaction.setInteractionWidth(0.42F);
            interaction.setInteractionHeight(0.50F);
            interaction.setResponsive(true);
            interaction.setGravity(false);
            interaction.setSilent(true);
            interaction.setInvulnerable(true);
            interaction.setPersistent(false);
            interaction.getPersistentDataContainer().set(keyPetOwner, PersistentDataType.STRING, owner.getUniqueId().toString());
        });
    }

    private void movePetHitbox(Player owner, Location visualTarget) {
        if (isBossFightNearby(owner)) {
            removePetHitbox(owner.getUniqueId());
            return;
        }
        UUID hitboxId = activePetHitboxes.get(owner.getUniqueId());
        Entity hitbox = hitboxId == null ? null : Bukkit.getEntity(hitboxId);
        Location target = petHitboxLocation(visualTarget);
        if (!(hitbox instanceof Interaction) || !hitbox.isValid() || !hitbox.getWorld().equals(target.getWorld())) {
            if (hitbox != null) {
                hitbox.remove();
            }
            Interaction newHitbox = spawnPetHitbox(owner, visualTarget);
            activePetHitboxes.put(owner.getUniqueId(), newHitbox.getUniqueId());
            return;
        }
        hitbox.teleport(target);
    }

    private Location petHitboxLocation(Location visualLocation) {
        return visualLocation.clone().add(0.0D, -0.18D, 0.0D);
    }

    private void spawnPetLabels(Player owner, Location visualLocation) {
        TextDisplay title = spawnPetLabel(
            owner,
            petLabelLocation(visualLocation, PET_LABEL_TITLE_OFFSET),
            petTitleText(),
            (byte) 0xFF
        );
        TextDisplay subtitle = spawnPetLabel(
            owner,
            petLabelLocation(visualLocation, PET_LABEL_SUBTITLE_OFFSET),
            petSubtitleText(owner),
            (byte) 0xE6
        );
        activePetTitleLabels.put(owner.getUniqueId(), title.getUniqueId());
        activePetSubtitleLabels.put(owner.getUniqueId(), subtitle.getUniqueId());
    }

    private TextDisplay spawnPetLabel(Player owner, Location location, Component text, byte opacity) {
        TextDisplay label = location.getWorld().spawn(location, TextDisplay.class, display -> {
            display.text(text.decoration(TextDecoration.ITALIC, false));
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(VisualRangeUtil.blocksToDisplayViewRange(32.0D));
            FamiliarMotion.configureDisplay(display, FamiliarMotion.TELEPORT_DURATION_TICKS);
            display.setGravity(false);
            display.setSilent(true);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setLineWidth(180);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setTextOpacity(opacity);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.getPersistentDataContainer().set(keyPetOwner, PersistentDataType.STRING, owner.getUniqueId().toString());
        });
        registerJavaVisual(label);
        return label;
    }

    private void movePetLabels(Player owner, Location visualTarget) {
        movePetLabel(owner, activePetTitleLabels, petLabelLocation(visualTarget, PET_LABEL_TITLE_OFFSET), petTitleText(), (byte) 0xFF);
        movePetLabel(owner, activePetSubtitleLabels, petLabelLocation(visualTarget, PET_LABEL_SUBTITLE_OFFSET), petSubtitleText(owner), (byte) 0xE6);
    }

    private void movePetLabel(Player owner, Map<UUID, UUID> labels, Location target, Component text, byte opacity) {
        UUID labelId = labels.get(owner.getUniqueId());
        Entity entity = labelId == null ? null : Bukkit.getEntity(labelId);
        if (!(entity instanceof TextDisplay label) || !label.isValid() || !label.getWorld().equals(target.getWorld())) {
            if (entity != null) {
                entity.remove();
            }
            TextDisplay newLabel = spawnPetLabel(owner, target, text, opacity);
            labels.put(owner.getUniqueId(), newLabel.getUniqueId());
            return;
        }
        label.text(text.decoration(TextDecoration.ITALIC, false));
        FamiliarMotion.configureDisplay(label, FamiliarMotion.TELEPORT_DURATION_TICKS);
        label.teleport(target);
    }

    private Component petTitleText() {
        return Component.text("Veil Wisp", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD);
    }

    private Component petSubtitleText(Player owner) {
        return Component.text(familiarSubtitle(owner), NamedTextColor.GRAY);
    }

    private Location petLabelLocation(Location visualLocation, double yOffset) {
        return visualLocation.clone().add(0.0D, yOffset, 0.0D);
    }

    private UUID petOwnerId(Entity entity) {
        if (entity == null) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(keyPetOwner, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void playPetInteraction(Player player, Entity petEntity, UUID ownerId) {
        Location center = petEntity.getLocation().add(0.0D, 0.45D, 0.0D);
        player.sendMessage(MessageUtil.info("The <light_purple>Veil Wisp</light_purple> circles your hand and chimes happily."));
        center.getWorld().playSound(center, Sound.ENTITY_AXOLOTL_IDLE_WATER, 0.75F, 1.45F);
        center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.75F);
        center.getWorld().spawnParticle(Particle.HEART, center, 3, 0.18D, 0.14D, 0.18D, 0.01D);
        center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 5, 0.22D, 0.18D, 0.22D, 0.02D);
        center.getWorld().spawnParticle(Particle.WAX_ON, center, 8, 0.24D, 0.2D, 0.24D, 0.015D);

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline() && !owner.getUniqueId().equals(player.getUniqueId())
            && owner.getWorld().equals(player.getWorld()) && owner.getLocation().distanceSquared(player.getLocation()) <= 100.0D) {
            owner.sendMessage(MessageUtil.info(player.getName() + " gave your fairy a pat."));
        }
    }

    private boolean isBossFightNearby(Player player) {
        if (player == null || plugin.getBossManager() == null) {
            return false;
        }
        for (Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), 30.0D, 18.0D, 30.0D)) {
            if (plugin.getBossManager().isCustomBoss(nearby)) {
                return true;
            }
        }
        return false;
    }

    private void unlockPet(Player player) {
        player.getPersistentDataContainer().set(keyPetUnlocked, PersistentDataType.BYTE, (byte) 1);
        selectVeilWisp(player);
        spawnPetIfUnlocked(player);
        player.sendMessage(MessageUtil.success("Mayor Bah awarded you the <white>Veil Wisp</white> familiar."));
    }

    public void grantPet(Player player, CommandSender actor) {
        if (player == null) {
            return;
        }
        boolean alreadyUnlocked = hasPetUnlocked(player);
        player.getPersistentDataContainer().set(keyPetUnlocked, PersistentDataType.BYTE, (byte) 1);
        selectVeilWisp(player);
        spawnPetIfUnlocked(player);
        if (!alreadyUnlocked) {
            player.sendMessage(MessageUtil.success("You unlocked the <white>Veil Wisp</white> familiar."));
        } else {
            player.sendMessage(MessageUtil.info("Your <white>Veil Wisp</white> familiar was restored."));
        }
        if (actor != null && !actor.equals(player)) {
            actor.sendMessage(MessageUtil.success("Gave <white>" + player.getName() + "</white> the Veil Wisp familiar."));
        }
    }

    public void revokePet(Player player, CommandSender actor) {
        if (player == null) {
            return;
        }
        player.getPersistentDataContainer().remove(keyPetUnlocked);
        player.getPersistentDataContainer().remove(keyPetHidden);
        if (VEIL_WISP_FAMILIAR_ID.equals(activeFamiliar(player))) player.getPersistentDataContainer().remove(keyActiveFamiliar);
        removePet(player.getUniqueId());
        player.sendMessage(MessageUtil.warn("Your <white>Veil Wisp</white> familiar was removed."));
        if (actor != null && !actor.equals(player)) {
            actor.sendMessage(MessageUtil.success("Removed <white>" + player.getName() + "</white>'s Veil Wisp familiar."));
        }
    }

    public boolean hasPetUnlocked(Player player) {
        Byte value = player.getPersistentDataContainer().get(keyPetUnlocked, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private boolean isPetHidden(Player player) {
        ensureActiveFamiliar(player);
        Byte value = player.getPersistentDataContainer().get(keyPetHidden, PersistentDataType.BYTE);
        return value != null && value == (byte) 1 || !VEIL_WISP_FAMILIAR_ID.equals(activeFamiliar(player));
    }

    private boolean isPetActive(Player player) {
        return hasPetUnlocked(player) && !isPetHidden(player) && currentPet(player) != null;
    }

    public boolean hasActiveVeilWisp(Player player) {
        if (player == null || !player.isOnline() || player.isDead() || !hasPetUnlocked(player) || isPetHidden(player)) {
            return false;
        }
        if (currentPet(player) == null) {
            spawnPetIfUnlocked(player);
        }
        return currentPet(player) != null;
    }

    public int activeVeilWispCount(Collection<UUID> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        Set<UUID> seen = new HashSet<>();
        for (UUID playerId : playerIds) {
            if (playerId == null || !seen.add(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (hasActiveVeilWisp(player)) {
                count++;
            }
        }
        return count;
    }

    private void setPetHidden(Player player, boolean hidden) {
        if (hidden) {
            player.getPersistentDataContainer().set(keyPetHidden, PersistentDataType.BYTE, (byte) 1);
            if (VEIL_WISP_FAMILIAR_ID.equals(activeFamiliar(player))) player.getPersistentDataContainer().remove(keyActiveFamiliar);
            removePet(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.55f, 0.7f);
            player.sendMessage(MessageUtil.info("Veil Wisp familiar dismissed."));
            return;
        }
        selectVeilWisp(player);
        spawnPetIfUnlocked(player);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.45f);
        player.sendMessage(MessageUtil.success("Veil Wisp familiar summoned."));
    }

    public void deactivateForFamiliarSwitch(Player player) {
        if (player == null) return;
        player.getPersistentDataContainer().set(keyPetHidden, PersistentDataType.BYTE, (byte) 1);
        removePet(player.getUniqueId());
    }

    private void selectVeilWisp(Player player) {
        if (plugin.getMinerManager() != null) plugin.getMinerManager().deactivateForFamiliarSwitch(player);
        if (plugin.getFarmerManager() != null) plugin.getFarmerManager().deactivateForFamiliarSwitch(player);
        if (plugin.getWitchManager() != null) plugin.getWitchManager().deactivateForFamiliarSwitch(player);
        player.getPersistentDataContainer().set(keyActiveFamiliar, PersistentDataType.STRING, VEIL_WISP_FAMILIAR_ID);
        player.getPersistentDataContainer().remove(keyPetHidden);
    }

    private void ensureActiveFamiliar(Player player) {
        if (activeFamiliar(player) != null) return;
        Byte hidden = player.getPersistentDataContainer().get(keyPetHidden, PersistentDataType.BYTE);
        if (hasPetUnlocked(player) && (hidden == null || hidden != (byte) 1)) {
            player.getPersistentDataContainer().set(keyActiveFamiliar, PersistentDataType.STRING, VEIL_WISP_FAMILIAR_ID);
        }
    }

    private String activeFamiliar(Player player) {
        return player.getPersistentDataContainer().get(keyActiveFamiliar, PersistentDataType.STRING);
    }

    private Set<String> completedQuests(Player player) {
        String raw = player.getPersistentDataContainer().get(keyCompleted, PersistentDataType.STRING);
        Set<String> completed = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return completed;
        }
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (!id.isBlank()) {
                completed.add(id);
            }
        }
        return completed;
    }

    public Set<String> completedQuestIds(Player player) {
        return player == null ? Set.of() : Set.copyOf(completedQuests(player));
    }

    private void saveCompleted(Player player, Set<String> completed) {
        player.getPersistentDataContainer().set(keyCompleted, PersistentDataType.STRING, String.join(",", completed));
    }

    private void applyBossCredit(Player player, String key, int amount, boolean notify) {
        if (player == null || amount <= 0) {
            return;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey namespacedKey = keyFor(key);
        int current = pdc.getOrDefault(namespacedKey, PersistentDataType.INTEGER, 0);
        pdc.set(namespacedKey, PersistentDataType.INTEGER, Math.min(1_000_000, current + amount));
        if (notify) {
            player.sendActionBar(MM.deserialize("<gold>Mayor quest progress updated.</gold>"));
        }
    }

    private void applyPendingBossCredits(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Map<String, Integer> credits = pendingBossCredits.remove(player.getUniqueId());
        if (credits == null || credits.isEmpty()) {
            return;
        }
        int applied = 0;
        for (Map.Entry<String, Integer> entry : credits.entrySet()) {
            int amount = Math.max(0, entry.getValue());
            if (amount <= 0) {
                continue;
            }
            applyBossCredit(player, entry.getKey(), amount, false);
            applied += amount;
        }
        savePendingBossCredits();
        if (applied > 0) {
            player.sendActionBar(MM.deserialize("<gold>Mayor quest progress updated.</gold>"));
        }
    }

    private boolean queuePendingBossCredit(UUID playerId, String key, int amount) {
        if (playerId == null || key == null || key.isBlank() || amount <= 0) {
            return false;
        }
        pendingBossCredits.compute(playerId, (ignored, existing) -> {
            Map<String, Integer> credits = existing == null ? new HashMap<>() : new HashMap<>(existing);
            int current = credits.getOrDefault(key, 0);
            credits.put(key, Math.min(1_000_000, current + amount));
            return credits;
        });
        return true;
    }

    private void loadPendingBossCredits() {
        pendingBossCredits.clear();
        if (!pendingCreditsFile.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(pendingCreditsFile);
        ConfigurationSection root = config.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String playerKey : root.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(playerKey);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ConfigurationSection playerSection = root.getConfigurationSection(playerKey);
            if (playerSection == null) {
                continue;
            }
            Map<String, Integer> credits = new HashMap<>();
            for (String key : playerSection.getKeys(false)) {
                int amount = playerSection.getInt(key, 0);
                if (amount > 0) {
                    credits.put(key, Math.min(1_000_000, amount));
                }
            }
            if (!credits.isEmpty()) {
                pendingBossCredits.put(playerId, credits);
            }
        }
    }

    private void savePendingBossCredits() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Integer>> entry : pendingBossCredits.entrySet()) {
            for (Map.Entry<String, Integer> credit : entry.getValue().entrySet()) {
                int amount = Math.max(0, credit.getValue());
                if (amount > 0) {
                    config.set("players." + entry.getKey() + "." + credit.getKey(), amount);
                }
            }
        }
        File parent = pendingCreditsFile.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        File temporary = new File(parent, pendingCreditsFile.getName() + ".next");
        try {
            config.save(temporary);
            try {
                Files.move(
                    temporary.toPath(),
                    pendingCreditsFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary.toPath(), pendingCreditsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException ignored) {
            }
            plugin.getLogger().warning("Could not save pending Mayor quest credits: " + ex.getMessage());
        }
    }

    private QuestDefinition questById(String questId) {
        for (QuestDefinition quest : quests) {
            if (quest.id().equals(questId)) {
                return quest;
            }
        }
        return null;
    }

    private String questId(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(keyMenuQuest, PersistentDataType.STRING);
    }

    private String menuAction(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(keyMenuAction, PersistentDataType.STRING);
    }

    private void handlePetAction(Player player, String action) {
        if (ACTION_BACK_TO_QUESTS.equals(action)) {
            openPetCollectionMenu(player);
            return;
        }
        if (!hasPetUnlocked(player)) {
            player.sendMessage(MessageUtil.warn("Complete Mayor Bah's orders to unlock the Veil Wisp familiar."));
            openPetMenu(player);
            return;
        }
        if (ACTION_SPAWN_PET.equals(action)) {
            if (!hasActiveVeilWisp(player)) setPetHidden(player, false);
            openPetMenu(player);
            return;
        }
        if (ACTION_DESPAWN_PET.equals(action)) {
            if (hasActiveVeilWisp(player)) setPetHidden(player, true);
            openPetMenu(player);
        }
    }

    private void handlePetCollectionAction(Player player, String action) {
        if (ACTION_BACK_TO_MAIN_MENU.equals(action)) {
            MainMenuCommand.openMenu(plugin, player);
            return;
        }
        if (ACTION_OPEN_MINER_PET.equals(action)) {
            if (plugin.getMinerManager() != null) {
                plugin.getMinerManager().openPetMenu(player);
            }
            return;
        }
        if (ACTION_OPEN_WISP_PET.equals(action)) {
            openPetMenu(player);
            return;
        }
        if (ACTION_OPEN_FARMER_PET.equals(action)) {
            if (plugin.getFarmerManager() != null) plugin.getFarmerManager().openPetMenu(player);
            return;
        }
        if (ACTION_OPEN_WITCH_PET.equals(action)) {
            if (plugin.getWitchManager() != null) plugin.getWitchManager().openPetMenu(player);
            return;
        }
        if (!hasPetUnlocked(player)) {
            return;
        }
        if (ACTION_SPAWN_PET.equals(action)) {
            if (!hasActiveVeilWisp(player)) setPetHidden(player, false);
            openPetCollectionMenu(player);
            return;
        }
        if (ACTION_DESPAWN_PET.equals(action)) {
            if (hasActiveVeilWisp(player)) setPetHidden(player, true);
            openPetCollectionMenu(player);
        }
    }

    private boolean holderBelongsToPlayer(InventoryHolder holder, Player player) {
        UUID playerId = player.getUniqueId();
        if (holder instanceof MayorPetMenuHolder petHolder) {
            return playerId.equals(petHolder.playerId());
        }
        if (holder instanceof MayorPetCollectionMenuHolder collectionHolder) {
            return playerId.equals(collectionHolder.playerId());
        }
        return true;
    }

    private ItemStack item(Material material, String name, List<String> loreLines, String questId) {
        return item(material, name, loreLines, questId, null);
    }

    private ItemStack actionItem(Material material, String name, List<String> loreLines, String action) {
        return item(material, name, loreLines, null, action);
    }

    private ItemStack item(Material material, String name, List<String> loreLines, String questId, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)).decoration(TextDecoration.ITALIC, false));
        meta.lore(MenuItemUtil.visibleMiniLore(name, loreLines).stream()
            .map(line -> line == null || line.isBlank() ? Component.empty() : MM.deserialize(line).decoration(TextDecoration.ITALIC, false))
            .toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);
        if (questId != null) {
            meta.getPersistentDataContainer().set(keyMenuQuest, PersistentDataType.STRING, questId);
        }
        if (action != null) {
            meta.getPersistentDataContainer().set(keyMenuAction, PersistentDataType.STRING, action);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE, null);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private List<QuestDefinition> buildQuests() {
        return List.of(
            new QuestDefinition(
                "veil_supplies",
                "Veil Supplies",
                Material.SCULK,
                "Bring the materials spawn needs for the Veil season.",
                List.of(new MaterialRequirement(Material.SCULK, 32), new MaterialRequirement(Material.AMETHYST_SHARD, 16)),
                75,
                false
            ),
            new QuestDefinition(
                "marshal_proof",
                "Marshal's Proof",
                Material.GOLDEN_SWORD,
                "Defeat the Veilbound Marshal once.",
                List.of(new BossRequirement("yule_the_minion", "Veilbound Marshal", 1)),
                125,
                false
            ),
            new QuestDefinition(
                "gloam_cinders",
                "Gloam Cinders",
                Material.BLAZE_POWDER,
                "Turn in early boss materials from ash and silk.",
                List.of(new RelicRequirement("solar_ember", 1), new RelicRequirement("widow_silk", 1)),
                175,
                false
            ),
            new QuestDefinition(
                "rift_witness",
                "Rift Witness",
                Material.ENDER_EYE,
                "Survive and defeat Asterion in the End.",
                List.of(new BossRequirement("aurelion_the_rift_seraph", "Asterion", 1)),
                250,
                false
            ),
            new QuestDefinition(
                "argent_briar",
                "Argent Briar",
                Material.ANVIL,
                "Bring proof from the Confessor and the Regent.",
                List.of(new RelicRequirement("titan_gear", 1), new RelicRequirement("living_bark", 1)),
                350,
                false
            ),
            new QuestDefinition(
                "oathkeeper_pact",
                "Oathkeeper Pact",
                Material.MAGMA_CREAM,
                "Break the Corrupted Oathkeeper and bring back its essence.",
                List.of(new BossRequirement("corrupted_oathkeeper", "Corrupted Oathkeeper", 1), new RelicRequirement("corrupted_essence", 1)),
                500,
                true
            )
        );
    }

    private NamespacedKey keyFor(String id) {
        return new NamespacedKey(plugin, id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
    }

    private String bossKillKey(String bossId) {
        return "mayor_boss_" + bossId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private String prettyMaterial(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private interface Requirement {
        boolean met(SMPCore plugin, Player player);

        boolean consume(SMPCore plugin, Player player);

        String progressLine(SMPCore plugin, Player player);
    }

    private record MaterialRequirement(Material material, int amount) implements Requirement {
        @Override
        public boolean met(SMPCore plugin, Player player) {
            return count(player, material) >= amount;
        }

        @Override
        public boolean consume(SMPCore plugin, Player player) {
            return remove(player, material, amount);
        }

        @Override
        public String progressLine(SMPCore plugin, Player player) {
            int count = count(player, material);
            return (count >= amount ? "<green>" : "<gray>") + pretty(plugin, material) + ": <white>" + Math.min(count, amount) + "/" + amount + "</white>";
        }

        private static int count(Player player, Material material) {
            int count = 0;
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (isPlainMaterial(item, material)) {
                    count += item.getAmount();
                }
            }
            return count;
        }

        private static boolean remove(Player player, Material material, int amount) {
            if (count(player, material) < amount) {
                return false;
            }
            int remaining = amount;
            for (int slot = 0; slot < player.getInventory().getStorageContents().length && remaining > 0; slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                if (!isPlainMaterial(item, material)) {
                    continue;
                }
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
                if (item.getAmount() <= 0) {
                    player.getInventory().setItem(slot, null);
                }
            }
            player.updateInventory();
            return remaining <= 0;
        }

        private static boolean isPlainMaterial(ItemStack item, Material material) {
            if (item == null || item.getType() != material || item.getAmount() <= 0) {
                return false;
            }
            ItemMeta meta = item.getItemMeta();
            return meta == null || meta.getPersistentDataContainer().getKeys().stream()
                .noneMatch(key -> "smpcore".equalsIgnoreCase(key.getNamespace()));
        }

        private static String pretty(SMPCore plugin, Material material) {
            return plugin.getMayorQuestManager().prettyMaterial(material);
        }
    }

    private record RelicRequirement(String relicId, int amount) implements Requirement {
        @Override
        public boolean met(SMPCore plugin, Player player) {
            return count(plugin, player, relicId) >= amount;
        }

        @Override
        public boolean consume(SMPCore plugin, Player player) {
            return remove(plugin, player, relicId, amount);
        }

        @Override
        public String progressLine(SMPCore plugin, Player player) {
            int count = count(plugin, player, relicId);
            String name = plugin.getSeasonRelicManager() == null ? relicId : plugin.getSeasonRelicManager().displayNameFor(relicId);
            if (name == null || name.isBlank()) {
                name = relicId;
            }
            return (count >= amount ? "<green>" : "<gray>") + name + ": <white>" + Math.min(count, amount) + "/" + amount + "</white>";
        }

        private static int count(SMPCore plugin, Player player, String relicId) {
            if (plugin.getSeasonRelicManager() == null) {
                return 0;
            }
            int count = 0;
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (item != null && relicId.equals(plugin.getSeasonRelicManager().relicId(item))) {
                    count += item.getAmount();
                }
            }
            return count;
        }

        private static boolean remove(SMPCore plugin, Player player, String relicId, int amount) {
            if (count(plugin, player, relicId) < amount) {
                return false;
            }
            int remaining = amount;
            for (int slot = 0; slot < player.getInventory().getStorageContents().length && remaining > 0; slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                if (item == null || !relicId.equals(plugin.getSeasonRelicManager().relicId(item))) {
                    continue;
                }
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
                if (item.getAmount() <= 0) {
                    player.getInventory().setItem(slot, null);
                }
            }
            player.updateInventory();
            return remaining <= 0;
        }
    }

    private record BossRequirement(String bossId, String displayName, int amount) implements Requirement {
        @Override
        public boolean met(SMPCore plugin, Player player) {
            return count(plugin, player) >= amount;
        }

        @Override
        public boolean consume(SMPCore plugin, Player player) {
            return true;
        }

        @Override
        public String progressLine(SMPCore plugin, Player player) {
            int count = count(plugin, player);
            return (count >= amount ? "<green>" : "<gray>") + "Defeat " + displayName + ": <white>" + Math.min(count, amount) + "/" + amount + "</white>";
        }

        private int count(SMPCore plugin, Player player) {
            NamespacedKey key = plugin.getMayorQuestManager().keyFor(plugin.getMayorQuestManager().bossKillKey(bossId));
            return player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
        }
    }

    private record QuestDefinition(String id, String title, Material icon, String summary, List<Requirement> requirements, int essenceReward, boolean finalQuest) {
        private boolean ready(SMPCore plugin, Player player) {
            for (Requirement requirement : requirements) {
                if (!requirement.met(plugin, player)) {
                    return false;
                }
            }
            return true;
        }

        private boolean consume(SMPCore plugin, Player player) {
            for (Requirement requirement : requirements) {
                if (!requirement.met(plugin, player)) {
                    return false;
                }
            }
            for (Requirement requirement : requirements) {
                if (!requirement.consume(plugin, player)) {
                    return false;
                }
            }
            return true;
        }
    }

    private record MayorQuestMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record MayorPetMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record MayorPetCollectionMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
