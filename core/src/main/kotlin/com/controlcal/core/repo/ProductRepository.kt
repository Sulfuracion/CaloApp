package com.controlcal.core.repo

import com.controlcal.core.model.Product

interface ProductRepository {
    suspend fun getById(id: Long): Product?
    suspend fun getByBarcode(barcode: String): Product?
    suspend fun searchByName(prefix: String): List<Product>
    suspend fun getActiveProducts(): List<Product>
    suspend fun getPantryProducts(): List<Product>
    suspend fun setInPantry(id: Long, inPantry: Boolean)
    suspend fun updatePantryConfig(
        id: Long,
        inPantry: Boolean,
        minG: Double?,
        maxG: Double?,
        unitName: String?,
        unitG: Double?,
    )
    suspend fun insert(product: Product): Long
    suspend fun update(product: Product)
    suspend fun archive(id: Long)
    suspend fun hasEntries(productId: Long): Boolean
}
