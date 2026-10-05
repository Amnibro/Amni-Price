package com.amniscient.price.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amniscient.price.domain.Category
import com.amniscient.price.domain.LatLng

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Free-text branch label, e.g. "Main St". Distinguishes branches of the same chain. */
    val location: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Town or area used to group stores on the price map; filled from the map position when possible. */
    val region: String? = null,
) {
    val position: LatLng? get() = if (latitude != null && longitude != null) LatLng(latitude, longitude) else null

    /** "Kroger · Main St" when a branch label exists. */
    val displayName: String get() = location?.let { "$name · $it" } ?: name
}

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
    @ColumnInfo(defaultValue = "OTHER") val category: Category = Category.OTHER,
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

@Entity(
    tableName = "shopping_list",
    foreignKeys = [ForeignKey(ProductEntity::class, ["id"], ["productId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("productId")],
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val productId: Long? = null,
    val quantity: Int = 1,
    val checked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A price joined with the names needed to display it. */
data class PriceRow(
    @Embedded val price: PriceEntity,
    val productName: String,
    val storeName: String,
)
