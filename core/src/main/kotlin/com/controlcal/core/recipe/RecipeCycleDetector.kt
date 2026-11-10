package com.controlcal.core.recipe

/**
 * Pure graph cycle detector for recipe dependencies.
 *
 * Recipes can be used as ingredients in other recipes (e.g. homemade tomato sauce in lasagna).
 * This detector prevents circular references such as:
 * - A contains A (direct self-reference)
 * - A contains B and B contains A (2-node cycle)
 * - A -> B -> C -> A (multi-node cycle)
 */
object RecipeCycleDetector {

    /**
     * Determines whether adding [candidateIngredientProductId] as an ingredient to a recipe
     * whose associated product has ID [targetRecipeProductId] would produce a circular dependency.
     *
     * @param targetRecipeProductId The product ID of the recipe being created/edited. If `null` or 0,
     *                               it is a new recipe that does not yet exist in any other recipe.
     * @param candidateIngredientProductId The product ID of the food item being considered as an ingredient.
     * @param ingredientProductIdsProvider Function that, given a recipe product ID, returns the list of
     *                                     product IDs of its ingredients. If the product is not a recipe,
     *                                     it returns an empty list.
     * @return `true` if adding this ingredient would create a cycle, `false` otherwise.
     */
    fun wouldCreateCycle(
        targetRecipeProductId: Long?,
        candidateIngredientProductId: Long,
        ingredientProductIdsProvider: (recipeProductId: Long) -> List<Long>,
    ): Boolean {
        if (targetRecipeProductId == null || targetRecipeProductId == 0L) {
            return false
        }

        // Direct self-reference
        if (candidateIngredientProductId == targetRecipeProductId) {
            return true
        }

        // BFS traversal down the candidate's ingredient dependency graph
        val visited = mutableSetOf<Long>()
        val queue = ArrayDeque<Long>()

        visited.add(candidateIngredientProductId)
        queue.add(candidateIngredientProductId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val childProductIds = ingredientProductIdsProvider(current)

            for (childId in childProductIds) {
                if (childId == targetRecipeProductId) {
                    return true
                }
                if (visited.add(childId)) {
                    queue.add(childId)
                }
            }
        }

        return false
    }
}
