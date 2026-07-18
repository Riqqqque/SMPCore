package me.rique.smpcore.launch;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchAccessManagerTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VISITOR = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void parsesUniqueValidUuidsAndIgnoresBadEntries() {
        Set<UUID> parsed = LaunchAccessManager.parseAllowedUuids(List.of(
            OWNER.toString(),
            "not-a-uuid",
            "  " + OWNER + "  "
        ));

        assertEquals(Set.of(OWNER), parsed);
    }

    @Test
    void lockedGateOnlyAllowsConfiguredOwners() {
        assertFalse(LaunchAccessManager.shouldDeny(true, true, Set.of(OWNER), OWNER));
        assertTrue(LaunchAccessManager.shouldDeny(true, true, Set.of(OWNER), VISITOR));
        assertTrue(LaunchAccessManager.shouldDeny(true, true, Set.of(), OWNER));
    }

    @Test
    void openOrDisabledGateDoesNotBlockPlayers() {
        assertFalse(LaunchAccessManager.shouldDeny(true, false, Set.of(), VISITOR));
        assertFalse(LaunchAccessManager.shouldDeny(false, true, Set.of(), VISITOR));
    }
}
