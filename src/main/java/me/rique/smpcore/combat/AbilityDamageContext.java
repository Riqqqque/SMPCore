package me.rique.smpcore.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Marks synchronous damage produced by an activated ability so held-item
 * listeners do not mistake it for a normal melee hit.
 */
public final class AbilityDamageContext {

    private static final ThreadLocal<Map<UUID, Integer>> ACTIVE_DEPTHS = new ThreadLocal<>();

    private AbilityDamageContext() {
    }

    public static void damage(Player attacker, LivingEntity target, double amount) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(target, "target");
        run(attacker.getUniqueId(), () -> target.damage(amount, attacker));
    }

    public static boolean isActive(Player attacker) {
        return attacker != null && isActive(attacker.getUniqueId());
    }

    static boolean isActive(UUID attackerId) {
        Map<UUID, Integer> depths = ACTIVE_DEPTHS.get();
        return attackerId != null && depths != null && depths.getOrDefault(attackerId, 0) > 0;
    }

    static void run(UUID attackerId, Runnable action) {
        Objects.requireNonNull(attackerId, "attackerId");
        Objects.requireNonNull(action, "action");

        Map<UUID, Integer> depths = ACTIVE_DEPTHS.get();
        if (depths == null) {
            depths = new HashMap<>();
            ACTIVE_DEPTHS.set(depths);
        }
        depths.merge(attackerId, 1, Integer::sum);
        try {
            action.run();
        } finally {
            int remaining = depths.getOrDefault(attackerId, 1) - 1;
            if (remaining > 0) {
                depths.put(attackerId, remaining);
            } else {
                depths.remove(attackerId);
            }
            if (depths.isEmpty()) {
                ACTIVE_DEPTHS.remove();
            }
        }
    }
}
