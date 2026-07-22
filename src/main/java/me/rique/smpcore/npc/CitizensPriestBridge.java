package me.rique.smpcore.npc;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.essence.PriestManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.HologramTrait;
import net.citizensnpcs.api.trait.trait.Equipment;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class CitizensPriestBridge implements PriestNpcBridge, Listener {

    private static final String NPC_DATA_KEY = "smpcore_priest_npc";
    private static final String NPC_NAME = PriestManager.NPC_DISPLAY_NAME;
    private static final String NPC_NAMEPLATE = PriestManager.NPC_NAMEPLATE;
    private static final Set<String> LEGACY_NAMES = Set.of(
        "Father Aldren, Veil Confessor",
        "Veil Priest"
    );
    private static final String SKIN_NAME = "ConfessPriest";
    private static final String SKIN_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTY1MDM5OTc4MzcyNiwKICAicHJvZmlsZUlkIiA6ICJmZTYxY2RiMjUyMTA0ODYzYTljY2E2ODAwZDRiMzgzZSIsCiAgInBy"
        + "b2ZpbGVOYW1lIiA6ICJNeVNoYWRvd3MiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAg"
        + "ICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTZhYjY5ZDVhMGE5YjQzMGFmYWM4NWUyMWU4NjEzNGNiZmQ1NWU2"
        + "NzQ5ZmJiMzlkZThhYzNkNTEwMmQ3NWE3ZCIKICAgIH0KICB9Cn0=";
    private static final String SKIN_SIGNATURE = "xu1Uv8JEfU9EqeBNeES3QvMNYR5pYWchlcO2aIskL0VUAElVWRYwWAI9YZDeuzGR6WYK3wYVLpNNnh/Fs+NOcZgTxpCj7O+BxHIkffvF/snkMoSTEfM0GKf0"
        + "h5YVMJaoTS3R4IFNpSmYtrzb/vFnApfY67OLcpXjGZ2SR5ET6gZgKWddjWi8HvrvkM9L99IyOd1NEUbKZCr57IZB0tffP4TYz/cko+QSILc6awPD0zHVMdyV"
        + "ebjii1WBN+paDJSAfovODJRIp8EEkfHgPVm4xrfvvNa9nIqLzJXM7bLneSv1OHyYvdSXrYW1nAZDkC9CPb5qyDqkv3RSkY6Bhb22DcdIJ5Cr7ffY1GQyFKHg"
        + "go3l7TeAPfBF+6IBskcqFrCn0zFeVtitN5uJzR6JnmNJtCZkyS+fLBUbVCriIRBQvp0e9YVnPMXwNZ2HmmxIyHg/BhDCmx+IvqQMxyvYUoIhZT6eWegy7fY8"
        + "yn6ISauTjQftgSyfPXkeKZ0/EQE77JjBqGufXUvYDDZGvu0r8Lqvv5rqx+PkvA97UOK/kzUImAWNkUGtb4LgQ6/8wYgMLeFxh0dkAvRBxywWia8fBXeyu2Iy"
        + "i6sr+ge3rGtfN2i1NjMlOjm/tx2GGyIPLJsGdX8z1KBN0iKKHoERkC4fxWiJFxXqk8YdoVx2omj36xnRIDo=";

    private final SMPCore plugin;
    private final PriestManager priestManager;
    private final NamespacedKey npcKey;

    public CitizensPriestBridge(SMPCore plugin, PriestManager priestManager, NamespacedKey npcKey) {
        this.plugin = plugin;
        this.priestManager = priestManager;
        this.npcKey = npcKey;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refreshLoadedNpcs();
    }

    @Override
    public Entity spawnPriest(Location location) {
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
    public int removeNearestPriest(Location origin, double radius) {
        if (!isReady() || origin == null || origin.getWorld() == null) {
            return 0;
        }

        NPC nearest = priestNpcs().stream()
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
    public List<Location> priestLocations() {
        if (!isReady()) {
            return List.of();
        }
        List<Location> locations = new ArrayList<>();
        for (NPC npc : priestNpcs()) {
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
        for (NPC npc : priestNpcs()) {
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
        if (!isPriestNpc(event.getNPC())) {
            return;
        }
        event.setCancelled(true);
        event.setDelayedCancellation(false);
        Player clicker = event.getClicker();
        Bukkit.getScheduler().runTask(plugin, () -> priestManager.openMenuFromNpc(clicker));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpcSpawn(NPCSpawnEvent event) {
        if (isPriestNpc(event.getNPC())) {
            Bukkit.getScheduler().runTask(plugin, () -> tagSpawnedEntity(event.getNPC(), event.getNPC().getEntity()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpcDespawn(NPCDespawnEvent event) {
        if (!isPriestNpc(event.getNPC())) {
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

        HologramTrait hologram = npc.getOrAddTrait(HologramTrait.class);
        hologram.clear();

        Equipment equipment = npc.getOrAddTrait(Equipment.class);
        equipment.set(Equipment.EquipmentSlot.HAND, new ItemStack(Material.ENCHANTED_BOOK));
        equipment.set(Equipment.EquipmentSlot.OFF_HAND, new ItemStack(Material.AMETHYST_SHARD));
    }

    private void tagSpawnedEntity(NPC npc, Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
        entity.addScoreboardTag("smpcore_npc");
        entity.addScoreboardTag("smpcore_priest_npc");
        entity.setInvulnerable(true);
        entity.setSilent(true);
        if (entity instanceof LivingEntity living) {
            living.setCanPickupItems(false);
            living.setCollidable(false);
            AttributeInstance scale = living.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(0.98);
            }
        }
        if (plugin.getNpcHologramManager() != null) {
            plugin.getNpcHologramManager().show(entity, hologramOwner(npc), NPC_NAMEPLATE, 0.42D);
        }
    }

    private String hologramOwner(NPC npc) {
        return "priest:citizens:" + npc.getId();
    }

    private boolean isReady() {
        return Bukkit.getPluginManager().isPluginEnabled("Citizens") && CitizensAPI.hasImplementation();
    }

    private NPCRegistry registry() {
        return CitizensAPI.getNPCRegistry();
    }

    private List<NPC> priestNpcs() {
        List<NPC> npcs = new ArrayList<>();
        for (NPC npc : registry()) {
            if (isPriestNpc(npc)) {
                npcs.add(npc);
            }
        }
        return npcs;
    }

    private boolean isPriestNpc(NPC npc) {
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
