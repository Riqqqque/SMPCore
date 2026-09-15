package me.rique.smpcore.command;

import me.rique.smpcore.util.PluginCommandRoots;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffCommandCatalogTest {

    private static final Set<String> REQUIRED_STAFF_ROOTS = Set.of(
        "staffmode", "fly", "speed", "heal", "feed", "god", "vanish", "forceunvanish",
        "freeze", "unfreeze", "invsee", "viewteamvault", "nick", "ban", "kick", "unban", "announce", "smite",
        "gmc", "gms", "gma", "gmsp",
        "launchaccess", "startsmp", "setspawn", "spawnprotect", "warp", "day", "night", "sun", "storm",
        "bossdungeon", "bossloadout", "bosses", "bossmasteryadmin", "bossledgeradmin",
        "spawnnpc", "corruptionwarden", "mayor", "artificer", "dungeonkeeper", "brewmaster",
        "adventurer", "dealer", "croupier", "duelmaster", "goblinhunter", "miner", "farmer",
        "witch", "overseer", "beastwarden", "bossbroker", "blackmarket", "fisher", "reforger", "priest",
        "customitem", "legendary", "replenishbook", "delicatebook", "telekinesisbook",
        "smeltingtouchbook", "wisebook", "doublejumpbook", "dashbook", "essenceadmin",
        "backpackadmin", "itemrecovery", "deathinventory", "itemaudit", "familiaradmin",
        "beastwardenadmin", "mayorfamiliar", "setpower", "admin", "corruptionstation",
        "stall", "shops", "duel", "tavernadmin", "spawner", "goblins", "spawnlife",
        "bedrockskulls", "smpcore", "veil", "changelog"
    );

    @Test
    void catalogCoversEveryDocumentedStaffCommandFamily() {
        assertTrue(StaffCommandCatalog.coveredRoots().containsAll(REQUIRED_STAFF_ROOTS),
            () -> "Missing staff roots: " + difference(REQUIRED_STAFF_ROOTS, StaffCommandCatalog.coveredRoots()));
    }

    @Test
    void catalogEntriesHaveUniqueIdsAndUsefulInstructions() {
        Set<String> ids = new HashSet<>();
        for (StaffCommandCatalog.Entry entry : StaffCommandCatalog.entries()) {
            assertTrue(ids.add(entry.id()), "Duplicate manager entry id: " + entry.id());
            assertNotNull(entry.icon(), "Missing icon: " + entry.id());
            assertFalse(entry.description().isBlank(), "Missing description: " + entry.id());
            assertFalse(entry.usages().isEmpty(), "Missing syntax: " + entry.id());
            assertTrue(entry.usages().stream().allMatch(usage -> usage.startsWith("/")),
                "Syntax must start with /: " + entry.id());
            assertTrue(entry.preparedCommand().startsWith("/"), "Prepared command must start with /: " + entry.id());
            assertFalse(entry.roots().isEmpty(), "Missing command roots: " + entry.id());
        }
        assertEquals(StaffCommandCatalog.entries().size(), ids.size());
    }

    @Test
    void everyCatalogRootUsesTheManagedCommandPipeline() {
        StaffCommandCatalog.coveredRoots().forEach(root ->
            assertTrue(PluginCommandRoots.contains(root), "Manager root missing from PluginCommandRoots: " + root));
        Set.of("manager", "staffmanager", "adminmanager").forEach(root ->
            assertTrue(PluginCommandRoots.contains(root), "Manager command missing from PluginCommandRoots: " + root));
    }

    @Test
    void searchFindsAliasesPurposesAndSyntax() {
        assertEquals("item-recovery", StaffCommandCatalog.search("duplicate restore").getFirst().id());
        assertTrue(StaffCommandCatalog.search("bdungeon").stream().anyMatch(entry -> entry.id().equals("boss-dungeon")));
        assertTrue(StaffCommandCatalog.search("setspawn").stream().anyMatch(entry -> entry.id().equals("set-spawn")));
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }
}
