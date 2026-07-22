package me.rique.smpcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
        applyStyledItemFlags(meta);
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
        if (meta == null) {
            return false;
        }
        boolean changed = false;
        if (meta.hasAttributeModifiers() && !meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            changed = true;
        }
        if (hasVanillaEnchants(meta) && !meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            changed = true;
        }
        return changed;
    }

    public static List<Component> customEnchantLore(ItemMeta meta) {
        return formatCustomEnchantLore(customEnchantLines(meta), runicEnchantLines(meta));
    }

    static List<Component> formatCustomEnchantLore(List<String> enchants) {
        return formatCustomEnchantLore(enchants, Set.of());
    }

    static List<Component> formatCustomEnchantLore(List<String> enchants, Set<String> goldEnchants) {
        if (enchants == null || enchants.isEmpty()) {
            return List.of();
        }
        Set<String> highlighted = goldEnchants == null ? Set.of() : goldEnchants;
        Component line = Component.text("Enchants: ", NamedTextColor.AQUA);
        for (int index = 0; index < enchants.size(); index++) {
            String enchant = enchants.get(index);
            if (index > 0) {
                line = line.append(Component.text(" \u2022 ", NamedTextColor.DARK_GRAY));
            }
            line = line.append(Component.text(
                enchant,
                highlighted.contains(enchant) ? NamedTextColor.GOLD : NamedTextColor.AQUA
            ));
        }
        line = line.decoration(TextDecoration.ITALIC, false);
        return wrapLine(line);
    }

    public static List<Component> stripCustomEnchantLore(List<Component> current) {
        if (current == null || current.isEmpty()) {
            return new ArrayList<>();
        }
        List<Component> cleaned = new ArrayList<>();
        boolean removingContinuation = false;
        for (Component line : current) {
            if (line == null) {
                continue;
            }
            String rawPlain = PLAIN.serialize(line);
            String plain = rawPlain.trim();
            if (isEnchantLine(plain)) {
                removingContinuation = true;
                continue;
            }
            if (removingContinuation && isContinuation(line, rawPlain)) {
                continue;
            }
            removingContinuation = false;
            cleaned.add(line);
        }
        return cleaned;
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
        return wrapComponentLines(out);
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
        if (meta == null) {
            return false;
        }
        boolean conflictsChanged = resolveEnchantConflicts(meta);
        if (!isManagedItem(meta)) {
            if (conflictsChanged) {
                item.setItemMeta(meta);
            }
            return conflictsChanged;
        }
        List<Component> current = meta.lore() == null ? List.of() : meta.lore();
        List<Component> rebuilt = new ArrayList<>(customEnchantLore(meta));
        rebuilt.addAll(stripCustomEnchantLore(current));
        List<Component> normalized = normalizeLore(rebuilt);
        boolean loreChanged = !normalized.equals(current);
        if (loreChanged) {
            meta.lore(normalized);
        }
        boolean flagsChanged = applyStyledItemFlags(meta);
        boolean stackSizeChanged = false;
        if (item.getType() == Material.ENCHANTED_BOOK
            && (!meta.hasMaxStackSize() || meta.getMaxStackSize() != 1)) {
            meta.setMaxStackSize(1);
            stackSizeChanged = true;
        }
        if (!loreChanged && !flagsChanged && !stackSizeChanged && !conflictsChanged) {
            return false;
        }
        item.setItemMeta(meta);
        return true;
    }

    public static boolean refreshEnchantLore(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        boolean conflictsChanged = resolveEnchantConflicts(meta);
        List<Component> current = meta.lore() == null ? List.of() : meta.lore();
        List<Component> rebuilt = new ArrayList<>(customEnchantLore(meta));
        rebuilt.addAll(stripCustomEnchantLore(current));
        List<Component> normalized = normalizeLore(rebuilt);
        boolean loreChanged = !normalized.equals(current);
        if (loreChanged) {
            meta.lore(normalized);
        }
        boolean flagsChanged = applyStyledItemFlags(meta);
        if (!loreChanged && !flagsChanged && !conflictsChanged) {
            return false;
        }
        item.setItemMeta(meta);
        return true;
    }

    public static boolean hasSmeltingSilkConflict(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && hasManagedEnchant(meta, "smelting_touch_enchant")
            && (meta.hasEnchant(Enchantment.SILK_TOUCH)
                || meta instanceof EnchantmentStorageMeta storageMeta
                && storageMeta.hasStoredEnchant(Enchantment.SILK_TOUCH));
    }

    public static boolean hasAnyEnchantConflict(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (hasManagedEnchant(meta, "smelting_touch_enchant")
            && (meta.hasEnchant(Enchantment.SILK_TOUCH)
                || meta instanceof EnchantmentStorageMeta storageMeta
                && storageMeta.hasStoredEnchant(Enchantment.SILK_TOUCH))) {
            return true;
        }
        if (!vanillaConflictLosers(meta.getEnchants()).isEmpty()) {
            return true;
        }
        return meta instanceof EnchantmentStorageMeta storageMeta
            && !vanillaConflictLosers(storageMeta.getStoredEnchants()).isEmpty();
    }

    private static boolean resolveEnchantConflicts(ItemMeta meta) {
        boolean changed = false;
        if (hasManagedEnchant(meta, "smelting_touch_enchant")
            && ItemEnchantConflictPolicy.customConflictsWithVanilla("smelting_touch_enchant", "silk_touch")) {
            changed |= meta.removeEnchant(Enchantment.SILK_TOUCH);
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                changed |= storageMeta.removeStoredEnchant(Enchantment.SILK_TOUCH);
            }
        }
        for (Enchantment loser : vanillaConflictLosers(meta.getEnchants())) {
            changed |= meta.removeEnchant(loser);
        }
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            for (Enchantment loser : vanillaConflictLosers(storageMeta.getStoredEnchants())) {
                changed |= storageMeta.removeStoredEnchant(loser);
            }
        }
        return changed;
    }

    private static List<Enchantment> vanillaConflictLosers(Map<Enchantment, Integer> enchants) {
        if (enchants == null || enchants.size() < 2) {
            return List.of();
        }
        List<Map.Entry<Enchantment, Integer>> candidates = new ArrayList<>(enchants.entrySet());
        candidates.removeIf(entry -> entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0);
        candidates.sort((left, right) -> ItemEnchantConflictPolicy.compareVanillaCandidates(
            enchantKey(left.getKey()), left.getValue(), enchantKey(right.getKey()), right.getValue()
        ));

        List<Enchantment> accepted = new ArrayList<>();
        List<Enchantment> losers = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> candidate : candidates) {
            Enchantment enchantment = candidate.getKey();
            boolean conflicts = accepted.stream().anyMatch(existing ->
                existing.conflictsWith(enchantment) || enchantment.conflictsWith(existing));
            if (conflicts) {
                losers.add(enchantment);
            } else {
                accepted.add(enchantment);
            }
        }
        return losers;
    }

    private static boolean hasManagedEnchant(ItemMeta meta, String keyName) {
        if (meta == null || keyName == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (org.bukkit.NamespacedKey key : pdc.getKeys()) {
            if (!"smpcore".equalsIgnoreCase(key.getNamespace()) || !keyName.equals(key.getKey())) {
                continue;
            }
            Integer level = pdc.get(key, PersistentDataType.INTEGER);
            return level != null && level > 0;
        }
        return false;
    }

    private static String enchantKey(Enchantment enchantment) {
        return enchantment == null || enchantment.getKey() == null ? "" : enchantment.getKey().getKey();
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
        if (!runicEnchantLines(meta).isEmpty()) {
            return true;
        }
        if (meta.getPersistentDataContainer().getKeys().stream()
            .anyMatch(key -> "smpcore".equalsIgnoreCase(key.getNamespace()))) {
            return true;
        }
        List<Component> lore = meta.lore();
        if (lore == null) {
            return false;
        }
        for (Component line : lore) {
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
        List<String> lines = new ArrayList<>(vanillaEnchantLines(meta));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String customBookId = null;
        boolean customBookMarkerPresent = false;
        boolean replenishBookMarkerPresent = false;
        for (org.bukkit.NamespacedKey key : pdc.getKeys()) {
            if (!"smpcore".equalsIgnoreCase(key.getNamespace())) {
                continue;
            }
            if ("custom_enchant_book".equals(key.getKey())) {
                customBookMarkerPresent = true;
                customBookId = pdc.get(key, PersistentDataType.STRING);
            } else if ("replenish_book".equals(key.getKey())) {
                replenishBookMarkerPresent = true;
            }
        }
        String selectedCustomBookKey = customBookId == null || customBookId.isBlank()
            ? null
            : customBookId.toLowerCase(Locale.ROOT) + "_enchant";
        boolean hasCustomBookMarker = customBookMarkerPresent;
        boolean hasReplenishBookMarker = replenishBookMarkerPresent;
        pdc.getKeys().forEach(key -> {
            if (!"smpcore".equalsIgnoreCase(key.getNamespace())) {
                return;
            }
            if (("replenish_hoe".equals(key.getKey()) || "replenish_book".equals(key.getKey()))
                && pdc.has(key, PersistentDataType.BYTE)) {
                Byte marker = pdc.get(key, PersistentDataType.BYTE);
                if (marker != null && marker == (byte) 1
                    && !(hasCustomBookMarker && hasReplenishBookMarker)) {
                    lines.add("Replenish I");
                }
                return;
            }
            EnchantLine line = CUSTOM_ENCHANT_LINES.get(key.getKey());
            if (line == null) {
                return;
            }
            if (hasReplenishBookMarker
                || hasCustomBookMarker && !key.getKey().equals(selectedCustomBookKey)) {
                return;
            }
            Integer level = pdc.get(key, PersistentDataType.INTEGER);
            if (level != null && level > 0) {
                lines.add(line.display(level));
            }
        });
        Map<String, String> unique = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                unique.putIfAbsent(line, line);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean hasVanillaEnchants(ItemMeta meta) {
        return meta != null && (!meta.getEnchants().isEmpty()
            || meta instanceof EnchantmentStorageMeta storageMeta && !storageMeta.getStoredEnchants().isEmpty());
    }

    private static List<String> vanillaEnchantLines(ItemMeta meta) {
        if (meta == null) {
            return List.of();
        }
        Map<Enchantment, Integer> enchants = new HashMap<>(meta.getEnchants());
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            storageMeta.getStoredEnchants().forEach((enchantment, level) ->
                enchants.merge(enchantment, level, Math::max));
        }
        List<String> lines = new ArrayList<>();
        enchants.forEach((enchantment, level) -> {
            if (enchantment != null && level != null && level > 0) {
                lines.add(vanillaEnchantDisplay(enchantment.getKey().getKey(), level));
            }
        });
        lines.sort(String.CASE_INSENSITIVE_ORDER);
        return lines;
    }

    private static Set<String> runicEnchantLines(ItemMeta meta) {
        if (meta == null || meta.getEnchants().isEmpty()) {
            return Set.of();
        }
        Set<String> highlighted = new HashSet<>();
        meta.getEnchants().forEach((enchantment, level) -> {
            if (enchantment == null || level == null
                || !isRunicEnhancedLevel(level, enchantment.getMaxLevel())) {
                return;
            }
            highlighted.add(vanillaEnchantDisplay(enchantment.getKey().getKey(), level));
        });
        return highlighted;
    }

    static boolean isRunicEnhancedLevel(int level, int normalMaxLevel) {
        int safeMax = Math.max(1, normalMaxLevel);
        return level == safeMax + 1 || level == safeMax + 2;
    }

    static String vanillaEnchantDisplay(String key, int level) {
        String normalized = key == null ? "enchant" : key.trim().toLowerCase(Locale.ROOT);
        String name = switch (normalized) {
            case "bane_of_arthropods" -> "Bane of Arthropods";
            case "binding_curse" -> "Curse of Binding";
            case "luck_of_the_sea" -> "Luck of the Sea";
            case "vanishing_curse" -> "Curse of Vanishing";
            default -> titleWords(normalized);
        };
        return name + " " + roman(Math.max(1, level));
    }

    private static String titleWords(String raw) {
        String[] words = raw.split("_+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) out.append(word.substring(1));
        }
        return out.isEmpty() ? "Enchant" : out.toString();
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
            int level = Math.max(1, Math.min(Math.max(1, maxLevel), rawLevel));
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
