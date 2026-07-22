package me.rique.smpcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void customEnchantsWrapUnderOneLeftAlignedHeader() {
        List<String> enchants = List.of(
            "Sharpness V", "Mending I", "Dash I", "Delicate I", "Echoing I", "Essence Capture I", "Frostbite I",
            "Kingslayer I", "Reinforced I", "Soul Siphon I", "Telekinesis I", "Wise III"
        );

        List<String> lines = CustomLoreUtil.formatCustomEnchantLore(enchants).stream()
            .map(PLAIN::serialize)
            .toList();
        String combined = String.join(" ", lines);

        assertTrue(lines.size() > 1);
        assertEquals(1L, lines.stream().filter(line -> line.startsWith("Enchants:")).count());
        assertTrue(lines.stream().noneMatch(line -> line.startsWith(" ")));
        assertTrue(lines.stream().allMatch(line -> line.codePointCount(0, line.length()) <= CustomLoreUtil.MAX_LORE_WIDTH));
        for (String enchant : enchants) {
            assertTrue(combined.contains(enchant));
        }
    }

    @Test
    void runicOvercapEnchantIsGoldWithoutRecoloringNormalEnchants() {
        List<Component> lore = CustomLoreUtil.formatCustomEnchantLore(
            List.of("Mending I", "Sharpness VII", "Unbreaking III"),
            Set.of("Sharpness VII")
        );

        assertEquals(NamedTextColor.GOLD, colorOf(lore, "Sharpness VII"));
        assertEquals(NamedTextColor.AQUA, colorOf(lore, "Mending I"));
        assertEquals(NamedTextColor.AQUA, colorOf(lore, "Unbreaking III"));
        assertTrue(CustomLoreUtil.isRunicEnhancedLevel(6, 5));
        assertTrue(CustomLoreUtil.isRunicEnhancedLevel(7, 5));
        assertFalse(CustomLoreUtil.isRunicEnhancedLevel(5, 5));
        assertFalse(CustomLoreUtil.isRunicEnhancedLevel(8, 5));
    }

    @Test
    void vanillaEnchantNamesUseReadableMinecraftWording() {
        assertEquals("Sharpness V", CustomLoreUtil.vanillaEnchantDisplay("sharpness", 5));
        assertEquals("Luck of the Sea III", CustomLoreUtil.vanillaEnchantDisplay("luck_of_the_sea", 3));
        assertEquals("Curse of Binding I", CustomLoreUtil.vanillaEnchantDisplay("binding_curse", 1));
        assertEquals("Bane of Arthropods IV", CustomLoreUtil.vanillaEnchantDisplay("bane_of_arthropods", 4));
    }

    @Test
    void enchantLoreMigrationRemovesOldHeadersAndWrappedContinuations() {
        List<Component> current = new java.util.ArrayList<>();
        current.add(Component.text("A weapon description."));
        current.addAll(CustomLoreUtil.formatCustomEnchantLore(List.of(
            "Dash I", "Delicate I", "Echoing I", "Essence Capture I", "Frostbite I"
        )));
        current.add(Component.text("Enchants: Kingslayer I • Reinforced I"));
        current.add(Component.text("  • Soul Siphon I"));
        current.add(Component.text("LEGENDARY WEAPON"));

        List<String> cleaned = CustomLoreUtil.stripCustomEnchantLore(current).stream()
            .map(PLAIN::serialize)
            .toList();

        assertEquals(List.of("A weapon description.", "LEGENDARY WEAPON"), cleaned);
        assertFalse(cleaned.stream().anyMatch(line -> line.contains("Enchants:") || line.contains("Soul Siphon")));
    }

    private static TextColor colorOf(List<Component> lines, String text) {
        for (Component line : lines) {
            TextColor color = colorOf(line, text);
            if (color != null) {
                return color;
            }
        }
        return null;
    }

    private static TextColor colorOf(Component component, String text) {
        if (component instanceof TextComponent textComponent && textComponent.content().contains(text)) {
            return textComponent.color();
        }
        for (Component child : component.children()) {
            TextColor color = colorOf(child, text);
            if (color != null) {
                return color;
            }
        }
        return null;
    }
}
