package com.controlcal.core.suggest

import com.controlcal.core.model.SavedMeal
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedMealRankerTest {

    @Test
    fun rank_ordersByRecencyAndFrequencyCorrectly() {
        val meal1 = SavedMeal(id = 1L, name = "Never used old", createdAt = 1000L, updatedAt = 1000L, lastUsedAt = null, useCount = 0)
        val meal2 = SavedMeal(id = 2L, name = "Never used recent", createdAt = 2000L, updatedAt = 2000L, lastUsedAt = null, useCount = 0)
        val meal3 = SavedMeal(id = 3L, name = "Used yesterday 1 time", createdAt = 500L, updatedAt = 500L, lastUsedAt = 3000L, useCount = 1)
        val meal4 = SavedMeal(id = 4L, name = "Used today 5 times", createdAt = 500L, updatedAt = 500L, lastUsedAt = 4000L, useCount = 5)
        val meal5 = SavedMeal(id = 5L, name = "Used today 2 times", createdAt = 500L, updatedAt = 500L, lastUsedAt = 4000L, useCount = 2)

        val unranked = listOf(meal1, meal2, meal3, meal4, meal5)
        val ranked = SavedMealRanker.rank(unranked)

        // Expected order:
        // 1. meal4: lastUsedAt = 4000, useCount = 5
        // 2. meal5: lastUsedAt = 4000, useCount = 2
        // 3. meal3: lastUsedAt = 3000, useCount = 1
        // 4. meal2: lastUsedAt = null, createdAt = 2000
        // 5. meal1: lastUsedAt = null, createdAt = 1000
        assertEquals(listOf(4L, 5L, 3L, 2L, 1L), ranked.map { it.id })
    }
}
