package com.pumaterial.app.data.repository

import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.core.file.FileDownloader
import com.pumaterial.app.data.local.database.DownloadedMaterialDao
import com.pumaterial.app.data.local.entity.DownloadedMaterialEntity
import com.pumaterial.app.domain.model.Material
import com.pumaterial.app.domain.repository.DownloadRepository
import com.pumaterial.app.domain.repository.DownloadedMaterialEntityWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class DownloadRepositoryImpl(
    private val fileDownloader: FileDownloader,
    private val downloadedDao: DownloadedMaterialDao
) : DownloadRepository {

    override fun observeDownloadedMaterials(): Flow<List<DownloadedMaterialEntityWrapper>> {
        return downloadedDao.getAllDownloadedMaterials().map { list ->
            list.map { entity ->
                DownloadedMaterialEntityWrapper(
                    materialId = entity.materialId,
                    subjectId = entity.subjectId,
                    moduleId = entity.moduleId,
                    title = entity.title,
                    fileName = entity.fileName,
                    fileType = entity.fileType,
                    localFilePath = entity.localFilePath,
                    localVersion = entity.localVersion,
                    cloudVersion = entity.cloudVersion,
                    fileSize = entity.fileSize,
                    downloadedAt = entity.downloadedAt
                )
            }
        }
    }

    override fun observeDownloadedMaterialIds(): Flow<List<String>> {
        return downloadedDao.getAllDownloadedMaterialIds()
    }

    override fun isMaterialDownloaded(materialId: String): Flow<Boolean> {
        return downloadedDao.observeDownloadedMaterialById(materialId).map { it != null }
    }

    override suspend fun getLocalFileForMaterial(materialId: String, fileName: String): File? {
        val file = fileDownloader.getLocalMaterialFile(materialId, fileName)
        return if (file.exists()) file else null
    }

    override suspend fun downloadOrUpdateMaterial(
        material: Material,
        onProgress: (Float) -> Unit
    ): Resource<File> {
        if (material.downloadUrl.isBlank()) {
            return Resource.Error("Download URL is missing.")
        }

        val result = fileDownloader.downloadMaterialSafely(
            materialId = material.id,
            fileName = material.fileName,
            downloadUrl = material.downloadUrl,
            expectedSizeBytes = material.fileSize,
            onProgress = onProgress
        )

        return result.fold(
            onSuccess = { targetFile ->
                val entity = DownloadedMaterialEntity(
                    materialId = material.id,
                    subjectId = material.subjectId,
                    moduleId = material.moduleId,
                    title = material.title,
                    fileName = material.fileName,
                    fileType = material.fileType,
                    localFilePath = targetFile.absolutePath,
                    localVersion = material.version,
                    cloudVersion = material.version,
                    fileSize = targetFile.length(),
                    downloadedAt = System.currentTimeMillis()
                )
                downloadedDao.insertDownloadedMaterial(entity)
                Resource.Success(targetFile)
            },
            onFailure = { error ->
                Resource.Error(error.localizedMessage ?: "Download failed.", error)
            }
        )
    }

    override suspend fun deleteDownloadedMaterial(
        materialId: String,
        fileName: String
    ): Resource<Unit> {
        return try {
            fileDownloader.deleteLocalMaterialFile(materialId, fileName)
            downloadedDao.deleteDownloadedMaterial(materialId)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to delete local file.", e)
        }
    }
}
