package me.rique.smpcore.story;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class DialogueEngineTest {
    @Test
    void highestPriorityValidNodeWins() {
        StoryProfile profile = new StoryProfile(UUID.randomUUID());
        DialogueNode low = node("low", 10, false, 0, null, Map.of());
        DialogueNode high = node("high", 100, false, 0, null, Map.of());
        assertEquals("high", DialogueEngine.select(List.of(low, high), profile.snapshot(), "NPC_INTERACT", "mira", 1_000).orElseThrow().id());
    }

    @Test
    void oneTimeNodeCannotRunTwice() {
        StoryProfile profile = new StoryProfile(UUID.randomUUID());
        DialogueNode node = node("once", 10, true, 0, null, Map.of());
        assertTrue(DialogueEngine.select(List.of(node), profile.snapshot(), "NPC_INTERACT", "mira", 1_000).isPresent());
        profile.markDialogueSeen("once");
        assertTrue(DialogueEngine.select(List.of(node), profile.snapshot(), "NPC_INTERACT", "mira", 2_000).isEmpty());
    }

    @Test
    void repeatableNodeRespectsCooldown() {
        StoryProfile profile = new StoryProfile(UUID.randomUUID());
        DialogueNode node = node("repeat", 10, false, 10_000, null, Map.of());
        profile.markDialogueTime("Mira", 5_000);
        assertTrue(DialogueEngine.select(List.of(node), profile.snapshot(), "NPC_INTERACT", "mira", 12_000).isEmpty());
        assertTrue(DialogueEngine.select(List.of(node), profile.snapshot(), "NPC_INTERACT", "mira", 15_000).isPresent());
    }

    @Test
    void requiredMemoryAndFlagsAreEnforced() {
        StoryProfile profile = new StoryProfile(UUID.randomUUID());
        DialogueNode node = node("conditional", 20, false, 0, "kael_the_ashen", Map.of("truth_known", "true"));
        assertTrue(DialogueEngine.select(List.of(node), profile.snapshot(), "NPC_INTERACT", "mira", 1_000).isEmpty());
        profile.unlockMemory("kael_the_ashen");
        profile.setFlag("truth_known", "true");
        assertTrue(DialogueEngine.select(List.of(node), profile.snapshot(), "NPC_INTERACT", "mira", 1_000).isPresent());
    }

    @Test
    void invalidCodexReferenceIsRejectedWithUsefulMessage() {
        DialogueNode invalid = new DialogueNode("bad", "Mira", "NPC_INTERACT", "mira", 1, true, 0, null,
            "", "", Map.of(), List.of(new DialogueNode.Line("line", 0, "")), List.of("unlockCodex:missing.entry"));
        List<StoryContent.ValidationError> errors = StoryContent.validateDialogueNodes(List.of(invalid), List.of("people.mira"));
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().message().contains("missing.entry"));
    }

    private static DialogueNode node(String id, int priority, boolean once, long cooldown, String memory, Map<String, String> flags) {
        return new DialogueNode(id, "Mira", "NPC_INTERACT", "mira", priority, once, cooldown, null,
            memory == null ? "" : memory, "", flags, List.of(new DialogueNode.Line("test", 0, "")), List.of());
    }
}
