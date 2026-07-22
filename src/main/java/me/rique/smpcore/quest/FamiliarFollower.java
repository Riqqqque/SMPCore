package me.rique.smpcore.quest;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.compat.BedrockFamiliarVisibilityManager;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class FamiliarFollower {
    private static final long INTERACTION_COOLDOWN_MS = 2_000L;
    private static final double NAME_LABEL_Y_OFFSET = 0.72D;

    private final SMPCore plugin;
    private final org.bukkit.NamespacedKey ownerKey;
    private final NamespacedKey nameHologramKey;
    private final String displayName;
    private final NamedTextColor nameColor;
    private final Particle particle;
    private final Supplier<ItemStack> itemSupplier;
    private final Predicate<Player> activeCheck;
    private final Map<UUID, UUID> displays = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> bedrockBodies = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> nameLabels = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> hitboxes = new ConcurrentHashMap<>();
    private final Map<UUID, FamiliarMotion.State> motionStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextInteractionAt = new ConcurrentHashMap<>();
    private BukkitTask task;

    FamiliarFollower(
        SMPCore plugin,
        String ownerKey,
        String displayName,
        NamedTextColor nameColor,
        Particle particle,
        Supplier<ItemStack> itemSupplier,
        Predicate<Player> activeCheck
    ) {
        this.plugin = plugin;
        this.ownerKey = new org.bukkit.NamespacedKey(plugin, ownerKey);
        this.nameHologramKey = new NamespacedKey(plugin, "familiar_name_hologram");
        this.displayName = displayName;
        this.nameColor = nameColor;
        this.particle = particle;
        this.itemSupplier = itemSupplier;
        this.activeCheck = activeCheck;
    }

    void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, FamiliarMotion.UPDATE_TICKS);
    }

    void shutdown() {
        if (task != null) task.cancel();
        task = null;
        displays.values().forEach(this::removeEntity);
        bedrockBodies.values().forEach(this::removeEntity);
        nameLabels.values().forEach(this::removeEntity);
        hitboxes.values().forEach(this::removeEntity);
        displays.clear();
        bedrockBodies.clear();
        nameLabels.clear();
        hitboxes.clear();
        motionStates.clear();
        nextInteractionAt.clear();
    }

    void spawn(Player player) {
        if (player == null || !activeCheck.test(player) || currentDisplay(player) != null) return;
        remove(player.getUniqueId());
        FamiliarMotion.State motion = motionStates.computeIfAbsent(
            player.getUniqueId(),
            ignored -> new FamiliarMotion.State(player.getLocation().getYaw())
        );
        motion.reset(player.getLocation().getYaw());
        Location target = FamiliarMotion.target(player, motion, null);
        ItemDisplay display = target.getWorld().spawn(target, ItemDisplay.class, entity -> {
            entity.setItemStack(itemSupplier.get());
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setViewRange(VisualRangeUtil.blocksToDisplayViewRange(32));
            FamiliarMotion.configureDisplay(entity, FamiliarMotion.TELEPORT_DURATION_TICKS);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.customName(Component.empty());
            entity.setCustomNameVisible(false);
            tagOwner(entity, player);
        });
        displays.put(player.getUniqueId(), display.getUniqueId());
        BedrockFamiliarVisibilityManager visibility = plugin.getBedrockFamiliarVisibilityManager();
        if (visibility != null) {
            visibility.registerJavaVisual(display);
            spawnBedrockBody(player, target);
        }
        spawnHitbox(player, target);
        spawnNameLabel(player, target);
    }

    void remove(Player player) {
        if (player != null) remove(player.getUniqueId());
    }

    void remove(UUID playerId) {
        removeEntity(displays.remove(playerId));
        removeEntity(bedrockBodies.remove(playerId));
        removeEntity(nameLabels.remove(playerId));
        removeEntity(hitboxes.remove(playerId));
        motionStates.remove(playerId);
        nextInteractionAt.remove(playerId);
    }

    boolean isOwnedHitbox(Entity entity, Player player) {
        if (!(entity instanceof Interaction) || player == null) return false;
        String owner = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return player.getUniqueId().toString().equals(owner);
    }

    boolean beginInteraction(Player player) {
        if (player == null) return false;
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        Long nextAllowed = nextInteractionAt.get(playerId);
        if (!interactionAllowed(nextAllowed, now)) return false;
        nextInteractionAt.put(playerId, now + INTERACTION_COOLDOWN_MS);
        return true;
    }

    static boolean interactionAllowed(Long nextAllowed, long now) {
        return nextAllowed == null || nextAllowed <= now;
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!activeCheck.test(player)) {
                remove(player.getUniqueId());
                continue;
            }
            Entity display = currentDisplay(player);
            if (display == null) {
                spawn(player);
                display = currentDisplay(player);
            }
            if (display == null) continue;
            FamiliarMotion.State motion = motionStates.computeIfAbsent(
                player.getUniqueId(),
                ignored -> new FamiliarMotion.State(player.getLocation().getYaw())
            );
            Location target = FamiliarMotion.target(player, motion, display.getLocation());
            FamiliarMotion.move(display, target);
            moveBedrockBody(player, target);
            moveNameLabel(player, target);
            if (isBossFightNearby(player)) removeEntity(hitboxes.remove(player.getUniqueId()));
            else moveHitbox(player, target);
            motion.advance();
            if (motion.ticks() % FamiliarMotion.PARTICLE_INTERVAL_TICKS == 0) {
                player.spawnParticle(particle, target, 1, 0.06, 0.06, 0.06, 0.0D);
            }
        }
    }

    private Entity currentDisplay(Player player) {
        UUID id = displays.get(player.getUniqueId());
        Entity entity = id == null ? null : Bukkit.getEntity(id);
        if (entity == null || !entity.isValid()) displays.remove(player.getUniqueId());
        return entity != null && entity.isValid() ? entity : null;
    }

    private void spawnBedrockBody(Player player, Location target) {
        BedrockFamiliarVisibilityManager visibility = plugin.getBedrockFamiliarVisibilityManager();
        if (visibility == null) return;
        ArmorStand body = FamiliarBedrockBody.spawn(
            target,
            itemSupplier.get(),
            entity -> tagOwner(entity, player)
        );
        bedrockBodies.put(player.getUniqueId(), body.getUniqueId());
        visibility.registerBedrockVisual(body);
    }

    private void moveBedrockBody(Player player, Location target) {
        BedrockFamiliarVisibilityManager visibility = plugin.getBedrockFamiliarVisibilityManager();
        if (visibility == null) {
            removeEntity(bedrockBodies.remove(player.getUniqueId()));
            return;
        }
        UUID id = bedrockBodies.get(player.getUniqueId());
        Entity body = id == null ? null : Bukkit.getEntity(id);
        if (!(body instanceof ArmorStand) || !body.isValid()) {
            removeEntity(id);
            spawnBedrockBody(player, target);
            return;
        }
        FamiliarBedrockBody.move(body, target);
    }

    private void spawnNameLabel(Player player, Location visualTarget) {
        Location location = nameLabelLocation(visualTarget);
        TextDisplay label = location.getWorld().spawn(location, TextDisplay.class, display -> {
            display.text(Component.text(player.getName() + "'s " + displayName, nameColor));
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
            display.setTextOpacity((byte) 0xFF);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.getPersistentDataContainer().set(nameHologramKey, PersistentDataType.BYTE, (byte) 1);
            tagOwner(display, player);
        });
        nameLabels.put(player.getUniqueId(), label.getUniqueId());
    }

    private void moveNameLabel(Player player, Location visualTarget) {
        UUID id = nameLabels.get(player.getUniqueId());
        Entity entity = id == null ? null : Bukkit.getEntity(id);
        Location target = nameLabelLocation(visualTarget);
        if (!(entity instanceof TextDisplay label) || !label.isValid() || !label.getWorld().equals(target.getWorld())) {
            removeEntity(id);
            spawnNameLabel(player, visualTarget);
            return;
        }
        label.text(Component.text(player.getName() + "'s " + displayName, nameColor));
        FamiliarMotion.move(label, target);
    }

    private static Location nameLabelLocation(Location visualTarget) {
        return visualTarget.clone().add(0.0D, NAME_LABEL_Y_OFFSET, 0.0D);
    }

    private void spawnHitbox(Player player, Location target) {
        Interaction hitbox = target.getWorld().spawn(target.clone().add(0.0D, -0.18D, 0.0D), Interaction.class, entity -> {
            entity.setInteractionWidth(0.42F);
            entity.setInteractionHeight(0.50F);
            entity.setResponsive(true);
            entity.setGravity(false);
            entity.setSilent(true);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            tagOwner(entity, player);
        });
        hitboxes.put(player.getUniqueId(), hitbox.getUniqueId());
    }

    private void moveHitbox(Player player, Location target) {
        UUID id = hitboxes.get(player.getUniqueId());
        Entity entity = id == null ? null : Bukkit.getEntity(id);
        if (!(entity instanceof Interaction) || !entity.isValid() || !entity.getWorld().equals(target.getWorld())) {
            removeEntity(id);
            spawnHitbox(player, target);
            return;
        }
        entity.teleport(target.clone().add(0.0D, -0.18D, 0.0D));
    }

    private boolean isBossFightNearby(Player player) {
        return plugin.getBossManager() != null && plugin.getBossManager().isActiveBossFight(player);
    }

    private void tagOwner(Entity entity, Player player) {
        entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
    }

    private void removeEntity(UUID id) {
        BedrockFamiliarVisibilityManager visibility = plugin.getBedrockFamiliarVisibilityManager();
        if (visibility != null) visibility.unregisterVisual(id);
        Entity entity = id == null ? null : Bukkit.getEntity(id);
        if (entity != null) entity.remove();
    }
}
