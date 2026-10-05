package com.amniscient.price.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val location: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = true), Index("normalizedName")],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val barcode: String? = null,
    val brand: String? = null,
    val sizeText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class PriceSource { SHELF, RECEIPT, MANUAL, IMPORT }

@Entity(
    tableName = "prices",
    foreignKeys = [
        ForeignKey(ProductEntity::class, ["id"], ["productId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(StoreEntity::class, ["id"], ["storeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("productId"), Index("storeId"), Index("observedAt")],
)
data class PriceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val storeId: Long,
    val priceCents: Long,
    val observedAt: Long = System.currentTimeMillis(),
    val source: PriceSource = PriceSource.MANUAL,
    val onSale: Boolean = false,
    val note: String? = null,
)

data class PriceWithStore(
    @Embedded val price: PriceEntity,
    val storeName: String,
)
