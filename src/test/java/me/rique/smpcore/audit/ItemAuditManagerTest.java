package me.rique.smpcore.audit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemAuditManagerTest {

    @Test
    void doubleJumpBooksAreNotTreatedAsQuietCraftedItems() {
        assertFalse(ItemAuditManager.isQuietCraftableItemKey("custom_enchant:double_jump"));
        assertTrue(ItemAuditManager.isQuietCraftableItemKey("custom_enchant:kingslayer"));
    }

    @Test
    void ancientCityOriginIsRecognizedAsGenerated() {
        assertTrue(ItemAuditManager.isGeneratedMethod("ancient_city_loot"));
        assertTrue(ItemAuditManager.isGeneratedMethod("ANCIENT_CITY_LOOT"));
        assertFalse(ItemAuditManager.isGeneratedMethod("craftable_first_seen"));
        assertFalse(ItemAuditManager.isGeneratedMethod(null));
    }

    @Test
    void customEnchantBooksDoNotLogEveryBackpackOwnerHandoff() {
        assertFalse(ItemAuditManager.shouldRecordOwnerChange("custom_enchant:double_jump"));
        assertFalse(ItemAuditManager.shouldRecordOwnerChange("custom_enchant:kingslayer"));
        assertTrue(ItemAuditManager.shouldRecordOwnerChange("legendary:confessors_splitter"));
        assertFalse(ItemAuditManager.shouldRecordOwnerChange(null));
    }

    @Test
    void duplicateInstanceAlertsDedupeGloballyByTrackedId() {
        String first = ItemAuditManager.anomalyDedupeKey(
            UUID.randomUUID(),
            "custom_enchant:double_jump",
            "tracked-book-id",
            "duplicate_instance",
            "Inventory slot 1 and Backpack slot 4"
        );
        String second = ItemAuditManager.anomalyDedupeKey(
            UUID.randomUUID(),
            "custom_enchant:double_jump",
            "tracked-book-id",
            "duplicate_instance",
            "Team Vault slot 20 and Backpack slot 38"
        );

        assertEquals(first, second);
        assertEquals(24L * 60L * 60L * 1000L, ItemAuditManager.anomalyCooldownMillis("duplicate_instance"));
        assertEquals(5L * 60L * 1000L, ItemAuditManager.anomalyCooldownMillis("unknown_origin"));
    }
}
