package me.rique.smpcore.util;

import org.bukkit.World;

public final class ScheduledDimensionPolicy {

    private ScheduledDimensionPolicy() {}

    public static boolean blocksTravel(
        World.Environment from,
        World.Environment to,
        boolean bypass,
        boolean unlocked
    ) {
        if (from == null || to == null || from == to || bypass || unlocked) return false;
        return to == World.Environment.NETHER || to == World.Environment.THE_END;
    }
}
