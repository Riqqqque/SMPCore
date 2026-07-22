package me.rique.smpcore.compat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockSkullManagerTest {

    @Test
    void acceptsMinecraftSkinProfiles() {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/abc123\"}}}";
        assertTrue(BedrockSkullManager.isValidProfileValue(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsMalformedOrUnrelatedBase64() {
        assertFalse(BedrockSkullManager.isValidProfileValue("not-base64"));
        assertFalse(BedrockSkullManager.isValidProfileValue(Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void registersEveryFamiliarTextureForBedrock() {
        assertTrue(BedrockSkullManager.isBuiltInSkinHash("b412e70375ec99ee38ae94b30e9b10752d459662b54794dfe66fe6a183c672d3"));
        assertTrue(BedrockSkullManager.isBuiltInSkinHash("a236b0e63ecbbe2a0090e4bd4f043d36b6068d25bb981389765450d8d7ee6d8c"));
        assertTrue(BedrockSkullManager.isBuiltInSkinHash("5656274dc2350d527b9e58868946c60f06727a8013ef5ca32eadf1fe72d98867"));
        assertTrue(BedrockSkullManager.isBuiltInSkinHash("8e47a564bb58bf248ef7774b227de4681e95cb8245bd8388d288cbf1ec17a888"));
    }
}
