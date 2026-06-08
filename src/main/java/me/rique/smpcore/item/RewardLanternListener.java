package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MessageUtil;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RewardLanternListener implements Listener {

    public static final String ITEM_ID = "reward_soul_lantern";

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int EFFECT_DURATION_TICKS = 5 * 60 * 20;
    private static final double HOLOGRAM_HEIGHT = 0.75;

    private final SMPCore plugin;
    private final NamespacedKey keyRewardLantern;
    private final NamespacedKey keyRewardOwner;
    private final NamespacedKey keyRewardOwnerName;
    private final NamespacedKey keyRewardAccess;
    private final NamespacedKey keyRewardCooldownUntil;
    private final NamespacedKey keyRewardLanternHologram;
    private final NamespacedKey keyRewardLanternHologramItem;
    private final List<PotionEffectType> beneficialEffects;
    private final Map<UUID, UUID> hologramsByItem = new ConcurrentHashMap<>();
    private BukkitTask hologramTask;

    public RewardLanternListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keyRewardLantern = new NamespacedKey(plugin, "reward_lantern_item");
        this.keyRewardOwner = new NamespacedKey(plugin, "reward_lantern_owner");
        this.keyRewardOwnerName = new NamespacedKey(plugin, "reward_lantern_owner_name");
        this.keyRewardAccess = new NamespacedKey(plugin, "reward_lantern_access");
        this.keyRewardCooldownUntil = new NamespacedKey(plugin, "reward_lantern_cooldown_until");
        this.keyRewardLanternHologram = new NamespacedKey(plugin, "reward_lantern_hologram");
        this.keyRewardLanternHologramItem = new NamespacedKey(plugin, "reward_lantern_hologram_item");
        this.beneficialEffects = resolveBeneficialEffects();
    }

    public void start() {
        Bukkit.getScheduler().runTask(plugin, this::syncLoadedLanternHolograms);
        hologramTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickLanternHolograms, 10L, 10L);
    }

    public void shutdown() {
        if (hologramTask != null) {
            hologramTask.cancel();
            hologramTask = null;
        }
        for (UUID displayId : new ArrayList<>(hologramsByItem.values())) {
            Entity display = Bukkit.getEntity(displayId);
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        hologramsByItem.clear();
    }

    public ItemStack createRewardLantern(Player owner) {
        return createRewardLantern(owner == null ? null : owner.getUniqueId(), owner == null ? "Unknown" : owner.getName());
    }

    public ItemStack createRewardLantern(UUID ownerId, String ownerName) {
        ItemStack item = new ItemStack(Material.SOUL_LANTERN);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyRewardLantern, PersistentDataType.BYTE, (byte) 1);
        if (ownerId != null) {
            pdc.set(keyRewardOwner, PersistentDataType.STRING, ownerId.toString());
        }
        if (ownerName != null && !ownerName.isBlank()) {
            pdc.set(keyRewardOwnerName, PersistentDataType.STRING, ownerName);
        }
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.MYTHIC, "Reward Soul Lantern"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.SOUL_LANTERN,
            CustomLoreUtil.Rarity.MYTHIC.label(),
            "REWARD",
            List.of(
                "<gray>Right-click to gain every beneficial effect for <white>5 minutes</white>.</gray>",
                "<gray>Cooldown: <white>" + formatDurationSeconds(cooldownSeconds()) + "</white>.</gray>",
                "<gray>Bound to <white>" + safeOwnerName(ownerName) + "</white>.</gray>"
            ),
            List.of(
                CustomLoreUtil.section(
                    "Use",
                    "Blessing Burst",
                    "<gray>Applies all <white>positive potion effects</white> at <white>level I</white>.</gray>",
                    "<gray>Only the rewarded player can activate it.</gray>",
                    "<gray>Shared cooldown: <white>" + formatDurationSeconds(cooldownSeconds()) + "</white>.</gray>"
                )
            )
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isRewardLantern(ItemStack item) {
        if (item == null || item.getType() != Material.SOUL_LANTERN) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(keyRewardLantern, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public void grantRewardAccess(Player player) {
        if (player == null) {
            return;
        }
        player.getPersistentDataContainer().set(keyRewardAccess, PersistentDataType.BYTE, (byte) 1);
    }

    public void revokeRewardAccess(Player player) {
        if (player == null) {
            return;
        }
        player.getPersistentDataContainer().remove(keyRewardAccess);
    }

    public boolean hasRewardAccess(Player player) {
        if (player == null) {
            return false;
        }
        Byte marker = player.getPersistentDataContainer().get(keyRewardAccess, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRewardLanternUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!isRewardLantern(item)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        UUID ownerId = ownerId(item);
        if (ownerId == null) {
            player.sendMessage(MessageUtil.error("This reward lantern is missing its owner binding."));
            return;
        }
        if (!ownerId.equals(player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("This reward lantern does not belong to you."));
            return;
        }
        if (!hasRewardAccess(player)) {
            player.sendMessage(MessageUtil.warn("Your reward lantern access has been removed."));
            return;
        }
        long remainingCooldownMillis = remainingCooldownMillis(player);
        if (remainingCooldownMillis > 0L) {
            player.sendMessage(MessageUtil.warn(
                "Your Reward Soul Lantern is on cooldown for <white>" + formatDurationMillis(remainingCooldownMillis) + "</white>."
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.35f);
            return;
        }

        applyLanternEffects(player);
        startCooldown(player);
    }

    @EventHandler
    public void onRewardLanternSpawn(ItemSpawnEvent event) {
        Item itemEntity = event.getEntity();
        if (!isRewardLantern(itemEntity.getItemStack())) {
            return;
        }
        ensureLanternHologram(itemEntity);
    }

    @EventHandler
    public void onRewardLanternPickup(EntityPickupItemEvent event) {
        syncLanternHologramAfterPickup(event.getItem());
    }

    @EventHandler
    public void onRewardLanternHopperPickup(InventoryPickupItemEvent event) {
        syncLanternHologramAfterPickup(event.getItem());
    }

    @EventHandler
    public void onRewardLanternDespawn(ItemDespawnEvent event) {
        removeLanternHologram(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        removeStaleChunkHolograms(event.getChunk());
        syncChunkLanternHolograms(event.getChunk());
    }

    private void applyLanternEffects(Player player) {
        for (PotionEffectType effectType : beneficialEffects) {
            if (effectType == null) {
                continue;
            }
            PotionEffect current = player.getPotionEffect(effectType);
            if (current != null && current.getAmplifier() > 0 && current.getDuration() > 20) {
                continue;
            }
            if (current != null && current.getAmplifier() == 0 && current.getDuration() >= (EFFECT_DURATION_TICKS - 20)) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(effectType, EFFECT_DURATION_TICKS, 0, true, true, true));
        }

        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }

        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0, 1.0, 0.0), 30, 0.45, 0.75, 0.45, 0.04);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0, 1.0, 0.0), 48, 0.6, 0.8, 0.6, 0.25);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.15f);
        player.sendMessage(MessageUtil.success(
            "The Reward Soul Lantern surges through you for <white>5 minutes</white>. Cooldown: <white>"
                + formatDurationSeconds(cooldownSeconds())
                + "</white>."
        ));
    }

    private List<PotionEffectType> resolveBeneficialEffects() {
        List<PotionEffectType> effects = new ArrayList<>();
        for (PotionEffectType effectType : Registry.MOB_EFFECT) {
            if (effectType == null) {
                continue;
            }
            if (effectType.isInstant()) {
                continue;
            }
            if (effectType.getCategory() != PotionEffectTypeCategory.BENEFICIAL) {
                continue;
            }
            effects.add(effectType);
        }
        return effects;
    }

    private UUID ownerId(ItemStack item) {
        if (!isRewardLantern(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(keyRewardOwner, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String ownerName(ItemStack item) {
        if (!isRewardLantern(item)) {
            return "Unknown";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "Unknown";
        }
        String stored = meta.getPersistentDataContainer().get(keyRewardOwnerName, PersistentDataType.STRING);
        return safeOwnerName(stored);
    }

    private String safeOwnerName(String ownerName) {
        return ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName;
    }

    private void startCooldown(Player player) {
        long nextUseAt = System.currentTimeMillis() + (cooldownSeconds() * 1000L);
        player.getPersistentDataContainer().set(keyRewardCooldownUntil, PersistentDataType.LONG, nextUseAt);
    }

    private long remainingCooldownMillis(Player player) {
        if (player == null) {
            return 0L;
        }
        Long cooldownUntil = player.getPersistentDataContainer().get(keyRewardCooldownUntil, PersistentDataType.LONG);
        if (cooldownUntil == null) {
            return 0L;
        }
        long remaining = cooldownUntil - System.currentTimeMillis();
        if (remaining <= 0L) {
            player.getPersistentDataContainer().remove(keyRewardCooldownUntil);
            return 0L;
        }
        return remaining;
    }

    private int cooldownSeconds() {
        return plugin.getConfigManager() == null ? 1800 : plugin.getConfigManager().rewardLanternCooldownSeconds;
    }

    private String formatDurationMillis(long durationMillis) {
        long totalSeconds = Math.max(1L, Math.ceilDiv(durationMillis, 1000L));
        return formatDurationSeconds(totalSeconds);
    }

    private String formatDurationSeconds(long totalSeconds) {
        long safeSeconds = Math.max(1L, totalSeconds);
        long minutes = safeSeconds / 60L;
        long seconds = safeSeconds % 60L;
        if (minutes <= 0L) {
            return seconds + (seconds == 1L ? " second" : " seconds");
        }
        if (seconds == 0L) {
            return minutes + (minutes == 1L ? " minute" : " minutes");
        }
        return minutes + (minutes == 1L ? " minute " : " minutes ")
            + seconds + (seconds == 1L ? " second" : " seconds");
    }

    private void tickLanternHolograms() {
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(hologramsByItem.entrySet())) {
            UUID itemId = entry.getKey();
            Entity itemEntity = Bukkit.getEntity(itemId);
            if (!(itemEntity instanceof Item item) || !item.isValid() || item.isDead() || !isRewardLantern(item.getItemStack())) {
                removeLanternHologram(itemId);
                continue;
            }

            Entity displayEntity = Bukkit.getEntity(entry.getValue());
            if (!(displayEntity instanceof TextDisplay display) || !display.isValid()) {
                hologramsByItem.remove(itemId);
                ensureLanternHologram(item);
                continue;
            }

            display.teleport(hologramLocation(item));
            Component updatedText = buildLanternHologramText(item.getItemStack());
            if (!Objects.equals(display.text(), updatedText)) {
                display.text(updatedText);
            }
        }
    }

    private void syncLoadedLanternHolograms() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                syncChunkLanternHolograms(chunk);
            }
        }
    }

    private void syncChunkLanternHolograms(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Item item && isRewardLantern(item.getItemStack())) {
                ensureLanternHologram(item);
            }
        }
    }

    private void removeStaleChunkHolograms(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!isLanternHologram(entity)) {
                continue;
            }
            UUID itemId = hologramItemId(entity);
            Entity itemEntity = itemId == null ? null : Bukkit.getEntity(itemId);
            if (!(itemEntity instanceof Item item) || !item.isValid() || !isRewardLantern(item.getItemStack())) {
                entity.remove();
                if (itemId != null) {
                    hologramsByItem.remove(itemId);
                }
            }
        }
    }

    private void ensureLanternHologram(Item itemEntity) {
        if (itemEntity == null || !itemEntity.isValid() || !isRewardLantern(itemEntity.getItemStack())) {
            return;
        }

        UUID itemId = itemEntity.getUniqueId();
        UUID existingId = hologramsByItem.get(itemId);
        Entity existing = existingId == null ? null : Bukkit.getEntity(existingId);
        if (existing instanceof TextDisplay display && display.isValid()) {
            display.teleport(hologramLocation(itemEntity));
            display.text(buildLanternHologramText(itemEntity.getItemStack()));
            VisualRangeUtil.applyHologramRange(display);
            return;
        }

        removeLanternHologram(itemId);
        itemEntity.getWorld().spawn(hologramLocation(itemEntity), TextDisplay.class, display -> {
            tagLanternHologram(display, itemId);
            display.text(buildLanternHologramText(itemEntity.getItemStack()));
            display.setGravity(false);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(false);
            VisualRangeUtil.applyHologramRange(display);
            display.setLineWidth(200);
            display.setBackgroundColor(Color.fromARGB(96, 8, 16, 24));
            hologramsByItem.put(itemId, display.getUniqueId());
        });
    }

    private void syncLanternHologramAfterPickup(Item itemEntity) {
        if (itemEntity == null) {
            return;
        }
        UUID itemId = itemEntity.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Entity entity = Bukkit.getEntity(itemId);
            if (entity instanceof Item item && item.isValid() && !item.isDead() && isRewardLantern(item.getItemStack())) {
                ensureLanternHologram(item);
                return;
            }
            removeLanternHologram(itemId);
        });
    }

    private Location hologramLocation(Item itemEntity) {
        return itemEntity.getLocation().clone().add(0.0, HOLOGRAM_HEIGHT, 0.0);
    }

    private Component buildLanternHologramText(ItemStack item) {
        return displayName(item)
            .append(Component.newline())
            .append(MM.deserialize("<gray>Bound to <white>" + ownerName(item) + "</white></gray>"));
    }

    private Component displayName(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return meta.displayName();
        }
        return CustomLoreUtil.displayName(CustomLoreUtil.Rarity.MYTHIC, "Reward Soul Lantern");
    }

    private void tagLanternHologram(TextDisplay display, UUID itemId) {
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(keyRewardLanternHologram, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyRewardLanternHologramItem, PersistentDataType.STRING, itemId.toString());
    }

    private void removeLanternHologram(UUID itemId) {
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

    private boolean isLanternHologram(Entity entity) {
        return entity instanceof TextDisplay display
            && isMarked(display.getPersistentDataContainer(), keyRewardLanternHologram);
    }

    private UUID hologramItemId(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(keyRewardLanternHologramItem, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isMarked(PersistentDataContainer pdc, NamespacedKey key) {
        Byte marker = pdc.get(key, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }
}
