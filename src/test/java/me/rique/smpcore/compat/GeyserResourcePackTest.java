package me.rique.smpcore.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeyserResourcePackTest {
    private static final Path BEDROCK_ROOT = Path.of("src/main/bedrock-resourcepack");
    private static final Path JAVA_TEXTURE_ROOT = Path.of("src/main/resourcepack/assets/smpcore/textures/item");
    private static final Path JAVA_EQUIPMENT_ROOT = Path.of("src/main/resourcepack/assets/smpcore/textures/entity/equipment/humanoid");
    private static final Set<String> EXPECTED_MODELS = Set.of(
        "smpcore:backpack",
        "smpcore:expanded_backpack",
        "smpcore:team_leader_crown",
        "smpcore:first_dragon_sigil"
    );

    @Test
    void packManifestAndTextureAtlasAreValid() throws IOException {
        JsonObject manifest = read(BEDROCK_ROOT.resolve("manifest.json"));
        assertEquals(2, manifest.get("format_version").getAsInt());
        assertEquals("resources", manifest.getAsJsonArray("modules").get(0).getAsJsonObject().get("type").getAsString());

        JsonObject textureData = read(BEDROCK_ROOT.resolve("textures/item_texture.json"))
            .getAsJsonObject("texture_data");
        assertEquals(EXPECTED_MODELS, textureData.keySet());
        for (String model : EXPECTED_MODELS) {
            String fileName = model.substring(model.indexOf(':') + 1) + ".png";
            assertTrue(Files.isRegularFile(JAVA_TEXTURE_ROOT.resolve(fileName)), "Missing source texture for " + model);
        }

        JsonObject attachable = read(BEDROCK_ROOT.resolve("attachables/team_leader_crown.json"))
            .getAsJsonObject("minecraft:attachable")
            .getAsJsonObject("description");
        assertEquals("smpcore:team_leader_crown", attachable.get("identifier").getAsString());
        assertEquals("geometry.humanoid.armor.helmet", attachable.getAsJsonObject("geometry").get("default").getAsString());
        assertTrue(Files.isRegularFile(JAVA_EQUIPMENT_ROOT.resolve("team_leader_crown.png")));
    }

    @Test
    void everyMappingUsesAUniquePackedTexture() throws IOException {
        JsonObject mapping = read(Path.of("src/main/geyser/custom_mappings/smpcore-items.json"));
        assertEquals(2, mapping.get("format_version").getAsInt());

        JsonObject items = mapping.getAsJsonObject("items");
        Set<String> seenModels = new java.util.HashSet<>();
        Set<String> seenIdentifiers = new java.util.HashSet<>();
        for (String baseItem : items.keySet()) {
            assertTrue(baseItem.startsWith("minecraft:"));
            JsonArray definitions = items.getAsJsonArray(baseItem);
            definitions.forEach(element -> {
                JsonObject definition = element.getAsJsonObject();
                String model = definition.get("model").getAsString();
                String identifier = definition.get("bedrock_identifier").getAsString();
                assertTrue(EXPECTED_MODELS.contains(model), "Unexpected model " + model);
                assertTrue(seenModels.add(model), "Duplicate model " + model);
                assertTrue(seenIdentifiers.add(identifier), "Duplicate Bedrock identifier " + identifier);
            });
        }
        assertEquals(EXPECTED_MODELS, seenModels);
    }

    private JsonObject read(Path path) throws IOException {
        try (java.io.Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
