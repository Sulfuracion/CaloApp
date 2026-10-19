package com.controlcal.core.search

import com.controlcal.core.model.Product

object ProductSearchEngine {

    /**
     * Searches and ranks local products matching [query].
     *
     * Rules from PLAN.md section 5.1 & Task M.7:
     * - Searches in all active local products (excluding [Product.archived]).
     * - Matches in any part of [Product.name] or [Product.brand], ignoring case,
     *   accents (tildes), dieresis, and folding ñ/Ñ to n.
     * - If [query] consists solely of 8 or more digits, it also matches by
     *   [Product.barcode] (exact match or barcode starting with the query digits).
     * - Ranking order: products present in [recentProductIds] appear first
     *   (ordered according to their position in [recentProductIds]), followed
     *   by the rest of the matching products ordered alphabetically by name.
     */
    fun search(
        query: String,
        products: List<Product>,
        recentProductIds: List<Long> = emptyList(),
    ): List<Product> {
        val trimmedRawQuery = query.trim()
        val normalizedQuery = TextNormalizer.normalize(trimmedRawQuery)

        // Only active products can appear
        val activeProducts = products.filter { !it.archived }

        val recencyMap = recentProductIds.withIndex().associate { it.value to it.index }

        val matchedProducts = if (normalizedQuery.isEmpty()) {
            activeProducts
        } else {
            val isBarcodeQuery = trimmedRawQuery.length >= 8 && trimmedRawQuery.all { it.isDigit() }

            activeProducts.filter { product ->
                val nameNormalized = TextNormalizer.normalize(product.name)
                val brandNormalized = product.brand?.let { TextNormalizer.normalize(it) } ?: ""

                val matchesText = nameNormalized.contains(normalizedQuery) ||
                    (brandNormalized.isNotEmpty() && brandNormalized.contains(normalizedQuery))

                val matchesBarcode = isBarcodeQuery && product.barcode != null &&
                    (product.barcode == trimmedRawQuery || product.barcode.startsWith(trimmedRawQuery))

                matchesText || matchesBarcode
            }
        }

        return matchedProducts.sortedWith(
            compareBy<Product> { recencyMap[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.name.lowercase() }
        )
    }
}
