package me.rique.smpcore.quest;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.boss.BossManager;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossMasteryManager implements Listener {

    static final List<Integer> RANK_REQUIREMENTS = List.of(1, 3, 6, 10, 15);
    private static final int[] BOSS_SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 22, 24};
    private static final int[] RANK_SLOTS = {11, 13, 15, 29, 31};
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long ELEVENTH_BELL_COOLDOWN_MS = 180_000L;
    private static final long ELEVENTH_BELL_DURATION_MS = 15_000L;
    static final double HUNTMARK_BOSS_DAMAGE_BONUS = 0.04;
    private static final long OATHKEEPER_RANK_TWO_ESSENCE = 300L;
    private static final Set<String> MASTERY_ARMOR_IDS = Set.of(
        "gloam_hunters_hood", "briar_hunters_coat", "depth_hunters_leggings", "argent_trailboots"
    );

    private static final String[] COMMON_REWARDS = {
        "gilded_skull", "solar_ember", "widow_silk", "living_bark", "abyssal_pearl",
        "titan_gear", "rift_lens", "rift_lens", "crimson_rib"
    };
    private static final String[] UTILITY_REWARDS = {
        "bloodbound_banner", "ember_vial", "widow_antidote", "root_sigil", "abyssal_conch",
        "titan_charm", "warped_key", "briar_snare", "veilflare_lantern", "eclipse_seal"
    };
    private static final String[] MASTERY_REWARDS = {
        "marshals_musterblade", "cindervale_deadeye", "gloam_hunters_hood", "briar_hunters_coat",
        "depth_hunters_leggings", "argent_trailboots", "riftbroker_glaive", "runebloom_hexstaff",
        "nocturne_bellhammer", "eleventh_bell"
    };

    private final SMPCore plugin;
    private final File dataFile;
    private final Map<UUID, Map<String, Progress>> progress = new HashMap<>();
    private final Set<UUID> claiming = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BellHunt> bellHunts = new ConcurrentHashMap<>();
    private final NamespacedKey itemIdKey;
    private final NamespacedKey menuActionKey;
    private final NamespacedKey introducedKey;
    private final NamespacedKey projectileBonusKey;
    private final NamespacedKey bellCooldownKey;
    private BukkitTask bellTask;

    public BossMasteryManager(SMPCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "boss-mastery.yml");
        this.itemIdKey = new NamespacedKey(plugin, "boss_mastery_item");
        this.menuActionKey = new NamespacedKey(plugin, "boss_mastery_action");
        this.introducedKey = new NamespacedKey(plugin, "bossbroker_intro");
        this.projectileBonusKey = new NamespacedKey(plugin, "boss_mastery_projectile_bonus");
        this.bellCooldownKey = new NamespacedKey(plugin, "eleventh_bell_cooldown");
    }

    public void start() {
        load();
        migrateRecordedVictories();
        Bukkit.getOnlinePlayers().forEach(this::refreshMasteryArmorDurability);
        bellTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBellHunts, 10L, 10L);
    }

    public void shutdown() {
        if (bellTask != null) bellTask.cancel();
        bellTask = null;
        bellHunts.clear();
        saveNow();
    }

    public void openFromNpc(Player player) {
        if (player == null) return;
        if (player.getPersistentDataContainer().has(introducedKey, PersistentDataType.BYTE)) {
            openMenu(player);
            return;
        }
        player.getPersistentDataContainer().set(introducedKey, PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(MM.deserialize("<green>Mogrik:</green> <white>Grikk's hidden goblins are his business. I pay for bigger targets.</white>"));
        player.sendMessage(MM.deserialize("<green>Mogrik:</green> <white>Beat each boss often enough and I'll open the good part of the vault.</white>"));
        player.sendMessage(MM.deserialize("<green>Mogrik:</green> <white>Five ranks per boss. Real victories only.</white>"));
        player.playSound(player.getLocation(), Sound.ENTITY_PILLAGER_AMBIENT, 0.65f, 0.9f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) openMenu(player);
        }, 20L);
    }

    public void openMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(
            new MasteryMenuHolder(player.getUniqueId(), null),
            45,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#65a30d:#facc15><bold>Mogrik's Boss Ledger</bold></gradient>"), "Boss Ledger")
        );
        fill(inventory);
        int totalRanks = 0;
        int readyRanks = 0;
        List<BossManager.BossType> bosses = BossManager.BossType.progressionOrder();
        for (int index = 0; index < bosses.size() && index < BOSS_SLOTS.length; index++) {
            BossManager.BossType boss = bosses.get(index);
            Progress state = progress(player.getUniqueId(), boss.id());
            totalRanks += state.claimedRank;
            if (state.claimedRank < RANK_REQUIREMENTS.size()
                && state.kills >= RANK_REQUIREMENTS.get(state.claimedRank)) readyRanks++;
            inventory.setItem(BOSS_SLOTS[index], menuItem(
                boss.menuIcon(),
                "<red><bold>" + boss.plainDisplayName() + "</bold></red>",
                List.of(
                    "<gray>Victories: <white>" + state.kills + "</white></gray>",
                    "<gray>Mastery: <gold>" + state.claimedRank + "/5</gold></gray>",
                    state.claimedRank >= 5
                        ? "<green>All rewards claimed.</green>"
                        : "<yellow>" + BedrockCompat.menuActionWord(player) + " to view five ranks.</yellow>"
                ),
                "boss:" + boss.id()
            ));
        }
        inventory.setItem(4, menuItem(Material.GOLD_BLOCK, "<gold><bold>BOSS MASTERY</bold></gold>", List.of(
            "<gray>Every eligible fighter earns one victory.</gray>",
            "<gray>Ranks unlock at <white>1, 3, 6, 10, and 15</white> wins.</gray>",
            "<gray>Claimed ranks: <gold>" + totalRanks + "/50</gold></gray>",
            readyRanks > 0 ? "<green>Rewards ready: " + readyRanks + "</green>" : "<gray>No rewards waiting.</gray>"
        ), null));
        inventory.setItem(40, menuItem(Material.BARRIER, "<red><bold>CLOSE</bold></red>", List.of(), "close"));
        player.openInventory(inventory);
    }

    private void openBoss(Player player, BossManager.BossType boss) {
        Progress state = progress(player.getUniqueId(), boss.id());
        deliverLegacyHuntmark(player, boss, state);
        Inventory inventory = Bukkit.createInventory(
            new MasteryMenuHolder(player.getUniqueId(), boss.id()),
            45,
            BedrockCompat.menuTitle(player, Component.text(boss.plainDisplayName()), boss.plainDisplayName())
        );
        fill(inventory);
        inventory.setItem(4, menuItem(boss.menuIcon(), "<red><bold>" + boss.plainDisplayName() + "</bold></red>", List.of(
            "<gray>Victories: <white>" + state.kills + "</white></gray>",
            "<gray>Claim ranks in order. Every rank is permanent.</gray>"
        ), null));
        for (int rank = 1; rank <= 5; rank++) {
            int required = RANK_REQUIREMENTS.get(rank - 1);
            Reward reward = rewardFor(boss, rank);
            RankState rankState = rankState(state, rank);
            String stateLine = switch (rankState) {
                case CLAIMED -> "<green>Claimed</green>";
                case READY -> "<yellow>" + BedrockCompat.menuActionWord(player) + " to claim</yellow>";
                case WAITING -> "<gray>Progress: <white>" + Math.min(state.kills, required) + "/" + required + "</white></gray>";
                case LOCKED -> "<dark_gray>Claim the previous rank first.</dark_gray>";
            };
            Material icon = switch (rankState) {
                case CLAIMED -> Material.LIME_STAINED_GLASS_PANE;
                case READY -> reward.icon;
                case WAITING -> Material.YELLOW_STAINED_GLASS_PANE;
                case LOCKED -> Material.GRAY_STAINED_GLASS_PANE;
            };
            inventory.setItem(RANK_SLOTS[rank - 1], menuItem(icon, rankState == RankState.READY
                ? "<green><bold>RANK " + roman(rank) + " READY</bold></green>"
                : "<gold><bold>RANK " + roman(rank) + "</bold></gold>", List.of(
                    "<gray>Requirement: <white>" + required + " victories</white></gray>",
                    "<gray>Reward: <white>" + reward.summary + "</white></gray>",
                    stateLine
                ), rankState == RankState.READY ? "claim:" + boss.id() + ":" + rank : null));
        }
        inventory.setItem(36, menuItem(Material.ARROW, "<yellow><bold>BACK</bold></yellow>", List.of("<gray>Return to all bosses.</gray>"), "back"));
        inventory.setItem(44, menuItem(Material.BARRIER, "<red><bold>CLOSE</bold></red>", List.of(), "close"));
        player.openInventory(inventory);
    }

    public void recordBossDefeat(String bossId, Collection<UUID> participants) {
        BossManager.BossType boss = BossManager.BossType.fromId(bossId);
        if (boss == null || participants == null || participants.isEmpty()) return;
        boolean changed = false;
        for (UUID playerId : new LinkedHashSet<>(participants)) {
            if (playerId == null) continue;
            Progress state = progress(playerId, boss.id());
            if (state.kills < Integer.MAX_VALUE) state.kills++;
            changed = true;
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && state.claimedRank < 5
                && state.kills == RANK_REQUIREMENTS.get(state.claimedRank)) {
                player.sendMessage(MessageUtil.success("Mogrik has a new <white>" + boss.plainDisplayName() + "</white> reward ready."));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.65f, 1.4f);
            }
        }
        if (changed && !saveNow()) {
            plugin.getLogger().severe("Boss mastery victories could not be saved.");
        }
    }

    public int kills(UUID playerId, String bossId) {
        BossManager.BossType boss = BossManager.BossType.fromId(bossId);
        return boss == null ? 0 : progress(playerId, boss.id()).kills;
    }

    public void setKillsForAdmin(Player player, BossManager.BossType boss, int amount) {
        Progress state = progress(player.getUniqueId(), boss.id());
        state.kills = Math.max(0, amount);
        if (!saveNow()) player.sendMessage(MessageUtil.error("Could not save Boss Mastery data."));
        else player.sendMessage(MessageUtil.info("Boss Mastery test progress updated for <white>" + boss.plainDisplayName() + "</white>."));
    }

    public void resetForAdmin(Player player) {
        progress.remove(player.getUniqueId());
        if (!saveNow()) player.sendMessage(MessageUtil.error("Could not save Boss Mastery data."));
        else player.sendMessage(MessageUtil.info("Boss Mastery progress reset."));
    }

    public ItemStack createMasteryItem(String id) {
        String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("huntmark_")) {
            BossManager.BossType boss = BossManager.BossType.fromId(normalized.substring("huntmark_".length()));
            return boss == null ? null : huntmark(boss);
        }
        return switch (normalized) {
            case "marshals_musterblade" -> weapon(Material.DIAMOND_SWORD, id, "Marshal's Musterblade", 0.12,
                "A clean early boss weapon.", Enchantment.SHARPNESS, 5);
            case "cindervale_deadeye" -> weapon(Material.CROSSBOW, id, "Cindervale Deadeye", 0.15,
                "Built for steady ranged boss pressure.", Enchantment.QUICK_CHARGE, 3);
            case "gloam_hunters_hood" -> armor(Material.DIAMOND_HELMET, id, "Gloam Hunter's Hood", "Head");
            case "briar_hunters_coat" -> armor(Material.DIAMOND_CHESTPLATE, id, "Briar Hunter's Coat", "Chest");
            case "depth_hunters_leggings" -> armor(Material.DIAMOND_LEGGINGS, id, "Depth Hunter's Leggings", "Legs");
            case "argent_trailboots" -> armor(Material.DIAMOND_BOOTS, id, "Argent Trailboots", "Feet");
            case "riftbroker_glaive" -> weapon(Material.NETHERITE_SWORD, id, "Riftbroker Glaive", 0.22,
                "Cuts harder during custom boss fights.", Enchantment.SHARPNESS, 6);
            case "runebloom_hexstaff" -> weapon(Material.MACE, id, "Runebloom Hexstaff", 0.25,
                "A heavy focus for late-path bosses.", Enchantment.DENSITY, 5);
            case "nocturne_bellhammer" -> weapon(Material.NETHERITE_AXE, id, "Nocturne Bellhammer", 0.28,
                "Mogrik's strongest direct boss weapon.", Enchantment.SHARPNESS, 6);
            case "eleventh_bell" -> eleventhBell();
            default -> null;
        };
    }

    public boolean isMasteryItem(ItemStack item) {
        return masteryId(item) != null;
    }

    private ItemStack weapon(Material material, String id, String name, double bossBonus, String description, Enchantment mainEnchant, int level) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.LEGENDARY, name));
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        meta.addEnchant(mainEnchant, level, true);
        if (material == Material.CROSSBOW) meta.addEnchant(Enchantment.PIERCING, 4, true);
        if (material == Material.MACE) meta.addEnchant(Enchantment.BREACH, 3, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(CustomLoreUtil.buildStyledLore(meta, material, "LEGENDARY", "BOSS MASTERY WEAPON", List.of(
            "<gray>" + description + "</gray>"
        ), List.of(
            CustomLoreUtil.section("PVE", "Bossbreaker", "<green>+" + percent(bossBonus) + " damage to custom bosses.</green>"),
            CustomLoreUtil.section("Source", "Mogrik's Ledger", "<gray>Earned from fifteen victories.</gray>")
        )));
        CustomLoreUtil.applyStyledItemFlags(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack armor(Material material, String id, String name, String slot) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.LEGENDARY, name));
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 4, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        if (meta instanceof Damageable damageable && material.getMaxDurability() > 0) {
            damageable.setMaxDamage(material.getMaxDurability() * 8);
            damageable.setDamage(0);
            meta = damageable;
        }
        if (material == Material.DIAMOND_BOOTS) meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(CustomLoreUtil.buildStyledLore(meta, material, "LEGENDARY", "BOSS MASTERY ARMOR", List.of(
            "<gray>Bossbroker's Pursuit set - " + slot + ".</gray>"
        ), List.of(
            CustomLoreUtil.section("Full Set", "Relentless Pursuit", "<green>+15% damage to custom bosses.</green>", "<aqua>12% less ordinary boss damage.</aqua>", "<dark_gray>Failed mechanics ignore this defense.</dark_gray>")
        )));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack huntmark(BossManager.BossType boss) {
        ItemStack item = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = item.getItemMeta();
        String id = huntmarkId(boss);
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.EPIC, boss.plainDisplayName() + " Huntmark"));
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        meta.lore(CustomLoreUtil.buildStyledLore(meta, Material.RECOVERY_COMPASS, "EPIC", "BOSS MASTERY HUNTMARK", List.of(
            "<gray>Mogrik's proof that this quarry is no longer new to you.</gray>"
        ), List.of(
            CustomLoreUtil.section("Permanent", "Studied Quarry", "<green>+" + percent(HUNTMARK_BOSS_DAMAGE_BONUS) + " damage to " + boss.plainDisplayName() + ".</green>"),
            CustomLoreUtil.section("Keepsake", "No Need to Carry", "<gray>The bonus stays active while this mark is stored.</gray>")
        )));
        CustomLoreUtil.applyStyledItemFlags(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack eleventhBell() {
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(CustomLoreUtil.displayName(CustomLoreUtil.Rarity.MYTHIC, "The Eleventh Bell"));
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, "eleventh_bell");
        meta.lore(CustomLoreUtil.buildStyledLore(meta, Material.BELL, "MYTHIC", "BOSS MASTERY TRINKET", List.of(
            "<gray>A prize from the last page of Mogrik's ledger.</gray>"
        ), List.of(
            CustomLoreUtil.section("Sneak-Use", "Call the Hunt", "<gray>Mark up to 8 nearby hostile mobs for 15 seconds.</gray>", "<gray>Hits echo 25% damage to the other marks.</gray>"),
            CustomLoreUtil.section("Safety", "Ordinary Mobs Only", "<gray>Never targets players, bosses, minions, NPCs, or familiars.</gray>"),
            CustomLoreUtil.section("Cooldown", "180 Seconds", "<gray>Hold it in either hand and sneak-right-click.</gray>")
        )));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowShot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Projectile projectile)) return;
        double bonus = weaponBonus(event.getBow());
        if (bonus > 0.0) projectile.getPersistentDataContainer().set(projectileBonusKey, PersistentDataType.DOUBLE, bonus);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager());
        String bossId = plugin.getBossManager().customBossId(event.getEntity());
        if (attacker != null && bossId != null) {
            double weapon = event.getDamager() instanceof Projectile projectile
                ? projectile.getPersistentDataContainer().getOrDefault(projectileBonusKey, PersistentDataType.DOUBLE, 0.0)
                : weaponBonus(attacker.getInventory().getItemInMainHand());
            double set = wearsPursuitSet(attacker) ? 0.15 : 0.0;
            double huntmark = huntmarkBonus(attacker.getUniqueId(), bossId);
            if (weapon + set + huntmark > 0.0) {
                event.setDamage(event.getDamage() * (1.0 + weapon + set + huntmark));
            }
        }

        if (event.getEntity() instanceof Player victim && wearsPursuitSet(victim)
            && isBossDamage(event.getDamager()) && !plugin.getBossManager().isLethalBossMechanicDamage(victim)) {
            event.setDamage(event.getDamage() * 0.88);
        }

        if (attacker != null) echoBellDamage(attacker, event.getEntity(), event.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBellUse(PlayerInteractEvent event) {
        if (event.getHand() == null || !event.getAction().isRightClick()) return;
        ItemStack item = event.getItem();
        if (!"eleventh_bell".equals(masteryId(item))) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            player.sendActionBar(MM.deserialize("<gray>Sneak-right-click to call the hunt.</gray>"));
            return;
        }
        long now = System.currentTimeMillis();
        long readyAt = player.getPersistentDataContainer().getOrDefault(bellCooldownKey, PersistentDataType.LONG, 0L);
        if (readyAt > now) {
            player.sendActionBar(MM.deserialize("<gray>Eleventh Bell: <white>" + ((readyAt - now + 999L) / 1000L) + "s</white></gray>"));
            return;
        }
        Set<UUID> targets = player.getNearbyEntities(12.0, 8.0, 12.0).stream()
            .filter(entity -> entity instanceof Monster)
            .filter(Entity::isValid)
            .filter(entity -> !entity.isDead())
            .filter(entity -> !plugin.getBossManager().isBossEncounterEntity(entity))
            .sorted((a, b) -> Double.compare(a.getLocation().distanceSquared(player.getLocation()), b.getLocation().distanceSquared(player.getLocation())))
            .limit(8)
            .map(Entity::getUniqueId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (targets.size() < 2) {
            player.sendMessage(MessageUtil.warn("The Eleventh Bell needs at least two nearby hostile mobs."));
            return;
        }
        player.getPersistentDataContainer().set(bellCooldownKey, PersistentDataType.LONG, now + ELEVENTH_BELL_COOLDOWN_MS);
        bellHunts.put(player.getUniqueId(), new BellHunt(now + ELEVENTH_BELL_DURATION_MS, targets));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.1f, 0.65f);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0, 1.0, 0.0), 55, 1.0, 0.8, 1.0, 0.2);
        player.sendActionBar(MM.deserialize("<gold><bold>THE HUNT IS CALLED</bold></gold> <gray>15s</gray>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MasteryMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId.equals(player.getUniqueId())) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        String action = action(event.getCurrentItem());
        if (action == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> handleAction(player, action));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MasteryMenuHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bellHunts.remove(event.getPlayer().getUniqueId());
        claiming.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshMasteryArmorDurability(event.getPlayer());
    }

    private void refreshMasteryArmorDurability(Player player) {
        if (player == null) return;
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            String id = masteryId(item);
            if (id == null || !MASTERY_ARMOR_IDS.contains(id)) continue;
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof Damageable damageable) || item.getType().getMaxDurability() <= 0) continue;
            int maxDamage = item.getType().getMaxDurability() * 8;
            damageable.setMaxDamage(maxDamage);
            damageable.setDamage(Math.max(0, Math.min(damageable.getDamage(), maxDamage - 1)));
            if (damageable.getEnchantLevel(Enchantment.UNBREAKING) < 4) damageable.addEnchant(Enchantment.UNBREAKING, 4, true);
            item.setItemMeta(damageable);
            changed = true;
        }
        if (changed) player.updateInventory();
    }

    private void handleAction(Player player, String action) {
        if (action.equals("close")) {
            player.closeInventory();
            return;
        }
        if (action.equals("back")) {
            openMenu(player);
            return;
        }
        if (action.startsWith("boss:")) {
            BossManager.BossType boss = BossManager.BossType.fromId(action.substring(5));
            if (boss != null) openBoss(player, boss);
            return;
        }
        String[] parts = action.split(":");
        if (parts.length != 3 || !parts[0].equals("claim")) return;
        BossManager.BossType boss = BossManager.BossType.fromId(parts[1]);
        int rank;
        try {
            rank = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
            return;
        }
        if (boss != null) claim(player, boss, rank);
    }

    private void claim(Player player, BossManager.BossType boss, int rank) {
        if (!claiming.add(player.getUniqueId())) return;
        try {
            Progress state = progress(player.getUniqueId(), boss.id());
            if (rank < 1 || rank > 5 || rank != state.claimedRank + 1 || state.kills < RANK_REQUIREMENTS.get(rank - 1)) {
                player.sendMessage(MessageUtil.warn("That mastery rank is not ready."));
                openBoss(player, boss);
                return;
            }
            Reward reward = rewardFor(boss, rank);
            ItemStack item = reward.item();
            if (item != null && !canFit(player.getInventory(), item)) {
                player.sendMessage(MessageUtil.warn("Make room for the reward, then claim it again."));
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.7f, 0.9f);
                return;
            }
            if (reward.essence > 0 && !plugin.getEssenceManager().canCreditFully(player, reward.essence)) {
                player.sendMessage(MessageUtil.warn("Your Essence account is still loading or cannot hold the full reward."));
                return;
            }

            int previous = state.claimedRank;
            boolean previousHuntmarkGranted = state.huntmarkGranted;
            state.claimedRank = rank;
            if (rank == 4) state.huntmarkGranted = true;
            if (!saveNow()) {
                state.claimedRank = previous;
                state.huntmarkGranted = previousHuntmarkGranted;
                player.sendMessage(MessageUtil.error("The ledger could not save your claim. Nothing was consumed."));
                return;
            }

            if (item != null) {
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
                if (!leftovers.isEmpty()) {
                    plugin.getLogger().severe("Preflight inventory check failed for Boss Mastery reward " + reward.summary + " and " + player.getUniqueId());
                    leftovers.values().forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
                }
            }
            if (reward.essence > 0 && !plugin.getEssenceManager().credit(player, reward.essence, "boss_mastery_rank_" + rank)) {
                plugin.getLogger().warning("Could not credit " + reward.essence + " Essence for Boss Mastery claim by " + player.getUniqueId());
            }
            player.sendMessage(MessageUtil.success("Claimed <white>" + boss.plainDisplayName() + " Rank " + roman(rank) + "</white>: " + reward.summary + "."));
            player.playSound(player.getLocation(), rank == 5 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.8f, rank == 5 ? 0.9f : 1.35f);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0, 1.0, 0.0), rank == 5 ? 45 : 20, 0.5, 0.7, 0.5, 0.05);
            openBoss(player, boss);
        } finally {
            claiming.remove(player.getUniqueId());
        }
    }

    private Reward rewardFor(BossManager.BossType boss, int rank) {
        int index = Math.max(0, Math.min(9, boss.progressionTier() - 1));
        return switch (rank) {
            case 1 -> new Reward(Material.AMETHYST_SHARD, null, 0, 40L + boss.progressionTier() * 20L,
                (40 + boss.progressionTier() * 20) + " Essence");
            case 2 -> {
                String commonReward = commonRewardId(boss.progressionTier());
                yield commonReward == null
                    ? new Reward(Material.AMETHYST_SHARD, null, 0, OATHKEEPER_RANK_TWO_ESSENCE, OATHKEEPER_RANK_TWO_ESSENCE + " Essence")
                    : relicReward(commonReward, 3 + Math.min(5, boss.progressionTier()));
            }
            case 3 -> relicReward(UTILITY_REWARDS[index], 1);
            case 4 -> huntmarkReward(boss);
            case 5 -> new Reward(masteryIcon(index), MASTERY_REWARDS[index], 1, 100L + boss.progressionTier() * 40L,
                masteryName(MASTERY_REWARDS[index]) + " + " + (100 + boss.progressionTier() * 40) + " Essence");
            default -> throw new IllegalArgumentException("rank");
        };
    }

    static String commonRewardId(int tier) {
        int index = tier - 1;
        return index < 0 || index >= COMMON_REWARDS.length ? null : COMMON_REWARDS[index];
    }

    static double huntmarkDamageBonusForRank(int claimedRank) {
        return claimedRank >= 4 ? HUNTMARK_BOSS_DAMAGE_BONUS : 0.0;
    }

    private Reward huntmarkReward(BossManager.BossType boss) {
        return new Reward(
            Material.RECOVERY_COMPASS,
            huntmarkId(boss),
            1,
            0L,
            boss.plainDisplayName() + " Huntmark (+" + percent(HUNTMARK_BOSS_DAMAGE_BONUS) + " damage)"
        );
    }

    private String huntmarkId(BossManager.BossType boss) {
        return "huntmark_" + boss.id();
    }

    private void deliverLegacyHuntmark(Player player, BossManager.BossType boss, Progress state) {
        if (state.claimedRank < 4 || state.huntmarkGranted) {
            return;
        }
        ItemStack huntmark = huntmark(boss);
        if (!canFit(player.getInventory(), huntmark)) {
            player.sendMessage(MessageUtil.warn("Make one inventory slot to receive your updated <white>" + boss.plainDisplayName() + " Huntmark</white>."));
            return;
        }

        state.huntmarkGranted = true;
        if (!saveNow()) {
            state.huntmarkGranted = false;
            player.sendMessage(MessageUtil.error("The ledger could not save your Huntmark delivery."));
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(huntmark);
        leftovers.values().forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
        player.sendMessage(MessageUtil.success("Mogrik replaced the old Rank IV payout with your permanent <white>" + boss.plainDisplayName() + " Huntmark</white>."));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
    }

    private Reward relicReward(String relicId, int amount) {
        ItemStack preview = plugin.getSeasonRelicManager().createRelicItem(relicId);
        Material icon = preview == null ? Material.BARRIER : preview.getType();
        String name = preview == null || preview.getItemMeta() == null || !preview.getItemMeta().hasDisplayName()
            ? relicId.replace('_', ' ')
            : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(preview.getItemMeta().displayName());
        return new Reward(icon, relicId, amount, 0L, amount + "x " + name);
    }

    private RankState rankState(Progress progress, int rank) {
        if (progress.claimedRank >= rank) return RankState.CLAIMED;
        if (progress.claimedRank + 1 != rank) return RankState.LOCKED;
        return progress.kills >= RANK_REQUIREMENTS.get(rank - 1) ? RankState.READY : RankState.WAITING;
    }

    private Progress progress(UUID playerId, String bossId) {
        return progress.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>())
            .computeIfAbsent(bossId, ignored -> new Progress());
    }

    private void migrateRecordedVictories() {
        plugin.getDatabase().loadAllSuccessfulBossCounts().whenComplete((counts, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.getLogger().warning("Could not migrate prior Boss Mastery victories: " + error.getMessage());
                return;
            }
            boolean changed = false;
            for (Map.Entry<UUID, Map<String, Integer>> playerEntry : counts.entrySet()) {
                for (Map.Entry<String, Integer> bossEntry : playerEntry.getValue().entrySet()) {
                    BossManager.BossType boss = BossManager.BossType.fromId(bossEntry.getKey());
                    if (boss == null) continue;
                    Progress state = progress(playerEntry.getKey(), boss.id());
                    int migrated = Math.max(0, bossEntry.getValue());
                    if (migrated > state.kills) {
                        state.kills = migrated;
                        changed = true;
                    }
                }
            }
            if (changed) saveNow();
        }));
    }

    private void load() {
        progress.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String rawUuid : players.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection bosses = players.getConfigurationSection(rawUuid);
            if (bosses == null) continue;
            for (String bossId : bosses.getKeys(false)) {
                BossManager.BossType boss = BossManager.BossType.fromId(bossId);
                if (boss == null) continue;
                Progress state = progress(playerId, boss.id());
                state.kills = Math.max(0, bosses.getInt(bossId + ".kills"));
                state.claimedRank = Math.max(0, Math.min(5, bosses.getInt(bossId + ".claimed-rank")));
                state.huntmarkGranted = bosses.getBoolean(bossId + ".huntmark-granted");
            }
        }
    }

    private boolean saveNow() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Progress>> playerEntry : progress.entrySet()) {
            for (Map.Entry<String, Progress> bossEntry : playerEntry.getValue().entrySet()) {
                String path = "players." + playerEntry.getKey() + "." + bossEntry.getKey();
                yaml.set(path + ".kills", bossEntry.getValue().kills);
                yaml.set(path + ".claimed-rank", bossEntry.getValue().claimedRank);
                yaml.set(path + ".huntmark-granted", bossEntry.getValue().huntmarkGranted);
            }
        }
        File temporary = new File(dataFile.getParentFile(), dataFile.getName() + ".next");
        File previous = new File(dataFile.getParentFile(), dataFile.getName() + ".previous");
        try {
            yaml.save(temporary);
            if (dataFile.isFile()) Files.copy(dataFile.toPath(), previous.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException ignored) {
            }
            plugin.getLogger().severe("Could not save Boss Mastery data: " + ex.getMessage());
            return false;
        }
    }

    private void tickBellHunts() {
        long now = System.currentTimeMillis();
        bellHunts.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt <= now) return true;
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null || !owner.isOnline()) return true;
            entry.getValue().targets.removeIf(targetId -> {
                Entity entity = Bukkit.getEntity(targetId);
                if (!(entity instanceof Monster) || entity.isDead() || !entity.isValid()
                    || entity.getWorld() != owner.getWorld() || entity.getLocation().distanceSquared(owner.getLocation()) > 30.0 * 30.0
                    || plugin.getBossManager().isBossEncounterEntity(entity)) return true;
                entity.getWorld().spawnParticle(Particle.ENCHANT, entity.getLocation().add(0.0, entity.getHeight() * 0.55, 0.0), 2, 0.25, 0.35, 0.25, 0.02);
                return false;
            });
            return entry.getValue().targets.size() < 2;
        });
    }

    private void echoBellDamage(Player attacker, Entity struck, double damage) {
        BellHunt hunt = bellHunts.get(attacker.getUniqueId());
        if (hunt == null || hunt.expiresAt <= System.currentTimeMillis() || !hunt.targets.contains(struck.getUniqueId())) return;
        double echo = Math.min(10.0, Math.max(0.0, damage * 0.25));
        if (echo <= 0.0) return;
        for (UUID targetId : new ArrayList<>(hunt.targets)) {
            if (targetId.equals(struck.getUniqueId())) continue;
            Entity entity = Bukkit.getEntity(targetId);
            if (!(entity instanceof Monster monster) || monster.isDead() || !monster.isValid()
                || plugin.getBossManager().isBossEncounterEntity(monster)) continue;
            monster.setHealth(Math.max(1.0, monster.getHealth() - Math.min(echo, Math.max(0.0, monster.getHealth() - 1.0))));
            monster.getWorld().spawnParticle(Particle.CRIT, monster.getLocation().add(0.0, monster.getHeight() * 0.55, 0.0), 5, 0.2, 0.25, 0.2, 0.08);
        }
        attacker.playSound(attacker.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.35f, 1.5f);
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean isBossDamage(Entity damager) {
        if (plugin.getBossManager().isBossEncounterEntity(damager)) return true;
        return damager instanceof Projectile projectile && plugin.getBossManager().isBossOwnedProjectile(projectile);
    }

    private boolean wearsPursuitSet(Player player) {
        PlayerInventory inventory = player.getInventory();
        return "gloam_hunters_hood".equals(masteryId(inventory.getHelmet()))
            && "briar_hunters_coat".equals(masteryId(inventory.getChestplate()))
            && "depth_hunters_leggings".equals(masteryId(inventory.getLeggings()))
            && "argent_trailboots".equals(masteryId(inventory.getBoots()));
    }

    private double weaponBonus(ItemStack item) {
        return switch (masteryId(item) == null ? "" : masteryId(item)) {
            case "marshals_musterblade" -> 0.12;
            case "cindervale_deadeye" -> 0.15;
            case "riftbroker_glaive" -> 0.22;
            case "runebloom_hexstaff" -> 0.25;
            case "nocturne_bellhammer" -> 0.28;
            default -> 0.0;
        };
    }

    private double huntmarkBonus(UUID playerId, String bossId) {
        Map<String, Progress> bosses = progress.get(playerId);
        Progress state = bosses == null ? null : bosses.get(bossId);
        return state == null ? 0.0 : huntmarkDamageBonusForRank(state.claimedRank);
    }

    private String masteryId(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    private ItemStack menuItem(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream().map(MM::deserialize).toList());
        if (action != null) meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private String action(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
    }

    private void fill(Inventory inventory) {
        ItemStack filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, MenuItemUtil.INACTIVE_SLOT_NAME, List.of(), null);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private boolean canFit(PlayerInventory inventory, ItemStack reward) {
        int remaining = reward.getAmount();
        for (ItemStack existing : inventory.getStorageContents()) {
            if (existing == null || existing.getType().isAir()) remaining -= reward.getMaxStackSize();
            else if (existing.isSimilar(reward)) remaining -= Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private Material masteryIcon(int index) {
        return switch (index) {
            case 0, 6 -> index == 0 ? Material.DIAMOND_SWORD : Material.NETHERITE_SWORD;
            case 1 -> Material.CROSSBOW;
            case 2 -> Material.DIAMOND_HELMET;
            case 3 -> Material.DIAMOND_CHESTPLATE;
            case 4 -> Material.DIAMOND_LEGGINGS;
            case 5 -> Material.DIAMOND_BOOTS;
            case 7 -> Material.MACE;
            case 8 -> Material.NETHERITE_AXE;
            default -> Material.BELL;
        };
    }

    private String masteryName(String id) {
        ItemStack item = createMasteryItem(id);
        if (item == null || item.getItemMeta() == null || !item.getItemMeta().hasDisplayName()) return id.replace('_', ' ');
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }

    private String percent(double value) {
        return String.format(Locale.US, "%.0f%%", value * 100.0);
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }

    private final class Reward {
        private final Material icon;
        private final String itemId;
        private final int amount;
        private final long essence;
        private final String summary;

        private Reward(Material icon, String itemId, int amount, long essence, String summary) {
            this.icon = icon;
            this.itemId = itemId;
            this.amount = amount;
            this.essence = essence;
            this.summary = summary;
        }

        private ItemStack item() {
            if (itemId == null) return null;
            ItemStack item = itemId.startsWith("marshal") || itemId.startsWith("cindervale")
                || itemId.startsWith("gloam_hunter") || itemId.startsWith("briar_hunter")
                || itemId.startsWith("depth_hunter") || itemId.startsWith("argent_trail")
                || itemId.startsWith("huntmark_")
                || itemId.startsWith("riftbroker") || itemId.startsWith("runebloom_hex")
                || itemId.startsWith("nocturne_bell") || itemId.equals("eleventh_bell")
                ? createMasteryItem(itemId)
                : plugin.getSeasonRelicManager().createRelicItem(itemId);
            if (item != null) item.setAmount(Math.max(1, amount));
            return item;
        }
    }

    private static final class Progress {
        private int kills;
        private int claimedRank;
        private boolean huntmarkGranted;
    }

    private static final class BellHunt {
        private final long expiresAt;
        private final Set<UUID> targets;

        private BellHunt(long expiresAt, Set<UUID> targets) {
            this.expiresAt = expiresAt;
            this.targets = new HashSet<>(targets);
        }
    }

    private enum RankState { CLAIMED, READY, WAITING, LOCKED }

    private record MasteryMenuHolder(UUID playerId, String bossId)
        implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
