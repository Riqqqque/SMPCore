package me.rique.smpcore.story;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryServiceLifecycleTest {

    @Test
    void onlyNewestContentLoadMayReplaceActiveContent() {
        assertTrue(StoryService.isCurrentContentLoad(4L, 4L, false));
        assertFalse(StoryService.isCurrentContentLoad(3L, 4L, false));
        assertFalse(StoryService.isCurrentContentLoad(4L, 4L, true));
    }

    @Test
    void disconnectedPlayersAreNotRetainedAfterAsyncProfileLoad() {
        assertTrue(StoryService.shouldRetainLoadedProfile(false, true));
        assertFalse(StoryService.shouldRetainLoadedProfile(false, false));
        assertFalse(StoryService.shouldRetainLoadedProfile(true, true));
    }
}
