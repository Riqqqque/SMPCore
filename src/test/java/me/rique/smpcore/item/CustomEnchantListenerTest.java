package me.rique.smpcore.item;

import org.junit.jupiter.api.Test;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomEnchantListenerTest {

    @Test
    void customEnchantBooksMustBeSingleItemTransactions() {
        assertFalse(CustomEnchantListener.isValidCustomBookAmount(0));
        assertTrue(CustomEnchantListener.isValidCustomBookAmount(1));
        assertFalse(CustomEnchantListener.isValidCustomBookAmount(2));
        assertFalse(CustomEnchantListener.isValidCustomBookAmount(64));
    }

    @Test
    void customEnchantBookPayloadMustIdentifyExactlyOneMatchingEnchant() {
        assertEquals(CustomEnchantListener.CustomBookPayloadState.NONE,
            CustomEnchantListener.classifyCustomBookPayload(null, Map.of(), 1, false, false));
        assertEquals(CustomEnchantListener.CustomBookPayloadState.VALID,
            CustomEnchantListener.classifyCustomBookPayload("wise", Map.of("wise", 2), 1, false, false));
        assertEquals(CustomEnchantListener.CustomBookPayloadState.VALID,
            CustomEnchantListener.classifyCustomBookPayload("WISE", Map.of("wise", 3), 1, false, false));

        assertEquals(CustomEnchantListener.CustomBookPayloadState.INVALID,
            CustomEnchantListener.classifyCustomBookPayload("wise", Map.of("wise", 1, "dash", 1), 1, false, false));
        assertEquals(CustomEnchantListener.CustomBookPayloadState.INVALID,
            CustomEnchantListener.classifyCustomBookPayload("wise", Map.of("dash", 1), 1, false, false));
        assertEquals(CustomEnchantListener.CustomBookPayloadState.INVALID,
            CustomEnchantListener.classifyCustomBookPayload("missing", Map.of("wise", 1), 1, false, false));
        assertEquals(CustomEnchantListener.CustomBookPayloadState.INVALID,
            CustomEnchantListener.classifyCustomBookPayload(null, Map.of("wise", 1), 1, false, false));
    }

    @Test
    void customEnchantBookPayloadRejectsEveryContaminationPath() {
        assertEquals(CustomEnchantListener.CustomBookPayloadState.INVALID,
            CustomEnchantListener.classifyCustomBookPayload("wise", Map.of("wise", 1), 2, false, false));
        assertEquals(CustomEnchantListener.CustomBookPayloadState.INVALID,
            CustomEnchantListener.classifyCustomBookPayload("wise", Map.of("wise", 0), 1, false, false));
        assertEquals(CustomEnchantListener.CustomBookPayloadState.INVALID,
            CustomEnchantListener.classifyCustomBookPayload("wise", Map.of("wise", -1), 1, false, false));
        assertEquals(CustomEnchantListener.CustomBookPayloadState.INVALID,
            CustomEnchantListener.classifyCustomBookPayload("wise", Map.of("wise", 4), 1, false, false));
        assertEquals(CustomEnchantListener.CustomBookPayloadState.INVALID,
            CustomEnchantListener.classifyCustomBookPayload("wise", Map.of("wise", 1), 1, true, false));
        assertEquals(CustomEnchantListener.CustomBookPayloadState.INVALID,
            CustomEnchantListener.classifyCustomBookPayload("wise", Map.of("wise", 1), 1, false, true));

        Map<String, Integer> wrongType = new HashMap<>();
        wrongType.put("wise", null);
        assertEquals(CustomEnchantListener.CustomBookPayloadState.INVALID,
            CustomEnchantListener.classifyCustomBookPayload("wise", wrongType, 1, false, false));
    }

    @Test
    void legacyBookRepairUsesOnlyTheExplicitBookIdentity() {
        assertEquals("wise", CustomEnchantListener.canonicalCustomBookId(
            "wise",
            Map.of("wise", 2, "dash", 1),
            false
        ));
        assertEquals("wise", CustomEnchantListener.canonicalCustomBookId(
            null,
            Map.of("wise", 2),
            false
        ));
        assertEquals(null, CustomEnchantListener.canonicalCustomBookId(
            null,
            Map.of("wise", 2, "dash", 1),
            false
        ));
        assertEquals(null, CustomEnchantListener.canonicalCustomBookId(
            "wise",
            Map.of("dash", 1),
            false
        ));
        assertEquals(null, CustomEnchantListener.canonicalCustomBookId(
            "wise",
            Map.of("wise", 2),
            true
        ));
    }

    @Test
    void anvilLevelCombiningMatchesVanillaRulesWithoutOvercapping() {
        assertEquals(0, CustomEnchantListener.combinedEnchantLevel(0, 0, 3));
        assertEquals(1, CustomEnchantListener.combinedEnchantLevel(0, 1, 3));
        assertEquals(2, CustomEnchantListener.combinedEnchantLevel(1, 1, 3));
        assertEquals(3, CustomEnchantListener.combinedEnchantLevel(1, 3, 3));
        assertEquals(3, CustomEnchantListener.combinedEnchantLevel(3, 2, 3));
        assertEquals(3, CustomEnchantListener.combinedEnchantLevel(3, 3, 3));
        assertEquals(3, CustomEnchantListener.combinedEnchantLevel(99, 99, 3));
        assertEquals(1, CustomEnchantListener.combinedEnchantLevel(-5, 1, 3));
    }

    @Test
    void harvestingSelectsOnlyTheFirstEligibleCropDrop() {
        assertEquals(1, CustomEnchantListener.firstCropDropIndex(List.of(
            "STICK",
            "WHEAT",
            "WHEAT_SEEDS"
        )));
        assertEquals(0, CustomEnchantListener.firstCropDropIndex(List.of(
            "POTATO",
            "CARROT"
        )));
    }

    @Test
    void harvestingDoesNothingWhenThereIsNoCropDrop() {
        assertEquals(-1, CustomEnchantListener.firstCropDropIndex(List.of(
            "STICK",
            "COBBLESTONE"
        )));
        assertEquals(-1, CustomEnchantListener.firstCropDropIndex(List.of()));
        assertEquals(-1, CustomEnchantListener.firstCropDropIndex(null));
    }

    @Test
    void doubleJumpStaysArmedForTheWholeAirborneWindow() {
        assertEquals(CustomEnchantListener.DoubleJumpFlightAction.ARM,
            CustomEnchantListener.doubleJumpFlightAction(true, false, false, true));
        assertEquals(CustomEnchantListener.DoubleJumpFlightAction.KEEP_ARMED,
            CustomEnchantListener.doubleJumpFlightAction(true, true, true, false));
        assertEquals(CustomEnchantListener.DoubleJumpFlightAction.NONE,
            CustomEnchantListener.doubleJumpFlightAction(true, false, false, false));
        assertEquals(CustomEnchantListener.DoubleJumpFlightAction.NONE,
            CustomEnchantListener.doubleJumpFlightAction(true, false, true, true));
        assertEquals(CustomEnchantListener.DoubleJumpFlightAction.DISARM,
            CustomEnchantListener.doubleJumpFlightAction(false, true, true, false));
    }

    @Test
    void doubleJumpSteersWithoutDiscardingVerticalOrHorizontalMomentum() {
        Vector redirected = CustomEnchantListener.doubleJumpVelocity(
            new Vector(0.3, 0.42, 0.0),
            new Vector(0.0, 0.0, 1.0),
            0.75,
            0.82
        );
        assertTrue(redirected.getX() > 0.0);
        assertTrue(redirected.getZ() > 0.0);
        assertTrue(redirected.clone().setY(0.0).length() <= 0.75001);
        assertEquals(0.82, redirected.getY(), 0.00001);

        Vector rising = CustomEnchantListener.doubleJumpVelocity(
            new Vector(0.1, 1.1, 0.0),
            new Vector(1.0, 0.0, 0.0),
            0.75,
            0.82
        );
        assertEquals(1.1, rising.getY(), 0.00001);
    }
}
