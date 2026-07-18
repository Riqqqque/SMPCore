package me.rique.smpcore.story;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class StoryDialogueDeliveryTest {

    @Test
    void npcInteractionsNeverDuplicateDialogueIntoTheActionBar() {
        assertEquals("chat-only", StoryService.deliveryMode("both", "NPC_INTERACT"));
        assertEquals("chat-only", StoryService.deliveryMode("subtitles-only", "npc_interact"));
    }

    @Test
    void nonNpcDialogueKeepsItsConfiguredDeliveryMode() {
        assertEquals("both", StoryService.deliveryMode("both", "BOSS_ENTRANCE"));
        assertEquals("subtitles", StoryService.deliveryMode(" SUBTITLES ", "BOSS_PHASE"));
        assertEquals("chat-only", StoryService.deliveryMode(null, "REPLAY"));
    }
}
