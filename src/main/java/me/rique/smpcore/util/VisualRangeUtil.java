package me.rique.smpcore.util;

import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

public final class VisualRangeUtil {

    public static final float HOLOGRAM_VIEW_RANGE = 32.0f;

    private VisualRangeUtil() {
    }

    public static void applyHologramRange(Display display) {
        if (display != null) {
            display.setViewRange(HOLOGRAM_VIEW_RANGE);
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
        return (float) Math.max(0.05, Math.min(HOLOGRAM_VIEW_RANGE, requestedRange));
    }
}
