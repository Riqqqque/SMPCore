package me.rique.smpcore.boss;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossTestLoadoutManagerTest {

    @Test
    void everyProgressionBossHasOneCompleteLoadout() {
        List<BossTestLoadoutManager.LoadoutDefinition> loadouts = BossTestLoadoutManager.definitions();
        List<String> expectedBosses = BossBalance.progression().stream()
            .map(BossBalance.Profile::bossId)
            .toList();

        assertEquals(10, loadouts.size());
        assertEquals(expectedBosses, loadouts.stream().map(BossTestLoadoutManager.LoadoutDefinition::bossId).toList());
        for (BossTestLoadoutManager.LoadoutDefinition loadout : loadouts) {
            assertEquals(4, loadout.armor().size(), loadout.bossId());
            assertFalse(loadout.weapons().isEmpty(), loadout.bossId());
            assertTrue(loadout.protectionLevel() >= 4, loadout.bossId());
            assertTrue(loadout.weaponEnchantLevel() >= 5, loadout.bossId());
        }
    }

    @Test
    void customItemsAreAvailableBeforeTheBossTheyBenchmark() {
        for (BossTestLoadoutManager.LoadoutDefinition loadout : BossTestLoadoutManager.definitions()) {
            int bossTier = BossBalance.profile(loadout.bossId()).tier();
            for (BossTestLoadoutManager.ItemDefinition item : loadout.allDefinitions()) {
                if (item.relicId() == null) {
                    assertNotNull(item.material(), loadout.bossId());
                    continue;
                }
                int sourceTier = sourceTier(item.relicId());
                assertTrue(
                    sourceTier < bossTier,
                    () -> item.relicId() + " is Tier " + sourceTier + " gear and cannot benchmark Tier " + bossTier
                );
            }
        }
    }

    @Test
    void lateGameKitsModelTheIntendedUpgradeCurve() {
        var warden = loadout("voralith_the_crimson_warden");
        var oathkeeper = loadout("corrupted_oathkeeper");

        assertEquals(5, warden.protectionLevel());
        assertEquals(6, warden.weaponEnchantLevel());
        assertTrue(warden.armor().stream().allMatch(item -> item.relicId().startsWith("riftwalker_")));

        assertEquals(5, oathkeeper.protectionLevel());
        assertEquals(6, oathkeeper.weaponEnchantLevel());
        assertTrue(oathkeeper.armor().stream().allMatch(item -> item.relicId().startsWith("crimson_guard_")));
        assertFalse(oathkeeper.armor().stream().anyMatch(item -> item.relicId().startsWith("eclipse_mantle_")));
    }

    private BossTestLoadoutManager.LoadoutDefinition loadout(String bossId) {
        return BossTestLoadoutManager.definitions().stream()
            .filter(loadout -> loadout.bossId().equals(bossId))
            .findFirst()
            .orElseThrow();
    }

    private int sourceTier(String relicId) {
        if (relicId.startsWith("widow_court_")) return 3;
        if (relicId.startsWith("tidebound_")) return 5;
        if (relicId.startsWith("ashen_saint_")) return 6;
        if (relicId.startsWith("riftwalker_")) return 7;
        if (relicId.startsWith("crimson_guard_")) return 9;
        return switch (relicId) {
            case "oathbreaker_mattock" -> 1;
            case "crown_of_cinders", "stormcall_greaves", "cindershard_dagger", "ashen_verdict" -> 2;
            case "widowfang" -> 3;
            case "briarhook_saw", "thornwhisper" -> 4;
            case "tidebreaker" -> 5;
            case "saintsplitter" -> 6;
            case "nullglass_rapier", "rift_pike" -> 7;
            case "hollowsong_bow" -> 8;
            case "veilpiercer_glaive", "sunless_repeater", "gravemourn" -> 9;
            default -> throw new AssertionError("Unmapped test relic: " + relicId);
        };
    }
}
