package me.rique.smpcore.item;

import org.junit.jupiter.api.Test;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomEnchantListenerTest {

    @Test
    void anvilEnchantLoreFinalizersUseTheLastMutablePriority() throws NoSuchMethodException {
        EventHandler previewHandler = CustomEnchantListener.class
            .getDeclaredMethod("onPrepareAnvilLoreFinalized", PrepareAnvilEvent.class)
            .getAnnotation(EventHandler.class);
        EventHandler pickupHandler = CustomEnchantListener.class
            .getDeclaredMethod("onAnvilResultLorePickup", InventoryClickEvent.class)
            .getAnnotation(EventHandler.class);

        assertEquals(EventPriority.HIGHEST, previewHandler.priority());
        assertEquals(EventPriority.HIGHEST, pickupHandler.priority());
        assertTrue(pickupHandler.ignoreCancelled());
    }

    @Test
    void dashListensToVanillaPredictedAirClicks() throws NoSuchMethodException {
        EventHandler handler = CustomEnchantListener.class
            .getDeclaredMethod("onDashUse", PlayerInteractEvent.class)
            .getAnnotation(EventHandler.class);

        assertFalse(handler.ignoreCancelled());
        assertTrue(CustomEnchantListener.shouldHandleDashInteraction(
            Action.RIGHT_CLICK_AIR,
            EquipmentSlot.HAND,
            false,
            false,
            false
        ));
        assertFalse(CustomEnchantListener.shouldHandleDashInteraction(
            Action.RIGHT_CLICK_AIR,
            EquipmentSlot.OFF_HAND,
            false,
            false,
            false
        ));
    }

    @Test
    void normalRightClickRaisesAnOffhandShieldInsteadOfDashing() {
        assertFalse(CustomEnchantListener.shouldHandleDashInteraction(
            Action.RIGHT_CLICK_AIR,
            EquipmentSlot.HAND,
            false,
            false,
            true
        ));
        assertTrue(CustomEnchantListener.shouldHandleDashInteraction(
            Action.RIGHT_CLICK_AIR,
            EquipmentSlot.HAND,
            false,
            true,
            true
        ));
    }

    @Test
    void dashPreservesNormalBlockMenusUnlessThePlayerSneaks() {
        assertFalse(CustomEnchantListener.shouldHandleDashInteraction(
            Action.RIGHT_CLICK_BLOCK,
            EquipmentSlot.HAND,
            true,
            false,
            false
        ));
        assertTrue(CustomEnchantListener.shouldHandleDashInteraction(
            Action.RIGHT_CLICK_BLOCK,
            EquipmentSlot.HAND,
            true,
            true,
            true
        ));
        assertFalse(CustomEnchantListener.shouldHandleDashInteraction(
            Action.LEFT_CLICK_AIR,
            EquipmentSlot.HAND,
            false,
            false,
            false
        ));
    }

    @Test
    void customEnchantBooksMustBeSingleItemTransactions() {
        assertFalse(CustomEnchantListener.isValidCustomBookAmount(0));
        assertTrue(CustomEnchantListener.isValidCustomBookAmount(1));
        assertFalse(CustomEnchantListener.isValidCustomBookAmount(2));
        assertFalse(CustomEnchantListener.isValidCustomBookAmount(64));
    }

    @Test
    void managedEnchantsCannotLeakOntoTheWrongGearType() {
        assertTrue(CustomEnchantListener.isManagedEnchantCompatible("harvesting", Material.NETHERITE_HOE));
        assertFalse(CustomEnchantListener.isManagedEnchantCompatible("harvesting", Material.NETHERITE_SWORD));
        assertTrue(CustomEnchantListener.isManagedEnchantCompatible("dash", Material.DIAMOND_SWORD));
        assertFalse(CustomEnchantListener.isManagedEnchantCompatible("dash", Material.DIAMOND_HOE));
        assertTrue(CustomEnchantListener.isManagedEnchantCompatible("bulwark", Material.NETHERITE_CHESTPLATE));
        assertFalse(CustomEnchantListener.isManagedEnchantCompatible("bulwark", Material.NETHERITE_SWORD));
        assertTrue(CustomEnchantListener.isManagedEnchantCompatible("smelting_touch", Material.DIAMOND_PICKAXE));
        assertFalse(CustomEnchantListener.isManagedEnchantCompatible("smelting_touch", Material.DIAMOND_AXE));
        assertFalse(CustomEnchantListener.isManagedEnchantCompatible("missing", Material.DIAMOND_SWORD));
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

        assertEquals(CustomEnchantListener.CustomBookPayloadState.VALID,
            CustomEnchantListener.classifyCustomBookPayload("dash", Map.of("dash", 3), 1, false, false));
        assertEquals(CustomEnchantListener.CustomBookPayloadState.INVALID,
            CustomEnchantListener.classifyCustomBookPayload("dash", Map.of("dash", 4), 1, false, false));
    }

    @Test
    void dashLevelsGrantIndependentPersistentChargesWithoutSwapResets() {
        long now = 100_000L;
        long recharge = 15_000L;
        long[] uses = {now - 20_000L, now - 10_000L, now - 5_000L};

        CustomEnchantListener.DashChargeState levelThree =
            CustomEnchantListener.dashChargeState(3, uses, now, recharge);
        assertEquals(3, levelThree.maxCharges());
        assertEquals(1, levelThree.availableCharges());
        assertEquals(5_000L, levelThree.millisUntilNextCharge());
        assertEquals(2, levelThree.activeUses().size());

        CustomEnchantListener.DashChargeState levelOne =
            CustomEnchantListener.dashChargeState(1, uses, now, recharge);
        assertEquals(0, levelOne.availableCharges());
        assertEquals(10_000L, levelOne.millisUntilNextCharge());

        CustomEnchantListener.DashChargeState recharged =
            CustomEnchantListener.dashChargeState(3, uses, now + recharge, recharge);
        assertEquals(3, recharged.availableCharges());
        assertEquals(0L, recharged.millisUntilNextCharge());
        assertTrue(recharged.activeUses().isEmpty());
    }

    @Test
    void doubleJumpAndDashSpendSaturationBeforeVisibleHunger() {
        CustomEnchantListener.FoodCostState saturated =
            CustomEnchantListener.foodCostState(5.0f, 20, 2);
        assertTrue(saturated.canPay());
        assertEquals(3.0f, saturated.saturation(), 0.0001f);
        assertEquals(20, saturated.foodLevel());

        CustomEnchantListener.FoodCostState split =
            CustomEnchantListener.foodCostState(1.0f, 20, 2);
        assertTrue(split.canPay());
        assertEquals(0.0f, split.saturation(), 0.0001f);
        assertEquals(19, split.foodLevel());

        CustomEnchantListener.FoodCostState hungry =
            CustomEnchantListener.foodCostState(0.0f, 20, 2);
        assertTrue(hungry.canPay());
        assertEquals(18, hungry.foodLevel());
    }

    @Test
    void foodCostDoesNotSpendAnythingWhenItCannotCoverTheCost() {
        CustomEnchantListener.FoodCostState result =
            CustomEnchantListener.foodCostState(0.0f, 1, 2);
        assertFalse(result.canPay());
        assertEquals(0.0f, result.saturation(), 0.0001f);
        assertEquals(1, result.foodLevel());
    }

    @Test
    void applyingDoubleJumpFoodCostImmediatelySyncsTheClientHud() {
        AtomicReference<Float> saturation = new AtomicReference<>();
        AtomicInteger foodLevel = new AtomicInteger(-1);
        AtomicBoolean healthUpdateSent = new AtomicBoolean();
        Player player = (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) -> {
                if (method.getName().equals("setSaturation")) {
                    saturation.set((Float) args[0]);
                } else if (method.getName().equals("setFoodLevel")) {
                    foodLevel.set((Integer) args[0]);
                } else if (method.getName().equals("sendHealthUpdate") && (args == null || args.length == 0)) {
                    healthUpdateSent.set(true);
                }
                return null;
            }
        );

        CustomEnchantListener.applyFoodCost(
            player,
            new CustomEnchantListener.FoodCostState(true, 3.0F, 20)
        );

        assertEquals(3.0F, saturation.get(), 0.0001F);
        assertEquals(20, foodLevel.get());
        assertTrue(healthUpdateSent.get());
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
    void doubleJumpCannotRearmUntilItHasLeftTheGroundAndLanded() {
        CustomEnchantListener.DoubleJumpRecoveryPhase phase =
            CustomEnchantListener.DoubleJumpRecoveryPhase.WAITING_FOR_AIRBORNE;

        phase = CustomEnchantListener.advanceDoubleJumpRecovery(phase, true);
        assertEquals(CustomEnchantListener.DoubleJumpRecoveryPhase.WAITING_FOR_AIRBORNE, phase);

        phase = CustomEnchantListener.advanceDoubleJumpRecovery(phase, false);
        assertEquals(CustomEnchantListener.DoubleJumpRecoveryPhase.WAITING_FOR_LANDING, phase);

        phase = CustomEnchantListener.advanceDoubleJumpRecovery(phase, false);
        assertEquals(CustomEnchantListener.DoubleJumpRecoveryPhase.WAITING_FOR_LANDING, phase);

        assertNull(CustomEnchantListener.advanceDoubleJumpRecovery(phase, true));
    }

    @Test
    void doubleJumpCannotArmWhileSwimmingInLava() {
        assertFalse(CustomEnchantListener.isDoubleJumpMovementStateAllowed(
            false,
            false,
            false,
            true,
            false
        ));
        assertTrue(CustomEnchantListener.isDoubleJumpMovementStateAllowed(
            false,
            false,
            false,
            false,
            false
        ));
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
