package me.rique.smpcore.compat;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.AtomicYamlFile;
import me.rique.smpcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BedrockSkullManager implements Listener {

    private static final int MAX_REGISTERED_SKULLS = 512;
    private static final Set<String> BUILT_IN_SKIN_HASHES = Set.of(
        "b412e70375ec99ee38ae94b30e9b10752d459662b54794dfe66fe6a183c672d3",
        "a236b0e63ecbbe2a0090e4bd4f043d36b6068d25bb981389765450d8d7ee6d8c",
        "5656274dc2350d527b9e58868946c60f06727a8013ef5ca32eadf1fe72d98867",
        "8e47a564bb58bf248ef7774b227de4681e95cb8245bd8388d288cbf1ec17a888",
        "a6ab69d5a0a9b430afac85e21e86134cbfd55e6749fbb39de8ac3d5102d75a7d",
        "e608474a38fb33a395ab3d1642fb5bd7b03dd8302c30f48ed4f3f3ac259eaccb",
        "7309d8dc35a638a04b915a3b15a1452ceeae0d7ea42bcdadb21b03046987515c"
    );

    private final SMPCore plugin;
    private final File geyserFolder;
    private final File configFile;
    private final File skullsFile;
    private final Set<String> profiles = new LinkedHashSet<>();
    private final Set<String> skinHashes = new LinkedHashSet<>();
    private final Set<String> playerUuids = new LinkedHashSet<>();
    private final Set<String> playerUsernames = new LinkedHashSet<>();
    private boolean available;
    private boolean restartRequired;

    public BedrockSkullManager(SMPCore plugin) {
        this.plugin = plugin;
        this.geyserFolder = new File(plugin.getDataFolder().getParentFile(), "Geyser-Spigot");
        this.configFile = new File(geyserFolder, "config.yml");
        this.skullsFile = new File(geyserFolder, "custom-skulls.yml");
    }

    public void start() {
        available = configFile.isFile() && skullsFile.isFile();
        if (!available) {
            plugin.getLogger().info("Geyser custom skull registry is unavailable; skipping Bedrock skull mappings.");
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (!config.getBoolean("gameplay.enable-custom-content", false)) {
            plugin.getLogger().warning("Geyser gameplay.enable-custom-content is disabled; Bedrock custom skulls cannot render.");
        }
        loadRegistry();
        boolean changed = skinHashes.addAll(BUILT_IN_SKIN_HASHES);
        if (changed && saveRegistry()) {
            restartRequired = true;
            plugin.getLogger().warning("Registered SMPCore skull textures with Geyser. Restart once more to rebuild its Bedrock pack.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isRestartRequired() {
        return restartRequired;
    }

    public int registeredCount() {
        return profiles.size() + skinHashes.size() + playerUuids.size() + playerUsernames.size();
    }

    public RegistrationResult registerHeldSkull(Player player) {
        if (player == null || !available) return RegistrationResult.UNAVAILABLE;
        return registerItem(player.getInventory().getItemInMainHand());
    }

    public RegistrationResult registerItemForBedrock(ItemStack item) {
        if (!available) return RegistrationResult.UNAVAILABLE;
        return registerItem(item);
    }

    public int scanNearby(Location origin, int radius) {
        if (!available || origin == null || origin.getWorld() == null) return 0;
        int boundedRadius = Math.max(1, Math.min(64, radius));
        double maxDistanceSquared = (double) boundedRadius * boundedRadius;
        int added = 0;
        for (Chunk chunk : origin.getWorld().getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities(false)) {
                if (!(state instanceof Skull skull) || state.getLocation().distanceSquared(origin) > maxDistanceSquared) continue;
                var profile = skull.getProfile();
                if (registerProfileData(profile.properties(), profile.uuid(), profile.name()) == RegistrationResult.ADDED) added++;
            }
        }
        return added;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdminPlaceSkull(BlockPlaceEvent event) {
        if (!available || !event.getPlayer().hasPermission("smpcore.bedrockskulls.admin")) return;
        RegistrationResult result = registerItem(event.getItemInHand());
        if (result == RegistrationResult.ADDED) {
            event.getPlayer().sendMessage(MessageUtil.info("Registered this skull for Bedrock. It will appear after the next server restart."));
        }
    }

    private RegistrationResult registerItem(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (!(meta instanceof SkullMeta skullMeta)) return RegistrationResult.NOT_A_SKULL;
        return registerProfile(skullMeta.getPlayerProfile());
    }

    private synchronized RegistrationResult registerProfile(PlayerProfile profile) {
        if (!available) return RegistrationResult.UNAVAILABLE;
        if (profile == null) return RegistrationResult.NO_PROFILE;
        return registerProfileData(profile.getProperties(), profile.getId(), profile.getName());
    }

    private synchronized RegistrationResult registerProfileData(Collection<ProfileProperty> properties, UUID id, String name) {
        for (ProfileProperty property : properties) {
            if (!"textures".equals(property.getName()) || !isValidProfileValue(property.getValue())) continue;
            if (profiles.contains(property.getValue())) return RegistrationResult.ALREADY_REGISTERED;
            if (registeredCount() >= MAX_REGISTERED_SKULLS) return RegistrationResult.LIMIT_REACHED;
            profiles.add(property.getValue());
            if (!saveRegistry()) {
                profiles.remove(property.getValue());
                return RegistrationResult.UNAVAILABLE;
            }
            restartRequired = true;
            return RegistrationResult.ADDED;
        }
        if (id != null) return addFallback(playerUuids, id.toString());
        if (name != null && !name.isBlank()) return addFallback(playerUsernames, name);
        return RegistrationResult.NO_PROFILE;
    }

    private RegistrationResult addFallback(Set<String> destination, String value) {
        if (destination.contains(value)) return RegistrationResult.ALREADY_REGISTERED;
        if (registeredCount() >= MAX_REGISTERED_SKULLS) return RegistrationResult.LIMIT_REACHED;
        destination.add(value);
        if (!saveRegistry()) {
            destination.remove(value);
            return RegistrationResult.UNAVAILABLE;
        }
        restartRequired = true;
        return RegistrationResult.ADDED;
    }

    static boolean isValidProfileValue(String value) {
        if (value == null || value.isBlank() || value.length() > 16_384) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            return decoded.contains("textures.minecraft.net/texture/") && decoded.contains("SKIN");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    static boolean isBuiltInSkinHash(String hash) {
        return hash != null && BUILT_IN_SKIN_HASHES.contains(hash);
    }

    private void loadRegistry() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(skullsFile);
        profiles.addAll(clean(yaml.getStringList("player-profiles")));
        skinHashes.addAll(clean(yaml.getStringList("skin-hashes")));
        playerUuids.addAll(clean(yaml.getStringList("player-uuids")));
        playerUsernames.addAll(clean(yaml.getStringList("player-usernames")));
    }

    private List<String> clean(List<String> values) {
        List<String> clean = new ArrayList<>();
        for (String value : values) if (value != null && !value.isBlank()) clean.add(value.trim());
        return clean;
    }

    private boolean saveRegistry() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("player-usernames", List.copyOf(playerUsernames));
        yaml.set("player-uuids", List.copyOf(playerUuids));
        yaml.set("player-profiles", List.copyOf(profiles));
        yaml.set("skin-hashes", List.copyOf(skinHashes));
        try {
            AtomicYamlFile.save(yaml, skullsFile);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not update Geyser custom skull registry: " + ex.getMessage());
            return false;
        }
    }

    public enum RegistrationResult {
        ADDED, ALREADY_REGISTERED, NOT_A_SKULL, NO_PROFILE, LIMIT_REACHED, UNAVAILABLE
    }
}
