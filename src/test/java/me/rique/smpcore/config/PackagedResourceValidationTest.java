package me.rique.smpcore.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagedResourceValidationTest {

    @Test
    void allYamlResourcesParseStrictly() throws IOException {
        List<Path> files = filesUnder(Path.of("src", "main", "resources"), ".yml", ".yaml");
        assertFalse(files.isEmpty(), "No YAML resources were found");

        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        for (Path file : files) {
            assertDoesNotThrow(() -> {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    yaml.load(reader);
                }
            }, () -> "Malformed YAML resource: " + file);
        }
    }

    @Test
    void goldenAppleRecipeUsesGoldIngots() throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        try (Reader reader = Files.newBufferedReader(Path.of("src", "main", "resources", "config.yml"), StandardCharsets.UTF_8)) {
            Map<?, ?> config = yaml.load(reader);
            Map<?, ?> crafting = (Map<?, ?>) config.get("crafting");
            assertEquals("GOLD_INGOT", crafting.get("golden-apple-surround-material"));
        }
    }

    @Test
    void tabListDefaultsAreCompleteAndDoNotContainPersonalOwners() throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        try (Reader reader = Files.newBufferedReader(Path.of("src", "main", "resources", "config.yml"), StandardCharsets.UTF_8)) {
            Map<?, ?> config = yaml.load(reader);
            Map<?, ?> tabList = (Map<?, ?>) config.get("tab-list");
            assertEquals(Boolean.TRUE, tabList.get("always-show-online-players"));
            assertEquals(40, tabList.get("refresh-ticks"));
            assertEquals("ETHEREAL SMP", tabList.get("server-title"));
            assertEquals(List.of(), tabList.get("owner-uuids"));
        }
    }

    @Test
    void allResourcePackJsonParsesAndPackMetadataIsAtTheRoot() throws IOException {
        Path packRoot = Path.of("src", "main", "resourcepack");
        assertTrue(Files.isRegularFile(packRoot.resolve("pack.mcmeta")), "pack.mcmeta must be at the pack root");

        List<Path> files = filesUnder(packRoot, ".json", ".mcmeta");
        assertFalse(files.isEmpty(), "No resource-pack JSON was found");
        for (Path file : files) {
            assertDoesNotThrow(() -> {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonParser.parseReader(reader);
                }
            }, () -> "Malformed resource-pack JSON: " + file);
        }
    }

    @Test
    void customBackpackModelsHaveValidTextures() throws IOException {
        Path packRoot = Path.of("src", "main", "resourcepack");
        assertItemTexture(packRoot, "backpack", "generated", 16, 16);
        assertItemTexture(packRoot, "expanded_backpack", "generated", 16, 16);
    }

    @Test
    void everyCustomItemModelReferenceResolvesInsideThePack() throws IOException {
        Path packRoot = Path.of("src", "main", "resourcepack");
        Path itemDefinitions = packRoot.resolve("assets/smpcore/items");
        Set<String> missingModels = new TreeSet<>();
        Set<String> missingTextures = new TreeSet<>();

        for (Path definition : filesUnder(itemDefinitions, ".json")) {
            var json = JsonParser.parseString(Files.readString(definition, StandardCharsets.UTF_8));
            collectNamespacedValues(json, "model", "smpcore:item/", model -> {
                Path modelPath = packRoot.resolve("assets/smpcore/models/item/" + model + ".json");
                if (!Files.isRegularFile(modelPath)) missingModels.add(model);
            });
        }

        Path models = packRoot.resolve("assets/smpcore/models/item");
        for (Path model : filesUnder(models, ".json")) {
            var json = JsonParser.parseString(Files.readString(model, StandardCharsets.UTF_8));
            collectNamespacedValues(json, "layer0", "smpcore:item/", texture -> {
                Path texturePath = packRoot.resolve("assets/smpcore/textures/item/" + texture + ".png");
                if (!Files.isRegularFile(texturePath)) missingTextures.add(texture);
            });
        }

        assertTrue(missingModels.isEmpty(), "Missing custom item models: " + missingModels);
        assertTrue(missingTextures.isEmpty(), "Missing custom item textures: " + missingTextures);
    }

    private static void collectNamespacedValues(
        com.google.gson.JsonElement element,
        String requiredKey,
        String prefix,
        java.util.function.Consumer<String> consumer
    ) {
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                var value = entry.getValue();
                if ((requiredKey == null || requiredKey.equals(entry.getKey()))
                    && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isString()
                    && value.getAsString().startsWith(prefix)) {
                    consumer.accept(value.getAsString().substring(prefix.length()));
                }
                collectNamespacedValues(value, requiredKey, prefix, consumer);
            }
        } else if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) {
                collectNamespacedValues(child, requiredKey, prefix, consumer);
            }
        }
    }

    private static void assertItemTexture(
        Path packRoot,
        String itemId,
        String expectedParent,
        int expectedWidth,
        int expectedHeight
    ) throws IOException {
        Path modelPath = packRoot.resolve("assets/smpcore/models/item/" + itemId + ".json");
        var model = JsonParser.parseString(Files.readString(modelPath, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("minecraft:item/" + expectedParent, model.get("parent").getAsString(), itemId + " parent");
        assertEquals("smpcore:item/" + itemId, model.getAsJsonObject("textures").get("layer0").getAsString(), itemId + " texture");

        Path definitionPath = packRoot.resolve("assets/smpcore/items/" + itemId + ".json");
        var definition = JsonParser.parseString(Files.readString(definitionPath, StandardCharsets.UTF_8)).getAsJsonObject();
        var renderedModel = definition.getAsJsonObject("model");
        assertEquals("minecraft:model", renderedModel.get("type").getAsString(), itemId + " item model type");
        assertEquals("smpcore:item/" + itemId, renderedModel.get("model").getAsString(), itemId + " rendered model");

        Path texturePath = packRoot.resolve("assets/smpcore/textures/item/" + itemId + ".png");
        assertTrue(Files.isRegularFile(texturePath), "Missing texture: " + texturePath);
        assertTrue(Files.size(texturePath) > 0, "Empty texture: " + texturePath);
        var image = ImageIO.read(texturePath.toFile());
        assertNotNull(image, "Unreadable PNG: " + texturePath);
        assertEquals(expectedWidth, image.getWidth(), itemId + " texture width");
        assertEquals(expectedHeight, image.getHeight(), itemId + " texture height");
    }

    private static List<Path> filesUnder(Path root, String... extensions) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    for (String extension : extensions) {
                        if (name.endsWith(extension)) return true;
                    }
                    return false;
                })
                .sorted()
                .toList();
        }
    }
}
