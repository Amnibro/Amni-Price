package com.amniscient.price.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Upgrades a real v1 database to the latest version and checks data survives. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun migrateV1ToLatest() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO stores (id, name, location, createdAt) VALUES (1, 'Aldi', 'Oak Ave', 0)")
            execSQL("INSERT INTO products (id, name, normalizedName, barcode, brand, sizeText, createdAt) VALUES (1, 'Whole Milk', 'whole milk', NULL, NULL, '1 gal', 0)")
            execSQL("INSERT INTO prices (id, productId, storeId, priceCents, observedAt, source, onSale, note) VALUES (1, 1, 1, 329, 0, 'SHELF', 0, NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 5, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)

        db.query("SELECT category FROM products WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("DAIRY", c.getString(0)) // auto-categorized during the v2 migration
        }
        db.query("SELECT latitude, region FROM stores WHERE id = 1").use { c ->
            c.moveToFirst()
            assertNull(if (c.isNull(0)) null else c.getDouble(0))
            assertNull(c.getString(1))
        }
        db.query("SELECT externalId FROM stores WHERE id = 1").use { c ->
            c.moveToFirst()
            assertNull(c.getString(0))
        }
        db.query("SELECT kind FROM stores WHERE id = 1").use { c -> c.moveToFirst(); assertEquals("GROCERY", c.getString(0)) }
        db.query("SELECT channel FROM prices WHERE id = 1").use { c -> c.moveToFirst(); assertEquals("IN_STORE", c.getString(0)) }
        db.query("SELECT priceCents FROM prices WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals(329L, c.getLong(0))
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
