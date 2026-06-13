package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.config.ConfigManager;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnProtectionListener implements Listener {

    private static final long MESSAGE_THROTTLE_MS = 2500L;

    private final SMPCore plugin;
    private final Map<UUID, Long> nextMessageAt = new ConcurrentHashMap<>();

    public SpawnProtectionListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        nextMessageAt.clear();
    }

    public boolean isProtected(Location location) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.spawnProtectionEnabled) {
            return false;
        }
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!isProtectedWorld(location.getWorld())) {
            return false;
        }

        Location spawn = location.getWorld().getSpawnLocation();
        double radius = config.spawnProtectionRadius;
        double dx = location.getX() - spawn.getX();
        double dz = location.getZ() - spawn.getZ();
        return (dx * dx) + (dz * dz) <= radius * radius;
    }

    public boolean canEditSpawn(Player player) {
        if (player == null) {
            return false;
        }
        return player.isOp()
            || player.hasPermission("smpcore.spawnprotect.bypass")
            || plugin.getConfigManager().isSpawnProtectionBuilder(
                player.getName(),
                player.getUniqueId().toString()
            );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!shouldBlock(event.getPlayer(), event.getBlock())) {
            return;
        }

        event.setCancelled(true);
        sendDeny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!shouldBlock(event.getPlayer(), event.getBlockPlaced())) {
            return;
        }

        event.setCancelled(true);
        sendDeny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!shouldBlock(event.getPlayer(), event.getBlock())) {
            return;
        }

        event.setCancelled(true);
        sendDeny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!shouldBlock(event.getPlayer(), event.getBlock())) {
            return;
        }

        event.setCancelled(true);
        sendDeny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!isProtected(event.getBlock().getLocation())) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && canEditSpawn(player)) {
            return;
        }

        event.setCancelled(true);
        sendDeny(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (isProtected(event.getBlock().getLocation()) || isProtected(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (isProtected(event.getBlock().getLocation()) || isProtected(dispenseTarget(event.getBlock()).getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonTouchesProtectedArea(event.getBlock(), event.getDirection(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonTouchesProtectedArea(event.getBlock(), event.getDirection(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || !shouldBlock(player, event.getEntity().getLocation())) {
            return;
        }

        event.setCancelled(true);
        sendDeny(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (!isProtected(event.getEntity().getLocation())) {
            return;
        }
        Player remover = asPlayer(event.getRemover());
        if (remover != null && canEditSpawn(remover)) {
            return;
        }

        event.setCancelled(true);
        sendDeny(remover);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent) {
            return;
        }
        if (isProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || !shouldBlock(player, event.getEntity().getLocation())) {
            return;
        }

        event.setCancelled(true);
        sendDeny(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ArmorStand || entity instanceof Hanging)) {
            return;
        }
        if (!isProtected(entity.getLocation())) {
            return;
        }
        Player attacker = asPlayer(event.getDamager());
        if (attacker != null && canEditSpawn(attacker)) {
            return;
        }

        event.setCancelled(true);
        sendDeny(attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (!shouldBlock(event.getPlayer(), event.getRightClicked().getLocation())) {
            return;
        }

        event.setCancelled(true);
        sendDeny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand || entity instanceof Hanging)) {
            return;
        }
        if (!shouldBlock(event.getPlayer(), entity.getLocation())) {
            return;
        }

        event.setCancelled(true);
        sendDeny(event.getPlayer());
    }

    private boolean shouldBlock(Player player, Block block) {
        return block != null && shouldBlock(player, block.getLocation());
    }

    private boolean shouldBlock(Player player, Location location) {
        return isProtected(location) && !canEditSpawn(player);
    }

    private boolean isProtectedWorld(World world) {
        World configured = Bukkit.getWorld(plugin.getConfigManager().spawnWorld);
        if (configured != null) {
            return configured.getUID().equals(world.getUID());
        }
        return world.getName().equalsIgnoreCase(plugin.getConfigManager().spawnWorld);
    }

    private boolean pistonTouchesProtectedArea(Block piston, BlockFace direction, Iterable<Block> movedBlocks) {
        if (isProtected(piston.getLocation())) {
            return true;
        }
        for (Block block : movedBlocks) {
            if (isProtected(block.getLocation())) {
                return true;
            }
            if (isProtected(block.getRelative(direction).getLocation())) {
                return true;
            }
            if (isProtected(block.getRelative(direction.getOppositeFace()).getLocation())) {
                return true;
            }
        }
        return false;
    }

    private Block dispenseTarget(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Directional directional) {
            return block.getRelative(directional.getFacing());
        }
        return block;
    }

    private Player asPlayer(Entity entity) {
        return entity instanceof Player player ? player : null;
    }

    private void sendDeny(Player player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long next = nextMessageAt.get(player.getUniqueId());
        if (next != null && next > now) {
            return;
        }

        nextMessageAt.put(player.getUniqueId(), now + MESSAGE_THROTTLE_MS);
        String message = plugin.getConfigManager().spawnProtectionDenyMessage;
        if (message == null || message.isBlank()) {
            message = "<red>Spawn is protected. Ask staff if you need build access here.</red>";
        }
        player.sendMessage(MessageUtil.prefixedRaw(message));
    }
}
