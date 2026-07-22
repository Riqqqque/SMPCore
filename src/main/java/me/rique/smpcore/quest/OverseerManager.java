package me.rique.smpcore.quest;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.ReforgeManager;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OverseerManager implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int WEEKLY_DAILIES_REQUIRED = 5;
    private static final int DAILY_ESSENCE = 25;
    private static final int WEEKLY_ESSENCE = 150;
    private static final List<Material> ORES = List.of(
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE
    );

    private final SMPCore plugin;
    private final NamespacedKey dailyIdKey;
    private final NamespacedKey dailyProgressKey;
    private final NamespacedKey dailyClaimedKey;
    private final NamespacedKey weeklyIdKey;
    private final NamespacedKey weeklyDailiesKey;
    private final NamespacedKey weeklyClaimedKey;
    private final NamespacedKey authorityKey;
    private final NamespacedKey actionKey;
    private final File pendingBossCreditsFile;
    private final Map<UUID, String> pendingBossCredits = new ConcurrentHashMap<>();
    private boolean shutDown;

    public OverseerManager(SMPCore plugin) {
        this.plugin = plugin;
        dailyIdKey = new NamespacedKey(plugin, "overseer_daily_id");
        dailyProgressKey = new NamespacedKey(plugin, "overseer_daily_progress");
        dailyClaimedKey = new NamespacedKey(plugin, "overseer_daily_claimed");
        weeklyIdKey = new NamespacedKey(plugin, "overseer_weekly_id");
        weeklyDailiesKey = new NamespacedKey(plugin, "overseer_weekly_dailies");
        weeklyClaimedKey = new NamespacedKey(plugin, "overseer_weekly_claimed");
        authorityKey = new NamespacedKey(plugin, "veil_authority");
        actionKey = new NamespacedKey(plugin, "overseer_action");
        pendingBossCreditsFile = new File(plugin.getDataFolder(), "overseer-pending-boss-credits.yml");
        loadPendingBossCredits();
    }

    public void shutdown() {
        if (shutDown) return;
        shutDown = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof OverseerMenuHolder) {
                player.closeInventory();
            }
        }
        savePendingBossCredits();
        pendingBossCredits.clear();
    }

    public void openFromNpc(Player player) {
        normalize(player);
        DailyDirective directive = directiveToday();
        int progress = dailyProgress(player);
        boolean claimed = dailyClaimed(player);
        int weekly = weeklyDailies(player);
        int authority = authority(player);
        Inventory menu = Bukkit.createInventory(new OverseerMenuHolder(player.getUniqueId()), 36,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#7c3aed:#22d3ee><bold>Veil Overseer</bold></gradient>"), "Veil Overseer"));
        fill(menu);
        menu.setItem(4, item(Material.ENDER_EYE, "<gradient:#7c3aed:#22d3ee><bold>Season Directives</bold></gradient>", List.of(
            "<gray>Complete rotating work across the server.</gray>",
            "<gray>Authority: <white>" + authority + "</white> <dark_gray>· Rank " + authorityRankFor(authority).displayName() + "</dark_gray>"
        ), null));
        menu.setItem(11, item(directive.icon, "<yellow><bold>Daily: " + directive.title + "</bold></yellow>", List.of(
            "<gray>" + directive.description + "</gray>",
            "<gray>Progress: <white>" + Math.min(progress, directive.target) + "/" + directive.target + "</white>.</gray>",
            "<gray>Reward: <light_purple>" + DAILY_ESSENCE + " Essence</light_purple> + <aqua>4 XP Bottles</aqua>.</gray>",
            claimed ? "<green>Claimed today.</green>" : progress >= directive.target ? "<green>Click to claim.</green>" : "<yellow>Keep working.</yellow>"
        ), claimed ? null : "claim_daily"));
        menu.setItem(15, item(Material.NETHER_STAR, "<aqua><bold>Weekly Authority</bold></aqua>", List.of(
            "<gray>Claim five daily directives this week.</gray>",
            "<gray>Progress: <white>" + Math.min(weekly, WEEKLY_DAILIES_REQUIRED) + "/" + WEEKLY_DAILIES_REQUIRED + "</white>.</gray>",
            "<gray>Reward: <light_purple>" + WEEKLY_ESSENCE + " Essence</light_purple>, <aqua>16 XP Bottles</aqua>,</gray>",
            "<gray>one <light_purple>Reforge Stone</light_purple>, and <gold>+1 Authority</gold>.</gray>",
            weeklyClaimed(player) ? "<green>Claimed this week.</green>" : weekly >= WEEKLY_DAILIES_REQUIRED ? "<green>Click to claim.</green>" : "<yellow>Complete more dailies.</yellow>"
        ), weeklyClaimed(player) ? null : "claim_weekly"));
        menu.setItem(22, item(Material.WRITABLE_BOOK, "<gold><bold>Authority Ranks</bold></gold>", List.of(
            "<gray>0: <white>Veilmarked</white></gray>",
            "<gray>1: <white>Veil Deputy</white></gray>",
            "<gray>3: <white>Veil Marshal</white></gray>",
            "<gray>5: <white>Season Warden</white></gray>",
            "<dark_gray>Authority is seasonal reputation, not combat power.</dark_gray>"
        ), null));
        menu.setItem(31, item(Material.BARRIER, "<red><bold>Close</bold></red>", List.of("<gray>Close this menu.</gray>"), "close"));
        player.openInventory(menu);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        if (!ORES.contains(event.getBlock().getType())) return;
        if (plugin.getGoblinHuntManager() == null || !plugin.getGoblinHuntManager().isEligibleOreBreak(event.getBlock(), event.getPlayer())) return;
        advance(event.getPlayer(), DirectiveType.MINE, 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (plugin.getBossManager() != null && plugin.getBossManager().isCustomBoss(event.getEntity())) return;
        if (event.getEntity() instanceof Monster) advance(killer, DirectiveType.HUNT, 1);
    }

    /** Credits every tracked participant once after a legitimate progression boss victory. */
    public void recordBossDefeat(Collection<UUID> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) return;
        String today = dayId();
        boolean bossDaily = directiveToday().type == DirectiveType.BOSS;
        boolean[] changed = {false};
        participantIds.stream().filter(java.util.Objects::nonNull).distinct().forEach(playerId -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                advance(player, DirectiveType.BOSS, 1);
            } else if (bossDaily) {
                changed[0] |= !today.equals(pendingBossCredits.put(playerId, today));
            }
        });
        if (changed[0]) savePendingBossCredits();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        String creditedDay = pendingBossCredits.remove(playerId);
        if (creditedDay == null) return;
        savePendingBossCredits();
        if (!creditedDay.equals(dayId()) || directiveToday().type != DirectiveType.BOSS) return;
        Bukkit.getScheduler().runTask(plugin, () -> advance(event.getPlayer(), DirectiveType.BOSS, 1));
    }

    private void loadPendingBossCredits() {
        if (!pendingBossCreditsFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(pendingBossCreditsFile);
        String today = dayId();
        for (String rawId : yaml.getStringList("players")) {
            String[] parts = rawId.split("\\|", 2);
            if (parts.length != 2 || !today.equals(parts[1])) continue;
            try {
                pendingBossCredits.put(UUID.fromString(parts[0]), parts[1]);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void savePendingBossCredits() {
        YamlConfiguration yaml = new YamlConfiguration();
        String today = dayId();
        yaml.set("players", pendingBossCredits.entrySet().stream()
            .filter(entry -> today.equals(entry.getValue()))
            .map(entry -> entry.getKey() + "|" + entry.getValue())
            .sorted()
            .toList());
        File temporary = new File(pendingBossCreditsFile.getParentFile(), pendingBossCreditsFile.getName() + ".next");
        try {
            yaml.save(temporary);
            try {
                Files.move(
                    temporary.toPath(),
                    pendingBossCreditsFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary.toPath(), pendingBossCreditsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException ignored) {
            }
            plugin.getLogger().warning("Could not save pending Overseer boss credits: " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof OverseerMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId.equals(player.getUniqueId()) || event.getClick() != ClickType.LEFT) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        String action = action(event.getCurrentItem());
        if (action == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (action.equals("close")) player.closeInventory();
            else if (action.equals("claim_daily")) claimDaily(player);
            else if (action.equals("claim_weekly")) claimWeekly(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof OverseerMenuHolder) event.setCancelled(true);
    }

    private void advance(Player player, DirectiveType type, int amount) {
        normalize(player);
        DailyDirective directive = directiveToday();
        if (dailyClaimed(player) || directive.type != type) return;
        int old = dailyProgress(player);
        int next = Math.min(directive.target, old + Math.max(0, amount));
        if (next == old) return;
        player.getPersistentDataContainer().set(dailyProgressKey, PersistentDataType.INTEGER, next);
        if (next >= directive.target) {
            player.sendMessage(MessageUtil.success("Overseer directive complete: <white>" + directive.title + "</white>. Return to claim it."));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.35f);
        }
    }

    private void claimDaily(Player player) {
        normalize(player);
        DailyDirective directive = directiveToday();
        if (dailyClaimed(player) || dailyProgress(player) < directive.target) {
            player.sendMessage(MessageUtil.warn("That daily directive is not ready to claim."));
            openFromNpc(player);
            return;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(dailyClaimedKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(weeklyDailiesKey, PersistentDataType.INTEGER, Math.min(WEEKLY_DAILIES_REQUIRED, weeklyDailies(player) + 1));
        if (plugin.getEssenceManager() != null) plugin.getEssenceManager().credit(player, DAILY_ESSENCE, "overseer_daily");
        InventoryRecipeUtil.giveOrDrop(player, new ItemStack(Material.EXPERIENCE_BOTTLE, 4));
        player.sendMessage(MessageUtil.success("Daily directive claimed. <white>" + weeklyDailies(player) + "/" + WEEKLY_DAILIES_REQUIRED + "</white> weekly progress."));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.35f);
        openFromNpc(player);
    }

    private void claimWeekly(Player player) {
        normalize(player);
        if (weeklyClaimed(player) || weeklyDailies(player) < WEEKLY_DAILIES_REQUIRED) {
            player.sendMessage(MessageUtil.warn("The weekly authority cache is not ready."));
            openFromNpc(player);
            return;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(weeklyClaimedKey, PersistentDataType.BYTE, (byte) 1);
        int authority = authority(player) + 1;
        pdc.set(authorityKey, PersistentDataType.INTEGER, authority);
        if (plugin.getEssenceManager() != null) plugin.getEssenceManager().credit(player, WEEKLY_ESSENCE, "overseer_weekly");
        InventoryRecipeUtil.giveOrDrop(player, new ItemStack(Material.EXPERIENCE_BOTTLE, 16));
        if (plugin.getReforgeManager() != null) InventoryRecipeUtil.giveOrDrop(player, plugin.getReforgeManager().createReforgeStone(ReforgeManager.STONE_ID));
        player.showTitle(Title.title(MM.deserialize("<gold><bold>VEIL AUTHORITY " + authority + "</bold></gold>"), MM.deserialize("<aqua>Rank: " + authorityRankFor(authority).displayName() + "</aqua>"), Title.Times.times(Duration.ofMillis(350), Duration.ofMillis(2600), Duration.ofMillis(650))));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.95f);
        player.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 32, 0.45, 0.65, 0.45, 0.04);
        if (plugin.getStoryService() != null) plugin.getStoryService().onQuestStage(player, "overseer", "authority", authority);
        if (plugin.getTabListManager() != null) plugin.getTabListManager().requestRefresh();
        openFromNpc(player);
    }

    private void normalize(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String day = dayId();
        if (!day.equals(pdc.get(dailyIdKey, PersistentDataType.STRING))) {
            pdc.set(dailyIdKey, PersistentDataType.STRING, day);
            pdc.set(dailyProgressKey, PersistentDataType.INTEGER, 0);
            pdc.remove(dailyClaimedKey);
        }
        String week = weekId();
        if (!week.equals(pdc.get(weeklyIdKey, PersistentDataType.STRING))) {
            pdc.set(weeklyIdKey, PersistentDataType.STRING, week);
            pdc.set(weeklyDailiesKey, PersistentDataType.INTEGER, 0);
            pdc.remove(weeklyClaimedKey);
        }
    }

    private DailyDirective directiveToday() {
        int index = Math.floorMod((int) LocalDate.now(ZoneOffset.UTC).toEpochDay(), 3);
        return switch (index) {
            case 0 -> new DailyDirective(DirectiveType.MINE, "Ore Collection", "Mine 32 natural ore blocks anywhere in the world.", Material.DIAMOND_PICKAXE, 32);
            case 1 -> new DailyDirective(DirectiveType.HUNT, "Monster Control", "Defeat 20 hostile mobs.", Material.IRON_SWORD, 20);
            default -> new DailyDirective(DirectiveType.BOSS, "Arena Oversight", "Help defeat one progression boss.", Material.NETHER_STAR, 1);
        };
    }

    private int dailyProgress(Player player) { return Math.max(0, player.getPersistentDataContainer().getOrDefault(dailyProgressKey, PersistentDataType.INTEGER, 0)); }
    private boolean dailyClaimed(Player player) { return player.getPersistentDataContainer().has(dailyClaimedKey, PersistentDataType.BYTE); }
    private int weeklyDailies(Player player) { return Math.max(0, player.getPersistentDataContainer().getOrDefault(weeklyDailiesKey, PersistentDataType.INTEGER, 0)); }
    private boolean weeklyClaimed(Player player) { return player.getPersistentDataContainer().has(weeklyClaimedKey, PersistentDataType.BYTE); }
    public int authority(Player player) { return Math.max(0, player.getPersistentDataContainer().getOrDefault(authorityKey, PersistentDataType.INTEGER, 0)); }
    public AuthorityRank authorityRank(Player player) { return authorityRankFor(authority(player)); }
    public static AuthorityRank authorityRankFor(int level) {
        if (level >= 5) return AuthorityRank.SEASON_WARDEN;
        if (level >= 3) return AuthorityRank.VEIL_MARSHAL;
        if (level >= 1) return AuthorityRank.VEIL_DEPUTY;
        return AuthorityRank.VEILMARKED;
    }
    private String dayId() { return LocalDate.now(ZoneOffset.UTC).toString(); }
    private String weekId() { LocalDate now = LocalDate.now(ZoneOffset.UTC); WeekFields fields = WeekFields.of(DayOfWeek.MONDAY, 4); return now.get(fields.weekBasedYear()) + "-W" + now.get(fields.weekOfWeekBasedYear()); }

    private ItemStack item(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(MenuItemUtil.visibleMiniName(name)));
        meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream().map(MM::deserialize).toList());
        if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }
    private String action(ItemStack item) { ItemMeta meta = item == null ? null : item.getItemMeta(); return meta == null ? null : meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING); }
    private void fill(Inventory inventory) { ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), null); for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, pane); }

    private enum DirectiveType { MINE, HUNT, BOSS }
    public enum AuthorityRank {
        VEILMARKED("Veilmarked"),
        VEIL_DEPUTY("Veil Deputy"),
        VEIL_MARSHAL("Veil Marshal"),
        SEASON_WARDEN("Season Warden");

        private final String displayName;

        AuthorityRank(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
    private record DailyDirective(DirectiveType type, String title, String description, Material icon, int target) { }
    private record OverseerMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder { @Override public Inventory getInventory() { return null; } }
}
