package me.rique.smpcore.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorruptionManagerTest {

    @Test
    void catalystStackMovesOnlyOneItemIntoEmptySlot() {
        CorruptionManager.CatalystSlotChange change = CorruptionManager.moveOneCatalystIntoEmptySlot(0, 4);

        assertEquals(1, change.slotAmount());
        assertEquals(3, change.sourceAmount());
        assertEquals(0, change.returnedAmount());
    }

    @Test
    void singleCatalystMovesCleanlyIntoEmptySlot() {
        CorruptionManager.CatalystSlotChange change = CorruptionManager.moveOneCatalystIntoEmptySlot(0, 1);

        assertEquals(1, change.slotAmount());
        assertEquals(0, change.sourceAmount());
        assertEquals(0, change.returnedAmount());
    }

    @Test
    void occupiedCatalystSlotDoesNotAcceptMoreItems() {
        CorruptionManager.CatalystSlotChange change = CorruptionManager.moveOneCatalystIntoEmptySlot(1, 4);

        assertEquals(1, change.slotAmount());
        assertEquals(4, change.sourceAmount());
        assertEquals(0, change.returnedAmount());
    }

    @Test
    void stackedCatalystSlotTrimsToOneAndReturnsOverflow() {
        CorruptionManager.CatalystSlotChange change = CorruptionManager.trimCatalystSlot(4);

        assertEquals(1, change.slotAmount());
        assertEquals(0, change.sourceAmount());
        assertEquals(3, change.returnedAmount());
    }

    @Test
    void singleCatalystSlotHasNoOverflow() {
        CorruptionManager.CatalystSlotChange change = CorruptionManager.trimCatalystSlot(1);

        assertEquals(1, change.slotAmount());
        assertEquals(0, change.sourceAmount());
        assertEquals(0, change.returnedAmount());
    }
}
