package me.rique.smpcore.story;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class StoryResourceTest {
    @Test
    void npcChatterUsesAQuietRepeatWindow() {
        YamlConfiguration core = load("story.yml");
        assertEquals(600L, core.getLong("dialogue.npc-repeat-cooldown-seconds"));
    }

    @Test
    void canonicalResourcesContainEveryBossAndJournalCategory() {
        YamlConfiguration bosses = load("story-bosses.yml");
        YamlConfiguration codex = load("story-codex.yml");
        assertEquals(10, required(bosses, "bosses").getKeys(false).size());
        assertEquals(8, required(codex, "categories").getKeys(false).size());
        ConfigurationSection entries = required(codex, "entries");
        long entryCount = entries.getKeys(true).stream()
            .map(entries::getConfigurationSection)
            .filter(section -> section != null && section.contains("name") && section.contains("category"))
            .count();
        assertTrue(entryCount >= 50, "Only found " + entryCount + " journal entries");
    }

    @Test
    void everyDialogueNodeHasLinesAndAValidTrigger() {
        YamlConfiguration dialogue = load("story-dialogue.yml");
        assertEquals(3, dialogue.getInt("content-version"));
        ConfigurationSection nodes = required(dialogue, "dialogues");
        assertTrue(nodes.getKeys(false).size() >= 30);
        for (String id : nodes.getKeys(false)) {
            ConfigurationSection node = required(nodes, id);
            assertFalse(node.getString("trigger", "").isBlank(), id + " has no trigger");
            assertFalse(node.getMapList("lines").isEmpty(), id + " has no lines");
        }
    }

    @Test
    void everydayDialogueCoversEveryNpcWithoutLegacyMonologues() {
        YamlConfiguration dialogue = load("story-dialogue.yml");
        ConfigurationSection nodes = required(dialogue, "dialogues");
        Set<String> speakers = new HashSet<>();
        StringBuilder allText = new StringBuilder();
        for (String id : nodes.getKeys(false)) {
            ConfigurationSection node = required(nodes, id);
            speakers.add(node.getString("speaker", ""));
            for (var line : node.getMapList("lines")) {
                allText.append(' ').append(String.valueOf(line.get("text")));
            }
        }

        assertTrue(speakers.containsAll(Set.of(
            "Mira", "Mayor Bah", "Malakar", "Orin", "Veyr", "Brannik", "Father Aldren",
            "Grikk", "Torren", "Rowan", "Vespera", "Veil Overseer", "Bram", "Rook", "Silas",
            "Kael", "Mogrik", "Sable", "Corin"
        )));
        String text = allText.toString();
        assertFalse(text.contains("Metal remembers every hand"));
        assertFalse(text.contains("Every wager is a small oath"));
        assertFalse(text.contains("Directives are not heroism"));
        assertFalse(text.contains("arguments between hammer and memory"));
    }

    @Test
    void everySystemNpcHasAStoryDialogueMapping() {
        YamlConfiguration core = load("story.yml");
        ConfigurationSection mappings = required(core, "mappings.npcs");
        assertTrue(mappings.getKeys(false).containsAll(Set.of(
            "spawn_guide", "mayor", "dungeon_keeper", "gear_expert", "corruption_warden",
            "reforger", "priest", "goblin_hunter", "miner", "farmer", "witch", "overseer",
            "brewmaster", "cardsharp", "dealer", "beastwarden", "bossbroker", "black_marketeer", "fisher"
        )));
    }

    @Test
    void oathkeeperHasAllThreePhaseLines() {
        YamlConfiguration bosses = load("story-bosses.yml");
        assertEquals(3, bosses.getStringList("bosses.corrupted_oathkeeper.phases").size());
        assertEquals("Your Veilmark is the missing witness.", bosses.getString("bosses.corrupted_oathkeeper.memory"));
    }

    private static YamlConfiguration load(String name) {
        File file = new File("src/main/resources", name);
        assertTrue(file.isFile(), "Missing " + file.getAbsolutePath());
        return YamlConfiguration.loadConfiguration(file);
    }

    private static ConfigurationSection required(YamlConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        assertNotNull(section, "Missing section " + path);
        return section;
    }

    private static ConfigurationSection required(ConfigurationSection config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        assertNotNull(section, "Missing section " + path);
        return section;
    }
}
