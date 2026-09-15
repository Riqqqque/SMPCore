package me.rique.smpcore.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Utility helpers for location-related operations.
 */
public final class LocationUtil {

    private LocationUtil() {}

    /**
     * Returns a location directly above the highest solid block at (x, z),
     * with yaw/pitch preserved from the original location.
     */
    public static Location getTopLocation(Location origin) {
        World world = origin.getWorld();
        int x = origin.getBlockX();
        int z = origin.getBlockZ();
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x + 0.5, y + 1.0, z + 0.5,
                origin.getYaw(), origin.getPitch());
    }

    public static Location findNearestSafeStandingLocation(Location target, int horizontalRadius, int verticalRadius) {
        if (target == null || target.getWorld() == null) {
            return null;
        }

        World world = target.getWorld();
        int safeHorizontalRadius = Math.max(0, horizontalRadius);
        int safeVerticalRadius = Math.max(0, verticalRadius);
        int baseX = target.getBlockX();
        int baseY = clampFeetY(world, target.getBlockY());
        int baseZ = target.getBlockZ();

        for (int radius = 0; radius <= safeHorizontalRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy : orderedOffsets(safeVerticalRadius)) {
                        Location candidate = centered(world, baseX + dx, baseY + dy, baseZ + dz, target);
                        if (isSafeStandingLocation(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        for (int radius = 0; radius <= safeHorizontalRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = baseX + dx;
                    int z = baseZ + dz;
                    Location candidate = centered(world, x, world.getHighestBlockYAt(x, z) + 1, z, target);
                    if (isSafeStandingLocation(candidate)) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    public static boolean isSafeStandingLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        World world = location.getWorld();
        int feetY = location.getBlockY();
        if (feetY <= world.getMinHeight() || feetY >= world.getMaxHeight() - 1) {
            return false;
        }

        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block floor = feet.getRelative(BlockFace.DOWN);
        return isClearBodyBlock(feet)
            && isClearBodyBlock(head)
            && !floor.isPassable()
            && !floor.isLiquid()
            && !isUnsafeFloor(floor.getType());
    }

    /**
     * Converts a Location to a compact string key suitable for map keys and DB storage.
     * Format: "world:x:y:z"
     */
    public static String toKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    /**
     * Serialise a Location (with yaw/pitch) to a storable string.
     * Format: "world:x:y:z:yaw:pitch"
     */
    public static String serialise(Location loc) {
        return loc.getWorld().getName()
                + ":" + loc.getX()
                + ":" + loc.getY()
                + ":" + loc.getZ()
                + ":" + loc.getYaw()
                + ":" + loc.getPitch();
    }

    /**
     * Returns whether two locations are in the same chunk.
     */
    public static boolean sameChunk(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
                && a.getChunk().getX() == b.getChunk().getX()
                && a.getChunk().getZ() == b.getChunk().getZ();
    }

    /**
     * Returns the block-centred location for a given block position.
     */
    public static Location centreOf(Location blockLoc) {
        return new Location(blockLoc.getWorld(),
                blockLoc.getBlockX() + 0.5,
                blockLoc.getBlockY(),
                blockLoc.getBlockZ() + 0.5);
    }

    private static Location centered(World world, int x, int y, int z, Location rotationSource) {
        return new Location(world, x + 0.5, y, z + 0.5, rotationSource.getYaw(), rotationSource.getPitch());
    }

    private static int clampFeetY(World world, int y) {
        int min = world.getMinHeight() + 1;
        int max = world.getMaxHeight() - 2;
        return Math.max(min, Math.min(max, y));
    }

    private static int[] orderedOffsets(int radius) {
        int[] offsets = new int[(radius * 2) + 1];
        offsets[0] = 0;
        int index = 1;
        for (int step = 1; step <= radius; step++) {
            offsets[index++] = step;
            offsets[index++] = -step;
        }
        return offsets;
    }

    private static boolean isClearBodyBlock(Block block) {
        return block.isPassable() && !block.isLiquid() && !isUnsafeBody(block.getType());
    }

    private static boolean isUnsafeBody(Material material) {
        return switch (material) {
            case FIRE, SOUL_FIRE, LAVA, WATER, POWDER_SNOW -> true;
            default -> false;
        };
    }

    private static boolean isUnsafeFloor(Material material) {
        return switch (material) {
            case LAVA, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, CACTUS, POWDER_SNOW -> true;
            default -> false;
        };
    }
}
