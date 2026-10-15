package com.controlcal.app.data.repo

import com.controlcal.app.data.db.dao.MealEntryDao
import com.controlcal.app.data.db.dao.ProductDao
import com.controlcal.core.model.Product
import com.controlcal.core.repo.ProductRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val mealEntryDao: MealEntryDao,
) : ProductRepository {

    override suspend fun getById(id: Long): Product? =
        productDao.getById(id)?.toDomain()

    override suspend fun getByBarcode(barcode: String): Product? =
        productDao.getByBarcode(barcode)?.toDomain()

    override suspend fun searchByName(prefix: String): List<Product> =
        productDao.searchByName(prefix).map { it.toDomain() }

    override suspend fun getActiveProducts(): List<Product> =
        productDao.activeProducts().map { it.toDomain() }

    override suspend fun getPantryProducts(): List<Product> =
        productDao.pantryProducts().map { it.toDomain() }

    override suspend fun setInPantry(id: Long, inPantry: Boolean) =
        productDao.setInPantry(id, inPantry)

    override suspend fun updatePantryConfig(
        id: Long,
        inPantry: Boolean,
        minG: Double?,
        maxG: Double?,
        unitName: String?,
        unitG: Double?,
    ) = productDao.updatePantryConfig(id, inPantry, minG, maxG, unitName, unitG)

    override suspend fun insert(product: Product): Long =
        productDao.insert(product.toEntity())

    override suspend fun update(product: Product) =
        productDao.update(product.toEntity())

    override suspend fun archive(id: Long) =
        productDao.archive(id)

    override suspend fun hasEntries(productId: Long): Boolean =
        mealEntryDao.entriesForProduct(productId).isNotEmpty()
}
