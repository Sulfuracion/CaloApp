package com.controlcal.core.search

import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class ProductSearchEngineTest {

    private val sampleProducts = listOf(
        Product(
            id = 1L,
            barcode = "8410100012345",
            name = "Leche semidesnatada",
            brand = "Pascual",
            kcal100g = 46.0,
            protein100g = 3.1,
            carbs100g = 4.7,
            fat100g = 1.6,
            source = ProductSource.OFF,
        ),
        Product(
            id = 2L,
            barcode = "8410100099999",
            name = "Yogur natural",
            brand = "Danone",
            kcal100g = 57.0,
            protein100g = 3.3,
            carbs100g = 4.2,
            fat100g = 2.8,
            source = ProductSource.MANUAL,
        ),
        Product(
            id = 3L,
            barcode = null,
            name = "Plátano de Canarias",
            brand = null,
            kcal100g = 89.0,
            protein100g = 1.1,
            carbs100g = 20.0,
            fat100g = 0.3,
            source = ProductSource.OCR,
        ),
        Product(
            id = 4L,
            barcode = null,
            name = "Champiñones laminados",
            brand = "Hacendado",
            kcal100g = 22.0,
            protein100g = 2.2,
            carbs100g = 2.0,
            fat100g = 0.3,
            source = ProductSource.MANUAL,
        ),
        Product(
            id = 5L,
            barcode = "8412345678901",
            name = "Arándanos frescos",
            brand = null,
            kcal100g = 57.0,
            protein100g = 0.7,
            carbs100g = 14.5,
            fat100g = 0.3,
            source = ProductSource.MANUAL,
            archived = true, // Archived product!
        ),
        Product(
            id = 6L,
            barcode = "8412345678902",
            name = "Copos de avena integral",
            brand = "Mercadona",
            kcal100g = 370.0,
            protein100g = 13.5,
            carbs100g = 60.0,
            fat100g = 7.0,
            source = ProductSource.MANUAL,
        ),
    )

    @Test
    fun search_matchesSubstringInName() {
        val results = ProductSearchEngine.search("semidesnatada", sampleProducts)
        assertEquals(1, results.size)
        assertEquals("Leche semidesnatada", results[0].name)

        val avenaResults = ProductSearchEngine.search("avena", sampleProducts)
        assertEquals(1, avenaResults.size)
        assertEquals("Copos de avena integral", avenaResults[0].name)
    }

    @Test
    fun search_matchesByBrand() {
        val pascualResults = ProductSearchEngine.search("Pascual", sampleProducts)
        assertEquals(1, pascualResults.size)
        assertEquals("Leche semidesnatada", pascualResults[0].name)

        val danoneResults = ProductSearchEngine.search("danone", sampleProducts)
        assertEquals(1, danoneResults.size)
        assertEquals("Yogur natural", danoneResults[0].name)
    }

    @Test
    fun search_ignoresAccentsAndCase() {
        // User types without accent
        val platanoResults = ProductSearchEngine.search("platano", sampleProducts)
        assertEquals(1, platanoResults.size)
        assertEquals("Plátano de Canarias", platanoResults[0].name)

        // User types in uppercase without accent
        val upperResults = ProductSearchEngine.search("PLATANO", sampleProducts)
        assertEquals(1, upperResults.size)
        assertEquals("Plátano de Canarias", upperResults[0].name)
    }

    @Test
    fun search_handlesEnye() {
        // User types 'champinon' without enye
        val results1 = ProductSearchEngine.search("champinon", sampleProducts)
        assertEquals(1, results1.size)
        assertEquals("Champiñones laminados", results1[0].name)

        // User types 'champiñón' with enye and accent
        val results2 = ProductSearchEngine.search("champiñón", sampleProducts)
        assertEquals(1, results2.size)
        assertEquals("Champiñones laminados", results2[0].name)
    }

    @Test
    fun search_excludesArchivedProducts() {
        val results = ProductSearchEngine.search("arándanos", sampleProducts)
        // Product 5 has name "Arándanos frescos", but archived = true
        assertTrue(results.isEmpty())
    }

    @Test
    fun search_matchesBarcodeWithEightOrMoreDigits() {
        // Exact barcode (13 digits)
        val exactResults = ProductSearchEngine.search("8410100012345", sampleProducts)
        assertEquals(1, exactResults.size)
        assertEquals("Leche semidesnatada", exactResults[0].name)

        // Prefix barcode (8 digits)
        val prefixResults = ProductSearchEngine.search("84101000", sampleProducts)
        assertEquals(2, prefixResults.size)
        assertTrue(prefixResults.any { it.name == "Leche semidesnatada" })
        assertTrue(prefixResults.any { it.name == "Yogur natural" })

        // Fewer than 8 digits does not search by barcode
        val shortResults = ProductSearchEngine.search("8410", sampleProducts)
        assertTrue(shortResults.isEmpty())
    }

    @Test
    fun search_ranksRecentProductsFirst() {
        // Suppose product 6 (Copos de avena) was logged recently, then product 1 (Leche)
        val recentIds = listOf(6L, 1L)

        // Empty query: returns all active products with recents first
        val allResults = ProductSearchEngine.search("", sampleProducts, recentIds)
        assertEquals(5, allResults.size)
        assertEquals(6L, allResults[0].id) // Copos de avena
        assertEquals(1L, allResults[1].id) // Leche semidesnatada
        // Remaining 3 are sorted alphabetically: Champiñones (4), Plátano (3), Yogur (2)
        assertEquals(4L, allResults[2].id)
        assertEquals(3L, allResults[3].id)
        assertEquals(2L, allResults[4].id)

        // Non-empty query with recents: matching recent product comes before matching non-recent product
        val queryWithRecents = ProductSearchEngine.search("a", sampleProducts, recentIds)
        assertEquals(6L, queryWithRecents[0].id)
        assertEquals(1L, queryWithRecents[1].id)
    }

    @Test
    fun search_includesProductsFromAllSources() {
        val allActive = ProductSearchEngine.search("", sampleProducts)
        assertTrue(allActive.any { it.source == ProductSource.OFF })
        assertTrue(allActive.any { it.source == ProductSource.MANUAL })
        assertTrue(allActive.any { it.source == ProductSource.OCR })
    }

    @Test
    fun search_performanceTest_under50msWith1000Products() {
        // Generate 1,000 realistic products
        val largeCatalog = (1..1000).map { i ->
            Product(
                id = i.toLong(),
                barcode = if (i % 2 == 0) "84100000000$i".take(13).padEnd(13, '0') else null,
                name = when (i % 5) {
                    0 -> "Leche desnatada bio lote $i"
                    1 -> "Copos de avena suave $i"
                    2 -> "Plátano de Canarias premium $i"
                    3 -> "Yogur natural cremoso $i"
                    else -> "Champiñones enteros al ajillo $i"
                },
                brand = when (i % 4) {
                    0 -> "Pascual"
                    1 -> "Danone"
                    2 -> "Hacendado"
                    else -> null
                },
                kcal100g = 100.0 + (i % 50),
                protein100g = 10.0,
                carbs100g = 20.0,
                fat100g = 5.0,
                source = when (i % 3) {
                    0 -> ProductSource.OFF
                    1 -> ProductSource.OCR
                    else -> ProductSource.MANUAL
                },
                archived = (i % 100 == 0),
            )
        }

        val recentIds = (1..50).map { it.toLong() }

        // Warm-up JIT
        repeat(5) {
            ProductSearchEngine.search("platano", largeCatalog, recentIds)
        }

        // Measure execution time
        val elapsed = measureTimeMillis {
            val results = ProductSearchEngine.search("platano", largeCatalog, recentIds)
            assertFalse(results.isEmpty())
        }

        println("ProductSearchEngine search time for 1,000 products: $elapsed ms")
        assertTrue("Execution time must be under 50 ms but was $elapsed ms", elapsed < 50)
    }
}
