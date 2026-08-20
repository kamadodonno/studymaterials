package com.pumaterial.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pumaterial.app.core.common.Constants
import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.data.remote.dto.AnnouncementDto
import com.pumaterial.app.domain.model.Announcement
import com.pumaterial.app.domain.repository.AnnouncementRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AnnouncementRepositoryImpl(
    private val firestore: FirebaseFirestore
) : AnnouncementRepository {

    override fun observeAnnouncements(userSection: String): Flow<List<Announcement>> = callbackFlow {
        val listener = firestore.collection(Constants.COLL_ANNOUNCEMENTS)
            .whereEqualTo("isActive", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val all = snapshot?.documents?.mapNotNull { it.toObject(AnnouncementDto::class.java)?.toDomain(it.id) } ?: emptyList()
                // Filter by targetVisibility == "all" OR targetSection == userSection
                val filtered = all.filter { ann ->
                    ann.targetVisibility == "all" || ann.targetSection.isNullOrBlank() || ann.targetSection == userSection
                }
                trySend(filtered)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun createAnnouncement(
        title: String,
        message: String,
        targetVisibility: String,
        targetSection: String?,
        priority: String
    ): Resource<Announcement> {
        val trimmedTitle = title.trim()
        val trimmedMessage = message.trim()

        if (trimmedTitle.isBlank() || trimmedMessage.isBlank()) {
            return Resource.Error("Title and message cannot be empty.")
        }

        return try {
            val announcementId = UUID.randomUUID().toString()
            val data = mapOf(
                "id" to announcementId,
                "title" to trimmedTitle,
                "message" to trimmedMessage,
                "targetVisibility" to targetVisibility,
                "targetSection" to targetSection,
                "priority" to priority,
                "isActive" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "authorName" to "Administrator"
            )

            firestore.collection(Constants.COLL_ANNOUNCEMENTS).document(announcementId)
                .set(data)
                .await()

            val created = Announcement(
                id = announcementId,
                title = trimmedTitle,
                message = trimmedMessage,
                targetVisibility = targetVisibility,
                targetSection = targetSection,
                priority = priority,
                isActive = true,
                createdAt = System.currentTimeMillis(),
                authorName = "Administrator"
            )
            Resource.Success(created)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to post announcement.", e)
        }
    }

    override suspend fun deleteAnnouncement(announcementId: String): Resource<Unit> {
        return try {
            firestore.collection(Constants.COLL_ANNOUNCEMENTS).document(announcementId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to delete announcement.", e)
        }
    }
}
