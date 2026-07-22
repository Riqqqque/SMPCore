package me.rique.smpcore.spawn;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

interface SpawnLifeNavigator {

    boolean navigateTo(Entity npcEntity, Location target);

    boolean navigateTo(Entity npcEntity, Entity target);

    void cancel(Entity npcEntity);

    void teleport(Entity npcEntity, Location target);
}
