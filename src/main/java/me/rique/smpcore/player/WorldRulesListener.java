package me.rique.smpcore.player;

import me.rique.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSugarCaneBonemeal(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BONE_MEAL) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.SUGAR_CANE) {
            return;
        }

        Player player = event.getPlayer();
        Block top = clicked;
        while (top.getRelative(BlockFace.UP).getType() == Material.SUGAR_CANE) {
            top = top.getRelative(BlockFace.UP);
        }

        Block growthBlock = top.getRelative(BlockFace.UP);
        if (!growthBlock.getType().isAir()) {
            return;
        }
        if (growthBlock.getY() >= growthBlock.getWorld().getMaxHeight()) {
            return;
        }

        BlockState growthState = growthBlock.getState();
        growthState.setType(Material.SUGAR_CANE);
        BlockFertilizeEvent fertilizeEvent = new BlockFertilizeEvent(clicked, player, List.of(growthState));
        Bukkit.getPluginManager().callEvent(fertilizeEvent);
        if (fertilizeEvent.isCancelled() || fertilizeEvent.getBlocks().isEmpty()) {
            return;
        }

        for (BlockState state : fertilizeEvent.getBlocks()) {
            state.update(true, false);
        }

        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);

        if (player.getGameMode() != GameMode.CREATIVE) {
            consumeBonemeal(player, event.getHand(), item);
        }

        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        player.swingHand(hand);
        player.getWorld().playSound(growthBlock.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, growthBlock.getLocation().add(0.5, 0.5, 0.5), 12, 0.2, 0.35, 0.2, 0.0);
    }

    private void applyConfiguredWorldRules(World world) {
        if (!plugin.getConfigManager().forceHardDifficulty) return;
        if (world.getDifficulty() == Difficulty.HARD) return;
        world.setDifficulty(Difficulty.HARD);
    }

    private void consumeBonemeal(Player player, EquipmentSlot hand, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return;
        }
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
            return;
        }
        player.getInventory().setItemInMainHand(null);
    }
}
