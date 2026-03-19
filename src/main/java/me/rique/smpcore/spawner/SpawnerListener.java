package me.rique.smpcore.spawner;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitScheduler;
import io.papermc.paper.event.player.PlayerPickBlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles block/entity events for custom spawners.
 */
public final class SpawnerListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final NamespacedKey STACK_COUNT_KEY = new NamespacedKey("smpcore", "stack_count");
    public static final NamespacedKey SUGAR_COUNT_KEY = new NamespacedKey("smpcore", "sugar_count");
    public static final NamespacedKey REDSTONE_CONTROLLED_KEY = new NamespacedKey("smpcore", "redstone_controlled");
    public static final NamespacedKey AI_NERFED_KEY = new NamespacedKey("smpcore", "ai_nerfed");

    private final SMPCore plugin;
    private final SpawnerManager manager;
    private final BukkitScheduler scheduler;

    public SpawnerListener(SMPCore plugin) {
        this.plugin = plugin;
        this.manager = plugin.getSpawnerManager();
        this.scheduler = plugin.getServer().getScheduler();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.SPAWNER) return;

        ItemStack item = event.getItemInHand();
        ItemMeta meta = item.getItemMeta();

        String entityType = "PIG";
        if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof CreatureSpawner cs) {
            EntityType et = cs.getSpawnedType();
            if (et != null) entityType = et.name();
        }

        int stackCount = 1;
        int sugarCount = 0;
        boolean redstoneControlled = false;
        boolean aiNerfed = false;
        if (meta != null) {
            Integer pdc = meta.getPersistentDataContainer().get(STACK_COUNT_KEY, PersistentDataType.INTEGER);
            if (pdc != null) stackCount = pdc;
            Integer sugar = meta.getPersistentDataContainer().get(SUGAR_COUNT_KEY, PersistentDataType.INTEGER);
            if (sugar != null) sugarCount = sugar;
            Byte redstone = meta.getPersistentDataContainer().get(REDSTONE_CONTROLLED_KEY, PersistentDataType.BYTE);
            if (redstone != null) redstoneControlled = redstone == (byte) 1;
            Byte ai = meta.getPersistentDataContainer().get(AI_NERFED_KEY, PersistentDataType.BYTE);
            if (ai != null) aiNerfed = ai == (byte) 1;
        }
        stackCount = Math.max(1, Math.min(stackCount, plugin.getConfigManager().spawnerMaxStack));
        sugarCount = Math.max(0, Math.min(sugarCount, plugin.getConfigManager().spawnerMaxSugar));

        Location loc = event.getBlock().getLocation();
        if (manager.isTracked(loc)) return;

        if (loc.getBlock().getState() instanceof CreatureSpawner cs) {
            try {
                cs.setSpawnedType(EntityType.valueOf(entityType));
            } catch (IllegalArgumentException ignored) {
                // Keep default type if malformed item metadata is encountered.
            }
            cs.update(true, false);
        }

        manager.register(loc, entityType, stackCount, sugarCount, redstoneControlled, aiNerfed);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        Location loc = block.getLocation();
        SpawnerData data = manager.getData(loc);
        Player player = event.getPlayer();

        String entityType = "PIG";
        int stackCount = 1;
        int sugarCount = 0;
        boolean redstoneControlled = false;
        boolean aiNerfed = false;
        if (data != null) {
            entityType = data.entityType();
            stackCount = data.stackCount();
            sugarCount = data.sugarCount();
            redstoneControlled = data.redstoneControlled();
            aiNerfed = data.aiNerfed();
        } else if (block.getState() instanceof CreatureSpawner cs) {
            if (cs.getSpawnedType() != null) {
                entityType = cs.getSpawnedType().name();
            }
            Integer stackPdc = cs.getPersistentDataContainer().get(STACK_COUNT_KEY, PersistentDataType.INTEGER);
            if (stackPdc != null) stackCount = stackPdc;
            Integer sugarPdc = cs.getPersistentDataContainer().get(SUGAR_COUNT_KEY, PersistentDataType.INTEGER);
            if (sugarPdc != null) sugarCount = sugarPdc;
            Byte redstonePdc = cs.getPersistentDataContainer().get(REDSTONE_CONTROLLED_KEY, PersistentDataType.BYTE);
            if (redstonePdc != null) redstoneControlled = redstonePdc == (byte) 1;
            Byte aiPdc = cs.getPersistentDataContainer().get(AI_NERFED_KEY, PersistentDataType.BYTE);
            if (aiPdc != null) aiNerfed = aiPdc == (byte) 1;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean hasSilk = tool.containsEnchantment(Enchantment.SILK_TOUCH);
        boolean canSilk = plugin.getConfigManager().spawnerSilkTouchEnabled
            && player.getGameMode() != GameMode.CREATIVE
            && hasSilk
            && player.hasPermission("smpcore.spawner.use");

        event.setExpToDrop(0);
        event.setDropItems(false);
        // Run one tick later so we only apply custom drops after all plugins finalize cancellation.
        final String finalEntityType = entityType;
        final int finalStackCount = stackCount;
        final int finalSugarCount = sugarCount;
        final boolean finalRedstoneControlled = redstoneControlled;
        final boolean finalAiNerfed = aiNerfed;
        scheduler.runTask(plugin, () -> {
            if (loc.getBlock().getType() == Material.SPAWNER) {
                return;
            }

            if (manager.isTracked(loc)) {
                manager.unregister(loc);
            }

            if (player.getGameMode() == GameMode.CREATIVE) {
                return;
            }

            if (canSilk) {
                block.getWorld().dropItemNaturally(
                    loc.clone().add(0.5, 0.5, 0.5),
                    buildSpawnerItem(finalEntityType, finalStackCount, finalSugarCount, finalRedstoneControlled, finalAiNerfed)
                );
                return;
            }

            final int xp = 15 + (finalStackCount - 1) * 5;
            block.getWorld().spawn(loc.clone().add(0.5, 0.5, 0.5), ExperienceOrb.class, orb -> orb.setExperience(xp));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        Material handType = hand == null ? Material.AIR : hand.getType();

        if (isSpawnEgg(handType)) {
            if (!player.hasPermission("smpcore.spawner.use")) return;
            event.setCancelled(true);
            handleSpawnEgg(player, block.getLocation(), hand);
            return;
        }

        if (!player.hasPermission("smpcore.spawner.use")) return;

        Location loc = block.getLocation();
        if (handType == Material.SUGAR) {
            event.setCancelled(true);
            handleSugar(player, loc, hand);
            return;
        }
        if (handType == Material.REDSTONE) {
            event.setCancelled(true);
            handleRedstone(player, loc, hand);
            return;
        }
        if (isQuartzItem(handType)) {
            event.setCancelled(true);
            handleResetModifiers(player, loc, hand);
            return;
        }
        if (handType == Material.ENDER_EYE) {
            event.setCancelled(true);
            handleEyeOfEnder(player, loc, hand);
            return;
        }
        if (handType == Material.SPAWNER) {
            event.setCancelled(true);
            handleSpawnerStack(player, loc, hand);
            return;
        }

        event.setCancelled(true);
        openModifierGuide(player, loc);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        Location loc = event.getSpawner().getLocation();
        SpawnerData data = manager.getData(loc);
        if (data == null) return;

        if (data.redstoneControlled()) {
            boolean powered = isPowered(loc.getBlock());
            boolean disableWhenPowered = plugin.getConfigManager().spawnerRedstoneDisables;
            if (powered == disableWhenPowered) {
                event.setCancelled(true);
                return;
            }
        }

        if (plugin.getConfigManager().spawnerAiNerfEnabled && data.aiNerfed() && event.getEntity() instanceof Mob mob) {
            scheduler.runTask(plugin, () -> {
                if (mob.isValid()) {
                    // Disable pathfinding/targeting but keep normal physics interactions for farms.
                    mob.setAware(false);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (event.getOldCurrent() == event.getNewCurrent()) return;
        updateNearbyRedstoneHolograms(event.getBlock());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        manager.onChunkLoad(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        manager.onChunkUnload(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        cleanupDestroyedSpawners(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        cleanupDestroyedSpawners(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickBlock(PlayerPickBlockEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) return;
        if (!player.hasPermission("smpcore.spawner.admin")) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("You do not have permission to clone custom spawners."));
            return;
        }

        SpawnerData data = manager.getData(block.getLocation());
        String entityType = "PIG";
        int stackCount = 1;
        int sugarCount = 0;
        boolean redstoneControlled = false;
        boolean aiNerfed = false;
        if (data != null) {
            entityType = data.entityType();
            stackCount = data.stackCount();
            sugarCount = data.sugarCount();
            redstoneControlled = data.redstoneControlled();
            aiNerfed = data.aiNerfed();
        } else if (block.getState() instanceof CreatureSpawner cs) {
            if (cs.getSpawnedType() != null) {
                entityType = cs.getSpawnedType().name();
            }
            Integer stackPdc = cs.getPersistentDataContainer().get(STACK_COUNT_KEY, PersistentDataType.INTEGER);
            if (stackPdc != null) stackCount = stackPdc;
            Integer sugarPdc = cs.getPersistentDataContainer().get(SUGAR_COUNT_KEY, PersistentDataType.INTEGER);
            if (sugarPdc != null) sugarCount = sugarPdc;
            Byte redstonePdc = cs.getPersistentDataContainer().get(REDSTONE_CONTROLLED_KEY, PersistentDataType.BYTE);
            if (redstonePdc != null) redstoneControlled = redstonePdc == (byte) 1;
            Byte aiPdc = cs.getPersistentDataContainer().get(AI_NERFED_KEY, PersistentDataType.BYTE);
            if (aiPdc != null) aiNerfed = aiPdc == (byte) 1;
        }

        ItemStack custom = buildSpawnerItem(entityType, stackCount, sugarCount, redstoneControlled, aiNerfed);
        player.getInventory().setItem(event.getTargetSlot(), custom);
        player.getInventory().setHeldItemSlot(event.getTargetSlot());
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuideClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SpawnerGuideHolder)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuideDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SpawnerGuideHolder) {
            event.setCancelled(true);
        }
    }

    private void handleSugar(Player player, Location loc, ItemStack hand) {
        SpawnerData data = ensureTracked(loc);
        if (data == null) {
            player.sendMessage(MessageUtil.error("Could not read this spawner."));
            return;
        }

        int maxSugar = plugin.getConfigManager().spawnerMaxSugar;
        if (data.sugarCount() >= maxSugar) {
            player.sendMessage(MessageUtil.warn("This spawner is already at maximum speed!"));
            return;
        }

        int added = manager.addSugar(loc, 1);
        if (added <= 0) return;

        consumeOne(player, hand);
        double mult = data.speedMultiplier(maxSugar, plugin.getConfigManager().spawnerMaxMultiplier);
        player.sendMessage(MessageUtil.success(
            "Speed increased to <white>" + String.format(Locale.US, "%.2f", mult) + "x</white> "
                + "(<yellow>" + data.sugarCount() + "/" + maxSugar + "</yellow> sugar)."));
    }

    private void handleRedstone(Player player, Location loc, ItemStack hand) {
        SpawnerData tracked = ensureTracked(loc);
        if (tracked == null) {
            player.sendMessage(MessageUtil.error("Could not read this spawner."));
            return;
        }

        manager.toggleRedstone(loc);
        SpawnerData data = manager.getData(loc);
        consumeOne(player, hand);
        player.sendMessage(MessageUtil.success(
            "Redstone control <white>" + (data.redstoneControlled() ? "enabled" : "disabled") + "</white>."));
    }

    private void handleResetModifiers(Player player, Location loc, ItemStack hand) {
        SpawnerData data = ensureTracked(loc);
        if (data == null) {
            player.sendMessage(MessageUtil.error("Could not read this spawner."));
            return;
        }

        if (data.sugarCount() == 0 && !data.redstoneControlled() && !data.aiNerfed()) {
            player.sendMessage(MessageUtil.info("Spawner has no modifiers to reset."));
            return;
        }

        manager.resetModifiers(loc);
        consumeOne(player, hand);
        player.sendMessage(MessageUtil.success(
            "Spawner modifiers reset (<white>stack kept at x" + data.stackCount() + "</white>)."));
    }

    private void handleEyeOfEnder(Player player, Location loc, ItemStack hand) {
        SpawnerData data = ensureTracked(loc);
        if (data == null) {
            player.sendMessage(MessageUtil.error("Could not read this spawner."));
            return;
        }

        if (!plugin.getConfigManager().spawnerAiNerfEnabled) {
            player.sendMessage(MessageUtil.warn("AI nerf is disabled on this server."));
            return;
        }
        if (data.aiNerfed()) {
            player.sendMessage(MessageUtil.warn("AI nerf is already enabled on this spawner."));
            return;
        }

        manager.toggleAiNerf(loc);
        consumeOne(player, hand);
        player.sendMessage(MessageUtil.success(
            "AI nerf <white>enabled</white>. Spawned mobs will stop targeting/pathfinding."));
    }

    private void handleSpawnerStack(Player player, Location loc, ItemStack hand) {
        SpawnerData tracked = ensureTracked(loc);
        if (tracked == null) {
            player.sendMessage(MessageUtil.error("Could not read this spawner."));
            return;
        }

        ItemMeta meta = hand.getItemMeta();
        String entityType = "PIG";
        if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof CreatureSpawner cs) {
            EntityType et = cs.getSpawnedType();
            if (et != null) entityType = et.name();
        }

        int itemStack = 1;
        if (meta != null) {
            Integer pdc = meta.getPersistentDataContainer().get(STACK_COUNT_KEY, PersistentDataType.INTEGER);
            if (pdc != null) itemStack = pdc;
        }
        if (itemStack <= 0) {
            player.sendMessage(MessageUtil.error("Invalid spawner stack item."));
            return;
        }

        SpawnerManager.MergeResult merged = manager.mergeStack(loc, entityType, itemStack);
        if (merged == SpawnerManager.MergeResult.TYPE_MISMATCH) {
            player.sendMessage(MessageUtil.error("Spawner types must match to stack!"));
            return;
        }
        if (merged == SpawnerManager.MergeResult.WOULD_EXCEED_MAX) {
            player.sendMessage(MessageUtil.error(
                "That stack would exceed the max (<white>" + plugin.getConfigManager().spawnerMaxStack + "</white>)."));
            return;
        }
        if (merged != SpawnerManager.MergeResult.SUCCESS) return;

        consumeOne(player, hand);
        SpawnerData data = manager.getData(loc);
        player.sendMessage(MessageUtil.success("Spawner stacked! Stack: <yellow>x" + data.stackCount() + "</yellow>."));
    }

    private void handleSpawnEgg(Player player, Location loc, ItemStack hand) {
        String newType = entityTypeFromSpawnEgg(hand.getType());
        if (newType == null) {
            player.sendMessage(MessageUtil.error("That egg cannot be used on spawners."));
            return;
        }

        SpawnerData current = manager.getData(loc);
        if (current != null && current.entityType().equalsIgnoreCase(newType)) {
            player.sendMessage(MessageUtil.info("Spawner is already set to <white>" + formatName(newType) + "</white>."));
            return;
        }

        manager.setEntityType(loc, newType);
        consumeOne(player, hand);
        player.sendMessage(MessageUtil.success("Spawner type changed to <white>" + formatName(newType) + "</white>."));
    }

    private SpawnerData ensureTracked(Location loc) {
        SpawnerData existing = manager.getData(loc);
        if (existing != null) return existing;
        if (!(loc.getBlock().getState() instanceof CreatureSpawner cs)) return null;

        String entityType = cs.getSpawnedType() == null ? "PIG" : cs.getSpawnedType().name();
        int stackCount = 1;
        int sugarCount = 0;
        boolean redstoneControlled = false;
        boolean aiNerfed = false;

        Integer stackPdc = cs.getPersistentDataContainer().get(STACK_COUNT_KEY, PersistentDataType.INTEGER);
        if (stackPdc != null) stackCount = stackPdc;
        Integer sugarPdc = cs.getPersistentDataContainer().get(SUGAR_COUNT_KEY, PersistentDataType.INTEGER);
        if (sugarPdc != null) sugarCount = sugarPdc;
        Byte redstonePdc = cs.getPersistentDataContainer().get(REDSTONE_CONTROLLED_KEY, PersistentDataType.BYTE);
        if (redstonePdc != null) redstoneControlled = redstonePdc == (byte) 1;
        Byte aiPdc = cs.getPersistentDataContainer().get(AI_NERFED_KEY, PersistentDataType.BYTE);
        if (aiPdc != null) aiNerfed = aiPdc == (byte) 1;

        stackCount = Math.max(1, Math.min(stackCount, plugin.getConfigManager().spawnerMaxStack));
        sugarCount = Math.max(0, Math.min(sugarCount, plugin.getConfigManager().spawnerMaxSugar));
        manager.register(loc, entityType, stackCount, sugarCount, redstoneControlled, aiNerfed);
        return manager.getData(loc);
    }

    public static ItemStack buildSpawnerItem(String entityType, int stackCount) {
        return buildSpawnerItem(entityType, stackCount, 0, false, false);
    }

    public static ItemStack buildSpawnerItem(String entityType, int stackCount,
                                             int sugarCount, boolean redstoneControlled, boolean aiNerfed) {
        int normalizedStack = Math.max(1, stackCount);
        int normalizedSugar = Math.max(0, sugarCount);
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();

        if (meta instanceof BlockStateMeta bsm) {
            BlockState state = bsm.getBlockState();
            if (state instanceof CreatureSpawner cs) {
                try {
                    cs.setSpawnedType(EntityType.valueOf(entityType));
                } catch (IllegalArgumentException ignored) {
                    // Keep default type on malformed data.
                }
                bsm.setBlockState(cs);
            }
        }

        if (meta != null) {
            meta.getPersistentDataContainer().set(STACK_COUNT_KEY, PersistentDataType.INTEGER, normalizedStack);
            meta.getPersistentDataContainer().set(SUGAR_COUNT_KEY, PersistentDataType.INTEGER, normalizedSugar);
            meta.getPersistentDataContainer().set(
                REDSTONE_CONTROLLED_KEY, PersistentDataType.BYTE, redstoneControlled ? (byte) 1 : (byte) 0);
            meta.getPersistentDataContainer().set(
                AI_NERFED_KEY, PersistentDataType.BYTE, aiNerfed ? (byte) 1 : (byte) 0);

            String mobName = formatName(entityType);
            Component name = normalizedStack > 1
                ? MM.deserialize("<yellow>" + mobName + " Spawner</yellow> <white>x" + normalizedStack + "</white>")
                : MM.deserialize("<yellow>" + mobName + " Spawner</yellow>");
            meta.displayName(name);

            List<Component> lore = new ArrayList<>();
            if (normalizedStack > 1) {
                lore.add(MM.deserialize("<gray>Stack count: <white>" + normalizedStack + "</white></gray>"));
            }
            if (normalizedSugar > 0) {
                lore.add(MM.deserialize("<gray>Sugar: <white>" + normalizedSugar + "</white></gray>"));
            }
            if (redstoneControlled) {
                lore.add(MM.deserialize("<gray>Redstone control: <green>ON</green></gray>"));
            }
            if (aiNerfed) {
                lore.add(MM.deserialize("<gray>AI nerf: <light_purple>ON</light_purple></gray>"));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    private static String formatName(String entityType) {
        String[] parts = entityType.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            sb.append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private void consumeOne(Player player, ItemStack item) {
        if (player.getGameMode() == GameMode.CREATIVE) return;

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return;
        }

        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    }

    private void openModifierGuide(Player player, Location loc) {
        SpawnerData data = ensureTracked(loc);
        if (data == null) {
            player.sendMessage(MessageUtil.error("Could not read this spawner."));
            return;
        }

        int maxSugar = plugin.getConfigManager().spawnerMaxSugar;
        double speed = data.speedMultiplier(maxSugar, plugin.getConfigManager().spawnerMaxMultiplier);
        boolean powered = isPowered(loc.getBlock());
        boolean running = !data.redstoneControlled()
            || (powered != plugin.getConfigManager().spawnerRedstoneDisables);

        Inventory inv = plugin.getServer().createInventory(
            new SpawnerGuideHolder(),
            27,
            MM.deserialize("<gold><bold>Spawner Modifiers</bold></gold>")
        );

        ItemStack filler = createGuideItem(
            Material.GRAY_STAINED_GLASS_PANE,
            "<dark_gray> ",
            List.of()
        );
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(10, createGuideItem(
            Material.SUGAR,
            "<gold>Sugar</gold>",
            List.of(
                "<gray>Adds spawn speed up to <white>16x</white>.</gray>",
                "<gray>Current: <white>" + data.sugarCount() + "/" + maxSugar + "</white> (" + String.format(Locale.US, "%.2f", speed) + "x)</gray>"
            )
        ));

        inv.setItem(11, createGuideItem(
            Material.REDSTONE,
            "<gold>Redstone Dust</gold>",
            List.of(
                "<gray>Enables redstone control mode.</gray>",
                "<gray>Powered state toggles the spawner.</gray>",
                "<gray>Current: <white>" + (data.redstoneControlled() ? (running ? "ON" : "OFF") : "DISABLED") + "</white></gray>"
            )
        ));

        inv.setItem(12, createGuideItem(
            Material.ENDER_EYE,
            "<gold>Eye of Ender</gold>",
            List.of(
                "<gray>Enables AI nerf for spawned mobs.</gray>",
                "<gray>Current: <white>" + (data.aiNerfed() ? "ON" : "OFF") + "</white></gray>"
            )
        ));

        inv.setItem(13, createGuideItem(
            Material.QUARTZ,
            "<gold>Nether Quartz</gold>",
            List.of(
                "<gray>Resets sugar/redstone/AI modifiers.</gray>",
                "<gray>Stack size is kept.</gray>"
            )
        ));

        inv.setItem(14, createGuideItem(
            Material.SPAWNER,
            "<gold>Spawner Item</gold>",
            List.of(
                "<gray>Stack same-type spawners together.</gray>",
                "<gray>Current stack: <white>x" + data.stackCount() + "</white></gray>"
            )
        ));

        inv.setItem(15, createGuideItem(
            Material.ZOMBIE_SPAWN_EGG,
            "<gold>Spawn Egg</gold>",
            List.of(
                "<gray>Changes the spawner mob type.</gray>",
                "<gray>Current type: <white>" + formatName(data.entityType()) + "</white></gray>"
            )
        ));

        player.openInventory(inv);
    }

    private ItemStack createGuideItem(Material material, String title, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(MM.deserialize(title));
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>(loreLines.size());
            for (String line : loreLines) {
                lore.add(MM.deserialize(line));
            }
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean isSpawnEgg(Material material) {
        return material.name().endsWith("_SPAWN_EGG");
    }

    private static boolean isQuartzItem(Material material) {
        return material == Material.QUARTZ || "NETHER_QUARTZ".equals(material.name());
    }

    private static String entityTypeFromSpawnEgg(Material material) {
        if (!isSpawnEgg(material)) return null;
        String base = material.name().substring(0, material.name().length() - "_SPAWN_EGG".length());
        try {
            EntityType.valueOf(base);
            return base;
        } catch (IllegalArgumentException ignored) {
            // Known alias mismatch safety fallback.
            if ("MOOSHROOM".equals(base)) return "MUSHROOM_COW";
            if ("ZOMBIE_PIGMAN".equals(base)) return "PIG_ZOMBIE";
            return null;
        }
    }

    private static boolean isPowered(Block block) {
        return block.isBlockPowered() || block.isBlockIndirectlyPowered() || block.getBlockPower() > 0;
    }

    private void updateNearbyRedstoneHolograms(Block source) {
        final int horizontalRadius = 2;
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    Block nearby = source.getRelative(dx, dy, dz);
                    if (nearby.getType() != Material.SPAWNER) continue;
                    Location spawnerLoc = nearby.getLocation();
                    SpawnerData data = manager.getData(spawnerLoc);
                    if (data == null || !data.redstoneControlled()) continue;
                    manager.updateHologram(spawnerLoc);
                }
            }
        }
    }

    private void cleanupDestroyedSpawners(List<Block> blocks) {
        for (Block block : blocks) {
            if (block.getType() != Material.SPAWNER) continue;
            if (!manager.isTracked(block.getLocation())) continue;
            manager.unregister(block.getLocation());
        }
    }

    private record SpawnerGuideHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
