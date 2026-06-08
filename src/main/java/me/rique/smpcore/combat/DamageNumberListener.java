package me.rique.smpcore.combat;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.VisualRangeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DamageNumberListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int LIFETIME_TICKS = 22;
    private static final int MAX_ACTIVE_NUMBERS = 180;

    private final SMPCore plugin;
    private final Map<UUID, DamageNumberState> activeNumbers = new LinkedHashMap<>();
    private BukkitTask animationTask;

    public DamageNumberListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (animationTask != null) {
            return;
        }
        animationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickDamageNumbers, 1L, 1L);
    }

    public void shutdown() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
        for (UUID id : activeNumbers.keySet()) {
            Entity entity = plugin.getServer().getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        activeNumbers.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target) || target instanceof ArmorStand) {
            return;
        }

        double damage = event.getFinalDamage();
        if (damage <= 0.05 || !target.isValid()) {
            return;
        }

        DamageNumberStyle style = DamageNumberStyle.NORMAL;
        if (event instanceof EntityDamageByEntityEvent hitEvent && hitEvent.isCritical()) {
            style = DamageNumberStyle.CRITICAL;
        } else if (damage >= 12.0) {
            style = DamageNumberStyle.HEAVY;
        }

        showDamageNumber(target, damage, style);
    }

    public void showTrueDamage(LivingEntity target, double damage) {
        showDamageNumber(target, damage, DamageNumberStyle.TRUE);
    }

    public void showDamageNumber(LivingEntity target, double damage, DamageNumberStyle style) {
        if (target == null || target.isDead() || !target.isValid() || damage <= 0.05) {
            return;
        }
        World world = target.getWorld();
        Location spawn = displayLocation(target);
        Component text = style.format(damage);

        TextDisplay display = world.spawn(spawn, TextDisplay.class, entity -> {
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setShadowed(true);
            entity.setSeeThrough(false);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setTextOpacity((byte) 255);
            entity.setLineWidth(90);
            entity.setViewRange(VisualRangeUtil.clampHologramViewRange(style.viewRange()));
            entity.setBrightness(new Brightness(15, 15));
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setGlowing(style.glowing());
            if (style.glowing()) {
                entity.setGlowColorOverride(style.glowColor());
            }
        });

        Vector drift = new Vector(
            ThreadLocalRandom.current().nextDouble(-0.018, 0.018),
            ThreadLocalRandom.current().nextDouble(0.045, 0.065),
            ThreadLocalRandom.current().nextDouble(-0.018, 0.018)
        );
        activeNumbers.put(display.getUniqueId(), new DamageNumberState(spawn, drift, 0));
        trimOldNumbers();
    }

    private Location displayLocation(LivingEntity target) {
        double height = Math.max(0.8, target.getHeight());
        double spread = Math.min(0.45, Math.max(0.18, target.getWidth() * 0.45));
        return target.getLocation().clone().add(
            ThreadLocalRandom.current().nextDouble(-spread, spread),
            height + ThreadLocalRandom.current().nextDouble(0.25, 0.55),
            ThreadLocalRandom.current().nextDouble(-spread, spread)
        );
    }

    private void tickDamageNumbers() {
        Iterator<Map.Entry<UUID, DamageNumberState>> iterator = activeNumbers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DamageNumberState> entry = iterator.next();
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (!(entity instanceof TextDisplay display) || !display.isValid()) {
                iterator.remove();
                continue;
            }

            DamageNumberState state = entry.getValue().next();
            if (state.age() >= LIFETIME_TICKS) {
                display.remove();
                iterator.remove();
                continue;
            }

            Location next = state.origin().clone().add(state.drift().clone().multiply(state.age()));
            display.teleport(next);
            int opacity = Math.max(0, 255 - (state.age() * 11));
            display.setTextOpacity((byte) opacity);
            entry.setValue(state);
        }
    }

    private void trimOldNumbers() {
        while (activeNumbers.size() > MAX_ACTIVE_NUMBERS) {
            Iterator<UUID> iterator = activeNumbers.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            UUID oldest = iterator.next();
            Entity entity = plugin.getServer().getEntity(oldest);
            if (entity != null) {
                entity.remove();
            }
            iterator.remove();
        }
    }

    public enum DamageNumberStyle {
        NORMAL("<gradient:#fff07a:#ffb347><bold>%s</bold></gradient>", Color.fromRGB(255, 190, 70), false, 28.0f),
        HEAVY("<gradient:#ffd166:#ff6b35><bold>%s!</bold></gradient>", Color.fromRGB(255, 120, 45), true, 32.0f),
        CRITICAL("<gradient:#ff3d3d:#ffd166><bold>CRIT %s!</bold></gradient>", Color.fromRGB(255, 45, 45), true, 32.0f),
        TRUE("<gradient:#b388ff:#ff4d8d><bold>TRUE %s</bold></gradient>", Color.fromRGB(190, 80, 255), true, 32.0f);

        private final String template;
        private final Color glowColor;
        private final boolean glowing;
        private final float viewRange;

        DamageNumberStyle(String template, Color glowColor, boolean glowing, float viewRange) {
            this.template = template;
            this.glowColor = glowColor;
            this.glowing = glowing;
            this.viewRange = viewRange;
        }

        private Component format(double damage) {
            return MM.deserialize(template.formatted(formatDamage(damage)));
        }

        private Color glowColor() {
            return glowColor;
        }

        private boolean glowing() {
            return glowing;
        }

        private float viewRange() {
            return viewRange;
        }

        private static String formatDamage(double damage) {
            double rounded = Math.round(damage * 10.0) / 10.0;
            if (Math.abs(rounded - Math.rint(rounded)) < 0.001) {
                return String.format(Locale.US, "%.0f", rounded);
            }
            return String.format(Locale.US, "%.1f", rounded);
        }
    }

    private record DamageNumberState(Location origin, Vector drift, int age) {
        private DamageNumberState next() {
            return new DamageNumberState(origin, drift, age + 1);
        }
    }
}
