package me.rique.smpcore.item;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalvagingDepotListenerTest {

    @Test
    void onlyOneAvailableDepotCanExpandTheStation() {
        assertTrue(SalvagingDepotListener.allowsDepotPlacement(0, false, false));
        assertTrue(SalvagingDepotListener.allowsDepotPlacement(1, true, true));
        assertFalse(SalvagingDepotListener.allowsDepotPlacement(1, false, true));
        assertFalse(SalvagingDepotListener.allowsDepotPlacement(1, true, false));
        assertFalse(SalvagingDepotListener.allowsDepotPlacement(2, true, true));
    }

    @Test
    void largeDepotFacingAlwaysRunsAcrossTheChestPair() {
        assertTrue(SalvagingDepotListener.isValidPairFacing(1, 0, org.bukkit.block.BlockFace.NORTH));
        assertTrue(SalvagingDepotListener.isValidPairFacing(-1, 0, org.bukkit.block.BlockFace.SOUTH));
        assertTrue(SalvagingDepotListener.isValidPairFacing(0, 1, org.bukkit.block.BlockFace.EAST));
        assertTrue(SalvagingDepotListener.isValidPairFacing(0, -1, org.bukkit.block.BlockFace.WEST));
        assertFalse(SalvagingDepotListener.isValidPairFacing(1, 0, org.bukkit.block.BlockFace.EAST));
        assertFalse(SalvagingDepotListener.isValidPairFacing(0, 1, org.bukkit.block.BlockFace.NORTH));
        assertFalse(SalvagingDepotListener.isValidPairFacing(1, 1, org.bukkit.block.BlockFace.NORTH));
    }

    @Test
    void netheriteUpgradeComponentsCannotRoundBackIntoAFullIngot() {
        Map<Material, Integer> base = new LinkedHashMap<>();
        base.put(Material.DIAMOND, 3);
        base.put(Material.NETHERITE_SCRAP, 4);
        base.put(Material.GOLD_INGOT, 4);
        base.put(Material.STICK, 2);

        Map<Material, Integer> returned = SalvagingDepotListener.calculateReturnedMaterials(
            base,
            Material.DIAMOND,
            1,
            1.0D,
            0.66D
        );

        assertEquals(Map.of(
            Material.DIAMOND, 1,
            Material.NETHERITE_SCRAP, 2,
            Material.GOLD_INGOT, 2,
            Material.STICK, 1
        ), returned);
    }

    @Test
    void nearlyBrokenGearOnlyReturnsItsPrimaryScrap() {
        Map<Material, Integer> returned = SalvagingDepotListener.calculateReturnedMaterials(
            Map.of(Material.BREEZE_ROD, 1, Material.HEAVY_CORE, 1),
            Material.BREEZE_ROD,
            1,
            0.08D,
            0.66D
        );

        assertEquals(Map.of(Material.BREEZE_ROD, 1), returned);
    }

    @Test
    void fullyDamagedGearStillReturnsItsPrimaryScrap() {
        double durability = SalvagingDepotListener.salvageDurabilityFactor(100.0D, 100.0D);
        Map<Material, Integer> returned = SalvagingDepotListener.calculateReturnedMaterials(
            Map.of(Material.IRON_INGOT, 3, Material.STICK, 2),
            Material.IRON_INGOT,
            1,
            durability,
            0.66D
        );

        assertEquals(0.08D, durability, 0.000001D);
        assertEquals(Map.of(Material.IRON_INGOT, 1), returned);
        assertEquals(0.08D, SalvagingDepotListener.salvageDurabilityFactor(200.0D, 100.0D), 0.000001D);
        assertEquals(1.0D, SalvagingDepotListener.salvageDurabilityFactor(0.0D, 100.0D), 0.000001D);
    }

    @Test
    void zeroReturnRateNeverConsumesGear() {
        Map<Material, Integer> returned = SalvagingDepotListener.calculateReturnedMaterials(
            Map.of(Material.IRON_INGOT, 8),
            Material.IRON_INGOT,
            1,
            1.0D,
            0.0D
        );

        assertEquals(Map.of(), returned);
    }

    @Test
    void depotQueueAndCompletionBatchesKeepOneScanAtTheEarliestDeadline() {
        assertFalse(SalvagingDepotListener.shouldReplaceQueueScan(1_000L, true, 1_000L));
        assertFalse(SalvagingDepotListener.shouldReplaceQueueScan(1_000L, true, 1_500L));
        assertTrue(SalvagingDepotListener.shouldReplaceQueueScan(1_500L, true, 1_000L));
        assertTrue(SalvagingDepotListener.shouldReplaceQueueScan(null, true, 1_000L));
        assertTrue(SalvagingDepotListener.shouldReplaceQueueScan(1_000L, false, 1_500L));
    }

    @Test
    void hopperInputModesEnforceTheirDocumentedPolicy() {
        assertTrue(SalvagingDepotListener.acceptsHopperInput(
            SalvagingDepotListener.HopperInputMode.SALVAGE_ONLY,
            SalvagingDepotListener.HopperInputDisposition.SALVAGEABLE
        ));
        assertFalse(SalvagingDepotListener.acceptsHopperInput(
            SalvagingDepotListener.HopperInputMode.SALVAGE_ONLY,
            SalvagingDepotListener.HopperInputDisposition.DELETABLE
        ));
        assertTrue(SalvagingDepotListener.acceptsHopperInput(
            SalvagingDepotListener.HopperInputMode.ACCEPT_ALL,
            SalvagingDepotListener.HopperInputDisposition.PROTECTED
        ));
        assertTrue(SalvagingDepotListener.acceptsHopperInput(
            SalvagingDepotListener.HopperInputMode.GUARDED_TRASH,
            SalvagingDepotListener.HopperInputDisposition.DELETABLE
        ));
        assertFalse(SalvagingDepotListener.acceptsHopperInput(
            SalvagingDepotListener.HopperInputMode.GUARDED_TRASH,
            SalvagingDepotListener.HopperInputDisposition.PROTECTED
        ));
    }

    @Test
    void guardedTrashConservativelyProtectsValuableAndDataBearingMaterials() {
        assertTrue(SalvagingDepotListener.isConservativelyProtectedMaterial(Material.DIAMOND));
        assertTrue(SalvagingDepotListener.isConservativelyProtectedMaterial(Material.RAW_GOLD));
        assertTrue(SalvagingDepotListener.isConservativelyProtectedMaterial(Material.NETHERITE_INGOT));
        assertTrue(SalvagingDepotListener.isConservativelyProtectedMaterial(Material.ENCHANTED_BOOK));
        assertTrue(SalvagingDepotListener.isConservativelyProtectedMaterial(Material.PLAYER_HEAD));
        assertTrue(SalvagingDepotListener.isConservativelyProtectedMaterial(Material.WHITE_SHULKER_BOX));
        assertFalse(SalvagingDepotListener.isConservativelyProtectedMaterial(Material.COBBLESTONE));
        assertFalse(SalvagingDepotListener.isConservativelyProtectedMaterial(Material.ROTTEN_FLESH));
    }

    @Test
    void canceledHopperTransfersNeverReachTheCustomOutputMover() throws NoSuchMethodException {
        Method handler = SalvagingDepotListener.class.getMethod("onInventoryMoveItem", InventoryMoveItemEvent.class);
        EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

        assertEquals(EventPriority.HIGHEST, eventHandler.priority());
        assertTrue(eventHandler.ignoreCancelled());
    }

    @Test
    void inventoryCloseReconciliationDoesNotMutateAtMonitorPriority() throws NoSuchMethodException {
        Method handler = SalvagingDepotListener.class.getMethod("onInventoryClose", InventoryCloseEvent.class);
        EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

        assertEquals(EventPriority.HIGHEST, eventHandler.priority());
    }

    @Test
    void malformedProcessingStateIsNeverTreatedAsAValidPermanentLock() {
        UUID processId = UUID.randomUUID();

        assertFalse(SalvagingDepotListener.isValidProcessingState(null, 1_000L));
        assertFalse(SalvagingDepotListener.isValidProcessingState(processId, 0L));
        assertFalse(SalvagingDepotListener.isValidProcessingState(processId, -1L));
        assertTrue(SalvagingDepotListener.isValidProcessingState(processId, 1_000L));
    }

    @Test
    void depotSettingsRequireAPlayerToRemainNearby() {
        assertTrue(SalvagingDepotListener.isWithinSettingsRange(true, 64.0D));
        assertFalse(SalvagingDepotListener.isWithinSettingsRange(true, 64.0001D));
        assertFalse(SalvagingDepotListener.isWithinSettingsRange(false, 1.0D));
        assertFalse(SalvagingDepotListener.isWithinSettingsRange(true, Double.NaN));
    }
}
