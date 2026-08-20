package com.pumaterial.app.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pumaterial.app.data.local.entity.CachedModuleEntity
import com.pumaterial.app.data.local.entity.CachedSubjectEntity
import com.pumaterial.app.data.local.entity.DownloadedMaterialEntity
import com.pumaterial.app.data.local.entity.PersonalFolderEntity
import com.pumaterial.app.data.local.entity.PersonalFolderItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedMaterialDao {
    @Query("SELECT * FROM downloaded_materials ORDER BY downloadedAt DESC")
    fun getAllDownloadedMaterials(): Flow<List<DownloadedMaterialEntity>>

    @Query("SELECT * FROM downloaded_materials WHERE materialId = :materialId LIMIT 1")
    suspend fun getDownloadedMaterialById(materialId: String): DownloadedMaterialEntity?

    @Query("SELECT * FROM downloaded_materials WHERE materialId = :materialId LIMIT 1")
    fun observeDownloadedMaterialById(materialId: String): Flow<DownloadedMaterialEntity?>

    @Query("SELECT materialId FROM downloaded_materials")
    fun getAllDownloadedMaterialIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedMaterial(entity: DownloadedMaterialEntity)

    @Query("DELETE FROM downloaded_materials WHERE materialId = :materialId")
    suspend fun deleteDownloadedMaterial(materialId: String)

    @Query("SELECT COUNT(*) FROM downloaded_materials")
    fun getDownloadCount(): Flow<Int>
}

@Dao
interface PersonalFolderDao {
    @Query("SELECT * FROM personal_folders ORDER BY createdAt DESC")
    fun getAllFolders(): Flow<List<PersonalFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: PersonalFolderEntity)

    @Query("DELETE FROM personal_folders WHERE folderId = :folderId")
    suspend fun deleteFolder(folderId: String)

    @Query("UPDATE personal_folders SET folderName = :newName, colorHex = :colorHex WHERE folderId = :folderId")
    suspend fun updateFolder(folderId: String, newName: String, colorHex: String)

    @Query("SELECT * FROM personal_folder_items WHERE folderId = :folderId ORDER BY addedAt DESC")
    fun getItemsInFolder(folderId: String): Flow<List<PersonalFolderItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemToFolder(item: PersonalFolderItemEntity)

    @Query("DELETE FROM personal_folder_items WHERE folderId = :folderId AND materialId = :materialId")
    suspend fun removeItemFromFolder(folderId: String, materialId: String)

    @Query("SELECT COUNT(*) FROM personal_folder_items WHERE folderId = :folderId")
    fun getItemCountForFolder(folderId: String): Flow<Int>
}

@Dao
interface CachedHierarchyDao {
    @Query("SELECT * FROM cached_subjects WHERE isActive = 1 ORDER BY `order` ASC")
    fun getAllCachedSubjects(): Flow<List<CachedSubjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<CachedSubjectEntity>)

    @Query("DELETE FROM cached_subjects")
    suspend fun clearSubjects()

    @Query("SELECT * FROM cached_modules WHERE subjectId = :subjectId AND isActive = 1 ORDER BY `order` ASC")
    fun getCachedModulesForSubject(subjectId: String): Flow<List<CachedModuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModules(modules: List<CachedModuleEntity>)

    @Query("DELETE FROM cached_modules WHERE subjectId = :subjectId")
    suspend fun clearModulesForSubject(subjectId: String)
}
