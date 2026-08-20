package com.pumaterial.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.pumaterial.app.core.common.Constants
import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.data.local.database.CachedHierarchyDao
import com.pumaterial.app.data.local.database.DownloadedMaterialDao
import com.pumaterial.app.data.remote.dto.*
import com.pumaterial.app.domain.model.*
import com.pumaterial.app.domain.repository.MaterialRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

class MaterialRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val downloadedDao: DownloadedMaterialDao,
    private val cachedDao: CachedHierarchyDao
) : MaterialRepository {

    companion object {
        private const val TAG = "MaterialRepository"
    }

    override fun observeDepartments(): Flow<List<Department>> = callbackFlow {
        val listener = firestore.collection(Constants.COLL_DEPARTMENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeDepartments error", error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toObject(DepartmentDto::class.java)?.toDomain(it.id) }
                    ?.filter { it.isActive }
                    ?.sortedBy { it.order }
                    ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    override fun observeAcademicYears(): Flow<List<AcademicYear>> = callbackFlow {
        val listener = firestore.collection(Constants.COLL_ACADEMIC_YEARS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeAcademicYears error", error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toObject(AcademicYearDto::class.java)?.toDomain(it.id) }
                    ?.filter { it.isActive }
                    ?.sortedBy { it.order }
                    ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    override fun observeSections(): Flow<List<Section>> = callbackFlow {
        val listener = firestore.collection(Constants.COLL_SECTIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeSections error", error)
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) {
                    val defaults = Constants.DEFAULT_SECTION_NAMES.mapIndexed { idx, name ->
                        Section(id = "sec_$idx", name = name, order = idx + 1, isActive = true, isDefault = true)
                    }
                    trySend(defaults)
                    return@addSnapshotListener
                }
                val items = snapshot.documents.mapNotNull { it.toObject(SectionDto::class.java)?.toDomain(it.id) }
                    .sortedBy { it.order }
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    override fun observeSubjects(departmentId: String?, yearId: String?): Flow<List<Subject>> = callbackFlow {
        val listener = firestore.collection(Constants.COLL_SUBJECTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeSubjects error", error)
                    return@addSnapshotListener
                }
                val all = snapshot?.documents?.mapNotNull { it.toObject(SubjectDto::class.java)?.toDomain(it.id) } ?: emptyList()
                val filtered = all
                    .filter { it.isActive }
                    .filter { departmentId.isNullOrBlank() || it.departmentId == departmentId }
                    .filter { yearId.isNullOrBlank() || it.academicYearId == yearId }
                    .sortedBy { it.order }
                trySend(filtered)
            }
        awaitClose { listener.remove() }
    }

    override fun observeModules(subjectId: String): Flow<List<Module>> = callbackFlow {
        val listener = firestore.collection(Constants.COLL_MODULES)
            .whereEqualTo("subjectId", subjectId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeModules error", error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toObject(ModuleDto::class.java)?.toDomain(it.id) }
                    ?.filter { it.isActive }
                    ?.sortedBy { it.order }
                    ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    override fun observeMaterials(moduleId: String): Flow<List<Material>> {
        val cloudFlow = callbackFlow<List<Material>> {
            val listener = firestore.collection(Constants.COLL_MATERIALS)
                .whereEqualTo("moduleId", moduleId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "observeMaterials error", error)
                        return@addSnapshotListener
                    }
                    val items = snapshot?.documents?.mapNotNull { it.toObject(MaterialDto::class.java)?.toDomain(it.id) }
                        ?.filter { it.isActive }
                        ?.sortedByDescending { it.uploadedAt }
                        ?: emptyList()
                    trySend(items)
                }
            awaitClose { listener.remove() }
        }

        // Combine with local Room downloads to enrich with isDownloaded, localFilePath, and version status
        return combine(cloudFlow, downloadedDao.getAllDownloadedMaterials()) { cloudList, localList ->
            val localMap = localList.associateBy { it.materialId }
            cloudList.map { mat ->
                val localEntity = localMap[mat.id]
                if (localEntity != null) {
                    mat.copy(
                        isDownloaded = true,
                        localFilePath = localEntity.localFilePath,
                        localVersion = localEntity.localVersion
                    )
                } else {
                    mat.copy(isDownloaded = false, localFilePath = null, localVersion = 0)
                }
            }
        }
    }

    override fun observeRecentlyAdded(userSection: String, limit: Int): Flow<List<Material>> {
        val cloudFlow = callbackFlow<List<Material>> {
            val listener = firestore.collection(Constants.COLL_MATERIALS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "observeRecentlyAdded error", error)
                        return@addSnapshotListener
                    }
                    val items = snapshot?.documents?.mapNotNull { it.toObject(MaterialDto::class.java)?.toDomain(it.id) }
                        ?.filter { it.isActive }
                        ?.sortedByDescending { it.uploadedAt }
                        ?.take(limit)
                        ?: emptyList()
                    trySend(items)
                }
            awaitClose { listener.remove() }
        }

        return combine(cloudFlow, downloadedDao.getAllDownloadedMaterials()) { cloudList, localList ->
            val localMap = localList.associateBy { it.materialId }
            cloudList.map { mat ->
                val local = localMap[mat.id]
                if (local != null) {
                    mat.copy(isDownloaded = true, localFilePath = local.localFilePath, localVersion = local.localVersion)
                } else {
                    mat
                }
            }
        }
    }

    override fun observeRecentlyUpdated(userSection: String, limit: Int): Flow<List<Material>> {
        val cloudFlow = callbackFlow<List<Material>> {
            val listener = firestore.collection(Constants.COLL_MATERIALS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "observeRecentlyUpdated error", error)
                        return@addSnapshotListener
                    }
                    val items = snapshot?.documents?.mapNotNull { it.toObject(MaterialDto::class.java)?.toDomain(it.id) }
                        ?.filter { it.isActive }
                        ?.sortedByDescending { it.updatedAt }
                        ?.take(limit)
                        ?: emptyList()
                    trySend(items)
                }
            awaitClose { listener.remove() }
        }

        return combine(cloudFlow, downloadedDao.getAllDownloadedMaterials()) { cloudList, localList ->
            val localMap = localList.associateBy { it.materialId }
            cloudList.map { mat ->
                val local = localMap[mat.id]
                if (local != null) {
                    mat.copy(isDownloaded = true, localFilePath = local.localFilePath, localVersion = local.localVersion)
                } else {
                    mat
                }
            }
        }
    }

    override suspend fun searchMaterials(
        query: String,
        subjectId: String?,
        fileType: String?
    ): Resource<List<Material>> {
        return try {
            val snapshot = firestore.collection(Constants.COLL_MATERIALS).get().await()
            val all = snapshot.documents.mapNotNull { it.toObject(MaterialDto::class.java)?.toDomain(it.id) }
                .filter { it.isActive }
                .filter { subjectId.isNullOrBlank() || it.subjectId == subjectId }
                .filter { fileType.isNullOrBlank() || it.fileType.equals(fileType, ignoreCase = true) }

            val filtered = if (query.isBlank()) {
                all
            } else {
                val q = query.trim().lowercase()
                all.filter { mat ->
                    mat.title.lowercase().contains(q) ||
                    mat.description.lowercase().contains(q) ||
                    mat.fileName.lowercase().contains(q) ||
                    mat.subjectName.lowercase().contains(q) ||
                    mat.moduleName.lowercase().contains(q)
                }
            }

            Resource.Success(filtered)
        } catch (e: Exception) {
            Log.e(TAG, "searchMaterials error", e)
            Resource.Error("Search failed. Please check your network.", e)
        }
    }

    override suspend fun getMaterialById(materialId: String): Resource<Material> {
        return try {
            val doc = firestore.collection(Constants.COLL_MATERIALS).document(materialId).get().await()
            val mat = doc.toObject(MaterialDto::class.java)?.toDomain(doc.id)
                ?: return Resource.Error("Study material not found.")
            val local = downloadedDao.getDownloadedMaterialById(materialId)
            val enriched = if (local != null) {
                mat.copy(isDownloaded = true, localFilePath = local.localFilePath, localVersion = local.localVersion)
            } else {
                mat
            }
            Resource.Success(enriched)
        } catch (e: Exception) {
            Log.e(TAG, "getMaterialById error", e)
            Resource.Error("Failed to load material.", e)
        }
    }
}
