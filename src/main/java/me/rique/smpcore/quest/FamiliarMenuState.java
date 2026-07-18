package me.rique.smpcore.quest;

import org.bukkit.Material;

record FamiliarMenuState(boolean summoned) {
    static FamiliarMenuState from(boolean summoned) {
        return new FamiliarMenuState(summoned);
    }

    boolean canSummon() {
        return !summoned;
    }

    boolean canDismiss() {
        return summoned;
    }

    Material summonIcon() {
        return canSummon() ? Material.LIME_DYE : Material.LIME_STAINED_GLASS_PANE;
    }

    Material dismissIcon() {
        return canDismiss() ? Material.RED_DYE : Material.GRAY_STAINED_GLASS_PANE;
    }

    String summonAction(String action) {
        return canSummon() ? action : null;
    }

    String dismissAction(String action) {
        return canDismiss() ? action : null;
    }
}
