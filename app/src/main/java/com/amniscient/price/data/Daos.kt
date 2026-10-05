package com.amniscient.price.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): StoreEntity?

    @Insert suspend fun insert(store: StoreEntity): Long
    @Update suspend fun update(store: StoreEntity)
    @Delete suspend fun delete(store: StoreEntity)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE normalizedName = :normalized LIMIT 1")
    suspend fun findByNormalizedName(normalized: String): ProductEntity?

    @Insert suspend fun insert(product: ProductEntity): Long
    @Update suspend fun update(product: ProductEntity)
    @Delete suspend fun delete(product: ProductEntity)
}

@Dao
interface PriceDao {
    @Insert suspend fun insert(price: PriceEntity): Long

    @Delete suspend fun delete(price: PriceEntity)

    @Query(
        """SELECT prices.*, stores.name AS storeName FROM prices
           JOIN stores ON stores.id = prices.storeId
           WHERE productId = :productId ORDER BY observedAt DESC""",
    )
    fun observeForProduct(productId: Long): Flow<List<PriceWithStore>>

    /** The most recent observation for every (product, store) pair. */
    @Query(
        """SELECT p.* FROM prices p
           JOIN (SELECT productId, storeId, MAX(observedAt) AS maxAt FROM prices GROUP BY productId, storeId) l
           ON p.productId = l.productId AND p.storeId = l.storeId AND p.observedAt = l.maxAt""",
    )
    fun observeLatest(): Flow<List<PriceEntity>>

    @Query("SELECT * FROM prices ORDER BY observedAt")
    suspend fun getAll(): List<PriceEntity>

    @Query(
        """SELECT COUNT(*) FROM prices WHERE productId = :productId AND storeId = :storeId
           AND observedAt = :observedAt AND priceCents = :priceCents""",
    )
    suspend fun countExact(productId: Long, storeId: Long, observedAt: Long, priceCents: Long): Int
}
