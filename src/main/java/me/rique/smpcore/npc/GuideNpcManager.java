package me.rique.smpcore.npc;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuideNpcManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MENU_SIZE = 36;
    private static final int CLOSE_SLOT = 31;
    private static final long NPC_OPEN_DEBOUNCE_MS = 450L;

    private static final String ACTION_WELCOME = "welcome";
    private static final String ACTION_CHANGELOG = "changelog";
    private static final String ACTION_WIKI = "wiki";
    private static final String ACTION_SEASON = "season";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_GEAR_HOME = "gear_home";
    private static final String ACTION_GEAR_REFORGE = "gear_reforge";
    private static final String ACTION_GEAR_AWAKENING = "gear_awakening";
    private static final String ACTION_GEAR_CORRUPTION = "gear_corruption";
    private static final String ACTION_GEAR_ORBS = "gear_orbs";
    private static final String ACTION_GEAR_RUNIC = "gear_runic";
    private static final String ACTION_GEAR_FATE = "gear_fate";
    private static final String ACTION_GEAR_MYTHIC = "gear_mythic";
    private static final String ACTION_GEAR_UTILITIES = "gear_utilities";
    private static final String ACTION_GEAR_ORDER = "gear_order";

    private final SMPCore plugin;
    private final NamespacedKey keyNpcType;
    private final NamespacedKey keyMenuAction;
    private final Map<UUID, Long> nextNpcOpenAt = new ConcurrentHashMap<>();
    private GuideNpcBridge npcBridge;

    public GuideNpcManager(SMPCore plugin) {
        this.plugin = plugin;
        this.keyNpcType = new NamespacedKey(plugin, "guide_npc_type");
        this.keyMenuAction = new NamespacedKey(plugin, "guide_menu_action");
    }

    public void start() {
        tryEnableCitizensBridge();
        scheduleRefreshes();
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder(false) instanceof GuideMenuHolder) {
                player.closeInventory();
            }
        }
        if (npcBridge != null) {
            npcBridge.shutdown();
            npcBridge = null;
        }
        nextNpcOpenAt.clear();
    }

    public Entity spawnNpc(GuideNpcType type, Location location) {
        Location spawn = centeredSpawnLocation(location);
        Entity citizensNpc = spawnCitizensNpc(type, spawn);
        if (citizensNpc != null) {
            spawnEffects(type, citizensNpc.getLocation());
            plugin.getLogger().info("Spawned Citizens " + type.displayName + " at " + locationSummary(citizensNpc.getLocation()) + ".");
            return citizensNpc;
        }

        ArmorStand armorStand = spawn.getWorld().spawn(spawn, ArmorStand.class, stand -> configureFallbackNpc(type, stand));
        spawnEffects(type, armorStand.getLocation());
        plugin.getLogger().info("Spawned " + type.displayName + " at " + locationSummary(armorStand.getLocation()) + ".");
        return armorStand;
    }

    public int removeNearest(GuideNpcType type, Location origin, double radius) {
        if (npcBridge != null) {
            int removed = npcBridge.removeNearest(type, origin, radius);
            if (removed > 0) {
                return removed;
            }
        }

        Entity nearest = findFallbackNpcs(type).stream()
            .filter(entity -> entity.getWorld().equals(origin.getWorld()))
            .filter(entity -> entity.getLocation().distanceSquared(origin) <= radius * radius)
            .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(origin)))
            .orElse(null);
        if (nearest == null) {
            return 0;
        }
        nearest.remove();
        return 1;
    }

    public List<Location> locations(GuideNpcType type) {
        List<Location> locations = new ArrayList<>();
        if (npcBridge != null) {
            locations.addAll(npcBridge.locations(type));
        }
        locations.addAll(findFallbackNpcs(type).stream().map(Entity::getLocation).toList());
        return locations;
    }

    public int refreshNpcs() {
        int refreshed = 0;
        if (npcBridge != null) {
            refreshed += npcBridge.refreshLoadedNpcs();
        }
        for (GuideNpcType type : GuideNpcType.values()) {
            for (Entity entity : new ArrayList<>(findFallbackNpcs(type))) {
                if (entity instanceof ArmorStand armorStand) {
                    configureFallbackNpc(type, armorStand);
                    refreshed++;
                }
            }
        }
        return refreshed;
    }

    public void openMenuFromNpc(Player player, GuideNpcType type) {
        openMenuFromNpc(player, type, null);
    }

    public void openMenuFromNpc(Player player, GuideNpcType type, Entity interactionTarget) {
        if (player == null || type == null) {
            return;
        }
        String permission = permissionFor(type);
        if (!player.hasPermission(permission)) {
            player.sendMessage(MessageUtil.warn("You cannot use this NPC."));
            return;
        }

        long now = System.currentTimeMillis();
        Long nextOpenAt = nextNpcOpenAt.get(player.getUniqueId());
        if (nextOpenAt != null && nextOpenAt > now) {
            return;
        }
        nextNpcOpenAt.put(player.getUniqueId(), now + NPC_OPEN_DEBOUNCE_MS);
        if (plugin.getStoryService() != null) {
            plugin.getStoryService().onNpcInteract(player, type.id());
        }
        if (type == GuideNpcType.SPAWN_GUIDE) {
            openSpawnGuideMenu(player);
        } else if (type == GuideNpcType.CORRUPTION_WARDEN) {
            openCorruptionWardenMenu(player);
        } else if (type == GuideNpcType.GEAR_EXPERT) {
            openGearExpertMenu(player);
        } else if (type == GuideNpcType.MAYOR && plugin.getMayorQuestManager() != null) {
            plugin.getMayorQuestManager().openFromMayorNpc(player);
        } else if (type == GuideNpcType.DUNGEON_KEEPER && plugin.getBossDungeonManager() != null) {
            plugin.getBossDungeonManager().openFromKeeper(player);
        } else if ((type == GuideNpcType.BREWMASTER || type == GuideNpcType.CARDSHARP) && plugin.getTavernManager() != null) {
            plugin.getTavernManager().openNpc(player, type);
        } else if (type == GuideNpcType.DEALER && plugin.getBlackjackManager() != null) {
            plugin.getBlackjackManager().openDealer(player);
        } else if (type == GuideNpcType.ROULETTE_CROUPIER && plugin.getRouletteManager() != null) {
            plugin.getRouletteManager().openFromNpc(player);
        } else if (type == GuideNpcType.DUELMASTER && plugin.getDuelManager() != null) {
            plugin.getDuelManager().openMainMenu(player);
        } else if (type == GuideNpcType.GOBLIN_HUNTER && plugin.getGoblinHuntManager() != null) {
            plugin.getGoblinHuntManager().openFromHunter(player);
        } else if (type == GuideNpcType.MINER && plugin.getMinerManager() != null) {
            plugin.getMinerManager().openFromMiner(player);
        } else if (type == GuideNpcType.FARMER && plugin.getFarmerManager() != null) {
            plugin.getFarmerManager().openFromFarmer(player);
        } else if (type == GuideNpcType.WITCH && plugin.getWitchManager() != null) {
            plugin.getWitchManager().openFromWitch(player);
        } else if (type == GuideNpcType.OVERSEER && plugin.getOverseerManager() != null) {
            plugin.getOverseerManager().openFromNpc(player);
        } else if (type == GuideNpcType.BEASTWARDEN && plugin.getBeastwardenManager() != null) {
            plugin.getBeastwardenManager().openFromNpc(player);
        } else if (type == GuideNpcType.BOSSBROKER && plugin.getBossMasteryManager() != null) {
            plugin.getBossMasteryManager().openFromNpc(player);
        } else if (type == GuideNpcType.BLACK_MARKETEER && plugin.getBlackMarketManager() != null) {
            plugin.getBlackMarketManager().openFromNpc(player);
        } else if (type == GuideNpcType.FISHER && plugin.getFisherManager() != null) {
            plugin.getFisherManager().openFromFisher(player);
        } else if (type.isSpawnLife() && plugin.getSpawnLifeManager() != null) {
            plugin.getSpawnLifeManager().openFromNpc(player, type, interactionTarget);
        }
    }

    static String permissionFor(GuideNpcType type) {
        return switch (type) {
            case MAYOR -> "smpcore.mayor.use";
            case DUNGEON_KEEPER -> "smpcore.dungeon.use";
            case DEALER -> "smpcore.blackjack";
            case ROULETTE_CROUPIER -> "smpcore.roulette";
            case DUELMASTER -> "smpcore.duel.use";
            case BEASTWARDEN -> "smpcore.beastwarden.use";
            case BOSSBROKER -> "smpcore.bossmastery.use";
            case BLACK_MARKETEER -> "smpcore.blackmarket.use";
            case FETCH_HOUND, TOWN_CAT, TOWN_FOX, TOWN_PARROT, HIDDEN_ILLUSIONER,
                 TOWN_BAKER, TOWN_MASON, TOWN_COURIER, TOWN_DOCKHAND, TOWN_SEAMSTRESS,
                 TAVERN_HOST, TAVERN_REGULAR, TAVERN_TIPSY -> "smpcore.spawnlife.use";
            default -> "smpcore.guide.use";
        };
    }

    public Entity nearestLoadedNpc(GuideNpcType type, Location origin, double radius) {
        if (type == null || origin == null || origin.getWorld() == null || !Double.isFinite(radius) || radius <= 0.0D) {
            return null;
        }
        double radiusSquared = radius * radius;
        return origin.getWorld().getNearbyEntities(origin, radius, radius, radius).stream()
            .filter(entity -> typeOfEntity(entity) == type)
            .filter(Entity::isValid)
            .filter(entity -> entity.getLocation().distanceSquared(origin) <= radiusSquared)
            .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(origin)))
            .orElse(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuideNpcInteract(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerArmorStandManipulateEvent) {
            return;
        }
        GuideNpcType type = typeOfEntity(event.getRightClicked());
        if (type == null) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        openMenuFromNpcNextTick(event.getPlayer(), type, event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuideNpcArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        GuideNpcType type = typeOfEntity(event.getRightClicked());
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            openMenuFromNpcNextTick(event.getPlayer(), type, event.getRightClicked());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuideNpcDamage(EntityDamageEvent event) {
        if (typeOfEntity(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof GuideMenuHolder holder)) {
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

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (!MenuItemUtil.isVisibleItem(event.getCurrentItem())) {
            return;
        }
        String action = actionOf(event.getCurrentItem());
        if (action == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> handleMenuAction(player, action));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof GuideMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        nextNpcOpenAt.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("Citizens".equalsIgnoreCase(event.getPlugin().getName())) {
            tryEnableCitizensBridge();
            refreshNpcs();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity entity : event.getChunk().getEntities()) {
                GuideNpcType type = typeOfEntity(entity);
                if (type != null && entity instanceof ArmorStand armorStand) {
                    configureFallbackNpc(type, armorStand);
                }
            }
        });
    }

    private void openSpawnGuideMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new GuideMenuHolder(player.getUniqueId(), GuideNpcType.SPAWN_GUIDE),
            MENU_SIZE,
            Component.text("Mira's Guide")
        );
        fill(inventory);
        inventory.setItem(4, item(Material.WRITABLE_BOOK, "<gradient:#22d3ee:#a78bfa><bold>Mira's Guide</bold></gradient>", List.of(
            "<gray>Welcome to Season 5: Season of the Veil.</gray>",
            "<dark_gray>Short, useful, and safe to click.</dark_gray>"
        ), null));
        inventory.setItem(10, item(Material.COMPASS, "<aqua><bold>Start Here</bold></aqua>", List.of(
            "<gray>Spawn is protected. Use <white>/spawn</white> to return.</gray>",
            "<gray><white>/menu</white> opens gear, bosses, teams, and guides.</gray>",
            "<yellow>Click for the welcome notes.</yellow>"
        ), ACTION_WELCOME));
        inventory.setItem(12, item(Material.KNOWLEDGE_BOOK, "<gold><bold>Changelog</bold></gold>", List.of(
            "<gray>Short update notes for players.</gray>",
            "<yellow>Click to open.</yellow>"
        ), ACTION_CHANGELOG));
        inventory.setItem(14, item(Material.BOOK, "<yellow><bold>Wiki</bold></yellow>", List.of(
            "<gray>Rules, recipes, systems, and deeper help.</gray>",
            "<yellow>Click to get the link.</yellow>"
        ), ACTION_WIKI));
        inventory.setItem(16, item(Material.ECHO_SHARD, "<gradient:#7c3aed:#22d3ee><bold>Season Basics</bold></gradient>", List.of(
            "<white>Season 5: Season of the Veil</white>",
            "<gray>Essence buys buffs. Bosses drop materials.</gray>",
            "<gray>Corruption is powerful but risky.</gray>",
            "<yellow>Click for a quick summary.</yellow>"
        ), ACTION_SEASON));
        inventory.setItem(22, item(Material.PLAYER_HEAD, "<green><bold>New Player Tip</bold></green>", List.of(
            "<gray>Join a team, read recipes before crafting,</gray>",
            "<gray>and do not risk items you cannot lose.</gray>"
        ), null));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "<red><bold>Close</bold></red>", List.of("<gray>Close this menu.</gray>"), ACTION_CLOSE));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.45f, 1.35f);
    }

    private void openCorruptionWardenMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new GuideMenuHolder(player.getUniqueId(), GuideNpcType.CORRUPTION_WARDEN),
            MENU_SIZE,
            Component.text("Veyr's Warning")
        );
        fill(inventory);
        inventory.setItem(4, item(Material.SCULK_SHRIEKER, "<gradient:#ef4444:#7c3aed><bold>Veyr</bold></gradient>", List.of(
            "<gray>I handle corruption attempts. Check every outcome before using valuable gear.</gray>",
            "<dark_gray>Corruption attempts cannot be refunded.</dark_gray>"
        ), null));
        inventory.setItem(10, item(Material.RED_DYE, "<red><bold>Corrupted Essence</bold></red>", List.of(
            "<gray>One attempt per essence.</gray>",
            "<gray>25% x3/x4 stats. 25% -25% stats.</gray>",
            "<gray>25% -50% stats. 25% destroys the item.</gray>"
        ), null));
        inventory.setItem(12, item(Material.AMETHYST_SHARD, "<light_purple><bold>Corruption Stone</bold></light_purple>", List.of(
            "<gray>Cannot be used on legendary or mythic items.</gray>",
            "<gray>50% x2 stats. 50% seals unchanged.</gray>"
        ), null));
        inventory.setItem(14, item(Material.NETHERITE_SWORD, "<dark_red><bold>Locked Forever</bold></dark_red>", List.of(
            "<gray>Every result seals the item.</gray>",
            "<gray>No enchanting, reforging, crafting, or edits.</gray>"
        ), null));
        inventory.setItem(16, item(Material.RESPAWN_ANCHOR, "<gold><bold>The Ritual</bold></gold>", List.of(
            "<gray>Place one item and one catalyst.</gray>",
            "<gray>The ritual lasts 5 seconds and announces the result.</gray>",
            "<red>Do not use gear you are not willing to lose.</red>"
        ), null));
        inventory.setItem(22, item(Material.SOUL_LANTERN, "<aqua><bold>Veyr's Advice</bold></aqua>", List.of(
            "<gray>Corrupt backups first. Save your best item</gray>",
            "<gray>for when you can survive a bad roll.</gray>"
        ), null));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "<red><bold>Close</bold></red>", List.of("<gray>Close this menu.</gray>"), ACTION_CLOSE));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, 0.45f, 1.0f);
    }

    private void openGearExpertMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new GuideMenuHolder(player.getUniqueId(), GuideNpcType.GEAR_EXPERT),
            54,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#f59e0b:#a78bfa><bold>Orin's Workshop</bold></gradient>"), "Orin's Workshop")
        );
        fill(inventory);
        inventory.setItem(4, item(Material.SMITHING_TABLE, "<gradient:#f59e0b:#a78bfa><bold>Orin the Artificer</bold></gradient>", List.of(
            "<gray>I explain how to improve gear without wasting it.</gray>",
            "<gray>Choose a system for exact steps, costs, and risks.</gray>"
        ), null));
        inventory.setItem(10, item(Material.ANVIL, "<gold><bold>Reforging</bold></gold>", List.of(
            "<gray>Roll one of 15 prefixes with a Reforge Stone.</gray>",
            "<yellow>Click for costs, effects, and reroll rules.</yellow>"
        ), ACTION_GEAR_REFORGE));
        inventory.setItem(12, item(Material.ENCHANTING_TABLE, "<red><bold>Awakening</bold></red>", List.of(
            "<gray>A rare permanent power upgrade with failure risk.</gray>",
            "<yellow>Click before risking valuable gear.</yellow>"
        ), ACTION_GEAR_AWAKENING));
        inventory.setItem(14, item(Material.RESPAWN_ANCHOR, "<dark_red><bold>Corruption</bold></dark_red>", List.of(
            "<gray>Extreme stat rolls that permanently seal the item.</gray>",
            "<red>Some outcomes weaken or destroy it.</red>"
        ), ACTION_GEAR_CORRUPTION));
        inventory.setItem(16, item(Material.ENDER_EYE, "<light_purple><bold>All Orbs</bold></light_purple>", List.of(
            "<gray>Warden's Lure, Veilshift, Runebloom,</gray>",
            "<gray>Orb of the Mystics, and " + soulImprintName(player) + ".</gray>",
            "<yellow>Click to learn where each one belongs.</yellow>"
        ), ACTION_GEAR_ORBS));
        inventory.setItem(19, item(Material.LOOM, "<aqua><bold>Runic Loom</bold></aqua>", List.of(
            "<gray>Push an existing enchant beyond its normal edge.</gray>",
            "<yellow>Click for requirements and safe caps.</yellow>"
        ), ACTION_GEAR_RUNIC));
        inventory.setItem(21, item(Material.LODESTONE, "<red><bold>Fate Crucible</bold></red>", List.of(
            "<gray>Gamble an entire rare-currency stack.</gray>",
            "<yellow>Click to understand the exact risk.</yellow>"
        ), ACTION_GEAR_FATE));
        inventory.setItem(23, item(Material.END_CRYSTAL, "<light_purple><bold>Mythic Forging</bold></light_purple>", List.of(
            "<gray>Fuse two specific legendaries with an Ascendant Core.</gray>",
            "<yellow>Click for the permanent consequences.</yellow>"
        ), ACTION_GEAR_MYTHIC));
        inventory.setItem(25, item(Material.CHEST, "<green><bold>Utility Stations</bold></green>", List.of(
            "<gray>Salvaging Depot and XP Lectern.</gray>",
            "<yellow>Click for safe use and recovery rules.</yellow>"
        ), ACTION_GEAR_UTILITIES));
        inventory.setItem(31, item(Material.COMPASS, "<yellow><bold>Recommended Upgrade Order</bold></yellow>", List.of(
            "<gray>What to do first, what stacks, and what locks later steps.</gray>",
            "<yellow>Click for Orin's safest progression path.</yellow>"
        ), ACTION_GEAR_ORDER));
        inventory.setItem(49, item(Material.BARRIER, "<red><bold>Close</bold></red>", List.of("<gray>Leave Orin's workshop.</gray>"), ACTION_CLOSE));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.6f, 1.15f);
    }

    private void openGearDetail(Player player, String action) {
        Inventory inventory = Bukkit.createInventory(
            new GuideMenuHolder(player.getUniqueId(), GuideNpcType.GEAR_EXPERT),
            45,
            BedrockCompat.menuTitle(player, Component.text(gearPageTitle(action)), gearPageTitle(action))
        );
        fill(inventory);
        switch (action) {
            case ACTION_GEAR_REFORGE -> renderReforgeHelp(inventory);
            case ACTION_GEAR_AWAKENING -> renderAwakeningHelp(inventory);
            case ACTION_GEAR_CORRUPTION -> renderCorruptionHelp(inventory);
            case ACTION_GEAR_ORBS -> renderOrbHelp(player, inventory);
            case ACTION_GEAR_RUNIC -> renderRunicHelp(inventory);
            case ACTION_GEAR_FATE -> renderFateHelp(player, inventory);
            case ACTION_GEAR_MYTHIC -> renderMythicHelp(inventory);
            case ACTION_GEAR_UTILITIES -> renderUtilityHelp(inventory);
            case ACTION_GEAR_ORDER -> renderUpgradeOrder(inventory);
            default -> {
                openGearExpertMenu(player);
                return;
            }
        }
        inventory.setItem(36, item(Material.ARROW, "<yellow><bold>Back to Workshop</bold></yellow>", List.of("<gray>Choose another gear system.</gray>"), ACTION_GEAR_HOME));
        inventory.setItem(44, item(Material.BARRIER, "<red><bold>Close</bold></red>", List.of("<gray>Leave Orin's workshop.</gray>"), ACTION_CLOSE));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, 1.2f);
    }

    private void renderReforgeHelp(Inventory inventory) {
        inventory.setItem(4, helpTitle(Material.ANVIL, "Reforging", "A flexible early-to-midgame stat reroll."));
        inventory.setItem(10, help(Material.AMETHYST_SHARD, "Reforge Stone", "Craft with 1 Smooth Stone and 4 Amethyst Shards.", "One stone is consumed per roll."));
        inventory.setItem(12, help(Material.PLAYER_HEAD, "Where", "Right-click Brannik and insert one gear item", "plus one Reforge Stone."));
        inventory.setItem(14, help(Material.NETHERITE_SWORD, "What Changes", "Rolls one of 15 good-to-bad prefixes.", "A new roll replaces the old prefix."));
        inventory.setItem(16, help(Material.ENCHANTED_BOOK, "What Stays", "Custom data, enchants, durability, and model", "data remain on the item."));
        inventory.setItem(22, help(Material.BARRIER, "Important Limit", "Corrupted items are sealed and cannot be reforged.", "Reforge before corruption."));
        inventory.setItem(24, help(Material.COMPASS, "Best Use", "Use cheap stones to find a prefix that fits", "your build before risking final upgrades."));
    }

    private void renderAwakeningHelp(Inventory inventory) {
        inventory.setItem(4, helpTitle(Material.ENCHANTING_TABLE, "Awakening", "A rare permanent upgrade with real failure risk."));
        inventory.setItem(10, help(Material.AMETHYST_SHARD, "Gear Catalyst", "Normal gear attempts consume 1 Awakening Shard.", "The shard comes from Rift Oracle progression."));
        inventory.setItem(12, help(Material.LIME_DYE, "Success", "Base success chance: 5%.", "Successful gear gains its awakening bonuses."));
        inventory.setItem(14, help(Material.FLINT_AND_STEEL, "Failure", "Failure removes 50% of remaining durability.", "Repair the item before every attempt."));
        inventory.setItem(16, help(Material.BARRIER, "Shatter Point", "If failure leaves under 15% durability,", "the item is destroyed."));
        inventory.setItem(20, help(Material.NETHER_STAR, "Ancient Scrolls", "An Ancient Scroll uses 1 Nether Star instead.", "Success becomes a class-choice scroll; failure destroys it."));
        inventory.setItem(22, help(Material.END_ROD, "Finding the Table", "Asterion the Rift Oracle is the normal source", "for Awakening Tables and Awakening Shards."));
        inventory.setItem(24, help(Material.SHIELD, "Final Path", "Use high-durability gear and finish every other edit first.", "Awakened items can never be corrupted."));
    }

    private void renderCorruptionHelp(Inventory inventory) {
        inventory.setItem(4, helpTitle(Material.RESPAWN_ANCHOR, "Corruption", "The final high-risk step for finished gear."));
        inventory.setItem(10, help(Material.RED_DYE, "Corrupted Essence", "25% x3/x4 stats; 25% -25%;", "25% -50%; 25% destroys the item."));
        inventory.setItem(12, help(Material.AMETHYST_SHARD, "Corruption Stone", "50% x2 stats; 50% seals unchanged.", "Cannot be used on legendary or mythic items."));
        inventory.setItem(14, help(Material.CLOCK, "The Ritual", "Insert one unlocked item and one catalyst.", "The corruption ritual takes 5 seconds."));
        inventory.setItem(16, help(Material.CHAINMAIL_CHESTPLATE, "Permanent Seal", "Every surviving result locks forever:", "no anvil, smithing, enchanting, grindstone, or reforge."));
        inventory.setItem(22, help(Material.TOTEM_OF_UNDYING, "No Safe Undo", "A bad roll cannot be cleansed or rerolled.", "Never corrupt the only copy of important gear."));
        inventory.setItem(24, help(Material.COMPASS, "Final Path", "Finish every other edit before corruption.", "Awakened items cannot enter this path."));
    }

    private void renderOrbHelp(Player player, Inventory inventory) {
        String imprintName = soulImprintName(player);
        inventory.setItem(4, helpTitle(Material.ENDER_EYE, "Orbs and Imprints", "Each currency has a different destination."));
        inventory.setItem(10, help(Material.ECHO_SHARD, "Warden's Lure Orb", "Click one armor piece in your inventory.", "Adds +5 boss aggro; one per armor piece."));
        inventory.setItem(12, help(Material.ENDER_EYE, "Veilshift Orb", "Click armor, a weapon, or a tool.", "Rolls one bonus stat; another orb replaces it."));
        inventory.setItem(14, help(Material.EXPERIENCE_BOTTLE, "Runebloom Orb", "Fuel for the Runic Loom.", "Raises one chosen existing enchant by +1 or +2."));
        inventory.setItem(16, help(Material.ENDER_PEARL, "Orb of the Mystics", "A single-use Enderman drop that summons", "a random legendary altar; caller cooldown is 1 hour."));
        inventory.setItem(20, help(Material.END_CRYSTAL, imprintName, "Click the same eligible gear item three times.", "Creates a copy that cannot be imprinted or corrupted again."));
        inventory.setItem(22, help(Material.LODESTONE, "Fate-Compatible Currency", "Veil orbs and " + imprintName + " can be gambled", "as a whole stack in the Fate Crucible."));
        inventory.setItem(24, help(Material.BOOK, "Find Recipes", "Open /reliquary, then Armory of the Veil", "or Mythic Works for exact ingredient lists."));
    }

    private void renderRunicHelp(Inventory inventory) {
        inventory.setItem(4, helpTitle(Material.LOOM, "Runic Loom", "Controlled enchant growth beyond normal limits."));
        inventory.setItem(10, help(Material.NETHERITE_SWORD, "Eligible Gear", "Insert one armor, tool, or weapon with", "at least 4 existing enchants."));
        inventory.setItem(12, help(Material.EXPERIENCE_BOTTLE, "Fuel", "Insert 1 Runebloom Orb.", "One orb is consumed for every successful upgrade."));
        inventory.setItem(14, help(Material.ENCHANTED_BOOK, "Choose the Enchant", "The menu lists eligible enchants already on the item.", "Pick exactly which enchant receives the roll."));
        inventory.setItem(16, help(Material.LIME_DYE, "The Roll", "Even odds of +1 or +2.", "The result is clamped to the loom's safe cap."));
        inventory.setItem(22, help(Material.BARRIER, "Restrictions", "Corrupted gear is locked out.", "An enchant already at the runic cap cannot rise again."));
        inventory.setItem(24, help(Material.CRAFTING_TABLE, "Reliable Orb Route", "1 Awakening Shard, 2 Riftglass Lenses,", "32 XP Bottles, and 32 Lapis Lazuli."));
    }

    private void renderFateHelp(Player player, Inventory inventory) {
        String imprintName = soulImprintName(player);
        inventory.setItem(4, helpTitle(Material.LODESTONE, "Fate Crucible", "An all-or-nothing currency gamble."));
        inventory.setItem(10, help(Material.ENDER_EYE, "Accepted Items", "Accepts eligible Veil orbs and " + imprintName + ".", "Normal gear and unrelated materials do not fit."));
        inventory.setItem(12, help(Material.CHEST, "Whole Stack", "The entire inserted stack is committed at once.", "Split the stack before opening the menu if needed."));
        inventory.setItem(14, help(Material.LIME_DYE, "50% Success", "The inserted stack is doubled and returned.", "Inventory overflow is returned or dropped safely."));
        inventory.setItem(16, help(Material.RED_DYE, "50% Failure", "The entire inserted stack is destroyed.", "There is no consolation output."));
        inventory.setItem(22, help(Material.BARRIER, "No Strategy Changes Odds", "Every roll remains exactly 50/50.", "Only risk currency you can afford to lose."));
    }

    private void renderMythicHelp(Inventory inventory) {
        inventory.setItem(4, helpTitle(Material.END_CRYSTAL, "Mythic Forge", "Turn exact legendary pairs into unique mythics."));
        inventory.setItem(10, help(Material.NETHER_STAR, "Requirements", "Insert two compatible source legendaries", "and 1 Ascendant Core."));
        inventory.setItem(12, help(Material.KNOWLEDGE_BOOK, "Exact Pairings", "Use /mythics to inspect every valid fusion.", "Random legendary pairs will not work."));
        inventory.setItem(14, help(Material.AMETHYST_SHARD, "Ascendant Core", "The center catalyst is consumed with both sources.", "Its recipe is under Mythic Works in /reliquary."));
        inventory.setItem(16, help(Material.BARRIER, "Permanent Cost", "Both source legendaries are consumed and retired", "from future legendary altar rolls forever."));
        inventory.setItem(22, help(Material.BEACON, "Server Limits", "Mythics obey the same unique-item limits.", "The forge refuses an output already at its limit."));
        inventory.setItem(24, help(Material.CHEST, "Before Clicking", "Clear inventory space and confirm both sources.", "There is no dismantle path back to the originals."));
    }

    private void renderUtilityHelp(Inventory inventory) {
        inventory.setItem(4, helpTitle(Material.CHEST, "Utility Stations", "Recovery and storage tools for gear progression."));
        inventory.setItem(10, help(Material.CHEST, "Salvaging Depot", "Place ordinary gear inside to recover about 66%", "of its materials. Join two depots for 54 slots."));
        inventory.setItem(12, help(Material.CLOCK, "Salvage Timing", "There is a 10-second cancel window, then", "6 seconds of locked processing."));
        inventory.setItem(14, help(Material.BARRIER, "Salvage Protection", "Relics, legendaries, backpacks, stations,", "and class items are rejected."));
        inventory.setItem(20, help(Material.LECTERN, "XP Lectern", "Stores exact XP safely and preserves it when moved.", "Deposit or withdraw 1, 5, 10, or all levels."));
        inventory.setItem(22, help(Material.EXPERIENCE_BOTTLE, "Bottle Stored XP", "10 stored XP + 1 plain glass bottle", "creates 1 Experience Bottle; batches of 1 or 8."));
        inventory.setItem(24, help(Material.HOPPER, "Private Base Safety", "Utility-station holograms have short range and", "do not render through solid walls or floors."));
    }

    private void renderUpgradeOrder(Inventory inventory) {
        inventory.setItem(4, helpTitle(Material.COMPASS, "Recommended Upgrade Order", "Avoid locking yourself out of later improvements."));
        inventory.setItem(10, help(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "1. Build the Base Item", "Finish crafting and netherite smithing first.", "Make sure it is the item you intend to keep."));
        inventory.setItem(12, help(Material.ENCHANTING_TABLE, "2. Add Enchants", "Apply normal and custom enchants before corruption.", "Runic Loom requires at least 4 existing enchants."));
        inventory.setItem(14, help(Material.ANVIL, "3. Reforge", "Roll the prefix you want while the item is editable.", "A later reforge replaces the previous one."));
        inventory.setItem(16, help(Material.ENDER_EYE, "4. Apply Direct Orbs", "Use Veilshift for a bonus stat and Warden's Lure", "on armor intended for tanking bosses."));
        inventory.setItem(20, help(Material.LOOM, "5. Runic Upgrades", "Spend Runebloom Orbs on the enchants that matter.", "Do this while the item remains unlocked."));
        inventory.setItem(22, help(Material.AMETHYST_SHARD, "6A. Awaken Final", "Repair fully, then accept the low success chance.", "An awakened item can never be corrupted."));
        inventory.setItem(24, help(Material.RESPAWN_ANCHOR, "6B. Corrupt Final", "Choose this instead of awakening; never after it.", "Corruption permanently seals every survivor."));
    }

    private ItemStack helpTitle(Material material, String title, String summary) {
        return item(material, "<gradient:#f59e0b:#a78bfa><bold>" + title + "</bold></gradient>", List.of("<gray>" + summary + "</gray>"), null);
    }

    private ItemStack help(Material material, String title, String... lines) {
        return item(material, "<yellow><bold>" + title + "</bold></yellow>", java.util.Arrays.stream(lines).map(line -> "<gray>" + line + "</gray>").toList(), null);
    }

    private String gearPageTitle(String action) {
        return switch (action) {
            case ACTION_GEAR_REFORGE -> "Reforging";
            case ACTION_GEAR_AWAKENING -> "Awakening";
            case ACTION_GEAR_CORRUPTION -> "Corruption";
            case ACTION_GEAR_ORBS -> "Orbs and Imprints";
            case ACTION_GEAR_RUNIC -> "Runic Loom";
            case ACTION_GEAR_FATE -> "Fate Crucible";
            case ACTION_GEAR_MYTHIC -> "Mythic Forge";
            case ACTION_GEAR_UTILITIES -> "Utility Stations";
            case ACTION_GEAR_ORDER -> "Upgrade Order";
            default -> "Orin's Workshop";
        };
    }

    private String soulImprintName(Player player) {
        return plugin.getSeasonRelicManager() == null
            ? "<obfuscated>Soul Imprint</obfuscated>"
            : plugin.getSeasonRelicManager().soulImprintDisplayName(player);
    }

    private void handleMenuAction(Player player, String action) {
        if (!player.isOnline()) {
            return;
        }
        switch (action) {
            case ACTION_WELCOME -> {
                player.closeInventory();
                player.sendMessage(MessageUtil.info("Mira: Welcome to <white>Season 5: Season of the Veil</white>. Use <white>/menu</white> first, then check Reliquary, classes, teams, and bosses."));
                player.sendMessage(MessageUtil.info("Spawn is protected. Public buttons only work when staff marks them for public use."));
            }
            case ACTION_CHANGELOG -> {
                player.closeInventory();
                player.performCommand("changelog");
            }
            case ACTION_WIKI -> {
                player.closeInventory();
                player.performCommand("wiki");
            }
            case ACTION_SEASON -> {
                player.closeInventory();
                player.sendMessage(MessageUtil.info("Mira: Season 5 is the <white>Season of the Veil</white>. Essence, bosses, reforges, corruption, and relic recipes drive progression."));
                player.sendMessage(MessageUtil.warn("Corruption can destroy items. Read Veyr's menu before using it."));
            }
            case ACTION_GEAR_HOME -> openGearExpertMenu(player);
            case ACTION_GEAR_REFORGE, ACTION_GEAR_AWAKENING, ACTION_GEAR_CORRUPTION,
                 ACTION_GEAR_ORBS, ACTION_GEAR_RUNIC, ACTION_GEAR_FATE, ACTION_GEAR_MYTHIC,
                 ACTION_GEAR_UTILITIES, ACTION_GEAR_ORDER -> openGearDetail(player, action);
            case ACTION_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, MenuItemUtil.INACTIVE_SLOT_LORE, null);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack item(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)).decoration(TextDecoration.ITALIC, false));
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream()
            .map(line -> MM.deserialize(line).decoration(TextDecoration.ITALIC, false))
            .toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);
        if (action != null) {
            meta.getPersistentDataContainer().set(keyMenuAction, PersistentDataType.STRING, action);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String actionOf(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(keyMenuAction, PersistentDataType.STRING);
    }

    private Entity spawnCitizensNpc(GuideNpcType type, Location location) {
        if (npcBridge == null) {
            return null;
        }
        try {
            return npcBridge.spawn(type, location);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Citizens " + type.displayName + " spawn failed; using fallback: " + ex.getMessage());
            return null;
        }
    }

    private void tryEnableCitizensBridge() {
        if (npcBridge != null || !Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            return;
        }
        try {
            Class<?> bridgeClass = Class.forName("me.rique.smpcore.npc.CitizensGuideBridge");
            npcBridge = (GuideNpcBridge) bridgeClass
                .getConstructor(SMPCore.class, GuideNpcManager.class, NamespacedKey.class)
                .newInstance(plugin, this, keyNpcType);
            plugin.getLogger().info("Citizens-backed guide NPCs enabled.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            npcBridge = null;
            plugin.getLogger().warning("Citizens is installed but SMPCore could not hook guide NPCs: " + ex.getMessage());
        }
    }

    private void scheduleRefreshes() {
        long[] delays = {1L, 40L, 120L};
        for (long delay : delays) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int refreshed = refreshNpcs();
                if (refreshed > 0) {
                    plugin.getLogger().info("Refreshed " + refreshed + " guide NPC(s).");
                }
            }, delay);
        }
    }

    private void configureFallbackNpc(GuideNpcType type, ArmorStand armorStand) {
        armorStand.customName(MM.deserialize(type.nameplate).decoration(TextDecoration.ITALIC, false));
        armorStand.setCustomNameVisible(true);
        armorStand.setSmall(type == GuideNpcType.SPAWN_GUIDE);
        armorStand.setArms(true);
        armorStand.setBasePlate(false);
        armorStand.setMarker(false);
        armorStand.setVisible(true);
        armorStand.setInvulnerable(true);
        armorStand.setSilent(true);
        armorStand.setCollidable(false);
        armorStand.setGravity(false);
        armorStand.setCanPickupItems(false);
        armorStand.setPersistent(true);
        armorStand.setRemoveWhenFarAway(false);
        armorStand.addScoreboardTag("smpcore_npc");
        armorStand.addScoreboardTag(type.scoreboardTag);
        armorStand.setDisabledSlots(EquipmentSlot.values());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            for (ArmorStand.LockType lockType : ArmorStand.LockType.values()) {
                armorStand.addEquipmentLock(slot, lockType);
            }
        }
        EntityEquipment equipment = armorStand.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(type.icon));
            equipment.setItemInMainHand(new ItemStack(type.handItem));
        }
        armorStand.getPersistentDataContainer().set(keyNpcType, PersistentDataType.STRING, type.id);
    }

    private List<Entity> findFallbackNpcs(GuideNpcType type) {
        List<Entity> found = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (typeOfEntity(entity) == type && entity instanceof ArmorStand) {
                    found.add(entity);
                }
            }
        }
        return found;
    }

    private GuideNpcType typeOfEntity(Entity entity) {
        if (entity == null) {
            return null;
        }
        String id = entity.getPersistentDataContainer().get(keyNpcType, PersistentDataType.STRING);
        GuideNpcType type = GuideNpcType.byId(id);
        if (type != null) {
            return type;
        }
        for (GuideNpcType candidate : GuideNpcType.values()) {
            if (entity.getScoreboardTags().contains(candidate.scoreboardTag)) {
                return candidate;
            }
        }
        return null;
    }

    private void openMenuFromNpcNextTick(Player player, GuideNpcType type, Entity interactionTarget) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    openMenuFromNpc(player, type, interactionTarget);
                }
            }
        }.runTask(plugin);
    }

    private Location centeredSpawnLocation(Location origin) {
        if (origin == null || origin.getWorld() == null) {
            throw new IllegalArgumentException("NPC spawn location must have a world.");
        }
        Location spawn = origin.clone();
        spawn.setX(origin.getBlockX() + 0.5);
        spawn.setZ(origin.getBlockZ() + 0.5);
        spawn.setPitch(0.0f);
        spawn.setY(safeFloorY(spawn));
        return spawn;
    }

    private double safeFloorY(Location origin) {
        World world = origin.getWorld();
        int x = origin.getBlockX();
        int z = origin.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 1, Math.max(world.getMinHeight(), origin.getBlockY()));
        for (int y = startY; y >= world.getMinHeight(); y--) {
            Block floor = world.getBlockAt(x, y, z);
            if (!floor.getType().isSolid() || floor.isPassable()) {
                continue;
            }
            Block feet = floor.getRelative(BlockFace.UP);
            Block head = feet.getRelative(BlockFace.UP);
            if (feet.isPassable() && head.isPassable()) {
                return y + 1.0;
            }
            return origin.getY();
        }
        return origin.getY();
    }

    private void spawnEffects(GuideNpcType type, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Sound sound = switch (type) {
            case CORRUPTION_WARDEN -> Sound.ENTITY_WARDEN_HEARTBEAT;
            case MAYOR -> Sound.BLOCK_BEACON_POWER_SELECT;
            case SPAWN_GUIDE -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case GEAR_EXPERT -> Sound.BLOCK_SMITHING_TABLE_USE;
            case DUNGEON_KEEPER -> Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
            case BREWMASTER -> Sound.BLOCK_BREWING_STAND_BREW;
            case CARDSHARP -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case DEALER -> Sound.ENTITY_VILLAGER_TRADE;
            case ROULETTE_CROUPIER -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case DUELMASTER -> Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB;
            case GOBLIN_HUNTER -> Sound.ENTITY_PILLAGER_AMBIENT;
            case MINER -> Sound.BLOCK_ANVIL_USE;
            case FARMER -> Sound.ITEM_HOE_TILL;
            case WITCH -> Sound.ENTITY_WITCH_CELEBRATE;
            case OVERSEER -> Sound.BLOCK_BEACON_POWER_SELECT;
            case BEASTWARDEN -> Sound.ENTITY_HORSE_SADDLE;
            case BOSSBROKER -> Sound.ENTITY_PILLAGER_CELEBRATE;
            case BLACK_MARKETEER -> Sound.ENTITY_ENDERMAN_AMBIENT;
            case FISHER -> Sound.ENTITY_VILLAGER_WORK_FISHERMAN;
            case FETCH_HOUND -> Sound.ENTITY_WOLF_AMBIENT;
            case TOWN_CAT -> Sound.ENTITY_CAT_AMBIENT;
            case TOWN_FOX -> Sound.ENTITY_FOX_AMBIENT;
            case TOWN_PARROT -> Sound.ENTITY_PARROT_AMBIENT;
            case HIDDEN_ILLUSIONER -> Sound.ENTITY_ILLUSIONER_MIRROR_MOVE;
            case TOWN_BAKER -> Sound.ENTITY_VILLAGER_WORK_BUTCHER;
            case TOWN_MASON -> Sound.ENTITY_VILLAGER_WORK_MASON;
            case TOWN_COURIER -> Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER;
            case TOWN_DOCKHAND -> Sound.ENTITY_VILLAGER_WORK_FISHERMAN;
            case TOWN_SEAMSTRESS -> Sound.ENTITY_VILLAGER_WORK_SHEPHERD;
            case TAVERN_HOST -> Sound.ENTITY_VILLAGER_TRADE;
            case TAVERN_REGULAR -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case TAVERN_TIPSY -> Sound.ENTITY_VILLAGER_CELEBRATE;
        };
        Particle particle = switch (type) {
            case CORRUPTION_WARDEN -> Particle.SCULK_SOUL;
            case MAYOR -> Particle.TOTEM_OF_UNDYING;
            case SPAWN_GUIDE -> Particle.END_ROD;
            case GEAR_EXPERT -> Particle.WAX_ON;
            case DUNGEON_KEEPER -> Particle.REVERSE_PORTAL;
            case BREWMASTER -> Particle.WITCH;
            case CARDSHARP -> Particle.ENCHANT;
            case DEALER -> Particle.CRIT;
            case ROULETTE_CROUPIER -> Particle.ENCHANT;
            case DUELMASTER -> Particle.FLAME;
            case GOBLIN_HUNTER -> Particle.HAPPY_VILLAGER;
            case MINER -> Particle.WAX_ON;
            case FARMER -> Particle.HAPPY_VILLAGER;
            case WITCH -> Particle.WITCH;
            case OVERSEER -> Particle.END_ROD;
            case BEASTWARDEN -> Particle.HEART;
            case BOSSBROKER -> Particle.HAPPY_VILLAGER;
            case BLACK_MARKETEER -> Particle.PORTAL;
            case FISHER -> Particle.SPLASH;
            case FETCH_HOUND -> Particle.HEART;
            case TOWN_CAT -> Particle.HAPPY_VILLAGER;
            case TOWN_FOX -> Particle.CHERRY_LEAVES;
            case TOWN_PARROT -> Particle.NOTE;
            case HIDDEN_ILLUSIONER -> Particle.WITCH;
            case TOWN_BAKER, TOWN_MASON, TOWN_COURIER, TOWN_DOCKHAND, TOWN_SEAMSTRESS, TAVERN_HOST -> Particle.HAPPY_VILLAGER;
            case TAVERN_REGULAR, TAVERN_TIPSY -> Particle.NOTE;
        };
        world.playSound(location, sound, 0.85f, type == GuideNpcType.MAYOR ? 1.05f : 0.9f);
        world.spawnParticle(particle, location.clone().add(0.0, 1.0, 0.0), 28, 0.35, 0.45, 0.35, 0.03);
    }

    private String locationSummary(Location location) {
        return location.getWorld().getName()
            + " "
            + location.getBlockX()
            + ", "
            + location.getBlockY()
            + ", "
            + location.getBlockZ();
    }

    public enum GuideNpcType {
        SPAWN_GUIDE(
            "spawn_guide",
            "Mira the Guide",
            "<gradient:#22d3ee:#a78bfa><bold>Mira the Guide</bold></gradient> <dark_gray>·</dark_gray> <yellow>Click for Help</yellow>",
            "smpcore_spawn_guide_npc",
            EntityType.PLAYER,
            Material.WRITABLE_BOOK,
            Material.COMPASS
        ),
        CORRUPTION_WARDEN(
            "corruption_warden",
            "Veyr",
            "<gradient:#ef4444:#7c3aed><bold>Veyr</bold></gradient>",
            "smpcore_corruption_warden_npc",
            EntityType.PLAYER,
            Material.SCULK_SHRIEKER,
            Material.RED_DYE
        ),
        MAYOR(
            "mayor",
            "Mayor Bah",
            "<gradient:#facc15:#38bdf8><bold>Mayor Bah</bold></gradient>",
            "smpcore_mayor_npc",
            EntityType.PLAYER,
            Material.EMERALD,
            Material.WRITABLE_BOOK
        ),
        GEAR_EXPERT(
            "gear_expert",
            "Orin the Artificer",
            "<gradient:#f59e0b:#a78bfa><bold>Orin the Artificer</bold></gradient>\n<yellow>Gear Help</yellow>",
            "smpcore_gear_expert_npc",
            EntityType.PLAYER,
            Material.SMITHING_TABLE,
            Material.AMETHYST_SHARD
        ),
        DUNGEON_KEEPER(
            "dungeon_keeper",
            "Malakar the Gatekeeper",
            "<gradient:#7f1d1d:#a78bfa><bold>Malakar</bold></gradient> <dark_gray>·</dark_gray> <yellow>Boss Dungeon</yellow>",
            "smpcore_dungeon_keeper_npc",
            EntityType.PLAYER,
            Material.RESPAWN_ANCHOR,
            Material.NETHER_STAR
        ),
        BREWMASTER(
            "brewmaster",
            "Bram the Brewmaster",
            "<gradient:#f59e0b:#facc15><bold>Bram the Brewmaster</bold></gradient> <dark_gray>·</dark_gray> <yellow>Drinks & Quest</yellow>",
            "smpcore_brewmaster_npc",
            EntityType.PLAYER,
            Material.BREWING_STAND,
            Material.HONEY_BOTTLE
        ),
        CARDSHARP(
            "cardsharp",
            "Rook the Retired Adventurer",
            "<gradient:#dc2626:#facc15><bold>Rook</bold></gradient> <dark_gray>·</dark_gray> <yellow>Tavern Trial</yellow>",
            "smpcore_cardsharp_npc",
            EntityType.PLAYER,
            Material.SUNFLOWER,
            Material.PAPER
        ),
        DEALER(
            "dealer",
            "Silas the Dealer",
            "<gradient:#facc15:#dc2626><bold>Silas the Dealer</bold></gradient> <dark_gray>·</dark_gray> <yellow>Blackjack</yellow>",
            "smpcore_dealer_npc",
            EntityType.PLAYER,
            Material.BLACK_CONCRETE,
            Material.PAPER
        ),
        ROULETTE_CROUPIER(
            "roulette_croupier",
            "Renn the Croupier",
            "<gradient:#dc2626:#facc15><bold>Renn the Croupier</bold></gradient>\n<yellow>European Roulette</yellow>",
            "smpcore_roulette_croupier_npc",
            EntityType.PLAYER,
            Material.RECOVERY_COMPASS,
            Material.GOLD_NUGGET
        ),
        DUELMASTER(
            "duelmaster",
            "Cassian the Fightmaster",
            "<gradient:#ef4444:#f59e0b><bold>Cassian the Fightmaster</bold></gradient>\n<yellow>Duels & Spectating</yellow>",
            "smpcore_duelmaster_npc",
            EntityType.PLAYER,
            Material.NETHERITE_SWORD,
            Material.IRON_SWORD
        ),
        GOBLIN_HUNTER(
            "goblin_hunter",
            "Grikk the Goblin Hunter",
            "<gradient:#65a30d:#facc15><bold>Grikk</bold></gradient> <dark_gray>·</dark_gray> <yellow>Goblin Hunt</yellow>",
            "smpcore_goblin_hunter_npc",
            EntityType.PLAYER,
            Material.PLAYER_HEAD,
            Material.IRON_SWORD
        ),
        MINER(
            "miner",
            "Torren the Miner",
            "<gradient:#f59e0b:#fde68a><bold>Torren the Miner</bold></gradient> <dark_gray>·</dark_gray> <yellow>Mining Trials</yellow>",
            "smpcore_miner_npc",
            EntityType.PLAYER,
            Material.IRON_PICKAXE,
            Material.LANTERN
        ),
        FARMER(
            "farmer",
            "Rowan the Farmer",
            "<gradient:#84cc16:#facc15><bold>Rowan the Farmer</bold></gradient> <dark_gray>-</dark_gray> <yellow>Fields & Feasts</yellow>",
            "smpcore_farmer_npc",
            EntityType.PLAYER,
            Material.GOLDEN_HOE,
            Material.WHEAT
        ),
        WITCH(
            "witch",
            "Vespera the Hedge-Witch",
            "<gradient:#a855f7:#22d3ee><bold>Vespera</bold></gradient> <dark_gray>-</dark_gray> <yellow>Moonlit Lessons</yellow>",
            "smpcore_witch_npc",
            EntityType.WITCH,
            Material.CAULDRON,
            Material.POTION
        ),
        OVERSEER(
            "overseer",
            "Veil Overseer",
            "<gradient:#7c3aed:#22d3ee><bold>Veil Overseer</bold></gradient> <dark_gray>·</dark_gray> <yellow>Season Directives</yellow>",
            "smpcore_overseer_npc",
            EntityType.PLAYER,
            Material.ENDER_EYE,
            Material.WRITABLE_BOOK
        ),
        BEASTWARDEN(
            "beastwarden",
            "Kael the Beastwarden",
            "<gradient:#65a30d:#f59e0b><bold>Kael the Beastwarden</bold></gradient>\n<yellow>Familiars & Steeds</yellow>",
            "smpcore_beastwarden_npc",
            EntityType.PLAYER,
            Material.DIAMOND_HORSE_ARMOR,
            Material.LEAD
        ),
        BOSSBROKER(
            "bossbroker",
            "Mogrik the Bossbroker",
            "<gradient:#65a30d:#facc15><bold>Mogrik the Bossbroker</bold></gradient>\n<yellow>Boss Bounties</yellow>",
            "smpcore_bossbroker_npc",
            EntityType.PLAYER,
            Material.GOLD_INGOT,
            Material.GOLDEN_AXE
        ),
        BLACK_MARKETEER(
            "black_marketeer",
            "Sable the Curio Broker",
            "<gradient:#581c87:#f59e0b><bold>Sable the Curio Broker</bold></gradient>\n<dark_gray>Black Market · Boss Trophies</dark_gray>",
            "smpcore_black_marketeer_npc",
            EntityType.PLAYER,
            Material.DECORATED_POT,
            Material.ENDER_CHEST
        ),
        FISHER(
            "fisher",
            "Corin the Fisher",
            "<gradient:#38bdf8:#fde68a><bold>Corin the Fisher</bold></gradient>\n<yellow>Coastal Errands</yellow>",
            "smpcore_fisher_npc",
            EntityType.PLAYER,
            Material.OAK_BOAT,
            Material.FISHING_ROD
        ),
        FETCH_HOUND(
            "fetch_hound",
            "Biscuit",
            "<gradient:#f59e0b:#fde68a><bold>Biscuit</bold></gradient>\n<yellow>Fetch</yellow>",
            "smpcore_fetch_hound_npc",
            EntityType.WOLF,
            Material.BONE,
            Material.STICK
        ),
        TOWN_CAT(
            "town_cat",
            "Miso the Mouser",
            "<gradient:#fbbf24:#f8fafc><bold>Miso</bold></gradient>\n<gray>Town Mouser</gray>",
            "smpcore_town_cat_npc",
            EntityType.CAT,
            Material.COD,
            Material.COD
        ),
        TOWN_FOX(
            "town_fox",
            "Pip the Fox",
            "<gradient:#fb923c:#fef3c7><bold>Pip</bold></gradient>\n<gray>Definitely Innocent</gray>",
            "smpcore_town_fox_npc",
            EntityType.FOX,
            Material.SWEET_BERRIES,
            Material.SWEET_BERRIES
        ),
        TOWN_PARROT(
            "town_parrot",
            "Buttons the Parrot",
            "<gradient:#22d3ee:#facc15><bold>Buttons</bold></gradient>\n<gray>Very Talkative</gray>",
            "smpcore_town_parrot_npc",
            EntityType.PARROT,
            Material.FEATHER,
            Material.WHEAT_SEEDS
        ),
        HIDDEN_ILLUSIONER(
            "hidden_illusioner",
            "The Crooked One",
            "<gradient:#7c3aed:#ec4899><bold>The Crooked One</bold></gradient>\n<dark_purple>Hidden Stranger</dark_purple>",
            "smpcore_hidden_illusioner_npc",
            EntityType.ILLUSIONER,
            Material.ENDER_EYE,
            Material.FERMENTED_SPIDER_EYE
        ),
        TOWN_BAKER(
            "town_baker",
            "Elowen the Baker",
            "<gradient:#f59e0b:#fde68a><bold>Elowen</bold></gradient>\n<gray>Veilward Baker</gray>",
            "smpcore_town_baker_npc",
            EntityType.PLAYER,
            Material.BREAD,
            Material.BREAD
        ),
        TOWN_MASON(
            "town_mason",
            "Jory the Mason",
            "<gradient:#94a3b8:#f8fafc><bold>Jory</bold></gradient>\n<gray>Veilward Mason</gray>",
            "smpcore_town_mason_npc",
            EntityType.PLAYER,
            Material.STONECUTTER,
            Material.BRICK
        ),
        TOWN_COURIER(
            "town_courier",
            "Nell the Courier",
            "<gradient:#38bdf8:#f8fafc><bold>Nell</bold></gradient>\n<gray>Veilward Courier</gray>",
            "smpcore_town_courier_npc",
            EntityType.PLAYER,
            Material.PAPER,
            Material.COMPASS
        ),
        TOWN_DOCKHAND(
            "town_dockhand",
            "Oren the Dockhand",
            "<gradient:#0ea5e9:#a3e635><bold>Oren</bold></gradient>\n<gray>Veilward Dockhand</gray>",
            "smpcore_town_dockhand_npc",
            EntityType.PLAYER,
            Material.OAK_BOAT,
            Material.LEAD
        ),
        TOWN_SEAMSTRESS(
            "town_seamstress",
            "Maeve the Seamstress",
            "<gradient:#c084fc:#f9a8d4><bold>Maeve</bold></gradient>\n<gray>Veilward Seamstress</gray>",
            "smpcore_town_seamstress_npc",
            EntityType.PLAYER,
            Material.LOOM,
            Material.SHEARS
        ),
        TAVERN_HOST(
            "tavern_host",
            "Tamsin the Host",
            "<gradient:#84cc16:#facc15><bold>Tamsin the Host</bold></gradient>\n<yellow>Tavern Welcome</yellow>",
            "smpcore_tavern_host_npc",
            EntityType.PLAYER,
            Material.OAK_DOOR,
            Material.PAPER
        ),
        TAVERN_REGULAR(
            "tavern_regular",
            "Nessa the Regular",
            "<gradient:#f472b6:#f59e0b><bold>Nessa the Regular</bold></gradient>\n<gray>Off-Duty Patron</gray>",
            "smpcore_tavern_regular_npc",
            EntityType.PLAYER,
            Material.ARROW,
            Material.GLASS_BOTTLE
        ),
        TAVERN_TIPSY(
            "tavern_tipsy",
            "Garrick the Tipsy",
            "<gradient:#fb923c:#fde68a><bold>Garrick the Tipsy</bold></gradient>\n<gray>One Mug Too Many</gray>",
            "smpcore_tavern_tipsy_npc",
            EntityType.PLAYER,
            Material.HONEY_BOTTLE,
            Material.HONEY_BOTTLE
        );

        private static final Map<String, GuideNpcType> BY_ID = Map.ofEntries(
            Map.entry(SPAWN_GUIDE.id, SPAWN_GUIDE),
            Map.entry(CORRUPTION_WARDEN.id, CORRUPTION_WARDEN),
            Map.entry(MAYOR.id, MAYOR),
            Map.entry(GEAR_EXPERT.id, GEAR_EXPERT),
            Map.entry(DUNGEON_KEEPER.id, DUNGEON_KEEPER),
            Map.entry(BREWMASTER.id, BREWMASTER),
            Map.entry(CARDSHARP.id, CARDSHARP),
            Map.entry(DEALER.id, DEALER),
            Map.entry(ROULETTE_CROUPIER.id, ROULETTE_CROUPIER),
            Map.entry(DUELMASTER.id, DUELMASTER),
            Map.entry(GOBLIN_HUNTER.id, GOBLIN_HUNTER),
            Map.entry(MINER.id, MINER),
            Map.entry(FARMER.id, FARMER),
            Map.entry(WITCH.id, WITCH),
            Map.entry(OVERSEER.id, OVERSEER),
            Map.entry(BEASTWARDEN.id, BEASTWARDEN),
            Map.entry(BOSSBROKER.id, BOSSBROKER),
            Map.entry(BLACK_MARKETEER.id, BLACK_MARKETEER),
            Map.entry(FISHER.id, FISHER),
            Map.entry(FETCH_HOUND.id, FETCH_HOUND),
            Map.entry(TOWN_CAT.id, TOWN_CAT),
            Map.entry(TOWN_FOX.id, TOWN_FOX),
            Map.entry(TOWN_PARROT.id, TOWN_PARROT),
            Map.entry(HIDDEN_ILLUSIONER.id, HIDDEN_ILLUSIONER),
            Map.entry(TOWN_BAKER.id, TOWN_BAKER),
            Map.entry(TOWN_MASON.id, TOWN_MASON),
            Map.entry(TOWN_COURIER.id, TOWN_COURIER),
            Map.entry(TOWN_DOCKHAND.id, TOWN_DOCKHAND),
            Map.entry(TOWN_SEAMSTRESS.id, TOWN_SEAMSTRESS),
            Map.entry(TAVERN_HOST.id, TAVERN_HOST),
            Map.entry(TAVERN_REGULAR.id, TAVERN_REGULAR),
            Map.entry(TAVERN_TIPSY.id, TAVERN_TIPSY)
        );

        private final String id;
        private final String displayName;
        private final String nameplate;
        private final String scoreboardTag;
        private final EntityType entityType;
        private final Material icon;
        private final Material handItem;

        GuideNpcType(String id, String displayName, String nameplate, String scoreboardTag, EntityType entityType, Material icon, Material handItem) {
            this.id = id;
            this.displayName = displayName;
            this.nameplate = nameplate;
            this.scoreboardTag = scoreboardTag;
            this.entityType = entityType;
            this.icon = icon;
            this.handItem = handItem;
        }

        public static GuideNpcType byId(String id) {
            return id == null ? null : BY_ID.get(id);
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public String nameplate() {
            return nameplate;
        }

        public String scoreboardTag() {
            return scoreboardTag;
        }

        public EntityType entityType() {
            return entityType;
        }

        public double hologramOffset() {
            return switch (this) {
                case CORRUPTION_WARDEN -> 0.46D;
                case FETCH_HOUND, TOWN_CAT, TOWN_FOX, TOWN_PARROT -> 0.18D;
                case SPAWN_GUIDE, MAYOR, GEAR_EXPERT, DUNGEON_KEEPER, BREWMASTER, CARDSHARP, DEALER, ROULETTE_CROUPIER, DUELMASTER, GOBLIN_HUNTER, MINER, FARMER, WITCH, OVERSEER, BEASTWARDEN, BOSSBROKER, BLACK_MARKETEER, FISHER, HIDDEN_ILLUSIONER, TOWN_BAKER, TOWN_MASON, TOWN_COURIER, TOWN_DOCKHAND, TOWN_SEAMSTRESS, TAVERN_HOST, TAVERN_REGULAR, TAVERN_TIPSY -> 0.42D;
            };
        }

        public boolean isSpawnLife() {
            return switch (this) {
                case FETCH_HOUND, TOWN_CAT, TOWN_FOX, TOWN_PARROT, HIDDEN_ILLUSIONER,
                     TOWN_BAKER, TOWN_MASON, TOWN_COURIER, TOWN_DOCKHAND, TOWN_SEAMSTRESS,
                     TAVERN_HOST, TAVERN_REGULAR, TAVERN_TIPSY -> true;
                default -> false;
            };
        }

        public Material icon() {
            return icon;
        }

        public Material handItem() {
            return handItem;
        }
    }

    private record GuideMenuHolder(UUID playerId, GuideNpcType type) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
