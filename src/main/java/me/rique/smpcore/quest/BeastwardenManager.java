package me.rique.smpcore.quest;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TraderLlama;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class BeastwardenManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int QUEST_MENU_SIZE = 54;
    private static final int TREE_MENU_SIZE = 54;
    private static final int[] QUEST_SLOTS = {10, 11, 12, 13, 14, 15, 16, 22};
    private static final double ARMOR_MOB_DAMAGE_MULTIPLIER = 4.0D;
    private static final double ARMOR_MOB_DAMAGE_TAKEN_MULTIPLIER = 0.65D;
    private static final long SECOND_WIND_COOLDOWN_MS = 90_000L;
    private static final long EVOLUTION_ESSENCE_COST = 2_000L;
    private static final long RIDE_PROGRESS_SAMPLE_TICKS = 5L;
    private static final double MAX_RIDE_SAMPLE_DISTANCE = 12.0D;
    private static final int RIDE_PROGRESS_REPORT_INTERVAL = 25;
    private static final List<String> FAMILIAR_IDS = List.of("veil_wisp", "miner", "tiller", "morrow");

    private static final List<QuestStage> QUESTS = List.of(
        QuestStage.materials("Road Rations", "Bring simple feed for the first handling lesson.", materials(
            Material.WHEAT, 32, Material.APPLE, 16
        )),
        QuestStage.progress("Earned Trust", "Tame three ordinary animals without Kael's help.", QuestKind.TAME, 3),
        QuestStage.progress("A Healthy Herd", "Breed eight animals and keep the herd growing.", QuestKind.BREED, 8),
        QuestStage.materials("Harness Work", "Bring supplies for a strong, comfortable harness.", materials(
            Material.LEATHER, 32, Material.IRON_INGOT, 16, Material.GOLDEN_CARROT, 8
        )),
        QuestStage.progress("Clear the Trail", "Defeat forty hostile vanilla creatures.", QuestKind.HOSTILE_KILL, 40),
        QuestStage.progress("The Long Road", "Travel 1,500 blocks on any horse, donkey, mule, or llama.", QuestKind.RIDE, 1_500),
        QuestStage.progress("Face the Stampede", "Defeat one ravager without leaving the fight to someone else.", QuestKind.RAVAGER, 1),
        QuestStage.materials("Wildbound Accord", "Bring the final offering and receive the Wildbound Regalia.", materials(
            Material.BONE, 32, Material.EMERALD, 16, Material.DIAMOND, 4
        ))
    );

    private static final Map<String, FamiliarDefinition> FAMILIARS = Map.of(
        "veil_wisp", new FamiliarDefinition("veil_wisp", "Veil Wisp", Material.AMETHYST_CLUSTER, Material.AMETHYST_SHARD, 16),
        "miner", new FamiliarDefinition("miner", "Miner Familiar", Material.GOLDEN_PICKAXE, Material.DIAMOND, 8),
        "tiller", new FamiliarDefinition("tiller", "Tiller", Material.MOSS_BLOCK, Material.GOLDEN_CARROT, 16),
        "morrow", new FamiliarDefinition("morrow", "Morrow", Material.POTION, Material.PHANTOM_MEMBRANE, 12)
    );

    private final SMPCore plugin;
    private final NamespacedKey stageKey;
    private final NamespacedKey progressKey;
    private final NamespacedKey introKey;
    private final NamespacedKey armorClaimedKey;
    private final NamespacedKey trainingUnlockedKey;
    private final NamespacedKey armorPieceKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey activeFamiliarKey;
    private final NamespacedKey secondWindCooldownKey;
    private final NamespacedKey speedModifierKey;
    private final Map<String, NamespacedKey> treeKeys = new LinkedHashMap<>();
    private final Map<String, NamespacedKey> evolutionKeys = new LinkedHashMap<>();
    private final Set<UUID> pendingMenuActions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, RideProgressSample> rideProgressSamples = new HashMap<>();
    private final BeastMountManager mounts;
    private BukkitTask effectsTask;
    private BukkitTask rideProgressTask;

    public BeastwardenManager(SMPCore plugin) {
        this.plugin = plugin;
        this.stageKey = new NamespacedKey(plugin, "beastwarden_quest_stage");
        this.progressKey = new NamespacedKey(plugin, "beastwarden_quest_progress");
        this.introKey = new NamespacedKey(plugin, "beastwarden_intro_seen");
        this.armorClaimedKey = new NamespacedKey(plugin, "beastwarden_armor_claimed");
        this.trainingUnlockedKey = new NamespacedKey(plugin, "familiar_training_unlocked");
        this.armorPieceKey = new NamespacedKey(plugin, "wildbound_armor_piece");
        this.actionKey = new NamespacedKey(plugin, "beastwarden_menu_action");
        this.activeFamiliarKey = new NamespacedKey(plugin, "active_familiar");
        this.secondWindCooldownKey = new NamespacedKey(plugin, "familiar_second_wind_at");
        this.speedModifierKey = new NamespacedKey(plugin, "familiar_tree_speed");
        for (String id : FAMILIAR_IDS) {
            treeKeys.put(id, new NamespacedKey(plugin, "familiar_tree_" + id));
            evolutionKeys.put(id, new NamespacedKey(plugin, "familiar_evolved_" + id));
        }
        this.mounts = new BeastMountManager(plugin, this);
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(mounts, plugin);
        mounts.start();
        effectsTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshPassiveEffects, 20L, 20L);
        rideProgressTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::sampleMountedQuestProgress,
            RIDE_PROGRESS_SAMPLE_TICKS,
            RIDE_PROGRESS_SAMPLE_TICKS
        );
    }

    public void shutdown() {
        if (effectsTask != null) {
            effectsTask.cancel();
            effectsTask = null;
        }
        if (rideProgressTask != null) {
            rideProgressTask.cancel();
            rideProgressTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeSpeedModifier(player);
        }
        mounts.shutdown();
        pendingMenuActions.clear();
        rideProgressSamples.clear();
    }

    public void openFromNpc(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!pdcByte(player, introKey)) {
            player.getPersistentDataContainer().set(introKey, PersistentDataType.BYTE, (byte) 1);
            player.sendMessage(MessageUtil.info("<gold>Kael:</gold> I train riders and familiars. Finish my field lessons and the stable is yours."));
            player.playSound(player.getLocation(), Sound.ENTITY_HORSE_BREATHE, 0.65F, 0.9F);
        }
        openMainMenu(player);
    }

    public boolean hasCompletedQuestline(Player player) {
        return player != null && questStage(player) >= QUESTS.size() && pdcByte(player, trainingUnlockedKey);
    }

    public boolean hasFullArmorSet(Player player) {
        if (player == null) {
            return false;
        }
        return isArmorPiece(player.getInventory().getHelmet(), "helm")
            && isArmorPiece(player.getInventory().getChestplate(), "chestplate")
            && isArmorPiece(player.getInventory().getLeggings(), "leggings")
            && isArmorPiece(player.getInventory().getBoots(), "boots");
    }

    public boolean summonSteed(Player player) {
        return mounts.summon(player);
    }

    public boolean recallSteed(Player player) {
        return mounts.recall(player, true);
    }

    public boolean toggleSteed(Player player) {
        return mounts.toggle(player);
    }

    public String steedStatus(Player player) {
        return mounts.status(player);
    }

    public String activeFamiliarId(Player player) {
        if (player == null) return null;
        String id = player.getPersistentDataContainer().get(activeFamiliarKey, PersistentDataType.STRING);
        return isKnownFamiliarId(id) && familiarUnlocked(player, id) ? id : null;
    }

    static boolean isKnownFamiliarId(String id) {
        return id != null && FAMILIARS.containsKey(id);
    }

    public double familiarCoreMultiplier(Player player, String familiarId) {
        String id = normalizeFamiliarId(familiarId);
        if (player == null || id == null || !familiarUnlocked(player, id)) {
            return 1.0D;
        }
        return FamiliarSkillTree.coreEffectMultiplier(treeMask(player, id), isEvolved(player, id));
    }

    public double activeFamiliarMobDamageBonus(Player player) {
        String id = activeFamiliarId(player);
        return id == null ? 0.0D : FamiliarSkillTree.mobDamageMultiplier(treeMask(player, id)) - 1.0D;
    }

    public double activeFamiliarDamageTakenMultiplier(Player player) {
        String id = activeFamiliarId(player);
        return id == null ? 1.0D : FamiliarSkillTree.mobDamageTakenMultiplier(treeMask(player, id));
    }

    public double activeFamiliarSpeedBonus(Player player) {
        String id = activeFamiliarId(player);
        return id == null ? 0.0D : FamiliarSkillTree.movementSpeedBonus(treeMask(player, id));
    }

    public double activeFamiliarExperienceBonus(Player player) {
        String id = activeFamiliarId(player);
        return id == null ? 0.0D : FamiliarSkillTree.experienceMultiplier(treeMask(player, id)) - 1.0D;
    }

    public int unlockedSkillCount(Player player, String familiarId) {
        String id = normalizeFamiliarId(familiarId);
        return id == null ? 0 : FamiliarSkillTree.unlockedCount(treeMask(player, id));
    }

    public boolean isFamiliarEvolved(Player player, String familiarId) {
        String id = normalizeFamiliarId(familiarId);
        return id != null && isEvolved(player, id);
    }

    public void completeForAdmin(Player player, CommandSender actor) {
        if (player == null) return;
        rideProgressSamples.remove(player.getUniqueId());
        player.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, QUESTS.size());
        player.getPersistentDataContainer().set(trainingUnlockedKey, PersistentDataType.BYTE, (byte) 1);
        player.getPersistentDataContainer().remove(progressKey);
        if (!pdcByte(player, armorClaimedKey)) grantArmor(player);
        player.sendMessage(MessageUtil.success("Kael's Beastwarden training was completed by an administrator."));
        if (actor != null && !actor.equals(player)) actor.sendMessage(MessageUtil.success("Completed Beastwarden training for <white>" + player.getName() + "</white>."));
    }

    public void resetForAdmin(Player player, CommandSender actor) {
        if (player == null) return;
        mounts.recall(player, false);
        rideProgressSamples.remove(player.getUniqueId());
        player.getPersistentDataContainer().remove(stageKey);
        player.getPersistentDataContainer().remove(progressKey);
        player.getPersistentDataContainer().remove(introKey);
        player.getPersistentDataContainer().remove(armorClaimedKey);
        player.getPersistentDataContainer().remove(trainingUnlockedKey);
        player.getPersistentDataContainer().remove(secondWindCooldownKey);
        treeKeys.values().forEach(player.getPersistentDataContainer()::remove);
        evolutionKeys.values().forEach(player.getPersistentDataContainer()::remove);
        player.sendMessage(MessageUtil.warn("Your Beastwarden quest and familiar training progress were reset."));
        if (actor != null && !actor.equals(player)) actor.sendMessage(MessageUtil.success("Reset Beastwarden progress for <white>" + player.getName() + "</white>."));
    }

    public void giveArmorForAdmin(Player player, CommandSender actor) {
        if (player == null) return;
        grantArmorItems(player);
        if (actor != null && !actor.equals(player)) actor.sendMessage(MessageUtil.success("Gave Wildbound Regalia to <white>" + player.getName() + "</white>."));
    }

    public void openMainMenu(Player player) {
        openMainMenu(player, false);
    }

    public boolean openAdminPreview(Player player, AdminPreviewView view, String familiarId) {
        if (player == null || view == null) {
            return false;
        }
        String id = familiarId == null ? null : normalizeFamiliarId(familiarId);
        if (familiarId != null && id == null) {
            return false;
        }
        switch (view) {
            case MAIN -> openMainMenu(player, true);
            case TREE -> {
                if (id == null) openFamiliarSelect(player, SelectMode.TREE, true);
                else openTree(player, id, true);
            }
            case EVOLUTION -> {
                if (id == null) openFamiliarSelect(player, SelectMode.EVOLUTION, true);
                else openEvolutionConfirmation(player, id, true);
            }
        }
        return true;
    }

    private void openMainMenu(Player player, boolean preview) {
        int stage = preview ? QUESTS.size() : questStage(player);
        Inventory menu = Bukkit.createInventory(
            preview ? new AdminPreviewHolder(player.getUniqueId()) : new BeastMenuHolder(player.getUniqueId()),
            QUEST_MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#65a30d:#f59e0b><bold>Kael the Beastwarden</bold></gradient>"), "Kael the Beastwarden")
        );
        fill(menu);
        menu.setItem(4, item(Material.LEAD, "<gradient:#65a30d:#f59e0b><bold>Beastwarden Training</bold></gradient>", List.of(
            "<gray>Eight practical lessons.</gray>",
            "<gray>Final: <white>Wildbound Regalia</white>.</gray>",
            "<gray>Then train and evolve familiars.</gray>"
        ), null));
        for (int i = 0; i < QUESTS.size(); i++) {
            QuestStage quest = QUESTS.get(i);
            boolean done = i < stage;
            boolean current = i == stage;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + quest.description + "</gray>");
            if (quest.kind == QuestKind.MATERIALS) {
                quest.materials.forEach((material, amount) -> lore.add(
                    "<gray>" + pretty(material) + ": <white>" + countPlain(player, material) + "/" + amount + "</white>.</gray>"
                ));
            } else {
                lore.add("<gray>Progress: <white>" + progressText(player, quest) + "/" + quest.target + "</white>.</gray>");
            }
            if (done) lore.add("<green>Complete.</green>");
            else if (current) lore.add(requirementMet(player, quest) ? "<green>Click to finish this lesson.</green>" : "<yellow>Finish the requirement first.</yellow>");
            else lore.add("<dark_gray>Complete the earlier lesson first.</dark_gray>");
            Material icon = done ? Material.LIME_STAINED_GLASS_PANE : current ? quest.icon() : Material.GRAY_STAINED_GLASS_PANE;
            menu.setItem(QUEST_SLOTS[i], item(icon,
                done ? "<green><bold>" + quest.title + "</bold></green>" : current ? "<gold><bold>" + quest.title + "</bold></gold>" : "<dark_gray><bold>Locked Lesson</bold></dark_gray>",
                lore,
                current ? "quest:" + i : null
            ));
        }

        boolean complete = preview || hasCompletedQuestline(player);
        menu.setItem(37, item(
            Material.NETHERITE_CHESTPLATE,
            "<gold><bold>Wildbound Regalia</bold></gold>",
            wildboundMenuLore(complete),
            null
        ));
        menu.setItem(39, item(Material.DIAMOND_HORSE_ARMOR, "<yellow><bold>Recalled Steed</bold></yellow>", complete ? List.of(
            "<gray>Selected: <white>" + mounts.selectedType(player).displayName() + "</white>.</gray>",
            "<gray>Status: <white>" + mounts.status(player) + "</white>.</gray>",
            "<gray>Wear the full set to summon it.</gray>",
            preview ? "<dark_gray>Preview only.</dark_gray>" : "<yellow>Click to summon or recall.</yellow>"
        ) : List.of("<gray>Complete the training to unlock.</gray>"), complete && !preview ? "mount:toggle" : null));
        menu.setItem(41, item(Material.ENCHANTED_BOOK, "<light_purple><bold>Familiar Skill Trees</bold></light_purple>", complete ? List.of(
            "<gray>Each familiar has its own 50 upgrades.</gray>",
            "<gray>Five branches with a final keystone.</gray>",
            "<yellow>Click to choose a familiar.</yellow>"
        ) : List.of("<gray>Unlocks after the final lesson.</gray>"), complete ? "select:tree" : null));
        menu.setItem(43, item(Material.NETHER_STAR, "<aqua><bold>Familiar Evolution</bold></aqua>", complete ? List.of(
            "<gray>Evolve each familiar once.</gray>",
            "<gray>Evolution adds <white>5%</white> to its core perk.</gray>",
            "<yellow>Click to choose a familiar.</yellow>"
        ) : List.of("<gray>Unlocks after the final lesson.</gray>"), complete ? "select:evolution" : null));
        menu.setItem(48, item(Material.DIAMOND_HORSE_ARMOR, "<gold><bold>Select Horse</bold></gold>", complete ? List.of(
            "<gray>Faster, higher jump, diamond armor.</gray>",
            mounts.selectedType(player) == BeastMountManager.MountType.HORSE ? "<green>Currently selected.</green>" : "<yellow>Click to select.</yellow>"
        ) : List.of("<gray>Complete the training first.</gray>"), complete && !preview ? "mount:horse" : null));
        menu.setItem(50, item(Material.CHEST, "<gold><bold>Select Donkey</bold></gold>", complete ? List.of(
            "<gray>Slower, with persistent personal storage.</gray>",
            mounts.selectedType(player) == BeastMountManager.MountType.DONKEY ? "<green>Currently selected.</green>" : "<yellow>Click to select.</yellow>"
        ) : List.of("<gray>Complete the training first.</gray>"), complete && !preview ? "mount:donkey" : null));
        menu.setItem(53, item(Material.BARRIER, "<red><bold>Close</bold></red>", List.of("<gray>Close this menu.</gray>"), "close"));
        player.openInventory(menu);
    }

    private void openFamiliarSelect(Player player, SelectMode mode) {
        openFamiliarSelect(player, mode, false);
    }

    private void openFamiliarSelect(Player player, SelectMode mode, boolean preview) {
        Inventory menu = Bukkit.createInventory(
            preview ? new AdminPreviewHolder(player.getUniqueId()) : new FamiliarSelectHolder(player.getUniqueId(), mode),
            27,
            BedrockCompat.menuTitle(player, MM.deserialize(mode == SelectMode.TREE
                ? "<light_purple><bold>Choose a Familiar</bold></light_purple>"
                : "<aqua><bold>Choose an Evolution</bold></aqua>"), mode == SelectMode.TREE ? "Familiar Training" : "Familiar Evolution")
        );
        fill(menu);
        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < FAMILIAR_IDS.size(); i++) {
            FamiliarDefinition definition = FAMILIARS.get(FAMILIAR_IDS.get(i));
            boolean unlocked = preview || familiarUnlocked(player, definition.id);
            List<String> lore = new ArrayList<>();
            lore.add(preview
                ? "<gray>Preview all <white>50 upgrades</white>.</gray>"
                : unlocked ? "<gray>Skills: <white>" + unlockedSkillCount(player, definition.id) + "/50</white>.</gray>" : "<gray>This familiar is still locked.</gray>");
            if (unlocked) lore.add(preview
                ? "<gray>Evolution preview is also available.</gray>"
                : "<gray>Evolution: <white>" + (isEvolved(player, definition.id) ? "Complete" : "Available") + "</white>.</gray>");
            if (unlocked) lore.add("<yellow>Click to continue.</yellow>");
            menu.setItem(slots[i], item(unlocked ? definition.icon : Material.GRAY_DYE,
                unlocked ? "<gold><bold>" + definition.displayName + "</bold></gold>" : "<dark_gray><bold>Locked Familiar</bold></dark_gray>",
                lore,
                unlocked ? (mode == SelectMode.TREE ? "tree:" : "evolve:") + definition.id : null
            ));
        }
        menu.setItem(22, item(Material.ARROW, "<yellow><bold>Back</bold></yellow>", List.of("<gray>Return to Kael.</gray>"), "back:main"));
        player.openInventory(menu);
    }

    private void openTree(Player player, String familiarId) {
        openTree(player, familiarId, false);
    }

    private void openTree(Player player, String familiarId, boolean preview) {
        FamiliarDefinition familiar = familiarId == null ? null : FAMILIARS.get(familiarId);
        if (familiar == null || (!preview && !familiarUnlocked(player, familiarId))) {
            openFamiliarSelect(player, SelectMode.TREE, preview);
            return;
        }
        long mask = treeMask(player, familiarId);
        Inventory menu = Bukkit.createInventory(
            preview ? new AdminPreviewHolder(player.getUniqueId()) : new FamiliarTreeHolder(player.getUniqueId(), familiarId),
            TREE_MENU_SIZE,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#c084fc:#f59e0b><bold>" + familiar.displayName + " Training</bold></gradient>"), familiar.displayName + " Training")
        );
        for (int branch = 0; branch < FamiliarSkillTree.BRANCH_COUNT; branch++) {
            FamiliarSkillTree.Branch definition = FamiliarSkillTree.branch(branch);
            for (int rank = 0; rank < FamiliarSkillTree.RANKS_PER_BRANCH - 1; rank++) {
                int node = FamiliarSkillTree.nodeIndex(branch, rank);
                menu.setItem(branch * 9 + rank, skillNode(player, familiarId, mask, node, definition, preview));
            }
            int keystoneNode = FamiliarSkillTree.nodeIndex(branch, FamiliarSkillTree.RANKS_PER_BRANCH - 1);
            menu.setItem(45 + branch, skillNode(player, familiarId, mask, keystoneNode, definition, preview));
        }
        menu.setItem(50, item(Material.BOOK, "<gold><bold>Training Summary</bold></gold>", List.of(
            "<gray>Unlocked: <white>" + FamiliarSkillTree.unlockedCount(mask) + "/50</white>.</gray>",
            "<gray>Mob damage: <white>+" + percent(FamiliarSkillTree.mobDamageMultiplier(mask) - 1.0D) + "</white>.</gray>",
            "<gray>Mob defense: <white>-" + percent(1.0D - FamiliarSkillTree.mobDamageTakenMultiplier(mask)) + "</white>.</gray>",
            "<gray>Speed: <white>+" + percent(FamiliarSkillTree.movementSpeedBonus(mask)) + "</white>.</gray>",
            "<gray>Core perk: <white>+" + percent(FamiliarSkillTree.coreEffectMultiplier(mask, isEvolved(player, familiarId)) - 1.0D) + "</white>.</gray>",
            "<gray>Experience: <white>+" + percent(FamiliarSkillTree.experienceMultiplier(mask) - 1.0D) + "</white>.</gray>"
        ), null));
        menu.setItem(51, item(Material.NETHER_STAR, "<aqua><bold>Evolution</bold></aqua>", List.of(
            "<gray>Status: <white>" + (isEvolved(player, familiarId) ? "Complete" : "Not evolved") + "</white>.</gray>",
            "<yellow>Click to view.</yellow>"
        ), "evolve:" + familiarId));
        menu.setItem(52, item(Material.ARROW, "<yellow><bold>Familiars</bold></yellow>", List.of("<gray>Choose another familiar.</gray>"), "select:tree"));
        menu.setItem(53, item(Material.BARRIER, "<red><bold>Close</bold></red>", List.of("<gray>Close this menu.</gray>"), "close"));
        player.openInventory(menu);
    }

    private ItemStack skillNode(Player player, String familiarId, long mask, int node, FamiliarSkillTree.Branch branch, boolean preview) {
        int rank = FamiliarSkillTree.rankOf(node);
        boolean unlocked = FamiliarSkillTree.unlocked(mask, node);
        boolean available = FamiliarSkillTree.canUnlock(mask, node);
        boolean keystone = rank == FamiliarSkillTree.RANKS_PER_BRANCH - 1;
        Material icon = unlocked ? Material.LIME_STAINED_GLASS_PANE : available ? branch.icon() : Material.GRAY_STAINED_GLASS_PANE;
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + branch.name() + (keystone ? " Keystone" : " Rank " + (rank + 1)) + ".</gray>");
        lore.add("<white>" + FamiliarSkillTree.effectLine(FamiliarSkillTree.branchOf(node), rank) + "</white>");
        if (preview) {
            lore.add("<gray>Cost: <aqua>" + FamiliarSkillTree.cost(node) + " Essence</aqua>.</gray>");
            lore.add("<yellow>Click to preview its confirmation.</yellow>");
        } else if (unlocked) lore.add("<green>Unlocked.</green>");
        else {
            lore.add("<gray>Cost: <aqua>" + FamiliarSkillTree.cost(node) + " Essence</aqua>.</gray>");
            lore.add(available ? "<yellow>Click to review this purchase.</yellow>" : "<dark_gray>Unlock the previous node first.</dark_gray>");
        }
        return item(icon,
            unlocked ? "<green><bold>" + branch.nodeName(rank) + "</bold></green>" : available ? "<gold><bold>" + branch.nodeName(rank) + "</bold></gold>" : "<dark_gray><bold>" + branch.nodeName(rank) + "</bold></dark_gray>",
            lore,
            preview || available ? "skill:" + familiarId + ":" + node : null
        );
    }

    private void openSkillConfirmation(Player player, String familiarId, int node) {
        openSkillConfirmation(player, familiarId, node, false);
    }

    private void openSkillConfirmation(Player player, String familiarId, int node, boolean preview) {
        if (familiarId == null || !isKnownFamiliarId(familiarId) || node < 0 || node >= FamiliarSkillTree.NODE_COUNT) {
            openFamiliarSelect(player, SelectMode.TREE, preview);
            return;
        }
        if (!preview && !canPurchaseSkill(player, familiarId, node)) {
            openTree(player, familiarId, preview);
            return;
        }
        FamiliarSkillTree.Branch branch = FamiliarSkillTree.branch(FamiliarSkillTree.branchOf(node));
        int rank = FamiliarSkillTree.rankOf(node);
        Inventory menu = Bukkit.createInventory(
            preview ? new AdminPreviewHolder(player.getUniqueId()) : new ConfirmHolder(player.getUniqueId(), familiarId, node, false),
            27,
            BedrockCompat.menuTitle(player, MM.deserialize("<gold><bold>Confirm Training</bold></gold>"), "Confirm Training")
        );
        fill(menu);
        menu.setItem(13, item(branch.icon(), "<gold><bold>" + branch.nodeName(rank) + "</bold></gold>", List.of(
            "<gray>" + FamiliarSkillTree.effectLine(FamiliarSkillTree.branchOf(node), rank) + "</gray>",
            "<gray>Cost: <aqua>" + FamiliarSkillTree.cost(node) + " Essence</aqua>.</gray>"
        ), null));
        menu.setItem(11, preview
            ? item(Material.GRAY_DYE, "<gray><bold>Preview Only</bold></gray>", List.of("<gray>No Essence or progress can change here.</gray>"), null)
            : item(Material.LIME_CONCRETE, "<green><bold>Confirm</bold></green>", List.of("<gray>Spend the Essence and unlock this node.</gray>"), "confirm_skill:" + familiarId + ":" + node));
        menu.setItem(15, item(Material.RED_CONCRETE, "<red><bold>Cancel</bold></red>", List.of("<gray>Return without spending anything.</gray>"), "tree:" + familiarId));
        player.openInventory(menu);
    }

    private void openEvolutionConfirmation(Player player, String familiarId) {
        openEvolutionConfirmation(player, familiarId, false);
    }

    private void openEvolutionConfirmation(Player player, String familiarId, boolean preview) {
        FamiliarDefinition familiar = familiarId == null ? null : FAMILIARS.get(familiarId);
        if (familiar == null || (!preview && !familiarUnlocked(player, familiarId))) {
            openFamiliarSelect(player, SelectMode.EVOLUTION, preview);
            return;
        }
        if (!preview && isEvolved(player, familiarId)) {
            player.sendMessage(MessageUtil.info("<white>" + familiar.displayName + "</white> has already evolved."));
            openTree(player, familiarId);
            return;
        }
        Inventory menu = Bukkit.createInventory(
            preview ? new AdminPreviewHolder(player.getUniqueId()) : new ConfirmHolder(player.getUniqueId(), familiarId, -1, true),
            27,
            BedrockCompat.menuTitle(player, MM.deserialize("<aqua><bold>Familiar Evolution</bold></aqua>"), "Familiar Evolution")
        );
        fill(menu);
        menu.setItem(13, item(familiar.icon, "<aqua><bold>Evolve " + familiar.displayName + "</bold></aqua>", List.of(
            "<gray>Permanent <white>+5%</white> to its original perk.</gray>",
            "<gray>Cost: <aqua>" + EVOLUTION_ESSENCE_COST + " Essence</aqua>.</gray>",
            "<gray>Material: <white>" + familiar.materialAmount + " " + pretty(familiar.material) + "</white>.</gray>"
        ), null));
        menu.setItem(11, preview
            ? item(Material.GRAY_DYE, "<gray><bold>Preview Only</bold></gray>", List.of("<gray>No materials or progress can change here.</gray>"), null)
            : item(Material.LIME_CONCRETE, "<green><bold>Confirm Evolution</bold></green>", List.of("<gray>Consume the listed cost permanently.</gray>"), "confirm_evolve:" + familiarId));
        menu.setItem(15, item(Material.RED_CONCRETE, "<red><bold>Cancel</bold></red>", List.of("<gray>Return without spending anything.</gray>"), "tree:" + familiarId));
        player.openInventory(menu);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof BeastMenuMarker marker)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
            || !marker.playerId().equals(player.getUniqueId())
            || event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT
            || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        String action = action(event.getCurrentItem());
        if (action == null || !pendingMenuActions.add(player.getUniqueId())) {
            return;
        }
        boolean preview = marker instanceof AdminPreviewHolder;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (preview) handlePreviewAction(player, action);
                else handleAction(player, action);
            } finally {
                pendingMenuActions.remove(player.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof BeastMenuMarker) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTameProgress(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player) recordProgress(player, QuestKind.TAME, 1.0D);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreedProgress(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) recordProgress(player, QuestKind.BREED, 1.0D);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKillProgress(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !ordinaryMob(event.getEntity())) {
            return;
        }
        if (event.getEntity() instanceof Monster) recordProgress(killer, QuestKind.HOSTILE_KILL, 1.0D);
        if (event.getEntityType() == org.bukkit.entity.EntityType.RAVAGER) recordProgress(killer, QuestKind.RAVAGER, 1.0D);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuaranteedTame(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;
        if (!hasFullArmorSet(event.getPlayer()) || !(event.getRightClicked() instanceof Tameable tameable)
            || tameable.isTamed() || tameable.getOwner() != null || mounts.isManagedMount(event.getRightClicked())) {
            return;
        }
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        if (!isTamingMaterial(event.getRightClicked(), held)) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) consumeOne(event.getPlayer(), event.getHand(), held);
        tameable.setOwner(event.getPlayer());
        tameable.setTamed(true);
        if (tameable instanceof AbstractHorse horse) horse.setDomestication(horse.getMaxDomestication());
        LocationEffects.tame(event.getPlayer(), event.getRightClicked());
        event.getPlayer().sendActionBar(MM.deserialize("<green>Wildbound Regalia:</green> <white>Tamed with one item.</white>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        LivingEntity victim = event.getEntity() instanceof LivingEntity living ? living : null;
        if (victim == null) return;

        Player attacker = attackingPlayer(event.getDamager());
        if (attacker != null && ordinaryMob(victim)) {
            double multiplier = hasFullArmorSet(attacker) ? ARMOR_MOB_DAMAGE_MULTIPLIER : 1.0D;
            multiplier *= 1.0D + activeFamiliarMobDamageBonus(attacker);
            if (multiplier > 1.0D) event.setDamage(event.getDamage() * multiplier);
        }

        if (victim instanceof Player player) {
            LivingEntity source = causingLivingEntity(event.getDamager());
            if (!ordinaryMob(source)) return;
            double multiplier = hasFullArmorSet(player) ? ARMOR_MOB_DAMAGE_TAKEN_MULTIPLIER : 1.0D;
            multiplier *= activeFamiliarDamageTakenMultiplier(player);
            if (multiplier < 1.0D) event.setDamage(event.getDamage() * multiplier);
            trySecondWind(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExperience(PlayerExpChangeEvent event) {
        if (event.getAmount() <= 0) return;
        double bonus = activeFamiliarExperienceBonus(event.getPlayer());
        if (bonus <= 0.0D) return;
        double exact = event.getAmount() * bonus;
        int extra = (int) Math.floor(exact);
        if (ThreadLocalRandom.current().nextDouble() < exact - extra) extra++;
        if (extra > 0) event.setAmount((int) Math.min(Integer.MAX_VALUE, (long) event.getAmount() + extra));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingMenuActions.remove(event.getPlayer().getUniqueId());
        rideProgressSamples.remove(event.getPlayer().getUniqueId());
        removeSpeedModifier(event.getPlayer());
    }

    private void handleAction(Player player, String action) {
        if (action.equals("close")) {
            player.closeInventory();
            return;
        }
        if (action.equals("back:main")) {
            openMainMenu(player);
            return;
        }
        if (action.startsWith("quest:")) {
            finishQuest(player, parseInt(action.substring(6), -1));
            return;
        }
        if (action.equals("mount:toggle")) {
            mounts.toggle(player);
            openMainMenu(player);
            return;
        }
        if (action.equals("mount:horse") || action.equals("mount:donkey")) {
            mounts.selectType(player, action.equals("mount:donkey") ? BeastMountManager.MountType.DONKEY : BeastMountManager.MountType.HORSE);
            openMainMenu(player);
            return;
        }
        if (action.equals("select:tree")) {
            openFamiliarSelect(player, SelectMode.TREE);
            return;
        }
        if (action.equals("select:evolution")) {
            openFamiliarSelect(player, SelectMode.EVOLUTION);
            return;
        }
        if (action.startsWith("tree:")) {
            openTree(player, normalizeFamiliarId(action.substring(5)));
            return;
        }
        if (action.startsWith("evolve:")) {
            openEvolutionConfirmation(player, normalizeFamiliarId(action.substring(7)));
            return;
        }
        if (action.startsWith("skill:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) openSkillConfirmation(player, normalizeFamiliarId(parts[1]), parseInt(parts[2], -1));
            return;
        }
        if (action.startsWith("confirm_skill:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) purchaseSkill(player, normalizeFamiliarId(parts[1]), parseInt(parts[2], -1));
            return;
        }
        if (action.startsWith("confirm_evolve:")) {
            evolve(player, normalizeFamiliarId(action.substring("confirm_evolve:".length())));
        }
    }

    private void handlePreviewAction(Player player, String action) {
        if (action.equals("close")) {
            player.closeInventory();
        } else if (action.equals("back:main")) {
            openMainMenu(player, true);
        } else if (action.equals("select:tree")) {
            openFamiliarSelect(player, SelectMode.TREE, true);
        } else if (action.equals("select:evolution")) {
            openFamiliarSelect(player, SelectMode.EVOLUTION, true);
        } else if (action.startsWith("tree:")) {
            openTree(player, normalizeFamiliarId(action.substring(5)), true);
        } else if (action.startsWith("evolve:")) {
            openEvolutionConfirmation(player, normalizeFamiliarId(action.substring(7)), true);
        } else if (action.startsWith("skill:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) {
                openSkillConfirmation(player, normalizeFamiliarId(parts[1]), parseInt(parts[2], -1), true);
            }
        }
    }

    private void finishQuest(Player player, int expectedStage) {
        int stage = questStage(player);
        if (stage != expectedStage || stage < 0 || stage >= QUESTS.size()) {
            openMainMenu(player);
            return;
        }
        QuestStage quest = QUESTS.get(stage);
        if (!requirementMet(player, quest)) {
            player.sendMessage(MessageUtil.warn("Finish <white>" + quest.title + "</white> before turning it in."));
            openMainMenu(player);
            return;
        }
        if (stage == QUESTS.size() - 1 && freeStorageSlots(player) < 4) {
            player.sendMessage(MessageUtil.warn("Make <white>four empty inventory slots</white> before claiming the Wildbound Regalia."));
            openMainMenu(player);
            return;
        }
        if (quest.kind == QuestKind.MATERIALS
            && !InventoryRecipeUtil.removePlainMaterials(plugin, player, quest.materials)) {
            player.sendMessage(MessageUtil.error("Your materials changed before Kael could accept them."));
            openMainMenu(player);
            return;
        }
        int next = stage + 1;
        rideProgressSamples.remove(player.getUniqueId());
        player.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, next);
        player.getPersistentDataContainer().remove(progressKey);
        player.playSound(player.getLocation(), next == QUESTS.size() ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9F, next == QUESTS.size() ? 0.85F : 1.25F);
        player.sendMessage(MessageUtil.success("Lesson complete: <white>" + quest.title + "</white>."));
        if (next == QUESTS.size()) finishQuestline(player);
        openMainMenu(player);
    }

    private void finishQuestline(Player player) {
        player.getPersistentDataContainer().set(trainingUnlockedKey, PersistentDataType.BYTE, (byte) 1);
        if (!pdcByte(player, armorClaimedKey)) grantArmor(player);
        player.showTitle(Title.title(
            MM.deserialize("<gradient:#65a30d:#f59e0b><bold>BEASTWARDEN</bold></gradient>"),
            MM.deserialize("<yellow>Wildbound Regalia and familiar training unlocked.</yellow>"),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3800), Duration.ofMillis(1000))
        ));
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0D, 1.0D, 0.0D), 55, 0.7D, 0.9D, 0.7D, 0.05D);
    }

    private void grantArmor(Player player) {
        player.getPersistentDataContainer().set(armorClaimedKey, PersistentDataType.BYTE, (byte) 1);
        grantArmorItems(player);
        player.sendMessage(MessageUtil.success("Kael gave you the full <white>Wildbound Regalia</white>."));
    }

    private void grantArmorItems(Player player) {
        InventoryRecipeUtil.giveOrDrop(player, armor(Material.NETHERITE_HELMET, "Wildbound Crown", "helm"));
        InventoryRecipeUtil.giveOrDrop(player, armor(Material.NETHERITE_CHESTPLATE, "Wildbound Mantle", "chestplate"));
        InventoryRecipeUtil.giveOrDrop(player, armor(Material.NETHERITE_LEGGINGS, "Wildbound Legguards", "leggings"));
        InventoryRecipeUtil.giveOrDrop(player, armor(Material.NETHERITE_BOOTS, "Wildbound Treads", "boots"));
    }

    private ItemStack armor(Material material, String name, String piece) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.LEGENDARY, name));
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.getPersistentDataContainer().set(armorPieceKey, PersistentDataType.STRING, piece);
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            material,
            "LEGENDARY",
            "ARMOR",
            List.of("<gray>Armor awarded by Kael the Beastwarden.</gray>"),
            List.of(CustomLoreUtil.section(
                "SET",
                "Wildbound Regalia",
                "<gray>Full set: <white>4x damage</white> to ordinary mobs.</gray>",
                "<gray>Take <white>35% less</white> damage from them.</gray>",
                "<gray>One-item taming and <white>/steed</white>.</gray>",
                "<dark_gray>No effect on players or custom bosses.</dark_gray>"
            ))
        ));
        item.setItemMeta(meta);
        return item;
    }

    static List<String> wildboundMenuLore(boolean complete) {
        return List.of(
            "<gray>Wear the full set for every bonus:</gray>",
            "<gray><white>4x damage</white> to vanilla mobs.</gray>",
            "<gray><white>35% less damage</white> from vanilla mobs.</gray>",
            "<gray>Tame valid animals with one item.</gray>",
            "<gray><white>/steed</white> calls your horse or donkey.</gray>",
            "<dark_gray>No bonus against players or custom bosses.</dark_gray>",
            complete
                ? "<green>Unlocked through Beastwarden Training.</green>"
                : "<yellow>Complete all eight lessons to earn it.</yellow>"
        );
    }

    private void purchaseSkill(Player player, String familiarId, int node) {
        if (!canPurchaseSkill(player, familiarId, node)) {
            player.sendMessage(MessageUtil.warn("That training node is no longer available."));
            if (familiarId != null) openTree(player, familiarId);
            return;
        }
        long cost = FamiliarSkillTree.cost(node);
        if (plugin.getEssenceManager() == null || !plugin.getEssenceManager().spend(player, cost, "familiar_training_" + familiarId + "_" + node)) {
            player.sendMessage(MessageUtil.warn("You need <white>" + cost + " Essence</white> for this training."));
            openTree(player, familiarId);
            return;
        }
        long nextMask = FamiliarSkillTree.unlock(treeMask(player, familiarId), node);
        player.getPersistentDataContainer().set(treeKeys.get(familiarId), PersistentDataType.LONG, nextMask);
        player.playSound(player.getLocation(), FamiliarSkillTree.rankOf(node) == FamiliarSkillTree.RANKS_PER_BRANCH - 1
            ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8F, 1.2F);
        player.sendMessage(MessageUtil.success("Unlocked <white>" + FamiliarSkillTree.branch(FamiliarSkillTree.branchOf(node)).nodeName(FamiliarSkillTree.rankOf(node)) + "</white>."));
        openTree(player, familiarId);
    }

    private boolean canPurchaseSkill(Player player, String familiarId, int node) {
        return hasCompletedQuestline(player)
            && familiarId != null
            && familiarUnlocked(player, familiarId)
            && FamiliarSkillTree.canUnlock(treeMask(player, familiarId), node);
    }

    private void evolve(Player player, String familiarId) {
        FamiliarDefinition familiar = FAMILIARS.get(familiarId);
        if (!hasCompletedQuestline(player) || familiar == null || !familiarUnlocked(player, familiarId) || isEvolved(player, familiarId)) {
            if (familiarId != null) openTree(player, familiarId);
            return;
        }
        Map<Material, Integer> materials = Map.of(familiar.material, familiar.materialAmount);
        if (!InventoryRecipeUtil.hasPlainMaterials(plugin, player, materials)) {
            player.sendMessage(MessageUtil.warn("You need <white>" + familiar.materialAmount + " " + pretty(familiar.material) + "</white>."));
            openTree(player, familiarId);
            return;
        }
        if (plugin.getEssenceManager() == null || plugin.getEssenceManager().balance(player) < EVOLUTION_ESSENCE_COST) {
            player.sendMessage(MessageUtil.warn("You need <white>" + EVOLUTION_ESSENCE_COST + " Essence</white>."));
            openTree(player, familiarId);
            return;
        }
        if (!InventoryRecipeUtil.removePlainMaterials(plugin, player, materials)) {
            player.sendMessage(MessageUtil.error("The evolution materials changed before they could be accepted."));
            openTree(player, familiarId);
            return;
        }
        if (!plugin.getEssenceManager().spend(player, EVOLUTION_ESSENCE_COST, "familiar_evolution_" + familiarId)) {
            InventoryRecipeUtil.giveOrDrop(player, new ItemStack(familiar.material, familiar.materialAmount));
            player.sendMessage(MessageUtil.error("Your Essence changed. The evolution materials were returned."));
            openTree(player, familiarId);
            return;
        }
        player.getPersistentDataContainer().set(evolutionKeys.get(familiarId), PersistentDataType.BYTE, (byte) 1);
        player.showTitle(Title.title(
            MM.deserialize("<aqua><bold>FAMILIAR EVOLVED</bold></aqua>"),
            MM.deserialize("<white>" + familiar.displayName + " gained 5% core strength.</white>"),
            Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3200), Duration.ofMillis(800))
        ));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.15F);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D), 38, 0.6D, 0.8D, 0.6D, 0.04D);
        openTree(player, familiarId);
    }

    private void recordProgress(Player player, QuestKind kind, double amount) {
        if (player == null || amount <= 0.0D || currentQuestKind(player) != kind) return;
        QuestStage quest = QUESTS.get(questStage(player));
        double before = questProgress(player);
        double after = Math.min(quest.target, before + amount);
        if (after <= before) return;
        player.getPersistentDataContainer().set(progressKey, PersistentDataType.DOUBLE, after);
        if (kind == QuestKind.RIDE && shouldReportRideProgress(before, after, quest.target)) {
            player.sendActionBar(MM.deserialize(
                "<gold>The Long Road:</gold> <white>" + (int) Math.floor(after) + "/" + quest.target + " blocks</white>"
            ));
        }
        if (before < quest.target && after >= quest.target) {
            player.sendMessage(MessageUtil.success("Kael's lesson is ready to turn in: <white>" + quest.title + "</white>."));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.35F);
        }
    }

    private void sampleMountedQuestProgress() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (currentQuestKind(player) != QuestKind.RIDE || !(player.getVehicle() instanceof AbstractHorse mount)) {
                rideProgressSamples.remove(playerId);
                continue;
            }

            var currentLocation = mount.getLocation();
            RideProgressSample current = new RideProgressSample(
                currentLocation.getWorld().getUID(),
                mount.getUniqueId(),
                currentLocation.getX(),
                currentLocation.getZ()
            );
            RideProgressSample previous = rideProgressSamples.put(playerId, current);
            if (previous == null) {
                QuestStage quest = QUESTS.get(questStage(player));
                player.sendActionBar(MM.deserialize(
                    "<gold>Mounted travel tracking:</gold> <white>" + progressText(player, quest) + "/" + quest.target + " blocks</white>"
                ));
                continue;
            }

            double distance = acceptedRideSampleDistance(
                previous.worldId.equals(current.worldId),
                previous.mountId.equals(current.mountId),
                current.x - previous.x,
                current.z - previous.z
            );
            if (distance > 0.0D) {
                recordProgress(player, QuestKind.RIDE, distance);
            }
        }
    }

    static double acceptedRideSampleDistance(boolean sameWorld, boolean sameMount, double dx, double dz) {
        if (!sameWorld || !sameMount || !Double.isFinite(dx) || !Double.isFinite(dz)) {
            return 0.0D;
        }
        double distance = Math.hypot(dx, dz);
        return distance > 0.01D && distance <= MAX_RIDE_SAMPLE_DISTANCE ? distance : 0.0D;
    }

    static boolean shouldReportRideProgress(double before, double after, double target) {
        if (!Double.isFinite(before) || !Double.isFinite(after) || after <= before || after >= target) {
            return false;
        }
        return (int) Math.floor(after / RIDE_PROGRESS_REPORT_INTERVAL)
            > (int) Math.floor(Math.max(0.0D, before) / RIDE_PROGRESS_REPORT_INTERVAL);
    }

    public void sendQuestProgress(Player player, CommandSender recipient) {
        if (player == null || recipient == null) return;
        int stage = questStage(player);
        if (stage >= QUESTS.size()) {
            recipient.sendMessage(MessageUtil.success("<white>" + player.getName() + "</white> completed all Beastwarden lessons."));
            return;
        }
        QuestStage quest = QUESTS.get(stage);
        recipient.sendMessage(MessageUtil.info(
            "<white>" + player.getName() + "</white> - lesson <white>" + (stage + 1) + "/" + QUESTS.size()
                + "</white>: <gold>" + quest.title + "</gold>."
        ));
        if (quest.kind == QuestKind.MATERIALS) {
            quest.materials.forEach((material, amount) -> recipient.sendMessage(MessageUtil.info(
                pretty(material) + ": <white>" + Math.min(amount, countPlain(player, material)) + "/" + amount + "</white>."
            )));
            return;
        }
        String unit = quest.kind == QuestKind.RIDE ? " blocks" : "";
        recipient.sendMessage(MessageUtil.info(
            "Progress: <white>" + progressText(player, quest) + "/" + quest.target + unit + "</white>."
        ));
    }

    private void refreshPassiveEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            double speed = activeFamiliarSpeedBonus(player);
            AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (attribute == null) continue;
            attribute.removeModifier(speedModifierKey);
            if (speed > 0.0D) {
                attribute.addTransientModifier(new AttributeModifier(speedModifierKey, speed, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
    }

    private void removeSpeedModifier(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute != null) attribute.removeModifier(speedModifierKey);
    }

    private void trySecondWind(Player player, EntityDamageByEntityEvent event) {
        String familiarId = activeFamiliarId(player);
        if (familiarId == null || !FamiliarSkillTree.hasKeystone(treeMask(player, familiarId), 1)) return;
        double maxHealth = attributeValue(player, Attribute.MAX_HEALTH, 20.0D);
        if (player.getHealth() - event.getDamage() > maxHealth * 0.30D) return;
        long now = System.currentTimeMillis();
        long next = player.getPersistentDataContainer().getOrDefault(secondWindCooldownKey, PersistentDataType.LONG, 0L);
        if (next > now) return;
        player.getPersistentDataContainer().set(secondWindCooldownKey, PersistentDataType.LONG, now + SECOND_WIND_COOLDOWN_MS);
        event.setDamage(Math.min(event.getDamage(), Math.max(0.0D, player.getHealth() - maxHealth * 0.30D)));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            player.setHealth(Math.min(attributeValue(player, Attribute.MAX_HEALTH, 20.0D), player.getHealth() + 8.0D));
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.8F, 1.35F);
            player.spawnParticle(Particle.HEART, player.getLocation().add(0.0D, 1.0D, 0.0D), 10, 0.4D, 0.55D, 0.4D, 0.02D);
            player.sendActionBar(MM.deserialize("<green>Second Wind restored you.</green>"));
        });
    }

    private boolean ordinaryMob(LivingEntity entity) {
        if (entity == null || entity instanceof Player || entity instanceof ArmorStand || mounts.isManagedMount(entity)) return false;
        if (entity.getScoreboardTags().contains("smpcore_npc")) return false;
        if (entity.getScoreboardTags().stream().anyMatch(tag -> tag.startsWith("smpcore_custom_boss"))) return false;
        return plugin.getBossManager() == null || !plugin.getBossManager().isCustomBoss(entity);
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private LivingEntity causingLivingEntity(Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) return living;
        return null;
    }

    private boolean isTamingMaterial(Entity entity, ItemStack item) {
        if (item == null || item.getType().isAir() || !InventoryRecipeUtil.isPlainMaterial(plugin, item, item.getType())) return false;
        Material material = item.getType();
        if (entity instanceof Wolf) return material == Material.BONE;
        if (entity instanceof Cat) return material == Material.COD || material == Material.SALMON;
        if (entity instanceof Parrot) return Set.of(Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS, Material.TORCHFLOWER_SEEDS).contains(material);
        if (entity instanceof Llama || entity instanceof TraderLlama) return material == Material.WHEAT || material == Material.HAY_BLOCK;
        if (entity instanceof AbstractHorse) return material == Material.APPLE || material == Material.GOLDEN_CARROT || material == Material.GOLDEN_APPLE;
        return false;
    }

    private void consumeOne(Player player, EquipmentSlot hand, ItemStack held) {
        ItemStack next = held.getAmount() <= 1 ? null : held.asQuantity(held.getAmount() - 1);
        player.getInventory().setItem(hand, next);
    }

    private boolean familiarUnlocked(Player player, String id) {
        return switch (id) {
            case "veil_wisp" -> plugin.getMayorQuestManager() != null && plugin.getMayorQuestManager().hasPetUnlocked(player);
            case "miner" -> plugin.getMinerManager() != null && plugin.getMinerManager().hasMinerPet(player);
            case "tiller" -> plugin.getFarmerManager() != null && plugin.getFarmerManager().hasTiller(player);
            case "morrow" -> plugin.getWitchManager() != null && plugin.getWitchManager().hasMorrow(player);
            default -> false;
        };
    }

    private long treeMask(Player player, String familiarId) {
        NamespacedKey key = treeKeys.get(familiarId);
        return key == null || player == null ? 0L : player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L);
    }

    private boolean isEvolved(Player player, String familiarId) {
        NamespacedKey key = evolutionKeys.get(familiarId);
        return key != null && pdcByte(player, key);
    }

    private int questStage(Player player) {
        return Math.clamp(player.getPersistentDataContainer().getOrDefault(stageKey, PersistentDataType.INTEGER, 0), 0, QUESTS.size());
    }

    private double questProgress(Player player) {
        return sanitizedQuestProgress(
            player.getPersistentDataContainer().getOrDefault(progressKey, PersistentDataType.DOUBLE, 0.0D)
        );
    }

    static double sanitizedQuestProgress(double progress) {
        return Double.isFinite(progress) ? Math.max(0.0D, progress) : 0.0D;
    }

    private QuestKind currentQuestKind(Player player) {
        int stage = questStage(player);
        return stage >= QUESTS.size() ? null : QUESTS.get(stage).kind;
    }

    private boolean requirementMet(Player player, QuestStage quest) {
        return quest.kind == QuestKind.MATERIALS
            ? InventoryRecipeUtil.hasPlainMaterials(plugin, player, quest.materials)
            : questProgress(player) >= quest.target;
    }

    private int countPlain(Player player, Material material) {
        return InventoryRecipeUtil.countIngredient(player, InventoryRecipeUtil.plainMaterial(plugin, material, 1));
    }

    private int freeStorageSlots(Player player) {
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) free++;
        }
        return free;
    }

    private String progressText(Player player, QuestStage quest) {
        double progress = Math.min(quest.target, questProgress(player));
        return quest.kind == QuestKind.RIDE ? String.valueOf((int) Math.floor(progress)) : String.valueOf((int) progress);
    }

    private boolean isArmorPiece(ItemStack item, String piece) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return piece.equals(item.getItemMeta().getPersistentDataContainer().get(armorPieceKey, PersistentDataType.STRING));
    }

    private ItemStack item(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(CustomLoreUtil.wrapMiniMessageLines(lore).stream().map(MM::deserialize).map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private String action(ItemStack item) {
        return item == null || item.getType().isAir() || !item.hasItemMeta()
            ? null : item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private void fill(Inventory menu) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, "<black>.</black>", List.of(), null);
        for (int slot = 0; slot < menu.getSize(); slot++) menu.setItem(slot, filler);
    }

    private boolean pdcByte(Player player, NamespacedKey key) {
        return player != null && player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private static Map<Material, Integer> materials(Object... entries) {
        Map<Material, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            result.put((Material) entries[index], (Integer) entries[index + 1]);
        }
        return Map.copyOf(result);
    }

    private static String normalizeFamiliarId(String raw) {
        if (raw == null) return null;
        String id = raw.toLowerCase(Locale.ROOT).replace('-', '_').trim();
        return FAMILIARS.containsKey(id) ? id : null;
    }

    private static String pretty(Material material) {
        StringBuilder out = new StringBuilder();
        for (String part : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static String percent(double value) {
        return String.format(Locale.US, value * 100.0D % 1.0D == 0.0D ? "%.0f%%" : "%.1f%%", value * 100.0D);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double attributeValue(Player player, Attribute attribute, double fallback) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    private enum QuestKind { MATERIALS, TAME, BREED, HOSTILE_KILL, RIDE, RAVAGER }
    private enum SelectMode { TREE, EVOLUTION }
    public enum AdminPreviewView { MAIN, TREE, EVOLUTION }

    private record QuestStage(String title, String description, QuestKind kind, Map<Material, Integer> materials, int target) {
        static QuestStage materials(String title, String description, Map<Material, Integer> materials) {
            return new QuestStage(title, description, QuestKind.MATERIALS, materials, 0);
        }

        static QuestStage progress(String title, String description, QuestKind kind, int target) {
            return new QuestStage(title, description, kind, Map.of(), target);
        }

        Material icon() {
            return switch (kind) {
                case MATERIALS -> materials.keySet().stream().findFirst().orElse(Material.BUNDLE);
                case TAME -> Material.BONE;
                case BREED -> Material.WHEAT;
                case HOSTILE_KILL -> Material.IRON_SWORD;
                case RIDE -> Material.SADDLE;
                case RAVAGER -> Material.RAVAGER_SPAWN_EGG;
            };
        }
    }

    private record FamiliarDefinition(String id, String displayName, Material icon, Material material, int materialAmount) { }

    private record RideProgressSample(UUID worldId, UUID mountId, double x, double z) { }

    private sealed interface BeastMenuMarker extends InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder permits BeastMenuHolder, FamiliarSelectHolder, FamiliarTreeHolder, ConfirmHolder, AdminPreviewHolder {
        UUID playerId();

        @Override
        default Inventory getInventory() {
            return null;
        }
    }

    private record BeastMenuHolder(UUID playerId) implements BeastMenuMarker { }
    private record FamiliarSelectHolder(UUID playerId, SelectMode mode) implements BeastMenuMarker { }
    private record FamiliarTreeHolder(UUID playerId, String familiarId) implements BeastMenuMarker { }
    private record ConfirmHolder(UUID playerId, String familiarId, int node, boolean evolution) implements BeastMenuMarker { }
    private record AdminPreviewHolder(UUID playerId) implements BeastMenuMarker { }

    private static final class LocationEffects {
        private LocationEffects() { }

        static void tame(Player player, Entity entity) {
            entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0.0D, entity.getHeight() * 0.75D, 0.0D), 9, 0.4D, 0.4D, 0.4D, 0.02D);
            player.playSound(entity.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.45F);
        }
    }
}
