package me.rique.smpcore.backpack;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackpackListenerTest {

    @Test
    void normalizesLabelWhitespaceWithoutChangingVisibleText() {
        assertEquals("Mining Supplies", BackpackListener.normalizeBackpackSuffix("  Mining   Supplies  "));
        assertEquals("Nether <Loot>", BackpackListener.normalizeBackpackSuffix("Nether <Loot>"));
    }

    @Test
    void rejectsFormattingAndInvisibleControlCharacters() {
        assertNull(BackpackListener.normalizeBackpackSuffix("\u00a7cFake Rarity"));
        assertNull(BackpackListener.normalizeBackpackSuffix("Left\u202ERight"));
        assertNull(BackpackListener.normalizeBackpackSuffix("Line\nBreak"));
    }

    @Test
    void enforcesTheTwentyFourCharacterLimitByCodePoint() {
        assertEquals("123456789012345678901234", BackpackListener.normalizeBackpackSuffix("123456789012345678901234"));
        assertNull(BackpackListener.normalizeBackpackSuffix("1234567890123456789012345"));
    }

    @Test
    void rejectsHostileSerializedItemLengthsBeforeAllocating() {
        assertTrue(BackpackListener.isSafeSerializedItemLength(0));
        assertTrue(BackpackListener.isSafeSerializedItemLength(1536 * 1024));
        assertFalse(BackpackListener.isSafeSerializedItemLength(-1));
        assertFalse(BackpackListener.isSafeSerializedItemLength((1536 * 1024) + 1));
        assertFalse(BackpackListener.isSafeSerializedItemLength(Integer.MAX_VALUE));
    }

    @Test
    void rejectsHostileTotalBackpackPayloadsBeforeAllocating() {
        assertTrue(BackpackListener.isSafeSerializedBackpackLength(0));
        assertTrue(BackpackListener.isSafeSerializedBackpackLength(1536L * 1024L));
        assertFalse(BackpackListener.isSafeSerializedBackpackLength(-1));
        assertFalse(BackpackListener.isSafeSerializedBackpackLength((1536L * 1024L) + 1L));
    }

    @Test
    void matchesOpenBackpacksByPersistentIdsInsteadOfObjectIdentity() {
        assertTrue(BackpackListener.matchesSessionIdentity("bag-1", "session-1", "bag-1", "session-1"));
        assertFalse(BackpackListener.matchesSessionIdentity("bag-1", "session-1", "bag-2", "session-1"));
        assertFalse(BackpackListener.matchesSessionIdentity("bag-1", "session-1", "bag-1", "session-2"));
        assertFalse(BackpackListener.matchesSessionIdentity("bag-1", "session-1", "bag-1", null));
        assertFalse(BackpackListener.matchesSessionIdentity(null, "session-1", "bag-1", "session-1"));
    }

    @Test
    void keepInventoryDomainsFinalizeOpenBackpacksIntoThePlayerInventory() {
        assertFalse(BackpackListener.keepsBackpackInInventory(false, false, false));
        assertTrue(BackpackListener.keepsBackpackInInventory(true, false, false));
        assertTrue(BackpackListener.keepsBackpackInInventory(false, true, false));
        assertTrue(BackpackListener.keepsBackpackInInventory(false, false, true));
    }

    @Test
    void onlyRepeatedBackpackIdsAreRekeyed() {
        Set<String> seen = new HashSet<>();
        assertFalse(BackpackListener.needsBackpackRekey(seen, "bag-a"));
        assertFalse(BackpackListener.needsBackpackRekey(seen, "bag-b"));
        assertTrue(BackpackListener.needsBackpackRekey(seen, "bag-a"));
        assertFalse(BackpackListener.needsBackpackRekey(seen, null));
        assertFalse(BackpackListener.needsBackpackRekey(seen, ""));
    }

    @Test
    void freshBackpackIdsNeverReuseReservedIdentities() {
        Set<String> reserved = new HashSet<>();
        for (int i = 0; i < 256; i++) {
            String id = BackpackListener.freshBackpackId(reserved);
            assertTrue(reserved.contains(id));
            assertEquals(i + 1, reserved.size());
        }
    }

    @Test
    void copiedBackpackIdsReceiveEmptyIndependentStorage() {
        var contents = BackpackListener.emptyContentsForCopiedBackpack(54);
        assertEquals(54, contents.length);
        for (var item : contents) {
            assertNull(item);
        }
    }

    @Test
    void normalizedSingleBackpacksKeepTheirExistingMetadata() {
        assertTrue(BackpackListener.canNormalizeBackpackInPlace(true, 1, Material.FLOWER_POT));
        assertFalse(BackpackListener.canNormalizeBackpackInPlace(true, 2, Material.FLOWER_POT));
        assertFalse(BackpackListener.canNormalizeBackpackInPlace(false, 1, Material.FLOWER_POT));
        assertFalse(BackpackListener.canNormalizeBackpackInPlace(true, 1, Material.MINECART));
    }

    @Test
    void enderChestWrappersForTheSamePlayerAreOnlyScannedOnce() {
        UUID owner = UUID.randomUUID();
        assertTrue(BackpackListener.sameLogicalPlayerInventory(
            owner, "ENDER_CHEST", owner, "ENDER_CHEST"
        ));
        assertTrue(BackpackListener.sameLogicalPlayerInventory(
            owner, "PLAYER", owner, "PLAYER"
        ));
        assertFalse(BackpackListener.sameLogicalPlayerInventory(
            owner, "ENDER_CHEST", UUID.randomUUID(), "ENDER_CHEST"
        ));
        assertFalse(BackpackListener.sameLogicalPlayerInventory(
            owner, "CHEST", owner, "CHEST"
        ));
    }
}
