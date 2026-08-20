package com.pumaterial.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pumaterial.app.core.common.Constants
import com.pumaterial.app.data.local.entity.CachedModuleEntity
import com.pumaterial.app.data.local.entity.CachedSubjectEntity
import com.pumaterial.app.data.local.entity.DownloadedMaterialEntity
import com.pumaterial.app.data.local.entity.PersonalFolderEntity
import com.pumaterial.app.data.local.entity.PersonalFolderItemEntity

@Database(
    entities = [
        DownloadedMaterialEntity::class,
        PersonalFolderEntity::class,
        PersonalFolderItemEntity::class,
        CachedSubjectEntity::class,
        CachedModuleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadedMaterialDao(): DownloadedMaterialDao
    abstract fun personalFolderDao(): PersonalFolderDao
    abstract fun cachedHierarchyDao(): CachedHierarchyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
