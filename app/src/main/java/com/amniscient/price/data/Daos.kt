package com.amniscient.price.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE id = :id")
    fun observeById(id: Long): Flow<StoreEntity?>

    @Query("SELECT * FROM stores WHERE name = :name COLLATE NOCASE")
    suspend fun findAllByName(name: String): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun getById(id: Long): StoreEntity?

    @Query("SELECT * FROM stores WHERE externalId IS NOT NULL")
    suspend fun community(): List<StoreEntity>
    @Query("DELETE FROM stores WHERE externalId IS NOT NULL AND id NOT IN (SELECT storeId FROM prices)")
    suspend fun deleteUnusedCommunity()
    @Insert suspend fun insert(store: StoreEntity): Long
    @Update suspend fun update(store: StoreEntity)
    @Delete suspend fun delete(store: StoreEntity)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE normalizedName = :normalized LIMIT 1")
    suspend fun findByNormalizedName(normalized: String): ProductEntity?

    @Insert suspend fun insert(product: ProductEntity): Long
    @Update suspend fun update(product: ProductEntity)
    @Delete suspend fun delete(product: ProductEntity)
    @Query("DELETE FROM products WHERE id NOT IN (SELECT productId FROM prices) AND id NOT IN (SELECT productId FROM shopping_list WHERE productId IS NOT NULL)")
    suspend fun deleteUnpriced()
}

@Dao
interface PriceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(price: PriceEntity): Long

    @Delete suspend fun delete(price: PriceEntity)

    @Query(
        """SELECT prices.*, CASE WHEN stores.location IS NULL THEN stores.name ELSE stores.name || ' · ' || stores.location END AS storeName FROM prices
           JOIN stores ON stores.id = prices.storeId
           WHERE productId = :productId ORDER BY observedAt DESC""",
    )
    fun observeForProduct(productId: Long): Flow<List<PriceWithStore>>

    @Query(
        """SELECT prices.*, products.name AS productName, CASE WHEN stores.location IS NULL THEN stores.name ELSE stores.name || ' · ' || stores.location END AS storeName FROM prices
           JOIN products ON products.id = prices.productId
           JOIN stores ON stores.id = prices.storeId
           WHERE prices.source != 'COMMUNITY'
           ORDER BY observedAt DESC, prices.id DESC LIMIT :limit""",
    )
    fun observeRecent(limit: Int): Flow<List<PriceRow>>

    /** The most recent observation for every (product, store) pair. */
    @Query(
        """SELECT p.* FROM prices p
           JOIN (SELECT productId, storeId, MAX(observedAt) AS maxAt FROM prices WHERE channel = 'IN_STORE' GROUP BY productId, storeId) l
           ON p.productId = l.productId AND p.storeId = l.storeId AND p.observedAt = l.maxAt
           WHERE p.channel = 'IN_STORE'""",
    )
    fun observeLatest(): Flow<List<PriceEntity>>

    @Query("SELECT * FROM prices")
    fun observeAll(): Flow<List<PriceEntity>>
    @Query("SELECT DISTINCT productId FROM prices WHERE source != 'COMMUNITY'")
    suspend fun ownProductIds(): List<Long>
    @Query("SELECT * FROM prices WHERE source != 'COMMUNITY'")
    fun observeOwn(): Flow<List<PriceEntity>>
    @Query("SELECT COUNT(*) FROM prices WHERE source = 'COMMUNITY'")
    fun observeCommunityCount(): Flow<Int>
    @Query("DELETE FROM prices WHERE source = 'COMMUNITY'")
    suspend fun deleteCommunity()
    @Insert suspend fun insertAll(list: List<PriceEntity>)

    @Query("SELECT * FROM prices ORDER BY observedAt")
    suspend fun getAll(): List<PriceEntity>

    @Query("SELECT * FROM prices WHERE productId = :productId AND storeId = :storeId AND channel = 'IN_STORE' ORDER BY observedAt DESC LIMIT 1")
    suspend fun latestAt(productId: Long, storeId: Long): PriceEntity?

    @Query(
        """SELECT COUNT(*) FROM prices WHERE productId = :productId AND storeId = :storeId
           AND observedAt = :observedAt AND priceCents = :priceCents""",
    )
    suspend fun countExact(productId: Long, storeId: Long, observedAt: Long, priceCents: Long): Int
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_list ORDER BY checked, createdAt")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Insert suspend fun insert(item: ShoppingItemEntity): Long
    @Update suspend fun update(item: ShoppingItemEntity)
    @Delete suspend fun delete(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_list WHERE checked = 1")
    suspend fun clearChecked()

    @Query("SELECT * FROM shopping_list WHERE productId = :productId AND checked = 0 LIMIT 1")
    suspend fun findOpenForProduct(productId: Long): ShoppingItemEntity?
}
