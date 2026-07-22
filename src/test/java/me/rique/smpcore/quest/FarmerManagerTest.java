package me.rique.smpcore.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmerManagerTest {
    @Test
    void heartyFoodDoublesNutritionAndSaturation() {
        FarmerManager.FoodStats result = FarmerManager.doubledFoodStats(6, 7.2F);

        assertEquals(12, result.nutrition());
        assertEquals(14.4F, result.saturation(), 0.0001F);
    }

    @Test
    void heartyFoodNeverProducesNegativeStats() {
        FarmerManager.FoodStats result = FarmerManager.doubledFoodStats(-2, -1.0F);

        assertEquals(0, result.nutrition());
        assertEquals(0.0F, result.saturation());
    }
}
