package com.controlcal.app.test

import com.controlcal.core.model.Product
import com.controlcal.core.repo.ProductRepository

class FakeProductRepository : ProductRepository {
    val products = mutableListOf<Product>()

    override suspend fun getById(id: Long): Product? = products.find { it.id == id }

    override suspend fun getByBarcode(barcode: String): Product? = products.find { it.barcode == barcode }

    override suspend fun searchByName(prefix: String): List<Product> =
        products.filter { it.name.startsWith(prefix, ignoreCase = true) }

    override suspend fun getActiveProducts(): List<Product> = products.filter { !it.archived }

    override suspend fun getAllIncludingArchived(): List<Product> = products.toList()

    override suspend fun getPantryProducts(): List<Product> = products.filter { !it.archived && it.inPantry }

    override suspend fun setInPantry(id: Long, inPantry: Boolean) {
        val idx = products.indexOfFirst { it.id == id }
        if (idx >= 0) products[idx] = products[idx].copy(inPantry = inPantry)
    }

    override suspend fun updatePantryConfig(
        id: Long,
        inPantry: Boolean,
        minG: Double?,
        maxG: Double?,
        unitName: String?,
        unitG: Double?,
    ) {
        val idx = products.indexOfFirst { it.id == id }
        if (idx >= 0) {
            products[idx] = products[idx].copy(
                inPantry = inPantry,
                minG = minG,
                maxG = maxG,
                unitName = unitName,
                unitG = unitG,
            )
        }
    }

    override suspend fun insert(product: Product): Long {
        val id = if (product.id == 0L) (products.size + 1).toLong() else product.id
        products.add(product.copy(id = id))
        return id
    }

    override suspend fun update(product: Product) {
        val idx = products.indexOfFirst { it.id == product.id }
        if (idx >= 0) products[idx] = product
    }

    override suspend fun archive(id: Long) {
        val idx = products.indexOfFirst { it.id == id }
        if (idx >= 0) products[idx] = products[idx].copy(archived = true)
    }

    override suspend fun hasEntries(productId: Long): Boolean = false
}
