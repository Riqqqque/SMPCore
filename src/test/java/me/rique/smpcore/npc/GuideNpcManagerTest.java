package me.rique.smpcore.npc;

import org.junit.jupiter.api.Test;
import org.bukkit.entity.EntityType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideNpcManagerTest {

    @Test
    void everyGuideNpcTypeRoundTripsThroughItsPersistentId() {
        for (GuideNpcManager.GuideNpcType type : GuideNpcManager.GuideNpcType.values()) {
            assertEquals(type, GuideNpcManager.GuideNpcType.byId(type.id()));
        }
    }

    @Test
    void gearExpertUsesStablePersistentIdentity() {
        GuideNpcManager.GuideNpcType type = GuideNpcManager.GuideNpcType.GEAR_EXPERT;

        assertEquals("gear_expert", type.id());
        assertEquals("Orin the Artificer", type.displayName());
    }

    @Test
    void specializedNpcsRequireTheirSystemPermissions() {
        assertEquals("smpcore.mayor.use", GuideNpcManager.permissionFor(GuideNpcManager.GuideNpcType.MAYOR));
        assertEquals("smpcore.dungeon.use", GuideNpcManager.permissionFor(GuideNpcManager.GuideNpcType.DUNGEON_KEEPER));
        assertEquals("smpcore.blackjack", GuideNpcManager.permissionFor(GuideNpcManager.GuideNpcType.DEALER));
        assertEquals("smpcore.bossmastery.use", GuideNpcManager.permissionFor(GuideNpcManager.GuideNpcType.BOSSBROKER));
        assertEquals("smpcore.blackmarket.use", GuideNpcManager.permissionFor(GuideNpcManager.GuideNpcType.BLACK_MARKETEER));
        assertEquals("smpcore.guide.use", GuideNpcManager.permissionFor(GuideNpcManager.GuideNpcType.MINER));
        assertEquals("smpcore.spawnlife.use", GuideNpcManager.permissionFor(GuideNpcManager.GuideNpcType.FETCH_HOUND));
        assertEquals("smpcore.spawnlife.use", GuideNpcManager.permissionFor(GuideNpcManager.GuideNpcType.TAVERN_HOST));
    }

    @Test
    void professionNpcsUsePlayerSkinsWhileWitchKeepsHerNativeType() {
        assertEquals(EntityType.PLAYER, GuideNpcManager.GuideNpcType.FARMER.entityType());
        assertEquals(EntityType.PLAYER, GuideNpcManager.GuideNpcType.FISHER.entityType());
        assertEquals(EntityType.WITCH, GuideNpcManager.GuideNpcType.WITCH.entityType());
    }

    @Test
    void spawnLifeUsesNativeAnimalsAndAHiddenIllusioner() {
        assertEquals(EntityType.WOLF, GuideNpcManager.GuideNpcType.FETCH_HOUND.entityType());
        assertEquals(EntityType.CAT, GuideNpcManager.GuideNpcType.TOWN_CAT.entityType());
        assertEquals(EntityType.FOX, GuideNpcManager.GuideNpcType.TOWN_FOX.entityType());
        assertEquals(EntityType.PARROT, GuideNpcManager.GuideNpcType.TOWN_PARROT.entityType());
        assertEquals(EntityType.ILLUSIONER, GuideNpcManager.GuideNpcType.HIDDEN_ILLUSIONER.entityType());
        assertTrue(GuideNpcManager.GuideNpcType.TOWN_BAKER.isSpawnLife());
        assertTrue(GuideNpcManager.GuideNpcType.TOWN_SEAMSTRESS.isSpawnLife());
        assertEquals(EntityType.PLAYER, GuideNpcManager.GuideNpcType.TAVERN_HOST.entityType());
        assertEquals(EntityType.PLAYER, GuideNpcManager.GuideNpcType.TAVERN_REGULAR.entityType());
        assertEquals(EntityType.PLAYER, GuideNpcManager.GuideNpcType.TAVERN_TIPSY.entityType());
        assertTrue(GuideNpcManager.GuideNpcType.TAVERN_HOST.isSpawnLife());
        assertTrue(GuideNpcManager.GuideNpcType.TAVERN_REGULAR.isSpawnLife());
        assertTrue(GuideNpcManager.GuideNpcType.TAVERN_TIPSY.isSpawnLife());
    }
}
