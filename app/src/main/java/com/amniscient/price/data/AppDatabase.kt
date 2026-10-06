package com.amniscient.price.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.amniscient.price.domain.Categorizer
import com.amniscient.price.domain.Category

class Converters {
    @TypeConverter fun fromSource(source: PriceSource): String = source.name
    @TypeConverter fun toSource(value: String): PriceSource =
        runCatching { PriceSource.valueOf(value) }.getOrDefault(PriceSource.MANUAL)

    @TypeConverter fun fromCategory(category: Category): String = category.name
    @TypeConverter fun toCategory(value: String): Category =
        runCatching { Category.valueOf(value) }.getOrDefault(Category.OTHER)
}

/**
 * Bump [version] and add a Migration when changing entities. Exported schemas live in
 * app/schemas so migrations can be reviewed and tested.
 */
@Database(
    entities = [StoreEntity::class, ProductEntity::class, PriceEntity::class, ShoppingItemEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun productDao(): ProductDao
    abstract fun priceDao(): PriceDao
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        /** v2: product categories and the shopping list. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                db.execSQL(SHOPPING_LIST_SQL)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_list_productId` ON `shopping_list` (`productId`)")
                // Auto-categorize products saved before categories existed.
                db.query("SELECT id, name FROM products").use { c ->
                    while (c.moveToNext()) {
                        val category = Categorizer.categorize(c.getString(1))
                        db.execSQL("UPDATE products SET category = ? WHERE id = ?", arrayOf(category.name, c.getLong(0)))
                    }
                }
            }
        }

        /** v3: store coordinates and region for the price map. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stores ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE stores ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE stores ADD COLUMN region TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stores ADD COLUMN externalId TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stores_externalId` ON `stores` (`externalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_prices_productId_storeId_observedAt` ON `prices` (`productId`, `storeId`, `observedAt`)")
            }
        }
        private const val SHOPPING_LIST_SQL =
            "CREATE TABLE IF NOT EXISTS `shopping_list` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `productId` INTEGER, `quantity` INTEGER NOT NULL, `checked` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "amni-price.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
