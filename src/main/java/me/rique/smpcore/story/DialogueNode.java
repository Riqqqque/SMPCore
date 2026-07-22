package me.rique.smpcore.story;

import java.util.List;
import java.util.Map;

public record DialogueNode(
    String id,
    String speaker,
    String trigger,
    String context,
    int priority,
    boolean once,
    long cooldownMillis,
    StoryChapter chapter,
    String requiredMemory,
    String excludedMemory,
    Map<String, String> requiredFlags,
    List<Line> lines,
    List<String> actions
) {
    public DialogueNode {
        requiredFlags = requiredFlags == null ? Map.of() : Map.copyOf(requiredFlags);
        lines = lines == null ? List.of() : List.copyOf(lines);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public record Line(String text, long delayTicks, String sound) { }
}
