package me.rique.smpcore.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainMenuCommandTest {

    @Test
    void everyStatsNavigationButtonHasADestination() {
        assertEquals(MainMenuCommand.StatsMenuDestination.MAIN_MENU, MainMenuCommand.statsMenuDestination("back"));
        assertEquals(MainMenuCommand.StatsMenuDestination.BASE_STATS, MainMenuCommand.statsMenuDestination("stats"));
        assertEquals(MainMenuCommand.StatsMenuDestination.BASE_STATS, MainMenuCommand.statsMenuDestination("refresh"));
        assertEquals(MainMenuCommand.StatsMenuDestination.CUSTOM_STATS, MainMenuCommand.statsMenuDestination("custom_stats"));
        assertEquals(MainMenuCommand.StatsMenuDestination.CUSTOM_STATS, MainMenuCommand.statsMenuDestination("custom_stats_refresh"));
        assertEquals(MainMenuCommand.StatsMenuDestination.CLOSE, MainMenuCommand.statsMenuDestination("close"));
        assertEquals(MainMenuCommand.StatsMenuDestination.NONE, MainMenuCommand.statsMenuDestination("unknown"));
        assertEquals(MainMenuCommand.StatsMenuDestination.NONE, MainMenuCommand.statsMenuDestination(null));
    }
}
