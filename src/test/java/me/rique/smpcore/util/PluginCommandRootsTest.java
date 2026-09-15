package me.rique.smpcore.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginCommandRootsTest {
    private static final Pattern DIRECT_COMMAND = Pattern.compile(
        "\\.register\\s*\\(\\s*Commands\\.literal\\s*\\(\\s*\"([a-z0-9_-]+)\""
    );

    @Test
    void spawnLifeCommandsRemainAvailableDuringManagedCommandPhases() {
        assertTrue(PluginCommandRoots.contains("spawnlife"));
        assertTrue(PluginCommandRoots.contains("ambientnpc"));
        assertTrue(PluginCommandRoots.contains("townnpc"));
        assertTrue(PluginCommandRoots.contains("stall"));
        assertTrue(PluginCommandRoots.contains("stalls"));
        assertTrue(PluginCommandRoots.contains("marketstall"));
    }

    @Test
    void launchSystemsAndAliasesAreCoveredByPreStartLockdownAndAuditing() {
        Set.of(
            "adminspawnstick", "backpack", "backpacklabel", "bedrockskulls", "bedrockheads",
            "bossdungeon", "bdungeon", "bossjoin", "bossqueue", "bossloadout", "bossgear", "testgear",
            "deathinventory", "deathinv", "invrestore", "familiar", "familiars", "veilfamiliar",
            "familiaradmin", "famadmin", "goblins", "goblinhunt", "veil", "veiljournal",
            "blackmarket", "blackmarketnpc", "fisher", "fishernpc", "miner", "minernpc",
            "warp", "warps", "wild", "rtp", "randomtp", "bounties", "bountylist", "wanted",
            "itemrecovery", "invrecovery", "guirecovery", "allyhome", "allyhomes",
            "allysethome", "allydelhome", "staffmode", "opmode"
        ).forEach(root -> assertTrue(PluginCommandRoots.contains(root), "Missing command root: " + root));
    }

    @Test
    void rouletteAndCroupierRoutesCannotBypassManagedCommandPhases() {
        Set.of("roulette", "roul", "croupier", "roulettecroupier", "roulettedealer")
            .forEach(root -> assertTrue(PluginCommandRoots.contains(root), "Missing command root: " + root));
    }

    @Test
    void everyDirectBrigadierCommandUsesTheManagedCommandPipeline() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        List<Path> sources;
        try (var paths = Files.walk(sourceRoot)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        for (Path source : sources) {
            Matcher matcher = DIRECT_COMMAND.matcher(Files.readString(source));
            while (matcher.find()) {
                String root = matcher.group(1);
                assertTrue(PluginCommandRoots.contains(root),
                    "Direct command root is missing from PluginCommandRoots: " + root + " in " + source);
            }
        }
    }
}
