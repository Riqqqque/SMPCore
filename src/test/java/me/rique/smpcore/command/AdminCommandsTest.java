package me.rique.smpcore.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCommandsTest {

    @Test
    void missingModerationReasonUsesTheFallback() {
        assertEquals("No reason provided.", AdminCommands.normalizeModerationReason(null, "No reason provided."));
        assertEquals("No reason provided.", AdminCommands.normalizeModerationReason(" \t\n ", "No reason provided."));
    }

    @Test
    void moderationReasonIsSingleLineAndDropsUnsafeControls() {
        assertEquals(
            "Repeated harassment near spawn",
            AdminCommands.normalizeModerationReason("  Repeated\t harassment\nnear\u0000 spawn  ", "fallback")
        );
    }

    @Test
    void moderationReasonLimitCountsUnicodeCharactersNotUtf16Units() {
        assertFalse(AdminCommands.isModerationReasonTooLong("😀".repeat(256)));
        assertTrue(AdminCommands.isModerationReasonTooLong("😀".repeat(257)));
    }
}
