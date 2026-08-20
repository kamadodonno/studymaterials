package com.pumaterial.app.data.repository

import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.data.local.database.PersonalFolderDao
import com.pumaterial.app.data.local.entity.PersonalFolderEntity
import com.pumaterial.app.data.local.entity.PersonalFolderItemEntity
import com.pumaterial.app.domain.model.Material
import com.pumaterial.app.domain.model.PersonalFolder
import com.pumaterial.app.domain.model.PersonalFolderItem
import com.pumaterial.app.domain.repository.PersonalFolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class PersonalFolderRepositoryImpl(
    private val folderDao: PersonalFolderDao
) : PersonalFolderRepository {

    override fun observeFolders(): Flow<List<PersonalFolder>> {
        return folderDao.getAllFolders().map { list ->
            list.map { entity ->
                PersonalFolder(
                    folderId = entity.folderId,
                    folderName = entity.folderName,
                    colorHex = entity.colorHex,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    override fun observeItemsInFolder(folderId: String): Flow<List<PersonalFolderItem>> {
        return folderDao.getItemsInFolder(folderId).map { list ->
            list.map { entity ->
                PersonalFolderItem(
                    folderId = entity.folderId,
                    materialId = entity.materialId,
                    title = entity.title,
                    fileName = entity.fileName,
                    fileType = entity.fileType,
                    localFilePath = entity.localFilePath,
                    addedAt = entity.addedAt
                )
            }
        }
    }

    override suspend fun createFolder(name: String, colorHex: String): Resource<Unit> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return Resource.Error("Folder name cannot be empty.")
        }
        return try {
            val entity = PersonalFolderEntity(
                folderId = UUID.randomUUID().toString(),
                folderName = trimmed,
                colorHex = colorHex.ifBlank { "#1E40AF" },
                createdAt = System.currentTimeMillis()
            )
            folderDao.insertFolder(entity)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to create folder.", e)
        }
    }

    override suspend fun updateFolder(
        folderId: String,
        name: String,
        colorHex: String
    ): Resource<Unit> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return Resource.Error("Folder name cannot be empty.")
        }
        return try {
            folderDao.updateFolder(folderId, trimmed, colorHex)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to update folder.", e)
        }
    }

    override suspend fun deleteFolder(folderId: String): Resource<Unit> {
        return try {
            folderDao.deleteFolder(folderId)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to delete folder.", e)
        }
    }

    override suspend fun addItemToFolder(
        folderId: String,
        material: Material
    ): Resource<Unit> {
        return try {
            val entity = PersonalFolderItemEntity(
                folderId = folderId,
                materialId = material.id,
                title = material.title,
                fileName = material.fileName,
                fileType = material.fileType,
                localFilePath = material.localFilePath,
                addedAt = System.currentTimeMillis()
            )
            folderDao.insertItemToFolder(entity)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to add item to folder.", e)
        }
    }

    override suspend fun removeItemFromFolder(
        folderId: String,
        materialId: String
    ): Resource<Unit> {
        return try {
            folderDao.removeItemFromFolder(folderId, materialId)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to remove item.", e)
        }
    }
}
