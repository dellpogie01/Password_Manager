package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [VaultEntryEntity::class, VaultMetaEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_meta ADD COLUMN unlockMode TEXT NOT NULL DEFAULT 'LEGACY'")
            }
        }

        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getDatabase(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "pw_manager_vault.db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
