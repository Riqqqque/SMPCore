package me.rique.smpcore.combat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class TntMinecartDamageListener implements Listener {

    static final double MAX_DAMAGE = 10.0D;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTntMinecartDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !isTntMinecartDamage(event)) {
            return;
        }
        event.setDamage(cappedDamage(event.getDamage()));
    }

    private static boolean isTntMinecartDamage(EntityDamageByEntityEvent event) {
        return isTntMinecart(event.getDamager())
            || isTntMinecart(event.getDamageSource().getDirectEntity())
            || isTntMinecart(event.getDamageSource().getCausingEntity());
    }

    private static boolean isTntMinecart(Entity entity) {
        return entity != null && entity.getType() == EntityType.TNT_MINECART;
    }

    static double cappedDamage(double damage) {
        if (Double.isNaN(damage)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(MAX_DAMAGE, damage));
    }
}
