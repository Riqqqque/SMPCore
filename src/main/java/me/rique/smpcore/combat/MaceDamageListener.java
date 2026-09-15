package me.rique.smpcore.combat;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class MaceDamageListener implements Listener {

    static final double FULL_CHARGE_MAX_DAMAGE = 10.0D;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaceDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)
            || !(event.getDamager() instanceof Player attacker)
            || attacker.getInventory().getItemInMainHand().getType() != Material.MACE) {
            return;
        }

        double cap = damageCap(attacker.getCooledAttackStrength(0.0F));
        event.setDamage(Math.min(sanitizeDamage(event.getDamage()), cap));
    }

    static double damageCap(float attackStrength) {
        double charge = Math.max(0.0D, Math.min(1.0D, attackStrength));
        return FULL_CHARGE_MAX_DAMAGE * (0.2D + 0.8D * charge * charge);
    }

    private static double sanitizeDamage(double damage) {
        if (!Double.isFinite(damage)) {
            return damage == Double.POSITIVE_INFINITY ? Double.MAX_VALUE : 0.0D;
        }
        return Math.max(0.0D, damage);
    }
}
