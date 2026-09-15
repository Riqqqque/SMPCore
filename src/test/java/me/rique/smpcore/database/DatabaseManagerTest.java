package me.rique.smpcore.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseManagerTest {

    private static final long DAY = 24L * 60L * 60L * 1000L;

    @Test
    void thirdTeamExitInsideSevenDaysStartsTheTwentyFourHourJoinLock() {
        long start = 1_000_000L;
        DatabaseManager.TeamLeaveActivityState first = DatabaseManager.nextTeamLeaveActivity(
            0L, 0, 0L, start, 7L * DAY, 3, DAY
        );
        DatabaseManager.TeamLeaveActivityState second = DatabaseManager.nextTeamLeaveActivity(
            first.windowStartedAt(), first.leaveCount(), first.joinLockedUntil(), start + DAY, 7L * DAY, 3, DAY
        );
        DatabaseManager.TeamLeaveActivityState third = DatabaseManager.nextTeamLeaveActivity(
            second.windowStartedAt(), second.leaveCount(), second.joinLockedUntil(), start + (2L * DAY), 7L * DAY, 3, DAY
        );

        assertEquals(1, first.leaveCount());
        assertEquals(0L, first.joinLockedUntil());
        assertEquals(2, second.leaveCount());
        assertEquals(0L, second.joinLockedUntil());
        assertEquals(3, third.leaveCount());
        assertEquals(start + (3L * DAY), third.joinLockedUntil());
    }

    @Test
    void leaveCounterResetsAfterTheSevenDayWindow() {
        long start = 1_000_000L;
        DatabaseManager.TeamLeaveActivityState next = DatabaseManager.nextTeamLeaveActivity(
            start, 2, 0L, start + (7L * DAY), 7L * DAY, 3, DAY
        );

        assertEquals(start + (7L * DAY), next.windowStartedAt());
        assertEquals(1, next.leaveCount());
        assertEquals(0L, next.joinLockedUntil());
    }
}
