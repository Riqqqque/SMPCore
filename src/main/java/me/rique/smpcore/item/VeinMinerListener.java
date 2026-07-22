package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.AtomicYamlFile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VeinMinerListener implements Listener {

    private static final int[][] CARDINAL_NEIGHBORS = {
        { 1, 0, 0 }, { -1, 0, 0 },
        { 0, 1, 0 }, { 0, -1, 0 },
        { 0, 0, 1 }, { 0, 0, -1 }
    };
    private static final int[][] DIAGONAL_NEIGHBORS = buildDiagonalNeighbors();
    private static final Map<String, List<Material>> DEFAULT_ORE_FAMILIES = createDefaultOreFamilies();
    private static final Map<String, List<Material>> DEFAULT_TREE_FAMILIES = createDefaultTreeFamilies();

    private final SMPCore plugin;
    private final File dataFile;
    private final Map<UUID, Boolean> playerEnabled = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Material>> playerCustomBlocks = new ConcurrentHashMap<>();
    private final Set<BlockKey> internalBreaks = ConcurrentHashMap.newKeySet();
    private final Map<Material, String> oreFamilyByMaterial = new EnumMap<>(Material.class);
    private final Map<Material, String> treeFamilyByMaterial = new EnumMap<>(Material.class);

    private boolean enabled;
    private boolean defaultPlayerEnabled;
    private boolean requireSneak;
    private boolean searchDiagonals;
    private int maxBlocksPerChain;
    private boolean oresEnabled;
    private boolean oresRequirePickaxe;
    private boolean treesEnabled;
    private boolean treesRequireAxe;

    public VeinMinerListener(SMPCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "veinminer-data.yml");
        loadPlayerSettings();
        reloadConfig();
    }

    public void reloadConfig() {
        oreFamilyByMaterial.clear();
        treeFamilyByMaterial.clear();

        enabled = plugin.getConfigManager().veinMinerEnabled;
        defaultPlayerEnabled = plugin.getConfigManager().veinMinerDefaultEnabled;
        requireSneak = plugin.getConfigManager().veinMinerRequireSneak;
        searchDiagonals = plugin.getConfigManager().veinMinerSearchDiagonals;
        maxBlocksPerChain = plugin.getConfigManager().veinMinerMaxBlocksPerChain;
        oresEnabled = plugin.getConfigManager().veinMinerOresEnabled;
        oresRequirePickaxe = plugin.getConfigManager().veinMinerOresRequirePickaxe;
        treesEnabled = plugin.getConfigManager().veinMinerTreesEnabled;
        treesRequireAxe = plugin.getConfigManager().veinMinerTreesRequireAxe;

        loadFamilySection(plugin.getConfig().getConfigurationSection("vein-miner.ores.families"), oreFamilyByMaterial, "ore");
        loadFamilySection(plugin.getConfig().getConfigurationSection("vein-miner.trees.families"), treeFamilyByMaterial, "tree");

        if (oresEnabled && oreFamilyByMaterial.isEmpty()) {
            loadDefaultFamilies(DEFAULT_ORE_FAMILIES, oreFamilyByMaterial);
            plugin.getLogger().warning("Vein miner ore families are missing or empty in config.yml. Using built-in defaults.");
        }
        if (treesEnabled && treeFamilyByMaterial.isEmpty()) {
            loadDefaultFamilies(DEFAULT_TREE_FAMILIES, treeFamilyByMaterial);
            plugin.getLogger().warning("Vein miner tree families are missing or empty in config.yml. Using built-in defaults.");
        }
    }

    public void shutdown() {
        savePlayerSettings();
    }

    public void suppressNextBreak(Location location) {
        if (location == null || location.getWorld() == null) return;
        internalBreaks.add(blockKey(location));
    }

    public boolean isEnabledFor(Player player) {
        if (!enabled) return false;
        if (!player.hasPermission("smpcore.veinminer.use")) return false;
        return playerEnabled.getOrDefault(player.getUniqueId(), defaultPlayerEnabled);
    }

    public boolean toggle(Player player) {
        boolean next = !isEnabledFor(player);
        setEnabled(player, next);
        return next;
    }

    public void setEnabled(Player player, boolean value) {
        if (value == defaultPlayerEnabled) {
            playerEnabled.remove(player.getUniqueId());
        } else {
            playerEnabled.put(player.getUniqueId(), value);
        }
        savePlayerSettings();
    }

    public boolean addCustomBlock(Player player, Material material) {
        if (!isValidCustomBlock(material)) {
            return false;
        }
        Set<Material> blocks = playerCustomBlocks.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        boolean changed = blocks.add(material);
        if (changed) {
            savePlayerSettings();
        }
        return changed;
    }

    public boolean removeCustomBlock(Player player, Material material) {
        if (material == null) {
            return false;
        }
        Set<Material> blocks = playerCustomBlocks.get(player.getUniqueId());
        if (blocks == null) {
            return false;
        }
        boolean changed = blocks.remove(material);
        if (blocks.isEmpty()) {
            playerCustomBlocks.remove(player.getUniqueId());
        }
        if (changed) {
            savePlayerSettings();
        }
        return changed;
    }

    public List<Material> customBlocks(Player player) {
        Set<Material> blocks = playerCustomBlocks.get(player.getUniqueId());
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream().sorted().toList();
    }

    public boolean isValidCustomBlock(Material material) {
        return material != null && material.isBlock() && !material.isAir();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        BlockKey key = blockKey(event.getBlock().getLocation());
        if (internalBreaks.remove(key)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        if (!shouldVeinMine(player, event.getBlock())) {
            return;
        }

        VeinTarget target = classifyTarget(player, event.getBlock().getType());
        if (target == null) {
            return;
        }

        List<Block> chain = collectChain(event.getBlock(), target);
        if (chain.size() <= 1) {
            return;
        }

        List<Block> extraBlocks = new ArrayList<>(chain);
        extraBlocks.remove(0);

        Bukkit.getScheduler().runTask(plugin, () -> breakChain(player, extraBlocks, target));
    }

    private boolean shouldVeinMine(Player player, Block origin) {
        if (!enabled) return false;
        if (!isEnabledFor(player)) return false;
        if (player.getGameMode() == GameMode.CREATIVE) return false;
        if (requireSneak && !player.isSneaking()) return false;
        if (maxBlocksPerChain <= 1) return false;
        if (origin.getType().isAir()) return false;

        ItemStack tool = player.getInventory().getItemInMainHand();
        VeinTarget target = classifyTarget(player, origin.getType());
        if (target == null) return false;

        return canUseToolForTarget(tool, target, origin.getType());
    }

    private VeinTarget classifyTarget(Player player, Material material) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (isVeinwakePick(tool) && isVeinwakeExcludedMaterial(material)) {
            return null;
        }

        String oreFamily = oresEnabled ? oreFamilyByMaterial.get(material) : null;
        if (oreFamily != null) {
            return new VeinTarget(VeinTargetType.ORE, oreFamily);
        }

        String treeFamily = treesEnabled ? treeFamilyByMaterial.get(material) : null;
        if (treeFamily != null) {
            return new VeinTarget(VeinTargetType.TREE, treeFamily);
        }
        if (isVeinwakeTerrain(material) && isVeinwakePick(tool)) {
            return new VeinTarget(VeinTargetType.VEINWAKE_TERRAIN, material.name());
        }
        Set<Material> customBlocks = playerCustomBlocks.get(player.getUniqueId());
        if (customBlocks != null && customBlocks.contains(material)) {
            return new VeinTarget(VeinTargetType.CUSTOM, material.name());
        }
        return null;
    }

    private List<Block> collectChain(Block origin, VeinTarget target) {
        int[][] offsets = searchDiagonals ? DIAGONAL_NEIGHBORS : CARDINAL_NEIGHBORS;
        ArrayDeque<Block> queue = new ArrayDeque<>();
        Set<BlockKey> visited = new HashSet<>();
        List<Block> collected = new ArrayList<>();

        queue.add(origin);
        visited.add(blockKey(origin.getLocation()));

        while (!queue.isEmpty() && collected.size() < maxBlocksPerChain) {
            Block block = queue.removeFirst();
            if (!matchesTarget(block.getType(), target)) {
                continue;
            }

            collected.add(block);
            Location location = block.getLocation();
            for (int[] offset : offsets) {
                Block next = location.getWorld().getBlockAt(
                    location.getBlockX() + offset[0],
                    location.getBlockY() + offset[1],
                    location.getBlockZ() + offset[2]
                );
                BlockKey nextKey = blockKey(next.getLocation());
                if (!visited.add(nextKey)) continue;
                if (!matchesTarget(next.getType(), target)) continue;
                queue.addLast(next);
            }
        }

        return collected;
    }

    private boolean matchesTarget(Material material, VeinTarget target) {
        return switch (target.type()) {
            case ORE -> target.family().equals(oreFamilyByMaterial.get(material));
            case TREE -> target.family().equals(treeFamilyByMaterial.get(material));
            case VEINWAKE_TERRAIN, CUSTOM -> target.family().equals(material.name());
        };
    }

    private void breakChain(Player player, Collection<Block> blocks, VeinTarget target) {
        if (!player.isOnline()) return;

        for (Block block : blocks) {
            if (!player.isOnline()) return;
            if (block.getType().isAir()) continue;
            if (!canUseToolForTarget(player.getInventory().getItemInMainHand(), target, block.getType())) return;

            BlockKey key = blockKey(block.getLocation());
            internalBreaks.add(key);
            boolean success = player.breakBlock(block);
            if (!success) {
                internalBreaks.remove(key);
            }
        }
    }

    private boolean canUseToolForTarget(ItemStack tool, VeinTarget target, Material material) {
        return switch (target.type()) {
            case ORE -> !oresRequirePickaxe || isPickaxe(tool);
            case TREE -> !treesRequireAxe || isAxe(tool);
            case VEINWAKE_TERRAIN -> isVeinwakePick(tool);
            case CUSTOM -> canMineCustomTarget(tool, material);
        };
    }

    private void loadFamilySection(ConfigurationSection section, Map<Material, String> output, String kind) {
        if (section == null) return;

        for (String family : section.getKeys(false)) {
            List<String> names = section.getStringList(family);
            if (names.isEmpty()) continue;

            for (String raw : names) {
                Material material = parseMaterial(raw);
                if (material == null) {
                    plugin.getLogger().warning("Ignoring unknown " + kind + " vein miner material: " + raw);
                    continue;
                }
                output.put(material, family.toLowerCase(Locale.ROOT));
            }
        }
    }

    private void loadDefaultFamilies(Map<String, List<Material>> defaults, Map<Material, String> output) {
        defaults.forEach((family, materials) -> {
            for (Material material : materials) {
                output.put(material, family);
            }
        });
    }

    private Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isPickaxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (plugin.getMinerManager() != null && plugin.getMinerManager().isVeinwakePick(item)) return true;
        return Tag.ITEMS_PICKAXES.isTagged(item.getType()) || hasPickaxeMaterialName(item.getType());
    }

    static boolean hasPickaxeMaterialName(Material material) {
        return material != null && material.name().endsWith("_PICKAXE");
    }

    static boolean isVeinwakeTerrain(Material material) {
        return material == Material.STONE || material == Material.DEEPSLATE;
    }

    static boolean isVeinwakeExcludedMaterial(Material material) {
        return material == Material.NETHERRACK;
    }

    private boolean isVeinwakePick(ItemStack item) {
        return plugin.getMinerManager() != null && plugin.getMinerManager().isVeinwakePick(item);
    }

    private boolean isAxe(ItemStack item) {
        return item != null && Tag.ITEMS_AXES.isTagged(item.getType());
    }

    private boolean canMineCustomTarget(ItemStack tool, Material material) {
        if (material == null) {
            return false;
        }
        if (requiresPickaxe(material)) {
            return isPickaxe(tool);
        }
        String name = material.name();
        if (name.endsWith("_LOG")
            || name.endsWith("_WOOD")
            || name.endsWith("_STEM")
            || name.endsWith("_HYPHAE")) {
            return isAxe(tool);
        }
        return tool != null && tool.getType() != Material.AIR;
    }

    private boolean requiresPickaxe(Material material) {
        String name = material.name();
        return name.endsWith("_ORE")
            || name.equals("ANCIENT_DEBRIS")
            || name.equals("OBSIDIAN")
            || name.equals("CRYING_OBSIDIAN")
            || name.endsWith("_STONE")
            || name.contains("DEEPSLATE")
            || name.contains("BLACKSTONE")
            || name.contains("BASALT")
            || name.contains("TUFF")
            || name.contains("TERRACOTTA")
            || name.contains("CONCRETE")
            || name.contains("COPPER")
            || name.contains("IRON")
            || name.contains("GOLD")
            || name.contains("DIAMOND")
            || name.contains("EMERALD")
            || name.contains("NETHERITE")
            || name.contains("PRISMARINE");
    }

    private void loadPlayerSettings() {
        playerEnabled.clear();
        playerCustomBlocks.clear();
        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = data.getConfigurationSection("players");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    playerEnabled.put(uuid, section.getBoolean(key));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring invalid vein miner player setting key: " + key);
                }
            }
        }

        ConfigurationSection blocksSection = data.getConfigurationSection("custom-blocks");
        if (blocksSection == null) {
            return;
        }
        for (String key : blocksSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                Set<Material> blocks = ConcurrentHashMap.newKeySet();
                for (String raw : blocksSection.getStringList(key)) {
                    Material material = parseMaterial(raw);
                    if (isValidCustomBlock(material)) {
                        blocks.add(material);
                    }
                }
                if (!blocks.isEmpty()) {
                    playerCustomBlocks.put(uuid, blocks);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid vein miner custom block key: " + key);
            }
        }
    }

    private void savePlayerSettings() {
        YamlConfiguration data = new YamlConfiguration();
        playerEnabled.forEach((uuid, value) -> {
            data.set("players." + uuid, value);
        });
        playerCustomBlocks.forEach((uuid, blocks) -> {
            if (blocks == null || blocks.isEmpty()) {
                return;
            }
            data.set(
                "custom-blocks." + uuid,
                blocks.stream().sorted().map(Material::name).toList()
            );
        });
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Failed to create plugin data folder for vein miner settings.");
                return;
            }
            AtomicYamlFile.save(data, dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save vein miner settings: " + e.getMessage());
        }
    }

    private BlockKey blockKey(Location location) {
        return new BlockKey(
            location.getWorld().getUID(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }

    private static int[][] buildDiagonalNeighbors() {
        List<int[]> offsets = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    offsets.add(new int[] { x, y, z });
                }
            }
        }
        return offsets.toArray(int[][]::new);
    }

    private static Map<String, List<Material>> createDefaultOreFamilies() {
        Map<String, List<Material>> defaults = new LinkedHashMap<>();
        defaults.put("coal", List.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE));
        defaults.put("copper", List.of(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE));
        defaults.put("iron", List.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE));
        defaults.put("gold", List.of(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE));
        defaults.put("redstone", List.of(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE));
        defaults.put("lapis", List.of(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE));
        defaults.put("diamond", List.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE));
        defaults.put("emerald", List.of(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE));
        defaults.put("nether_gold", List.of(Material.NETHER_GOLD_ORE));
        defaults.put("quartz", List.of(Material.NETHER_QUARTZ_ORE));
        defaults.put("ancient_debris", List.of(Material.ANCIENT_DEBRIS));
        return defaults;
    }

    private static Map<String, List<Material>> createDefaultTreeFamilies() {
        Map<String, List<Material>> defaults = new LinkedHashMap<>();
        defaults.put("oak", List.of(Material.OAK_LOG, Material.OAK_WOOD));
        defaults.put("spruce", List.of(Material.SPRUCE_LOG, Material.SPRUCE_WOOD));
        defaults.put("birch", List.of(Material.BIRCH_LOG, Material.BIRCH_WOOD));
        defaults.put("jungle", List.of(Material.JUNGLE_LOG, Material.JUNGLE_WOOD));
        defaults.put("acacia", List.of(Material.ACACIA_LOG, Material.ACACIA_WOOD));
        defaults.put("dark_oak", List.of(Material.DARK_OAK_LOG, Material.DARK_OAK_WOOD));
        defaults.put("mangrove", List.of(Material.MANGROVE_LOG, Material.MANGROVE_WOOD));
        defaults.put("cherry", List.of(Material.CHERRY_LOG, Material.CHERRY_WOOD));
        defaults.put("pale_oak", List.of(Material.PALE_OAK_LOG, Material.PALE_OAK_WOOD));
        defaults.put("crimson", List.of(Material.CRIMSON_STEM, Material.CRIMSON_HYPHAE));
        defaults.put("warped", List.of(Material.WARPED_STEM, Material.WARPED_HYPHAE));
        return defaults;
    }

    private record VeinTarget(VeinTargetType type, String family) {}

    private enum VeinTargetType {
        ORE,
        TREE,
        VEINWAKE_TERRAIN,
        CUSTOM
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {}
}
