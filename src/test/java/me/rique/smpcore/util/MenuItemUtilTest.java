package me.rique.smpcore.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuItemUtilTest {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void visibleMenuLoreWrapsLongLinesForJavaAndBedrockTooltips() {
        List<String> lore = MenuItemUtil.visibleMiniLore("<yellow>Info</yellow>", List.of(
            "<gray>This menu description is intentionally long enough to require safe wrapping on smaller clients.</gray>"
        ));

        assertTrue(lore.size() > 1);
        assertTrue(lore.stream()
            .map(MM::deserialize)
            .map(PLAIN::serialize)
            .allMatch(line -> line.codePointCount(0, line.length()) <= CustomLoreUtil.MAX_LORE_WIDTH));
    }

    @Test
    void wrappedMenuLoreKeepsContinuationLinesLeftAligned() {
        List<String> lore = MenuItemUtil.visibleMiniLore("<red>Combat Multipliers</red>", List.of(
            "<dark_gray>Vanilla attack and defense totals stay on Base Stats.</dark_gray>"
        ));
        List<String> plain = lore.stream().map(MM::deserialize).map(PLAIN::serialize).toList();

        assertTrue(plain.size() > 1);
        assertEquals("Base Stats.", plain.get(plain.size() - 1));
        assertTrue(plain.stream().noneMatch(line -> line.startsWith(" ")));
    }

    @Test
    void componentMenuLoreAlsoKeepsContinuationLinesLeftAligned() {
        List<String> plain = MenuItemUtil.visibleLore(
            net.kyori.adventure.text.Component.text("Info"),
            List.of(net.kyori.adventure.text.Component.text(
                "This component menu description is long enough to wrap without indenting its continuation."
            ))
        ).stream().map(PLAIN::serialize).toList();

        assertTrue(plain.size() > 1);
        assertTrue(plain.stream().noneMatch(line -> line.startsWith(" ")));
    }

    @Test
    void invisibleFillerSlotsStillKeepEmptyLore() {
        assertEquals(List.of(), MenuItemUtil.visibleMiniLore("<dark_gray> </dark_gray>", List.of()));
    }
}
