package com.controlcal.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.controlcal.app.data.db.entity.ProductEntity

@Dao
interface ProductDao {

    @Insert
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductEntity?

    /** Prefix search from the first letter; used by the diary search box. */
    @Query("SELECT * FROM products WHERE name LIKE :prefix || '%' ORDER BY name")
    suspend fun searchByName(prefix: String): List<ProductEntity>

    /**
     * Products with entries are never deleted: they are archived
     * (section 4 of docs/PLAN.md).
     */
    @Query("UPDATE products SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("SELECT * FROM products WHERE archived = 0 ORDER BY name")
    suspend fun activeProducts(): List<ProductEntity>
}
