package me.rique.smpcore.player;

import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerManagerTest {

    @Test
    void detectsEveryStockStateThatCouldShowAsUnavailable() {
        assertTrue(VillagerManager.stockNeedsRepair(12, 12));
        assertTrue(VillagerManager.stockNeedsRepair(0, 12));
        assertFalse(VillagerManager.stockNeedsRepair(0, 1_000_000));
    }

    @Test
    void repairsLegacyInfiniteOffersThatSuppressPlayerExperience() {
        assertTrue(VillagerManager.offerNeedsRepair(0, 1_000_000, false, 2));
        assertFalse(VillagerManager.offerNeedsRepair(0, 1_000_000, true, 2));
    }

    @Test
    void repairsLegacyOffersThatCannotLevelTheirVillager() {
        assertTrue(VillagerManager.offerNeedsRepair(0, 1_000_000, true, 0));
        assertTrue(VillagerManager.offerNeedsRepair(0, 1_000_000, true, -1));
        assertEquals(2, VillagerManager.repairedVillagerExperience(1, 0));
        assertEquals(5, VillagerManager.repairedVillagerExperience(2, 0));
        assertEquals(10, VillagerManager.repairedVillagerExperience(3, 0));
        assertEquals(15, VillagerManager.repairedVillagerExperience(4, 0));
        assertEquals(30, VillagerManager.repairedVillagerExperience(5, 0));
        assertEquals(17, VillagerManager.repairedVillagerExperience(3, 17));
    }

    @Test
    void oldClickVillagersSnapshotWrapperIsAccepted() {
        String legacy = "{foo:1,levels:{VillagerData:{level:5}},bar:2}";

        String repaired = VillagerManager.fixLegacySnapshot(legacy);

        assertEquals("{foo:1,VillagerData:{level:5},bar:2}", repaired);
    }

    @Test
    void retainsLegacyDataNamespaceForExistingItemsAndClaims() {
        assertEquals("clickvillagers:villager", VillagerManager.VILLAGER_ITEM_KEY.toString());
        assertEquals("clickvillagers:nbt", VillagerManager.VILLAGER_NBT_KEY.toString());
        assertEquals("clickvillagers:owner", VillagerManager.OWNER_KEY.toString());
        assertEquals("clickvillagers:trade", VillagerManager.TRADE_OPEN_KEY.toString());
    }

    @Test
    void retainsTheLegacyPermissionSurface() {
        assertEquals("clickvillagers.pickup", VillagerManager.PERMISSION_PICKUP);
        assertEquals("clickvillagers.place", VillagerManager.PERMISSION_PLACE);
        assertEquals("clickvillagers.hopper", VillagerManager.PERMISSION_HOPPER);
        assertEquals("clickvillagers.claim", VillagerManager.PERMISSION_CLAIM);
        assertEquals("clickvillagers.partner", VillagerManager.PERMISSION_PARTNER);
        assertEquals("clickvillagers.config", VillagerManager.PERMISSION_CONFIG);
    }

    @Test
    void ownsInteractionBeforeOpeningMerchant() throws Exception {
        EventHandler handler = annotation("onVillagerInteract", PlayerInteractEntityEvent.class);

        assertEquals(EventPriority.HIGHEST, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    @Test
    void disablesStockConsumptionBeforeOtherTradeListeners() throws Exception {
        EventHandler handler = annotation("onTrade", PlayerTradeEvent.class);

        assertEquals(EventPriority.LOWEST, handler.priority());
        assertFalse(handler.ignoreCancelled());
    }

    @Test
    void repairsMerchantBeforeInventoryIsSent() throws Exception {
        EventHandler handler = annotation("onMerchantOpen", InventoryOpenEvent.class);

        assertEquals(EventPriority.LOWEST, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    @Test
    void startupRepairRunsAtAMutatingPriority() throws Exception {
        EventHandler handler = annotation("onServerLoad", ServerLoadEvent.class);

        assertEquals(EventPriority.NORMAL, handler.priority());
    }

    private static EventHandler annotation(String methodName, Class<?> parameterType)
        throws Exception {
        Method method = VillagerManager.class.getDeclaredMethod(methodName, parameterType);
        return method.getAnnotation(EventHandler.class);
    }
}
