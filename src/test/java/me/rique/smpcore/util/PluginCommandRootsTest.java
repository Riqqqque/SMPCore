package me.rique.smpcore.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginCommandRootsTest {

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
            "blackmarket", "blackmarketnpc", "fisher", "fishernpc", "miner", "minernpc"
        ).forEach(root -> assertTrue(PluginCommandRoots.contains(root), "Missing command root: " + root));
    }
}
