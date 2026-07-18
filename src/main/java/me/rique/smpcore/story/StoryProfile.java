package me.rique.smpcore.story;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StoryProfile {
    public static final int CURRENT_VERSION = 1;

    private static final List<String> BOSS_ORDER = List.of(
        "yule_the_minion", "kael_the_ashen", "vesper_the_widow_queen",
        "mirewood_the_root_tyrant", "nereida_the_abyss_mother", "iron_saint",
        "aurelion_the_rift_seraph", "morvessa_the_runebloom_witch",
        "voralith_the_crimson_warden", "corrupted_oathkeeper"
    );

    private final UUID playerId;
    private int storyVersion;
    private StoryChapter chapter;
    private String mainStage;
    private final Set<String> bossMemories;
    private final Set<String> codexEntries;
    private final Set<String> seenDialogue;
    private final Set<String> processedEvents;
    private final Map<String, String> flags;
    private final Map<String, Long> lastDialogue;
    private int awakeningUses;
    private int corruptionUses;
    private int corruptionFailures;
    private StoryAlignment alignment;
    private volatile boolean dirty;
    private volatile long revision;

    public StoryProfile(UUID playerId) {
        this(playerId, CURRENT_VERSION, StoryChapter.PROLOGUE, "PROLOGUE_FIND_MIRA",
            Set.of(), Set.of("oath.overview", "people.veilward", "veilmark.overview"), Set.of(), Set.of(),
            Map.of(), Map.of(), 0, 0, 0, StoryAlignment.UNDECIDED);
    }

    public StoryProfile(
        UUID playerId,
        int storyVersion,
        StoryChapter chapter,
        String mainStage,
        Collection<String> bossMemories,
        Collection<String> codexEntries,
        Collection<String> seenDialogue,
        Collection<String> processedEvents,
        Map<String, String> flags,
        Map<String, Long> lastDialogue,
        int awakeningUses,
        int corruptionUses,
        int corruptionFailures,
        StoryAlignment alignment
    ) {
        this.playerId = playerId;
        this.storyVersion = Math.max(0, storyVersion);
        this.chapter = chapter == null ? StoryChapter.PROLOGUE : chapter;
        this.mainStage = clean(mainStage, "PROLOGUE_FIND_MIRA");
        this.bossMemories = cleanSet(bossMemories);
        this.codexEntries = cleanSet(codexEntries);
        this.seenDialogue = cleanSet(seenDialogue);
        this.processedEvents = cleanSet(processedEvents);
        this.flags = cleanMap(flags);
        this.lastDialogue = cleanLongMap(lastDialogue);
        this.awakeningUses = Math.max(0, awakeningUses);
        this.corruptionUses = Math.max(0, corruptionUses);
        this.corruptionFailures = Math.max(0, corruptionFailures);
        this.alignment = alignment == null ? StoryAlignment.UNDECIDED : alignment;
    }

    public UUID playerId() { return playerId; }
    public int storyVersion() { return storyVersion; }
    public StoryChapter chapter() { return chapter; }
    public String mainStage() { return mainStage; }
    public Set<String> bossMemories() { return Collections.unmodifiableSet(bossMemories); }
    public Set<String> codexEntries() { return Collections.unmodifiableSet(codexEntries); }
    public Set<String> seenDialogue() { return Collections.unmodifiableSet(seenDialogue); }
    public Set<String> processedEvents() { return Collections.unmodifiableSet(processedEvents); }
    public Map<String, String> flags() { return Collections.unmodifiableMap(flags); }
    public Map<String, Long> lastDialogue() { return Collections.unmodifiableMap(lastDialogue); }
    public int awakeningUses() { return awakeningUses; }
    public int corruptionUses() { return corruptionUses; }
    public int corruptionFailures() { return corruptionFailures; }
    public StoryAlignment alignment() { return alignment; }
    public boolean dirty() { return dirty; }
    public long revision() { return revision; }

    public synchronized void markClean(long savedRevision) {
        if (revision == savedRevision) dirty = false;
    }

    private synchronized void markDirty() {
        revision++;
        dirty = true;
    }

    public boolean migrate() {
        if (storyVersion >= CURRENT_VERSION) return false;
        storyVersion = CURRENT_VERSION;
        codexEntries.add("oath.overview");
        codexEntries.add("people.veilward");
        codexEntries.add("veilmark.overview");
        recomputeChapter();
        markDirty();
        return true;
    }

    public boolean unlockMemory(String bossId) {
        String id = normalize(bossId);
        if (!BOSS_ORDER.contains(id) || !bossMemories.add(id)) return false;
        codexEntries.add("witnesses." + id);
        codexEntries.add("memories." + id);
        recomputeChapter();
        markDirty();
        return true;
    }

    public boolean unlockEntry(String entryId) {
        String id = normalizeDotted(entryId);
        if (id.isBlank() || !codexEntries.add(id)) return false;
        markDirty();
        return true;
    }

    public boolean lockEntry(String entryId) {
        String id = normalizeDotted(entryId);
        if (id.startsWith("oath.overview") || id.startsWith("people.veilward") || id.startsWith("veilmark.overview")) return false;
        boolean changed = codexEntries.remove(id);
        if (id.startsWith("memories.")) bossMemories.remove(id.substring("memories.".length()));
        if (changed) markDirty();
        return changed;
    }

    public boolean markDialogueSeen(String nodeId) {
        String id = normalizeDotted(nodeId);
        if (id.isBlank() || !seenDialogue.add(id)) return false;
        markDirty();
        return true;
    }

    public void forgetDialogue(String nodeId) {
        if (seenDialogue.remove(normalizeDotted(nodeId))) markDirty();
    }

    public boolean claimEvent(String eventKey) {
        String id = clean(eventKey, "");
        if (id.isBlank() || !processedEvents.add(id)) return false;
        if (processedEvents.size() > 512) {
            List<String> oldest = new ArrayList<>(processedEvents);
            oldest.subList(0, processedEvents.size() - 384).forEach(processedEvents::remove);
        }
        markDirty();
        return true;
    }

    public String flag(String key) { return flags.get(normalizeDotted(key)); }
    public boolean flagBoolean(String key) { return Boolean.parseBoolean(flag(key)); }

    public void setFlag(String key, String value) {
        String safeKey = normalizeDotted(key);
        if (safeKey.isBlank()) return;
        String safeValue = clean(value, "true");
        if (!safeValue.equals(flags.put(safeKey, safeValue))) markDirty();
    }

    public void setStage(String stage) {
        String safe = clean(stage, mainStage).toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (!safe.equals(mainStage)) {
            mainStage = safe;
            markDirty();
        }
    }

    public void setChapter(StoryChapter chapter) {
        StoryChapter safe = chapter == null ? StoryChapter.PROLOGUE : chapter;
        if (safe != this.chapter) {
            this.chapter = safe;
            markDirty();
        }
    }

    public long lastDialogueAt(String speaker) { return lastDialogue.getOrDefault(normalize(speaker), 0L); }
    public void markDialogueTime(String speaker, long now) {
        lastDialogue.put(normalize(speaker), Math.max(0L, now));
        markDirty();
    }

    public void recordAwakening() {
        awakeningUses++;
        if (awakeningUses >= 5) flags.put("leans_mend", "true");
        updateBalancedUnderstanding();
        markDirty();
    }

    public void recordCorruption(boolean destructiveFailure) {
        if (destructiveFailure) corruptionFailures++; else corruptionUses++;
        if (corruptionUses >= 5) flags.put("leans_sever", "true");
        updateBalancedUnderstanding();
        markDirty();
    }

    public boolean chooseAlignment(StoryAlignment choice) {
        if (choice == null || choice == StoryAlignment.UNDECIDED || alignment != StoryAlignment.UNDECIDED) return false;
        alignment = choice;
        chapter = StoryChapter.EPILOGUE;
        mainStage = "EPILOGUE_SPEAK_TO_SILAS";
        flags.put("final_choice_pending", "false");
        codexEntries.add("oath.eleventh_oath");
        codexEntries.add("oath.alignment_" + choice.name().toLowerCase(Locale.ROOT));
        codexEntries.add("threads.the_unwritten");
        markDirty();
        return true;
    }

    public void reset(boolean keepBossHistory) {
        Set<String> memories = keepBossHistory ? new LinkedHashSet<>(bossMemories) : Set.of();
        bossMemories.clear();
        bossMemories.addAll(memories);
        codexEntries.clear();
        codexEntries.addAll(List.of("oath.overview", "people.veilward", "veilmark.overview"));
        for (String memory : bossMemories) {
            codexEntries.add("witnesses." + memory);
            codexEntries.add("memories." + memory);
        }
        seenDialogue.clear();
        processedEvents.clear();
        flags.clear();
        lastDialogue.clear();
        awakeningUses = 0;
        corruptionUses = 0;
        corruptionFailures = 0;
        alignment = StoryAlignment.UNDECIDED;
        mainStage = "PROLOGUE_FIND_MIRA";
        chapter = StoryChapter.PROLOGUE;
        recomputeChapter();
        storyVersion = CURRENT_VERSION;
        markDirty();
    }

    public Snapshot snapshot() {
        return new Snapshot(playerId, storyVersion, chapter, mainStage, Set.copyOf(bossMemories), Set.copyOf(codexEntries),
            Set.copyOf(seenDialogue), Set.copyOf(processedEvents), Map.copyOf(flags), Map.copyOf(lastDialogue),
            awakeningUses, corruptionUses, corruptionFailures, alignment);
    }

    private void updateBalancedUnderstanding() {
        if (awakeningUses > 0 && corruptionUses > 0) flags.put("understands_both", "true");
    }

    private void recomputeChapter() {
        if (alignment != StoryAlignment.UNDECIDED) {
            chapter = StoryChapter.EPILOGUE;
            return;
        }
        int farthest = -1;
        for (String memory : bossMemories) farthest = Math.max(farthest, BOSS_ORDER.indexOf(memory));
        StoryChapter derived = switch (farthest) {
            case 9 -> StoryChapter.ACT_5;
            case 7, 8 -> StoryChapter.ACT_4;
            case 5, 6 -> StoryChapter.ACT_3;
            case 2, 3, 4 -> StoryChapter.ACT_2;
            case 0, 1 -> StoryChapter.ACT_1;
            default -> StoryChapter.PROLOGUE;
        };
        if (derived.ordinal() > chapter.ordinal()) chapter = derived;
    }

    public static String encodeSet(Collection<String> values) {
        if (values == null || values.isEmpty()) return "";
        return values.stream().map(StoryProfile::encode).sorted().reduce((a, b) -> a + ";" + b).orElse("");
    }

    public static Set<String> decodeSet(String value) {
        Set<String> out = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return out;
        for (String part : value.split(";")) if (!part.isBlank()) out.add(decode(part));
        return out;
    }

    public static String encodeMap(Map<String, ?> values) {
        if (values == null || values.isEmpty()) return "";
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(entry -> encode(entry.getKey()) + ":" + encode(String.valueOf(entry.getValue())))
            .reduce((a, b) -> a + ";" + b).orElse("");
    }

    public static Map<String, String> decodeMap(String value) {
        Map<String, String> out = new LinkedHashMap<>();
        if (value == null || value.isBlank()) return out;
        for (String part : value.split(";")) {
            int split = part.indexOf(':');
            if (split > 0) out.put(decode(part.substring(0, split)), decode(part.substring(split + 1)));
        }
        return out;
    }

    public static Map<String, Long> decodeLongMap(String value) {
        Map<String, Long> out = new LinkedHashMap<>();
        decodeMap(value).forEach((key, raw) -> {
            try { out.put(key, Long.parseLong(raw)); } catch (NumberFormatException ignored) { }
        });
        return out;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(clean(value, "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static Set<String> cleanSet(Collection<String> values) {
        Set<String> out = new LinkedHashSet<>();
        if (values != null) values.stream().map(StoryProfile::normalizeDotted).filter(value -> !value.isBlank()).forEach(out::add);
        return out;
    }

    private static Map<String, String> cleanMap(Map<String, String> values) {
        Map<String, String> out = new LinkedHashMap<>();
        if (values != null) values.forEach((key, value) -> out.put(normalizeDotted(key), clean(value, "")));
        return out;
    }

    private static Map<String, Long> cleanLongMap(Map<String, Long> values) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (values != null) values.forEach((key, value) -> out.put(normalize(key), Math.max(0L, value == null ? 0L : value)));
        return out;
    }

    static String normalize(String value) {
        return clean(value, "").toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    static String normalizeDotted(String value) {
        return normalize(value).replaceAll("[^a-z0-9_.:]", "");
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record Snapshot(
        UUID playerId, int storyVersion, StoryChapter chapter, String mainStage, Set<String> bossMemories,
        Set<String> codexEntries, Set<String> seenDialogue, Set<String> processedEvents, Map<String, String> flags,
        Map<String, Long> lastDialogue, int awakeningUses, int corruptionUses, int corruptionFailures,
        StoryAlignment alignment
    ) { }
}
