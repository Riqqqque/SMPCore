package me.rique.smpcore.quest;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("UnstableApiUsage")
public final class FarmerManager implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int[] QUEST_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23};
    private static final int FIELD_ARC_END = 4;
    private static final int KITCHEN_ARC_END = 8;
    private static final double CROP_BONUS_CHANCE = 0.10D;
    private static final double EXTRA_SERVING_CHANCE = 0.10D;
    private static final double HEARTY_FOOD_CHANCE = 0.05D;
    public static final String FARMER_FAMILIAR_ID = "tiller";
    private static final UUID TILLER_PROFILE_ID = UUID.fromString("ab321dae-2c54-44ec-8bd3-785442784f33");
    private static final String TILLER_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTY1NjI3NGRjMjM1MGQ1MjdiOWU1ODg2ODk0NmM2MGYwNjcyN2E4MDEzZWY1Y2EzMmVhZGYxZmU3MmQ5ODg2NyJ9fX0=";

    private static final List<QuestStage> STAGES = List.of(
        new QuestStage("Soil and Seed", "Stock a field with more than one crop.", Material.WHEAT_SEEDS,
            Map.of(Material.WHEAT_SEEDS, 48, Material.CARROT, 24, Material.POTATO, 24), ProgressType.NONE, 0, false),
        new QuestStage("Patient Rows", "Harvest mature crops instead of tearing up young rows.", Material.IRON_HOE,
            Map.of(), ProgressType.HARVEST, 96, false),
        new QuestStage("Orchard and Vine", "Bring in fruit from vines, bushes, and gourds.", Material.MELON_SLICE,
            Map.of(Material.MELON_SLICE, 32, Material.PUMPKIN, 16, Material.SWEET_BERRIES, 32, Material.GLOW_BERRIES, 16), ProgressType.NONE, 0, false),
        new QuestStage("Granary Oath", "Fill Rowan's winter granary and earn his field tool.", Material.HAY_BLOCK,
            Map.of(Material.WHEAT, 64, Material.BEETROOT, 24, Material.HAY_BLOCK, 12), ProgressType.NONE, 0, false),
        new QuestStage("Daily Bread", "Prepare reliable food for a working settlement.", Material.BREAD,
            Map.of(Material.BREAD, 32, Material.BAKED_POTATO, 24, Material.COOKIE, 16), ProgressType.NONE, 0, false),
        new QuestStage("Stockpot Practice", "Master recipes that use more than a furnace.", Material.RABBIT_STEW,
            Map.of(Material.MUSHROOM_STEW, 6, Material.RABBIT_STEW, 6, Material.BEETROOT_SOUP, 8), ProgressType.NONE, 0, false),
        new QuestStage("Hearth and Grill", "Build a balanced store of cooked proteins.", Material.COOKED_BEEF,
            Map.of(Material.COOKED_BEEF, 16, Material.COOKED_CHICKEN, 16, Material.COOKED_PORKCHOP, 16, Material.COOKED_SALMON, 16), ProgressType.NONE, 0, false),
        new QuestStage("Harvest Banquet", "Prepare a full table of food for the town.", Material.CAKE,
            Map.of(Material.GOLDEN_CARROT, 8, Material.CAKE, 4, Material.PUMPKIN_PIE, 16, Material.COOKED_RABBIT, 8), ProgressType.NONE, 0, false),
        new QuestStage("Kitchen Rhythm", "Craft ninety-six servings with Tiller beside you.", Material.BOWL,
            Map.of(), ProgressType.FOOD_CRAFT, 96, true),
        new QuestStage("The Full Larder", "Collect preserves and travel food from every corner.", Material.HONEY_BOTTLE,
            Map.of(Material.APPLE, 32, Material.DRIED_KELP, 32, Material.HONEY_BOTTLE, 8, Material.COOKED_COD, 16), ProgressType.NONE, 0, true),
        new QuestStage("Harvest Moon", "Bring in one last large harvest with your familiar.", Material.JACK_O_LANTERN,
            Map.of(), ProgressType.HARVEST, 192, true),
        new QuestStage("First Feast", "Lay out the final feast and unlock Tiller's last bonus.", Material.GOLDEN_CARROT,
            Map.of(Material.CAKE, 4, Material.GOLDEN_CARROT, 16, Material.BREAD, 32, Material.PUMPKIN_PIE, 16), ProgressType.NONE, 0, true)
    );

    private final SMPCore plugin;
    private final NamespacedKey stageKey;
    private final NamespacedKey progressKey;
    private final NamespacedKey hoeRewardKey;
    private final NamespacedKey specialHoeKey;
    private final NamespacedKey petKey;
    private final NamespacedKey petHiddenKey;
    private final NamespacedKey masteryKey;
    private final NamespacedKey activeFamiliarKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey heartyFoodKey;
    private final FamiliarFollower follower;

    public FarmerManager(SMPCore plugin) {
        this.plugin = plugin;
        this.stageKey = new NamespacedKey(plugin, "farmer_quest_stage");
        this.progressKey = new NamespacedKey(plugin, "farmer_quest_progress");
        this.hoeRewardKey = new NamespacedKey(plugin, "farmer_hoe_reward");
        this.specialHoeKey = new NamespacedKey(plugin, "rowans_field_hoe");
        this.petKey = new NamespacedKey(plugin, "tiller_familiar_unlocked");
        this.petHiddenKey = new NamespacedKey(plugin, "tiller_familiar_hidden");
        this.masteryKey = new NamespacedKey(plugin, "tiller_hearty_food_unlocked");
        this.activeFamiliarKey = new NamespacedKey(plugin, "active_familiar");
        this.actionKey = new NamespacedKey(plugin, "farmer_menu_action");
        this.heartyFoodKey = new NamespacedKey(plugin, "hearty_food_quality");
        this.follower = new FamiliarFollower(
            plugin,
            "tiller_familiar_owner",
            "Tiller",
            NamedTextColor.GREEN,
            Particle.HAPPY_VILLAGER,
            this::tillerHead,
            this::hasActiveTiller
        );
    }

    public void start() {
        follower.start();
    }

    public void shutdown() {
        follower.shutdown();
    }

    public void openFromFarmer(Player player) {
        openQuestMenu(player);
    }

    public void openQuestMenu(Player player) {
        int stage = questStage(player);
        Inventory menu = Bukkit.createInventory(new FarmerMenuHolder(player.getUniqueId()), 54,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#84cc16:#facc15><bold>Rowan the Farmer</bold></gradient>"), "Rowan the Farmer"));
        fill(menu);
        menu.setItem(4, item(Material.GOLDEN_HOE, "<green><bold>From Furrow to Feast</bold></green>", List.of(
            "<gray>Chapter I: field trials and Rowan's special hoe.</gray>",
            "<gray>Chapter II: kitchen commissions and Tiller.</gray>",
            "<gray>Chapter III: awaken Tiller's hearty-food gift.</gray>"
        ), null));
        for (int i = 0; i < STAGES.size(); i++) {
            QuestStage quest = STAGES.get(i);
            boolean done = i < stage;
            boolean current = i == stage;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + quest.description() + "</gray>");
            lore.add("<dark_gray>" + chapterName(i) + "</dark_gray>");
            quest.cost().forEach((material, amount) -> lore.add("<gray>- <white>" + amount + " " + pretty(material) + "</white></gray>"));
            if (quest.progressType() != ProgressType.NONE) {
                int progress = current ? questProgress(player) : done ? quest.progressTarget() : 0;
                lore.add("<gray>Progress: <white>" + progress + "/" + quest.progressTarget() + "</white>.</gray>");
            }
            if (quest.requiresPet()) lore.add("<dark_gray>Tiller must be active.</dark_gray>");
            if (done) lore.add("<green>Complete.</green>");
            else if (current) lore.add(canComplete(player, quest) ? "<green>Click to complete.</green>" : "<yellow>Still in progress.</yellow>");
            else lore.add("<dark_gray>Complete the earlier commission first.</dark_gray>");
            menu.setItem(QUEST_SLOTS[i], item(
                done ? Material.LIME_STAINED_GLASS_PANE : current ? quest.icon() : Material.GRAY_STAINED_GLASS_PANE,
                done ? "<green><bold>" + quest.title() + "</bold></green>" : current ? "<yellow><bold>" + quest.title() + "</bold></yellow>" : "<dark_gray><bold>Locked</bold></dark_gray>",
                lore,
                current ? "turn_in:" + i : null
            ));
        }
        menu.setItem(40, applyTillerHeadTexture(item(Material.PLAYER_HEAD,
            hasTiller(player) ? "<green><bold>Tiller, the Sprout Mole</bold></green>" : "<dark_gray><bold>Tiller, the Sprout Mole</bold></dark_gray>",
            hasTiller(player) ? familiarLore(player) : List.of("<gray>Complete the Harvest Banquet to unlock.</gray>"),
            hasTiller(player) ? "pet" : null)));
        menu.setItem(49, item(Material.BARRIER, "<red><bold>Close</bold></red>", List.of(), "close"));
        player.openInventory(menu);
    }

    public void openPetMenu(Player player) {
        Inventory menu = Bukkit.createInventory(new FarmerPetMenuHolder(player.getUniqueId()), 27,
            BedrockCompat.menuTitle(player, MM.deserialize("<green><bold>Tiller, the Sprout Mole</bold></green>"), "Tiller"));
        fill(menu);
        ItemStack head = tillerHead();
        ItemMeta meta = head.getItemMeta();
        meta.displayName(MM.deserialize("<green><bold>Tiller, the Sprout Mole</bold></green>"));
        meta.lore(CustomLoreUtil.wrapMiniMessageLines(familiarLore(player)).stream().map(MM::deserialize).toList());
        head.setItemMeta(meta);
        menu.setItem(4, head);
        if (hasTiller(player)) {
            FamiliarMenuState state = FamiliarMenuState.from(hasActiveTiller(player));
            menu.setItem(11, item(state.summonIcon(),
                state.canSummon() ? "<green><bold>Summon</bold></green>" : "<green><bold>Currently Summoned</bold></green>",
                state.canSummon()
                    ? List.of("<gray>Bring Tiller out and enable its buffs.</gray>")
                    : List.of("<gray>Tiller is following you.</gray>", "<dark_gray>Right-click to interact. Sneak-right-click to manage.</dark_gray>"),
                state.summonAction("summon")));
            menu.setItem(15, item(state.dismissIcon(),
                state.canDismiss() ? "<red><bold>Dismiss</bold></red>" : "<dark_gray><bold>Already Dismissed</bold></dark_gray>",
                state.canDismiss()
                    ? List.of("<gray>Hide Tiller and pause its buffs.</gray>")
                    : List.of("<gray>Tiller is already resting.</gray>"),
                state.dismissAction("dismiss")));
        }
        menu.setItem(22, item(Material.ARROW, "<yellow><bold>Back</bold></yellow>", List.of("<gray>Return to your familiar stable.</gray>"), "familiars"));
        player.openInventory(menu);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof FarmerMenuHolder) && !(holder instanceof FarmerPetMenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        String action = action(event.getCurrentItem());
        if (action != null) Bukkit.getScheduler().runTask(plugin, () -> handleAction(player, action));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof FarmerMenuHolder || holder instanceof FarmerPetMenuHolder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        Material crop = cropDrop(event.getBlock());
        if (crop == null) return;
        recordProgress(event.getPlayer(), ProgressType.HARVEST, 1);
        if (hasActiveTiller(event.getPlayer()) && ThreadLocalRandom.current().nextDouble() < coreChance(event.getPlayer(), CROP_BONUS_CHANCE)) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5D, 0.4D, 0.5D), new ItemStack(crop));
            event.getPlayer().playSound(event.getBlock().getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.25F, 1.55F);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropHarvest(PlayerHarvestBlockEvent event) {
        Material crop = cropDrop(event.getHarvestedBlock());
        if (crop == null) return;
        recordProgress(event.getPlayer(), ProgressType.HARVEST, 1);
        if (hasActiveTiller(event.getPlayer()) && ThreadLocalRandom.current().nextDouble() < coreChance(event.getPlayer(), CROP_BONUS_CHANCE)) {
            event.getItemsHarvested().add(new ItemStack(crop));
            event.getPlayer().playSound(event.getHarvestedBlock().getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.25F, 1.55F);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack current = event.getCurrentItem();
        if (!isFood(current)) return;
        recordProgress(player, ProgressType.FOOD_CRAFT, estimateCraftedAmount(event));
        if (!hasActiveTiller(player)) return;

        ItemStack result = current.clone();
        boolean extraServing = result.getAmount() < result.getMaxStackSize()
            && ThreadLocalRandom.current().nextDouble() < coreChance(player, EXTRA_SERVING_CHANCE);
        boolean hearty = hasHeartyFoodMastery(player)
            && ThreadLocalRandom.current().nextDouble() < coreChance(player, HEARTY_FOOD_CHANCE);
        if (extraServing) result.setAmount(result.getAmount() + 1);
        if (hearty) result = makeHearty(result);
        if (!extraServing && !hearty) return;
        event.setCurrentItem(result);
        if (hearty) {
            player.sendActionBar(MM.deserialize("<gold>Tiller prepared a <bold>Hearty</bold> batch.</gold>"));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45F, 1.65F);
        } else {
            player.sendActionBar(MM.deserialize("<green>Tiller made one extra serving.</green>"));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (hasActiveTiller(event.getPlayer())) follower.spawn(event.getPlayer());
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
        player.sendMessage(MessageUtil.info("<green>Tiller</green> sniffs your hand, then taps it with a muddy paw."));
        player.playSound(event.getRightClicked().getLocation(), Sound.ENTITY_RABBIT_AMBIENT, 0.6F, 1.25F);
        player.spawnParticle(Particle.HAPPY_VILLAGER, event.getRightClicked().getLocation().add(0.0D, 0.4D, 0.0D), 5, 0.2D, 0.16D, 0.2D, 0.01D);
    }

    public boolean hasTiller(Player player) {
        return pdcByte(player, petKey);
    }

    public boolean hasHeartyFoodMastery(Player player) {
        return pdcByte(player, masteryKey);
    }

    public boolean hasActiveTiller(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) return false;
        ensureActiveFamiliar(player);
        return hasTiller(player) && !isPetHidden(player) && FARMER_FAMILIAR_ID.equals(activeFamiliar(player));
    }

    public double familiarCoreMultiplier(Player player) {
        return plugin.getBeastwardenManager() == null
            ? 1.0D : plugin.getBeastwardenManager().familiarCoreMultiplier(player, FARMER_FAMILIAR_ID);
    }

    private double coreChance(Player player, double baseChance) {
        return Math.min(1.0D, Math.max(0.0D, baseChance) * familiarCoreMultiplier(player));
    }

    public void grantTiller(Player player, CommandSender actor) {
        if (player == null) return;
        boolean already = hasTiller(player);
        player.getPersistentDataContainer().set(petKey, PersistentDataType.BYTE, (byte) 1);
        selectTiller(player);
        follower.spawn(player);
        player.sendMessage(already ? MessageUtil.info("Your <white>Tiller</white> familiar was restored.") : MessageUtil.success("You unlocked <white>Tiller, the Sprout Mole</white>."));
        if (actor != null && !actor.equals(player)) actor.sendMessage(MessageUtil.success("Gave <white>" + player.getName() + "</white> the Tiller familiar."));
    }

    public void revokeTiller(Player player, CommandSender actor) {
        if (player == null) return;
        player.getPersistentDataContainer().remove(petKey);
        player.getPersistentDataContainer().remove(petHiddenKey);
        player.getPersistentDataContainer().remove(masteryKey);
        if (FARMER_FAMILIAR_ID.equals(activeFamiliar(player))) player.getPersistentDataContainer().remove(activeFamiliarKey);
        follower.remove(player);
        player.sendMessage(MessageUtil.warn("Your <white>Tiller</white> familiar was removed."));
        if (actor != null && !actor.equals(player)) actor.sendMessage(MessageUtil.success("Removed <white>" + player.getName() + "</white>'s Tiller familiar."));
    }

    public void deactivateForFamiliarSwitch(Player player) {
        if (player == null) return;
        player.getPersistentDataContainer().set(petHiddenKey, PersistentDataType.BYTE, (byte) 1);
        follower.remove(player);
    }

    static FoodStats doubledFoodStats(int nutrition, float saturation) {
        return new FoodStats(Math.max(0, nutrition * 2), Math.max(0.0F, saturation * 2.0F));
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
                if (!hasActiveTiller(player)) {
                    selectTiller(player);
                    follower.spawn(player);
                }
                openPetMenu(player);
            }
            case "dismiss" -> {
                if (hasActiveTiller(player)) {
                    player.getPersistentDataContainer().set(petHiddenKey, PersistentDataType.BYTE, (byte) 1);
                    if (FARMER_FAMILIAR_ID.equals(activeFamiliar(player))) player.getPersistentDataContainer().remove(activeFamiliarKey);
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
        if (quest.requiresPet() && !hasActiveTiller(player)) {
            player.sendMessage(MessageUtil.warn("Tiller must be summoned while you finish this commission."));
            return;
        }
        if (quest.progressType() != ProgressType.NONE && questProgress(player) < quest.progressTarget()) {
            player.sendMessage(MessageUtil.warn("Progress is <white>" + questProgress(player) + "/" + quest.progressTarget() + "</white>."));
            return;
        }
        if (!quest.cost().isEmpty() && !InventoryRecipeUtil.hasPlainMaterials(plugin, player, quest.cost())) {
            player.sendMessage(MessageUtil.warn("You do not have every item in this commission yet."));
            return;
        }
        if (!quest.cost().isEmpty() && !InventoryRecipeUtil.removePlainMaterials(plugin, player, quest.cost())) {
            player.sendMessage(MessageUtil.error("Your inventory changed before Rowan could accept the delivery."));
            return;
        }
        int next = stage + 1;
        player.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, next);
        player.getPersistentDataContainer().remove(progressKey);
        if (next == FIELD_ARC_END && !pdcByte(player, hoeRewardKey)) rewardHoe(player);
        if (next == KITCHEN_ARC_END && !hasTiller(player)) unlockTiller(player);
        if (next == STAGES.size() && !hasHeartyFoodMastery(player)) unlockMastery(player);
        if (plugin.getStoryService() != null) {
            plugin.getStoryService().onQuestStage(player, "farmer", quest.title(), next);
        }
        player.sendMessage(MessageUtil.success("Commission complete: <white>" + quest.title() + "</white>."));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.75F, 1.25F);
        openQuestMenu(player);
    }

    private void rewardHoe(Player player) {
        player.getPersistentDataContainer().set(hoeRewardKey, PersistentDataType.BYTE, (byte) 1);
        InventoryRecipeUtil.giveOrDrop(player, createFieldHoe());
        player.showTitle(Title.title(
            MM.deserialize("<green><bold>FURROWKEEPER</bold></green>"),
            MM.deserialize("<yellow>Rowan gave you his masterwork hoe.</yellow>"),
            Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3000), Duration.ofMillis(800))
        ));
    }

    private void unlockTiller(Player player) {
        player.getPersistentDataContainer().set(petKey, PersistentDataType.BYTE, (byte) 1);
        selectTiller(player);
        follower.spawn(player);
        if (plugin.getStoryService() != null) plugin.getStoryService().onFamiliarUnlocked(player, "tiller");
        player.showTitle(Title.title(
            MM.deserialize("<green><bold>TILLER</bold></green>"),
            MM.deserialize("<yellow>Tiller is now available in your Familiar Stable.</yellow>"),
            Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3200), Duration.ofMillis(900))
        ));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.15F);
    }

    private void unlockMastery(Player player) {
        player.getPersistentDataContainer().set(masteryKey, PersistentDataType.BYTE, (byte) 1);
        player.showTitle(Title.title(
            MM.deserialize("<gold><bold>HEARTY HARVEST</bold></gold>"),
            MM.deserialize("<yellow>Tiller can now create doubled-stat food.</yellow>"),
            Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3400), Duration.ofMillis(900))
        ));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.9F);
    }

    private ItemStack createFieldHoe() {
        ItemStack hoe = new ItemStack(Material.NETHERITE_HOE);
        ItemMeta meta = hoe.getItemMeta();
        meta.displayName(MM.deserialize("<gradient:#84cc16:#facc15><bold>Furrowkeeper</bold></gradient>").decoration(TextDecoration.ITALIC, false));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.NETHERITE_HOE,
            "EPIC",
            "HOE",
            List.of("<gray>Rowan's masterwork field hoe.</gray>"),
            List.of(CustomLoreUtil.section("Source", "Fieldwork",
                "<dark_gray>Earned by finishing Rowan's Fieldwork chapter.</dark_gray>"))
        ));
        meta.addEnchant(requireEnchantment("efficiency"), 5, true);
        meta.addEnchant(requireEnchantment("unbreaking"), 3, true);
        meta.addEnchant(requireEnchantment("fortune"), 3, true);
        meta.addEnchant(requireEnchantment("mending"), 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(specialHoeKey, PersistentDataType.BYTE, (byte) 1);
        hoe.setItemMeta(meta);
        if (plugin.getReplenishListener() != null) hoe = plugin.getReplenishListener().applyReplenish(hoe);
        if (plugin.getCustomEnchantListener() != null) {
            hoe = plugin.getCustomEnchantListener().applyManagedEnchant(hoe, "harvesting", 3);
            hoe = plugin.getCustomEnchantListener().applyManagedEnchant(hoe, "wise", 2);
        }
        return hoe;
    }

    private ItemStack makeHearty(ItemStack item) {
        FoodProperties food = item.getData(DataComponentTypes.FOOD);
        if (food == null || food.nutrition() <= 0) return item;
        FoodStats doubled = doubledFoodStats(food.nutrition(), food.saturation());
        item.setData(DataComponentTypes.FOOD, FoodProperties.food()
            .nutrition(doubled.nutrition())
            .saturation(doubled.saturation())
            .canAlwaysEat(food.canAlwaysEat()));
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.removeIf(line -> PLAIN.serialize(line).startsWith("Hearty: ") || PLAIN.serialize(line).startsWith("Doubled nourishment:"));
        if (!lore.isEmpty()) lore.add(Component.empty());
        lore.add(MM.deserialize("<gold><bold>Hearty:</bold></gold> <white>" + doubled.nutrition() + " hunger</white> <gray>/</gray> <white>" + formatSaturation(doubled.saturation()) + " saturation</white>"));
        lore.add(MM.deserialize("<dark_gray>Doubled nourishment from Tiller.</dark_gray>"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(heartyFoodKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean canComplete(Player player, QuestStage quest) {
        if (quest.requiresPet() && !hasActiveTiller(player)) return false;
        if (quest.progressType() != ProgressType.NONE && questProgress(player) < quest.progressTarget()) return false;
        return quest.cost().isEmpty() || InventoryRecipeUtil.hasPlainMaterials(plugin, player, quest.cost());
    }

    private void recordProgress(Player player, ProgressType type, int amount) {
        int stage = questStage(player);
        if (stage >= STAGES.size() || amount <= 0) return;
        QuestStage quest = STAGES.get(stage);
        if (quest.progressType() != type || quest.requiresPet() && !hasActiveTiller(player)) return;
        int current = questProgress(player);
        int next = Math.min(quest.progressTarget(), current + amount);
        if (next == current) return;
        player.getPersistentDataContainer().set(progressKey, PersistentDataType.INTEGER, next);
        if (next == quest.progressTarget()) {
            player.sendMessage(MessageUtil.success("Commission ready: <white>" + quest.title() + "</white>. Return to Rowan."));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65F, 1.45F);
        }
    }

    private int estimateCraftedAmount(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        int resultAmount = result == null || result.getType().isAir() ? 1 : Math.max(1, result.getAmount());
        if (!event.isShiftClick()) return resultAmount;
        int crafts = Integer.MAX_VALUE;
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient == null || ingredient.getType().isAir()) continue;
            crafts = Math.min(crafts, ingredient.getAmount());
        }
        return crafts == Integer.MAX_VALUE ? resultAmount : Math.max(1, Math.min(4096, resultAmount * crafts));
    }

    private boolean isFood(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getData(DataComponentTypes.FOOD) != null;
    }

    private Material cropDrop(Block block) {
        if (block == null || !(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) return null;
        return switch (block.getType()) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            default -> null;
        };
    }

    private void selectTiller(Player player) {
        if (plugin.getMayorQuestManager() != null) plugin.getMayorQuestManager().deactivateForFamiliarSwitch(player);
        if (plugin.getMinerManager() != null) plugin.getMinerManager().deactivateForFamiliarSwitch(player);
        if (plugin.getWitchManager() != null) plugin.getWitchManager().deactivateForFamiliarSwitch(player);
        player.getPersistentDataContainer().set(activeFamiliarKey, PersistentDataType.STRING, FARMER_FAMILIAR_ID);
        player.getPersistentDataContainer().remove(petHiddenKey);
    }

    private void ensureActiveFamiliar(Player player) {
        if (activeFamiliar(player) != null) return;
        if (hasTiller(player) && !pdcByte(player, petHiddenKey)) {
            player.getPersistentDataContainer().set(activeFamiliarKey, PersistentDataType.STRING, FARMER_FAMILIAR_ID);
        }
    }

    private boolean isPetHidden(Player player) {
        return pdcByte(player, petHiddenKey) || !FARMER_FAMILIAR_ID.equals(activeFamiliar(player));
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

    private List<String> familiarLore(Player player) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Field Hand: <white>10%</white> chance for +1 mature crop.</gray>");
        lore.add("<gray>Kitchen Hand: <white>10%</white> chance for +1 crafted serving.</gray>");
        lore.add(hasHeartyFoodMastery(player)
            ? "<gold>Hearty Harvest: <white>5%</white> chance for doubled food stats.</gold>"
            : "<dark_gray>Hearty Harvest is locked behind Chapter III.</dark_gray>");
        lore.add("<yellow>Click to manage.</yellow>");
        return lore;
    }

    private ItemStack tillerHead() {
        return applyTillerHeadTexture(new ItemStack(Material.PLAYER_HEAD));
    }

    public ItemStack applyTillerHeadTexture(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return item;
        ItemMeta itemMeta = item.getItemMeta();
        if (!(itemMeta instanceof SkullMeta meta)) return item;
        PlayerProfile profile = Bukkit.createProfile(TILLER_PROFILE_ID, "TillerSproutMole");
        profile.setProperty(new ProfileProperty("textures", TILLER_TEXTURE));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    private Enchantment requireEnchantment(String key) {
        Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(NamespacedKey.minecraft(key));
        if (enchantment == null) throw new IllegalStateException("Missing enchantment: " + key);
        return enchantment;
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

    private String chapterName(int stage) {
        if (stage < FIELD_ARC_END) return "Chapter I - Fieldwork";
        if (stage < KITCHEN_ARC_END) return "Chapter II - The Kitchen";
        return "Chapter III - Harvest Mastery";
    }

    private String pretty(Material material) {
        String value = material.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String formatSaturation(float value) {
        return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    public record FoodStats(int nutrition, float saturation) {
    }

    private enum ProgressType {
        NONE,
        HARVEST,
        FOOD_CRAFT
    }

    private record QuestStage(
        String title,
        String description,
        Material icon,
        Map<Material, Integer> cost,
        ProgressType progressType,
        int progressTarget,
        boolean requiresPet
    ) {
    }

    private record FarmerMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record FarmerPetMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
