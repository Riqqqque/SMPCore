package me.rique.smpcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomLoreUtilTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void combinedModifiersAlwaysUseCanonicalCompactOrder() {
        List<Component> normalized = CustomLoreUtil.normalizeLore(List.of(
            Component.text("MYTHIC WEAPON"),
            Component.text("Final: Dmg 14 • Speed 1.7"),
            Component.text("Veilshift: +1.5 Attack Damage"),
            Component.empty(),
            Component.text("A short description."),
            Component.text("Reforge Stats: Dmg +6% • Taken +0% • Wear +5%"),
            Component.text("Corrupted: Exalted • x1.15 stats"),
            Component.text("Reforge: Brutal (+6% damage)"),
            Component.text("Enchants: Kingslayer I • Soul Siphon I"),
            Component.text("minecraft:netherite_sword"),
            Component.text("Sealed: no further item modifiers.")
        ));

        assertEquals(List.of(
            "A short description.",
            "",
            "Enchants: Kingslayer I • Soul Siphon I",
            "Reforge: Brutal (+6% damage)",
            "Reforge Stats: Dmg +6% • Taken +0%",
            "• Wear +5%",
            "Veilshift: +1.5 Attack Damage",
            "Corrupted: Exalted • x1.15 stats",
            "Final: Dmg 14 • Speed 1.7",
            "Sealed: no further item modifiers.",
            "",
            "MYTHIC WEAPON"
        ), normalized.stream().map(PLAIN::serialize).toList());
        assertEquals(normalized, CustomLoreUtil.normalizeLore(normalized));
    }

    @Test
    void normalizationRemovesExtraSpacersAndInternalIds() {
        List<Component> normalized = CustomLoreUtil.normalizeLore(List.of(
            Component.empty(),
            Component.text("Useful detail."),
            Component.empty(),
            Component.empty(),
            Component.text("minecraft:diamond_sword")
        ));

        assertEquals(List.of("Useful detail."), normalized.stream().map(PLAIN::serialize).toList());
    }

    @Test
    void longStyledLoreWrapsWithoutLosingWordsOrFormattingOrder() {
        List<Component> lore = CustomLoreUtil.buildStyledLore(
            null,
            org.bukkit.Material.DIAMOND_SWORD,
            "MYTHIC",
            "WEAPON",
            List.of("<gray>This intentionally long description explains the weapon without running beyond the edge of the player's screen.</gray>"),
            List.of(CustomLoreUtil.section(
                "Ability",
                "Measured Strike",
                "<gray><white>Right-click</white> to release a carefully aimed strike that rewards good positioning.</gray>"
            ))
        );

        List<String> plain = lore.stream().map(PLAIN::serialize).toList();
        String joined = String.join(" ", plain).replaceAll("\\s+", " ");
        assertTrue(plain.stream().allMatch(line -> line.codePointCount(0, line.length()) <= CustomLoreUtil.MAX_LORE_WIDTH));
        assertTrue(joined.contains("description explains the weapon"));
        assertTrue(joined.contains("ABILITY • Measured Strike"));
        assertEquals("MYTHIC WEAPON", plain.get(plain.size() - 1));
    }

    @Test
    void compactHeaderOnlySectionsDoNotWasteTooltipLines() {
        List<Component> lore = CustomLoreUtil.buildStyledLore(
            null,
            org.bukkit.Material.NETHERITE_AXE,
            "LEGENDARY",
            "WEAPON",
            List.of("<gray>A heavy axe from the machine chapel.</gray>"),
            List.of(
                CustomLoreUtil.section("Stats", "+3.75 Damage • +0.21 Speed"),
                CustomLoreUtil.section("Combat", "+30% Boss • -22% PvP"),
                CustomLoreUtil.section("Echo", "Iron Sentence", "<gray>+2.5 hit dmg • Weakness II 6s/9s CD.</gray>")
            )
        );

        assertEquals(List.of(
            "A heavy axe from the machine chapel.",
            "STATS • +3.75 Damage • +0.21 Speed",
            "COMBAT • +30% Boss • -22% PvP",
            "ECHO • Iron Sentence",
            "+2.5 hit dmg • Weakness II 6s/9s CD.",
            "",
            "LEGENDARY WEAPON"
        ), lore.stream().map(PLAIN::serialize).toList());
    }

    @Test
    void baseLoreRefreshCanPreserveEveryManagedModifier() {
        List<Component> modifiers = CustomLoreUtil.managedModifierLore(List.of(
            Component.text("Old base description."),
            Component.text("Reforge: Brutal (+6% damage)"),
            Component.text("Reforge Stats: Dmg +6% • Taken +0%"),
            Component.text("  • Wear +5%"),
            Component.text("Veilshift: +1.5 Attack Damage"),
            Component.text("Soul Imprint"),
            Component.text("LEGENDARY WEAPON")
        ));

        assertEquals(List.of(
            "Reforge: Brutal (+6% damage)",
            "Reforge Stats: Dmg +6% • Taken +0%",
            "  • Wear +5%",
            "Veilshift: +1.5 Attack Damage",
            "Soul Imprint"
        ), modifiers.stream().map(PLAIN::serialize).toList());
    }

    @Test
    void managedModifierRemovalAlsoRemovesWrappedContinuationLines() {
        List<Component> cleaned = CustomLoreUtil.removeManagedLines(List.of(
            Component.text("Base ability."),
            Component.text("Reforge Stats: Dmg +6% • Taken +0%"),
            Component.text("  • Wear +5%"),
            Component.text("Veilshift: +1.5 Attack Damage")
        ), Set.of("Reforge Stats:"));

        assertEquals(List.of("Base ability.", "Veilshift: +1.5 Attack Damage"),
            cleaned.stream().map(PLAIN::serialize).toList());
    }

    @Test
    void wrappedLoreStaysLeftAlignedAndRetainsContinuationMetadata() {
        List<Component> wrapped = CustomLoreUtil.wrapLoreLines(List.of(
            Component.text("Reforge Stats: Dmg +12% • Taken -4% • Wear -10%")
        ));

        assertTrue(wrapped.size() > 1);
        assertTrue(wrapped.stream().map(PLAIN::serialize).noneMatch(line -> line.startsWith(" ")));

        List<Component> normalized = CustomLoreUtil.normalizeLore(wrapped);
        assertEquals(normalized, CustomLoreUtil.normalizeLore(normalized));
        assertTrue(normalized.stream().map(PLAIN::serialize).noneMatch(line -> line.startsWith(" ")));

        List<Component> cleaned = CustomLoreUtil.removeManagedLines(normalized, Set.of("Reforge Stats:"));
        assertTrue(cleaned.stream().map(PLAIN::serialize).noneMatch(line -> line.contains("Wear -10%")));
    }
}
