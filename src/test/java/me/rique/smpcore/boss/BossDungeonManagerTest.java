package me.rique.smpcore.boss;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossDungeonManagerTest {

    @Test
    void protectedDungeonBlocksDoNotPreventEating() {
        assertTrue(BossDungeonManager.allowsProtectedDungeonFoodUse(Action.RIGHT_CLICK_BLOCK, true));
        assertFalse(BossDungeonManager.allowsProtectedDungeonFoodUse(Action.RIGHT_CLICK_BLOCK, false));
        assertFalse(BossDungeonManager.allowsProtectedDungeonFoodUse(Action.RIGHT_CLICK_AIR, true));
        assertFalse(BossDungeonManager.allowsProtectedDungeonFoodUse(Action.LEFT_CLICK_BLOCK, true));
    }

    @Test
    void summonCostsUseExactlyOneFocusAndOneCatalyst() {
        assertCost(Material.BELL, Material.GOLDEN_SWORD);
        assertCost(Material.SOUL_CAMPFIRE, Material.BOW);
        assertCost(Material.COBWEB, Material.FERMENTED_SPIDER_EYE);
        assertCost(Material.MANGROVE_ROOTS, Material.SPORE_BLOSSOM);
        assertCost(Material.PRISMARINE_BRICKS, Material.WATER_BUCKET);
        assertCost(Material.ANVIL, Material.IRON_BLOCK);
        assertCost(Material.END_ROD, Material.ENDER_EYE);
        assertCost(Material.BREWING_STAND, Material.DRAGON_BREATH);
        assertCost(Material.SCULK_SHRIEKER, Material.ECHO_SHARD);
        assertCost(Material.RESPAWN_ANCHOR, Material.NETHER_STAR);
    }

    @Test
    void summonCostsRejectInvalidMaterialPairs() {
        assertThrows(IllegalArgumentException.class, () -> BossDungeonManager.summonCosts(null, Material.BELL));
        assertThrows(IllegalArgumentException.class, () -> BossDungeonManager.summonCosts(Material.BELL, null));
        assertThrows(IllegalArgumentException.class, () -> BossDungeonManager.summonCosts(Material.BELL, Material.BELL));
    }

    @Test
    void tierFiveUsesSimpleOceanMaterialsInsteadOfTreasureOnlyHearts() {
        assertEquals(Material.PRISMARINE_BRICKS, BossManager.THALASSA_RITUAL_FOCUS);
        assertEquals(Material.WATER_BUCKET, BossManager.THALASSA_RITUAL_CATALYST);
        assertFalse(BossDungeonManager.summonCosts(
            BossManager.THALASSA_RITUAL_FOCUS,
            BossManager.THALASSA_RITUAL_CATALYST
        )
            .containsKey(Material.HEART_OF_THE_SEA));
        assertFalse(BossDungeonManager.summonCosts(
            BossManager.THALASSA_RITUAL_FOCUS,
            BossManager.THALASSA_RITUAL_CATALYST
        )
            .containsKey(Material.CONDUIT));
    }

    @Test
    void bossSpectatorZoneCoversTheFullDungeonInterior() {
        assertTrue(BossDungeonManager.spectatorOffsetAllowed(0.0D, 0.0D, 0.0D));
        assertTrue(BossDungeonManager.spectatorOffsetAllowed(39.45D, -0.95D, 39.45D));
        assertTrue(BossDungeonManager.spectatorOffsetAllowed(-39.45D, 59.95D, -39.45D));
        assertFalse(BossDungeonManager.spectatorOffsetAllowed(39.46D, 0.0D, 0.0D));
        assertFalse(BossDungeonManager.spectatorOffsetAllowed(0.0D, -0.96D, 0.0D));
        assertFalse(BossDungeonManager.spectatorOffsetAllowed(0.0D, 59.96D, 0.0D));
        assertFalse(BossDungeonManager.spectatorOffsetAllowed(Double.NaN, 0.0D, 0.0D));
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

    @Test
    void allowedCrossWorldDungeonExitsRestoreThePlayersNormalState() {
        assertTrue(BossDungeonManager.shouldRestoreDungeonExitState(true, false, false));
        assertTrue(!BossDungeonManager.shouldRestoreDungeonExitState(true, false, true));
        assertTrue(!BossDungeonManager.shouldRestoreDungeonExitState(false, false, false));
        assertTrue(!BossDungeonManager.shouldRestoreDungeonExitState(false, true, false));
    }

    @Test
    void bossCatalogBackButtonsKeepTheirOriginalDestination() {
        assertEquals(
            BossDungeonManager.CatalogBackDestination.MAIN_MENU,
            BossDungeonManager.catalogBackDestination(BossDungeonManager.CatalogBackTarget.MAIN_MENU)
        );
        assertEquals(
            BossDungeonManager.CatalogBackDestination.ENTRANCE,
            BossDungeonManager.catalogBackDestination(BossDungeonManager.CatalogBackTarget.ENTRANCE)
        );
        assertEquals(
            BossDungeonManager.CatalogBackDestination.LEAVE_DUNGEON,
            BossDungeonManager.catalogBackDestination(BossDungeonManager.CatalogBackTarget.LEAVE_DUNGEON)
        );
        assertEquals(
            BossDungeonManager.CatalogBackDestination.ENTRANCE,
            BossDungeonManager.catalogBackDestination(null)
        );
    }

    @Test
    void arenaCleanupRecognizesCurrentAndLegacyBossMinionMarkers() {
        assertTrue(BossManager.shouldCleanupBossMinion(true, false, false));
        assertTrue(BossManager.shouldCleanupBossMinion(false, true, false));
        assertTrue(BossManager.shouldCleanupBossMinion(false, false, true));
        assertFalse(BossManager.shouldCleanupBossMinion(false, false, false));
    }

    @Test
    void rareBossArmorRollUsesAnExactTwoPercentBoundary() {
        assertTrue(BossManager.rollsBossArmorReward(0.0D));
        assertTrue(BossManager.rollsBossArmorReward(0.019999999D));
        assertFalse(BossManager.rollsBossArmorReward(0.02D));
        assertFalse(BossManager.rollsBossArmorReward(1.0D));
        assertFalse(BossManager.rollsBossArmorReward(-0.001D));
        assertFalse(BossManager.rollsBossArmorReward(Double.NaN));
        assertEquals(0.02D, BossManager.BOSS_ARMOR_REWARD_CHANCE);
    }

    private void assertCost(Material focus, Material catalyst) {
        Map<Material, Integer> actual = BossDungeonManager.summonCosts(focus, catalyst);
        assertEquals(2, actual.size());
        assertEquals(1, actual.get(focus));
        assertEquals(1, actual.get(catalyst));
    }
}
