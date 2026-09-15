package me.rique.smpcore.leaderboard;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaderboardManagerTest {

    @Test
    void rankingLayoutUsesAThreePlayerPodiumThenSevenContenders() {
        int[] slots = LeaderboardManager.rankingSlots();

        assertArrayEquals(new int[] {22, 20, 24, 37, 38, 39, 40, 41, 42, 43}, slots);
        assertEquals(slots.length, Arrays.stream(slots).distinct().count());
    }

    @Test
    void everyOverviewCardRoutesToItsLeaderboard() {
        assertEquals(LeaderboardManager.LeaderboardType.PLAYER_KILLS, LeaderboardManager.overviewTypeAt(19));
        assertEquals(LeaderboardManager.LeaderboardType.DEATHS, LeaderboardManager.overviewTypeAt(28));
        assertEquals(LeaderboardManager.LeaderboardType.MOB_KILLS, LeaderboardManager.overviewTypeAt(37));
        assertEquals(LeaderboardManager.LeaderboardType.PLAYTIME, LeaderboardManager.overviewTypeAt(21));
        assertEquals(LeaderboardManager.LeaderboardType.BOSS_KILLS, LeaderboardManager.overviewTypeAt(23));
        assertEquals(LeaderboardManager.LeaderboardType.BOSS_DAMAGE, LeaderboardManager.overviewTypeAt(32));
        assertEquals(LeaderboardManager.LeaderboardType.BOSS_FIGHTS, LeaderboardManager.overviewTypeAt(41));
        assertEquals(LeaderboardManager.LeaderboardType.DUEL_WINS, LeaderboardManager.overviewTypeAt(25));
        assertEquals(LeaderboardManager.LeaderboardType.DUEL_BET_WINS, LeaderboardManager.overviewTypeAt(34));
    }
}
