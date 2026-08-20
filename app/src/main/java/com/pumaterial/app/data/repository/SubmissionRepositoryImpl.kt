package com.pumaterial.app.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.pumaterial.app.core.common.AppError
import com.pumaterial.app.core.common.Constants
import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.core.file.FileHelper
import com.pumaterial.app.core.file.MimeTypes
import com.pumaterial.app.data.local.datastore.UserSessionManager
import com.pumaterial.app.data.remote.dto.SubmissionDto
import com.pumaterial.app.domain.model.Submission
import com.pumaterial.app.domain.repository.SubmissionRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SubmissionRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val sessionManager: UserSessionManager
) : SubmissionRepository {

    override fun observeMySubmissions(): Flow<List<Submission>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection(Constants.COLL_SUBMISSIONS)
            .whereEqualTo("submittedBy", uid)
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

    override suspend fun submitStudyMaterial(
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
        onProgress: (Float) -> Unit
    ): Resource<Submission> {
        val user = sessionManager.userSessionFlow.firstOrNull()
            ?: return Resource.Error("Please complete registration before submitting material.")

        val ext = fileType.lowercase()
        if (ext !in Constants.SUPPORTED_EXTENSIONS) {
            return Resource.Error("Unsupported file type .$ext. Only PDF, PPT, PPTX, DOC, and DOCX are allowed.")
        }

        if (fileSize > Constants.DEFAULT_MAX_UPLOAD_SIZE_BYTES) {
            return Resource.Error("File size exceeds the 50 MB limit.")
        }

        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            return Resource.Error("Please provide a title for the study material.")
        }

        return try {
            val submissionId = UUID.randomUUID().toString()
            val sanitizedName = FileHelper.sanitizeFileName(fileName)
            val storagePath = "submissions/pending/${user.uid}/$submissionId/$sanitizedName"
            val storageRef = storage.reference.child(storagePath)

            // 1. Upload to isolated pending storage path with progress
            val mimeType = MimeTypes.getMimeTypeForExtension(ext)
            val metadata = StorageMetadata.Builder()
                .setContentType(mimeType)
                .setCustomMetadata("uploadedBy", user.uid)
                .setCustomMetadata("enrollment", user.enrollmentNumber)
                .build()

            val uploadTask = storageRef.putFile(fileUri, metadata)
            uploadTask.addOnProgressListener { taskSnapshot ->
                if (taskSnapshot.totalByteCount > 0) {
                    val progress = (taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount.toFloat()).coerceIn(0f, 1f)
                    onProgress(progress)
                }
            }
            uploadTask.await()

            // 2. Create Firestore submission document
            val submissionData = mapOf(
                "id" to submissionId,
                "submittedBy" to user.uid,
                "submitterName" to user.name,
                "submitterEnrollment" to user.enrollmentNumber,
                "submitterSection" to user.displaySection,
                "fileName" to sanitizedName,
                "fileType" to ext,
                "fileSize" to fileSize,
                "title" to trimmedTitle,
                "description" to description.trim(),
                "subjectId" to subjectId,
                "subjectName" to subjectName,
                "moduleId" to moduleId,
                "moduleName" to moduleName,
                "storagePath" to storagePath,
                "status" to "pending",
                "submittedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection(Constants.COLL_SUBMISSIONS).document(submissionId)
                .set(submissionData)
                .await()

            // 3. Increment student's submission count
            try {
                firestore.collection(Constants.COLL_USERS).document(user.uid)
                    .update("submissionCount", FieldValue.increment(1))
                    .await()
            } catch (_: Exception) {}

            val created = Submission(
                id = submissionId,
                submittedBy = user.uid,
                submitterName = user.name,
                submitterEnrollment = user.enrollmentNumber,
                submitterSection = user.displaySection,
                fileName = sanitizedName,
                fileType = ext,
                fileSize = fileSize,
                title = trimmedTitle,
                description = description.trim(),
                subjectId = subjectId,
                subjectName = subjectName,
                moduleId = moduleId,
                moduleName = moduleName,
                storagePath = storagePath,
                status = "pending",
                submittedAt = System.currentTimeMillis()
            )

            Resource.Success(created)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to submit material. Please try again.", e)
        }
    }

    override suspend fun deleteMyPendingSubmission(submissionId: String): Resource<Unit> {
        val uid = auth.currentUser?.uid ?: return Resource.Error("Not authenticated")
        return try {
            val doc = firestore.collection(Constants.COLL_SUBMISSIONS).document(submissionId).get().await()
            val dto = doc.toObject(SubmissionDto::class.java)

            if (dto != null && dto.submittedBy == uid && dto.status == "pending") {
                if (dto.storagePath.isNotBlank()) {
                    try {
                        storage.reference.child(dto.storagePath).delete().await()
                    } catch (_: Exception) {}
                }
                firestore.collection(Constants.COLL_SUBMISSIONS).document(submissionId).delete().await()
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to delete submission.", e)
        }
    }
}
