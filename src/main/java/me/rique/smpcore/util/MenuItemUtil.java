package me.rique.smpcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.regex.Pattern;

public final class MenuItemUtil {

    public static final String INACTIVE_SLOT_NAME = "<dark_gray> </dark_gray>";
    public static final List<String> INACTIVE_SLOT_LORE = List.of();

    private static final Component INACTIVE_SLOT_COMPONENT = Component.text(" ", NamedTextColor.DARK_GRAY)
        .decoration(TextDecoration.ITALIC, false);
    private static final List<Component> INACTIVE_SLOT_LORE_COMPONENTS = List.of();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern LEGACY_COLOR_CODE = Pattern.compile("(?i)(?:&|\\u00A7)[0-9a-fk-or]");

    private MenuItemUtil() {
    }

    public static String visibleMiniName(String name) {
        return isBlankMiniName(name) ? INACTIVE_SLOT_NAME : name;
    }

    public static List<String> visibleMiniLore(String name, List<String> lore) {
        List<String> safeLore = lore == null ? List.of() : lore;
        return isBlankMiniName(name) && safeLore.isEmpty()
            ? INACTIVE_SLOT_LORE
            : CustomLoreUtil.wrapMenuMiniMessageLines(safeLore);
    }

    public static Component visibleName(Component name) {
        return isBlankComponent(name) ? INACTIVE_SLOT_COMPONENT : name;
    }

    public static List<Component> visibleLore(Component name, List<Component> lore) {
        List<Component> safeLore = lore == null ? List.of() : lore;
        return isBlankComponent(name) && safeLore.isEmpty()
            ? INACTIVE_SLOT_LORE_COMPONENTS
            : CustomLoreUtil.wrapMenuLoreLines(safeLore);
    }

    public static boolean isVisibleItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }

    private static boolean isBlankMiniName(String name) {
        if (name == null) {
            return true;
        }
        String stripped = MINI_MESSAGE_TAG.matcher(name).replaceAll("");
        stripped = LEGACY_COLOR_CODE.matcher(stripped).replaceAll("");
        return stripped.trim().isEmpty();
    }

    private static boolean isBlankComponent(Component name) {
        return name == null || PLAIN.serialize(name).trim().isEmpty();
    }
}
