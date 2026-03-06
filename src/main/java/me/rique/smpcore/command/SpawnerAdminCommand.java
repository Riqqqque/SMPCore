package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.spawner.SpawnerData;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * /spawner — admin inspection and override commands.
 * Usage: /spawner info | /spawner reset
 */
@SuppressWarnings("UnstableApiUsage")
public final class SpawnerAdminCommand {

    private SpawnerAdminCommand() {}

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("spawner")
                .requires(src -> src.getSender().hasPermission("smpcore.spawner.admin"))

                // /spawner info — show data for the targeted spawner
                .then(Commands.literal("info")
                    .requires(src -> src.getSender() instanceof Player)
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        Block target = player.getTargetBlockExact(8);
                        if (target == null || target.getType() != org.bukkit.Material.SPAWNER) {
                            player.sendMessage(MessageUtil.error("Look at a spawner (within 8 blocks)."));
                            return 0;
                        }
                        SpawnerData data = plugin.getSpawnerManager().getData(target.getLocation());
                        if (data == null) {
                            player.sendMessage(MessageUtil.info("This spawner is not tracked."));
                            return 0;
                        }
                        int maxSugar = plugin.getConfigManager().spawnerMaxSugar;
                        double maxMult = plugin.getConfigManager().spawnerMaxMultiplier;
                        player.sendMessage(MessageUtil.info(
                            "<white>" + data.entityType() + "</white> spawner at "
                            + "<aqua>" + data.x() + ", " + data.y() + ", " + data.z() + "</aqua>"
                            + "\nStack: <white>" + data.stackCount() + "</white>"
                            + "  Sugar: <white>" + data.sugarCount() + "/" + maxSugar + "</white>"
                            + "  Speed: <white>" + String.format("%.1fx", data.speedMultiplier(maxSugar, maxMult)) + "</white>"
                            + "\nRedstone: <white>" + data.redstoneControlled() + "</white>"
                            + "  AI Nerf: <white>" + data.aiNerfed() + "</white>"
                        ));
                        return Command.SINGLE_SUCCESS;
                    }))

                // /spawner reset — reset all modifiers on targeted spawner
                .then(Commands.literal("reset")
                    .requires(src -> src.getSender() instanceof Player)
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        Block target = player.getTargetBlockExact(8);
                        if (target == null || target.getType() != org.bukkit.Material.SPAWNER) {
                            player.sendMessage(MessageUtil.error("Look at a spawner (within 8 blocks)."));
                            return 0;
                        }
                        plugin.getSpawnerManager().unregister(target.getLocation());
                        // Re-register with defaults
                        String entityType = "PIG";
                        if (target.getState() instanceof org.bukkit.block.CreatureSpawner cs && cs.getSpawnedType() != null) {
                            entityType = cs.getSpawnedType().name();
                        }
                        plugin.getSpawnerManager().register(target.getLocation(), entityType, 1);
                        player.sendMessage(MessageUtil.success("Spawner reset to defaults."));
                        return Command.SINGLE_SUCCESS;
                    }))

                .build(),
            "Admin spawner management",
            java.util.List.of("spawnermgr")
        );
    }
}
