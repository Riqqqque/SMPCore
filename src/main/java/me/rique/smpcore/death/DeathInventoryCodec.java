package me.rique.smpcore.death;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

final class DeathInventoryCodec {

    static final int SCHEMA_VERSION = 1;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private DeathInventoryCodec() {
    }

    static InventoryPayload capture(Player player) {
        PlayerInventory inventory = player.getInventory();
        EncodedGroup storage = encodeGroup(inventory.getStorageContents());
        EncodedGroup armor = encodeGroup(inventory.getArmorContents());
        EncodedGroup extra = encodeGroup(inventory.getExtraContents());
        EncodedGroup crafting = encodeGroup(personalCraftingInputs(player));
        EncodedGroup cursor = encodeGroup(new ItemStack[]{player.getItemOnCursor()});
        int heldSlot = inventory.getHeldItemSlot();
        String fingerprint = fingerprint(
            storage.bytes(), armor.bytes(), extra.bytes(), crafting.bytes(), cursor.bytes(), heldSlot
        );
        return new InventoryPayload(storage, armor, extra, crafting, cursor, heldSlot, fingerprint);
    }

    static void write(YamlConfiguration data, InventoryPayload inventory) {
        data.set("inventory.encoding", "paper-raw-nbt-base64");
        data.set("inventory.fingerprint-algorithm", "SHA-256");
        data.set("inventory.fingerprint-sha256", inventory.fingerprint());
        data.set("inventory.held-hotbar-slot", inventory.heldSlot());
        writeGroup(data, "inventory.storage", inventory.storage());
        writeGroup(data, "inventory.armor", inventory.armor());
        writeGroup(data, "inventory.extra", inventory.extra());
        writeGroup(data, "inventory.personal-crafting-inputs", inventory.crafting());
        writeGroup(data, "inventory.cursor", inventory.cursor());
        data.set("inventory.stack-count", totalStackCount(inventory));
        data.set("inventory.item-count", totalItemCount(inventory));
        data.set("inventory.notes", List.of(
            "storage slots are hotbar 0-8 and main inventory 9-35",
            "armor order is boots, leggings, chestplate, helmet",
            "extra includes offhand and any Paper body or saddle slots",
            "personal-crafting-inputs contains the four 2x2 input slots, never the derived result",
            "nbt-base64 is authoritative; readable-slots is only an admin summary"
        ));
    }

    static InventoryPayload read(YamlConfiguration data) throws IOException {
        if (data.getInt("schema-version") != SCHEMA_VERSION) {
            throw new IOException("Unsupported death inventory schema version.");
        }
        EncodedGroup storage = readGroup(data, "inventory.storage");
        EncodedGroup armor = readGroup(data, "inventory.armor");
        EncodedGroup extra = readGroup(data, "inventory.extra");
        EncodedGroup crafting = readGroup(data, "inventory.personal-crafting-inputs");
        EncodedGroup cursor = readGroup(data, "inventory.cursor");
        if (crafting.items().length != 4) {
            throw new IOException("Personal crafting input group must contain exactly four slots.");
        }
        if (cursor.items().length != 1) {
            throw new IOException("Cursor group must contain exactly one slot.");
        }
        int heldSlot = data.getInt("inventory.held-hotbar-slot", -1);
        if (heldSlot < 0 || heldSlot > 8) {
            throw new IOException("Saved held hotbar slot is invalid.");
        }
        String actual = fingerprint(
            storage.bytes(), armor.bytes(), extra.bytes(), crafting.bytes(), cursor.bytes(), heldSlot
        );
        String expected = data.getString("inventory.fingerprint-sha256", "");
        if (!actual.equalsIgnoreCase(expected)) {
            throw new IOException("Inventory fingerprint does not match the saved NBT.");
        }
        return new InventoryPayload(storage, armor, extra, crafting, cursor, heldSlot, actual);
    }

    static void apply(Player player, InventoryPayload payload) {
        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(cloneItems(payload.storage().items()));
        inventory.setArmorContents(cloneItems(payload.armor().items()));
        inventory.setExtraContents(cloneItems(payload.extra().items()));
        inventory.setHeldItemSlot(payload.heldSlot());
        for (int slot = 0; slot < payload.crafting().items().length; slot++) {
            ItemStack item = payload.crafting().items()[slot];
            player.getOpenInventory().getTopInventory().setItem(slot + 1, item == null ? null : item.clone());
        }
        ItemStack cursor = payload.cursor().items()[0];
        player.setItemOnCursor(cursor == null ? ItemStack.empty() : cursor.clone());
        player.updateInventory();
    }

    static boolean slotCountsMatch(InventoryPayload saved, InventoryPayload current) {
        return DeathInventoryPolicy.compatibleSlotCounts(
            saved.storage().items().length,
            saved.armor().items().length,
            saved.extra().items().length,
            saved.crafting().items().length,
            current.storage().items().length,
            current.armor().items().length,
            current.extra().items().length,
            current.crafting().items().length
        );
    }

    static ItemStack[] personalCraftingInputs(Player player) {
        ItemStack[] inputs = new ItemStack[4];
        if (player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {
            return inputs;
        }
        for (int slot = 0; slot < inputs.length; slot++) {
            inputs[slot] = player.getOpenInventory().getTopInventory().getItem(slot + 1);
        }
        return inputs;
    }

    static int countStacks(List<ItemStack> items) {
        int count = 0;
        for (ItemStack item : items) {
            if (!isEmpty(item)) {
                count++;
            }
        }
        return count;
    }

    static int countItems(List<ItemStack> items) {
        int count = 0;
        for (ItemStack item : items) {
            if (!isEmpty(item)) {
                count += Math.max(0, item.getAmount());
            }
        }
        return count;
    }

    static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    private static void writeGroup(YamlConfiguration data, String path, EncodedGroup group) {
        data.set(path + ".slot-count", group.items().length);
        data.set(path + ".nbt-base64", Base64.getEncoder().encodeToString(group.bytes()));
        data.set(path + ".readable-slots", group.summaries());
    }

    private static EncodedGroup encodeGroup(ItemStack[] input) {
        ItemStack[] items = cloneItems(input);
        byte[] bytes = ItemStack.serializeItemsAsBytes(items);
        return new EncodedGroup(items, bytes, readableSlots(items));
    }

    private static EncodedGroup readGroup(YamlConfiguration data, String path) throws IOException {
        int expectedCount = data.getInt(path + ".slot-count", -1);
        String encoded = data.getString(path + ".nbt-base64");
        if (expectedCount < 0 || encoded == null || encoded.isBlank()) {
            throw new IOException("Missing encoded inventory group " + path + ".");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0) {
                throw new IOException("Encoded inventory group " + path + " is empty.");
            }
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(bytes);
            if (items.length != expectedCount) {
                throw new IOException("Slot count mismatch in " + path + ".");
            }
            return new EncodedGroup(items, bytes, data.getStringList(path + ".readable-slots"));
        } catch (IllegalArgumentException ex) {
            throw new IOException("Malformed Base64 or NBT in " + path + ".", ex);
        }
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        if (items == null) {
            return new ItemStack[0];
        }
        ItemStack[] cloned = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            cloned[i] = isEmpty(item) ? null : item.clone();
        }
        return cloned;
    }

    private static List<String> readableSlots(ItemStack[] items) {
        List<String> readable = new ArrayList<>(items.length);
        for (int slot = 0; slot < items.length; slot++) {
            ItemStack item = items[slot];
            if (isEmpty(item)) {
                readable.add(slot + ": empty");
                continue;
            }
            String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? PLAIN.serialize(item.getItemMeta().displayName())
                : item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            readable.add(slot + ": " + item.getAmount() + "x " + item.getType().getKey() + " — " + displayName);
        }
        return readable;
    }

    private static String fingerprint(
        byte[] storage,
        byte[] armor,
        byte[] extra,
        byte[] crafting,
        byte[] cursor,
        int heldSlot
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, "storage", storage);
            updateDigest(digest, "armor", armor);
            updateDigest(digest, "extra", extra);
            updateDigest(digest, "personal-crafting-inputs", crafting);
            updateDigest(digest, "cursor", cursor);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(heldSlot).array());
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private static void updateDigest(MessageDigest digest, String label, byte[] bytes) {
        byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(labelBytes.length).array());
        digest.update(labelBytes);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static int countStacks(ItemStack[] items) {
        return countStacks(Arrays.asList(items));
    }

    private static int countItems(ItemStack[] items) {
        return countItems(Arrays.asList(items));
    }

    private static int totalStackCount(InventoryPayload payload) {
        return countStacks(payload.storage().items())
            + countStacks(payload.armor().items())
            + countStacks(payload.extra().items())
            + countStacks(payload.crafting().items())
            + countStacks(payload.cursor().items());
    }

    private static int totalItemCount(InventoryPayload payload) {
        return countItems(payload.storage().items())
            + countItems(payload.armor().items())
            + countItems(payload.extra().items())
            + countItems(payload.crafting().items())
            + countItems(payload.cursor().items());
    }

    record EncodedGroup(ItemStack[] items, byte[] bytes, List<String> summaries) {
    }

    record InventoryPayload(
        EncodedGroup storage,
        EncodedGroup armor,
        EncodedGroup extra,
        EncodedGroup crafting,
        EncodedGroup cursor,
        int heldSlot,
        String fingerprint
    ) {
    }
}
