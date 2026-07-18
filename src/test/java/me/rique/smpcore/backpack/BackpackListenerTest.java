package me.rique.smpcore.backpack;

import org.junit.jupiter.api.Test;

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
        assertTrue(BackpackListener.isSafeSerializedItemLength(2 * 1024 * 1024));
        assertFalse(BackpackListener.isSafeSerializedItemLength(-1));
        assertFalse(BackpackListener.isSafeSerializedItemLength((2 * 1024 * 1024) + 1));
        assertFalse(BackpackListener.isSafeSerializedItemLength(Integer.MAX_VALUE));
    }

    @Test
    void matchesOpenBackpacksByPersistentIdsInsteadOfObjectIdentity() {
        assertTrue(BackpackListener.matchesSessionIdentity("bag-1", "session-1", "bag-1", "session-1"));
        assertFalse(BackpackListener.matchesSessionIdentity("bag-1", "session-1", "bag-2", "session-1"));
        assertFalse(BackpackListener.matchesSessionIdentity("bag-1", "session-1", "bag-1", "session-2"));
        assertFalse(BackpackListener.matchesSessionIdentity("bag-1", "session-1", "bag-1", null));
        assertFalse(BackpackListener.matchesSessionIdentity(null, "session-1", "bag-1", "session-1"));
    }
}
