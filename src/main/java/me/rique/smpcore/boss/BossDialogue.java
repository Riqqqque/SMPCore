package me.rique.smpcore.boss;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BossDialogue {
    private static final List<Profile> PROFILES = List.of(
        new Profile(
            "yule_the_minion",
            "Stand your ground. The Veil remembers cowards.",
            List.of("The muster is not finished."),
            List.of("Hold the line!", "You are not ready for what follows.", "Another step. Another grave."),
            "The Veil... chose you."
        ),
        new Profile(
            "kael_the_ashen",
            "One breath. One bolt. One grave.",
            List.of("The cinders have found your trail."),
            List.of("Run. The next shot enjoys a chase.", "Your armor has gaps.", "I only need one clean shot."),
            "At last... a shot I could not see."
        ),
        new Profile(
            "vesper_the_widow_queen",
            "Struggle. The web tightens when you do.",
            List.of("My brood is hungry."),
            List.of("Every path leads deeper into my web.", "Your heartbeat shakes the silk.", "Come closer, little ember."),
            "The web... will remember you."
        ),
        new Profile(
            "mirewood_the_root_tyrant",
            "Every root beneath you answers to me.",
            List.of("The old roots wake."),
            List.of("The earth has already taken your measure.", "You cannot outrun the ground.", "Feed the briar."),
            "Even fallen roots... grow again."
        ),
        new Profile(
            "nereida_the_abyss_mother",
            "The drowned tide has come to collect.",
            List.of("Now breathe the deep."),
            List.of("The surface will not save you.", "Every breath belongs to the tide.", "Sink quietly."),
            "The tide... withdraws."
        ),
        new Profile(
            "iron_saint",
            "Kneel. The verdict is already forged.",
            List.of("Mercy has left the forge."),
            List.of("Your defense is an unfinished prayer.", "Iron remembers every strike.", "Confess beneath the hammer."),
            "The verdict... is yours."
        ),
        new Profile(
            "aurelion_the_rift_seraph",
            "Distance is a kindness I can revoke.",
            List.of("The rift closes around you."),
            List.of("You stand exactly where I intended.", "Space bends. You break.", "There is no safe distance."),
            "The path beyond me... is open."
        ),
        new Profile(
            "morvessa_the_runebloom_witch",
            "Come closer. The garden is hungry.",
            List.of("Bloom, my beautiful poisons."),
            List.of("Every cure begins with a better poison.", "The Runebloom knows your name.", "Mind the roots beneath the petals."),
            "Take the bloom... if it accepts you."
        ),
        new Profile(
            "voralith_the_crimson_warden",
            "Your heartbeat is loud in my dark.",
            List.of("Silence belongs to me."),
            List.of("The dark heard you coming.", "Your pulse betrays you.", "There is nowhere quiet enough to hide."),
            "For one moment... the dark is still."
        ),
        new Profile(
            "corrupted_oathkeeper",
            "Bring me your strongest oath. I will break it.",
            List.of("The oath bends. I do not.", "No more mercy. No more names."),
            List.of("Your vows sound hollow.", "Every oath breaks somewhere.", "The Veil already knows your name."),
            "Keep your oath. You earned its weight."
        )
    );
    private static final Map<String, Profile> BY_ID;

    static {
        Map<String, Profile> byId = new LinkedHashMap<>();
        for (Profile profile : PROFILES) {
            byId.put(profile.bossId(), profile);
        }
        BY_ID = Map.copyOf(byId);
    }

    private BossDialogue() {
    }

    public static List<Profile> profiles() {
        return PROFILES;
    }

    public static Profile profile(String bossId) {
        Profile profile = bossId == null ? null : BY_ID.get(bossId);
        if (profile == null) {
            throw new IllegalArgumentException("No boss dialogue profile for " + bossId);
        }
        return profile;
    }

    public record Profile(
        String bossId,
        String entranceLine,
        List<String> phaseLines,
        List<String> combatLines,
        String defeatLine
    ) {
        public Profile {
            phaseLines = List.copyOf(phaseLines);
            combatLines = List.copyOf(combatLines);
        }

        public String phaseLine(int phase) {
            int index = phase - 2;
            return index >= 0 && index < phaseLines.size() ? phaseLines.get(index) : "";
        }
    }
}
