package me.rique.smpcore.spawn;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class CitizensSpawnLifeNavigator implements SpawnLifeNavigator {

    @Override
    public boolean navigateTo(Entity npcEntity, Location target) {
        NPC npc = npcOf(npcEntity);
        if (npc == null || target == null || target.getWorld() == null || !npc.isSpawned()) {
            return false;
        }
        Navigator navigator = npc.getNavigator();
        configure(navigator);
        if (!navigator.canNavigateTo(target)) {
            return false;
        }
        navigator.setTarget(target);
        return true;
    }

    @Override
    public boolean navigateTo(Entity npcEntity, Entity target) {
        NPC npc = npcOf(npcEntity);
        if (npc == null || target == null || !target.isValid() || !npc.isSpawned()
            || !npcEntity.getWorld().equals(target.getWorld())) {
            return false;
        }
        Navigator navigator = npc.getNavigator();
        configure(navigator);
        navigator.setTarget(target, true);
        return true;
    }

    @Override
    public void cancel(Entity npcEntity) {
        NPC npc = npcOf(npcEntity);
        if (npc != null) {
            npc.getNavigator().cancelNavigation();
        }
    }

    @Override
    public void teleport(Entity npcEntity, Location target) {
        NPC npc = npcOf(npcEntity);
        if (npc != null && target != null && target.getWorld() != null) {
            npc.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }

    private void configure(Navigator navigator) {
        navigator.getDefaultParameters()
            .range(36.0F)
            .distanceMargin(0.8D)
            .speedModifier(1.15F)
            .stationaryTicks(60)
            .updatePathRate(10);
    }

    private NPC npcOf(Entity entity) {
        if (entity == null || !entity.isValid() || !CitizensAPI.hasImplementation()) {
            return null;
        }
        return CitizensAPI.getNPCRegistry().getNPC(entity);
    }
}
