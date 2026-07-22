package me.rique.smpcore.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicYamlFile {

    private AtomicYamlFile() {
    }

    public static void save(YamlConfiguration configuration, File target) throws IOException {
        if (configuration == null || target == null) {
            throw new IllegalArgumentException("Configuration and target are required.");
        }

        Path targetPath = target.toPath().toAbsolutePath();
        Path parent = targetPath.getParent();
        if (parent == null) {
            throw new IOException("No parent directory is available for " + target + ".");
        }

        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, target.getName() + ".", ".tmp");
        try {
            configuration.save(temporary.toFile());
            try {
                Files.move(temporary, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
