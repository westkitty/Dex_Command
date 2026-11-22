package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProjectEntity::class,
        WorkItemEntity::class,
        ExecutionRecordEntity::class,
        DependencyLinkEntity::class,
        ImportedDocumentEntity::class,
        AuditEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DexTypeConverters::class)
abstract class DexDatabase : RoomDatabase() {
    abstract fun dexDao(): DexDao

    companion object {
        @Volatile
        private var INSTANCE: DexDatabase? = null

        fun getInstance(context: Context): DexDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DexDatabase::class.java,
                    "dex_command.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
