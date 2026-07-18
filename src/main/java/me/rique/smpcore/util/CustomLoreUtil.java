package me.rique.smpcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CustomLoreUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    public static final int MAX_LORE_WIDTH = 42;
    private static final String LEGACY_CONTINUATION_PREFIX = "  ";
    private static final String CONTINUATION_INSERTION = "smpcore:lore-continuation";
    private static final Map<String, EnchantLine> CUSTOM_ENCHANT_LINES = Map.ofEntries(
        Map.entry("delicate_enchant", new EnchantLine("Delicate", 1)),
        Map.entry("telekinesis_enchant", new EnchantLine("Telekinesis", 1)),
        Map.entry("smelting_touch_enchant", new EnchantLine("Smelting Touch", 1)),
        Map.entry("wise_enchant", new EnchantLine("Wise", 3)),
        Map.entry("double_jump_enchant", new EnchantLine("Double Jump", 1)),
        Map.entry("dash_enchant", new EnchantLine("Dash", 1)),
        Map.entry("frostbite_enchant", new EnchantLine("Frostbite", 2)),
        Map.entry("harvesting_enchant", new EnchantLine("Harvesting", 3)),
        Map.entry("bulwark_enchant", new EnchantLine("Bulwark", 3)),
        Map.entry("reinforced_enchant", new EnchantLine("Reinforced", 3)),
        Map.entry("kingslayer_enchant", new EnchantLine("Kingslayer", 1)),
        Map.entry("soul_siphon_enchant", new EnchantLine("Soul Siphon", 1)),
        Map.entry("echoing_enchant", new EnchantLine("Echoing", 1)),
        Map.entry("essence_capture_enchant", new EnchantLine("Essence Capture", 3))
    );

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

        boolean wroteSection = false;
        for (LoreSection section : sections) {
            if (section == null) {
                continue;
            }
            List<String> bodyLines = section.bodyLines() == null ? List.of() : section.bodyLines();
            boolean hasHeader = section.label() != null && !section.label().isBlank()
                && section.title() != null && !section.title().isBlank();
            if (!hasHeader && bodyLines.isEmpty()) {
                continue;
            }
            if (!wroteSection && topLines != null && !topLines.isEmpty()) {
                addSpacer(lore);
            }
            if (hasHeader) {
                lore.add(mm("<gold><bold>" + section.label().toUpperCase(Locale.ROOT)
                    + "</bold></gold> <dark_gray>•</dark_gray> <yellow><bold>" + section.title() + "</bold></yellow>"));
            }
            appendRawLines(lore, bodyLines);
            wroteSection = true;
        }

        addSpacer(lore);
        lore.add(mm(rarityLine(tierLabel, itemKind)));
        return normalizeLore(lore);
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

    public static boolean applyStyledItemFlags(ItemMeta meta) {
        if (meta == null || !meta.hasAttributeModifiers() || meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)) {
            return false;
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return true;
    }

    public static List<Component> customEnchantLore(ItemMeta meta) {
        List<String> enchants = customEnchantLines(meta);
        if (enchants.isEmpty()) {
            return List.of();
        }
        List<Component> lore = new ArrayList<>();
        List<String> line = new ArrayList<>();
        for (String enchant : enchants) {
            String candidate = "Enchants: " + String.join(" • ", line) + (line.isEmpty() ? "" : " • ") + enchant;
            if (!line.isEmpty() && candidate.codePointCount(0, candidate.length()) > MAX_LORE_WIDTH) {
                lore.add(enchantLine(line));
                line.clear();
            }
            line.add(enchant);
        }
        if (!line.isEmpty()) {
            lore.add(enchantLine(line));
        }
        return lore;
    }

    public static List<Component> normalizeLore(List<Component> current) {
        if (current == null || current.isEmpty()) {
            return current == null ? new ArrayList<>() : new ArrayList<>(current);
        }
        List<ClassifiedLine> enchants = new ArrayList<>();
        List<ClassifiedLine> base = new ArrayList<>();
        List<ClassifiedLine> modifiers = new ArrayList<>();
        List<ClassifiedLine> footer = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        LineKind previousKind = null;
        int previousRank = 100;
        int sourceIndex = 0;
        for (Component line : current) {
            if (line == null) {
                continue;
            }
            String rawPlain = PLAIN.serialize(line);
            String plain = rawPlain.trim();
            if (plain.isBlank() || plain.startsWith("minecraft:")) {
                continue;
            }
            LineKind kind;
            int rank = 100;
            if (isContinuation(line, rawPlain) && previousKind != null) {
                kind = previousKind;
                rank = previousRank;
            } else if (isEnchantLine(plain)) {
                kind = LineKind.ENCHANT;
                rank = 0;
            } else if (isModifierLine(plain)) {
                kind = LineKind.MODIFIER;
                rank = modifierRank(plain);
            } else if (isRarityFooter(plain)) {
                kind = LineKind.FOOTER;
            } else {
                kind = LineKind.BASE;
            }
            previousKind = kind;
            previousRank = rank;
            String dedupeKey = kind + "\u0000" + rank + "\u0000" + plain.toLowerCase(Locale.ROOT);
            if (!seen.add(dedupeKey)) {
                continue;
            }
            ClassifiedLine classified = new ClassifiedLine(line, kind, rank, sourceIndex++);
            switch (kind) {
                case ENCHANT -> enchants.add(classified);
                case BASE -> base.add(classified);
                case MODIFIER -> modifiers.add(classified);
                case FOOTER -> footer.add(classified);
            }
        }
        modifiers.sort(Comparator.comparingInt(ClassifiedLine::rank).thenComparingInt(ClassifiedLine::sourceIndex));
        List<Component> out = new ArrayList<>();
        appendClassified(out, base);
        if (!enchants.isEmpty() || !modifiers.isEmpty()) {
            addSpacer(out);
            appendClassified(out, enchants);
            appendClassified(out, modifiers);
        }
        if (!footer.isEmpty()) {
            addSpacer(out);
            appendClassified(out, footer);
        }
        return out;
    }

    public static List<Component> wrapLoreLines(List<Component> lines) {
        return wrapComponentLines(lines);
    }

    public static List<Component> wrapMenuLoreLines(List<Component> lines) {
        return wrapComponentLines(lines);
    }

    private static List<Component> wrapComponentLines(List<Component> lines) {
        List<Component> wrapped = new ArrayList<>();
        if (lines == null) {
            return wrapped;
        }
        for (Component line : lines) {
            if (line == null || PLAIN.serialize(line).isBlank()) {
                addSpacer(wrapped);
            } else {
                wrapped.addAll(wrapLine(line));
            }
        }
        return wrapped;
    }

    public static List<String> wrapMiniMessageLines(List<String> lines) {
        return wrapSerializedLines(lines);
    }

    public static List<String> wrapMenuMiniMessageLines(List<String> lines) {
        return wrapSerializedLines(lines);
    }

    private static List<String> wrapSerializedLines(List<String> lines) {
        List<String> wrapped = new ArrayList<>();
        if (lines == null) {
            return wrapped;
        }
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                wrapped.add("");
                continue;
            }
            for (Component component : wrapLine(MM.deserialize(line))) {
                wrapped.add(MM.serialize(component));
            }
        }
        return wrapped;
    }

    public static List<Component> removeManagedLines(List<Component> current, Set<String> prefixes) {
        List<Component> cleaned = new ArrayList<>();
        if (current == null || current.isEmpty() || prefixes == null || prefixes.isEmpty()) {
            return current == null ? cleaned : new ArrayList<>(current);
        }
        boolean removingContinuation = false;
        for (Component line : current) {
            String raw = line == null ? "" : PLAIN.serialize(line);
            String plain = raw.trim();
            boolean managed = prefixes.stream().anyMatch(plain::startsWith);
            if (managed) {
                removingContinuation = true;
                continue;
            }
            if (removingContinuation && isContinuation(line, raw)) {
                continue;
            }
            removingContinuation = false;
            cleaned.add(line);
        }
        return cleaned;
    }

    public static List<Component> managedModifierLore(List<Component> current) {
        List<Component> modifiers = new ArrayList<>();
        if (current == null || current.isEmpty()) {
            return modifiers;
        }
        boolean preservingContinuation = false;
        for (Component line : current) {
            if (line == null) {
                preservingContinuation = false;
                continue;
            }
            String raw = PLAIN.serialize(line);
            String plain = raw.trim();
            if (isContinuation(line, raw)) {
                if (preservingContinuation && !plain.isBlank()) {
                    modifiers.add(line);
                }
                continue;
            }
            preservingContinuation = isModifierLine(plain);
            if (preservingContinuation) {
                modifiers.add(line);
            }
        }
        return modifiers;
    }

    public static boolean normalizeItemLore(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.lore() == null || !isManagedItem(meta)) {
            return false;
        }
        List<Component> normalized = normalizeLore(meta.lore());
        boolean loreChanged = !normalized.equals(meta.lore());
        if (loreChanged) {
            meta.lore(normalized);
        }
        boolean flagsChanged = applyStyledItemFlags(meta);
        if (!loreChanged && !flagsChanged) {
            return false;
        }
        item.setItemMeta(meta);
        return true;
    }

    private static boolean isModifierLine(String plain) {
        return modifierRank(plain) < 100;
    }

    private static int modifierRank(String plain) {
        if (plain.startsWith("Reforge:")) return 10;
        if (plain.startsWith("Reforge Stats:")) return 11;
        if (plain.startsWith("Outgoing Damage:") || plain.startsWith("Damage Taken:")
            || plain.startsWith("Durability Loss:") || plain.startsWith("Draw Time:")) return 12;
        if (plain.startsWith("Boss Aggro:")) return 20;
        if (plain.startsWith("Veilshift:")) return 21;
        if (plain.startsWith("Awakened:")) return 30;
        if (plain.equalsIgnoreCase("Awakening Bonus")) return 30;
        if (plain.startsWith("Corruption:")) return 40;
        if (plain.startsWith("Corrupted:")) return 40;
        if (plain.startsWith("Stats:")) return 41;
        if (plain.startsWith("Final Stats:") || plain.startsWith("Final:")) return 43;
        if (plain.startsWith("Seal:") || plain.startsWith("Sealed:")) return 44;
        if (plain.equalsIgnoreCase("Soul Imprint")) return 50;
        if (plain.startsWith("Repairs Remaining:")) return 60;
        return 100;
    }

    private static boolean isEnchantLine(String plain) {
        return plain.startsWith("Enchants:") || plain.startsWith("Delicate ")
            || plain.startsWith("Telekinesis ") || plain.startsWith("Smelting Touch ") || plain.startsWith("Wise ")
            || plain.startsWith("Double Jump ") || plain.startsWith("Dash ") || plain.startsWith("Frostbite ")
            || plain.startsWith("Harvesting ") || plain.startsWith("Bulwark ") || plain.startsWith("Reinforced ")
            || plain.startsWith("Kingslayer ") || plain.startsWith("Soul Siphon ") || plain.startsWith("Echoing ")
            || plain.startsWith("Essence Capture ") || plain.startsWith("Replenish ");
    }

    private static boolean isRarityFooter(String plain) {
        for (Rarity rarity : Rarity.values()) {
            if (plain.startsWith(rarity.label() + " ")) {
                return true;
            }
        }
        return false;
    }

    private static void appendRawLines(List<Component> lore, List<String> lines) {
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                addSpacer(lore);
            } else {
                lore.addAll(wrapLine(mm(line)));
            }
        }
    }

    private static void appendCustomEnchantLines(List<Component> lore, ItemMeta meta) {
        lore.addAll(customEnchantLore(meta));
    }

    private static Component enchantLine(List<String> enchants) {
        return Component.text("Enchants: " + String.join(" • ", enchants), NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false);
    }

    private static void appendClassified(List<Component> target, List<ClassifiedLine> lines) {
        for (ClassifiedLine line : lines) {
            target.addAll(wrapLine(line.component()));
        }
    }

    private static List<Component> wrapLine(Component source) {
        List<StyledGlyph> glyphs = new ArrayList<>();
        collectGlyphs(source, Style.empty(), glyphs);
        if (glyphs.isEmpty()) {
            return List.of(Component.empty());
        }

        int existingIndent = 0;
        while (existingIndent < glyphs.size() && existingIndent < LEGACY_CONTINUATION_PREFIX.length()
            && glyphs.get(existingIndent).text().isBlank()) {
            existingIndent++;
        }
        List<StyledGlyph> content = glyphs.subList(existingIndent, glyphs.size());
        boolean sourceContinuation = isContinuation(source, PLAIN.serialize(source));
        List<Component> wrapped = new ArrayList<>();
        int start = 0;
        boolean first = true;
        while (start < content.size()) {
            while (start < content.size() && content.get(start).text().isBlank()) {
                start++;
            }
            if (start >= content.size()) {
                break;
            }
            int available = MAX_LORE_WIDTH;
            int hardEnd = Math.min(content.size(), start + available);
            int end = hardEnd;
            if (hardEnd < content.size()) {
                int bulletBreak = lastBulletBreak(content, start, hardEnd);
                int wordBreak = lastWhitespace(content, start, hardEnd);
                if (bulletBreak > start + 12) {
                    end = bulletBreak;
                } else if (wordBreak > start) {
                    end = wordBreak;
                }
            }
            while (end > start && content.get(end - 1).text().isBlank()) {
                end--;
            }
            if (end <= start) {
                end = Math.min(content.size(), start + available);
            }
            wrapped.add(buildLine(content, start, end, sourceContinuation || !first));
            start = end;
            first = false;
        }
        return wrapped.isEmpty() ? List.of(Component.empty()) : wrapped;
    }

    private static void collectGlyphs(Component component, Style inherited, List<StyledGlyph> glyphs) {
        Style effective = inherited.merge(component.style(), Style.Merge.Strategy.ALWAYS);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            text.content().codePoints().forEach(codePoint -> glyphs.add(new StyledGlyph(
                new String(Character.toChars(codePoint)),
                effective
            )));
        }
        for (Component child : component.children()) {
            collectGlyphs(child, effective, glyphs);
        }
    }

    private static int lastWhitespace(List<StyledGlyph> glyphs, int start, int end) {
        for (int index = end - 1; index > start; index--) {
            if (glyphs.get(index).text().isBlank()) {
                return index;
            }
        }
        return -1;
    }

    private static int lastBulletBreak(List<StyledGlyph> glyphs, int start, int end) {
        for (int index = end - 1; index > start; index--) {
            if ("•".equals(glyphs.get(index).text())) {
                int breakAt = index;
                while (breakAt > start && glyphs.get(breakAt - 1).text().isBlank()) {
                    breakAt--;
                }
                return breakAt;
            }
        }
        return -1;
    }

    private static Component buildLine(List<StyledGlyph> glyphs, int start, int end, boolean continuation) {
        Component line = Component.empty();
        if (continuation) {
            line = line.insertion(CONTINUATION_INSERTION);
        }
        int runStart = start;
        while (runStart < end) {
            Style style = glyphs.get(runStart).style();
            StringBuilder text = new StringBuilder();
            int runEnd = runStart;
            while (runEnd < end && style.equals(glyphs.get(runEnd).style())) {
                text.append(glyphs.get(runEnd).text());
                runEnd++;
            }
            line = line.append(Component.text(text.toString(), style));
            runStart = runEnd;
        }
        return line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE).compact();
    }

    private static boolean isContinuation(Component component, String rawPlain) {
        return rawPlain.startsWith(LEGACY_CONTINUATION_PREFIX) || hasContinuationMarker(component);
    }

    private static boolean hasContinuationMarker(Component component) {
        if (component == null) {
            return false;
        }
        if (CONTINUATION_INSERTION.equals(component.insertion())) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasContinuationMarker(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isManagedItem(ItemMeta meta) {
        if (meta.getPersistentDataContainer().getKeys().stream()
            .anyMatch(key -> "smpcore".equalsIgnoreCase(key.getNamespace()))) {
            return true;
        }
        for (Component line : meta.lore()) {
            String plain = PLAIN.serialize(line).trim();
            if (isEnchantLine(plain) || isModifierLine(plain) || isRarityFooter(plain)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> customEnchantLines(ItemMeta meta) {
        if (meta == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.getKeys().forEach(key -> {
            if (!"smpcore".equalsIgnoreCase(key.getNamespace())) {
                return;
            }
            if ("replenish_hoe".equals(key.getKey()) && pdc.has(key, PersistentDataType.BYTE)) {
                lines.add("Replenish I");
                return;
            }
            EnchantLine line = CUSTOM_ENCHANT_LINES.get(key.getKey());
            if (line == null) {
                return;
            }
            Integer level = pdc.get(key, PersistentDataType.INTEGER);
            if (level != null && level > 0) {
                lines.add(line.display(level));
            }
        });
        lines.sort(String::compareToIgnoreCase);
        return lines;
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(value);
        };
    }

    private record EnchantLine(String name, int maxLevel) {
        private String display(int rawLevel) {
            int level = Math.max(1, Math.min(255, rawLevel));
            return name + " " + roman(level);
        }
    }

    private static String rarityLine(String tierLabel, String itemKind) {
        Rarity rarity = Rarity.fromLabel(tierLabel);
        return "<gradient:" + rarity.gradient() + "><bold>" + rarity.label() + " " + itemKind + "</bold></gradient>";
    }

    public record LoreSection(String label, String title, List<String> bodyLines) {
    }

    private record StyledGlyph(String text, Style style) {
    }

    private record ClassifiedLine(Component component, LineKind kind, int rank, int sourceIndex) {
    }

    private enum LineKind {
        ENCHANT,
        BASE,
        MODIFIER,
        FOOTER
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
