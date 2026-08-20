package com.pumaterial.app.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.pumaterial.app.core.common.Constants
import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.core.file.FileHelper
import com.pumaterial.app.core.file.MimeTypes
import com.pumaterial.app.data.remote.dto.*
import com.pumaterial.app.domain.model.*
import com.pumaterial.app.domain.repository.AdminDashboardStats
import com.pumaterial.app.domain.repository.AdminRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AdminRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : AdminRepository {

    companion object {
        private const val TAG = "AdminRepository"
    }

    override suspend fun getDashboardStats(): Resource<AdminDashboardStats> {
        return try {
            val usersSnap = firestore.collection(Constants.COLL_USERS).get().await()
            val totalUsers = usersSnap.size()

            val usersBySection = mutableMapOf<String, Int>()
            usersSnap.documents.forEach { doc ->
                val sec = doc.getString("section") ?: "Unknown"
                val other = doc.getString("otherSection")
                val displaySec = if (sec == "Other" && !other.isNullOrBlank()) "Other ($other)" else sec
                usersBySection[displaySec] = (usersBySection[displaySec] ?: 0) + 1
            }

            val pendingSnap = firestore.collection(Constants.COLL_SUBMISSIONS)
                .whereEqualTo("status", "pending")
                .get().await()
            val pendingCount = pendingSnap.size()

            val materialsSnap = firestore.collection(Constants.COLL_MATERIALS)
                .whereEqualTo("isActive", true)
                .get().await()
            val materialsCount = materialsSnap.size()

            val subjectsSnap = firestore.collection(Constants.COLL_SUBJECTS)
                .whereEqualTo("isActive", true)
                .get().await()
            val subjectsCount = subjectsSnap.size()

            Resource.Success(
                AdminDashboardStats(
                    totalUsers = totalUsers,
                    usersBySection = usersBySection,
                    pendingSubmissionsCount = pendingCount,
                    totalMaterialsCount = materialsCount,
                    totalSubjectsCount = subjectsCount
                )
            )
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to fetch dashboard stats.", e)
        }
    }

    override fun observeUsers(): Flow<List<User>> = callbackFlow {
        val listener = firestore.collection(Constants.COLL_USERS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toObject(UserDto::class.java)?.toDomain(it.id) } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    override fun observePendingSubmissions(): Flow<List<Submission>> = callbackFlow {
        val listener = firestore.collection(Constants.COLL_SUBMISSIONS)
            .whereEqualTo("status", "pending")
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toObject(SubmissionDto::class.java)?.toDomain(it.id) } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun approveSubmissionSafely(
        submission: Submission,
        editedTitle: String,
        editedDescription: String,
        subjectId: String,
        subjectName: String,
        moduleId: String,
        moduleName: String,
        visibility: String,
        section: String?
    ): Resource<Material> {
        val adminUid = auth.currentUser?.uid ?: return Resource.Error("Admin not authenticated")

        return try {
            val materialId = UUID.randomUUID().toString()
            val sanitizedName = FileHelper.sanitizeFileName(submission.fileName)
            val publishedStoragePath = "materials/$subjectId/$moduleId/${materialId}_$sanitizedName"

            val sourceRef = storage.reference.child(submission.storagePath)
            val destRef = storage.reference.child(publishedStoragePath)

            // Step 1: Verify source submission file exists in pending quarantine
            val sourceMetadata = sourceRef.metadata.await()
            val fileSize = sourceMetadata.sizeBytes

            // Step 2: Copy file to published library (download bytes and re-upload to target)
            val maxBytes = 55L * 1024 * 1024 // 55 MB max memory buffer
            val bytes = sourceRef.getBytes(maxBytes).await()

            val mimeType = MimeTypes.getMimeTypeForExtension(submission.fileType)
            val newMetadata = StorageMetadata.Builder()
                .setContentType(mimeType)
                .setCustomMetadata("publishedBy", adminUid)
                .setCustomMetadata("submittedBy", submission.submittedBy)
                .build()

            destRef.putBytes(bytes, newMetadata).await()

            // Step 3: Verify published copy exists & fetch download URL
            val downloadUrl = destRef.downloadUrl.await().toString()

            // Step 4: Create published Firestore material record
            val materialData = mapOf(
                "id" to materialId,
                "subjectId" to subjectId,
                "subjectName" to subjectName,
                "moduleId" to moduleId,
                "moduleName" to moduleName,
                "title" to editedTitle.trim().ifBlank { submission.title },
                "description" to editedDescription.trim(),
                "fileName" to sanitizedName,
                "fileType" to submission.fileType,
                "fileSize" to fileSize,
                "storagePath" to publishedStoragePath,
                "downloadUrl" to downloadUrl,
                "version" to 1,
                "isActive" to true,
                "visibility" to visibility,
                "section" to if (visibility == "section") section else null,
                "uploadedBy" to submission.submittedBy,
                "uploadedByName" to submission.submitterName,
                "uploadedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection(Constants.COLL_MATERIALS).document(materialId)
                .set(materialData)
                .await()

            // Increment module / subject material counts
            try {
                firestore.collection(Constants.COLL_MODULES).document(moduleId)
                    .update("materialCount", FieldValue.increment(1)).await()
                firestore.collection(Constants.COLL_SUBJECTS).document(subjectId)
                    .update("materialCount", FieldValue.increment(1)).await()
            } catch (_: Exception) {}

            // Step 5: Mark submission as approved
            firestore.collection(Constants.COLL_SUBMISSIONS).document(submission.id)
                .update(
                    mapOf(
                        "status" to "approved",
                        "reviewedAt" to FieldValue.serverTimestamp(),
                        "reviewedBy" to adminUid
                    )
                ).await()

            // Step 6: Delete original pending quarantine file
            try {
                sourceRef.delete().await()
            } catch (_: Exception) {}

            val publishedMaterial = Material(
                id = materialId,
                subjectId = subjectId,
                subjectName = subjectName,
                moduleId = moduleId,
                moduleName = moduleName,
                title = editedTitle.trim().ifBlank { submission.title },
                description = editedDescription.trim(),
                fileName = sanitizedName,
                fileType = submission.fileType,
                fileSize = fileSize,
                storagePath = publishedStoragePath,
                downloadUrl = downloadUrl,
                version = 1,
                isActive = true,
                visibility = visibility,
                section = if (visibility == "section") section else null,
                uploadedBy = submission.submittedBy,
                uploadedByName = submission.submitterName,
                uploadedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            Resource.Success(publishedMaterial)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to publish submission.", e)
        }
    }

    override suspend fun rejectSubmission(submissionId: String, reason: String): Resource<Unit> {
        val adminUid = auth.currentUser?.uid ?: return Resource.Error("Admin not authenticated")

        return try {
            val doc = firestore.collection(Constants.COLL_SUBMISSIONS).document(submissionId).get().await()
            val dto = doc.toObject(SubmissionDto::class.java)

            // 1. Delete storage file to free space
            if (dto?.storagePath != null && dto.storagePath.isNotBlank()) {
                try {
                    storage.reference.child(dto.storagePath).delete().await()
                } catch (_: Exception) {}
            }

            // 2. Update Firestore record
            firestore.collection(Constants.COLL_SUBMISSIONS).document(submissionId)
                .update(
                    mapOf(
                        "status" to "rejected",
                        "rejectionReason" to reason.trim().ifBlank { "Not relevant" },
                        "reviewedAt" to FieldValue.serverTimestamp(),
                        "reviewedBy" to adminUid
                    )
                ).await()

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to reject submission.", e)
        }
    }

    override suspend fun createDepartment(
        name: String,
        code: String,
        description: String,
        order: Int
    ): Resource<Department> {
        return try {
            val id = UUID.randomUUID().toString()
            val data = mapOf(
                "id" to id,
                "name" to name.trim(),
                "code" to code.trim().uppercase(),
                "description" to description.trim(),
                "order" to order,
                "isActive" to true,
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection(Constants.COLL_DEPARTMENTS).document(id).set(data).await()
            Resource.Success(
                Department(id = id, name = name.trim(), code = code.trim().uppercase(), description = description.trim(), order = order)
            )
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to create department.", e)
        }
    }

    override suspend fun createAcademicYear(
        name: String,
        code: String,
        order: Int
    ): Resource<AcademicYear> {
        return try {
            val id = UUID.randomUUID().toString()
            val data = mapOf(
                "id" to id,
                "name" to name.trim(),
                "code" to code.trim().uppercase(),
                "order" to order,
                "isActive" to true
            )
            firestore.collection(Constants.COLL_ACADEMIC_YEARS).document(id).set(data).await()
            Resource.Success(AcademicYear(id = id, name = name.trim(), code = code.trim().uppercase(), order = order))
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to create academic year.", e)
        }
    }

    override suspend fun createSection(name: String, order: Int): Resource<Section> {
        return try {
            val id = UUID.randomUUID().toString()
            val data = mapOf(
                "id" to id,
                "name" to name.trim(),
                "order" to order,
                "isActive" to true,
                "isDefault" to false
            )
            firestore.collection(Constants.COLL_SECTIONS).document(id).set(data).await()
            Resource.Success(Section(id = id, name = name.trim(), order = order, isActive = true, isDefault = false))
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to create section.", e)
        }
    }

    override suspend fun updateSectionActiveState(sectionId: String, isActive: Boolean): Resource<Unit> {
        return try {
            firestore.collection(Constants.COLL_SECTIONS).document(sectionId)
                .update("isActive", isActive)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to update section.", e)
        }
    }

    override suspend fun createSubject(
        name: String,
        code: String,
        departmentId: String,
        academicYearId: String,
        icon: String,
        order: Int
    ): Resource<Subject> {
        return try {
            val id = UUID.randomUUID().toString()
            val data = mapOf(
                "id" to id,
                "name" to name.trim(),
                "code" to code.trim().uppercase(),
                "departmentId" to departmentId,
                "academicYearId" to academicYearId,
                "icon" to icon.ifBlank { "book" },
                "order" to order,
                "isActive" to true,
                "moduleCount" to 0,
                "materialCount" to 0,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection(Constants.COLL_SUBJECTS).document(id).set(data).await()
            Resource.Success(
                Subject(
                    id = id,
                    name = name.trim(),
                    code = code.trim().uppercase(),
                    departmentId = departmentId,
                    academicYearId = academicYearId,
                    icon = icon.ifBlank { "book" },
                    order = order
                )
            )
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to create subject.", e)
        }
    }

    override suspend fun createModule(
        subjectId: String,
        name: String,
        description: String,
        order: Int
    ): Resource<Module> {
        return try {
            val id = UUID.randomUUID().toString()
            val data = mapOf(
                "id" to id,
                "subjectId" to subjectId,
                "name" to name.trim(),
                "description" to description.trim(),
                "order" to order,
                "isActive" to true,
                "materialCount" to 0
            )
            firestore.collection(Constants.COLL_MODULES).document(id).set(data).await()
            // Increment subject moduleCount
            try {
                firestore.collection(Constants.COLL_SUBJECTS).document(subjectId)
                    .update("moduleCount", FieldValue.increment(1)).await()
            } catch (_: Exception) {}

            Resource.Success(
                Module(id = id, subjectId = subjectId, name = name.trim(), description = description.trim(), order = order)
            )
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to create module.", e)
        }
    }

    override suspend fun publishMaterial(
        fileUri: Uri,
        fileName: String,
        fileType: String,
        fileSize: Long,
        title: String,
        description: String,
        subjectId: String,
        subjectName: String,
        moduleId: String,
        moduleName: String,
        visibility: String,
        section: String?,
        onProgress: (Float) -> Unit
    ): Resource<Material> {
        val adminUid = auth.currentUser?.uid ?: return Resource.Error("Admin not authenticated")

        return try {
            val materialId = UUID.randomUUID().toString()
            val sanitizedName = FileHelper.sanitizeFileName(fileName)
            val storagePath = "materials/$subjectId/$moduleId/${materialId}_$sanitizedName"
            val storageRef = storage.reference.child(storagePath)

            val mimeType = MimeTypes.getMimeTypeForExtension(fileType)
            val metadata = StorageMetadata.Builder()
                .setContentType(mimeType)
                .setCustomMetadata("uploadedBy", adminUid)
                .build()

            val uploadTask = storageRef.putFile(fileUri, metadata)
            uploadTask.addOnProgressListener { taskSnapshot ->
                if (taskSnapshot.totalByteCount > 0) {
                    val progress = (taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount.toFloat()).coerceIn(0f, 1f)
                    onProgress(progress)
                }
            }
            uploadTask.await()

            val downloadUrl = storageRef.downloadUrl.await().toString()

            val data = mapOf(
                "id" to materialId,
                "subjectId" to subjectId,
                "subjectName" to subjectName,
                "moduleId" to moduleId,
                "moduleName" to moduleName,
                "title" to title.trim(),
                "description" to description.trim(),
                "fileName" to sanitizedName,
                "fileType" to fileType.lowercase(),
                "fileSize" to fileSize,
                "storagePath" to storagePath,
                "downloadUrl" to downloadUrl,
                "version" to 1,
                "isActive" to true,
                "visibility" to visibility,
                "section" to if (visibility == "section") section else null,
                "uploadedBy" to adminUid,
                "uploadedByName" to "Administrator",
                "uploadedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection(Constants.COLL_MATERIALS).document(materialId).set(data).await()

            // Increment counts
            try {
                firestore.collection(Constants.COLL_MODULES).document(moduleId)
                    .update("materialCount", FieldValue.increment(1)).await()
                firestore.collection(Constants.COLL_SUBJECTS).document(subjectId)
                    .update("materialCount", FieldValue.increment(1)).await()
            } catch (_: Exception) {}

            val mat = Material(
                id = materialId,
                subjectId = subjectId,
                subjectName = subjectName,
                moduleId = moduleId,
                moduleName = moduleName,
                title = title.trim(),
                description = description.trim(),
                fileName = sanitizedName,
                fileType = fileType.lowercase(),
                fileSize = fileSize,
                storagePath = storagePath,
                downloadUrl = downloadUrl,
                version = 1,
                isActive = true,
                visibility = visibility,
                section = if (visibility == "section") section else null,
                uploadedBy = adminUid,
                uploadedByName = "Administrator"
            )

            Resource.Success(mat)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to upload material.", e)
        }
    }

    override suspend fun publishMaterialLink(
        title: String,
        description: String,
        url: String,
        fileType: String,
        subjectId: String,
        subjectName: String,
        moduleId: String,
        moduleName: String,
        visibility: String,
        section: String?
    ): Resource<Material> {
        val adminUid = auth.currentUser?.uid ?: return Resource.Error("Admin not authenticated")

        return try {
            val materialId = UUID.randomUUID().toString()
            val cleanUrl = FileHelper.convertToDirectDownloadUrl(url)
            val cleanFileType = fileType.lowercase().ifBlank { "pdf" }
            val cleanFileName = "${FileHelper.sanitizeFileName(title.trim())}.$cleanFileType"

            val data = mapOf(
                "id" to materialId,
                "subjectId" to subjectId,
                "subjectName" to subjectName,
                "moduleId" to moduleId,
                "moduleName" to moduleName,
                "title" to title.trim(),
                "description" to description.trim(),
                "fileName" to cleanFileName,
                "fileType" to cleanFileType,
                "fileSize" to 0L,
                "storagePath" to "",
                "downloadUrl" to cleanUrl,
                "version" to 1,
                "isActive" to true,
                "visibility" to visibility,
                "section" to if (visibility == "section") section else null,
                "uploadedBy" to adminUid,
                "uploadedByName" to "Administrator",
                "uploadedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection(Constants.COLL_MATERIALS).document(materialId).set(data).await()

            // Increment counts
            try {
                firestore.collection(Constants.COLL_MODULES).document(moduleId)
                    .update("materialCount", FieldValue.increment(1)).await()
                firestore.collection(Constants.COLL_SUBJECTS).document(subjectId)
                    .update("materialCount", FieldValue.increment(1)).await()
            } catch (_: Exception) {}

            val mat = Material(
                id = materialId,
                subjectId = subjectId,
                subjectName = subjectName,
                moduleId = moduleId,
                moduleName = moduleName,
                title = title.trim(),
                description = description.trim(),
                fileName = cleanFileName,
                fileType = cleanFileType,
                fileSize = 0L,
                storagePath = "",
                downloadUrl = cleanUrl,
                version = 1,
                isActive = true,
                visibility = visibility,
                section = if (visibility == "section") section else null,
                uploadedBy = adminUid,
                uploadedByName = "Administrator"
            )

            Resource.Success(mat)
        } catch (e: Exception) {
            Log.e(TAG, "publishMaterialLink error", e)
            Resource.Error(e.localizedMessage ?: "Failed to save material link.", e)
        }
    }

    override suspend fun replaceMaterial(
        materialId: String,
        fileUri: Uri,
        fileName: String,
        fileType: String,
        fileSize: Long,
        subjectId: String,
        moduleId: String,
        newVersion: Int,
        onProgress: (Float) -> Unit
    ): Resource<Unit> {
        val adminUid = auth.currentUser?.uid ?: return Resource.Error("Admin not authenticated")

        return try {
            val sanitizedName = FileHelper.sanitizeFileName(fileName)
            val newStoragePath = "materials/$subjectId/$moduleId/${materialId}_v${newVersion}_$sanitizedName"
            val storageRef = storage.reference.child(newStoragePath)

            val mimeType = MimeTypes.getMimeTypeForExtension(fileType)
            val metadata = StorageMetadata.Builder()
                .setContentType(mimeType)
                .setCustomMetadata("uploadedBy", adminUid)
                .setCustomMetadata("version", newVersion.toString())
                .build()

            val uploadTask = storageRef.putFile(fileUri, metadata)
            uploadTask.addOnProgressListener { taskSnapshot ->
                if (taskSnapshot.totalByteCount > 0) {
                    val progress = (taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount.toFloat()).coerceIn(0f, 1f)
                    onProgress(progress)
                }
            }
            uploadTask.await()

            val downloadUrl = storageRef.downloadUrl.await().toString()

            firestore.collection(Constants.COLL_MATERIALS).document(materialId)
                .update(
                    mapOf(
                        "fileName" to sanitizedName,
                        "fileType" to fileType.lowercase(),
                        "fileSize" to fileSize,
                        "storagePath" to newStoragePath,
                        "downloadUrl" to downloadUrl,
                        "version" to newVersion,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to replace material.", e)
        }
    }

    override suspend fun deactivateMaterial(materialId: String, isActive: Boolean): Resource<Unit> {
        return try {
            firestore.collection(Constants.COLL_MATERIALS).document(materialId)
                .update("isActive", isActive)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to update material state.", e)
        }
    }

    override suspend fun updateMaterial(
        materialId: String,
        title: String,
        description: String,
        visibility: String,
        section: String?
    ): Resource<Unit> {
        return try {
            val updates = mapOf(
                "title" to title.trim(),
                "description" to description.trim(),
                "visibility" to visibility,
                "section" to if (visibility == "section") section else null,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection(Constants.COLL_MATERIALS).document(materialId).update(updates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateMaterial error", e)
            Resource.Error(e.localizedMessage ?: "Failed to update material.", e)
        }
    }

    override suspend fun updateSubject(
        subjectId: String,
        name: String,
        code: String,
        deptId: String,
        yearId: String
    ): Resource<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "name" to name.trim(),
                "code" to code.trim().uppercase(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (deptId.isNotBlank()) updates["departmentId"] = deptId
            if (yearId.isNotBlank()) updates["academicYearId"] = yearId

            firestore.collection(Constants.COLL_SUBJECTS).document(subjectId).update(updates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateSubject error", e)
            Resource.Error(e.localizedMessage ?: "Failed to update subject.", e)
        }
    }

    override suspend fun updateModule(
        moduleId: String,
        name: String,
        description: String
    ): Resource<Unit> {
        return try {
            val updates = mapOf(
                "name" to name.trim(),
                "description" to description.trim()
            )
            firestore.collection(Constants.COLL_MODULES).document(moduleId).update(updates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateModule error", e)
            Resource.Error(e.localizedMessage ?: "Failed to update module.", e)
        }
    }

    override suspend fun updateDepartment(
        departmentId: String,
        name: String,
        code: String,
        description: String
    ): Resource<Unit> {
        return try {
            val updates = mapOf(
                "name" to name.trim(),
                "code" to code.trim().uppercase(),
                "description" to description.trim()
            )
            firestore.collection(Constants.COLL_DEPARTMENTS).document(departmentId).update(updates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateDepartment error", e)
            Resource.Error(e.localizedMessage ?: "Failed to update department.", e)
        }
    }

    override suspend fun updateAcademicYear(
        yearId: String,
        name: String,
        code: String
    ): Resource<Unit> {
        return try {
            val updates = mapOf(
                "name" to name.trim(),
                "code" to code.trim().uppercase()
            )
            firestore.collection(Constants.COLL_ACADEMIC_YEARS).document(yearId).update(updates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateAcademicYear error", e)
            Resource.Error(e.localizedMessage ?: "Failed to update academic year.", e)
        }
    }

    override suspend fun deleteMaterial(materialId: String, storagePath: String): Resource<Unit> {
        return try {
            val doc = firestore.collection(Constants.COLL_MATERIALS).document(materialId).get().await()
            val moduleId = doc.getString("moduleId")
            val subjectId = doc.getString("subjectId")

            if (storagePath.isNotBlank()) {
                try {
                    storage.reference.child(storagePath).delete().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Storage file deletion skipped or failed: ${e.message}")
                }
            }
            firestore.collection(Constants.COLL_MATERIALS).document(materialId).delete().await()

            // Decrement counts
            if (!moduleId.isNullOrBlank()) {
                try {
                    firestore.collection(Constants.COLL_MODULES).document(moduleId)
                        .update("materialCount", FieldValue.increment(-1)).await()
                } catch (_: Exception) {}
            }
            if (!subjectId.isNullOrBlank()) {
                try {
                    firestore.collection(Constants.COLL_SUBJECTS).document(subjectId)
                        .update("materialCount", FieldValue.increment(-1)).await()
                } catch (_: Exception) {}
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteMaterial error", e)
            Resource.Error(e.localizedMessage ?: "Failed to delete material.", e)
        }
    }

    override suspend fun deleteSubject(subjectId: String): Resource<Unit> {
        return try {
            firestore.collection(Constants.COLL_SUBJECTS).document(subjectId).delete().await()

            // Delete associated modules
            try {
                val modulesSnap = firestore.collection(Constants.COLL_MODULES)
                    .whereEqualTo("subjectId", subjectId).get().await()
                for (doc in modulesSnap.documents) {
                    doc.reference.delete()
                }
            } catch (_: Exception) {}

            // Delete associated materials
            try {
                val materialsSnap = firestore.collection(Constants.COLL_MATERIALS)
                    .whereEqualTo("subjectId", subjectId).get().await()
                for (doc in materialsSnap.documents) {
                    val path = doc.getString("storagePath")
                    if (!path.isNullOrBlank()) {
                        try { storage.reference.child(path).delete().await() } catch (_: Exception) {}
                    }
                    doc.reference.delete()
                }
            } catch (_: Exception) {}

            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteSubject error", e)
            Resource.Error(e.localizedMessage ?: "Failed to delete subject.", e)
        }
    }

    override suspend fun deleteModule(moduleId: String): Resource<Unit> {
        return try {
            val doc = firestore.collection(Constants.COLL_MODULES).document(moduleId).get().await()
            val subjectId = doc.getString("subjectId")
            firestore.collection(Constants.COLL_MODULES).document(moduleId).delete().await()

            if (!subjectId.isNullOrBlank()) {
                try {
                    firestore.collection(Constants.COLL_SUBJECTS).document(subjectId)
                        .update("moduleCount", FieldValue.increment(-1)).await()
                } catch (_: Exception) {}
            }

            // Delete associated materials
            try {
                val materialsSnap = firestore.collection(Constants.COLL_MATERIALS)
                    .whereEqualTo("moduleId", moduleId).get().await()
                for (mDoc in materialsSnap.documents) {
                    val path = mDoc.getString("storagePath")
                    if (!path.isNullOrBlank()) {
                        try { storage.reference.child(path).delete().await() } catch (_: Exception) {}
                    }
                    mDoc.reference.delete()
                }
            } catch (_: Exception) {}

            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteModule error", e)
            Resource.Error(e.localizedMessage ?: "Failed to delete module.", e)
        }
    }

    override suspend fun deleteDepartment(departmentId: String): Resource<Unit> {
        return try {
            firestore.collection(Constants.COLL_DEPARTMENTS).document(departmentId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteDepartment error", e)
            Resource.Error(e.localizedMessage ?: "Failed to delete department.", e)
        }
    }

    override suspend fun deleteAcademicYear(yearId: String): Resource<Unit> {
        return try {
            firestore.collection(Constants.COLL_ACADEMIC_YEARS).document(yearId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteAcademicYear error", e)
            Resource.Error(e.localizedMessage ?: "Failed to delete academic year.", e)
        }
    }

    override suspend fun updateUserRole(
        userId: String,
        newRole: String,
        permissions: List<String>
    ): Resource<Unit> {
        return try {
            firestore.collection(Constants.COLL_USERS).document(userId)
                .update(
                    mapOf(
                        "role" to newRole,
                        "permissions" to permissions
                    )
                ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to update user role.", e)
        }
    }

    override suspend fun deleteUser(userId: String): Resource<Unit> {
        return try {
            firestore.collection(Constants.COLL_USERS).document(userId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to delete user profile.", e)
        }
    }
}
