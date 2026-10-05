package com.amniscient.price.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun fromSource(source: PriceSource): String = source.name
    @TypeConverter fun toSource(value: String): PriceSource =
        runCatching { PriceSource.valueOf(value) }.getOrDefault(PriceSource.MANUAL)
}

/**
 * Bump [version] and add a Migration when changing entities. Exported schemas live in
 * app/schemas so migrations can be reviewed and tested.
 */
@Database(
    entities = [StoreEntity::class, ProductEntity::class, PriceEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun productDao(): ProductDao
    abstract fun priceDao(): PriceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "amni-price.db").build()
    }
}
