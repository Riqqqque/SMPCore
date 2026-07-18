package me.rique.smpcore.waystone;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.CustomLoreUtil;
import me.rique.smpcore.util.LocationUtil;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages waystone registry and per-player known waystones.
 */
public final class WaystoneManager {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String LANDING_TOGGLE_ACTION = "__landing_toggle__";

    private final SMPCore plugin;
    private final NamespacedKey waystoneMenuTargetKey;
    private final ConcurrentMap<String, WaystoneEntry> waystonesByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Set<String>> knownByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<Set<String>>> knownLoading = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> knownSessionTokens = new ConcurrentHashMap<>();
    private final Set<UUID> knownLoaded = ConcurrentHashMap.newKeySet();
    private final AtomicLong knownSessionSequence = new AtomicLong();

    public WaystoneManager(SMPCore plugin) {
        this.plugin = plugin;
        this.waystoneMenuTargetKey = new NamespacedKey(plugin, "waystone_menu_target");
    }

    public CompletableFuture<Void> loadAll() {
        return plugin.getDatabase().loadAllWaystones().thenAccept(entries -> {
            waystonesByKey.clear();
            for (WaystoneEntry entry : entries) {
                waystonesByKey.put(entry.key(), entry);
            }
        });
    }

    public void unloadPlayer(UUID playerId) {
        knownByPlayer.remove(playerId);
        knownLoading.remove(playerId);
        knownLoaded.remove(playerId);
        knownSessionTokens.remove(playerId);
    }

    public CompletableFuture<InteractResult> onSignInteract(Player player, Location middleFence, String signName) {
        String trimmed = sanitizeName(signName);
        if (trimmed == null) {
            return CompletableFuture.completedFuture(InteractResult.invalid("Waystone sign name is empty."));
        }

        WaystoneEntry probe = new WaystoneEntry(
            trimmed,
            middleFence.getWorld().getName(),
            middleFence.getBlockX(),
            middleFence.getBlockY(),
            middleFence.getBlockZ()
        );

        return getOrCreateWaystone(probe, player.getUniqueId()).thenCompose(entry -> {
            if (entry == null) {
                return CompletableFuture.completedFuture(InteractResult.invalid(
                    "Waystone name or location conflicts with an existing waystone."
                ));
            }

            return ensureKnownLoaded(player.getUniqueId()).thenCompose(known -> {
                boolean wasKnown = known.contains(entry.key());
                if (wasKnown) {
                    return CompletableFuture.completedFuture(InteractResult.known(entry));
                }
                known.add(entry.key());
                return plugin.getDatabase().addKnownWaystone(player.getUniqueId(), entry)
                    .thenApply(inserted -> InteractResult.registered(entry));
            });
        });
    }

    public CompletableFuture<List<WaystoneEntry>> knownWaystones(UUID playerId) {
        return ensureKnownLoaded(playerId).thenCompose(known -> {
            if (known.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }

            List<WaystoneEntry> list = new ArrayList<>();
            for (String key : known) {
                WaystoneEntry entry = waystonesByKey.get(key);
                if (entry != null) list.add(entry);
            }

            if (list.size() == known.size()) {
                list.sort(Comparator.comparing(WaystoneEntry::name, String.CASE_INSENSITIVE_ORDER));
                return CompletableFuture.completedFuture(list);
            }

            return plugin.getDatabase().loadKnownWaystones(playerId).thenApply(fresh -> {
                for (WaystoneEntry e : fresh) {
                    waystonesByKey.put(e.key(), e);
                }
                syncKnownWaystones(known, fresh);
                fresh.sort(Comparator.comparing(WaystoneEntry::name, String.CASE_INSENSITIVE_ORDER));
                return fresh;
            });
        });
    }

    public CompletableFuture<Boolean> canUseWaypoint(UUID playerId, String world, int x, int y, int z) {
        return ensureKnownLoaded(playerId).thenApply(known ->
            known.contains(WaystoneEntry.key(world, x, y, z))
        );
    }

    public CompletableFuture<WaystoneEntry> resolveCoordinates(String world, int x, int y, int z) {
        String key = WaystoneEntry.key(world, x, y, z);
        WaystoneEntry cached = waystonesByKey.get(key);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return plugin.getDatabase().getWaystoneByLocation(world, x, y, z)
            .thenApply(opt -> {
                opt.ifPresent(entry -> waystonesByKey.put(entry.key(), entry));
                return opt.orElse(null);
            });
    }

    public Location teleportLocation(WaystoneEntry entry) {
        World world = Bukkit.getWorld(entry.world());
        if (world == null) return null;

        int bx = entry.x();
        int by = entry.y();
        int bz = entry.z();
        int[][] offsets = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {0, 0}
        };

        for (int[] off : offsets) {
            int x = bx + off[0];
            int z = bz + off[1];
            int y = by;
            if (isSafe(world, x, y, z)) {
                return centered(world, x, y, z);
            }
            if (isSafe(world, x, y + 1, z)) {
                return centered(world, x, y + 1, z);
            }
            if (isSafe(world, x, y - 1, z)) {
                return centered(world, x, y - 1, z);
            }
        }

        Location glowstoneTop = glowstoneTopLocation(entry);
        return glowstoneTop == null || glowstoneTop.getWorld() == null ? null : glowstoneTop;
    }

    public void sendWaystoneList(Player player, List<WaystoneEntry> waystones) {
        List<WaystoneEntry> valid = filterValidWaystones(waystones);
        if (valid.isEmpty()) {
            player.sendMessage(MessageUtil.info("You do not know any waystones yet."));
            return;
        }

        player.sendMessage(MessageUtil.info("Known waystones:"));
        for (WaystoneEntry entry : valid) {
            player.sendMessage(MessageUtil.prefixedRaw(
                "<gray>- <name> " +
                    "<dark_gray>(<white><world></white> <white><x></white>,<white><y></white>,<white><z></white>)</dark_gray>",
                MessageUtil.placeholder("name", entry.name()),
                MessageUtil.placeholder("world", entry.world()),
                MessageUtil.placeholder("x", Integer.toString(entry.x())),
                MessageUtil.placeholder("y", Integer.toString(entry.y())),
                MessageUtil.placeholder("z", Integer.toString(entry.z()))
            ));
        }
    }

    public void openWaystoneMenu(Player player, List<WaystoneEntry> waystones) {
        openWaystoneMenu(player, waystones, false);
    }

    private void openWaystoneMenu(Player player, List<WaystoneEntry> waystones, boolean topOfGlowstone) {
        List<WaystoneEntry> valid = filterValidWaystones(waystones);
        if (valid.isEmpty()) {
            player.sendMessage(MessageUtil.info("You do not know any waystones yet."));
            return;
        }

        final int maxEntries = 45;
        boolean truncated = valid.size() > maxEntries;
        if (truncated) {
            valid = new ArrayList<>(valid.subList(0, maxEntries));
        }

        List<WaystoneEntry> shown = List.copyOf(valid);
        Inventory inventory = Bukkit.createInventory(
            new WaystoneMenuHolder(shown, topOfGlowstone),
            54,
            BedrockCompat.menuTitle(player, Component.text("Waystones"), "Waystones")
        );
        for (int i = 0; i < valid.size(); i++) {
            inventory.setItem(i, createMenuItem(player, valid.get(i), topOfGlowstone));
        }

        if (truncated) {
            ItemStack more = new ItemStack(Material.PAPER);
            ItemMeta meta = more.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("More waystones exist", NamedTextColor.YELLOW));
                meta.lore(CustomLoreUtil.wrapLoreLines(List.of(
                    Component.text("Only the first 45 are shown here.", NamedTextColor.GRAY)
                )));
                more.setItemMeta(meta);
            }
            inventory.setItem(49, more);
        }
        inventory.setItem(53, createLandingToggle(topOfGlowstone));

        player.openInventory(inventory);
    }

    public boolean isWaystoneMenu(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof WaystoneMenuHolder;
    }

    public void handleWaystoneMenuClick(Player player, Inventory inventory, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (!(inventory.getHolder(false) instanceof WaystoneMenuHolder holder)) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String key = meta.getPersistentDataContainer().get(waystoneMenuTargetKey, PersistentDataType.STRING);
        if (key == null || key.isBlank()) return;

        if (LANDING_TOGGLE_ACTION.equals(key)) {
            openWaystoneMenu(player, holder.waystones(), !holder.topOfGlowstone());
            return;
        }

        player.closeInventory();
        teleportKnownByKey(player, key, holder.topOfGlowstone());
    }

    public void teleportKnownByKey(Player player, String key) {
        teleportKnownByKey(player, key, false);
    }

    public void teleportKnownByKey(Player player, String key, boolean topOfGlowstone) {
        if (!player.isOnline()) return;
        if (isInPlayerCombat(player)) return;
        if (!player.hasPermission("smpcore.waystone.use")) {
            player.sendMessage(MessageUtil.error("You do not have permission to use waystones."));
            return;
        }
        WaystoneEntry current = waystonesByKey.get(key);
        if (current == null) {
            player.sendMessage(MessageUtil.error("That waystone no longer exists."));
            return;
        }
        switch (structureStatus(current)) {
            case BROKEN -> {
                removeWaystone(current);
                player.sendMessage(MessageUtil.error("That waystone was destroyed."));
                return;
            }
            case WORLD_UNAVAILABLE -> {
                player.sendMessage(MessageUtil.error("Waystone world is not loaded."));
                return;
            }
            case INTACT -> {
            }
        }

        canUseWaypoint(player.getUniqueId(), current.world(), current.x(), current.y(), current.z())
            .thenAccept(allowed ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (!allowed) {
                        player.sendMessage(MessageUtil.error("You have not unlocked that waystone."));
                        return;
                    }
                    if (waystonesByKey.get(key) != current) {
                        player.sendMessage(MessageUtil.error("That waystone no longer exists."));
                        return;
                    }
                    StructureStatus latestStatus = structureStatus(current);
                    if (latestStatus == StructureStatus.BROKEN) {
                        removeWaystone(current);
                        player.sendMessage(MessageUtil.error("That waystone was destroyed."));
                        return;
                    }
                    if (latestStatus == StructureStatus.WORLD_UNAVAILABLE) {
                        player.sendMessage(MessageUtil.error("Waystone world is not loaded."));
                        return;
                    }
                    if (isInPlayerCombat(player)) return;
                    teleport(player, current, topOfGlowstone);
                })
            )
            .exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(MessageUtil.error("Waystones are unavailable right now. Try again in a moment."));
                    }
                });
                return null;
            });
    }

    private List<WaystoneEntry> filterValidWaystones(List<WaystoneEntry> waystones) {
        if (waystones.isEmpty()) return List.of();
        List<WaystoneEntry> valid = new ArrayList<>(waystones.size());
        for (WaystoneEntry entry : waystones) {
            WaystoneEntry current = waystonesByKey.get(entry.key());
            if (current == null) continue;
            if (structureStatus(current) == StructureStatus.BROKEN) {
                removeWaystone(current);
            } else {
                valid.add(current);
            }
        }
        valid.sort(Comparator.comparing(WaystoneEntry::name, String.CASE_INSENSITIVE_ORDER));
        return valid;
    }

    private ItemStack createMenuItem(Player player, WaystoneEntry entry, boolean topOfGlowstone) {
        ItemStack item = new ItemStack(Material.LODESTONE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(entry.name(), NamedTextColor.AQUA));
        meta.lore(CustomLoreUtil.wrapLoreLines(List.of(
            Component.text("World: " + entry.world(), NamedTextColor.GRAY),
            Component.text("X: " + entry.x() + " Y: " + entry.y() + " Z: " + entry.z(), NamedTextColor.GRAY),
            Component.text("Landing: " + (topOfGlowstone ? "Glowstone top" : "Safest nearby spot"), NamedTextColor.DARK_GRAY),
            Component.text(BedrockCompat.menuActionWord(player) + " to teleport", NamedTextColor.YELLOW)
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(waystoneMenuTargetKey, PersistentDataType.STRING, entry.key());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLandingToggle(boolean topOfGlowstone) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(
            "Landing: " + (topOfGlowstone ? "Glowstone top" : "Safest spot"),
            NamedTextColor.YELLOW
        ));
        meta.lore(CustomLoreUtil.wrapLoreLines(List.of(
            Component.text("Tap to change where waystones place you.", NamedTextColor.GRAY),
            Component.text("This replaces unreliable left/right menu clicks.", NamedTextColor.DARK_GRAY)
        )));
        meta.getPersistentDataContainer().set(waystoneMenuTargetKey, PersistentDataType.STRING, LANDING_TOGGLE_ACTION);
        item.setItemMeta(meta);
        return item;
    }

    public void handlePotentialStructureChange(Location location) {
        if (location == null || location.getWorld() == null) return;
        String world = location.getWorld().getName();
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();
        Set<String> checked = new HashSet<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    String key = WaystoneEntry.key(world, bx + dx, by + dy, bz + dz);
                    if (!checked.add(key)) continue;
                    WaystoneEntry entry = waystonesByKey.get(key);
                    if (entry == null) continue;
                    if (structureStatus(entry) == StructureStatus.BROKEN) {
                        removeWaystone(entry);
                    }
                }
            }
        }
    }

    private void teleport(Player player, WaystoneEntry entry, boolean topOfGlowstone) {
        if (isInPlayerCombat(player)) return;
        Location destination = topOfGlowstone ? glowstoneTopLocation(entry) : null;
        if (destination == null) {
            destination = teleportLocation(entry);
        }
        if (destination == null || destination.getWorld() == null) {
            player.sendMessage(MessageUtil.error("Waystone world is not loaded."));
            return;
        }

        plugin.getPlayerManager().saveBackLocation(player);
        player.teleportAsync(destination).thenAccept(ok ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (ok) {
                    player.sendMessage(MessageUtil.success("Teleported to waystone <white>" + entry.name() + "</white>."));
                } else {
                    player.sendMessage(MessageUtil.error("Teleport failed."));
                }
            })
        );
    }

    private boolean isInPlayerCombat(Player player) {
        if (plugin.getCombatLogListener() == null || !plugin.getCombatLogListener().isInPlayerCombat(player)) {
            return false;
        }
        player.sendMessage(MessageUtil.warn("You cannot teleport while in combat."));
        return true;
    }

    private Location glowstoneTopLocation(WaystoneEntry entry) {
        World world = Bukkit.getWorld(entry.world());
        if (world == null) return null;

        int x = entry.x();
        int y = entry.y() + 2;
        int z = entry.z();

        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block below = world.getBlockAt(x, y - 1, z);
        if (feet.getType() != Material.AIR || head.getType() != Material.AIR) return null;
        if (!below.getType().isSolid()) return null;

        return centered(world, x, y, z);
    }

    private CompletableFuture<Set<String>> ensureKnownLoaded(UUID playerId) {
        if (knownLoaded.contains(playerId)) {
            return CompletableFuture.completedFuture(
                knownByPlayer.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet())
            );
        }
        CompletableFuture<Set<String>> inFlight = knownLoading.get(playerId);
        if (inFlight != null) return inFlight;

        long token = knownSessionToken(playerId);
        CompletableFuture<Set<String>> future = new CompletableFuture<>();
        CompletableFuture<Set<String>> previous = knownLoading.putIfAbsent(playerId, future);
        if (previous != null) return previous;

        plugin.getDatabase().loadKnownWaystoneKeys(playerId).whenComplete((keys, ex) -> {
            try {
                if (ex != null) {
                    future.completeExceptionally(ex);
                    return;
                }

                Set<String> loaded = ConcurrentHashMap.newKeySet();
                loaded.addAll(keys);
                if (sameKnownSession(playerId, token) && knownLoading.get(playerId) == future) {
                    Set<String> set = knownByPlayer.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet());
                    set.clear();
                    set.addAll(loaded);
                    knownLoaded.add(playerId);
                    future.complete(set);
                    return;
                }

                future.complete(loaded);
            } finally {
                knownLoading.remove(playerId, future);
            }
        });

        return future;
    }

    private long knownSessionToken(UUID playerId) {
        return knownSessionTokens.computeIfAbsent(playerId, ignored -> knownSessionSequence.incrementAndGet());
    }

    private boolean sameKnownSession(UUID playerId, long token) {
        Long current = knownSessionTokens.get(playerId);
        return current != null && current == token;
    }

    private CompletableFuture<WaystoneEntry> getOrCreateWaystone(WaystoneEntry probe, UUID creator) {
        String key = probe.key();
        WaystoneEntry cached = waystonesByKey.get(key);
        if (cached != null) {
            if (!cached.name().equalsIgnoreCase(probe.name())) return CompletableFuture.completedFuture(null);
            return CompletableFuture.completedFuture(cached);
        }

        return plugin.getDatabase().getWaystoneByLocation(probe.world(), probe.x(), probe.y(), probe.z())
            .thenCompose(opt -> {
                if (opt.isPresent()) {
                    WaystoneEntry found = opt.get();
                    waystonesByKey.put(found.key(), found);
                    if (!found.name().equalsIgnoreCase(probe.name())) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return CompletableFuture.completedFuture(found);
                }

                return plugin.getDatabase().createWaystone(probe, creator).thenCompose(created -> {
                    if (created) {
                        waystonesByKey.put(probe.key(), probe);
                        return CompletableFuture.completedFuture(probe);
                    }
                    return plugin.getDatabase().getWaystoneByLocation(probe.world(), probe.x(), probe.y(), probe.z())
                        .thenApply(fallback -> {
                            WaystoneEntry found = fallback.orElse(null);
                            if (found == null) return null;
                            waystonesByKey.put(found.key(), found);
                            return found.name().equalsIgnoreCase(probe.name()) ? found : null;
                        });
                });
            });
    }

    private static String sanitizeName(String raw) {
        if (raw == null) return null;
        String out = raw.trim();
        if (out.isEmpty()) return null;
        if (out.length() > 32) out = out.substring(0, 32);
        return out;
    }

    private static boolean isSafe(World world, int x, int y, int z) {
        return LocationUtil.isSafeStandingLocation(centered(world, x, y, z));
    }

    private static Location centered(World world, int x, int y, int z) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private StructureStatus structureStatus(WaystoneEntry entry) {
        World world = Bukkit.getWorld(entry.world());
        if (world == null) return StructureStatus.WORLD_UNAVAILABLE;

        return isStructureIntact(world, entry) ? StructureStatus.INTACT : StructureStatus.BROKEN;
    }

    private boolean isStructureIntact(World world, WaystoneEntry entry) {
        Block middle = world.getBlockAt(entry.x(), entry.y(), entry.z());
        if (middle.getType() != Material.STONE_BRICK_WALL) return false;
        if (middle.getRelative(BlockFace.DOWN).getType() != Material.LODESTONE) return false;
        if (middle.getRelative(BlockFace.UP).getType() != Material.GLOWSTONE) return false;
        return hasNamedSign(middle);
    }

    private void syncKnownWaystones(Set<String> known, List<WaystoneEntry> entries) {
        known.clear();
        for (WaystoneEntry entry : entries) {
            known.add(entry.key());
        }
    }

    private static boolean hasNamedSign(Block middle) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block block = middle.getRelative(face);
            if (!(block.getState() instanceof Sign sign)) continue;
            if (extractSignName(sign) != null) return true;
        }
        return false;
    }

    private static String extractSignName(Sign sign) {
        String front = PLAIN.serialize(sign.getSide(Side.FRONT).line(0)).trim();
        if (!front.isEmpty()) return front;
        String back = PLAIN.serialize(sign.getSide(Side.BACK).line(0)).trim();
        return back.isEmpty() ? null : back;
    }

    private void removeWaystone(WaystoneEntry entry) {
        waystonesByKey.remove(entry.key());
        for (Set<String> known : knownByPlayer.values()) {
            known.remove(entry.key());
        }
        plugin.getDatabase().deleteWaystone(entry.world(), entry.x(), entry.y(), entry.z())
            .exceptionally(ex -> {
                plugin.getLogger().severe("Failed to remove destroyed waystone " + entry.key() + ": " + ex.getMessage());
                return null;
            });
    }

    public record InteractResult(Type type, WaystoneEntry waystone, String error) {
        public static InteractResult registered(WaystoneEntry e) { return new InteractResult(Type.REGISTERED, e, null); }
        public static InteractResult known(WaystoneEntry e) { return new InteractResult(Type.KNOWN, e, null); }
        public static InteractResult invalid(String message) { return new InteractResult(Type.INVALID, null, message); }

        public enum Type { REGISTERED, KNOWN, INVALID }
    }

    private enum StructureStatus {
        INTACT,
        BROKEN,
        WORLD_UNAVAILABLE
    }

    private record WaystoneMenuHolder(
        List<WaystoneEntry> waystones,
        boolean topOfGlowstone
    ) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
