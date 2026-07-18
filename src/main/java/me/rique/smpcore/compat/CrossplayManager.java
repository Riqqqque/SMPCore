package me.rique.smpcore.compat;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("UnstableApiUsage")
public final class CrossplayManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int CONTROLS_SIZE = 27;
    private static final int ANVIL_SIZE = 36;
    private static final int LEFT_INPUT_SLOT = 11;
    private static final int RESULT_PREVIEW_SLOT = 13;
    private static final int RIGHT_INPUT_SLOT = 15;
    private static final int CONFIRM_SLOT = 22;
    private static final int BACK_SLOT = 27;
    private static final int CLOSE_SLOT = 31;
    private static final long BEDROCK_GESTURE_DEBOUNCE_MS = 350L;

    private static final String ACTION_PRIMARY = "primary";
    private static final String ACTION_ALTERNATE = "alternate";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_WIKI = "wiki";

    private final SMPCore plugin;
    private final NamespacedKey actionKey;
    private final Map<UUID, Long> nextBedrockGestureAt = new ConcurrentHashMap<>();

    public CrossplayManager(SMPCore plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "crossplay_action");
    }

    public static void registerCommands(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("bedrock")
                .requires(source -> source.getSender() instanceof Player player
                    && player.hasPermission("smpcore.crossplay"))
                .executes(ctx -> {
                    plugin.getCrossplayManager().openControls((Player) ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Open Bedrock-friendly controls",
            List.of("crossplay", "bedrocktools")
        );

        commands.register(
            Commands.literal("ability")
                .requires(source -> source.getSender() instanceof Player player
                    && player.hasPermission("smpcore.crossplay"))
                .executes(ctx -> {
                    plugin.getCrossplayManager().activateHeldAbility((Player) ctx.getSource().getSender(), false);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("alternate")
                    .executes(ctx -> {
                        plugin.getCrossplayManager().activateHeldAbility((Player) ctx.getSource().getSender(), true);
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("alt")
                    .executes(ctx -> {
                        plugin.getCrossplayManager().activateHeldAbility((Player) ctx.getSource().getSender(), true);
                        return Command.SINGLE_SUCCESS;
                    }))
                .build(),
            "Use the held custom item's ability",
            List.of("useability")
        );

        commands.register(
            Commands.literal("customanvil")
                .requires(source -> source.getSender() instanceof Player player
                    && player.hasPermission("smpcore.customanvil"))
                .executes(ctx -> {
                    plugin.getCrossplayManager().sendAnvilAccessHint((Player) ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
            "Explain how to open the crossplay-safe custom anvil",
            List.of("bedrockanvil", "sanvil")
        );
    }

    public void openControls(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new ControlsHolder(),
            CONTROLS_SIZE,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize("<gradient:#22d3ee:#a78bfa><bold>Crossplay Controls</bold></gradient>"),
                "Crossplay Controls"
            )
        );
        decorateFrame(inventory);
        inventory.setItem(4, menuItem(
            Material.RECOVERY_COMPASS,
            "<aqua><bold>Bedrock-Friendly Controls</bold></aqua>",
            List.of(
                "<gray>Reliable buttons for actions that Java</gray>",
                "<gray>and Bedrock clients send differently.</gray>"
            )
        ));
        inventory.setItem(11, actionItem(
            Material.LIME_CONCRETE,
            "<green><bold>Use Held Ability</bold></green>",
            List.of("<gray>Runs the primary ability of your held custom item.</gray>"),
            ACTION_PRIMARY
        ));
        inventory.setItem(13, actionItem(
            Material.YELLOW_CONCRETE,
            "<yellow><bold>Alternate Ability</bold></yellow>",
            List.of("<gray>Runs its alternate, sneak, or secondary action.</gray>"),
            ACTION_ALTERNATE
        ));
        inventory.setItem(15, menuItem(
            Material.FEATHER,
            "<light_purple><bold>Combat Shortcuts</bold></light_purple>",
            List.of(
                "<gray>Crouch + Drop: primary held ability.</gray>",
                "<gray>Crouch + Punch: alternate held ability.</gray>",
                "<dark_gray>Your held item stays in your inventory.</dark_gray>"
            )
        ));
        inventory.setItem(18, actionItem(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to /menu.</gray>"), ACTION_BACK));
        inventory.setItem(22, actionItem(Material.BARRIER, "<red>Close</red>", List.of("<gray>Close this menu.</gray>"), ACTION_CLOSE));
        inventory.setItem(26, actionItem(Material.WRITABLE_BOOK, "<aqua>Wiki Link</aqua>", List.of("<gray>Print the full URL in chat.</gray>"), ACTION_WIKI));
        player.openInventory(inventory);
    }

    private void openCustomAnvil(Player player) {
        if (!player.hasPermission("smpcore.customanvil")) {
            player.sendMessage(MessageUtil.error("You do not have permission to use the custom anvil."));
            return;
        }
        Inventory inventory = Bukkit.createInventory(
            new CrossplayAnvilHolder(player.getUniqueId()),
            ANVIL_SIZE,
            BedrockCompat.menuTitle(
                player,
                MM.deserialize("<gradient:#a78bfa:#22d3ee><bold>Custom Anvil</bold></gradient>"),
                "Custom Anvil"
            )
        );
        refreshAnvil(inventory);
        player.openInventory(inventory);
    }

    public void sendAnvilAccessHint(Player player) {
        player.sendMessage(MessageUtil.info(
            "Use a placed <white>anvil</white> to open SMPCore crafting. Crouch-use it for the normal vanilla anvil."
        ));
    }

    public boolean activateHeldAbility(Player player, boolean alternate) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        boolean handled = plugin.getSeasonRelicManager() != null
            && plugin.getSeasonRelicManager().activateHeldCrossplayAbility(player, alternate);
        if (!handled && plugin.getLegendaryListener() != null) {
            handled = plugin.getLegendaryListener().activateHeldCrossplayAbility(player, alternate);
        }
        if (!handled && plugin.getSuperpowerManager() != null) {
            handled = plugin.getSuperpowerManager().activateHeldCrossplayAbility(player, alternate);
        }
        if (!handled && plugin.getCustomToolListener() != null) {
            handled = plugin.getCustomToolListener().activateHeldCrossplayAbility(player);
        }
        if (!handled && plugin.getRewardLanternListener() != null) {
            handled = plugin.getRewardLanternListener().activateHeldCrossplayAbility(player);
        }
        if (!handled && plugin.getBackpackListener() != null) {
            handled = plugin.getBackpackListener().activateHeldCrossplayAbility(player);
        }
        if (!handled && plugin.getCustomEnchantListener() != null) {
            handled = plugin.getCustomEnchantListener().activateHeldCrossplayAbility(player);
        }
        if (!handled) {
            player.sendMessage(MessageUtil.warn("Your held item does not have a supported active ability."));
        }
        return handled;
    }

    private boolean supportsHeldAbility(Player player, ItemStack item) {
        if (player == null || item == null || item.getType().isAir()) {
            return false;
        }
        if (plugin.getSeasonRelicManager() != null && plugin.getSeasonRelicManager().supportsCrossplayAbility(item)) {
            return true;
        }
        if (plugin.getLegendaryListener() != null && plugin.getLegendaryListener().supportsCrossplayAbility(item)) {
            return true;
        }
        if (plugin.getSuperpowerManager() != null) {
            if (plugin.getSuperpowerManager().isAncientScroll(item)
                || plugin.getSuperpowerManager().isTheWorldClock(item)
                || plugin.getSuperpowerManager().isDruidGrimoire(item)
                || (plugin.getSuperpowerManager().isMotherNatureStick(item)
                    && plugin.getSuperpowerManager().hasPower(player, me.rique.smpcore.power.SuperpowerType.VERDANT))) {
                return true;
            }
        }
        if (plugin.getCustomToolListener() != null) {
            String toolId = plugin.getCustomToolListener().customToolId(item);
            if (me.rique.smpcore.item.CustomToolListener.SURVEYORS_LENS_ID.equals(toolId)
                || me.rique.smpcore.item.CustomToolListener.MENDERS_KIT_ID.equals(toolId)) {
                return true;
            }
        }
        if (plugin.getRewardLanternListener() != null && plugin.getRewardLanternListener().isRewardLantern(item)) {
            return true;
        }
        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) {
            return true;
        }
        return plugin.getCustomEnchantListener() != null && plugin.getCustomEnchantListener().hasDashEnchant(item);
    }

    private boolean markBedrockGesture(Player player) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        if (nextBedrockGestureAt.getOrDefault(playerId, 0L) > now) {
            return false;
        }
        nextBedrockGestureAt.put(playerId, now + BEDROCK_GESTURE_DEBOUNCE_MS);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBedrockPrimaryGesture(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack dropped = event.getItemDrop().getItemStack().clone();
        if (!BedrockCompat.isBedrockPlayer(player)
            || !player.hasPermission("smpcore.crossplay")
            || !player.isSneaking()
            || !supportsHeldAbility(player, dropped)
            || !markBedrockGesture(player)) {
            return;
        }

        int heldSlot = player.getInventory().getHeldItemSlot();
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getInventory().getHeldItemSlot() != heldSlot) {
                return;
            }
            ItemStack restored = player.getInventory().getItem(heldSlot);
            if (restored == null || !restored.isSimilar(dropped)) {
                return;
            }
            activateHeldAbility(player, false);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBedrockAlternateGesture(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!BedrockCompat.isBedrockPlayer(player)
            || !player.hasPermission("smpcore.crossplay")
            || !player.isSneaking()
            || !supportsHeldAbility(player, held)
            || !markBedrockGesture(player)) {
            return;
        }
        event.setCancelled(true);
        activateHeldAbility(player, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBedrockAnvilUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
            || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || event.getClickedBlock() == null
            || !isAnvilBlock(event.getClickedBlock().getType())) {
            return;
        }
        Player player = event.getPlayer();
        if (!BedrockCompat.isBedrockPlayer(player) || !player.hasPermission("smpcore.customanvil")) {
            return;
        }
        if (player.isSneaking()) {
            player.sendActionBar(MM.deserialize("<gray>Opening the normal anvil.</gray>"));
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        Bukkit.getScheduler().runTask(plugin, () -> openCustomAnvil(player));
    }

    static boolean isAnvilBlock(Material material) {
        return material == Material.ANVIL || material == Material.CHIPPED_ANVIL || material == Material.DAMAGED_ANVIL;
    }

    @EventHandler
    public void onCrossplayKick(PlayerKickEvent event) {
        returnOpenCrossplayAnvilInputs(event.getPlayer());
    }

    @EventHandler
    public void onCrossplayQuit(PlayerQuitEvent event) {
        returnOpenCrossplayAnvilInputs(event.getPlayer());
        nextBedrockGestureAt.remove(event.getPlayer().getUniqueId());
    }

    private void returnOpenCrossplayAnvilInputs(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder(false) instanceof CrossplayAnvilHolder holder
            && holder.playerId().equals(player.getUniqueId())) {
            returnInputSlots(player, top);
        }
    }

    public void sendStatus(CommandSender sender) {
        Plugin geyser = findEnabledPlugin("geyser");
        Plugin floodgate = findEnabledPlugin("floodgate");
        Plugin viaVersion = findEnabledPlugin("viaversion");
        long bedrockPlayers = Bukkit.getOnlinePlayers().stream().filter(BedrockCompat::isBedrockPlayer).count();

        sender.sendMessage(MessageUtil.info("Crossplay status for Minecraft <white>" + Bukkit.getMinecraftVersion() + "</white>:"));
        sender.sendMessage(MessageUtil.info("Floodgate: <white>" + pluginStatus(floodgate) + "</white>"));
        sender.sendMessage(MessageUtil.info(
            "Geyser backend plugin: <white>" + pluginStatus(geyser) + "</white> <dark_gray>(may run on a proxy instead)</dark_gray>"
        ));
        sender.sendMessage(MessageUtil.info("ViaVersion: <white>" + pluginStatus(viaVersion) + "</white>"));
        sender.sendMessage(MessageUtil.info("Detected Bedrock players online: <white>" + bedrockPlayers + "</white>"));
        if (floodgate == null) {
            sender.sendMessage(MessageUtil.warn("Floodgate is optional here, but Bedrock detection and tailored controls need it on the backend."));
        }
        if (Bukkit.getMinecraftVersion().startsWith("26.2")
            && viaVersion == null
            && geyser != null
            && geyser.getPluginMeta().getVersion().startsWith("2.10.")) {
            sender.sendMessage(MessageUtil.warn("This Geyser build targets an older Java protocol than Paper 26.2. Install ViaVersion or a 26.2-compatible Geyser build."));
        }
    }

    public void shutdown() {
        nextBedrockGestureAt.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder(false) instanceof CrossplayAnvilHolder) {
                returnInputSlots(player, top);
                player.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onControlsClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ControlsHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !isNormalClick(event.getClick())) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= CONTROLS_SIZE) {
            return;
        }
        String action = action(event.getCurrentItem());
        if (action == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> handleControlAction(player, action));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onControlsDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ControlsHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof CrossplayAnvilHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
            }
            return;
        }

        int rawSlot = event.getRawSlot();
        boolean clickedTop = rawSlot >= 0 && rawSlot < top.getSize();
        if (clickedTop) {
            handleAnvilTopClick(event, player, top, rawSlot);
            return;
        }
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            shiftMoveIntoAnvil(player, top, event);
            return;
        }
        if (isBlockedClick(event.getClick())) {
            event.setCancelled(true);
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshAnvil(top));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof CrossplayAnvilHolder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    Bukkit.getScheduler().runTask(plugin, () -> refreshAnvil(top));
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof CrossplayAnvilHolder holder)
            || !(event.getPlayer() instanceof Player player)
            || !holder.playerId().equals(player.getUniqueId())) {
            return;
        }
        returnInputSlots(player, event.getInventory());
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Inventory top = event.getEntity().getOpenInventory().getTopInventory();
        if (!(top.getHolder(false) instanceof CrossplayAnvilHolder)) {
            return;
        }
        if (event.getKeepInventory()) {
            returnInputSlots(event.getEntity(), top);
            return;
        }
        evacuateToDrops(top, event.getDrops(), LEFT_INPUT_SLOT);
        evacuateToDrops(top, event.getDrops(), RIGHT_INPUT_SLOT);
    }

    private void handleControlAction(Player player, String action) {
        if (!player.isOnline()) {
            return;
        }
        switch (action) {
            case ACTION_PRIMARY -> {
                player.closeInventory();
                activateHeldAbility(player, false);
            }
            case ACTION_ALTERNATE -> {
                player.closeInventory();
                activateHeldAbility(player, true);
            }
            case ACTION_BACK -> player.performCommand("menu");
            case ACTION_WIKI -> {
                player.closeInventory();
                player.performCommand("wiki");
            }
            case ACTION_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleAnvilTopClick(InventoryClickEvent event, Player player, Inventory top, int rawSlot) {
        if (rawSlot == CONFIRM_SLOT) {
            event.setCancelled(true);
            if (isNormalClick(event.getClick()) && MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
                executeAnvil(player, top);
            }
            return;
        }
        if (rawSlot == BACK_SLOT) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        if (rawSlot != LEFT_INPUT_SLOT && rawSlot != RIGHT_INPUT_SLOT) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick() || isBlockedClick(event.getClick())) {
            event.setCancelled(true);
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshAnvil(top));
    }

    private void executeAnvil(Player player, Inventory inventory) {
        AnvilRecipe recipe = previewRecipe(inventory.getItem(LEFT_INPUT_SLOT), inventory.getItem(RIGHT_INPUT_SLOT));
        if (recipe == null) {
            player.sendMessage(MessageUtil.warn("Those items do not make a supported custom-anvil result."));
            refreshAnvil(inventory);
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE && player.getLevel() < recipe.levelCost()) {
            player.sendMessage(MessageUtil.warn("You need <white>" + recipe.levelCost() + "</white> XP levels for this."));
            return;
        }

        ItemStack result = recipe.result().clone();
        consumeOne(inventory, LEFT_INPUT_SLOT);
        consumeOne(inventory, RIGHT_INPUT_SLOT);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setLevel(Math.max(0, player.getLevel() - recipe.levelCost()));
        }
        InventoryRecipeUtil.giveOrDrop(player, result);
        if (recipe.recordAcquisition() && plugin.getItemAuditManager() != null) {
            plugin.getItemAuditManager().recordKnownAcquisition(
                player,
                result,
                "crossplay_anvil",
                recipe.auditDescription()
            );
        }
        player.sendMessage(MessageUtil.success(recipe.successMessage()));
        refreshAnvil(inventory);
        player.updateInventory();
    }

    private AnvilRecipe previewRecipe(ItemStack left, ItemStack right) {
        if (isEmpty(left) || isEmpty(right)) {
            return null;
        }
        AnvilRecipe recipe = plugin.getCustomEnchantListener() == null
            ? null
            : plugin.getCustomEnchantListener().crossplayAnvilRecipe(left, right);
        if (recipe == null && plugin.getReplenishListener() != null) {
            recipe = plugin.getReplenishListener().crossplayAnvilRecipe(left, right);
        }
        if (recipe == null && plugin.getLegendaryListener() != null) {
            recipe = plugin.getLegendaryListener().crossplayAnvilRecipe(left, right);
        }
        return recipe;
    }

    private void refreshAnvil(Inventory inventory) {
        if (inventory == null || !(inventory.getHolder(false) instanceof CrossplayAnvilHolder)) {
            return;
        }
        ItemStack left = cloneOrNull(inventory.getItem(LEFT_INPUT_SLOT));
        ItemStack right = cloneOrNull(inventory.getItem(RIGHT_INPUT_SLOT));
        decorateAnvil(inventory);
        inventory.setItem(LEFT_INPUT_SLOT, left);
        inventory.setItem(RIGHT_INPUT_SLOT, right);

        AnvilRecipe recipe = previewRecipe(left, right);
        inventory.setItem(RESULT_PREVIEW_SLOT, recipe == null ? waitingResultItem() : resultPreviewItem(recipe));
        inventory.setItem(CONFIRM_SLOT, confirmItem(recipe));
    }

    private void decorateAnvil(Inventory inventory) {
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot != LEFT_INPUT_SLOT && slot != RIGHT_INPUT_SLOT) {
                inventory.setItem(slot, filler);
            }
        }
        inventory.setItem(4, menuItem(
            Material.ANVIL,
            "<light_purple><bold>Custom Anvil</bold></light_purple>",
            List.of(
                "<gray>Put the base item on the left and ingredient on the right.</gray>",
                "<dark_gray>Built for custom recipes Geyser cannot show in a normal anvil.</dark_gray>"
            )
        ));
        inventory.setItem(10, menuItem(Material.ITEM_FRAME, "<aqua>Base Item</aqua>", List.of("<gray>Place one item in the slot to the right.</gray>")));
        inventory.setItem(16, menuItem(Material.GLOW_ITEM_FRAME, "<yellow>Ingredient</yellow>", List.of("<gray>Place the book, item, or repair core to the left.</gray>")));
        inventory.setItem(BACK_SLOT, menuItem(Material.ARROW, "<yellow>Exit</yellow>", List.of("<gray>Close the anvil and return your inputs.</gray>")));
        inventory.setItem(CLOSE_SLOT, menuItem(Material.BARRIER, "<red>Close</red>", List.of("<gray>Your input items will be returned.</gray>")));
        inventory.setItem(35, menuItem(
            Material.BOOK,
            "<aqua>Supported Results</aqua>",
            List.of(
                "<gray>Custom enchant books and item merges</gray>",
                "<gray>Replenish books</gray>",
                "<gray>Veil Dominion repairs</gray>",
                "<dark_gray>Crouch-use the block for a vanilla anvil.</dark_gray>"
            )
        ));
    }

    private ItemStack waitingResultItem() {
        return menuItem(
            Material.GRAY_STAINED_GLASS_PANE,
            "<gray>Result Preview</gray>",
            List.of("<dark_gray>Add a supported base item and ingredient.</dark_gray>")
        );
    }

    private ItemStack resultPreviewItem(AnvilRecipe recipe) {
        return menuItem(
            Material.GLOW_ITEM_FRAME,
            "<green><bold>Result: " + miniEscape(itemName(recipe.result())) + "</bold></green>",
            List.of(
                "<gray>Type: <white>" + prettyMaterial(recipe.result().getType()) + "</white></gray>",
                "<yellow>Cost: <white>" + recipe.levelCost() + " XP levels</white></yellow>",
                "<dark_gray>Preview only. Use the Combine button below.</dark_gray>"
            )
        );
    }

    private ItemStack confirmItem(AnvilRecipe recipe) {
        if (recipe == null) {
            return menuItem(
                Material.GRAY_CONCRETE,
                "<gray><bold>Waiting for Items</bold></gray>",
                List.of("<dark_gray>Add a supported combination above.</dark_gray>")
            );
        }
        return menuItem(
            Material.LIME_CONCRETE,
            "<green><bold>Combine Items</bold></green>",
            List.of(
                "<gray>Cost: <white>" + recipe.levelCost() + " XP levels</white></gray>",
                "<yellow>Tap or click to create the shown result.</yellow>"
            )
        );
    }

    private void shiftMoveIntoAnvil(Player player, Inventory top, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (isEmpty(clicked)) {
            return;
        }
        int destination = isEmpty(top.getItem(LEFT_INPUT_SLOT))
            ? LEFT_INPUT_SLOT
            : isEmpty(top.getItem(RIGHT_INPUT_SLOT)) ? RIGHT_INPUT_SLOT : -1;
        if (destination < 0) {
            player.sendMessage(MessageUtil.warn("Both custom-anvil input slots are full."));
            return;
        }
        top.setItem(destination, clicked.clone());
        event.setCurrentItem(null);
        refreshAnvil(top);
        player.updateInventory();
    }

    private void returnInputSlots(Player player, Inventory inventory) {
        returnSlot(player, inventory, LEFT_INPUT_SLOT);
        returnSlot(player, inventory, RIGHT_INPUT_SLOT);
    }

    private void returnSlot(Player player, Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (isEmpty(item)) {
            return;
        }
        inventory.setItem(slot, null);
        InventoryRecipeUtil.giveOrDrop(player, item);
    }

    private static void evacuateToDrops(Inventory inventory, List<ItemStack> drops, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (isEmpty(item)) {
            return;
        }
        drops.add(item.clone());
        inventory.setItem(slot, null);
    }

    private static void consumeOne(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (isEmpty(item) || item.getAmount() <= 1) {
            inventory.setItem(slot, null);
            return;
        }
        item.setAmount(item.getAmount() - 1);
        inventory.setItem(slot, item);
    }

    private void decorateFrame(Inventory inventory) {
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot < 9 || slot >= inventory.getSize() - 9 || slot % 9 == 0 || slot % 9 == 8) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private ItemStack actionItem(Material material, String name, List<String> lore, String action) {
        ItemStack item = menuItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
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

    private String action(ItemStack item) {
        if (isEmpty(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private Plugin findEnabledPlugin(String namePart) {
        String needle = namePart.toLowerCase(Locale.ROOT);
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            if (candidate.isEnabled() && candidate.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                return candidate;
            }
        }
        return null;
    }

    private static String pluginStatus(Plugin plugin) {
        return plugin == null ? "not detected" : plugin.getName() + " " + plugin.getPluginMeta().getVersion();
    }

    private static boolean isNormalClick(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT;
    }

    private static boolean isBlockedClick(ClickType click) {
        return click == ClickType.DOUBLE_CLICK
            || click == ClickType.DROP
            || click == ClickType.CONTROL_DROP
            || click == ClickType.MIDDLE
            || click == ClickType.NUMBER_KEY
            || click == ClickType.SWAP_OFFHAND
            || click == ClickType.UNKNOWN
            || click.isKeyboardClick()
            || click.isCreativeAction();
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private static String itemName(ItemStack item) {
        if (isEmpty(item)) {
            return "Empty";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            return PLAIN.serialize(meta.displayName());
        }
        return prettyMaterial(item.getType());
    }

    private static String prettyMaterial(Material material) {
        StringBuilder name = new StringBuilder();
        for (String part : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    private static String miniEscape(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("<", "\\<");
    }

    public record AnvilRecipe(
        ItemStack result,
        int levelCost,
        String successMessage,
        String auditDescription,
        boolean recordAcquisition
    ) {
        public AnvilRecipe {
            if (result == null || result.getType().isAir()) {
                throw new IllegalArgumentException("Custom-anvil result cannot be empty");
            }
            result = result.clone();
            result.setAmount(1);
            levelCost = Math.max(0, levelCost);
            successMessage = successMessage == null || successMessage.isBlank() ? "Items combined." : successMessage;
            auditDescription = auditDescription == null ? "Created through the crossplay custom anvil." : auditDescription;
        }
    }

    private record ControlsHolder() implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record CrossplayAnvilHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.MutableMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
