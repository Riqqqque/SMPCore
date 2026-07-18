package me.rique.smpcore.quest;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.InventoryRecipeUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;

public final class MinerManager implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long FEVER_DURATION_MS = 45_000L;
    private static final long FEVER_COOLDOWN_MS = 360_000L;
    private static final double FEVER_TRIPLE_CHANCE = 0.50D;
    private static final double PET_TRIPLE_CHANCE = 0.10D;
    public static final String MINER_FAMILIAR_ID = "miner";
    private static final UUID MINER_HEAD_PROFILE = UUID.fromString("d1e5dd49-ecd1-4332-a7e7-51848abee557");
    private static final String MINER_HEAD_TEXTURE = "ewogICJ0aW1lc3RhbXAiIDogMTY2MjgzMjQ4OTE4NCwKICAicHJvZmlsZUlkIiA6ICI1N2I0MTZlNjJjZGE0MTAzOTRiNzZkNmNkNDA3MjFiOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJSME1CSUVTIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2EyMzZiMGU2M2VjYmJlMmEwMDkwZTRiZDRmMDQzZDM2YjYwNjhkMjViYjk4MTM4OTc2NTQ1MGQ4ZDdlZTZkOGMiCiAgICB9CiAgfQp9";
    private static final List<QuestStage> STAGES = List.of(
        new QuestStage("Coal Dust", Material.COAL, 64, "Bring coal for the town's furnaces."),
        new QuestStage("Copper Veins", Material.RAW_COPPER, 48, "Bring raw copper for building supplies."),
        new QuestStage("Iron Resolve", Material.RAW_IRON, 32, "Bring raw iron for tools and repairs."),
        new QuestStage("Gilded Depths", Material.RAW_GOLD, 24, "Bring raw gold to reach the halfway reward."),
        new QuestStage("Redstone Pulse", Material.REDSTONE, 32, "Bring redstone for the town's mechanisms."),
        new QuestStage("Lapis Memory", Material.LAPIS_LAZULI, 24, "Bring lapis for enchanting work."),
        new QuestStage("Emerald Proof", Material.EMERALD, 12, "Bring emeralds for the final supply run."),
        new QuestStage("Diamond Heart", Material.DIAMOND, 8, "Bring diamonds to finish Torren's quest.")
    );

    private final SMPCore plugin;
    private final NamespacedKey stageKey;
    private final NamespacedKey pickKey;
    private final NamespacedKey petKey;
    private final NamespacedKey petHiddenKey;
    private final NamespacedKey activeFamiliarKey;
    private final NamespacedKey feverUntilKey;
    private final NamespacedKey feverCooldownKey;
    private final NamespacedKey actionKey;
    private final Set<UUID> pendingMenuActions = ConcurrentHashMap.newKeySet();
    private final FamiliarFollower follower;

    public MinerManager(SMPCore plugin) {
        this.plugin = plugin;
        this.stageKey = new NamespacedKey(plugin, "miner_quest_stage");
        this.pickKey = new NamespacedKey(plugin, "veinwake_pick");
        this.petKey = new NamespacedKey(plugin, "miner_familiar_unlocked");
        this.petHiddenKey = new NamespacedKey(plugin, "miner_familiar_hidden");
        this.activeFamiliarKey = new NamespacedKey(plugin, "active_familiar");
        this.feverUntilKey = new NamespacedKey(plugin, "mining_fever_until");
        this.feverCooldownKey = new NamespacedKey(plugin, "mining_fever_cooldown");
        this.actionKey = new NamespacedKey(plugin, "miner_menu_action");
        this.follower = new FamiliarFollower(
            plugin,
            "miner_pet_owner",
            "Miner Familiar",
            NamedTextColor.GOLD,
            Particle.WAX_ON,
            this::minerHead,
            this::hasActiveMinerPet
        );
    }

    public void start() {
        follower.start();
    }

    public void shutdown() {
        follower.shutdown();
        pendingMenuActions.clear();
    }

    public void openFromMiner(Player player) {
        openQuestMenu(player);
    }

    public void openQuestMenu(Player player) {
        int stage = questStage(player);
        Inventory menu = Bukkit.createInventory(new MinerMenuHolder(player.getUniqueId()), 27,
            BedrockCompat.menuTitle(player, MM.deserialize("<gradient:#f59e0b:#facc15><bold>Torren the Miner</bold></gradient>"), "Torren the Miner"));
        fill(menu);
        menu.setItem(4, item(Material.IRON_PICKAXE, "<gold><bold>The Deep Road</bold></gold>", List.of(
            "<gray>Turn in each ore shipment in order.</gray>",
            "<gray>Halfway: <white>Veinwake Pick</white>.</gray>",
            "<gray>Final: <yellow>Miner Familiar</yellow>.</gray>"
        ), null));
        for (int i = 0; i < STAGES.size(); i++) {
            QuestStage definition = STAGES.get(i);
            boolean done = i < stage;
            boolean current = i == stage;
            int count = countPlain(player, definition.material());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + definition.description() + "</gray>");
            lore.add("<gray>Need: <white>" + definition.amount() + " " + pretty(definition.material()) + "</white>.</gray>");
            if (done) lore.add("<green>Complete.</green>");
            else if (current) lore.add(count >= definition.amount() ? "<green>Click to turn in.</green>" : "<yellow>You have " + count + ".</yellow>");
            else lore.add("<dark_gray>Finish the earlier shipment first.</dark_gray>");
            menu.setItem(9 + i, item(done ? Material.LIME_STAINED_GLASS_PANE : current ? definition.material() : Material.GRAY_STAINED_GLASS_PANE,
                done ? "<green><bold>" + definition.title() + "</bold></green>" : current ? "<gold><bold>" + definition.title() + "</bold></gold>" : "<dark_gray><bold>Locked</bold></dark_gray>",
                lore, current ? "turn_in:" + i : null));
        }
        menu.setItem(22, applyMinerHeadTexture(item(Material.PLAYER_HEAD,
            hasMinerPet(player) ? "<yellow><bold>Miner Familiar</bold></yellow>" : "<dark_gray><bold>Miner Familiar</bold></dark_gray>",
            hasMinerPet(player) ? List.of("<gray>10% chance for mined ore to triple.</gray>", "<yellow>Click to manage.</yellow>") : List.of("<gray>Complete Diamond Heart to unlock.</gray>"),
            hasMinerPet(player) ? "pet" : null)));
        player.openInventory(menu);
    }

    public void openPetMenu(Player player) {
        Inventory menu = Bukkit.createInventory(new MinerPetMenuHolder(player.getUniqueId()), 27,
            BedrockCompat.menuTitle(player, MM.deserialize("<yellow><bold>Miner Familiar</bold></yellow>"), "Miner Familiar"));
        fill(menu);
        ItemStack head = minerHead();
        ItemMeta meta = head.getItemMeta();
        meta.displayName(MM.deserialize("<yellow><bold>Miner Familiar</bold></yellow>"));
        meta.lore(CustomLoreUtil.wrapMiniMessageLines(List.of(
            "<gray>10% chance for natural ore to triple.</gray>",
            "<dark_gray>Stacks additively with Mining Luck and Mining Fever.</dark_gray>"
        )).stream().map(MM::deserialize).toList());
        head.setItemMeta(meta);
        menu.setItem(4, head);
        if (hasMinerPet(player)) {
            FamiliarMenuState state = FamiliarMenuState.from(hasActiveMinerPet(player));
            menu.setItem(11, item(state.summonIcon(),
                state.canSummon() ? "<green><bold>Summon</bold></green>" : "<green><bold>Currently Summoned</bold></green>",
                state.canSummon()
                    ? List.of("<gray>Bring out the Miner Familiar.</gray>")
                    : List.of("<gray>The Miner Familiar is following you.</gray>", "<dark_gray>Right-click to talk. Sneak-right-click to manage.</dark_gray>"),
                state.summonAction("summon")));
            menu.setItem(15, item(state.dismissIcon(),
                state.canDismiss() ? "<red><bold>Dismiss</bold></red>" : "<dark_gray><bold>Already Dismissed</bold></dark_gray>",
                state.canDismiss()
                    ? List.of("<gray>Hide it and pause its ore bonus.</gray>")
                    : List.of("<gray>The Miner Familiar is already resting.</gray>"),
                state.dismissAction("dismiss")));
        }
        menu.setItem(22, item(Material.ARROW, "<yellow><bold>Back</bold></yellow>", List.of("<gray>Return to your familiar stable.</gray>"), "familiars"));
        player.openInventory(menu);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof MinerMenuHolder) && !(holder instanceof MinerPetMenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        String action = action(event.getCurrentItem());
        if (action == null || !pendingMenuActions.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                handleAction(player, action);
            } finally {
                pendingMenuActions.remove(player.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof MinerMenuHolder || holder instanceof MinerPetMenuHolder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()) return;
        if (!event.getAction().isRightClick() || !isVeinwakePick(event.getItem())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long cooldown = pdcLong(player, feverCooldownKey);
        if (cooldown > now) {
            player.sendMessage(MessageUtil.warn("Mining Fever is ready in <white>" + formatTime(cooldown - now) + "</white>."));
            return;
        }
        player.getPersistentDataContainer().set(feverUntilKey, PersistentDataType.LONG, now + FEVER_DURATION_MS);
        player.getPersistentDataContainer().set(feverCooldownKey, PersistentDataType.LONG, now + FEVER_COOLDOWN_MS);
        player.sendMessage(MessageUtil.success("<gold>Mining Fever</gold> is active for <white>45 seconds</white>."));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
        player.spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 24, 0.4, 0.6, 0.4, 0.08);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { if (hasActiveMinerPet(event.getPlayer())) follower.spawn(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        pendingMenuActions.remove(event.getPlayer().getUniqueId());
        follower.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !follower.isOwnedHitbox(event.getRightClicked(), event.getPlayer())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            openPetMenu(player);
            return;
        }
        if (!follower.beginInteraction(player)) return;
        player.sendMessage(MessageUtil.info("<gold>Miner Familiar:</gold> <white>Hey, don't touch me. I've got ore dust everywhere.</white>"));
        player.playSound(event.getRightClicked().getLocation(), Sound.ENTITY_VILLAGER_NO, 0.65F, 0.9F);
        player.spawnParticle(Particle.ANGRY_VILLAGER, event.getRightClicked().getLocation().add(0.0D, 0.45D, 0.0D), 2, 0.18D, 0.15D, 0.18D, 0.0D);
    }

    public OreBonusResult rollOreBonuses(Player player) {
        if (player == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return new OreBonusResult(0, false, false);
        boolean pet = hasActiveMinerPet(player) && ThreadLocalRandom.current().nextDouble() < minerPetTripleChance(player);
        boolean fever = pdcLong(player, feverUntilKey) > System.currentTimeMillis() && ThreadLocalRandom.current().nextDouble() < FEVER_TRIPLE_CHANCE;
        return new OreBonusResult(bonusCopies(pet, fever), pet, fever);
    }

    static int bonusCopies(boolean petProc, boolean feverProc) {
        return (petProc ? 2 : 0) + (feverProc ? 2 : 0);
    }

    public boolean hasMinerPet(Player player) {
        Byte value = player.getPersistentDataContainer().get(petKey, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    public void grantMinerPet(Player player, CommandSender actor) {
        if (player == null) return;
        boolean already = hasMinerPet(player);
        player.getPersistentDataContainer().set(petKey, PersistentDataType.BYTE, (byte) 1);
        selectMinerFamiliar(player);
        follower.spawn(player);
        player.sendMessage(already
            ? MessageUtil.info("Your <white>Miner Familiar</white> was restored.")
            : MessageUtil.success("You unlocked the <white>Miner Familiar</white>."));
        if (actor != null && !actor.equals(player)) actor.sendMessage(MessageUtil.success("Gave <white>" + player.getName() + "</white> the Miner Familiar."));
    }

    public void revokeMinerPet(Player player, CommandSender actor) {
        if (player == null) return;
        player.getPersistentDataContainer().remove(petKey);
        player.getPersistentDataContainer().remove(petHiddenKey);
        if (MINER_FAMILIAR_ID.equals(activeFamiliar(player))) player.getPersistentDataContainer().remove(activeFamiliarKey);
        follower.remove(player);
        player.sendMessage(MessageUtil.warn("Your <white>Miner Familiar</white> was removed."));
        if (actor != null && !actor.equals(player)) actor.sendMessage(MessageUtil.success("Removed <white>" + player.getName() + "</white>'s Miner Familiar."));
    }

    public boolean hasActiveMinerPet(Player player) {
        if (player == null) return false;
        ensureActiveFamiliar(player);
        return player.isOnline() && !player.isDead() && hasMinerPet(player) && !isPetHidden(player)
            && MINER_FAMILIAR_ID.equals(activeFamiliar(player));
    }

    public double minerPetTripleChance(Player player) {
        if (!hasActiveMinerPet(player)) return 0.0D;
        double multiplier = plugin.getBeastwardenManager() == null
            ? 1.0D : plugin.getBeastwardenManager().familiarCoreMultiplier(player, MINER_FAMILIAR_ID);
        return Math.min(0.20D, PET_TRIPLE_CHANCE * multiplier);
    }

    public long miningFeverRemainingMillis(Player player) {
        return Math.max(0L, pdcLong(player, feverUntilKey) - System.currentTimeMillis());
    }

    public long miningFeverCooldownRemainingMillis(Player player) {
        return Math.max(0L, pdcLong(player, feverCooldownKey) - System.currentTimeMillis());
    }

    public double miningFeverTripleChance(Player player) {
        return miningFeverRemainingMillis(player) > 0L ? FEVER_TRIPLE_CHANCE : 0.0D;
    }


    private void handleAction(Player player, String action) {
        if (action.startsWith("turn_in:")) {
            try {
                turnIn(player, Integer.parseInt(action.substring("turn_in:".length())));
            } catch (NumberFormatException ignored) {
            }
            return;
        }
        switch (action) {
            case "pet" -> openPetMenu(player);
            case "summon" -> {
                if (!hasActiveMinerPet(player)) {
                    selectMinerFamiliar(player);
                    follower.spawn(player);
                }
                openPetMenu(player);
            }
            case "dismiss" -> {
                if (hasActiveMinerPet(player)) {
                    player.getPersistentDataContainer().set(petHiddenKey, PersistentDataType.BYTE, (byte) 1);
                    if (MINER_FAMILIAR_ID.equals(activeFamiliar(player))) player.getPersistentDataContainer().remove(activeFamiliarKey);
                    follower.remove(player);
                }
                openPetMenu(player);
            }
            case "familiars" -> { if (plugin.getMayorQuestManager() != null) plugin.getMayorQuestManager().openPetCollectionMenu(player); }
            default -> { }
        }
    }

    private void turnIn(Player player, int expectedStage) {
        int stage = questStage(player);
        if (stage != expectedStage) {
            openQuestMenu(player);
            return;
        }
        if (stage >= STAGES.size()) { openQuestMenu(player); return; }
        QuestStage quest = STAGES.get(stage);
        Map<Material, Integer> cost = Map.of(quest.material(), quest.amount());
        if (!InventoryRecipeUtil.hasPlainMaterials(plugin, player, cost)) {
            player.sendMessage(MessageUtil.warn("You need <white>" + quest.amount() + " " + pretty(quest.material()) + "</white>."));
            openQuestMenu(player);
            return;
        }
        if (!InventoryRecipeUtil.removePlainMaterials(plugin, player, cost)) {
            player.sendMessage(MessageUtil.error("The shipment changed before it could be accepted."));
            return;
        }
        int next = stage + 1;
        player.getPersistentDataContainer().set(stageKey, PersistentDataType.INTEGER, next);
        if (next == STAGES.size() / 2 && !hasVeinwakePick(player)) {
            player.getPersistentDataContainer().set(pickKey, PersistentDataType.BYTE, (byte) 1);
            InventoryRecipeUtil.giveOrDrop(player, createVeinwakePick());
            player.sendMessage(MessageUtil.success("Torren gave you the <white>Veinwake Pick</white>."));
        }
        if (next == STAGES.size()) {
            player.getPersistentDataContainer().set(petKey, PersistentDataType.BYTE, (byte) 1);
            selectMinerFamiliar(player);
            follower.spawn(player);
            player.showTitle(Title.title(
                MM.deserialize("<gold><bold>MINER FAMILIAR</bold></gold>"),
                MM.deserialize("<yellow>You completed Torren's mining jobs.</yellow>"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000))
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f);
            if (plugin.getStoryService() != null) plugin.getStoryService().onFamiliarUnlocked(player, "miner");
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }
        if (plugin.getStoryService() != null) {
            plugin.getStoryService().onQuestStage(player, "miner", quest.title(), next);
        }
        player.sendMessage(MessageUtil.success("Shipment complete: <white>" + quest.title() + "</white>."));
        openQuestMenu(player);
    }

    private ItemStack createVeinwakePick() {
        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = pick.getItemMeta();
        meta.displayName(MM.deserialize("<gradient:#f59e0b:#fde68a><bold>Veinwake Pick</bold></gradient>"));
        meta.lore(CustomLoreUtil.buildStyledLore(
            meta,
            Material.DIAMOND_PICKAXE,
            "RARE",
            "PICKAXE",
            List.of("<gray>A sturdy diamond pick made by Torren.</gray>"),
            List.of(CustomLoreUtil.section("Ability", "Mining Fever",
                "<gray><white>Sneak + Right-click</white> to activate for 45s.</gray>",
                "<gray>Natural ores have a <white>50%</white> chance to triple.</gray>",
                "<gray>Cooldown: <white>6 minutes</white>.</gray>",
                "<dark_gray>Ore bonuses stack additively.</dark_gray>"))
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(pickKey, PersistentDataType.BYTE, (byte) 1);
        pick.setItemMeta(meta);
        return pick;
    }

    private boolean isVeinwakePick(ItemStack item) { return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(pickKey, PersistentDataType.BYTE); }
    private boolean hasVeinwakePick(Player player) { Byte value = player.getPersistentDataContainer().get(pickKey, PersistentDataType.BYTE); return value != null && value == 1; }
    private boolean isPetHidden(Player player) {
        Byte value = player.getPersistentDataContainer().get(petHiddenKey, PersistentDataType.BYTE);
        return value != null && value == 1 || !MINER_FAMILIAR_ID.equals(activeFamiliar(player));
    }

    public void deactivateForFamiliarSwitch(Player player) {
        if (player == null) return;
        player.getPersistentDataContainer().set(petHiddenKey, PersistentDataType.BYTE, (byte) 1);
        follower.remove(player);
    }

    private void selectMinerFamiliar(Player player) {
        if (plugin.getMayorQuestManager() != null) plugin.getMayorQuestManager().deactivateForFamiliarSwitch(player);
        if (plugin.getFarmerManager() != null) plugin.getFarmerManager().deactivateForFamiliarSwitch(player);
        if (plugin.getWitchManager() != null) plugin.getWitchManager().deactivateForFamiliarSwitch(player);
        player.getPersistentDataContainer().set(activeFamiliarKey, PersistentDataType.STRING, MINER_FAMILIAR_ID);
        player.getPersistentDataContainer().remove(petHiddenKey);
    }

    private void ensureActiveFamiliar(Player player) {
        if (activeFamiliar(player) != null) return;
        Byte hidden = player.getPersistentDataContainer().get(petHiddenKey, PersistentDataType.BYTE);
        if (hasMinerPet(player) && (hidden == null || hidden != (byte) 1)) {
            player.getPersistentDataContainer().set(activeFamiliarKey, PersistentDataType.STRING, MINER_FAMILIAR_ID);
        }
    }

    private String activeFamiliar(Player player) {
        return player.getPersistentDataContainer().get(activeFamiliarKey, PersistentDataType.STRING);
    }
    private int questStage(Player player) { Integer value = player.getPersistentDataContainer().get(stageKey, PersistentDataType.INTEGER); return Math.clamp(value == null ? 0 : value, 0, STAGES.size()); }


    private ItemStack minerHead() {
        return applyMinerHeadTexture(new ItemStack(Material.PLAYER_HEAD));
    }

    public ItemStack applyMinerHeadTexture(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return item;
        ItemMeta itemMeta = item.getItemMeta();
        if (!(itemMeta instanceof SkullMeta meta)) return item;
        PlayerProfile profile = Bukkit.createProfile(MINER_HEAD_PROFILE, "MinerPet");
        profile.setProperty(new ProfileProperty("textures", MINER_HEAD_TEXTURE));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(CustomLoreUtil.wrapMiniMessageLines(lore).stream()
            .map(line -> MM.deserialize(line).decoration(TextDecoration.ITALIC, false))
            .toList());
        if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }
    private String action(ItemStack item) { return item == null || !item.hasItemMeta() ? null : item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING); }
    private void fill(Inventory menu) { ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), null); for (int i = 0; i < menu.getSize(); i++) menu.setItem(i, pane); }
    private int countPlain(Player player, Material material) { return InventoryRecipeUtil.countIngredient(player, InventoryRecipeUtil.plainMaterial(plugin, material, Integer.MAX_VALUE)); }
    private long pdcLong(Player player, NamespacedKey key) { Long value = player.getPersistentDataContainer().get(key, PersistentDataType.LONG); return value == null ? 0L : value; }
    private String pretty(Material material) { String value = material.name().toLowerCase().replace('_', ' '); return Character.toUpperCase(value.charAt(0)) + value.substring(1); }
    private String formatTime(long millis) { long seconds = Math.max(1, (millis + 999) / 1000); return seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s"; }


    public record OreBonusResult(int bonusCopies, boolean petProc, boolean feverProc) { }
    private record QuestStage(String title, Material material, int amount, String description) { }
    private record MinerMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder { @Override public Inventory getInventory() { return null; } }
    private record MinerPetMenuHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder { @Override public Inventory getInventory() { return null; } }
}
