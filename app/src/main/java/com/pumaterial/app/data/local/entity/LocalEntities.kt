package com.pumaterial.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_materials")
data class DownloadedMaterialEntity(
    @PrimaryKey val materialId: String,
    val subjectId: String,
    val moduleId: String,
    val title: String,
    val fileName: String,
    val fileType: String,
    val localFilePath: String,
    val localVersion: Int,
    val cloudVersion: Int,
    val fileSize: Long,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "personal_folders")
data class PersonalFolderEntity(
    @PrimaryKey val folderId: String,
    val folderName: String,
    val colorHex: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "personal_folder_items",
    primaryKeys = ["folderId", "materialId"],
    foreignKeys = [
        ForeignKey(
            entity = PersonalFolderEntity::class,
            parentColumns = ["folderId"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["folderId"])]
)
data class PersonalFolderItemEntity(
    val folderId: String,
    val materialId: String,
    val title: String,
    val fileName: String,
    val fileType: String,
    val localFilePath: String?,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_subjects")
data class CachedSubjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val departmentId: String,
    val academicYearId: String,
    val icon: String,
    val order: Int,
    val isActive: Boolean,
    val moduleCount: Int,
    val materialCount: Int,
    val updatedAt: Long
)

@Entity(tableName = "cached_modules")
data class CachedModuleEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val name: String,
    val order: Int,
    val description: String,
    val isActive: Boolean,
    val materialCount: Int
)
