package me.rique.smpcore.player;

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import me.rique.smpcore.SMPCore;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class ExactSpawnListener implements Listener {

    private final SMPCore plugin;
    private volatile Location cachedSpawn;

    public ExactSpawnListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean applyConfiguredSpawn() {
        Location spawn = plugin.getConfigManager().exactSpawnLocation();
        if (spawn == null || spawn.getWorld() == null) {
            cachedSpawn = null;
            return false;
        }

        World world = spawn.getWorld();
        world.setSpawnLocation(spawn);
        if (plugin.getConfigManager().spawnExactEnforceSpawnRadius) {
            enforceSpawnRadius(world);
        }
        cachedSpawn = spawn.clone();
        return true;
    }

    public boolean setExactSpawn(Location source) {
        Location spawn = blockCentered(source);
        if (spawn == null || spawn.getWorld() == null) {
            return false;
        }

        plugin.getConfigManager().setExactSpawnLocation(spawn);
        return applyConfiguredSpawn();
    }

    public Location exactSpawnLocation() {
        Location spawn = cachedSpawn;
        if (spawn != null) {
            return spawn.clone();
        }

        spawn = plugin.getConfigManager().exactSpawnLocation();
        return spawn == null ? null : spawn.clone();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        if (!shouldOverrideLoginLocation(event.isNewPlayer())) {
            return;
        }
        Location spawn = cachedSpawn;
        if (spawn != null) {
            event.setSpawnLocation(spawn.clone());
        }
    }

    static boolean shouldOverrideLoginLocation(boolean newPlayer) {
        return newPlayer;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }

        Location spawn = exactSpawnLocation();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    @SuppressWarnings({"unchecked", "removal"})
    private void enforceSpawnRadius(World world) {
        GameRule<?> rawRule = GameRule.getByName("spawnRadius");
        if (rawRule != null && rawRule.getType() == Integer.class) {
            world.setGameRule((GameRule<Integer>) rawRule, 0);
        }
    }

    private Location blockCentered(Location source) {
        if (source == null || source.getWorld() == null) {
            return null;
        }
        return new Location(
            source.getWorld(),
            source.getBlockX() + 0.5,
            source.getBlockY(),
            source.getBlockZ() + 0.5,
            source.getYaw(),
            source.getPitch()
        );
    }
}
