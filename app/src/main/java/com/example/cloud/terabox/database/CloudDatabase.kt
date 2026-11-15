package com.example.cloud.terabox.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CloudAccountEntity::class,
        CloudFileEntity::class,
        CloudRootEntity::class,
        CloudMediaStateEntity::class,
        CloudSyncStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CloudDatabase : RoomDatabase() {
    abstract fun accountDao(): CloudAccountDao
    abstract fun fileDao(): CloudFileDao
    abstract fun rootDao(): CloudRootDao
    abstract fun mediaStateDao(): CloudMediaStateDao
    abstract fun syncStateDao(): CloudSyncStateDao

    companion object {
        @Volatile
        private var INSTANCE: CloudDatabase? = null

        fun getDatabase(context: Context): CloudDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CloudDatabase::class.java,
                    "proxxquepus_cloud_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
