package me.rique.smpcore.quest;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BrewingStand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class WitchManager implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int[] QUEST_SLOTS = {10, 11, 12, 13, 14, 15, 16, 22};
    private static final int MIN_DURATION_BONUS_PERCENT = 15;
    private static final int MAX_DURATION_BONUS_PERCENT = 30;
    private static final int MAX_TRAINED_DURATION_BONUS_PERCENT = 36;
    private static final double EMPOWERED_THROW_CHANCE = 0.05D;
    public static final String WITCH_FAMILIAR_ID = "morrow";
    private static final UUID MORROW_PROFILE_ID = UUID.fromString("97cb7661-6088-4ff5-8417-a9abc45b59d1");
    private static final String MORROW_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0N2E1NjRiYjU4YmYyNDhlZjc3NzRiMjI3ZGU0NjgxZTk1Y2I4MjQ1YmQ4Mzg4ZDI4OGNiZjFlYzE3YTg4OCJ9fX0=";

    private static final List<QuestStage> STAGES = List.of(
        new QuestStage("Gathering the Hedge", "Build a proper shelf of overworld reagents.", Material.SPIDER_EYE,
            Map.of(Material.RED_MUSHROOM, 24, Material.BROWN_MUSHROOM, 24, Material.SPIDER_EYE, 16, Material.SUGAR, 32), List.of(), ProgressType.NONE, 0),
        new QuestStage("Nether Infusion", "Bring back the Nether ingredients needed for stronger brews.", Material.NETHER_WART,
            Map.of(Material.NETHER_WART, 32, Material.BLAZE_POWDER, 16, Material.MAGMA_CREAM, 12, Material.GHAST_TEAR, 8), List.of(), ProgressType.NONE, 0),
        new QuestStage("Three Foundations", "Brew three dependable families of ordinary potions.", Material.POTION,
            Map.of(), List.of(
                new PotionRequirement(Material.POTION, PotionType.SWIFTNESS, 4),
                new PotionRequirement(Material.POTION, PotionType.HEALING, 4),
                new PotionRequirement(Material.POTION, PotionType.NIGHT_VISION, 4)
            ), ProgressType.NONE, 0),
        new QuestStage("Volatile Lesson", "Throw twenty splash or lingering potions without wasting the bottle.", Material.SPLASH_POTION,
            Map.of(), List.of(), ProgressType.THROWN_POTION, 20),
        new QuestStage("Lingering Proof", "Prepare clouds for pursuit, retreat, and control.", Material.LINGERING_POTION,
            Map.of(), List.of(
                new PotionRequirement(Material.LINGERING_POTION, PotionType.LONG_POISON, 3),
                new PotionRequirement(Material.LINGERING_POTION, PotionType.LONG_WEAKNESS, 3),
                new PotionRequirement(Material.LINGERING_POTION, PotionType.LONG_SWIFTNESS, 3)
            ), ProgressType.NONE, 0),
        new QuestStage("Rare Catalysts", "Collect uncommon ingredients for advanced potion effects.", Material.RABBIT_FOOT,
            Map.of(Material.RABBIT_FOOT, 8, Material.TURTLE_SCUTE, 8, Material.PHANTOM_MEMBRANE, 12, Material.PUFFERFISH, 8), List.of(), ProgressType.NONE, 0),
        new QuestStage("Controlled Reactions", "Finish twenty-four valid vanilla brews under Vespera's rules.", Material.BREWING_STAND,
            Map.of(), List.of(), ProgressType.BREWED_POTION, 24),
        new QuestStage("Moonlit Thesis", "Submit four advanced brews and the final catalyst.", Material.DRAGON_BREATH,
            Map.of(Material.DRAGON_BREATH, 8), List.of(
                new PotionRequirement(Material.POTION, PotionType.STRONG_STRENGTH, 4),
                new PotionRequirement(Material.POTION, PotionType.LONG_FIRE_RESISTANCE, 4),
                new PotionRequirement(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 4),
                new PotionRequirement(Material.LINGERING_POTION, PotionType.LONG_INVISIBILITY, 4)
            ), ProgressType.NONE, 0)
    );

    private final SMPCore plugin;
    private final NamespacedKey stageKey;
    private final NamespacedKey progressKey;
    private final NamespacedKey petKey;
    private final NamespacedKey petHiddenKey;
    private final NamespacedKey bossBrewingKey;
    private final NamespacedKey activeFamiliarKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey lastBrewerKey;
    private final NamespacedKey durationBonusKey;
    private final NamespacedKey originalPotionTypeKey;
    private final FamiliarFollower follower;

    public WitchManager(SMPCore plugin) {
        this.plugin = plugin;
        this.stageKey = new NamespacedKey(plugin, "witch_quest_stage");
        this.progressKey = new NamespacedKey(plugin, "witch_quest_progress");
        this.petKey = new NamespacedKey(plugin, "morrow_familiar_unlocked");
        this.petHiddenKey = new NamespacedKey(plugin, "morrow_familiar_hidden");
        this.bossBrewingKey = new NamespacedKey(plugin, "boss_brewing_unlocked");
        this.activeFamiliarKey = new NamespacedKey(plugin, "active_familiar");
        this.actionKey = new NamespacedKey(plugin, "witch_menu_action");
        this.lastBrewerKey = new NamespacedKey(plugin, "witch_last_brewer");
        this.durationBonusKey = new NamespacedKey(plugin, "morrow_duration_bonus");
        this.originalPotionTypeKey = new NamespacedKey(plugin, "morrow_original_potion_type");
        this.follower = new FamiliarFollower(
            plugin,
            "morrow_familiar_owner",
            "Morrow",
            NamedTextColor.LIGHT_PURPLE,
            Particle.WITCH,
            this::morrowHead,
            this::hasActiveMorrow
        );
    }

    public void start() {
        follower.start();
    }

    public void shutdown() {
        follower.shutdown();
    }

    public void openFromWitch(Player player) {
        openQuestMenu(player);
    }

    public void openQuestMenu(Player player) {
        int stage = questStage(player);
        Inventory menu = Bukkit.createInventory(new WitchMenuHolder(player.getUniqueId()), 45,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#a855f7:#22d3ee><bold>Vespera the Hedge-Witch</bold></gradient>"), "Vespera"));
        fill(menu);
        menu.setItem(4, item(Material.CAULDRON, "<light_purple><bold>The Moonlit Thesis</bold></light_purple>", List.of(
            "<gray>Eight lessons in reagents, brewing, and delivery.</gray>",
            "<gray>Final reward: <white>Morrow the Moonmoth</white>.</gray>",
            "<gold>Final unlock: boss-material potion brewing.</gold>"
        ), null));
        for (int i = 0; i < STAGES.size(); i++) {
            QuestStage quest = STAGES.get(i);
            boolean done = i < stage;
            boolean current = i == stage;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + quest.description() + "</gray>");
            quest.materials().forEach((material, amount) -> lore.add("<gray>- <white>" + amount + " " + pretty(material) + "</white></gray>"));
            quest.potions().forEach(requirement -> lore.add("<gray>- <white>" + requirement.amount() + " " + potionName(requirement) + "</white></gray>"));
            if (quest.progressType() != ProgressType.NONE) {
                int progress = current ? questProgress(player) : done ? quest.progressTarget() : 0;
                lore.add("<gray>Progress: <white>" + progress + "/" + quest.progressTarget() + "</white>.</gray>");
            }
            if (done) lore.add("<green>Complete.</green>");
            else if (current) lore.add(canComplete(player, quest) ? "<green>Click to complete.</green>" : "<yellow>Still in progress.</yellow>");
            else lore.add("<dark_gray>Complete the earlier lesson first.</dark_gray>");
            menu.setItem(QUEST_SLOTS[i], item(
                done ? Material.LIME_STAINED_GLASS_PANE : current ? quest.icon() : Material.GRAY_STAINED_GLASS_PANE,
                done ? "<green><bold>" + quest.title() + "</bold></green>" : current ? "<light_purple><bold>" + quest.title() + "</bold></light_purple>" : "<dark_gray><bold>Locked</bold></dark_gray>",
                lore,
                current ? "turn_in:" + i : null
            ));
        }
        menu.setItem(31, applyMorrowHeadTexture(item(Material.PLAYER_HEAD,
            hasMorrow(player) ? "<light_purple><bold>Morrow, the Rosy Moonmoth</bold></light_purple>" : "<dark_gray><bold>Morrow, the Rosy Moonmoth</bold></dark_gray>",
            hasMorrow(player) ? familiarLore() : List.of("<gray>Complete the Moonlit Thesis to unlock.</gray>"),
            hasMorrow(player) ? "pet" : null)));
        menu.setItem(40, item(Material.BARRIER, "<red><bold>Close</bold></red>", List.of(), "close"));
        player.openInventory(menu);
    }

    public void openPetMenu(Player player) {
        Inventory menu = Bukkit.createInventory(new WitchPetMenuHolder(player.getUniqueId()), 27,
            BedrockCompat.menuTitle(player, MM.deserialize("<light_purple><bold>Morrow, the Rosy Moonmoth</bold></light_purple>"), "Morrow"));
        fill(menu);
        ItemStack head = morrowHead();
        ItemMeta meta = head.getItemMeta();
        meta.displayName(MM.deserialize("<light_purple><bold>Morrow, the Rosy Moonmoth</bold></light_purple>"));
        meta.lore(CustomLoreUtil.wrapMiniMessageLines(familiarLore()).stream().map(MM::deserialize).toList());
        head.setItemMeta(meta);
        menu.setItem(4, head);
        if (hasMorrow(player)) {
            FamiliarMenuState state = FamiliarMenuState.from(hasActiveMorrow(player));
            menu.setItem(11, item(state.summonIcon(),
                state.canSummon() ? "<green><bold>Summon</bold></green>" : "<green><bold>Currently Summoned</bold></green>",
                state.canSummon()
                    ? List.of("<gray>Bring Morrow out and enable potion blessings.</gray>")
                    : List.of("<gray>Morrow is following you.</gray>", "<dark_gray>Right-click to interact. Sneak-right-click to manage.</dark_gray>"),
                state.summonAction("summon")));
            menu.setItem(15, item(state.dismissIcon(),
                state.canDismiss() ? "<red><bold>Dismiss</bold></red>" : "<dark_gray><bold>Already Dismissed</bold></dark_gray>",
                state.canDismiss()
                    ? List.of("<gray>Hide Morrow and pause its bonuses.</gray>")
                    : List.of("<gray>Morrow is already resting.</gray>"),
                state.dismissAction("dismiss")));
        }
        menu.setItem(22, item(Material.ARROW, "<yellow><bold>Back</bold></yellow>", List.of("<gray>Return to your familiar stable.</gray>"), "familiars"));
        player.openInventory(menu);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof WitchMenuHolder || holder instanceof WitchPetMenuHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT) return;
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            String action = action(event.getCurrentItem());
            if (action != null) Bukkit.getScheduler().runTask(plugin, () -> handleAction(player, action));
            return;
        }
        if (event.getView().getTopInventory() instanceof BrewerInventory brewer && event.getWhoClicked() instanceof Player player) {
            markBrewer(brewer, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof WitchMenuHolder || holder instanceof WitchPetMenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory() instanceof BrewerInventory brewer && event.getWhoClicked() instanceof Player player) {
            markBrewer(brewer, player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewerOpen(InventoryOpenEvent event) {
        if (event.getInventory() instanceof BrewerInventory brewer && event.getPlayer() instanceof Player player) {
            markBrewer(brewer, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Player brewer = lastBrewer(event.getContents());
        if (brewer == null) return;
        int ordinaryResults = 0;
        for (int i = 0; i < event.getResults().size(); i++) {
            ItemStack result = event.getResults().get(i);
            if (!isOrdinaryVanillaPotion(result)) continue;
            ordinaryResults += Math.max(1, result.getAmount());
            if (hasActiveMorrow(brewer)) event.getResults().set(i, extendPotion(brewer, result));
        }
        recordProgress(brewer, ProgressType.BREWED_POTION, ordinaryResults);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionLaunch(PlayerLaunchProjectileEvent event) {
        if (!(event.getProjectile() instanceof ThrownPotion thrown)) return;
        ItemStack launched = event.getItemStack();
        if (launched.getType() != Material.SPLASH_POTION && launched.getType() != Material.LINGERING_POTION) return;
        if (isBossPotion(launched)) return;
        recordProgress(event.getPlayer(), ProgressType.THROWN_POTION, 1);
        if (!hasActiveMorrow(event.getPlayer())) return;
        ItemStack empowered = empoweredPotion(launched);
        if (empowered == null || ThreadLocalRandom.current().nextDouble() >= coreChance(event.getPlayer(), EMPOWERED_THROW_CHANCE)) return;
        thrown.setItem(empowered);
        event.getPlayer().sendActionBar(MM.deserialize("<light_purple>Morrow empowered the thrown potion by one level.</light_purple>"));
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.55F, 1.45F);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (hasActiveMorrow(event.getPlayer())) follower.spawn(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        follower.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !follower.isOwnedHitbox(event.getRightClicked(), event.getPlayer())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            openPetMenu(player);
            return;
        }
        if (!follower.beginInteraction(player)) return;
        player.sendMessage(MessageUtil.info("<light_purple>Morrow</light_purple> settles on your hand and shakes loose a little moon-dust."));
        player.playSound(event.getRightClicked().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45F, 1.7F);
        player.spawnParticle(Particle.WITCH, event.getRightClicked().getLocation().add(0.0D, 0.45D, 0.0D), 7, 0.22D, 0.18D, 0.22D, 0.01D);
    }

    public boolean hasMorrow(Player player) {
        return pdcByte(player, petKey);
    }

    public boolean hasBossBrewingUnlocked(Player player) {
        return pdcByte(player, bossBrewingKey);
    }

    public boolean hasActiveMorrow(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) return false;
        ensureActiveFamiliar(player);
        return hasMorrow(player) && !isPetHidden(player) && WITCH_FAMILIAR_ID.equals(activeFamiliar(player));
    }

    public double familiarCoreMultiplier(Player player) {
        return plugin.getBeastwardenManager() == null
            ? 1.0D : plugin.getBeastwardenManager().familiarCoreMultiplier(player, WITCH_FAMILIAR_ID);
    }

    private double coreChance(Player player, double baseChance) {
        return Math.min(1.0D, Math.max(0.0D, baseChance) * familiarCoreMultiplier(player));
    }

    public void grantMorrow(Player player, CommandSender actor) {
        if (player == null) return;
        boolean already = hasMorrow(player);
        player.getPersistentDataContainer().set(petKey, PersistentDataType.BYTE, (byte) 1);
        player.getPersistentDataContainer().set(bossBrewingKey, PersistentDataType.BYTE, (byte) 1);
        selectMorrow(player);
        follower.spawn(player);
        player.sendMessage(already ? MessageUtil.info("Your <white>Morrow</white> familiar was restored.") : MessageUtil.success("You unlocked <white>Morrow, the Rosy Moonmoth</white> and boss brewing."));
        if (actor != null && !actor.equals(player)) actor.sendMessage(MessageUtil.success("Gave <white>" + player.getName() + "</white> Morrow and boss brewing."));
    }

    public void revokeMorrow(Player player, CommandSender actor) {
        if (player == null) return;
        player.getPersistentDataContainer().remove(petKey);
        player.getPersistentDataContainer().remove(petHiddenKey);
        if (WITCH_FAMILIAR_ID.equals(activeFamiliar(player))) player.getPersistentDataContainer().remove(activeFamiliarKey);
        follower.remove(player);
        player.sendMessage(MessageUtil.warn("Your <white>Morrow</white> familiar was removed. Earned boss-brewing access was kept."));
        if (actor != null && !actor.equals(player)) actor.sendMessage(MessageUtil.success("Removed <white>" + player.getName() + "</white>'s Morrow familiar."));
    }

    public void deactivateForFamiliarSwitch(Player player) {
        if (player == null) return;
        player.getPersistentDataContainer().set(petHiddenKey, PersistentDataType.BYTE, (byte) 1);
        follower.remove(player);
    }

    static int extendedDuration(int originalTicks, int bonusPercent) {
        int safePercent = Math.clamp(bonusPercent, MIN_DURATION_BONUS_PERCENT, MAX_TRAINED_DURATION_BONUS_PERCENT);
        return Math.max(originalTicks, (int) Math.round(originalTicks * (1.0D + safePercent / 100.0D)));
    }

    static int upgradedAmplifier(int currentAmplifier, int maxLevel) {
        return Math.min(Math.max(0, maxLevel - 1), currentAmplifier + 1);
    }

    private void handleAction(Player player, String action) {
        if (action.startsWith("turn_in:")) {
            try {
                turnIn(player, Integer.parseInt(action.substring("turn_in:".length())));
            } catch (NumberFormatException ignored) {
            }
            return;
        }
        switch (action) {
            case "pet" -> openPetMenu(player);
            case "summon" -> {
                if (!hasActiveMorrow(player)) {
                    selectMorrow(player);
                    follower.spawn(player);
                }
                openPetMenu(player);
            }
            case "dismiss" -> {
                if (hasActiveMorrow(player)) {
                    player.getPersistentDataContainer().set(petHiddenKey, PersistentDataType.BYTE, (byte) 1);
                    if (WITCH_FAMILIAR_ID.equals(activeFamiliar(player))) player.getPersistentDataContainer().remove(activeFamiliarKey);
                    follower.remove(player);
                }
                openPetMenu(player);
            }
            case "familiars" -> {
                if (plugin.getMayorQuestManager() != null) plugin.getMayorQuestManager().openPetCollectionMenu(player);
            }
            case "close" -> player.closeInventory();
            default -> {
            }
        }
    }

    private void turnIn(Player player, int expectedStage) {
        int stage = questStage(player);
        if (stage != expectedStage || stage >= STAGES.size()) {
            openQuestMenu(player);
            return;
        }
        QuestStage quest = STAGES.get(stage);
        if (quest.progressType() != ProgressType.NONE && questProgress(player) < quest.progressTarget()) {
            player.sendMessage(MessageUtil.warn("Progress is <white>" + questProgress(player) + "/" + quest.progressTarget() + "</white>."));
            return;
        }
        if (!quest.materials().isEmpty() && !InventoryRecipeUtil.hasPlainMaterials(plugin, player, quest.materials())) {
            player.sendMessage(MessageUtil.warn("You do not have every reagent in this lesson yet."));
            return;
        }
        if (!hasPotionRequirements(player, quest.potions())) {
            player.sendMessage(MessageUtil.warn("One or more required vanilla potions are missing."));
            return;
        }
        if (!quest.materials().isEmpty() && !InventoryRecipeUtil.removePlainMaterials(plugin, player, quest.materials())) {
            player.sendMessage(MessageUtil.error("Your inventory changed before Vespera could accept the reagents."));
            return;
        }
        if (!removePotionRequirements(player, quest.potions())) {
            player.sendMessage(MessageUtil.error("Your potion inventory changed before the lesson could be sealed."));
            return;
        }
        int next = stage + 1;
        player.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, next);
        player.getPersistentDataContainer().remove(progressKey);
        if (next == STAGES.size()) unlockFinalReward(player);
        if (plugin.getStoryService() != null) {
            plugin.getStoryService().onQuestStage(player, "witch", quest.title(), next);
        }
        player.sendMessage(MessageUtil.success("Lesson complete: <white>" + quest.title() + "</white>."));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.75F, 1.3F);
        openQuestMenu(player);
    }

    private void unlockFinalReward(Player player) {
        player.getPersistentDataContainer().set(petKey, PersistentDataType.BYTE, (byte) 1);
        player.getPersistentDataContainer().set(bossBrewingKey, PersistentDataType.BYTE, (byte) 1);
        selectMorrow(player);
        follower.spawn(player);
        if (plugin.getStoryService() != null) plugin.getStoryService().onFamiliarUnlocked(player, "morrow");
        player.showTitle(Title.title(
            MM.deserialize("<light_purple><bold>MORROW</bold></light_purple>"),
            MM.deserialize("<aqua>Morrow and boss-brew recipes are now unlocked.</aqua>"),
            Title.Times.times(Duration.ofMillis(450), Duration.ofMillis(3600), Duration.ofMillis(950))
        ));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.05F);
        player.sendMessage(MessageUtil.success("Boss-material potion recipes are now unlocked for you."));
    }

    private ItemStack extendPotion(Player brewer, ItemStack source) {
        if (!isOrdinaryVanillaPotion(source) || !(source.getItemMeta() instanceof PotionMeta originalMeta)) return source;
        List<PotionEffect> effects = originalMeta.getAllEffects();
        if (effects.isEmpty() || effects.stream().allMatch(effect -> effect.getDuration() <= 1)) return source;
        int baseBonus = ThreadLocalRandom.current().nextInt(MIN_DURATION_BONUS_PERCENT, MAX_DURATION_BONUS_PERCENT + 1);
        int bonus = Math.min(MAX_TRAINED_DURATION_BONUS_PERCENT, (int) Math.round(baseBonus * familiarCoreMultiplier(brewer)));
        PotionType originalType = originalMeta.getBasePotionType();
        Color color = originalMeta.computeEffectiveColor();
        ItemStack result = source.clone();
        PotionMeta meta = (PotionMeta) result.getItemMeta();
        meta.setBasePotionType(PotionType.AWKWARD);
        meta.clearCustomEffects();
        for (PotionEffect effect : effects) {
            int duration = effect.getDuration() <= 1 ? effect.getDuration() : extendedDuration(effect.getDuration(), bonus);
            meta.addCustomEffect(new PotionEffect(
                effect.getType(), duration, effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()
            ), true);
        }
        meta.setColor(color);
        meta.displayName(MM.deserialize("<light_purple>" + escapedPotionDisplayName(source.getType(), originalType) + "</light_purple>").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.removeIf(line -> PLAIN.serialize(line).startsWith("Morrow's Infusion:"));
        lore.add(MM.deserialize("<light_purple>Morrow's Infusion:</light_purple> <white>+" + bonus + "% duration</white>"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(durationBonusKey, PersistentDataType.INTEGER, bonus);
        meta.getPersistentDataContainer().set(originalPotionTypeKey, PersistentDataType.STRING, originalType.name());
        result.setItemMeta(meta);
        return result;
    }

    private ItemStack empoweredPotion(ItemStack source) {
        if (!(source.getItemMeta() instanceof PotionMeta sourceMeta) || isBossPotion(source)) return null;
        List<PotionEffect> effects = sourceMeta.getAllEffects();
        if (effects.isEmpty()) return null;
        boolean eligible = false;
        List<PotionEffect> upgraded = new ArrayList<>();
        for (PotionEffect effect : effects) {
            PotionType family = upgradeableFamily(effect);
            int nextAmplifier = effect.getAmplifier();
            if (family != null && family.isUpgradeable() && effect.getAmplifier() < family.getMaxLevel() - 1) {
                nextAmplifier = upgradedAmplifier(effect.getAmplifier(), family.getMaxLevel());
                eligible = true;
            }
            upgraded.add(new PotionEffect(
                effect.getType(), effect.getDuration(), nextAmplifier, effect.isAmbient(), effect.hasParticles(), effect.hasIcon()
            ));
        }
        if (!eligible) return null;
        ItemStack result = source.clone();
        PotionMeta meta = (PotionMeta) result.getItemMeta();
        Color color = meta.computeEffectiveColor();
        meta.setBasePotionType(PotionType.AWKWARD);
        meta.clearCustomEffects();
        upgraded.forEach(effect -> meta.addCustomEffect(effect, true));
        meta.setColor(color);
        result.setItemMeta(meta);
        return result;
    }

    private boolean isOrdinaryVanillaPotion(ItemStack item) {
        if (item == null || !isPotionContainer(item.getType()) || !(item.getItemMeta() instanceof PotionMeta meta)) return false;
        return !isBossPotion(item)
            && !meta.hasCustomEffects()
            && meta.hasBasePotionType()
            && meta.getBasePotionType() != PotionType.AWKWARD
            && !meta.getAllEffects().isEmpty();
    }

    private PotionType upgradeableFamily(PotionEffect effect) {
        for (PotionType candidate : PotionType.values()) {
            if (!candidate.isUpgradeable()) continue;
            if (candidate.getPotionEffects().stream().anyMatch(base -> base.getType().equals(effect.getType()))) return candidate;
        }
        return null;
    }

    private boolean isBossPotion(ItemStack item) {
        return plugin.getBossPotionListener() != null && plugin.getBossPotionListener().isBossPotion(item);
    }

    private void markBrewer(BrewerInventory inventory, Player player) {
        BrewingStand stand = inventory == null ? null : inventory.getHolder();
        if (stand == null || player == null) return;
        stand.getPersistentDataContainer().set(lastBrewerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        stand.update(true, false);
    }

    private Player lastBrewer(BrewerInventory inventory) {
        BrewingStand stand = inventory == null ? null : inventory.getHolder();
        if (stand == null) return null;
        String raw = stand.getPersistentDataContainer().get(lastBrewerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return Bukkit.getPlayer(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean canComplete(Player player, QuestStage quest) {
        if (quest.progressType() != ProgressType.NONE && questProgress(player) < quest.progressTarget()) return false;
        if (!quest.materials().isEmpty() && !InventoryRecipeUtil.hasPlainMaterials(plugin, player, quest.materials())) return false;
        return hasPotionRequirements(player, quest.potions());
    }

    private boolean hasPotionRequirements(Player player, List<PotionRequirement> requirements) {
        for (PotionRequirement requirement : requirements) {
            if (countPotions(player, requirement) < requirement.amount()) return false;
        }
        return true;
    }

    private int countPotions(Player player, PotionRequirement requirement) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (matchesPotion(item, requirement)) count += item.getAmount();
        }
        return count;
    }

    private boolean removePotionRequirements(Player player, List<PotionRequirement> requirements) {
        if (requirements.isEmpty()) return true;
        ItemStack[] next = player.getInventory().getStorageContents();
        for (PotionRequirement requirement : requirements) {
            int remaining = requirement.amount();
            for (int slot = 0; slot < next.length && remaining > 0; slot++) {
                ItemStack item = next[slot];
                if (!matchesPotion(item, requirement)) continue;
                int taken = Math.min(remaining, item.getAmount());
                remaining -= taken;
                if (taken == item.getAmount()) next[slot] = null;
                else {
                    ItemStack reduced = item.clone();
                    reduced.setAmount(item.getAmount() - taken);
                    next[slot] = reduced;
                }
            }
            if (remaining > 0) return false;
        }
        player.getInventory().setStorageContents(next);
        return true;
    }

    private boolean matchesPotion(ItemStack item, PotionRequirement requirement) {
        if (item == null || item.getType() != requirement.container() || !(item.getItemMeta() instanceof PotionMeta meta)) return false;
        return !isBossPotion(item) && !meta.hasCustomEffects() && meta.getBasePotionType() == requirement.type();
    }

    private void recordProgress(Player player, ProgressType type, int amount) {
        int stage = questStage(player);
        if (stage >= STAGES.size() || amount <= 0) return;
        QuestStage quest = STAGES.get(stage);
        if (quest.progressType() != type) return;
        int current = questProgress(player);
        int next = Math.min(quest.progressTarget(), current + amount);
        if (next == current) return;
        player.getPersistentDataContainer().set(progressKey, PersistentDataType.INTEGER, next);
        if (next == quest.progressTarget()) {
            player.sendMessage(MessageUtil.success("Lesson ready: <white>" + quest.title() + "</white>. Return to Vespera."));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65F, 1.45F);
        }
    }

    private void selectMorrow(Player player) {
        if (plugin.getMayorQuestManager() != null) plugin.getMayorQuestManager().deactivateForFamiliarSwitch(player);
        if (plugin.getMinerManager() != null) plugin.getMinerManager().deactivateForFamiliarSwitch(player);
        if (plugin.getFarmerManager() != null) plugin.getFarmerManager().deactivateForFamiliarSwitch(player);
        player.getPersistentDataContainer().set(activeFamiliarKey, PersistentDataType.STRING, WITCH_FAMILIAR_ID);
        player.getPersistentDataContainer().remove(petHiddenKey);
    }

    private void ensureActiveFamiliar(Player player) {
        if (activeFamiliar(player) != null) return;
        if (hasMorrow(player) && !pdcByte(player, petHiddenKey)) {
            player.getPersistentDataContainer().set(activeFamiliarKey, PersistentDataType.STRING, WITCH_FAMILIAR_ID);
        }
    }

    private boolean isPetHidden(Player player) {
        return pdcByte(player, petHiddenKey) || !WITCH_FAMILIAR_ID.equals(activeFamiliar(player));
    }

    private String activeFamiliar(Player player) {
        return player.getPersistentDataContainer().get(activeFamiliarKey, PersistentDataType.STRING);
    }

    private int questStage(Player player) {
        Integer value = player.getPersistentDataContainer().get(stageKey, PersistentDataType.INTEGER);
        return Math.clamp(value == null ? 0 : value, 0, STAGES.size());
    }

    private int questProgress(Player player) {
        Integer value = player.getPersistentDataContainer().get(progressKey, PersistentDataType.INTEGER);
        return Math.max(0, value == null ? 0 : value);
    }

    private boolean pdcByte(Player player, NamespacedKey key) {
        if (player == null) return false;
        Byte value = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private List<String> familiarLore() {
        return List.of(
            "<gray>Moonlit Infusion: every vanilla brew gains <white>15-30%</white> duration.</gray>",
            "<gray>Unstable Grace: <white>5%</white> chance a thrown potion rises one valid level.</gray>",
            "<dark_gray>Already-maxed and boss potions are never upgraded.</dark_gray>",
            "<yellow>Click to manage.</yellow>"
        );
    }

    private ItemStack morrowHead() {
        return applyMorrowHeadTexture(new ItemStack(Material.PLAYER_HEAD));
    }

    public ItemStack applyMorrowHeadTexture(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return item;
        ItemMeta itemMeta = item.getItemMeta();
        if (!(itemMeta instanceof SkullMeta meta)) return item;
        PlayerProfile profile = Bukkit.createProfile(MORROW_PROFILE_ID, "MorrowMoonmoth");
        profile.setProperty(new ProfileProperty("textures", MORROW_TEXTURE));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(CustomLoreUtil.wrapMiniMessageLines(lore).stream()
            .map(line -> MM.deserialize(line).decoration(TextDecoration.ITALIC, false))
            .toList());
        if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory menu) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), null);
        for (int i = 0; i < menu.getSize(); i++) menu.setItem(i, pane);
    }

    private String action(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null : item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private String potionName(PotionRequirement requirement) {
        return escapedPotionDisplayName(requirement.container(), requirement.type()).replace("\\<", "<");
    }

    private String escapedPotionDisplayName(Material container, PotionType type) {
        String prefix = switch (container) {
            case SPLASH_POTION -> "Splash Potion of ";
            case LINGERING_POTION -> "Lingering Potion of ";
            default -> "Potion of ";
        };
        String raw = type.name();
        if (raw.startsWith("LONG_")) raw = raw.substring(5);
        if (raw.startsWith("STRONG_")) raw = raw.substring(7);
        String[] words = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder(prefix);
        for (int i = 0; i < words.length; i++) {
            if (i > 0) out.append(' ');
            out.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return out.toString().replace("<", "\\<");
    }

    private boolean isPotionContainer(Material material) {
        return material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION;
    }

    private String pretty(Material material) {
        String value = material.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private enum ProgressType {
        NONE,
        THROWN_POTION,
        BREWED_POTION
    }

    private record PotionRequirement(Material container, PotionType type, int amount) {
    }

    private record QuestStage(
        String title,
        String description,
        Material icon,
        Map<Material, Integer> materials,
        List<PotionRequirement> potions,
        ProgressType progressType,
        int progressTarget
    ) {
    }

    private record WitchMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record WitchPetMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
