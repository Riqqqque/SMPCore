package me.rique.smpcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public final class CustomLoreUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private CustomLoreUtil() {
    }

    public static List<Component> buildStyledLore(
        Material material,
        String tierLabel,
        String itemKind,
        List<String> topLines,
        List<LoreSection> sections
    ) {
        List<Component> lore = new ArrayList<>();
        appendRawLines(lore, topLines);

        for (LoreSection section : sections) {
            if (section == null || section.bodyLines().isEmpty()) {
                continue;
            }
            addSpacer(lore);
            lore.add(mm("<gold><bold>" + section.label() + ":</bold></gold> <yellow><bold>" + section.title() + "</bold></yellow>"));
            appendRawLines(lore, section.bodyLines());
        }

        addSpacer(lore);
        lore.add(mm(rarityLine(tierLabel, itemKind)));
        lore.add(mm("<dark_gray>minecraft:" + material.getKey().getKey() + "</dark_gray>"));
        return lore;
    }

    public static LoreSection section(String label, String title, String... bodyLines) {
        return new LoreSection(label, title, List.of(bodyLines));
    }

    public static Component mm(String raw) {
        return MM.deserialize(raw);
    }

    public static void addSpacer(List<Component> lore) {
        if (lore.isEmpty()) {
            return;
        }
        if (Component.empty().equals(lore.get(lore.size() - 1))) {
            return;
        }
        lore.add(Component.empty());
    }

    private static void appendRawLines(List<Component> lore, List<String> lines) {
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                addSpacer(lore);
            } else {
                lore.add(mm(line));
            }
        }
    }

    private static String rarityLine(String tierLabel, String itemKind) {
        String gradient = switch (tierLabel) {
            case "MYTHIC" -> "#ff4df0:#b56dff";
            case "CUSTOM" -> "#4deeea:#74ee15";
            case "ENCHANTED" -> "#7cf7c9:#58d68d";
            default -> "#ffd166:#f8961e";
        };
        return "<gradient:" + gradient + "><bold>" + tierLabel + " " + itemKind + "</bold></gradient>";
    }

    public record LoreSection(String label, String title, List<String> bodyLines) {
    }
}
