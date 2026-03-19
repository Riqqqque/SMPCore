package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public final class WorldRulesListener implements Listener {

    private final SMPCore plugin;

    public WorldRulesListener(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void applyConfiguredWorldRules() {
        for (World world : Bukkit.getWorlds()) {
            applyConfiguredWorldRules(world);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        applyConfiguredWorldRules(event.getWorld());
    }

    private void applyConfiguredWorldRules(World world) {
        if (!plugin.getConfigManager().forceHardDifficulty) return;
        if (world.getDifficulty() == Difficulty.HARD) return;
        world.setDifficulty(Difficulty.HARD);
    }
}
