package me.rique.smpcore.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.Set;

public final class ItemModelUtil {
    private static final String NAMESPACE = "smpcore";
    private static final Set<String> VANILLA_BACKED_ITEM_IDS = Set.of(
        "advanced_pickaxe",
        "awakening_shard",
        "awakening_table",
        "corrupted_essence",
        "corruption_stone",
        "eclipse_seal",
        "faradays_magnet",
        "fate_crucible",
        "lone_star_engine",
        "runebloom_orb",
        "runic_loom",
        "soul_imprint",
        "the_world_clock",
        "veilshift_orb",
        "warden_lure_orb"
    );

    private ItemModelUtil() {
    }

    public static void apply(ItemMeta meta, String itemId) {
        if (meta == null || itemId == null || itemId.isBlank()) {
            return;
        }
        String normalized = itemId.toLowerCase(Locale.ROOT);
        if (VANILLA_BACKED_ITEM_IDS.contains(normalized)) {
            meta.setItemModel(null);
            return;
        }
        meta.setItemModel(new NamespacedKey(NAMESPACE, itemId));
    }

    public static boolean clearVanillaBackedModel(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !isVanillaBackedItemModel(meta.getItemModel())) {
            return false;
        }
        meta.setItemModel(null);
        item.setItemMeta(meta);
        return true;
    }

    public static int clearVanillaBackedModels(Inventory inventory) {
        if (inventory == null) {
            return 0;
        }
        int cleared = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (clearVanillaBackedModel(item)) {
                inventory.setItem(slot, item);
                cleared++;
            }
        }
        return cleared;
    }

    static boolean isVanillaBackedItemModel(NamespacedKey model) {
        return model != null
            && NAMESPACE.equals(model.getNamespace())
            && VANILLA_BACKED_ITEM_IDS.contains(model.getKey().toLowerCase(Locale.ROOT));
    }
}
