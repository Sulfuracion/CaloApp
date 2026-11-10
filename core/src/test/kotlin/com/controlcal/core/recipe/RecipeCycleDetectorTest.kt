package com.controlcal.core.recipe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeCycleDetectorTest {

    @Test
    fun `direct self-reference is detected as cycle`() {
        val targetRecipeProductId = 100L
        val candidateIngredientProductId = 100L

        val isCycle = RecipeCycleDetector.wouldCreateCycle(
            targetRecipeProductId = targetRecipeProductId,
            candidateIngredientProductId = candidateIngredientProductId,
            ingredientProductIdsProvider = { emptyList() },
        )

        assertTrue(isCycle)
    }

    @Test
    fun `direct 2-node cycle is detected`() {
        // Recipe A (prod 100) wants to add Recipe B (prod 200).
        // But Recipe B already has Recipe A (prod 100) as an ingredient.
        val graph = mapOf(
            200L to listOf(100L, 50L), // B contains A and standard item 50
        )

        val isCycle = RecipeCycleDetector.wouldCreateCycle(
            targetRecipeProductId = 100L,
            candidateIngredientProductId = 200L,
            ingredientProductIdsProvider = { graph[it] ?: emptyList() },
        )

        assertTrue(isCycle)
    }

    @Test
    fun `indirect 3-node cycle is detected`() {
        // Recipe A (100) -> wants to add B (200).
        // B (200) contains C (300).
        // C (300) contains A (100).
        val graph = mapOf(
            200L to listOf(300L),
            300L to listOf(100L, 400L),
        )

        val isCycle = RecipeCycleDetector.wouldCreateCycle(
            targetRecipeProductId = 100L,
            candidateIngredientProductId = 200L,
            ingredientProductIdsProvider = { graph[it] ?: emptyList() },
        )

        assertTrue(isCycle)
    }

    @Test
    fun `valid DAG without cycles is allowed`() {
        // Recipe A (100) wants to add Recipe B (200).
        // Recipe B (200) contains Recipe C (300) and D (400).
        // Neither C nor D contain A.
        val graph = mapOf(
            200L to listOf(300L, 400L),
            300L to listOf(500L),
            400L to listOf(500L),
        )

        val isCycle = RecipeCycleDetector.wouldCreateCycle(
            targetRecipeProductId = 100L,
            candidateIngredientProductId = 200L,
            ingredientProductIdsProvider = { graph[it] ?: emptyList() },
        )

        assertFalse(isCycle)
    }

    @Test
    fun `new recipe with null or 0 product id cannot form cycle`() {
        val graph = mapOf(
            200L to listOf(300L),
        )

        val isCycleNull = RecipeCycleDetector.wouldCreateCycle(
            targetRecipeProductId = null,
            candidateIngredientProductId = 200L,
            ingredientProductIdsProvider = { graph[it] ?: emptyList() },
        )

        val isCycleZero = RecipeCycleDetector.wouldCreateCycle(
            targetRecipeProductId = 0L,
            candidateIngredientProductId = 200L,
            ingredientProductIdsProvider = { graph[it] ?: emptyList() },
        )

        assertFalse(isCycleNull)
        assertFalse(isCycleZero)
    }

    @Test
    fun `candidate without ingredients cannot form cycle`() {
        val isCycle = RecipeCycleDetector.wouldCreateCycle(
            targetRecipeProductId = 100L,
            candidateIngredientProductId = 55L, // ordinary product, not a recipe
            ingredientProductIdsProvider = { emptyList() },
        )

        assertFalse(isCycle)
    }
}
