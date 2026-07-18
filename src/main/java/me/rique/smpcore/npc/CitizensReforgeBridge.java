package me.rique.smpcore.npc;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.item.ReforgeManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Inventory;
import net.citizensnpcs.trait.HologramTrait;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class CitizensReforgeBridge implements ReforgeNpcBridge, Listener {

    private static final String NPC_DATA_KEY = "smpcore_reforge_npc";
    private static final String NPC_NAME = ReforgeManager.NPC_DISPLAY_NAME;
    private static final String NPC_NAMEPLATE = ReforgeManager.NPC_NAMEPLATE;
    private static final double CLOSE_LOOK_RANGE = 3.0D;
    private static final Set<String> LEGACY_NAMES = Set.of(
        "Brannik Ironvein, Master Reforger",
        "Dwarven Reforger"
    );
    private static final String SKIN_NAME = "DwarfEngineer";
    private static final String SKIN_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTc3OTgxMjYyMjg3MSwKICAicHJvZmlsZUlkIiA6ICIwNTljODIxYzhhODU0NGJiOWJiODVhOGMxNjVhYTc5YiIsCiAgInBy"
        + "b2ZpbGVOYW1lIiA6ICJoZWxsc3RydWNrZWR6IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDog"
        + "ewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2U2MDg0NzRhMzhmYjMzYTM5NWFiM2QxNjQyZmI1YmQ3YjAz"
        + "ZGQ4MzAyYzMwZjQ4ZWQ0ZjNmM2FjMjU5ZWFjY2IiCiAgICB9CiAgfQp9";
    private static final String SKIN_SIGNATURE = "VNxusQxHRuL44bANTyDtmPqoc9uXviHoCuRHgFpQFP7qKFwHsj3yeBAA2ka2b48ae7PfnvnKXtWfD5RsZFB5W4YmmKUVVbrABhYzKf0z2qjcjDRanvAycLn4"
        + "7Rl32DWGEZIGJVh7j9h9EgGnApB5pghADQniRP6RzMFfvm8FFJ6bduKnX48SgQeaS8QQNhpXvXliA6CWjzfTb0irG3judeS6fSc2496R9PeE/bXmAeEplgYj"
        + "jFXg8TQ1nLC6T5DNPWF9zHiXczAHG7ulSY3YQFbO1/EA0dI1+hMKEMbBst83VWi8AVt0o6iAGaTVdnbxMbOHiN2WaLu5YEiIaVS8mjT9PSIv574tq6yH+Ip8"
        + "+ObUlV2F6wZswGxToWvnKJTun8cENjzn5vh4QhulLRwZrsQp7qBIAw4d84Y65gIKz67ML5revdBCvsz0H6d5gBZYDEZvKl4D1w0Qha8Wo0OE8Sm1VLqShWmx"
        + "o9vgJ/J1wsmzxUVvXsjWdAr2aCROoGHDMgIqA/qaY1yLuYOjKlki6oGxDnZoqykcGMBR4QNFnf7r757I2fwmACmR42MMXz/SYRGrCSVXoZxI8OuTy9X+jdbl"
        + "Q+LqIGskoRhyvspxHROp+LlKW7MxrCdlnjfxdI9iFNcaz8q81ENp7F9qpYCCNg5B1+DaeDd9pSWN4cRaOgo=";

    private final SMPCore plugin;
    private final ReforgeManager reforgeManager;
    private final NamespacedKey npcKey;

    public CitizensReforgeBridge(SMPCore plugin, ReforgeManager reforgeManager, NamespacedKey npcKey) {
        this.plugin = plugin;
        this.reforgeManager = reforgeManager;
        this.npcKey = npcKey;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refreshLoadedNpcs();
    }

    @Override
    public Entity spawnDwarf(Location location) {
        if (!isReady() || location == null || location.getWorld() == null) {
            return null;
        }

        NPC npc = registry().createNPC(EntityType.PLAYER, NPC_NAME);
        configure(npc);
        boolean spawned = npc.spawn(location, SpawnReason.CREATE, entity -> tagSpawnedEntity(npc, entity));
        if (!spawned) {
            npc.destroy();
            return null;
        }

        registry().saveToStore();
        return npc.getEntity();
    }

    @Override
    public int removeNearestDwarf(Location origin, double radius) {
        if (!isReady() || origin == null || origin.getWorld() == null) {
            return 0;
        }

        NPC nearest = reforgerNpcs().stream()
            .filter(npc -> npcLocation(npc) != null)
            .filter(npc -> npcLocation(npc).getWorld().equals(origin.getWorld()))
            .filter(npc -> npcLocation(npc).distanceSquared(origin) <= radius * radius)
            .min(Comparator.comparingDouble(npc -> npcLocation(npc).distanceSquared(origin)))
            .orElse(null);
        if (nearest == null) {
            return 0;
        }

        nearest.destroy();
        registry().saveToStore();
        return 1;
    }

    @Override
    public List<Location> dwarfLocations() {
        if (!isReady()) {
            return List.of();
        }
        List<Location> locations = new ArrayList<>();
        for (NPC npc : reforgerNpcs()) {
            Location location = npcLocation(npc);
            if (location != null) {
                locations.add(location);
            }
        }
        return locations;
    }

    @Override
    public int refreshLoadedNpcs() {
        if (!isReady()) {
            return 0;
        }
        int refreshed = 0;
        for (NPC npc : reforgerNpcs()) {
            Location location = npcLocation(npc);
            boolean wasSpawned = npc.isSpawned();
            if (wasSpawned) {
                npc.despawn(DespawnReason.PLUGIN);
            }

            configure(npc);

            if (wasSpawned && location != null) {
                npc.spawn(location, SpawnReason.RESPAWN, entity -> tagSpawnedEntity(npc, entity));
            } else if (npc.isSpawned()) {
                tagSpawnedEntity(npc, npc.getEntity());
            }
            refreshed++;
        }
        if (refreshed > 0) {
            registry().saveToStore();
        }
        return refreshed;
    }

    @Override
    public void shutdown() {
        if (isReady()) {
            registry().saveToStore();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcRightClick(NPCRightClickEvent event) {
        if (!isReforgerNpc(event.getNPC())) {
            return;
        }
        event.setCancelled(true);
        event.setDelayedCancellation(false);
        Player clicker = event.getClicker();
        Bukkit.getScheduler().runTask(plugin, () -> reforgeManager.openReforgeMenuFromNpc(clicker));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpcSpawn(NPCSpawnEvent event) {
        if (isReforgerNpc(event.getNPC())) {
            Bukkit.getScheduler().runTask(plugin, () -> tagSpawnedEntity(event.getNPC(), event.getNPC().getEntity()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpcDespawn(NPCDespawnEvent event) {
        if (!isReforgerNpc(event.getNPC())) {
            return;
        }
        if (plugin.getNpcHologramManager() != null) {
            plugin.getNpcHologramManager().hide(hologramOwner(event.getNPC()));
        }
    }

    private void configure(NPC npc) {
        npc.setName(NPC_NAME);
        npc.setProtected(true);
        npc.setUseMinecraftAI(false);
        npc.data().setPersistent(NPC_DATA_KEY, true);
        npc.data().setPersistent(NPC.Metadata.COLLIDABLE, false);
        npc.data().setPersistent(NPC.Metadata.DEFAULT_PROTECTED, true);
        npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
        npc.data().setPersistent(NPC.Metadata.PICKUP_ITEMS, false);
        npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST, true);
        npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, true);
        npc.data().setPersistent(NPC.Metadata.SILENT, true);
        npc.data().setPersistent(NPC.Metadata.TRACKING_RANGE, 48);
        npc.data().setPersistent(NPC.Metadata.USE_MINECRAFT_AI, false);

        SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
        skin.clearTexture();
        skin.setSkinPersistent(SKIN_NAME, SKIN_SIGNATURE, SKIN_VALUE);
        skin.setFetchDefaultSkin(false);
        skin.setShouldUpdateSkins(false);

        configureCloseLook(npc);

        HologramTrait hologram = npc.getOrAddTrait(HologramTrait.class);
        hologram.clear();

        syncHeldItems(npc, null);
    }

    private void configureCloseLook(NPC npc) {
        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        lookClose.lookClose(true);
        lookClose.setRange(CLOSE_LOOK_RANGE);
        lookClose.setRandomLook(false);
        lookClose.setRandomlySwitchTargets(false);
        lookClose.setTargetNPCs(false);
        lookClose.setLinkedBody(true);
        lookClose.setHeadOnly(false);
        lookClose.setRealisticLooking(true);
        lookClose.setDisableWhileNavigating(true);
    }

    private void tagSpawnedEntity(NPC npc, Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
        entity.addScoreboardTag("smpcore_npc");
        entity.addScoreboardTag("smpcore_reforge_npc");
        entity.setInvulnerable(true);
        entity.setSilent(true);
        if (entity instanceof LivingEntity living) {
            living.setCanPickupItems(false);
            living.setCollidable(false);
            AttributeInstance scale = living.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(0.82);
            }
        }
        syncHeldItems(npc, entity);
        if (plugin.getNpcHologramManager() != null) {
            plugin.getNpcHologramManager().show(entity, hologramOwner(npc), NPC_NAMEPLATE, 0.42D);
        }
    }

    private void syncHeldItems(NPC npc, Entity entity) {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemStack shard = new ItemStack(Material.AMETHYST_SHARD);

        Inventory inventory = npc.getOrAddTrait(Inventory.class);
        inventory.setItem(0, mace);

        Equipment trait = npc.getOrAddTrait(Equipment.class);
        trait.set(Equipment.EquipmentSlot.HAND, mace);
        trait.set(Equipment.EquipmentSlot.OFF_HAND, shard);

        if (entity instanceof LivingEntity living) {
            EntityEquipment equipment = living.getEquipment();
            if (equipment != null) {
                equipment.setItemInMainHand(mace, true);
                equipment.setItemInOffHand(shard, true);
            }
        }
    }

    private String hologramOwner(NPC npc) {
        return "reforge:citizens:" + npc.getId();
    }

    private boolean isReady() {
        return Bukkit.getPluginManager().isPluginEnabled("Citizens") && CitizensAPI.hasImplementation();
    }

    private NPCRegistry registry() {
        return CitizensAPI.getNPCRegistry();
    }

    private List<NPC> reforgerNpcs() {
        List<NPC> npcs = new ArrayList<>();
        for (NPC npc : registry()) {
            if (isReforgerNpc(npc)) {
                npcs.add(npc);
            }
        }
        return npcs;
    }

    private boolean isReforgerNpc(NPC npc) {
        if (npc == null) {
            return false;
        }
        Object marker = npc.data().get(NPC_DATA_KEY);
        if (Boolean.TRUE.equals(marker)) {
            return true;
        }
        if (marker instanceof String value && Boolean.parseBoolean(value)) {
            return true;
        }
        return NPC_NAME.equals(npc.getName())
            || NPC_NAME.equals(npc.getRawName())
            || LEGACY_NAMES.contains(npc.getName())
            || LEGACY_NAMES.contains(npc.getRawName());
    }

    private Location npcLocation(NPC npc) {
        if (npc == null) {
            return null;
        }
        Entity entity = npc.getEntity();
        if (entity != null && entity.isValid()) {
            return entity.getLocation();
        }
        return npc.getStoredLocation();
    }
}
