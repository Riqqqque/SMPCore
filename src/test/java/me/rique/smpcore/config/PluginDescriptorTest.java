package me.rique.smpcore.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDescriptorTest {
    private static final Pattern PERMISSION_KEY = Pattern.compile("^  ([a-z0-9._-]+):\\s*$");
    private static final Pattern SOURCE_PERMISSION = Pattern.compile(
        "\"((?:smpcore|clickvillagers)\\.[a-z0-9._-]+)\""
    );

    @Test
    void descriptorTargetsTheCurrentServerAndHasUniquePermissions() throws IOException {
        String descriptor = readDescriptor();

        assertTrue(descriptor.contains("api-version: '26.2'"));
        assertTrue(descriptor.contains("main: me.rique.smpcore.SMPCore"));

        Set<String> permissions = new HashSet<>();
        boolean inPermissions = false;
        for (String line : descriptor.lines().toList()) {
            if (line.equals("permissions:")) {
                inPermissions = true;
                continue;
            }
            if (!inPermissions) {
                continue;
            }

            Matcher matcher = PERMISSION_KEY.matcher(line);
            if (matcher.matches()) {
                String permission = matcher.group(1);
                assertTrue(permissions.add(permission), "Duplicate permission: " + permission);
            }
        }

        assertTrue(permissions.contains("smpcore.tavern.admin"));
        assertTrue(permissions.contains("smpcore.bossmastery.use"));
        assertTrue(permissions.contains("smpcore.bossmastery.admin"));
        assertTrue(permissions.contains("smpcore.spawnlife.use"));
        assertTrue(permissions.contains("smpcore.spawnlife.admin"));
        assertTrue(permissions.contains("smpcore.staff"));
        assertTrue(permissions.contains("smpcore.staffmode"));
        assertTrue(permissions.contains("smpcore.admin.deathinventory"));
        assertTrue(permissions.contains("smpcore.stall.use"));
        assertTrue(permissions.contains("smpcore.stall.admin"));
        assertTrue(permissions.contains("smpcore.tab.title.owner"));
        assertTrue(permissions.contains("smpcore.tab.title.admin"));
        assertTrue(permissions.contains("smpcore.tab.title.moderator"));
        assertTrue(permissions.contains("smpcore.tab.title.builder"));
        assertEquals(permissions, readSourcePermissions(),
            "Every literal permission used by the plugin must be declared, with no stale descriptor entries");
    }

    private static String readDescriptor() throws IOException {
        try (InputStream stream = PluginDescriptorTest.class.getResourceAsStream("/paper-plugin.yml")) {
            assertNotNull(stream, "paper-plugin.yml was not packaged for tests");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> readSourcePermissions() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        List<Path> sources;
        try (var paths = Files.walk(sourceRoot)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        Set<String> permissions = new HashSet<>();
        for (Path source : sources) {
            Matcher matcher = SOURCE_PERMISSION.matcher(Files.readString(source));
            while (matcher.find()) {
                permissions.add(matcher.group(1));
            }
        }
        return permissions;
    }
}
