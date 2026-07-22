package me.rique.smpcore.shop;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.util.AtomicYamlFile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DecoratedPot;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class MarketStallTemplateStore {

    private static final int SCHEMA = 1;
    private static final int PAYLOAD_VERSION = 2;
    private static final int MAX_BLOCKS = 100_000;
    private static final int MAX_STRING_BYTES = 16_384;
    private static final int MAX_COMPRESSED_BYTES = 32 * 1024 * 1024;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final SMPCore plugin;
    private final File file;
    private final Map<String, StoredTemplate> templates = new LinkedHashMap<>();

    MarketStallTemplateStore(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market-stall-templates.yml");
    }

    void load() {
        requirePrimaryThread();
        templates.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("templates");
        if (root == null) return;
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) continue;
            try {
                StoredTemplate template = loadTemplate(rawId, section);
                decode(template);
                templates.put(template.id(), template);
            } catch (IllegalArgumentException | IOException ex) {
                plugin.getLogger().severe("Ignored invalid market stall template '" + rawId + "': " + ex.getMessage());
            }
        }
    }

    boolean hasTemplate(String id) {
        return id != null && templates.containsKey(id);
    }

    String templateHash(String id) {
        StoredTemplate template = id == null ? null : templates.get(id);
        return template == null ? null : template.sha256();
    }

    CaptureResult capture(String id, Region region, boolean overwrite, boolean requireEmptyChangedInventories) {
        requirePrimaryThread();
        if (id == null || region == null) return CaptureResult.failure("Stall id and region are required.");
        if (templates.containsKey(id) && !overwrite) return CaptureResult.failure("A launch template already exists.");
        World world = world(region);
        if (world == null) return CaptureResult.failure("The stall world is not loaded.");
        int volume = checkedVolume(region);

        List<SnapshotBlock> blocks = new ArrayList<>(volume);
        Map<String, Integer> materials = new LinkedHashMap<>();
        int nonEmptyContainers = 0;
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    blocks.add(snapshot(block));
                    materials.merge(block.getType().name(), 1, Integer::sum);
                    if (hasItems(block.getState())) nonEmptyContainers++;
                }
            }
        }
        if (requireEmptyChangedInventories && nonEmptyContainers > 0) {
            return CaptureResult.failure("Empty every container in the stall before replacing its launch template.");
        }

        try {
            byte[] payload = encode(blocks);
            StoredTemplate previous = templates.get(id);
            StoredTemplate created = new StoredTemplate(
                id,
                region,
                Instant.now().toString(),
                blocks.size(),
                sha256(payload),
                payload,
                sortedMaterials(materials)
            );
            templates.put(id, created);
            try {
                save();
            } catch (IOException ex) {
                if (previous == null) templates.remove(id);
                else templates.put(id, previous);
                throw ex;
            }
            return new CaptureResult(true, "", blocks.size(), nonEmptyContainers, created.sha256(), created.materials());
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save launch template for market stall '" + id + "': " + ex.getMessage());
            return CaptureResult.failure("The launch template could not be saved.");
        }
    }

    Inspection inspect(String id, Region region) {
        return inspect(id, region, Set.of());
    }

    Inspection inspect(String id, Region region, Set<String> forcedBlockKeys) {
        requirePrimaryThread();
        StoredTemplate template = templates.get(id);
        if (template == null) return Inspection.failure("No launch template exists for that stall.");
        if (!template.region().equals(region)) return Inspection.failure("The saved template does not match the stall's current world or bounds.");
        World world = world(region);
        if (world == null) return Inspection.failure("The stall world is not loaded.");
        try {
            List<SnapshotBlock> blocks = decode(template);
            return inspect(world, region, blocks, template.sha256(), forcedBlockKeys);
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().severe("Could not inspect market stall template '" + id + "': " + ex.getMessage());
            return Inspection.failure("The launch template failed validation.");
        }
    }

    RestoreResult restore(String id, Region region, Runnable beforeApply) {
        return restore(id, region, Set.of(), beforeApply);
    }

    RestoreResult restore(String id, Region region, Set<String> forcedBlockKeys, Runnable beforeApply) {
        requirePrimaryThread();
        StoredTemplate template = templates.get(id);
        if (template == null) return RestoreResult.failure("No launch template exists for that stall.");
        if (!template.region().equals(region)) return RestoreResult.failure("The saved template does not match the stall's current world or bounds.");
        World world = world(region);
        if (world == null) return RestoreResult.failure("The stall world is not loaded.");

        try {
            List<SnapshotBlock> blocks = decode(template);
            Inspection inspection = inspect(world, region, blocks, template.sha256(), forcedBlockKeys);
            if (!inspection.success()) return RestoreResult.failure(inspection.reason());
            if (inspection.blockingContainers() > 0) {
                return RestoreResult.failure("Empty " + inspection.blockingContainers() + " changed container(s) before restoring this stall.");
            }

            List<BlockData> parsed = new ArrayList<>(blocks.size());
            for (SnapshotBlock snapshot : blocks) parsed.add(Bukkit.createBlockData(snapshot.blockData()));
            if (beforeApply != null) beforeApply.run();

            List<Integer> changed = changedIndexes(world, region, blocks, forcedBlockKeys);
            for (int index : changed) blockAt(world, region, index).setType(Material.AIR, false);
            for (int index : changed) blockAt(world, region, index).setBlockData(parsed.get(index), false);
            for (int index : changed) applyTileState(blockAt(world, region, index), blocks.get(index));
            return new RestoreResult(true, "", changed.size(), template.sha256());
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().severe("Could not restore market stall template '" + id + "': " + ex.getMessage());
            return RestoreResult.failure("The launch template failed validation before any blocks were changed.");
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Market stall restore failed for '" + id + "': " + ex.getMessage());
            return RestoreResult.failure("The restore stopped because the world rejected a block state. Check the server log.");
        }
    }

    private Inspection inspect(World world, Region region, List<SnapshotBlock> blocks, String hash, Set<String> forcedBlockKeys) {
        List<Integer> changed = changedIndexes(world, region, blocks, forcedBlockKeys);
        int blockingContainers = 0;
        int blockingStacks = 0;
        for (int index : changed) {
            BlockState state = blockAt(world, region, index).getState();
            if (!(state instanceof InventoryHolder holder)) continue;
            int stacks = itemStackCount(holder);
            if (stacks > 0) {
                blockingContainers++;
                blockingStacks += stacks;
            }
        }
        return new Inspection(true, "", changed.size(), blockingContainers, blockingStacks, hash);
    }

    private List<Integer> changedIndexes(World world, Region region, List<SnapshotBlock> blocks, Set<String> forcedBlockKeys) {
        List<Integer> changed = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            Block block = blockAt(world, region, index);
            if ((forcedBlockKeys != null && forcedBlockKeys.contains(blockKey(block))) || !matches(block, blocks.get(index))) changed.add(index);
        }
        return changed;
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private SnapshotBlock snapshot(Block block) {
        BlockState state = block.getState();
        SignSnapshot sign = state instanceof Sign signState ? SignSnapshot.capture(signState) : null;
        PotSnapshot pot = state instanceof DecoratedPot decoratedPot ? PotSnapshot.capture(decoratedPot) : null;
        SkullSnapshot skull = state instanceof Skull skullState ? SkullSnapshot.capture(skullState) : null;
        BannerSnapshot banner = state instanceof Banner bannerState ? BannerSnapshot.capture(bannerState) : null;
        return new SnapshotBlock(block.getBlockData().getAsString(), sign, pot, skull, banner);
    }

    private boolean matches(Block block, SnapshotBlock snapshot) {
        if (!block.getBlockData().getAsString().equals(snapshot.blockData())) return false;
        BlockState state = block.getState();
        if (snapshot.sign() != null && (!(state instanceof Sign sign) || !snapshot.sign().matches(sign))) return false;
        if (snapshot.pot() != null && (!(state instanceof DecoratedPot pot) || !snapshot.pot().matches(pot))) return false;
        if (snapshot.skull() != null && (!(state instanceof Skull skull) || !snapshot.skull().matches(skull))) return false;
        return snapshot.banner() == null || (state instanceof Banner banner && snapshot.banner().matches(banner));
    }

    private void applyTileState(Block block, SnapshotBlock snapshot) {
        BlockState state = block.getState();
        if (snapshot.sign() != null && state instanceof Sign sign) snapshot.sign().apply(sign);
        if (snapshot.pot() != null && state instanceof DecoratedPot pot) snapshot.pot().apply(pot);
        if (snapshot.skull() != null && state instanceof Skull skull) snapshot.skull().apply(skull);
        if (snapshot.banner() != null && state instanceof Banner banner) snapshot.banner().apply(banner);
    }

    private Block blockAt(World world, Region region, int index) {
        int height = region.maxY() - region.minY() + 1;
        int depth = region.maxZ() - region.minZ() + 1;
        int perX = height * depth;
        int xOffset = index / perX;
        int remainder = index % perX;
        int yOffset = remainder / depth;
        int zOffset = remainder % depth;
        return world.getBlockAt(region.minX() + xOffset, region.minY() + yOffset, region.minZ() + zOffset);
    }

    private int checkedVolume(Region region) {
        if (region.minX() > region.maxX() || region.minY() > region.maxY() || region.minZ() > region.maxZ()) {
            throw new IllegalArgumentException("Stall template bounds are inverted.");
        }
        long volume = region.volume();
        if (volume <= 0L || volume > MAX_BLOCKS) throw new IllegalArgumentException("Stall template volume is invalid: " + volume);
        return (int) volume;
    }

    private World world(Region region) {
        return Bukkit.getWorld(region.worldId());
    }

    private boolean hasItems(BlockState state) {
        return state instanceof InventoryHolder holder && itemStackCount(holder) > 0;
    }

    private int itemStackCount(InventoryHolder holder) {
        int count = 0;
        for (ItemStack item : holder.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) count++;
        }
        return count;
    }

    private void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", SCHEMA);
        for (StoredTemplate template : templates.values()) {
            String path = "templates." + template.id();
            Region region = template.region();
            yaml.set(path + ".world-id", region.worldId().toString());
            yaml.set(path + ".world-name", region.worldName());
            yaml.set(path + ".bounds.min-x", region.minX());
            yaml.set(path + ".bounds.max-x", region.maxX());
            yaml.set(path + ".bounds.min-y", region.minY());
            yaml.set(path + ".bounds.max-y", region.maxY());
            yaml.set(path + ".bounds.min-z", region.minZ());
            yaml.set(path + ".bounds.max-z", region.maxZ());
            yaml.set(path + ".captured-at", template.capturedAt());
            yaml.set(path + ".block-count", template.blockCount());
            yaml.set(path + ".sha256", template.sha256());
            yaml.set(path + ".materials", template.materials());
            yaml.set(path + ".payload", Base64.getEncoder().encodeToString(template.payload()));
        }
        AtomicYamlFile.save(yaml, file);
    }

    private StoredTemplate loadTemplate(String rawId, ConfigurationSection section) {
        String id = rawId.toLowerCase(Locale.ROOT);
        String worldIdRaw = section.getString("world-id");
        String payloadRaw = section.getString("payload");
        String expectedHash = section.getString("sha256");
        if (worldIdRaw == null || payloadRaw == null || expectedHash == null) throw new IllegalArgumentException("Required metadata is missing.");
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(payloadRaw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Payload is not valid Base64.");
        }
        if (payload.length == 0 || payload.length > MAX_COMPRESSED_BYTES) throw new IllegalArgumentException("Payload size is invalid.");
        if (!sha256(payload).equalsIgnoreCase(expectedHash)) throw new IllegalArgumentException("Payload SHA-256 does not match.");
        Region region = new Region(
            UUID.fromString(worldIdRaw),
            section.getString("world-name", "world"),
            section.getInt("bounds.min-x"), section.getInt("bounds.max-x"),
            section.getInt("bounds.min-y"), section.getInt("bounds.max-y"),
            section.getInt("bounds.min-z"), section.getInt("bounds.max-z")
        );
        int volume = checkedVolume(region);
        int count = section.getInt("block-count", 0);
        if (count != volume) throw new IllegalArgumentException("Block count does not match the saved bounds.");
        Map<String, Integer> materials = new LinkedHashMap<>();
        ConfigurationSection materialSection = section.getConfigurationSection("materials");
        if (materialSection != null) {
            materialSection.getKeys(false).stream().sorted().forEach(key -> materials.put(key, materialSection.getInt(key)));
        }
        return new StoredTemplate(id, region, section.getString("captured-at", "unknown"), count, expectedHash.toLowerCase(Locale.ROOT), payload, materials);
    }

    private byte[] encode(List<SnapshotBlock> blocks) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes); DataOutputStream output = new DataOutputStream(gzip)) {
            output.writeInt(PAYLOAD_VERSION);
            output.writeInt(blocks.size());
            for (SnapshotBlock block : blocks) {
                writeString(output, block.blockData());
                output.writeBoolean(block.sign() != null);
                if (block.sign() != null) block.sign().write(output);
                output.writeBoolean(block.pot() != null);
                if (block.pot() != null) block.pot().write(output);
                output.writeBoolean(block.skull() != null);
                if (block.skull() != null) block.skull().write(output);
                output.writeBoolean(block.banner() != null);
                if (block.banner() != null) block.banner().write(output);
            }
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length == 0 || payload.length > MAX_COMPRESSED_BYTES) throw new IOException("Compressed template is too large.");
        return payload;
    }

    private List<SnapshotBlock> decode(StoredTemplate template) throws IOException {
        if (!sha256(template.payload()).equals(template.sha256())) throw new IOException("Payload SHA-256 does not match.");
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(template.payload())))) {
            int version = input.readInt();
            if (version != PAYLOAD_VERSION) throw new IOException("Unsupported payload version " + version + ".");
            int count = input.readInt();
            if (count != template.blockCount() || count <= 0 || count > MAX_BLOCKS) throw new IOException("Payload block count is invalid.");
            List<SnapshotBlock> blocks = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String blockData = readString(input);
                SignSnapshot sign = input.readBoolean() ? SignSnapshot.read(input) : null;
                PotSnapshot pot = input.readBoolean() ? PotSnapshot.read(input) : null;
                SkullSnapshot skull = input.readBoolean() ? SkullSnapshot.read(input) : null;
                BannerSnapshot banner = input.readBoolean() ? BannerSnapshot.read(input) : null;
                Bukkit.createBlockData(blockData);
                blocks.add(new SnapshotBlock(blockData, sign, pot, skull, banner));
            }
            if (input.read() != -1) throw new IOException("Payload has trailing data.");
            return blocks;
        } catch (EOFException ex) {
            throw new IOException("Payload ended early.", ex);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException("Template string is too long.");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) throw new IOException("Template string length is invalid.");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Template string ended early.");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static Map<String, Integer> sortedMaterials(Map<String, Integer> materials) {
        Map<String, Integer> sorted = new LinkedHashMap<>();
        materials.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Market stall templates must be accessed on the server thread.");
    }

    record Region(UUID worldId, String worldName, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        long volume() {
            return ((long) maxX - minX + 1L) * ((long) maxY - minY + 1L) * ((long) maxZ - minZ + 1L);
        }
    }

    record CaptureResult(boolean success, String reason, int blockCount, int nonEmptyContainers, String sha256, Map<String, Integer> materials) {
        static CaptureResult failure(String reason) {
            return new CaptureResult(false, reason, 0, 0, "", Map.of());
        }
    }

    record Inspection(boolean success, String reason, int changedBlocks, int blockingContainers, int blockingItemStacks, String sha256) {
        static Inspection failure(String reason) {
            return new Inspection(false, reason, 0, 0, 0, "");
        }
    }

    record RestoreResult(boolean success, String reason, int restoredBlocks, String sha256) {
        static RestoreResult failure(String reason) {
            return new RestoreResult(false, reason, 0, "");
        }
    }

    private record StoredTemplate(String id, Region region, String capturedAt, int blockCount, String sha256, byte[] payload, Map<String, Integer> materials) {
    }

    private record SnapshotBlock(String blockData, SignSnapshot sign, PotSnapshot pot, SkullSnapshot skull, BannerSnapshot banner) {
    }

    private record SignSnapshot(List<String> front, List<String> back, String frontColor, String backColor, boolean frontGlow, boolean backGlow, boolean waxed) {
        static SignSnapshot capture(Sign sign) {
            SignSide front = sign.getSide(Side.FRONT);
            SignSide back = sign.getSide(Side.BACK);
            return new SignSnapshot(lines(front), lines(back), front.getColor().name(), back.getColor().name(), front.isGlowingText(), back.isGlowingText(), sign.isWaxed());
        }

        private static List<String> lines(SignSide side) {
            List<String> lines = new ArrayList<>(4);
            for (int line = 0; line < 4; line++) lines.add(PLAIN.serialize(side.line(line)));
            return List.copyOf(lines);
        }

        boolean matches(Sign sign) {
            SignSide currentFront = sign.getSide(Side.FRONT);
            SignSide currentBack = sign.getSide(Side.BACK);
            return front.equals(lines(currentFront))
                && back.equals(lines(currentBack))
                && frontColor.equals(currentFront.getColor().name())
                && backColor.equals(currentBack.getColor().name())
                && frontGlow == currentFront.isGlowingText()
                && backGlow == currentBack.isGlowingText()
                && waxed == sign.isWaxed();
        }

        void apply(Sign sign) {
            applySide(sign.getSide(Side.FRONT), front, frontColor, frontGlow);
            applySide(sign.getSide(Side.BACK), back, backColor, backGlow);
            sign.setWaxed(waxed);
            sign.update(true, false);
        }

        private static void applySide(SignSide side, List<String> lines, String color, boolean glowing) {
            for (int line = 0; line < 4; line++) side.line(line, net.kyori.adventure.text.Component.text(lines.get(line)));
            side.setColor(DyeColor.valueOf(color));
            side.setGlowingText(glowing);
        }

        void write(DataOutputStream output) throws IOException {
            for (String line : front) writeString(output, line);
            for (String line : back) writeString(output, line);
            writeString(output, frontColor);
            writeString(output, backColor);
            output.writeBoolean(frontGlow);
            output.writeBoolean(backGlow);
            output.writeBoolean(waxed);
        }

        static SignSnapshot read(DataInputStream input) throws IOException {
            List<String> front = new ArrayList<>(4);
            List<String> back = new ArrayList<>(4);
            for (int line = 0; line < 4; line++) front.add(readString(input));
            for (int line = 0; line < 4; line++) back.add(readString(input));
            String frontColor = readString(input);
            String backColor = readString(input);
            DyeColor.valueOf(frontColor);
            DyeColor.valueOf(backColor);
            return new SignSnapshot(List.copyOf(front), List.copyOf(back), frontColor, backColor, input.readBoolean(), input.readBoolean(), input.readBoolean());
        }
    }

    private record PotSnapshot(Map<DecoratedPot.Side, Material> sherds) {
        static PotSnapshot capture(DecoratedPot pot) {
            Map<DecoratedPot.Side, Material> sherds = new EnumMap<>(DecoratedPot.Side.class);
            sherds.putAll(pot.getSherds());
            return new PotSnapshot(Map.copyOf(sherds));
        }

        boolean matches(DecoratedPot pot) {
            return sherds.equals(pot.getSherds());
        }

        void apply(DecoratedPot pot) {
            for (DecoratedPot.Side side : DecoratedPot.Side.values()) pot.setSherd(side, sherds.getOrDefault(side, Material.BRICK));
            pot.update(true, false);
        }

        void write(DataOutputStream output) throws IOException {
            for (DecoratedPot.Side side : DecoratedPot.Side.values()) writeString(output, sherds.getOrDefault(side, Material.BRICK).name());
        }

        static PotSnapshot read(DataInputStream input) throws IOException {
            Map<DecoratedPot.Side, Material> sherds = new EnumMap<>(DecoratedPot.Side.class);
            for (DecoratedPot.Side side : DecoratedPot.Side.values()) {
                Material material = Material.matchMaterial(readString(input));
                if (material == null) throw new IOException("Unknown decorated-pot sherd material.");
                sherds.put(side, material);
            }
            return new PotSnapshot(Map.copyOf(sherds));
        }
    }

    private record SkullSnapshot(UUID profileId, String profileName, List<ProfilePropertySnapshot> properties, String noteBlockSound, String customName) {
        static SkullSnapshot capture(Skull skull) {
            ResolvableProfile profile = skull.getProfile();
            UUID id = profile == null ? null : profile.uuid();
            String name = profile == null ? null : profile.name();
            List<ProfilePropertySnapshot> properties = profile == null ? List.of() : profile.properties().stream()
                .map(ProfilePropertySnapshot::capture)
                .sorted(Comparator.comparing(ProfilePropertySnapshot::name)
                    .thenComparing(ProfilePropertySnapshot::value)
                    .thenComparing(property -> property.signature() == null ? "" : property.signature()))
                .toList();
            NamespacedKey sound = skull.getNoteBlockSound();
            return new SkullSnapshot(
                id,
                name,
                properties,
                sound == null ? null : sound.toString(),
                skull.customName() == null ? null : PLAIN.serialize(skull.customName())
            );
        }

        boolean matches(Skull skull) {
            SkullSnapshot current = capture(skull);
            return java.util.Objects.equals(profileId, current.profileId)
                && java.util.Objects.equals(profileName, current.profileName)
                && properties.equals(current.properties)
                && java.util.Objects.equals(noteBlockSound, current.noteBlockSound)
                && java.util.Objects.equals(customName, current.customName);
        }

        void apply(Skull skull) {
            if (profileId != null || (profileName != null && !profileName.isBlank()) || !properties.isEmpty()) {
                ResolvableProfile.Builder builder = ResolvableProfile.resolvableProfile();
                if (profileId != null) builder.uuid(profileId);
                if (profileName != null && !profileName.isBlank()) builder.name(profileName);
                builder.addProperties(properties.stream().map(ProfilePropertySnapshot::toProfileProperty).toList());
                skull.setProfile(builder.build());
            }
            skull.setNoteBlockSound(noteBlockSound == null ? null : NamespacedKey.fromString(noteBlockSound));
            skull.customName(customName == null ? null : net.kyori.adventure.text.Component.text(customName));
            skull.update(true, false);
        }

        void write(DataOutputStream output) throws IOException {
            writeNullableString(output, profileId == null ? null : profileId.toString());
            writeNullableString(output, profileName);
            output.writeInt(properties.size());
            for (ProfilePropertySnapshot property : properties) property.write(output);
            writeNullableString(output, noteBlockSound);
            writeNullableString(output, customName);
        }

        static SkullSnapshot read(DataInputStream input) throws IOException {
            String id = readNullableString(input);
            String name = readNullableString(input);
            int propertyCount = input.readInt();
            if (propertyCount < 0 || propertyCount > 128) throw new IOException("Skull profile property count is invalid.");
            List<ProfilePropertySnapshot> properties = new ArrayList<>(propertyCount);
            for (int index = 0; index < propertyCount; index++) properties.add(ProfilePropertySnapshot.read(input));
            String sound = readNullableString(input);
            if (sound != null && NamespacedKey.fromString(sound) == null) throw new IOException("Skull note-block sound key is invalid.");
            return new SkullSnapshot(id == null ? null : UUID.fromString(id), name, List.copyOf(properties), sound, readNullableString(input));
        }
    }

    private record ProfilePropertySnapshot(String name, String value, String signature) {
        static ProfilePropertySnapshot capture(com.destroystokyo.paper.profile.ProfileProperty property) {
            return new ProfilePropertySnapshot(property.getName(), property.getValue(), property.getSignature());
        }

        com.destroystokyo.paper.profile.ProfileProperty toProfileProperty() {
            return signature == null
                ? new com.destroystokyo.paper.profile.ProfileProperty(name, value)
                : new com.destroystokyo.paper.profile.ProfileProperty(name, value, signature);
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, name);
            writeString(output, value);
            writeNullableString(output, signature);
        }

        static ProfilePropertySnapshot read(DataInputStream input) throws IOException {
            return new ProfilePropertySnapshot(readString(input), readString(input), readNullableString(input));
        }
    }

    private record BannerSnapshot(String baseColor, List<BannerPatternSnapshot> patterns, String customName) {
        static BannerSnapshot capture(Banner banner) {
            return new BannerSnapshot(
                banner.getBaseColor().name(),
                banner.getPatterns().stream().map(BannerPatternSnapshot::capture).toList(),
                banner.customName() == null ? null : PLAIN.serialize(banner.customName())
            );
        }

        boolean matches(Banner banner) {
            return equals(capture(banner));
        }

        void apply(Banner banner) {
            banner.setBaseColor(DyeColor.valueOf(baseColor));
            banner.setPatterns(patterns.stream().map(BannerPatternSnapshot::toPattern).toList());
            banner.customName(customName == null ? null : net.kyori.adventure.text.Component.text(customName));
            banner.update(true, false);
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, baseColor);
            output.writeInt(patterns.size());
            for (BannerPatternSnapshot pattern : patterns) pattern.write(output);
            writeNullableString(output, customName);
        }

        static BannerSnapshot read(DataInputStream input) throws IOException {
            String baseColor = readString(input);
            DyeColor.valueOf(baseColor);
            int patternCount = input.readInt();
            if (patternCount < 0 || patternCount > 64) throw new IOException("Banner pattern count is invalid.");
            List<BannerPatternSnapshot> patterns = new ArrayList<>(patternCount);
            for (int index = 0; index < patternCount; index++) patterns.add(BannerPatternSnapshot.read(input));
            return new BannerSnapshot(baseColor, List.copyOf(patterns), readNullableString(input));
        }
    }

    private record BannerPatternSnapshot(String color, String patternKey) {
        static BannerPatternSnapshot capture(Pattern pattern) {
            NamespacedKey key = RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN).getKeyOrThrow(pattern.getPattern());
            return new BannerPatternSnapshot(pattern.getColor().name(), key.toString());
        }

        Pattern toPattern() {
            NamespacedKey key = NamespacedKey.fromString(patternKey);
            PatternType type = key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN).get(key);
            if (type == null) throw new IllegalArgumentException("Unknown banner pattern " + patternKey + ".");
            return new Pattern(DyeColor.valueOf(color), type);
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, color);
            writeString(output, patternKey);
        }

        static BannerPatternSnapshot read(DataInputStream input) throws IOException {
            BannerPatternSnapshot snapshot = new BannerPatternSnapshot(readString(input), readString(input));
            DyeColor.valueOf(snapshot.color);
            snapshot.toPattern();
            return snapshot;
        }
    }

    private static void writeNullableString(DataOutputStream output, String value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) writeString(output, value);
    }

    private static String readNullableString(DataInputStream input) throws IOException {
        return input.readBoolean() ? readString(input) : null;
    }
}
