package me.rique.smpcore.spawn;

import me.rique.smpcore.npc.GuideNpcManager.GuideNpcType;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnLifeManagerTest {

    @Test
    void animalAndTownSoundsStaySparse() {
        long animalMinimum = SpawnLifeManager.nextAmbientDelayMillis(GuideNpcType.FETCH_HOUND, 0L);
        long animalMaximum = SpawnLifeManager.nextAmbientDelayMillis(GuideNpcType.TOWN_PARROT, -1L);
        long citizenMinimum = SpawnLifeManager.nextAmbientDelayMillis(GuideNpcType.TOWN_BAKER, 0L);
        long citizenMaximum = SpawnLifeManager.nextAmbientDelayMillis(GuideNpcType.TOWN_SEAMSTRESS, -1L);
        long tavernDelay = SpawnLifeManager.nextAmbientDelayMillis(GuideNpcType.TAVERN_TIPSY, Long.MIN_VALUE);

        assertTrue(animalMinimum >= 18_000L && animalMinimum <= 38_000L);
        assertTrue(animalMaximum >= 18_000L && animalMaximum <= 38_000L);
        assertTrue(citizenMinimum >= 28_000L && citizenMinimum <= 52_000L);
        assertTrue(citizenMaximum >= 28_000L && citizenMaximum <= 52_000L);
        assertTrue(tavernDelay >= 28_000L && tavernDelay <= 52_000L);
    }

    @Test
    void fetchTimeoutHasAnExactBoundary() {
        assertFalse(SpawnLifeManager.fetchTimedOut(599));
        assertTrue(SpawnLifeManager.fetchTimedOut(600));
        assertTrue(SpawnLifeManager.fetchTimedOut(1_200));
    }

    @Test
    void fetchEndsWhenPlayerLeavesTheFortyBlockSessionRange() {
        assertFalse(SpawnLifeManager.fetchPlayerOutOfRange(true, 1_600.0D));
        assertTrue(SpawnLifeManager.fetchPlayerOutOfRange(true, 1_600.01D));
        assertTrue(SpawnLifeManager.fetchPlayerOutOfRange(false, 0.0D));
        assertTrue(SpawnLifeManager.fetchPlayerOutOfRange(true, Double.NaN));
    }

    @Test
    void fetchDropIdentityRequiresTheOriginalOwnerAndToken() {
        assertTrue(SpawnLifeManager.fetchIdentityMatches("owner", "token", "owner", "token"));
        assertFalse(SpawnLifeManager.fetchIdentityMatches("owner", "token", "other", "token"));
        assertFalse(SpawnLifeManager.fetchIdentityMatches("owner", "token", "owner", "other"));
        assertFalse(SpawnLifeManager.fetchIdentityMatches(null, "token", "owner", "token"));
        assertFalse(SpawnLifeManager.fetchIdentityMatches("owner", null, "owner", "token"));
    }

    @Test
    void fetchOnlyRepathsSettledThrowsAtTheRetryBoundary() {
        assertFalse(SpawnLifeManager.fetchPathRetryDue(0, true));
        assertFalse(SpawnLifeManager.fetchPathRetryDue(8, true));
        assertFalse(SpawnLifeManager.fetchPathRetryDue(10, false));
        assertTrue(SpawnLifeManager.fetchPathRetryDue(10, true));
        assertTrue(SpawnLifeManager.fetchPathRetryDue(20, true));
    }

    @Test
    void onlyDogsAndCatsAcceptTheirNormalFoods() {
        assertTrue(SpawnLifeManager.acceptsFeed(GuideNpcType.FETCH_HOUND, Material.BONE));
        assertFalse(SpawnLifeManager.acceptsFeed(GuideNpcType.FETCH_HOUND, Material.COD));
        assertTrue(SpawnLifeManager.acceptsFeed(GuideNpcType.TOWN_CAT, Material.COD));
        assertTrue(SpawnLifeManager.acceptsFeed(GuideNpcType.TOWN_CAT, Material.SALMON));
        assertFalse(SpawnLifeManager.acceptsFeed(GuideNpcType.TOWN_CAT, Material.TROPICAL_FISH));
        assertFalse(SpawnLifeManager.acceptsFeed(GuideNpcType.TOWN_FOX, Material.SALMON));
    }

    @Test
    void feedingCooldownRoundsUpAndEndsExactlyOnTime() {
        assertTrue(SpawnLifeManager.feedCooldownSeconds(61_000L, 1_000L) == 60L);
        assertTrue(SpawnLifeManager.feedCooldownSeconds(1_001L, 1_000L) == 1L);
        assertTrue(SpawnLifeManager.feedCooldownSeconds(1_000L, 1_000L) == 0L);
        assertTrue(SpawnLifeManager.feedCooldownSeconds(999L, 1_000L) == 0L);
    }
}
