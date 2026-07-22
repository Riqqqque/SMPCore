package me.rique.smpcore.shop;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.AtomicYamlFile;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ShopPaymentLedger {

    private static final long MAX_STORED_PER_CURRENCY = 100_000_000L;

    private final SMPCore plugin;
    private final File file;
    private final Object lock = new Object();
    private final Map<UUID, Account> accounts = new LinkedHashMap<>();

    public ShopPaymentLedger(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop-payments.yml");
    }

    public void start() {
        synchronized (lock) {
            accounts.clear();
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = yaml.getConfigurationSection("accounts");
            if (root == null) return;
            for (String rawId : root.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(rawId);
                    ConfigurationSection section = root.getConfigurationSection(rawId);
                    if (section == null) continue;
                    Account account = new Account(section.getString("name", "Player"));
                    for (ShopCurrency currency : ShopCurrency.values()) {
                        long amount = Math.max(0L, section.getLong("payments." + currency.name(), 0L));
                        if (amount > 0L) account.payments.put(currency, Math.min(MAX_STORED_PER_CURRENCY, amount));
                    }
                    if (!account.payments.isEmpty()) accounts.put(playerId, account);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignored an invalid shop-payment account id: " + rawId);
                }
            }
        }
    }

    public void shutdown() {
        synchronized (lock) {
            saveLocked();
        }
    }

    public boolean credit(UUID ownerId, String ownerName, ShopCurrency currency, long amount) {
        if (ownerId == null || currency == null || amount <= 0L) return false;
        synchronized (lock) {
            Account account = accounts.computeIfAbsent(ownerId, ignored -> new Account(ownerName));
            String previousName = account.ownerName;
            long previous = account.payments.getOrDefault(currency, 0L);
            if (previous > MAX_STORED_PER_CURRENCY - amount) return false;
            account.ownerName = cleanName(ownerName, account.ownerName);
            account.payments.put(currency, previous + amount);
            if (saveLocked()) return true;
            account.ownerName = previousName;
            setAmount(account, currency, previous);
            removeIfEmpty(ownerId, account);
            return false;
        }
    }

    public List<String> summary(UUID ownerId) {
        synchronized (lock) {
            Account account = accounts.get(ownerId);
            if (account == null || account.payments.isEmpty()) return List.of();
            List<String> lines = new ArrayList<>();
            for (ShopCurrency currency : ShopCurrency.values()) {
                long amount = account.payments.getOrDefault(currency, 0L);
                if (amount > 0L) lines.add(amount + " " + currency.display(amount));
            }
            return List.copyOf(lines);
        }
    }

    public void collect(Player player) {
        if (player == null) return;
        Map<ShopCurrency, Long> claimed = new EnumMap<>(ShopCurrency.class);
        synchronized (lock) {
            Account account = accounts.get(player.getUniqueId());
            if (account == null || account.payments.isEmpty()) {
                player.sendMessage(MessageUtil.info("You have no shop payments waiting."));
                return;
            }

            ItemStack[] simulated = cloneContents(player.getInventory().getStorageContents());
            for (ShopCurrency currency : ShopCurrency.values()) {
                long waiting = account.payments.getOrDefault(currency, 0L);
                if (waiting <= 0L) continue;
                if (currency.isEssence()) {
                    if (plugin.getEssenceManager() != null
                        && plugin.getEssenceManager().isLoaded(player)
                        && plugin.getEssenceManager().canCreditFully(player, waiting)) {
                        claimed.put(currency, waiting);
                    }
                    continue;
                }
                long fitting = fittingAmount(simulated, currency.material(), waiting);
                if (fitting > 0L) claimed.put(currency, fitting);
            }

            if (claimed.isEmpty()) {
                player.sendMessage(MessageUtil.warn("Clear inventory space, or make room for the waiting Essence, then try again."));
                return;
            }

            Map<ShopCurrency, Long> previous = new EnumMap<>(account.payments);
            for (Map.Entry<ShopCurrency, Long> entry : claimed.entrySet()) {
                setAmount(account, entry.getKey(), account.payments.getOrDefault(entry.getKey(), 0L) - entry.getValue());
            }
            removeIfEmpty(player.getUniqueId(), account);
            if (!saveLocked()) {
                accounts.put(player.getUniqueId(), account);
                account.payments.clear();
                account.payments.putAll(previous);
                player.sendMessage(MessageUtil.error("Shop payments could not be saved. Nothing was collected."));
                return;
            }
        }

        Map<ShopCurrency, Long> failed = new EnumMap<>(ShopCurrency.class);
        for (Map.Entry<ShopCurrency, Long> entry : claimed.entrySet()) {
            ShopCurrency currency = entry.getKey();
            long amount = entry.getValue();
            if (currency.isEssence()) {
                if (plugin.getEssenceManager() == null || !plugin.getEssenceManager().credit(player, amount, "player shop payout")) {
                    failed.put(currency, amount);
                }
                continue;
            }
            long delivered = giveMaterial(player, currency.material(), amount);
            if (delivered < amount) failed.put(currency, amount - delivered);
        }

        if (!failed.isEmpty()) {
            for (Map.Entry<ShopCurrency, Long> entry : failed.entrySet()) {
                if (!credit(player.getUniqueId(), player.getName(), entry.getKey(), entry.getValue())) {
                    plugin.getLogger().severe("Could not restore an undelivered shop payout for " + player.getName() + ".");
                }
                claimed.computeIfPresent(entry.getKey(), (ignored, amount) -> amount - entry.getValue());
            }
        }
        player.updateInventory();
        List<String> delivered = new ArrayList<>();
        for (Map.Entry<ShopCurrency, Long> entry : claimed.entrySet()) {
            if (entry.getValue() > 0L) delivered.add(entry.getValue() + " " + entry.getKey().display(entry.getValue()));
        }
        if (delivered.isEmpty()) {
            player.sendMessage(MessageUtil.warn("No shop payments could be delivered. They are still waiting."));
        } else {
            player.sendMessage(MessageUtil.success("Collected <white>" + String.join(", ", delivered) + "</white> from your shops."));
        }
    }

    static long fittingAmount(ItemStack[] contents, Material material, long requested) {
        if (contents == null || material == null || requested <= 0L) return 0L;
        long capacity = 0L;
        int maxStack = Math.max(1, material.getMaxStackSize());
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) capacity += maxStack;
            else if (item.getType() == material && !item.hasItemMeta()) capacity += Math.max(0, maxStack - item.getAmount());
            if (capacity >= requested) break;
        }
        long fitting = Math.min(requested, capacity);
        addToCopy(contents, material, fitting);
        return fitting;
    }

    private long giveMaterial(Player player, Material material, long amount) {
        long remaining = amount;
        while (remaining > 0L) {
            int stack = (int) Math.min(remaining, Math.max(1, material.getMaxStackSize()));
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(material, stack));
            int left = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            remaining -= stack - left;
            if (left > 0) break;
        }
        return amount - remaining;
    }

    private boolean saveLocked() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        for (Map.Entry<UUID, Account> entry : accounts.entrySet()) {
            String path = "accounts." + entry.getKey();
            yaml.set(path + ".name", entry.getValue().ownerName);
            for (Map.Entry<ShopCurrency, Long> payment : entry.getValue().payments.entrySet()) {
                if (payment.getValue() > 0L) yaml.set(path + ".payments." + payment.getKey().name(), payment.getValue());
            }
        }
        try {
            AtomicYamlFile.save(yaml, file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save shop payments: " + ex.getMessage());
            return false;
        }
    }

    private static void setAmount(Account account, ShopCurrency currency, long amount) {
        if (amount <= 0L) account.payments.remove(currency);
        else account.payments.put(currency, amount);
    }

    private void removeIfEmpty(UUID ownerId, Account account) {
        if (account.payments.isEmpty()) accounts.remove(ownerId);
    }

    private static String cleanName(String requested, String fallback) {
        if (requested == null || requested.isBlank()) return fallback == null || fallback.isBlank() ? "Player" : fallback;
        return requested.trim().substring(0, Math.min(16, requested.trim().length()));
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) clone[i] = contents[i] == null ? null : contents[i].clone();
        return clone;
    }

    private static void addToCopy(ItemStack[] contents, Material material, long amount) {
        long remaining = amount;
        int maxStack = Math.max(1, material.getMaxStackSize());
        for (int i = 0; i < contents.length && remaining > 0L; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material || item.hasItemMeta()) continue;
            int add = (int) Math.min(remaining, Math.max(0, maxStack - item.getAmount()));
            if (add > 0) {
                contents[i] = item.asQuantity(item.getAmount() + add);
                remaining -= add;
            }
        }
        for (int i = 0; i < contents.length && remaining > 0L; i++) {
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir()) continue;
            int add = (int) Math.min(remaining, maxStack);
            contents[i] = new ItemStack(material, add);
            remaining -= add;
        }
    }

    private static final class Account {
        private String ownerName;
        private final EnumMap<ShopCurrency, Long> payments = new EnumMap<>(ShopCurrency.class);

        private Account(String ownerName) {
            this.ownerName = cleanName(ownerName, "Player");
        }
    }
}
