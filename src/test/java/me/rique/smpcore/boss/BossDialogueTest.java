package me.rique.smpcore.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BossDialogueTest {

    @Test
    void everyProgressionBossHasCompleteDialogue() {
        assertEquals(BossBalance.progression().size(), BossDialogue.profiles().size());

        for (BossBalance.Profile boss : BossBalance.progression()) {
            BossDialogue.Profile dialogue = BossDialogue.profile(boss.bossId());
            assertFalse(dialogue.entranceLine().isBlank(), boss.bossId() + " needs an entrance line");
            assertFalse(dialogue.phaseLines().isEmpty(), boss.bossId() + " needs a phase line");
            assertFalse(dialogue.combatLines().isEmpty(), boss.bossId() + " needs combat lines");
            assertFalse(dialogue.defeatLine().isBlank(), boss.bossId() + " needs a defeat line");
        }
    }
}
