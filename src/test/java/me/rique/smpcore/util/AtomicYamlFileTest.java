package me.rique.smpcore.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicYamlFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsParentAndReplacesCompleteDocument() throws Exception {
        File target = temporaryDirectory.resolve("nested/settings.yml").toFile();
        YamlConfiguration first = new YamlConfiguration();
        first.set("old", true);
        AtomicYamlFile.save(first, target);

        YamlConfiguration replacement = new YamlConfiguration();
        replacement.set("new", 42);
        AtomicYamlFile.save(replacement, target);

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(target);
        assertFalse(loaded.contains("old"));
        assertEquals(42, loaded.getInt("new"));
        try (var files = Files.list(target.toPath().getParent())) {
            assertEquals(1L, files.count());
        }
    }

    @Test
    void rejectsMissingArguments() {
        YamlConfiguration configuration = new YamlConfiguration();
        File target = temporaryDirectory.resolve("settings.yml").toFile();
        assertThrows(IllegalArgumentException.class, () -> AtomicYamlFile.save(null, target));
        assertThrows(IllegalArgumentException.class, () -> AtomicYamlFile.save(configuration, null));
    }
}
