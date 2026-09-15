package me.rique.smpcore.home;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllianceHomeManagerTest {

    @Test
    void allianceKeysAreOrderIndependentAndCaseInsensitive() {
        AllianceHomeManager.AllianceKey first =
            AllianceHomeManager.AllianceKey.of("The Honored Ones", "Night Watch");
        AllianceHomeManager.AllianceKey reversed =
            AllianceHomeManager.AllianceKey.of("night watch", "THE HONORED ONES");

        assertEquals(first, reversed);
        assertTrue(first.contains("night watch"));
        assertEquals("the honored ones", first.other("night watch"));
    }

    @Test
    void homeNamesStayCommandSafe() {
        assertEquals("shared_mine", AllianceHomeManager.normalizeHomeName(" Shared_Mine "));
        assertTrue(AllianceHomeManager.isValidHomeName("shared_mine"));
        assertTrue(AllianceHomeManager.isValidHomeName("base-2"));

        assertFalse(AllianceHomeManager.isValidHomeName(""));
        assertFalse(AllianceHomeManager.isValidHomeName("-base"));
        assertFalse(AllianceHomeManager.isValidHomeName("two words"));
        assertFalse(AllianceHomeManager.isValidHomeName("a".repeat(25)));
    }
}
