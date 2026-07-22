package me.rique.smpcore.quest;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

final class FamiliarMotion {
    static final int UPDATE_TICKS = 2;
    static final int PARTICLE_INTERVAL_TICKS = 10;
    static final int TELEPORT_DURATION_TICKS = 3;

    private static final double BACK_OFFSET = 1.45D;
    private static final double LEFT_OFFSET = 0.85D;
    private static final double HEIGHT_OFFSET = 1.35D;
    private static final double MIN_MOVE_DISTANCE_SQUARED = 0.0064D;
    private static final double HARD_SNAP_DISTANCE_SQUARED = 64.0D;
    private static final double PLAYER_MOVE_DISTANCE_SQUARED = 0.0025D;
    private static final double IDLE_FOLLOW_DISTANCE_SQUARED = 1.0D;
    private static final int IDLE_AFTER_TICKS = 12;

    private FamiliarMotion() {
    }

    static Location target(Player player, State motion, Location currentLocation) {
        Location base = player.getLocation().clone();
        boolean playerMoved = motion.updatePlayerPosition(base);
        float anchorYaw = motion.yaw;
        if (playerMoved) {
            anchorYaw = base.getYaw();
            float delta = normalizedYawDelta(anchorYaw - motion.yaw);
            motion.yaw += Math.clamp(delta * 0.35F, -10.0F, 10.0F);
        }

        Location followTarget = anchor(base, anchorYaw, motion, player.getTicksLived());
        if (motion.stationaryTicks >= IDLE_AFTER_TICKS
            && currentLocation != null
            && currentLocation.getWorld().equals(base.getWorld())
            && currentLocation.distanceSquared(followTarget) <= IDLE_FOLLOW_DISTANCE_SQUARED) {
            Location idle = currentLocation.clone();
            idle.setY(followTarget.getY());
            idle.setYaw(motion.yaw);
            idle.setPitch(0.0F);
            return idle;
        }
        return followTarget;
    }

    static void move(Entity entity, Location target) {
        if (!entity.getWorld().equals(target.getWorld())) {
            configureDisplay(entity, 0);
            entity.teleport(target);
            configureDisplay(entity, TELEPORT_DURATION_TICKS);
            return;
        }

        Location current = entity.getLocation();
        double distanceSquared = current.distanceSquared(target);
        if (distanceSquared <= MIN_MOVE_DISTANCE_SQUARED) return;
        if (distanceSquared >= HARD_SNAP_DISTANCE_SQUARED) {
            configureDisplay(entity, 0);
            entity.teleport(target);
            configureDisplay(entity, TELEPORT_DURATION_TICKS);
            return;
        }

        double blend = movementBlend(distanceSquared);
        Location next = current.clone().add(
            (target.getX() - current.getX()) * blend,
            (target.getY() - current.getY()) * blend,
            (target.getZ() - current.getZ()) * blend
        );
        next.setYaw(target.getYaw());
        next.setPitch(0.0F);
        configureDisplay(entity, TELEPORT_DURATION_TICKS);
        entity.teleport(next);
    }

    static void configureDisplay(Entity entity, int ticks) {
        if (!(entity instanceof Display display)) return;
        int safeTicks = Math.clamp(ticks, 0, 59);
        display.setTeleportDuration(safeTicks);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(safeTicks);
    }

    static double movementBlend(double distanceSquared) {
        return distanceSquared > 6.25D ? 0.78D : distanceSquared > 1.44D ? 0.58D : 0.42D;
    }

    static float normalizedYawDelta(float delta) {
        while (delta <= -180.0F) delta += 360.0F;
        while (delta > 180.0F) delta -= 360.0F;
        return delta;
    }

    static double bobOffset(int motionTicks, int playerTicksLived) {
        return Math.sin((motionTicks + playerTicksLived) * 0.18D) * 0.12D;
    }

    private static Location anchor(Location base, float anchorYaw, State motion, int playerTicksLived) {
        Vector direction = directionFromYaw(anchorYaw).normalize();
        Vector back = direction.clone().multiply(-BACK_OFFSET);
        Vector left = new Vector(direction.getZ(), 0.0D, -direction.getX()).normalize().multiply(LEFT_OFFSET);
        base.add(back).add(left).add(0.0D, HEIGHT_OFFSET + bobOffset(motion.ticks, playerTicksLived), 0.0D);
        base.setYaw(motion.yaw);
        base.setPitch(0.0F);
        return base;
    }

    private static Vector directionFromYaw(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vector(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    static final class State {
        private float yaw;
        private int ticks;
        private UUID lastPlayerWorldId;
        private double lastPlayerX;
        private double lastPlayerY;
        private double lastPlayerZ;
        private int stationaryTicks;

        State(float yaw) {
            this.yaw = yaw;
        }

        void reset(float yaw) {
            this.yaw = yaw;
            ticks = 0;
            lastPlayerWorldId = null;
            stationaryTicks = 0;
        }

        void advance() {
            ticks += UPDATE_TICKS;
        }

        int ticks() {
            return ticks;
        }

        private boolean updatePlayerPosition(Location location) {
            UUID worldId = location.getWorld().getUID();
            boolean moved = lastPlayerWorldId == null
                || !lastPlayerWorldId.equals(worldId)
                || distanceSquared(location) > PLAYER_MOVE_DISTANCE_SQUARED;
            lastPlayerWorldId = worldId;
            lastPlayerX = location.getX();
            lastPlayerY = location.getY();
            lastPlayerZ = location.getZ();
            if (moved) stationaryTicks = 0;
            else stationaryTicks = Math.min(stationaryTicks + UPDATE_TICKS, 20 * 60);
            return moved;
        }

        private double distanceSquared(Location location) {
            double dx = location.getX() - lastPlayerX;
            double dy = location.getY() - lastPlayerY;
            double dz = location.getZ() - lastPlayerZ;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
