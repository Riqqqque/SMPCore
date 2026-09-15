package me.rique.smpcore.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryRecipeUtilTest {

    @Test
    void missingMaterialMessageIncludesEveryShortageInRecipeOrder() {
        String message = InventoryRecipeUtil.missingIngredientsMessage(List.of(
            new InventoryRecipeUtil.MissingIngredient("Nocturne Rib", 4, 0),
            new InventoryRecipeUtil.MissingIngredient("Veil Heart", 1, 0),
            new InventoryRecipeUtil.MissingIngredient("Netherite Ingot", 1, 0)
        ));

        assertEquals(
            "Missing materials: <white>4x Nocturne Rib</white> <gray>(need <white>4</white>, have <white>0</white>)</gray>"
                + "<dark_gray>; </dark_gray><white>1x Veil Heart</white> <gray>(need <white>1</white>, have <white>0</white>)</gray>"
                + "<dark_gray>; </dark_gray><white>1x Netherite Ingot</white> <gray>(need <white>1</white>, have <white>0</white>)</gray>.",
            message
        );
    }

    @Test
    void missingMaterialMessageReportsOnlyTheActualDeficit() {
        assertEquals(
            "Missing materials: <white>2x Diamond</white> <gray>(need <white>8</white>, have <white>6</white>)</gray>.",
            InventoryRecipeUtil.missingIngredientsMessage(List.of(
                new InventoryRecipeUtil.MissingIngredient("Diamond", 8, 6)
            ))
        );
    }
}
