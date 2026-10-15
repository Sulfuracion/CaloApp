package com.controlcal.app.data.db

import android.database.sqlite.SQLiteConstraintException
import com.controlcal.app.data.db.dao.ProductDao
import com.controlcal.core.model.ProductSource
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductDaoTest : DaoTestBase() {

    private val productDao: ProductDao
        get() = db.productDao()

    @Test
    fun insertAndReadById() = runTest {
        val id = productDao.insert(
            TestData.product(
                barcode = "8412345678901",
                name = "Arroz integral",
                brand = "Marca",
                kcal100g = 350.0,
                protein100g = 7.5,
                carbs100g = 77.0,
                fat100g = 1.2,
                fiber100g = 2.8,
                sugars100g = 0.9,
                satFat100g = 0.3,
                salt100g = 0.01,
                isLiquid = false,
                unitName = "ración",
                unitG = 60.0,
                servingG = 60.0,
                minG = 0.0,
                maxG = 500.0,
                inPantry = true,
                source = ProductSource.MANUAL,
                archived = false,
                createdAt = 11L,
                updatedAt = 22L,
            ),
        )

        assertTrue(id > 0)
        val saved = productDao.getById(id)
        assertNotNull(saved)
        assertEquals("Arroz integral", saved!!.name)
        assertEquals("Marca", saved.brand)
        assertEquals("8412345678901", saved.barcode)
        assertEquals(350.0, saved.kcal100g, 0.0)
        assertEquals(7.5, saved.protein100g, 0.0)
        assertEquals(77.0, saved.carbs100g, 0.0)
        assertEquals(1.2, saved.fat100g, 0.0)
        assertEquals(2.8, saved.fiber100g!!, 0.0)
        assertEquals(0.9, saved.sugars100g!!, 0.0)
        assertEquals(0.3, saved.satFat100g!!, 0.0)
        assertEquals(0.01, saved.salt100g!!, 0.0)
        assertTrue(saved.inPantry)
        assertEquals(ProductSource.MANUAL, saved.source)
        assertEquals(60.0, saved.unitG!!, 0.0)
        assertEquals(11L, saved.createdAt)
        assertEquals(22L, saved.updatedAt)
    }

    @Test
    fun barcodeMustBeUnique() = runTest {
        productDao.insert(TestData.product(barcode = "8412345678901", name = "Uno"))
        assertFailsWith<SQLiteConstraintException> {
            productDao.insert(TestData.product(barcode = "8412345678901", name = "Dos"))
        }
        // A different barcode is fine.
        productDao.insert(TestData.product(barcode = "8412345678902", name = "Tres"))
    }

    @Test
    fun barcodeNullCanBeRepeated() = runTest {
        // UNIQUE allows several NULL barcodes (SQLite semantics).
        productDao.insert(TestData.product(barcode = null, name = "Uno"))
        productDao.insert(TestData.product(barcode = null, name = "Dos"))
    }

    @Test
    fun searchByNamePrefix() = runTest {
        productDao.insert(TestData.product(barcode = "8412345678901", name = "Arroz integral"))
        productDao.insert(TestData.product(barcode = "8412345678902", name = "Arroz blanco"))
        productDao.insert(TestData.product(barcode = "8412345678903", name = "Leche entera"))

        val result = productDao.searchByName("arr")
        assertEquals(listOf("Arroz blanco", "Arroz integral"), result.map { it.name })
    }

    @Test
    fun archiveProductKeepsEntriesReadable() = runTest {
        val productId = productDao.insert(TestData.product(name = "Yogur natural"))
        db.dailyLogDao().insert(TestData.dailyLog())
        val entryDao = db.mealEntryDao()
        entryDao.insert(
            TestData.mealEntry(productId = productId, productName = "Yogur natural", grams = 125.0),
        )
        entryDao.insert(
            TestData.mealEntry(
                meal = com.controlcal.core.model.DefaultMealIds.SNACK,
                productId = productId,
                productName = "Yogur natural",
                grams = 125.0,
                loggedAt = 2_000L,
            ),
        )

        productDao.archive(productId)

        val archived = productDao.getById(productId)
        assertNotNull(archived)
        assertTrue(archived!!.archived)

        // Entries remain readable and keep the copied product name.
        val entries = entryDao.entriesForDate(TestData.DATE_A)
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.productName == "Yogur natural" })
        assertTrue(entries.all { it.productId == productId })

        // Archived products are excluded from active lists.
        assertTrue(productDao.activeProducts().isEmpty())
    }

    @Test
    fun getByBarcodeFindsExactMatchOnly() = runTest {
        productDao.insert(TestData.product(barcode = "8412345678901", name = "Uno"))
        productDao.insert(TestData.product(barcode = "8412345678902", name = "Dos"))

        val found = productDao.getByBarcode("8412345678902")
        assertNotNull(found)
        assertEquals("Dos", found!!.name)
        assertNull(productDao.getByBarcode("999"))
    }

    @Test
    fun pantryProductsReturnsOnlyActivePantryItems() = runTest {
        productDao.insert(TestData.product(barcode = null, name = "Huevos", inPantry = true))
        productDao.insert(TestData.product(barcode = null, name = "Arroz", inPantry = false))
        productDao.insert(TestData.product(barcode = null, name = "Avena", inPantry = true))
        val pArchived = productDao.insert(TestData.product(barcode = null, name = "Pan", inPantry = true))
        productDao.archive(pArchived)

        val inPantry = productDao.pantryProducts()
        assertEquals(listOf("Avena", "Huevos"), inPantry.map { it.name })
    }

    @Test
    fun setInPantryTogglesState() = runTest {
        val id = productDao.insert(TestData.product(name = "Leche", inPantry = false))
        assertEquals(false, productDao.getById(id)!!.inPantry)

        productDao.setInPantry(id, true, updatedAt = 500L)
        val updated = productDao.getById(id)!!
        assertTrue(updated.inPantry)
        assertEquals(500L, updated.updatedAt)

        productDao.setInPantry(id, false, updatedAt = 600L)
        val toggledOff = productDao.getById(id)!!
        assertEquals(false, toggledOff.inPantry)
        assertEquals(600L, toggledOff.updatedAt)
    }

    @Test
    fun updatePantryConfigUpdatesLimitsAndUnit() = runTest {
        val id = productDao.insert(TestData.product(name = "Huevo", inPantry = false))

        productDao.updatePantryConfig(
            id = id,
            inPantry = true,
            minG = 50.0,
            maxG = 150.0,
            unitName = "huevo",
            unitG = 55.0,
            updatedAt = 1000L,
        )

        val updated = productDao.getById(id)!!
        assertTrue(updated.inPantry)
        assertEquals(50.0, updated.minG!!, 0.001)
        assertEquals(150.0, updated.maxG!!, 0.001)
        assertEquals("huevo", updated.unitName)
        assertEquals(55.0, updated.unitG!!, 0.001)
        assertEquals(1000L, updated.updatedAt)
    }
}
