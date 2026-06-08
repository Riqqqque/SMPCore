package me.rique.smpcore.util;

import me.rique.smpcore.SMPCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class InventoryRecipeUtil {

    private InventoryRecipeUtil() {
    }

    public record Ingredient(String name, int amount, Predicate<ItemStack> matcher) {
        public Ingredient {
            amount = Math.max(0, amount);
        }
    }

    public static Ingredient plainMaterial(SMPCore plugin, Material material, int amount) {
        return new Ingredient(prettyMaterial(material), amount, item -> isPlainMaterial(plugin, item, material));
    }

    public static List<Ingredient> plainMaterials(SMPCore plugin, Map<Material, Integer> materials) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : materials.entrySet()) {
            ingredients.add(plainMaterial(plugin, entry.getKey(), entry.getValue()));
        }
        return ingredients;
    }

    public static boolean hasPlainMaterials(SMPCore plugin, Player player, Map<Material, Integer> materials) {
        return hasIngredients(player, plainMaterials(plugin, materials));
    }

    public static boolean removePlainMaterials(SMPCore plugin, Player player, Map<Material, Integer> materials) {
        return removeIngredients(player, plainMaterials(plugin, materials));
    }

    public static boolean hasIngredients(Player player, Collection<Ingredient> ingredients) {
        if (player == null) {
            return false;
        }
        for (Ingredient ingredient : ingredients) {
            if (countIngredient(player, ingredient) < ingredient.amount()) {
                return false;
            }
        }
        return true;
    }

    public static int countIngredient(Player player, Ingredient ingredient) {
        if (player == null || ingredient == null || ingredient.amount() <= 0) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (matches(ingredient, item)) {
                count += item.getAmount();
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (matches(ingredient, offhand)) {
            count += offhand.getAmount();
        }
        return count;
    }

    public static boolean removeIngredients(Player player, Collection<Ingredient> ingredients) {
        if (player == null || ingredients == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents().clone();
        ItemStack nextOffhand = cloneOrNull(inventory.getItemInOffHand());

        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.amount() <= 0) {
                continue;
            }
            int remaining = ingredient.amount();

            for (int i = 0; i < storage.length && remaining > 0; i++) {
                ItemStack item = storage[i];
                if (!matches(ingredient, item)) {
                    continue;
                }
                int take = Math.min(remaining, item.getAmount());
                storage[i] = reduce(item, take);
                remaining -= take;
            }

            if (remaining > 0 && matches(ingredient, nextOffhand)) {
                int take = Math.min(remaining, nextOffhand.getAmount());
                nextOffhand = reduce(nextOffhand, take);
                remaining -= take;
            }

            if (remaining > 0) {
                return false;
            }
        }

        inventory.setStorageContents(storage);
        inventory.setItemInOffHand(nextOffhand);
        return true;
    }

    public static void giveOrDrop(Player player, ItemStack reward) {
        if (player == null || reward == null || reward.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward.clone());
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.updateInventory();
    }

    public static boolean isPlainMaterial(SMPCore plugin, ItemStack item, Material material) {
        if (item == null || item.getType() != material || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || plugin == null) {
            return true;
        }
        String namespace = plugin.getName().toLowerCase(Locale.ROOT);
        return meta.getPersistentDataContainer().getKeys().stream()
            .noneMatch(key -> namespace.equals(key.getNamespace()));
    }

    private static boolean matches(Ingredient ingredient, ItemStack item) {
        return item != null
            && !item.getType().isAir()
            && ingredient.matcher() != null
            && ingredient.matcher().test(item);
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    private static ItemStack reduce(ItemStack item, int amount) {
        int left = item.getAmount() - amount;
        return left <= 0 ? null : item.asQuantity(left);
    }

    private static String prettyMaterial(Material material) {
        StringBuilder out = new StringBuilder();
        for (String part : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
