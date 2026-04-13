package me.rique.smpcore.combat;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks PvP combat tags and punishes combat logging by killing the quitter
 * and dropping their inventory at logout location.
 */
public final class CombatLogListener implements Listener {

    private final SMPCore plugin;
    private final Map<UUID, CombatTag> combatTags = new ConcurrentHashMap<>();
    private final Map<UUID, Long> notifyCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> restrictionNotifyCooldown = new ConcurrentHashMap<>();

    public CombatLogListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getFinalDamage() <= 0.0) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        tagPlayers(attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.isGliding()) return;
        if (activeTag(player.getUniqueId()) == null) return;

        event.setCancelled(true);
        maybeNotifyRestriction(player, "You can't use an elytra while in combat.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        Player player = event.getPlayer();
        if (activeTag(player.getUniqueId()) == null) return;

        event.setShouldConsume(false);
        event.setCancelled(true);

        Firework firework = event.getFirework();
        if (firework.isValid()) {
            firework.remove();
        }

        maybeNotifyRestriction(player, "You can't use fireworks while in combat.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireworkUse(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        if (event.getMaterial() != Material.FIREWORK_ROCKET) return;

        Player player = event.getPlayer();
        if (activeTag(player.getUniqueId()) == null) return;

        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        maybeNotifyRestriction(player, "You can't use fireworks while in combat.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player quitter = event.getPlayer();
        CombatTag tag = activeTag(quitter.getUniqueId());
        if (tag == null) return;

        event.quitMessage(null);
        punishCombatLog(quitter, tag.opponentUuid());
        combatTags.remove(quitter.getUniqueId());
        notifyCooldown.remove(quitter.getUniqueId());
        restrictionNotifyCooldown.remove(quitter.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        combatTags.remove(id);
        notifyCooldown.remove(id);
        restrictionNotifyCooldown.remove(id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        combatTags.remove(id);
        notifyCooldown.remove(id);
        restrictionNotifyCooldown.remove(id);
    }

    private void punishCombatLog(Player quitter, UUID opponentUuid) {
        Location dropAt = quitter.getLocation().clone().add(0.0, 0.15, 0.0);
        dropAllItems(quitter, dropAt);

        quitter.setExp(0.0f);
        quitter.setLevel(0);
        quitter.setTotalExperience(0);
        quitter.setFireTicks(0);
        if (quitter.isInvulnerable()) {
            quitter.setInvulnerable(false);
        }
        if (quitter.getHealth() > 0.0) {
            quitter.setHealth(0.0);
        }

        Bukkit.broadcast(MessageUtil.prefixedRaw(
            "<red><white>" + quitter.getName() + "</white> combat logged and was slain.</red>"
        ));

        Player opponent = Bukkit.getPlayer(opponentUuid);
        if (opponent != null && opponent.isOnline()) {
            opponent.sendMessage(MessageUtil.success(
                "<white>" + quitter.getName() + "</white> combat logged and dropped their inventory."
            ));
        }
    }

    private void dropAllItems(Player player, Location dropAt) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        ItemStack[] armor = inv.getArmorContents();
        ItemStack[] extra = inv.getExtraContents();

        dropContents(dropAt, inv.getStorageContents());
        dropContents(dropAt, armor);
        dropContents(dropAt, extra);

        inv.setStorageContents(new ItemStack[storage.length]);
        inv.setArmorContents(new ItemStack[armor.length]);
        inv.setExtraContents(new ItemStack[extra.length]);
    }

    private static void dropContents(Location dropAt, ItemStack[] items) {
        if (items == null) return;
        for (ItemStack item : items) {
            dropItem(dropAt, item);
        }
    }

    private static void dropItem(Location dropAt, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return;
        if (dropAt.getWorld() == null) return;
        dropAt.getWorld().dropItemNaturally(dropAt, item.clone());
    }

    private CombatTag activeTag(UUID playerId) {
        CombatTag tag = combatTags.get(playerId);
        if (tag == null) return null;
        if (tag.expiresAt() <= System.currentTimeMillis()) {
            combatTags.remove(playerId);
            return null;
        }
        return tag;
    }

    private void maybeNotify(Player player) {
        long now = System.currentTimeMillis();
        long last = notifyCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 2000L) return;
        notifyCooldown.put(player.getUniqueId(), now);
        player.sendActionBar(MessageUtil.parse("<red>In combat: logging out will kill you.</red>"));
    }

    private void maybeNotifyRestriction(Player player, String message) {
        long now = System.currentTimeMillis();
        long last = restrictionNotifyCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 1500L) return;
        restrictionNotifyCooldown.put(player.getUniqueId(), now);
        player.sendActionBar(MessageUtil.parse("<red>" + message + "</red>"));
    }

    private void stopGlidingIfTagged(Player player) {
        if (!player.isGliding()) return;
        if (activeTag(player.getUniqueId()) == null) return;

        player.setGliding(false);
        maybeNotifyRestriction(player, "You can't use an elytra while in combat.");
    }

    private static boolean isCombatTaggable(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) return false;
        GameMode mode = player.getGameMode();
        return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
    }

    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    public boolean isInPlayerCombat(Player player) {
        return player != null && isInPlayerCombat(player.getUniqueId());
    }

    public boolean isInPlayerCombat(UUID playerId) {
        return activeTag(playerId) != null;
    }

    public void tagPlayers(Player attacker, Player victim) {
        if (attacker == null || victim == null || attacker.equals(victim)) {
            return;
        }
        if (!isCombatTaggable(attacker) || !isCombatTaggable(victim)) {
            return;
        }

        long expiresAt = System.currentTimeMillis() + plugin.getConfigManager().combatTagSeconds * 1000L;
        combatTags.put(attacker.getUniqueId(), new CombatTag(victim.getUniqueId(), expiresAt));
        combatTags.put(victim.getUniqueId(), new CombatTag(attacker.getUniqueId(), expiresAt));

        stopGlidingIfTagged(attacker);
        stopGlidingIfTagged(victim);
        maybeNotify(attacker);
        maybeNotify(victim);
    }

    private record CombatTag(UUID opponentUuid, long expiresAt) {}
}
