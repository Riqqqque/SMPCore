package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.awakening.AwakeningTableListener;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.legendary.LegendaryListener;
import me.rique.smpcore.legendary.MythicForgeListener;
import me.rique.smpcore.power.SuperpowerManager;
import me.rique.smpcore.season.SeasonRelicManager;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RareDropVisualListener implements Listener {

    private static final double HOLOGRAM_HEIGHT = 0.70;
    private static final String TEAM_PREFIX = "smpd_";
    private static final NamedTextColor[] GLOW_COLORS = {
        NamedTextColor.GOLD,
        NamedTextColor.AQUA,
        NamedTextColor.LIGHT_PURPLE,
        NamedTextColor.GREEN,
        NamedTextColor.RED,
        NamedTextColor.YELLOW,
        NamedTextColor.BLUE,
        NamedTextColor.DARK_AQUA,
        NamedTextColor.DARK_PURPLE,
        NamedTextColor.DARK_GREEN,
        NamedTextColor.DARK_RED,
        NamedTextColor.WHITE
    };

    private final SMPCore plugin;
    private final NamespacedKey keyRareHologram;
    private final NamespacedKey keyRareHologramItem;
    private final Map<UUID, UUID> hologramsByItem = new ConcurrentHashMap<>();
    private BukkitTask hologramTask;
    private BukkitTask reconcileTask;

    public RareDropVisualListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyRareHologram = new NamespacedKey(plugin, "rare_drop_hologram");
        this.keyRareHologramItem = new NamespacedKey(plugin, "rare_drop_hologram_item");
    }

    public void start() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    removeStaleChunkHolograms(chunk);
                }
            }
            syncLoadedRareDisplays();
        });
        hologramTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRareDisplays, 5L, 5L);
        reconcileTask = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcileLoadedRareItems, 40L, 100L);
    }

    public void shutdown() {
        if (hologramTask != null) {
            hologramTask.cancel();
            hologramTask = null;
        }
        if (reconcileTask != null) {
            reconcileTask.cancel();
            reconcileTask = null;
        }
        for (UUID itemId : new ArrayList<>(hologramsByItem.keySet())) {
            clearRareItemGlow(itemId);
            removeRareHologram(itemId);
        }
        hologramsByItem.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRareItemSpawn(ItemSpawnEvent event) {
        RareDropProfile profile = profileFor(event.getEntity().getItemStack());
        if (profile == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.getEntity().isValid() || event.getEntity().isDead()) {
                return;
            }
            RareDropProfile refreshed = profileFor(event.getEntity().getItemStack());
            if (refreshed != null) {
                applyRareItemVisuals(event.getEntity(), refreshed, true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRareItemDrop(PlayerDropItemEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Item item = event.getItemDrop();
            if (item == null || !item.isValid() || item.isDead()) {
                return;
            }
            RareDropProfile profile = profileFor(item.getItemStack());
            if (profile != null) {
                applyRareItemVisuals(item, profile, true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRareItemPickup(EntityPickupItemEvent event) {
        syncRareVisualAfterPickup(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRareItemHopperPickup(InventoryPickupItemEvent event) {
        syncRareVisualAfterPickup(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRareItemMerge(ItemMergeEvent event) {
        syncRareVisualAfterPickup(event.getEntity());
        syncRareVisualAfterPickup(event.getTarget());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRareItemDespawn(ItemDespawnEvent event) {
        clearRareItemGlow(event.getEntity().getUniqueId());
        removeRareHologram(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        removeStaleChunkHolograms(event.getChunk());
        syncChunkRareDisplays(event.getChunk());
    }

    private void tickRareDisplays() {
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(hologramsByItem.entrySet())) {
            UUID itemId = entry.getKey();
            Entity itemEntity = Bukkit.getEntity(itemId);
            if (!(itemEntity instanceof Item item) || !item.isValid() || item.isDead()) {
                clearRareItemGlow(itemId);
                removeRareHologram(itemId);
                continue;
            }

            RareDropProfile profile = profileFor(item.getItemStack());
            if (profile == null) {
                clearRareItemGlow(itemId);
                removeRareHologram(itemId);
                continue;
            }

            applyGlow(item, profile.glowColor());

            Entity displayEntity = Bukkit.getEntity(entry.getValue());
            if (!(displayEntity instanceof TextDisplay display) || !display.isValid()) {
                hologramsByItem.remove(itemId);
                ensureRareHologram(item, profile);
                continue;
            }

            display.teleport(hologramLocation(item));
            Component updatedText = buildRareHologramText(item.getItemStack(), profile);
            if (!Objects.equals(display.text(), updatedText)) {
                display.text(updatedText);
            }
        }
    }

    private void reconcileLoadedRareItems() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                syncChunkRareDisplays(chunk);
            }
        }
    }

    private void syncLoadedRareDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                syncChunkRareDisplays(chunk);
            }
        }
    }

    private void syncChunkRareDisplays(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            RareDropProfile profile = profileFor(item.getItemStack());
            if (profile != null) {
                applyRareItemVisuals(item, profile, false);
            } else {
                clearRareItemGlow(item.getUniqueId());
                removeRareHologram(item.getUniqueId());
            }
        }
    }

    private void removeStaleChunkHolograms(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!isRareHologram(entity)) {
                continue;
            }
            UUID itemId = hologramItemId(entity);
            UUID trackedDisplayId = itemId == null ? null : hologramsByItem.get(itemId);
            Entity itemEntity = itemId == null ? null : Bukkit.getEntity(itemId);
            if (!(entity instanceof TextDisplay)
                || !(itemEntity instanceof Item item)
                || !item.isValid()
                || profileFor(item.getItemStack()) == null
                || !entity.getUniqueId().equals(trackedDisplayId)) {
                entity.remove();
                if (itemId != null) {
                    hologramsByItem.remove(itemId);
                }
            }
        }
    }

    private void applyRareItemVisuals(Item itemEntity, RareDropProfile profile, boolean burst) {
        if (itemEntity == null || !itemEntity.isValid() || profile == null) {
            return;
        }

        applyGlow(itemEntity, profile.glowColor());
        ensureRareHologram(itemEntity, profile);
        if (burst) {
            Location center = itemEntity.getLocation().clone().add(0.0, 0.18, 0.0);
            itemEntity.getWorld().spawnParticle(Particle.ENCHANT, center, 16, 0.22, 0.12, 0.22, 0.02);
            itemEntity.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0.0, 0.2, 0.0), 8, 0.16, 0.18, 0.16, 0.01);
        }
    }

    private void ensureRareHologram(Item itemEntity, RareDropProfile profile) {
        if (itemEntity == null || !itemEntity.isValid() || profile == null) {
            return;
        }

        UUID itemId = itemEntity.getUniqueId();
        UUID existingId = hologramsByItem.get(itemId);
        Entity existing = existingId == null ? null : Bukkit.getEntity(existingId);
        if (existing instanceof TextDisplay display && display.isValid()) {
            display.teleport(hologramLocation(itemEntity));
            display.text(buildRareHologramText(itemEntity.getItemStack(), profile));
            VisualRangeUtil.applyHologramRange(display);
            return;
        }

        removeRareHologram(itemId);
        itemEntity.getWorld().spawn(hologramLocation(itemEntity), TextDisplay.class, display -> {
            tagRareHologram(display, itemId);
            display.text(buildRareHologramText(itemEntity.getItemStack(), profile));
            display.setGravity(false);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(false);
            VisualRangeUtil.applyHologramRange(display);
            display.setLineWidth(220);
            display.setTextOpacity((byte) 255);
            display.setBackgroundColor(Color.fromARGB(96, 8, 8, 16));
            hologramsByItem.put(itemId, display.getUniqueId());
        });
    }

    private void syncRareVisualAfterPickup(Item itemEntity) {
        if (itemEntity == null) {
            return;
        }
        UUID itemId = itemEntity.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Entity entity = Bukkit.getEntity(itemId);
            if (entity instanceof Item item && item.isValid() && !item.isDead()) {
                RareDropProfile profile = profileFor(item.getItemStack());
                if (profile != null) {
                    applyRareItemVisuals(item, profile, false);
                    return;
                }
            }
            clearRareItemGlow(itemId);
            removeRareHologram(itemId);
        });
    }

    private Location hologramLocation(Item itemEntity) {
        return itemEntity.getLocation().clone().add(0.0, HOLOGRAM_HEIGHT, 0.0);
    }

    private Component buildRareHologramText(ItemStack item, RareDropProfile profile) {
        Component text = Component.empty()
            .append(profile.displayName())
            .append(Component.newline())
            .append(Component.text(profile.subtitle(), profile.glowColor()));

        if (item != null && item.getAmount() > 1) {
            text = text.append(Component.newline())
                .append(Component.text("x" + item.getAmount(), NamedTextColor.GRAY));
        }
        return text;
    }

    private void tagRareHologram(Entity entity, UUID itemId) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(keyRareHologram, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyRareHologramItem, PersistentDataType.STRING, itemId.toString());
    }

    private void removeRareHologram(UUID itemId) {
        if (itemId == null) {
            return;
        }
        UUID displayId = hologramsByItem.remove(itemId);
        if (displayId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(displayId);
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private boolean isRareHologram(Entity entity) {
        return entity != null && isMarked(entity.getPersistentDataContainer(), keyRareHologram);
    }

    private UUID hologramItemId(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(keyRareHologramItem, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void applyGlow(Item itemEntity, NamedTextColor color) {
        itemEntity.setGlowing(true);
        Scoreboard scoreboard = mainScoreboard();
        if (scoreboard == null) {
            return;
        }
        Team currentTeam = scoreboard.getEntityTeam(itemEntity);
        Team targetTeam = ensureGlowTeam(scoreboard, color);
        if (currentTeam != null && !currentTeam.getName().equals(targetTeam.getName())) {
            currentTeam.removeEntity(itemEntity);
        }
        if (!targetTeam.hasEntity(itemEntity)) {
            targetTeam.addEntity(itemEntity);
        }
    }

    private void clearRareItemGlow(UUID itemId) {
        Entity entity = itemId == null ? null : Bukkit.getEntity(itemId);
        if (!(entity instanceof Item item) || !item.isValid()) {
            return;
        }
        item.setGlowing(false);
        Scoreboard scoreboard = mainScoreboard();
        if (scoreboard == null) {
            return;
        }
        Team currentTeam = scoreboard.getEntityTeam(item);
        if (currentTeam != null && currentTeam.getName().startsWith(TEAM_PREFIX)) {
            currentTeam.removeEntity(item);
        }
    }

    private Team ensureGlowTeam(Scoreboard scoreboard, NamedTextColor color) {
        String teamName = TEAM_PREFIX + glowIndex(color);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        team.color(color);
        return team;
    }

    private int glowIndex(NamedTextColor color) {
        for (int i = 0; i < GLOW_COLORS.length; i++) {
            if (GLOW_COLORS[i].equals(color)) {
                return i;
            }
        }
        return 0;
    }

    private Scoreboard mainScoreboard() {
        return Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private NamedTextColor colorForRarity(String rarityLabel) {
        return switch (CustomLoreUtil.Rarity.fromLabel(rarityLabel)) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.AQUA;
            case EPIC -> NamedTextColor.LIGHT_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
            case MYTHIC -> NamedTextColor.RED;
        };
    }

    private RareDropProfile profileFor(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        Component displayName = displayName(item);

        LegendaryListener legendary = plugin.getLegendaryListener();
        if (legendary != null) {
            String legendaryId = legendary.legendaryId(item);
            if (legendaryId != null) {
                String subtitle = legendary.rarityLabelForLegendary(legendaryId);
                if (subtitle == null || subtitle.isBlank()) {
                    subtitle = legendary.isMythicForgeOutputLegendary(legendaryId) ? "MYTHIC" : "LEGENDARY";
                }
                return new RareDropProfile(displayName, subtitle, colorForRarity(subtitle));
            }
            if (legendary.isEnderBoneItem(item)) {
                return new RareDropProfile(displayName, "EPIC", colorForRarity("EPIC"));
            }
            if (legendary.isOrbOfTheMysticsItem(item)) {
                return new RareDropProfile(displayName, "LEGENDARY", colorForRarity("LEGENDARY"));
            }
        }

        BossManager bossManager = plugin.getBossManager();
        if (bossManager != null && bossManager.isDominionCore(item)) {
            return new RareDropProfile(displayName, "LEGENDARY", colorForRarity("LEGENDARY"));
        }

        MythicForgeListener mythicForge = plugin.getMythicForgeListener();
        if (mythicForge != null) {
            if (mythicForge.isAscendantCoreItem(item)) {
                return new RareDropProfile(displayName, "LEGENDARY", colorForRarity("LEGENDARY"));
            }
            if (mythicForge.isMythicForgeItemStack(item)) {
                return new RareDropProfile(displayName, "MYTHIC", colorForRarity("MYTHIC"));
            }
        }

        AwakeningTableListener awakening = plugin.getAwakeningTableListener();
        if (awakening != null) {
            if (awakening.isAwakeningTableCustomItem(item)) {
                return new RareDropProfile(displayName, "MYTHIC", colorForRarity("MYTHIC"));
            }
            if (awakening.isAwakened(item)) {
                return new RareDropProfile(displayName, "MYTHIC", colorForRarity("MYTHIC"));
            }
        }

        if (plugin.getCustomToolListener() != null) {
            String toolId = plugin.getCustomToolListener().customToolId(item);
            if (toolId != null) {
                return new RareDropProfile(displayName, "RARE", colorForRarity("RARE"));
            }
        }

        if (plugin.getBackpackListener() != null && plugin.getBackpackListener().isBackpack(item)) {
            return new RareDropProfile(displayName, "UNCOMMON", colorForRarity("UNCOMMON"));
        }

        if (plugin.getSustenanceTalismanListener() != null && plugin.getSustenanceTalismanListener().isTalisman(item)) {
            return new RareDropProfile(displayName, "EPIC", colorForRarity("EPIC"));
        }

        SuperpowerManager powers = plugin.getSuperpowerManager();
        if (powers != null) {
            if (powers.isAncientScroll(item)) {
                return new RareDropProfile(displayName, "EPIC", colorForRarity("EPIC"));
            }
            if (powers.isWardenHeart(item)) {
                return new RareDropProfile(displayName, "EPIC", colorForRarity("EPIC"));
            }
            if (powers.isMotherNatureStick(item)) {
                return new RareDropProfile(displayName, "EPIC", colorForRarity("EPIC"));
            }
            if (powers.isTheWorldClock(item)) {
                return new RareDropProfile(displayName, "MYTHIC", colorForRarity("MYTHIC"));
            }
            if (powers.isDruidGrimoire(item)) {
                return new RareDropProfile(displayName, "EPIC", colorForRarity("EPIC"));
            }
        }

        SeasonRelicManager season = plugin.getSeasonRelicManager();
        if (season != null) {
            String relicId = season.relicId(item);
            if (relicId != null) {
                String rarity = season.rarityLabelFor(relicId);
                if (rarity == null || rarity.isBlank()) {
                    rarity = "RARE";
                }
                return new RareDropProfile(displayName, rarity, colorForRarity(rarity));
            }
        }

        return null;
    }

    private Component displayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return meta.displayName();
        }
        if (meta != null && meta.hasItemName() && meta.itemName() != null) {
            return meta.itemName();
        }
        return Component.text(prettyMaterial(item.getType()), NamedTextColor.WHITE);
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

    private boolean isMarked(PersistentDataContainer pdc, NamespacedKey key) {
        Byte marker = pdc.get(key, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private record RareDropProfile(Component displayName, String subtitle, NamedTextColor glowColor) {
    }
}
