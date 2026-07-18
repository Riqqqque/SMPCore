package me.rique.smpcore.boss;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossDungeonManagerTest {

    @Test
    void summonCostsKeepThemedKeysButReduceRepeatableMaterialLoss() {
        assertCost("yule_the_minion", Material.BELL, Material.GOLDEN_SWORD, Material.BELL, 1, Material.GOLDEN_SWORD, 1, Material.SOUL_SAND, 1, Material.GOLD_BLOCK, 2);
        assertCost("kael_the_ashen", Material.SOUL_CAMPFIRE, Material.BOW, Material.SOUL_CAMPFIRE, 1, Material.BOW, 1, Material.BONE_BLOCK, 3);
        assertCost("vesper_the_widow_queen", Material.COBWEB, Material.FERMENTED_SPIDER_EYE, Material.COBWEB, 1, Material.FERMENTED_SPIDER_EYE, 1, Material.MOSS_BLOCK, 1, Material.BLACK_CANDLE, 2);
        assertCost("aurelion_the_rift_seraph", Material.END_ROD, Material.ENDER_EYE, Material.END_ROD, 1, Material.ENDER_EYE, 1, Material.PURPUR_BLOCK, 1, Material.END_STONE_BRICKS, 2);
        assertCost("morvessa_the_runebloom_witch", Material.BREWING_STAND, Material.DRAGON_BREATH, Material.BREWING_STAND, 1, Material.DRAGON_BREATH, 1, Material.AMETHYST_BLOCK, 1, Material.FLOWERING_AZALEA_LEAVES, 2);
        assertCost("nereida_the_abyss_mother", Material.CONDUIT, Material.HEART_OF_THE_SEA, Material.CONDUIT, 1, Material.HEART_OF_THE_SEA, 1, Material.PRISMARINE, 1, Material.SEA_LANTERN, 2);
        assertCost("iron_saint", Material.ANVIL, Material.IRON_BLOCK, Material.ANVIL, 1, Material.IRON_BLOCK, 2, Material.SMITHING_TABLE, 1);
        assertCost("mirewood_the_root_tyrant", Material.MANGROVE_ROOTS, Material.SPORE_BLOSSOM, Material.MANGROVE_ROOTS, 1, Material.SPORE_BLOSSOM, 1, Material.MOSS_BLOCK, 1, Material.OAK_SAPLING, 2);
        assertCost("corrupted_oathkeeper", Material.RESPAWN_ANCHOR, Material.NETHER_STAR, Material.RESPAWN_ANCHOR, 1, Material.NETHER_STAR, 1, Material.CRYING_OBSIDIAN, 1, Material.MAGMA_BLOCK, 2, Material.SCULK_CATALYST, 2);

        Map<Material, Integer> warden = BossDungeonManager.summonCosts("voralith_the_crimson_warden", Material.SCULK_SHRIEKER, Material.ECHO_SHARD);
        assertEquals(1, warden.get(Material.SCULK_SHRIEKER));
        assertEquals(1, warden.get(Material.ECHO_SHARD));
        assertEquals(1, warden.get(Material.SCULK_CATALYST));
        assertEquals(1, warden.get(Material.REDSTONE_BLOCK));
        assertEquals(2, warden.get(Material.SOUL_LANTERN));
        assertTrue(!warden.containsKey(Material.REINFORCED_DEEPSLATE), "unobtainable blocks cannot be survival entry costs");
    }

    @Test
    void essenceEntryCostScalesAcrossTheBossPath() {
        long[] expected = {25L, 40L, 60L, 80L, 105L, 130L, 160L, 195L, 235L, 300L};
        for (int tier = 1; tier <= expected.length; tier++) {
            assertEquals(expected[tier - 1], BossDungeonManager.defaultEssenceCost(tier));
        }
        assertEquals(25L, BossDungeonManager.defaultEssenceCost(0));
        assertEquals(300L, BossDungeonManager.defaultEssenceCost(11));
    }

    @Test
    void bundledArenaHasAWorldRootAndRegionData() throws Exception {
        InputStream resource = getClass().getClassLoader().getResourceAsStream("dungeon/gothic-boss-room-world.zip");
        assertNotNull(resource);
        boolean level = false;
        boolean region = false;
        try (ZipInputStream zip = new ZipInputStream(resource)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                level |= entry.getName().equals("Gothic Boss Room World/level.dat");
                region |= entry.getName().startsWith("Gothic Boss Room World/region/") && entry.getName().endsWith(".mca");
            }
        }
        assertTrue(level, "arena archive must contain level.dat at its expected world root");
        assertTrue(region, "arena archive must contain region data");
    }

    @Test
    void arenaContainmentIgnoresHeight() {
        Location bottom = new Location(null, 0.0, -64.0, 0.0);
        Location aboveBuildHeight = new Location(null, 3.0, 400.0, 4.0);
        assertEquals(25.0, BossManager.horizontalDistanceSquared(bottom, aboveBuildHeight));
    }

    @Test
    void bossCountdownUsesTenRealSecondsAndStopsAtZero() {
        long startedAt = 5_000L;
        long endsAt = startedAt + BossDungeonManager.BOSS_SPAWN_COUNTDOWN_SECONDS * 1_000L;

        assertEquals(10, BossDungeonManager.BOSS_SPAWN_COUNTDOWN_SECONDS);
        assertEquals(10, BossDungeonManager.countdownSecondsRemaining(endsAt, startedAt));
        assertEquals(9, BossDungeonManager.countdownSecondsRemaining(endsAt, startedAt + 1_000L));
        assertEquals(1, BossDungeonManager.countdownSecondsRemaining(endsAt, endsAt - 1L));
        assertEquals(0, BossDungeonManager.countdownSecondsRemaining(endsAt, endsAt));
        assertEquals(0, BossDungeonManager.countdownSecondsRemaining(endsAt, endsAt + 5_000L));
    }

    private void assertCost(String bossId, Material focus, Material catalyst, Object... entries) {
        Map<Material, Integer> actual = BossDungeonManager.summonCosts(bossId, focus, catalyst);
        assertEquals(entries.length / 2, actual.size());
        for (int i = 0; i < entries.length; i += 2) {
            assertEquals(entries[i + 1], actual.get(entries[i]), bossId + " cost mismatch for " + entries[i]);
        }
    }
}
