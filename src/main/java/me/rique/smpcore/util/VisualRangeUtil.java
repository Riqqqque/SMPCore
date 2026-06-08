package me.rique.smpcore.util;

import org.bukkit.entity.Display;

public final class VisualRangeUtil {

    public static final float HOLOGRAM_VIEW_RANGE = 32.0f;

    private VisualRangeUtil() {
    }

    public static void applyHologramRange(Display display) {
        if (display != null) {
            display.setViewRange(HOLOGRAM_VIEW_RANGE);
        }
    }

    public static float clampHologramViewRange(double requestedRange) {
        return (float) Math.max(0.05, Math.min(HOLOGRAM_VIEW_RANGE, requestedRange));
    }
}
