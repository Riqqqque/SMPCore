package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.ItemModelUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FirstDragonSigilListener implements Listener {

    public static final String ITEM_ID = "first_dragon_sigil";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long COOLDOWN_MILLIS = 180_000L;
    private static final long FALL_PROTECTION_MILLIS = 15_000L;
    private static final double SHOCKWAVE_DAMAGE = 12.0D;

    private final SMPCore plugin;
    private final NamespacedKey keySigil;
    private final NamespacedKey keyOwner;
    private final NamespacedKey keyOwnerName;
    private final NamespacedKey keyRevoked;
    private final NamespacedKey keyCooldownUntil;
    private final Map<UUID, Long> fallProtection = new ConcurrentHashMap<>();

    public FirstDragonSigilListener(SMPCore plugin) {
        this.plugin = plugin;
        this.keySigil = new NamespacedKey(plugin, "first_dragon_sigil");
        this.keyOwner = new NamespacedKey(plugin, "first_dragon_sigil_owner");
        this.keyOwnerName = new NamespacedKey(plugin, "first_dragon_sigil_owner_name");
        this.keyRevoked = new NamespacedKey(plugin, "first_dragon_sigil_revoked");
        this.keyCooldownUntil = new NamespacedKey(plugin, "first_dragon_sigil_cooldown_until");
    }

    public ItemStack createSigil(Player owner) {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String ownerName = owner == null ? "Unknown" : owner.getName();
        if (owner != null) {
            owner.getPersistentDataContainer().remove(keyRevoked);
        }
        meta.getPersistentDataContainer().set(keySigil, PersistentDataType.BYTE, (byte) 1);
        if (owner != null) {
            meta.getPersistentDataContainer().set(keyOwner, PersistentDataType.STRING, owner.getUniqueId().toString());
        }
        meta.getPersistentDataContainer().set(keyOwnerName, PersistentDataType.STRING, ownerName);
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.MYTHIC, "The First Dragon's Sigil"));
        ItemModelUtil.apply(meta, ITEM_ID);
        meta.setMaxStackSize(1);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.ECHO_SHARD,
            CustomLoreUtil.Rarity.MYTHIC.label(),
            "RELIC",
            List.of(
                "<gray>Forged for the first champion to claim the Dragon Egg.</gray>",
                "<gray>Bound to <white>" + ownerName + "</white>.</gray>"
            ),
            List.of(CustomLoreUtil.section(
                "Item Ability",
                "Firstflight",
                "<gray>Right-click to launch forward and scatter hostile mobs.</gray>",
                "<gray>Grants Slow Falling, Fire Resistance, and fall protection.</gray>",
                "<gray>Cannot affect players or bosses. Cooldown: <white>3 minutes</white>.</gray>"
            ))
        ));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSigil(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
            && meta.getPersistentDataContainer().getOrDefault(keySigil, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
            || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            || !isSigil(event.getItem())) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        UUID ownerId = ownerId(event.getItem());
        if (!isAuthorizedOwner(ownerId, player.getUniqueId())) {
            player.sendMessage(MessageUtil.warn("The First Dragon's Sigil answers only to its champion."));
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6f, 1.5f);
            return;
        }
        if (isRevoked(player)) {
            player.sendMessage(MessageUtil.warn("The First Dragon's Sigil has been revoked by staff."));
            return;
        }
        if (isRestrictedFight(player)) {
            player.sendMessage(MessageUtil.warn("Firstflight is sealed during boss fights and duels."));
            return;
        }
        if (player.isInsideVehicle()) {
            player.sendMessage(MessageUtil.warn("Dismount before using Firstflight."));
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = player.getPersistentDataContainer()
            .getOrDefault(keyCooldownUntil, PersistentDataType.LONG, 0L);
        long remaining = cooldownUntil - now;
        if (remaining > 0L) {
            player.sendActionBar(MM.deserialize("<light_purple>Firstflight returns in <white>" + formatRemaining(remaining) + "</white>.</light_purple>"));
            return;
        }
        player.getPersistentDataContainer().set(
            keyCooldownUntil,
            PersistentDataType.LONG,
            now + COOLDOWN_MILLIS
        );
        activateFirstflight(player, now);
        playCelebration(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProtectedFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long expiresAt = fallProtection.getOrDefault(playerId, 0L);
        if (!isFallProtected(expiresAt, System.currentTimeMillis())) {
            fallProtection.remove(playerId);
            return;
        }
        fallProtection.remove(playerId);
        event.setCancelled(true);
        player.setFallDistance(0.0F);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0, 0.2, 0.0), 10, 0.35, 0.08, 0.35, 0.01);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7F, 1.5F);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        fallProtection.remove(playerId);
    }

    static boolean isAuthorizedOwner(UUID ownerId, UUID playerId) {
        return ownerId != null && ownerId.equals(playerId);
    }

    static String formatRemaining(long millis) {
        long seconds = Math.max(1L, (millis + 999L) / 1_000L);
        if (seconds < 60L) {
            return seconds + "s";
        }
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }

    static boolean shouldBlast(boolean hostileMonster, boolean bossEncounter) {
        return hostileMonster && !bossEncounter;
    }

    static boolean isFallProtected(long expiresAt, long now) {
        return expiresAt > now;
    }

    public int revoke(Player player) {
        if (player == null) {
            return 0;
        }
        player.getPersistentDataContainer().set(keyRevoked, PersistentDataType.BYTE, (byte) 1);
        player.getPersistentDataContainer().remove(keyCooldownUntil);
        fallProtection.remove(player.getUniqueId());
        int removed = removeBoundSigils(player.getInventory().getContents(), player.getInventory(), player.getUniqueId());
        removed += removeBoundSigils(player.getEnderChest().getContents(), player.getEnderChest(), player.getUniqueId());
        ItemStack cursor = player.getItemOnCursor();
        if (isBoundTo(cursor, player.getUniqueId())) {
            player.setItemOnCursor(null);
            removed++;
        }
        return removed;
    }

    private int removeBoundSigils(ItemStack[] contents, org.bukkit.inventory.Inventory inventory, UUID ownerId) {
        int removed = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            if (!isBoundTo(contents[slot], ownerId)) {
                continue;
            }
            inventory.setItem(slot, null);
            removed++;
        }
        return removed;
    }

    private boolean isBoundTo(ItemStack item, UUID ownerId) {
        return isSigil(item) && ownerId.equals(ownerId(item));
    }

    private boolean isRevoked(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keyRevoked, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private boolean isRestrictedFight(Player player) {
        return (plugin.getBossManager() != null && plugin.getBossManager().isActiveBossFight(player))
            || (plugin.getBossDungeonManager() != null && plugin.getBossDungeonManager().blocksExternalTeleport(player))
            || (plugin.getDuelManager() != null && plugin.getDuelManager().blocksExternalTeleport(player));
    }

    private void activateFirstflight(Player player, long now) {
        Vector direction = player.getLocation().getDirection().setY(0.0D);
        if (direction.lengthSquared() < 0.0001D) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        }
        Vector launch = direction.normalize().multiply(1.45D).setY(0.85D);
        player.setFallDistance(0.0F);
        player.setVelocity(launch);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 12 * 20, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 20, 0, false, true, true));
        fallProtection.put(player.getUniqueId(), now + FALL_PROTECTION_MILLIS);

        int blasted = 0;
        for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(8.0D, 5.0D, 8.0D)) {
            boolean boss = plugin.getBossManager() != null && plugin.getBossManager().isBossEncounterEntity(nearby);
            if (!shouldBlast(nearby instanceof Monster, boss) || !(nearby instanceof Monster monster)) {
                continue;
            }
            Vector away = monster.getLocation().toVector().subtract(player.getLocation().toVector());
            if (away.lengthSquared() < 0.0001D) {
                away = player.getLocation().getDirection().multiply(-1.0D);
            }
            monster.damage(SHOCKWAVE_DAMAGE, player);
            monster.setVelocity(away.normalize().multiply(1.25D).setY(0.45D));
            blasted++;
        }
        player.sendActionBar(MM.deserialize(
            "<gradient:#c084fc:#fbbf24><bold>FIRSTFLIGHT</bold></gradient> <gray>Fall ward: <white>15s</white>"
                + (blasted > 0 ? " <dark_gray>·</dark_gray> <white>" + blasted + "</white> foes scattered" : "")
        ));
    }

    private UUID ownerId(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(keyOwner, PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void playCelebration(Player player) {
        player.showTitle(Title.title(
            MM.deserialize("<gradient:#c084fc:#fbbf24><bold>FIRST OF THE END</bold></gradient>"),
            MM.deserialize("<gray>The Dragon remembers.</gray>"),
            Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(600))
        ));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.25f);
        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 0.8f);

        for (int frame = 0; frame < 9; frame++) {
            int currentFrame = frame;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead()) {
                    return;
                }
                drawWings(player, currentFrame / 8.0);
            }, frame * 2L);
        }
    }

    private void drawWings(Player player, double spread) {
        Location origin = player.getLocation().add(0.0, 1.15, 0.0);
        double yaw = Math.toRadians(-player.getLocation().getYaw());
        Particle.DustOptions violet = new Particle.DustOptions(Color.fromRGB(165, 70, 255), 1.15f);
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 190, 70), 0.9f);

        for (int side : new int[] {-1, 1}) {
            for (int step = 0; step <= 8; step++) {
                double progress = step / 8.0;
                double width = (0.2 + progress * 1.55) * spread;
                double height = Math.sin(progress * Math.PI) * (0.65 + spread * 0.35) - progress * 0.15;
                Vector offset = new Vector(side * width, height, 0.15 + progress * 0.3).rotateAroundY(yaw);
                Location point = origin.clone().add(offset);
                player.getWorld().spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, violet);
                if (step == 0 || step == 8) {
                    player.getWorld().spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, gold);
                }
            }
        }
        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, origin, 4, 0.25, 0.35, 0.25, 0.01, 1.0F);
        player.getWorld().spawnParticle(Particle.END_ROD, origin, 2, 0.2, 0.25, 0.2, 0.01);
    }
}
