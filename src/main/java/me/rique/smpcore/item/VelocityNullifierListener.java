package me.rique.smpcore.item;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.ItemModelUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VelocityNullifierListener implements Listener {

    public static final String ITEM_ID = "velocity_nullifier";
    public static final long COOLDOWN_MILLIS = 15_000L;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final NamespacedKey MACE_SMASH_KEY = NamespacedKey.minecraft("mace_smash");
    private static final Material BASE_MATERIAL = Material.RECOVERY_COMPASS;

    private final SMPCore plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey cooldownKey;
    private final NamespacedKey recipeKey;

    public VelocityNullifierListener(SMPCore plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, ITEM_ID);
        this.cooldownKey = new NamespacedKey(plugin, "velocity_nullifier_cooldown_until");
        this.recipeKey = new NamespacedKey(plugin, ITEM_ID);
    }

    public void start() {
        registerRecipe();
        Bukkit.getScheduler().runTask(plugin, () ->
            Bukkit.getOnlinePlayers().forEach(player -> player.discoverRecipe(recipeKey))
        );
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(BASE_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, ITEM_ID);
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.RARE, "Velocity Nullifier"));
        ItemModelUtil.apply(meta, ITEM_ID);
        meta.setMaxStackSize(1);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            BASE_MATERIAL,
            CustomLoreUtil.Rarity.RARE.label(),
            "DEFENSIVE RELIC",
            List.of(
                "<gray>Works anywhere in your carried inventory.</gray>",
                "<gray>Extra copies share the same recharge.</gray>"
            ),
            List.of(CustomLoreUtil.section(
                "Passive",
                "Momentum Break",
                "<gray>Cancels one incoming <white>mace smash</white>, including its damage and impact.</gray>",
                "<gray>Recharges <white>15 seconds</white> after it triggers.</gray>",
                "<dark_gray>Ordinary melee hits do not consume the charge.</dark_gray>"
            ))
        ));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isNullifier(ItemStack item) {
        if (item == null || item.getType() != BASE_MATERIAL) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
            && ITEM_ID.equals(meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
    }

    public Map<Material, Integer> recipeIngredients() {
        Map<Material, Integer> ingredients = new LinkedHashMap<>();
        ingredients.put(Material.AMETHYST_SHARD, 4);
        ingredients.put(Material.COPPER_INGOT, 4);
        ingredients.put(Material.WIND_CHARGE, 1);
        return ingredients;
    }

    public ItemStack[] recipeMatrix() {
        return new ItemStack[] {
            new ItemStack(Material.AMETHYST_SHARD),
            new ItemStack(Material.COPPER_INGOT),
            new ItemStack(Material.AMETHYST_SHARD),
            new ItemStack(Material.COPPER_INGOT),
            new ItemStack(Material.WIND_CHARGE),
            new ItemStack(Material.COPPER_INGOT),
            new ItemStack(Material.AMETHYST_SHARD),
            new ItemStack(Material.COPPER_INGOT),
            new ItemStack(Material.AMETHYST_SHARD)
        };
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(recipeKey);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (isManagedRecipe(event.getRecipe()) && !usesOnlyPlainIngredients(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!isManagedRecipe(event.getRecipe()) || usesOnlyPlainIngredients(event.getInventory().getMatrix())) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.sendMessage(MessageUtil.warn("Use plain vanilla ingredients for the Velocity Nullifier recipe."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaceSmash(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)
            || !(event.getDamager() instanceof Player attacker)
            || victim.equals(attacker)
            || victim.getGameMode() == GameMode.SPECTATOR
            || event.getFinalDamage() <= 0.0D
            || !isQualifyingMaceHit(
                event.getDamageSource().getDamageType().getKey(),
                attacker.getInventory().getItemInMainHand().getType()
            )
            || !hasNullifier(victim)) {
            return;
        }

        if (plugin.getTeamManager() != null
            && plugin.getTeamManager().shouldBlockFriendlyDamage(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        if (plugin.getDuelManager() != null
            && plugin.getDuelManager().isDuelParticipant(victim)
            && !plugin.getDuelManager().areOpponents(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = cooldownUntil(victim);
        long remaining = remainingCooldownMillis(now, cooldownUntil);
        if (remaining > 0L) {
            victim.sendActionBar(MM.deserialize(
                "<gray>Velocity Nullifier recharging: <white>" + remainingCooldownSeconds(now, cooldownUntil) + "s</white></gray>"
            ));
            return;
        }

        event.setCancelled(true);
        victim.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, now + COOLDOWN_MILLIS);
        victim.setVelocity(new Vector());
        victim.setFallDistance(0.0F);

        victim.getWorld().spawnParticle(
            Particle.REVERSE_PORTAL,
            victim.getLocation().clone().add(0.0D, 1.0D, 0.0D),
            42,
            0.55D,
            0.75D,
            0.55D,
            0.08D
        );
        victim.getWorld().spawnParticle(
            Particle.ELECTRIC_SPARK,
            victim.getLocation().clone().add(0.0D, 1.0D, 0.0D),
            18,
            0.45D,
            0.55D,
            0.45D,
            0.06D
        );
        victim.playSound(victim.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.9F, 1.45F);
        victim.sendActionBar(MM.deserialize(
            "<aqua><bold>VELOCITY NULLIFIED</bold></aqua> <gray>15s recharge</gray>"
        ));
        attacker.sendActionBar(MM.deserialize(
            "<gray>A <aqua>Velocity Nullifier</aqua> absorbed your mace smash.</gray>"
        ));
    }

    static boolean isQualifyingMaceHit(NamespacedKey damageTypeKey, Material weaponType) {
        return MACE_SMASH_KEY.equals(damageTypeKey) && weaponType == Material.MACE;
    }

    static long remainingCooldownMillis(long now, long cooldownUntil) {
        return Math.max(0L, cooldownUntil - now);
    }

    static long remainingCooldownSeconds(long now, long cooldownUntil) {
        long remaining = remainingCooldownMillis(now, cooldownUntil);
        return remaining == 0L ? 0L : Math.max(1L, (remaining + 999L) / 1_000L);
    }

    private long cooldownUntil(Player player) {
        Long stored = player.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
        return stored == null ? 0L : stored;
    }

    private boolean hasNullifier(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isNullifier(item)) {
                return true;
            }
        }
        return isNullifier(player.getItemOnCursor());
    }

    private void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createItem());
        recipe.shape("ACA", "CWC", "ACA");
        recipe.setIngredient('A', Material.AMETHYST_SHARD);
        recipe.setIngredient('C', Material.COPPER_INGOT);
        recipe.setIngredient('W', Material.WIND_CHARGE);
        recipe.setGroup("smpcore_utility");
        Bukkit.addRecipe(recipe);
    }

    private boolean isManagedRecipe(Recipe recipe) {
        return recipe instanceof org.bukkit.Keyed keyed && recipeKey.equals(keyed.getKey());
    }

    private boolean usesOnlyPlainIngredients(ItemStack[] matrix) {
        if (matrix == null) {
            return false;
        }
        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (!InventoryRecipeUtil.isPlainMaterial(plugin, item, item.getType())) {
                return false;
            }
        }
        return true;
    }
}
