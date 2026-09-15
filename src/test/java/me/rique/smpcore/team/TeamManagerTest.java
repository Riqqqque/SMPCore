package me.rique.smpcore.team;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamManagerTest {

    @Test
    void teamVaultOnlyOpensOutsideCombatEncounters() {
        assertNull(TeamManager.teamVaultRestriction(false, false));
        assertEquals(
            "Team storage is unavailable during an active boss fight.",
            TeamManager.teamVaultRestriction(true, false)
        );
        assertEquals(
            "Team storage is unavailable during a duel or while spectating one.",
            TeamManager.teamVaultRestriction(false, true)
        );
    }

    @Test
    void teamVaultCodecRejectsMalformedOrPathologicalSizes() {
        assertTrue(TeamManager.isSafeTeamVaultSlotCount(54, 54));
        assertTrue(TeamManager.isSafeTeamVaultSlotCount(0, 54));
        assertFalse(TeamManager.isSafeTeamVaultSlotCount(-1, 54));
        assertFalse(TeamManager.isSafeTeamVaultSlotCount(55, 54));
        assertFalse(TeamManager.isSafeTeamVaultSlotCount(54, 55));

        assertTrue(TeamManager.isSafeTeamVaultItemLength(1536 * 1024));
        assertFalse(TeamManager.isSafeTeamVaultItemLength(-1));
        assertFalse(TeamManager.isSafeTeamVaultItemLength((1536 * 1024) + 1));

        assertTrue(TeamManager.isSafeTeamVaultDataLength(1536L * 1024L));
        assertTrue(TeamManager.isSafeTeamVaultDataLength(54L * 24L * 1024L));
        assertFalse(TeamManager.isSafeTeamVaultDataLength(-1L));
        assertFalse(TeamManager.isSafeTeamVaultDataLength((1536L * 1024L) + 1L));
    }

    @Test
    void fullEmptyVaultEnvelopeRoundTripsWithoutTrailingData() throws Exception {
        byte[] payload = emptyVaultPayload(54, 0);

        assertEquals(54, TeamManager.deserializeTeamVaultData(payload, 54).length);
    }

    @Test
    void vaultDecoderFailsClosedOnBadFraming() {
        assertThrows(IOException.class, () -> TeamManager.deserializeTeamVaultData(emptyVaultPayload(55, 0), 54));
        assertThrows(IOException.class, () -> TeamManager.deserializeTeamVaultData(emptyVaultPayload(54, 1), 54));

        ByteBuffer truncated = ByteBuffer.allocate(Integer.BYTES * 2);
        truncated.putInt(1);
        truncated.putInt(20);
        assertThrows(IOException.class, () -> TeamManager.deserializeTeamVaultData(truncated.array(), 54));
    }

    @Test
    void formerTeammateKillBlockLastsThirtyDaysAndResetsAfterAnotherLeave() {
        long firstLeave = 1_000_000L;
        long firstExpiry = TeamManager.formerTeammateBlockUntil(firstLeave);
        assertEquals(firstLeave + (30L * 24L * 60L * 60L * 1000L), firstExpiry);
        assertTrue(TeamManager.isActiveKillProgressBlock(firstExpiry, firstExpiry - 1L));
        assertFalse(TeamManager.isActiveKillProgressBlock(firstExpiry, firstExpiry));

        long secondLeave = firstLeave + 5_000L;
        long resetExpiry = TeamManager.formerTeammateBlockUntil(secondLeave);
        assertTrue(resetExpiry > firstExpiry);
    }

    @Test
    void formerTeammatePairKeyIsSymmetricAndRejectsSelfKills() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(
            TeamManager.persistentPlayerPairKey(first, second),
            TeamManager.persistentPlayerPairKey(second, first)
        );
        assertEquals("", TeamManager.persistentPlayerPairKey(first, first));
    }

    @Test
    void teamJoinLockShowsTheFullRemainingDay() {
        assertEquals("24h 0m", TeamManager.formatTeamJoinLock(24L * 60L * 60L * 1000L));
        assertEquals("1m", TeamManager.formatTeamJoinLock(1L));
    }

    @Test
    void friendlyDamageNeedsConsentFromEveryAffectedTeam() {
        assertTrue(TeamManager.blocksFriendlyDamage(true, false, false, false));
        assertFalse(TeamManager.blocksFriendlyDamage(true, false, true, true));
        assertTrue(TeamManager.blocksFriendlyDamage(false, true, true, false));
        assertTrue(TeamManager.blocksFriendlyDamage(false, true, false, true));
        assertFalse(TeamManager.blocksFriendlyDamage(false, true, true, true));
        assertFalse(TeamManager.blocksFriendlyDamage(false, false, false, false));
    }

    @Test
    void ownerAndCaptainsHaveCaptainLevelAuthority() {
        assertTrue(TeamManager.hasCaptainAuthority(true, false));
        assertTrue(TeamManager.hasCaptainAuthority(false, true));
        assertTrue(TeamManager.hasCaptainAuthority(true, true));
        assertFalse(TeamManager.hasCaptainAuthority(false, false));
    }

    private byte[] emptyVaultPayload(int storedSlots, int trailingBytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + (Math.max(0, storedSlots) * Integer.BYTES) + trailingBytes);
        buffer.putInt(storedSlots);
        for (int slot = 0; slot < storedSlots; slot++) {
            buffer.putInt(0);
        }
        while (buffer.hasRemaining()) {
            buffer.put((byte) 1);
        }
        return buffer.array();
    }
}
