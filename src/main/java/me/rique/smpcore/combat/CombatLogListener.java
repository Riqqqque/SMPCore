package me.rique.smpcore.combat;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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

    public CombatLogListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getFinalDamage() <= 0.0) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (!isCombatTaggable(attacker) || !isCombatTaggable(victim)) return;

        long expiresAt = System.currentTimeMillis() + plugin.getConfigManager().combatTagSeconds * 1000L;
        combatTags.put(attacker.getUniqueId(), new CombatTag(victim.getUniqueId(), expiresAt));
        combatTags.put(victim.getUniqueId(), new CombatTag(attacker.getUniqueId(), expiresAt));

        maybeNotify(attacker);
        maybeNotify(victim);
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
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        combatTags.remove(id);
        notifyCooldown.remove(id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        combatTags.remove(id);
        notifyCooldown.remove(id);
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
        dropContents(dropAt, inv.getStorageContents());
        dropContents(dropAt, inv.getArmorContents());
        dropContents(dropAt, inv.getExtraContents());
        dropItem(dropAt, inv.getItemInOffHand());

        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(new ItemStack(Material.AIR));
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

    private record CombatTag(UUID opponentUuid, long expiresAt) {}
}
