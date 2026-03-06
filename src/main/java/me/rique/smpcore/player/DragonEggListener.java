package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Grants speed while a player holds a dragon egg in the offhand.
 */
public final class DragonEggListener extends BukkitRunnable implements Listener {

    private final SMPCore plugin;
    private final Set<UUID> affected = new HashSet<>();

    public DragonEggListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int configured = plugin.getConfigManager().dragonEggCheckInterval;
        int interval = Math.max(1, configured);
        if (configured < 1) {
            plugin.getLogger().warning("dragon-egg.check-interval must be >= 1. Falling back to 1 tick.");
        }
        runTaskTimer(plugin, interval, interval);
    }

    @Override
    public void run() {
        int amplifier = Math.max(0, plugin.getConfigManager().dragonEggSpeedAmplifier);
        int interval = Math.max(1, plugin.getConfigManager().dragonEggCheckInterval);
        int duration = interval + 5;

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean hasEgg = player.getInventory().getItemInOffHand().getType() == Material.DRAGON_EGG;

            if (hasEgg) {
                affected.add(player.getUniqueId());
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,
                    duration,
                    amplifier,
                    false,
                    false,
                    false
                ));
                continue;
            }

            if (affected.remove(player.getUniqueId())) {
                // Remove only the likely plugin-applied speed effect.
                PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
                if (current != null
                    && current.getAmplifier() == amplifier
                    && current.getDuration() <= duration + 20) {
                    player.removePotionEffect(PotionEffectType.SPEED);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        affected.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || event.getItem().getType() != Material.DRAGON_EGG) return;

        // Deny only the offhand egg use so right-click interactions and main-hand usage still work.
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }
}
