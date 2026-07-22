package me.rique.smpcore.story;

import me.rique.smpcore.SMPCore;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class StoryContent {
    private static final Map<String, String> DEFAULT_NPC_MAPPINGS = Map.ofEntries(
        Map.entry("spawn_guide", "mira"),
        Map.entry("mayor", "mayor_bah"),
        Map.entry("dungeon_keeper", "malakar"),
        Map.entry("gear_expert", "orin"),
        Map.entry("corruption_warden", "veyr"),
        Map.entry("reforger", "brannik"),
        Map.entry("priest", "father_aldren"),
        Map.entry("goblin_hunter", "grikk"),
        Map.entry("miner", "torren"),
        Map.entry("farmer", "rowan"),
        Map.entry("witch", "vespera"),
        Map.entry("overseer", "veil_overseer"),
        Map.entry("brewmaster", "bram"),
        Map.entry("cardsharp", "rook"),
        Map.entry("dealer", "silas"),
        Map.entry("beastwarden", "kael"),
        Map.entry("bossbroker", "mogrik"),
        Map.entry("black_marketeer", "sable"),
        Map.entry("fisher", "corin")
    );
    public static final List<String> FILES = List.of(
        "story.yml", "story-dialogue.yml", "story-codex.yml", "story-bosses.yml", "story-ambient.yml"
    );

    private final boolean enabled;
    private final String dialogueMode;
    private final long choiceTimeoutMillis;
    private final long npcRepeatCooldownMillis;
    private final double lowHealthThreshold;
    private final List<DialogueNode> dialogueNodes;
    private final Map<String, BossText> bosses;
    private final Map<String, CodexCategory> categories;
    private final Map<String, CodexEntry> entries;
    private final Map<String, AmbientEffect> ambientEffects;
    private final Map<String, String> npcMappings;

    private StoryContent(
        boolean enabled,
        String dialogueMode,
        long choiceTimeoutMillis,
        long npcRepeatCooldownMillis,
        double lowHealthThreshold,
        List<DialogueNode> dialogueNodes,
        Map<String, BossText> bosses,
        Map<String, CodexCategory> categories,
        Map<String, CodexEntry> entries,
        Map<String, AmbientEffect> ambientEffects,
        Map<String, String> npcMappings
    ) {
        this.enabled = enabled;
        this.dialogueMode = dialogueMode;
        this.choiceTimeoutMillis = choiceTimeoutMillis;
        this.npcRepeatCooldownMillis = npcRepeatCooldownMillis;
        this.lowHealthThreshold = lowHealthThreshold;
        this.dialogueNodes = List.copyOf(dialogueNodes);
        this.bosses = Map.copyOf(bosses);
        this.categories = Map.copyOf(categories);
        this.entries = Map.copyOf(entries);
        this.ambientEffects = Map.copyOf(ambientEffects);
        this.npcMappings = Map.copyOf(npcMappings);
    }

    public static StoryContent load(SMPCore plugin) {
        for (String name : FILES) {
            File file = new File(plugin.getDataFolder(), name);
            if (!file.isFile()) plugin.saveResource(name, false);
        }

        YamlConfiguration core = load(plugin, "story.yml");
        if (core.getBoolean("dialogue.sync-bundled-content", true)) {
            refreshVersionedResource(plugin, "story-dialogue.yml");
        }
        YamlConfiguration dialogue = load(plugin, "story-dialogue.yml");
        YamlConfiguration codex = load(plugin, "story-codex.yml");
        YamlConfiguration bossConfig = load(plugin, "story-bosses.yml");
        YamlConfiguration ambient = load(plugin, "story-ambient.yml");

        List<DialogueNode> nodes = parseDialogue(dialogue);
        Map<String, BossText> bosses = parseBosses(bossConfig);
        Map<String, CodexCategory> categories = parseCategories(codex);
        Map<String, CodexEntry> entries = parseEntries(codex, categories.keySet());
        List<ValidationError> dialogueErrors = validateDialogueNodes(nodes, entries.keySet());
        if (!dialogueErrors.isEmpty()) {
            Set<String> invalidIds = dialogueErrors.stream().map(ValidationError::nodeId).collect(java.util.stream.Collectors.toSet());
            nodes.removeIf(node -> invalidIds.contains(node.id()));
            dialogueErrors.forEach(error -> plugin.getLogger().warning("Story dialogue " + error.nodeId() + ": " + error.message()));
        }
        Map<String, AmbientEffect> ambientEffects = parseAmbient(ambient);
        Map<String, String> npcMappings = new LinkedHashMap<>(stringMap(core.getConfigurationSection("mappings.npcs")));
        DEFAULT_NPC_MAPPINGS.forEach(npcMappings::putIfAbsent);

        if (nodes.isEmpty()) plugin.getLogger().warning("The Eleventh Oath has no dialogue nodes. Check story-dialogue.yml.");
        if (bosses.size() < 10) plugin.getLogger().warning("The Eleventh Oath has " + bosses.size() + "/10 boss mappings. Missing bosses will use existing dialogue.");
        if (categories.isEmpty() || entries.isEmpty()) plugin.getLogger().warning("The Eleventh Oath journal is missing categories or entries.");

        return new StoryContent(
            core.getBoolean("enabled", true),
            core.getString("dialogue.mode", "both").toLowerCase(Locale.ROOT),
            Math.max(15_000L, core.getLong("dialogue.choice-timeout-seconds", 90L) * 1000L),
            Math.max(0L, core.getLong("dialogue.npc-repeat-cooldown-seconds", 600L) * 1000L),
            Math.clamp(bossConfig.getDouble("low-health-threshold", 0.20), 0.05, 0.50),
            nodes, bosses, categories, entries, ambientEffects, npcMappings
        );
    }

    private static YamlConfiguration load(SMPCore plugin, String name) {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
    }

    private static void refreshVersionedResource(SMPCore plugin, String name) {
        File installedFile = new File(plugin.getDataFolder(), name);
        try (InputStream input = plugin.getResource(name)) {
            if (input == null || !installedFile.isFile()) {
                return;
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                new InputStreamReader(input, StandardCharsets.UTF_8)
            );
            int bundledVersion = bundled.getInt("content-version", 0);
            YamlConfiguration installed = YamlConfiguration.loadConfiguration(installedFile);
            int installedVersion = installed.getInt("content-version", 0);
            if (bundledVersion <= installedVersion) {
                return;
            }

            File backup = new File(plugin.getDataFolder(), name + ".v" + installedVersion + ".backup");
            Files.copy(installedFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.saveResource(name, true);
            plugin.getLogger().info("Updated " + name + " to content version " + bundledVersion
                + "; previous copy saved as " + backup.getName() + ".");
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not update bundled " + name + ": " + ex.getMessage());
        }
    }

    private static List<DialogueNode> parseDialogue(YamlConfiguration config) {
        List<DialogueNode> out = new ArrayList<>();
        ConfigurationSection root = config.getConfigurationSection("dialogues");
        if (root == null) return out;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            List<DialogueNode.Line> lines = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("lines")) {
                Object textValue = raw.get("text");
                String text = textValue == null ? "" : String.valueOf(textValue).trim();
                if (text.isBlank()) continue;
                long delay = number(raw.get("delayTicks"), lines.isEmpty() ? 0L : 24L);
                Object soundValue = raw.get("sound");
                lines.add(new DialogueNode.Line(text, Math.max(0L, delay), soundValue == null ? "" : String.valueOf(soundValue)));
            }
            if (lines.isEmpty()) {
                for (String text : section.getStringList("text")) {
                    if (!text.isBlank()) lines.add(new DialogueNode.Line(text, lines.isEmpty() ? 0L : 24L, ""));
                }
            }
            StoryChapter requiredChapter = section.contains("conditions.chapter")
                ? StoryChapter.parse(section.getString("conditions.chapter")) : null;
            out.add(new DialogueNode(
                id,
                section.getString("speaker", "Narrator"),
                section.getString("trigger", "MANUAL_COMMAND"),
                section.getString("context", ""),
                section.getInt("priority", 0),
                section.getBoolean("once", false),
                Math.max(0L, section.getLong("cooldown-seconds", 0L) * 1000L),
                requiredChapter,
                section.getString("conditions.has-memory", ""),
                section.getString("conditions.missing-memory", ""),
                stringMap(section.getConfigurationSection("conditions.flags")),
                lines,
                section.getStringList("actions")
            ));
        }
        return out;
    }

    private static Map<String, BossText> parseBosses(YamlConfiguration config) {
        Map<String, BossText> out = new LinkedHashMap<>();
        ConfigurationSection root = config.getConfigurationSection("bosses");
        if (root == null) return out;
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) continue;
            String id = StoryProfile.normalize(rawId);
            out.put(id, new BossText(
                id,
                section.getString("display", id),
                section.getString("entrance", ""),
                section.getStringList("phases"),
                section.getString("low-health", ""),
                section.getString("defeat", ""),
                section.getString("memory", ""),
                section.getString("memory-entry", "memories." + id)
            ));
        }
        return out;
    }

    private static Map<String, CodexCategory> parseCategories(YamlConfiguration config) {
        Map<String, CodexCategory> out = new LinkedHashMap<>();
        ConfigurationSection root = config.getConfigurationSection("categories");
        if (root == null) return out;
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) continue;
            String id = StoryProfile.normalize(rawId);
            out.put(id, new CodexCategory(id, section.getString("name", rawId), material(section.getString("icon"), Material.BOOK),
                section.getString("description", ""), section.getInt("order", out.size())));
        }
        return out;
    }

    private static Map<String, CodexEntry> parseEntries(YamlConfiguration config, Collection<String> categories) {
        Map<String, CodexEntry> out = new LinkedHashMap<>();
        ConfigurationSection root = config.getConfigurationSection("entries");
        if (root == null) return out;
        for (String rawId : root.getKeys(true)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null || !section.contains("name") || !section.contains("category")) continue;
            String id = StoryProfile.normalizeDotted(rawId);
            String category = StoryProfile.normalize(section.getString("category", id.contains(".") ? id.substring(0, id.indexOf('.')) : "oath"));
            if (!categories.contains(category)) continue;
            out.put(id, new CodexEntry(id, category, section.getString("name", rawId),
                material(section.getString("icon"), Material.PAPER), section.getStringList("text"), section.getInt("order", out.size())));
        }
        return out;
    }

    private static Map<String, AmbientEffect> parseAmbient(YamlConfiguration config) {
        Map<String, AmbientEffect> out = new LinkedHashMap<>();
        ConfigurationSection root = config.getConfigurationSection("memories");
        if (root == null) return out;
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) continue;
            String id = StoryProfile.normalize(rawId);
            out.put(id, new AmbientEffect(section.getString("sound", ""), section.getString("particle", ""),
                (float) section.getDouble("volume", 0.7), (float) section.getDouble("pitch", 0.8),
                Math.max(0, section.getInt("particle-count", 18))));
        }
        return out;
    }

    public static List<ValidationError> validateDialogueNodes(List<DialogueNode> nodes, Collection<String> entryIds) {
        List<ValidationError> errors = new ArrayList<>();
        Set<String> knownEntries = new HashSet<>();
        if (entryIds != null) entryIds.stream().map(StoryProfile::normalizeDotted).forEach(knownEntries::add);
        Set<String> seenIds = new HashSet<>();
        if (nodes == null) return List.of(new ValidationError("<registry>", "dialogue list is missing"));
        for (DialogueNode node : nodes) {
            if (node == null) {
                errors.add(new ValidationError("<null>", "node is null"));
                continue;
            }
            String id = StoryProfile.normalizeDotted(node.id());
            if (id.isBlank()) errors.add(new ValidationError("<blank>", "node id is blank"));
            else if (!seenIds.add(id)) errors.add(new ValidationError(node.id(), "duplicate node id"));
            if (node.speaker() == null || node.speaker().isBlank()) errors.add(new ValidationError(node.id(), "speaker is missing"));
            if (node.trigger() == null || node.trigger().isBlank()) errors.add(new ValidationError(node.id(), "trigger is missing"));
            if (node.lines().isEmpty()) errors.add(new ValidationError(node.id(), "at least one line is required"));
            for (String action : node.actions()) {
                String[] parts = action.split(":", 2);
                if (parts.length == 2 && parts[0].equalsIgnoreCase("unlockCodex")
                    && !knownEntries.contains(StoryProfile.normalizeDotted(parts[1]))) {
                    errors.add(new ValidationError(node.id(), "unknown codex entry " + parts[1]));
                }
            }
        }
        return List.copyOf(errors);
    }

    private static Map<String, String> stringMap(ConfigurationSection section) {
        Map<String, String> out = new LinkedHashMap<>();
        if (section == null) return out;
        for (String key : section.getKeys(false)) out.put(StoryProfile.normalizeDotted(key), String.valueOf(section.get(key)));
        return out;
    }

    private static long number(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null || material.isAir() ? fallback : material;
    }

    public boolean enabled() { return enabled; }
    public String dialogueMode() { return dialogueMode; }
    public long choiceTimeoutMillis() { return choiceTimeoutMillis; }
    public long npcRepeatCooldownMillis() { return npcRepeatCooldownMillis; }
    public double lowHealthThreshold() { return lowHealthThreshold; }
    public List<DialogueNode> dialogueNodes() { return dialogueNodes; }
    public Map<String, BossText> bosses() { return bosses; }
    public Map<String, CodexCategory> categories() { return categories; }
    public Map<String, CodexEntry> entries() { return entries; }
    public Map<String, AmbientEffect> ambientEffects() { return ambientEffects; }
    public String storyNpcId(String existingId) { return npcMappings.getOrDefault(StoryProfile.normalizeDotted(existingId), StoryProfile.normalizeDotted(existingId)); }

    public record BossText(
        String id, String display, String entrance, List<String> phases, String lowHealth, String defeat, String memory, String memoryEntry
    ) {
        public BossText { phases = List.copyOf(phases == null ? List.of() : phases); }
        public String phase(int phase) {
            int index = Math.max(0, phase - 2);
            return phases.isEmpty() ? "" : phases.get(Math.min(index, phases.size() - 1));
        }
    }

    public record CodexCategory(String id, String name, Material icon, String description, int order) { }
    public record CodexEntry(String id, String category, String name, Material icon, List<String> text, int order) {
        public CodexEntry { text = List.copyOf(text == null ? List.of() : text); }
    }
    public record AmbientEffect(String sound, String particle, float volume, float pitch, int particleCount) { }
    public record ValidationError(String nodeId, String message) { }
}
