package me.rique.smpcore.player;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

final class GsitSeatBridge {

    private static final double STAIR_XZ_OFFSET = 0.123D;
    private static final double STAIR_Y_OFFSET = -0.5D;

    private final JavaPlugin plugin;
    private Api api;
    private boolean lookupFailed;

    GsitSeatBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    boolean trySitOnStair(Block block, Player player) {
        if (!(block.getBlockData() instanceof Stairs stairs)
            || stairs.getHalf() != Bisected.Half.BOTTOM
            || !player.isValid()
            || player.isSneaking()
            || player.isSleeping()
            || player.getVehicle() != null
            || !block.getRelative(BlockFace.UP).isPassable()
            || !hasClickPermission(player)) {
            return false;
        }

        Api resolved = resolveApi();
        if (resolved == null) {
            return false;
        }

        SeatPose pose = seatPose(stairs.getFacing().getOppositeFace(), stairs.getShape());
        if (pose == null) {
            return false;
        }

        try {
            if ((boolean) resolved.isEntitySitting().invoke(null, player)
                || !(boolean) resolved.canEntityUseSit().invoke(null, player)) {
                return false;
            }
            return resolved.createSeat().invoke(
                null,
                block,
                player,
                false,
                pose.xOffset(),
                STAIR_Y_OFFSET,
                pose.zOffset(),
                pose.yaw(),
                true
            ) != null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            disableAfterFailure(ex);
            return false;
        }
    }

    static boolean shouldUseSideClickFallback(BlockFace clickedFace) {
        return clickedFace != null && clickedFace != BlockFace.UP;
    }

    static SeatPose seatPose(BlockFace seatFacing, Stairs.Shape shape) {
        if (seatFacing == null || shape == null) {
            return null;
        }
        if (shape == Stairs.Shape.STRAIGHT) {
            return switch (seatFacing) {
                case EAST -> new SeatPose(STAIR_XZ_OFFSET, 0D, -90F);
                case SOUTH -> new SeatPose(0D, STAIR_XZ_OFFSET, 0F);
                case WEST -> new SeatPose(-STAIR_XZ_OFFSET, 0D, 90F);
                case NORTH -> new SeatPose(0D, -STAIR_XZ_OFFSET, 180F);
                default -> null;
            };
        }

        if ((seatFacing == BlockFace.NORTH && (shape == Stairs.Shape.OUTER_RIGHT || shape == Stairs.Shape.INNER_RIGHT))
            || (seatFacing == BlockFace.EAST && (shape == Stairs.Shape.OUTER_LEFT || shape == Stairs.Shape.INNER_LEFT))) {
            return new SeatPose(STAIR_XZ_OFFSET, -STAIR_XZ_OFFSET, -135F);
        }
        if ((seatFacing == BlockFace.NORTH && (shape == Stairs.Shape.OUTER_LEFT || shape == Stairs.Shape.INNER_LEFT))
            || (seatFacing == BlockFace.WEST && (shape == Stairs.Shape.OUTER_RIGHT || shape == Stairs.Shape.INNER_RIGHT))) {
            return new SeatPose(-STAIR_XZ_OFFSET, -STAIR_XZ_OFFSET, 135F);
        }
        if ((seatFacing == BlockFace.SOUTH && (shape == Stairs.Shape.OUTER_RIGHT || shape == Stairs.Shape.INNER_RIGHT))
            || (seatFacing == BlockFace.WEST && (shape == Stairs.Shape.OUTER_LEFT || shape == Stairs.Shape.INNER_LEFT))) {
            return new SeatPose(-STAIR_XZ_OFFSET, STAIR_XZ_OFFSET, 45F);
        }
        if ((seatFacing == BlockFace.SOUTH && (shape == Stairs.Shape.OUTER_LEFT || shape == Stairs.Shape.INNER_LEFT))
            || (seatFacing == BlockFace.EAST && (shape == Stairs.Shape.OUTER_RIGHT || shape == Stairs.Shape.INNER_RIGHT))) {
            return new SeatPose(STAIR_XZ_OFFSET, STAIR_XZ_OFFSET, -45F);
        }
        return null;
    }

    private boolean hasClickPermission(Player player) {
        return player.hasPermission("GSit.SitClick")
            || player.hasPermission("GSit.Sit.*")
            || player.hasPermission("GSit.*");
    }

    private Api resolveApi() {
        if (api != null || lookupFailed) {
            return api;
        }

        Plugin gsit = Bukkit.getPluginManager().getPlugin("GSit");
        if (gsit == null || !gsit.isEnabled()) {
            return null;
        }

        try {
            Class<?> apiClass = Class.forName("dev.geco.gsit.api.GSitAPI", true, gsit.getClass().getClassLoader());
            api = new Api(
                apiClass.getMethod("isEntitySitting", LivingEntity.class),
                apiClass.getMethod("canEntityUseSit", Entity.class),
                apiClass.getMethod(
                    "createSeat",
                    Block.class,
                    LivingEntity.class,
                    boolean.class,
                    double.class,
                    double.class,
                    double.class,
                    float.class,
                    boolean.class
                )
            );
            return api;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            disableAfterFailure(ex);
            return null;
        }
    }

    private void disableAfterFailure(Throwable error) {
        if (!lookupFailed) {
            plugin.getLogger().warning("GSit stair-seat integration is unavailable: " + error.getMessage());
        }
        lookupFailed = true;
        api = null;
    }

    record SeatPose(double xOffset, double zOffset, float yaw) {
    }

    private record Api(Method isEntitySitting, Method canEntityUseSit, Method createSeat) {
    }
}
