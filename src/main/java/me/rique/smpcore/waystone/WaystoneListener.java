package me.rique.smpcore.waystone;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Detects waystone structures via sign interactions.
 */
public final class WaystoneListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final SMPCore plugin;

    public WaystoneListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) return;
        if (!event.getPlayer().hasPermission("smpcore.waystone.use")) return;
        var player = event.getPlayer();

        Location middle = resolveWaystoneMiddleFence(sign.getBlock());
        if (middle == null) return;

        String name = extractName(sign);
        event.setCancelled(true);

        plugin.getWaystoneManager().onSignInteract(player, middle, name)
            .thenAccept(result ->
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    switch (result.type()) {
                        case INVALID -> player.sendMessage(MessageUtil.error(result.error()));
                        case REGISTERED -> player.sendMessage(MessageUtil.success(
                            "Waystone <white>" + result.waystone().name() + "</white> registered."
                        ));
                        case KNOWN -> plugin.getWaystoneManager().knownWaystones(player.getUniqueId())
                            .thenAccept(list -> org.bukkit.Bukkit.getScheduler().runTask(plugin,
                                () -> plugin.getWaystoneManager().openWaystoneMenu(player, list)))
                            .exceptionally(ex -> {
                                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (player.isOnline()) {
                                        player.sendMessage(MessageUtil.error("Waystones are unavailable right now. Try again in a moment."));
                                    }
                                });
                                return null;
                            });
                    }
                })
            )
            .exceptionally(ex -> {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(MessageUtil.error("Waystones are unavailable right now. Try again in a moment."));
                    }
                });
                return null;
            });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWaystoneMenuClick(InventoryClickEvent event) {
        if (!plugin.getWaystoneManager().isWaystoneMenu(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        plugin.getWaystoneManager().handleWaystoneMenuClick(player, event.getCurrentItem(), event.isRightClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWaystoneMenuDrag(InventoryDragEvent event) {
        if (plugin.getWaystoneManager().isWaystoneMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!canAffectWaystone(event.getBlock().getType())) return;
        plugin.getWaystoneManager().handlePotentialStructureChange(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (!canAffectWaystone(block.getType())) continue;
            plugin.getWaystoneManager().handlePotentialStructureChange(block.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (!canAffectWaystone(block.getType())) continue;
            plugin.getWaystoneManager().handlePotentialStructureChange(block.getLocation());
        }
    }

    private static String extractName(Sign sign) {
        String front = PLAIN.serialize(sign.getSide(Side.FRONT).line(0)).trim();
        if (!front.isEmpty()) return front;
        return PLAIN.serialize(sign.getSide(Side.BACK).line(0)).trim();
    }

    private static Location resolveWaystoneMiddleFence(Block signBlock) {
        Block[] candidates = {
            signBlock,
            signBlock.getRelative(1, 0, 0),
            signBlock.getRelative(-1, 0, 0),
            signBlock.getRelative(0, 1, 0),
            signBlock.getRelative(0, -1, 0),
            signBlock.getRelative(0, 0, 1),
            signBlock.getRelative(0, 0, -1),
            signBlock.getRelative(1, 1, 0),
            signBlock.getRelative(-1, 1, 0),
            signBlock.getRelative(0, 1, 1),
            signBlock.getRelative(0, 1, -1),
            signBlock.getRelative(1, -1, 0),
            signBlock.getRelative(-1, -1, 0),
            signBlock.getRelative(0, -1, 1),
            signBlock.getRelative(0, -1, -1)
        };

        for (Block block : candidates) {
            if (block.getType() == Material.STONE_BRICK_WALL) {
                Block below = block.getRelative(0, -1, 0);
                Block above = block.getRelative(0, 1, 0);
                if (below.getType() == Material.LODESTONE && above.getType() == Material.GLOWSTONE) {
                    return block.getLocation();
                }
                continue;
            }

            if (block.getType() == Material.LODESTONE) {
                Block above = block.getRelative(0, 1, 0);
                Block above2 = block.getRelative(0, 2, 0);
                if (above.getType() == Material.STONE_BRICK_WALL && above2.getType() == Material.GLOWSTONE) {
                    return above.getLocation();
                }
            }
        }
        return null;
    }

    private static boolean canAffectWaystone(Material type) {
        return type == Material.LODESTONE
            || type == Material.STONE_BRICK_WALL
            || type == Material.GLOWSTONE
            || type.name().endsWith("_SIGN");
    }
}
