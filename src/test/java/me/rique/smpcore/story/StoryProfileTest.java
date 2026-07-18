package me.rique.smpcore.story;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class StoryProfileTest {
    @Test
    void bossMemoryUnlockIsIdempotent() {
        StoryProfile profile = new StoryProfile(UUID.randomUUID());
        assertTrue(profile.unlockMemory("yule_the_minion"));
        assertFalse(profile.unlockMemory("yule_the_minion"));
        assertEquals(1, profile.bossMemories().size());
    }

    @Test
    void outOfOrderBossKillUnlocksCorrectMemoryWithoutRegressing() {
        StoryProfile profile = new StoryProfile(UUID.randomUUID());
        assertTrue(profile.unlockMemory("morvessa_the_runebloom_witch"));
        assertEquals(StoryChapter.ACT_4, profile.chapter());
        assertTrue(profile.unlockMemory("yule_the_minion"));
        assertEquals(StoryChapter.ACT_4, profile.chapter());
    }

    @Test
    void alignmentCanOnlyBeSelectedOnce() {
        StoryProfile profile = oathkeeperProfile();
        assertTrue(profile.chooseAlignment(StoryAlignment.MEND));
        assertFalse(profile.chooseAlignment(StoryAlignment.SEVER));
        assertEquals(StoryAlignment.MEND, profile.alignment());
        assertEquals(StoryChapter.EPILOGUE, profile.chapter());
    }

    @Test
    void allAlignmentsLeaveMechanicalCountersAndMemoriesEqual() {
        StoryProfile mend = oathkeeperProfile();
        StoryProfile bind = oathkeeperProfile();
        StoryProfile sever = oathkeeperProfile();
        mend.recordAwakening(); bind.recordAwakening(); sever.recordAwakening();
        mend.recordCorruption(false); bind.recordCorruption(false); sever.recordCorruption(false);
        assertTrue(mend.chooseAlignment(StoryAlignment.MEND));
        assertTrue(bind.chooseAlignment(StoryAlignment.BIND));
        assertTrue(sever.chooseAlignment(StoryAlignment.SEVER));
        assertEquals(mend.bossMemories(), bind.bossMemories());
        assertEquals(bind.bossMemories(), sever.bossMemories());
        assertEquals(mend.awakeningUses(), sever.awakeningUses());
        assertEquals(mend.corruptionUses(), sever.corruptionUses());
    }

    @Test
    void countersOnlyMoveWhenTheRelevantRecorderIsCalled() {
        StoryProfile profile = new StoryProfile(UUID.randomUUID());
        assertEquals(0, profile.awakeningUses());
        assertEquals(0, profile.corruptionUses());
        profile.recordAwakening();
        profile.recordCorruption(false);
        assertEquals(1, profile.awakeningUses());
        assertEquals(1, profile.corruptionUses());
        assertTrue(profile.flagBoolean("understands_both"));
    }

    @Test
    void destructiveFailureEventCanOnlyBeClaimedOnce() {
        StoryProfile profile = new StoryProfile(UUID.randomUUID());
        assertTrue(profile.claimEvent("corruption:delivery-1"));
        profile.recordCorruption(true);
        assertFalse(profile.claimEvent("corruption:delivery-1"));
        assertEquals(1, profile.corruptionFailures());
        assertEquals(0, profile.corruptionUses());
    }

    @Test
    void duplicateBossDeathEventDoesNotDuplicateProgress() {
        StoryProfile profile = new StoryProfile(UUID.randomUUID());
        assertTrue(profile.claimEvent("boss:yule_the_minion:encounter"));
        assertTrue(profile.unlockMemory("yule_the_minion"));
        assertFalse(profile.claimEvent("boss:yule_the_minion:encounter"));
        assertEquals(1, profile.bossMemories().size());
    }

    @Test
    void migrationPreservesExistingData() {
        UUID playerId = UUID.randomUUID();
        StoryProfile profile = new StoryProfile(playerId, 0, StoryChapter.ACT_2, "OLD_STAGE",
            Set.of("kael_the_ashen"), Set.of("custom.entry"), Set.of("dialogue.old"), Set.of("event.old"),
            Map.of("legacy", "true"), Map.of("mira", 42L), 3, 4, 1, StoryAlignment.UNDECIDED);
        assertTrue(profile.migrate());
        assertEquals(StoryProfile.CURRENT_VERSION, profile.storyVersion());
        assertTrue(profile.bossMemories().contains("kael_the_ashen"));
        assertTrue(profile.codexEntries().contains("custom.entry"));
        assertEquals("true", profile.flag("legacy"));
    }

    @Test
    void encodedStateRoundTripsWithoutDelimiterDamage() {
        Set<String> values = Set.of("entry.one", "value:with:semicolon", "unicode.æther");
        assertEquals(values, StoryProfile.decodeSet(StoryProfile.encodeSet(values)));
        Map<String, String> map = Map.of("flag.one", "value:one;two", "other", "yes");
        assertEquals(map, StoryProfile.decodeMap(StoryProfile.encodeMap(map)));
    }

    @Test
    void resetCanPreserveBossHistory() {
        StoryProfile profile = oathkeeperProfile();
        profile.chooseAlignment(StoryAlignment.BIND);
        profile.reset(true);
        assertTrue(profile.bossMemories().contains("corrupted_oathkeeper"));
        assertEquals(StoryAlignment.UNDECIDED, profile.alignment());
        assertTrue(profile.codexEntries().contains("memories.corrupted_oathkeeper"));
    }

    @Test
    void completedSaveCannotClearNewerUnsavedChanges() {
        StoryProfile profile = new StoryProfile(UUID.randomUUID());
        profile.setFlag("first", "true");
        long firstRevision = profile.revision();
        profile.setFlag("second", "true");

        profile.markClean(firstRevision);
        assertTrue(profile.dirty());

        profile.markClean(profile.revision());
        assertFalse(profile.dirty());
    }

    private static StoryProfile oathkeeperProfile() {
        StoryProfile profile = new StoryProfile(UUID.randomUUID());
        assertTrue(profile.unlockMemory("corrupted_oathkeeper"));
        return profile;
    }
}
