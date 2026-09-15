package me.rique.smpcore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.npc.GuideNpcManager;
import me.rique.smpcore.util.MessageUtil;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

@SuppressWarnings("UnstableApiUsage")
public final class NpcHomeCommand {

    private NpcHomeCommand() {
    }

    public static void register(Commands commands, SMPCore plugin) {
        commands.register(
            Commands.literal("npchome")
                .requires(source -> source.getSender().hasPermission("smpcore.guide.admin"))
                .executes(context -> usage(context.getSource().getSender()))
                .then(Commands.literal("here")
                    .then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(context -> setHere(
                            plugin,
                            context.getSource().getSender(),
                            IntegerArgumentType.getInteger(context, "id")
                        ))))
                .then(Commands.literal("set")
                    .then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .then(Commands.argument("world", StringArgumentType.word())
                            .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                    .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(context -> set(
                                            plugin,
                                            context.getSource().getSender(),
                                            IntegerArgumentType.getInteger(context, "id"),
                                            StringArgumentType.getString(context, "world"),
                                            DoubleArgumentType.getDouble(context, "x"),
                                            DoubleArgumentType.getDouble(context, "y"),
                                            DoubleArgumentType.getDouble(context, "z")
                                        ))))))))
                .build(),
            "Restore a managed Citizens NPC and update its protected home",
            List.of("npcanchor")
        );
    }

    private static int setHere(SMPCore plugin, CommandSender sender, int id) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Run /npchome set from console, or use /npchome here in game."));
            return 0;
        }
        return set(plugin, sender, id, player.getWorld().getName(), player.getX(), player.getY(), player.getZ());
    }

    private static int set(
        SMPCore plugin,
        CommandSender sender,
        int id,
        String worldName,
        double x,
        double y,
        double z
    ) {
        if (!CitizensAPI.hasImplementation()) {
            sender.sendMessage(MessageUtil.error("Citizens is unavailable."));
            return 0;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(MessageUtil.error("World not found: " + worldName + "."));
            return 0;
        }
        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if (npc == null) {
            sender.sendMessage(MessageUtil.error("Citizens NPC " + id + " was not found."));
            return 0;
        }
        Location previous = npc.getStoredLocation();
        float yaw = previous == null ? 0.0F : previous.getYaw();
        float pitch = previous == null ? 0.0F : previous.getPitch();
        Location anchor = new Location(world, x, y, z, yaw, pitch);
        GuideNpcManager manager = plugin.getGuideNpcManager();
        if (manager == null) {
            sender.sendMessage(MessageUtil.error("The NPC manager is unavailable."));
            return 0;
        }

        Entity entity = npc.getEntity();
        boolean moved;
        if (entity != null && entity.isValid()) {
            moved = manager.setNpcAnchor(entity, anchor);
        } else {
            moved = npc.spawn(anchor, SpawnReason.RESPAWN, spawned -> manager.setNpcAnchor(spawned, anchor));
        }
        if (!moved) {
            sender.sendMessage(MessageUtil.error("NPC " + id + " could not be restored."));
            return 0;
        }
        CitizensAPI.getNPCRegistry().saveToStore();
        sender.sendMessage(MessageUtil.success(
            npc.getName() + " restored to " + world.getName() + " "
                + format(x) + ", " + format(y) + ", " + format(z) + "."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("/npchome here <Citizens ID>"));
        sender.sendMessage(MessageUtil.info("/npchome set <ID> <world> <x> <y> <z>"));
        return Command.SINGLE_SUCCESS;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
