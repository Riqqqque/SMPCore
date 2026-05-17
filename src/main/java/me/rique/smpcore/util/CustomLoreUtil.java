package me.rique.smpcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class CustomLoreUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private CustomLoreUtil() {
    }

    public static List<Component> buildStyledLore(
        ItemMeta meta,
        Material material,
        String tierLabel,
        String itemKind,
        List<String> topLines,
        List<LoreSection> sections
    ) {
        List<Component> lore = new ArrayList<>();
        appendCustomEnchantLines(lore, meta);
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

    public static List<Component> buildStyledLore(
        Material material,
        String tierLabel,
        String itemKind,
        List<String> topLines,
        List<LoreSection> sections
    ) {
        return buildStyledLore(null, material, tierLabel, itemKind, topLines, sections);
    }

    public static LoreSection section(String label, String title, String... bodyLines) {
        return new LoreSection(label, title, List.of(bodyLines));
    }

    public static Component mm(String raw) {
        return MM.deserialize(raw);
    }

    public static Component displayName(Rarity rarity, String name) {
        return MM.deserialize(displayNameTag(rarity, name));
    }

    public static String displayNameTag(Rarity rarity, String name) {
        Rarity resolved = rarity == null ? Rarity.COMMON : rarity;
        return "<gradient:" + resolved.gradient() + "><bold>" + name + "</bold></gradient>";
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

    public static void applyStyledItemFlags(ItemMeta meta) {
        // Vanilla enchant presentation should stay visible on styled items.
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

    private static void appendCustomEnchantLines(List<Component> lore, ItemMeta meta) {
        if (meta == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        meta.getPersistentDataContainer().getKeys().forEach(key -> {
            switch (key.getKey()) {
                case "replenish_hoe" -> lines.add("Replenish I");
                case "delicate_enchant" -> lines.add("Delicate I");
                case "telekinesis_enchant" -> lines.add("Telekinesis I");
                default -> {
                }
            }
        });
        lines.sort(String::compareToIgnoreCase);
        for (String line : lines) {
            lore.add(mm("<gray>" + line + "</gray>"));
        }
    }

    private static String rarityLine(String tierLabel, String itemKind) {
        Rarity rarity = Rarity.fromLabel(tierLabel);
        return "<gradient:" + rarity.gradient() + "><bold>" + rarity.label() + " " + itemKind + "</bold></gradient>";
    }

    public record LoreSection(String label, String title, List<String> bodyLines) {
    }

    public enum Rarity {
        COMMON("COMMON", "#cbd5e1:#f8fafc"),
        UNCOMMON("UNCOMMON", "#74ee15:#22c55e"),
        RARE("RARE", "#4deeea:#2f80ed"),
        EPIC("EPIC", "#b56dff:#ff4df0"),
        LEGENDARY("LEGENDARY", "#ffd166:#f8961e"),
        MYTHIC("MYTHIC", "#ff4d6d:#8a2be2");

        private final String label;
        private final String gradient;

        Rarity(String label, String gradient) {
            this.label = label;
            this.gradient = gradient;
        }

        public String label() {
            return label;
        }

        public String gradient() {
            return gradient;
        }

        public static Rarity fromLabel(String label) {
            if (label == null || label.isBlank()) {
                return COMMON;
            }
            String normalized = label.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
            return switch (normalized) {
                case "MYTHICAL" -> MYTHIC;
                case "CUSTOM", "BOUND" -> UNCOMMON;
                case "ENCHANTED" -> RARE;
                default -> {
                    try {
                        yield Rarity.valueOf(normalized);
                    } catch (IllegalArgumentException ex) {
                        yield COMMON;
                    }
                }
            };
        }
    }
}
