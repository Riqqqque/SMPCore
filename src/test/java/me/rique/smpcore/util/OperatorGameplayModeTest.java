package me.rique.smpcore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorGameplayModeTest {

    @Test
    void operatorsNeedStaffModeForPassiveGameplayBypasses() {
        assertFalse(OperatorGameplayMode.passiveBypassAllowed(true, true, false));
        assertTrue(OperatorGameplayMode.passiveBypassAllowed(true, true, true));
        assertFalse(OperatorGameplayMode.passiveBypassAllowed(true, false, true));
    }

    @Test
    void explicitlyPermittedNonOperatorsKeepTheirAssignedBypasses() {
        assertTrue(OperatorGameplayMode.passiveBypassAllowed(false, true, false));
        assertFalse(OperatorGameplayMode.passiveBypassAllowed(false, false, false));
    }
}
