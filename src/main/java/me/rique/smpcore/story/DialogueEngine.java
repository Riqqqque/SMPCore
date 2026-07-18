package me.rique.smpcore.story;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

public final class DialogueEngine {
    private DialogueEngine() { }

    public static Optional<DialogueNode> select(
        Collection<DialogueNode> nodes,
        StoryProfile.Snapshot profile,
        String trigger,
        String context,
        long now
    ) {
        if (nodes == null || profile == null) return Optional.empty();
        String wantedTrigger = normalize(trigger);
        String wantedContext = normalize(context);
        return nodes.stream()
            .filter(node -> node != null && !node.lines().isEmpty())
            .filter(node -> normalize(node.trigger()).equals(wantedTrigger))
            .filter(node -> node.context() == null || node.context().isBlank() || normalize(node.context()).equals(wantedContext))
            .filter(node -> !node.once() || !profile.seenDialogue().contains(StoryProfile.normalizeDotted(node.id())))
            .filter(node -> node.chapter() == null || node.chapter() == profile.chapter())
            .filter(node -> node.requiredMemory() == null || node.requiredMemory().isBlank()
                || profile.bossMemories().contains(StoryProfile.normalize(node.requiredMemory())))
            .filter(node -> node.excludedMemory() == null || node.excludedMemory().isBlank()
                || !profile.bossMemories().contains(StoryProfile.normalize(node.excludedMemory())))
            .filter(node -> node.requiredFlags().entrySet().stream().allMatch(entry ->
                String.valueOf(entry.getValue()).equalsIgnoreCase(profile.flags().get(StoryProfile.normalizeDotted(entry.getKey())))))
            .filter(node -> node.cooldownMillis() <= 0L
                || now - profile.lastDialogue().getOrDefault(StoryProfile.normalize(node.speaker()), 0L) >= node.cooldownMillis())
            .max(Comparator.comparingInt(DialogueNode::priority).thenComparing(DialogueNode::id, Comparator.reverseOrder()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
