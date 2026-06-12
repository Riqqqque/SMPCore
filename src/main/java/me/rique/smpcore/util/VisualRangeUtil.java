package me.rique.smpcore.util;

import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

public final class VisualRangeUtil {

    public static final double HOLOGRAM_VIEW_RANGE_BLOCKS = 32.0D;
    private static final double DISPLAY_RANGE_BLOCK_UNIT = 64.0D;
    private static final float MIN_DISPLAY_VIEW_RANGE = 0.05f;

    private VisualRangeUtil() {
    }

    public static void applyHologramRange(Display display) {
        if (display != null) {
            display.setViewRange(blocksToDisplayViewRange(HOLOGRAM_VIEW_RANGE_BLOCKS));
            if (display instanceof TextDisplay textDisplay) {
                textDisplay.setSeeThrough(false);
            }
        }
    }

    public static void applyHologramRange(Display display, double requestedRange) {
        if (display != null) {
            display.setViewRange(clampHologramViewRange(requestedRange));
            if (display instanceof TextDisplay textDisplay) {
                textDisplay.setSeeThrough(false);
            }
        }
    }

    public static float clampHologramViewRange(double requestedRange) {
        if (requestedRange <= 1.0D) {
            return (float) Math.max(MIN_DISPLAY_VIEW_RANGE, Math.min(1.0D, requestedRange));
        }
        double clampedBlocks = Math.max(1.0D, Math.min(HOLOGRAM_VIEW_RANGE_BLOCKS, requestedRange));
        return blocksToDisplayViewRange(clampedBlocks);
    }

    public static float blocksToDisplayViewRange(double blockRange) {
        return (float) Math.max(MIN_DISPLAY_VIEW_RANGE, blockRange / DISPLAY_RANGE_BLOCK_UNIT);
    }
}
