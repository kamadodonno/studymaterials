package com.pumaterial.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pumaterial.app.core.common.AppError
import com.pumaterial.app.core.common.Constants
import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.data.local.datastore.UserSessionManager
import com.pumaterial.app.data.remote.dto.UserDto
import com.pumaterial.app.domain.model.User
import com.pumaterial.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import java.lang.Exception

class AuthRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sessionManager: UserSessionManager
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepository"
    }

    override val currentUserFlow: Flow<User?> = sessionManager.userSessionFlow

    override suspend fun getCurrentUser(): User? {
        return sessionManager.userSessionFlow.firstOrNull()
    }

    override suspend fun registerStudent(
        name: String,
        enrollmentNumber: String,
        section: String,
        otherSection: String?
    ): Resource<User> {
        val trimmedName = name.trim()
        val rawEnrollment = enrollmentNumber.trim()
        val normalizedEnrollment = rawEnrollment.uppercase()

        if (trimmedName.isBlank()) {
            return Resource.Error("Please enter your full name.")
        }
        if (normalizedEnrollment.isBlank()) {
            return Resource.Error("Please enter your enrollment number.")
        }
        if (section.isBlank()) {
            return Resource.Error("Please select your section.")
        }

        return try {
            // 1. Authenticate anonymously
            var user = auth.currentUser
            if (user == null) {
                val authResult = auth.signInAnonymously().await()
                user = authResult.user ?: throw AppError.AuthError("Failed to initialize anonymous session.")
            }
            val uid = user.uid

            val enrollmentRef = firestore.collection(Constants.COLL_ENROLLMENTS).document(normalizedEnrollment)
            val userRef = firestore.collection(Constants.COLL_USERS).document(uid)

            // 2. Atomic Firestore transaction for enrollment uniqueness
            firestore.runTransaction { transaction ->
                val enrollmentSnap = transaction.get(enrollmentRef)
                if (enrollmentSnap.exists()) {
                    val existingUid = enrollmentSnap.getString("uid")
                    if (existingUid != uid) {
                        throw AppError.DuplicateEnrollmentError("Enrollment number $normalizedEnrollment is already registered by another student.")
                    }
                }

                // Reserve enrollment index
                transaction.set(enrollmentRef, mapOf(
                    "uid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                ))

                // Create/update user document
                val userData = mutableMapOf<String, Any>(
                    "uid" to uid,
                    "name" to trimmedName,
                    "enrollmentNumber" to rawEnrollment,
                    "normalizedEnrollmentNumber" to normalizedEnrollment,
                    "section" to section,
                    "role" to Constants.ROLE_STUDENT,
                    "permissions" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "submissionCount" to 0,
                    "downloadCount" to 0
                )
                if (!otherSection.isNullOrBlank()) {
                    userData["otherSection"] = otherSection.trim()
                }

                transaction.set(userRef, userData)
            }.await()

            val registeredUser = User(
                uid = uid,
                name = trimmedName,
                enrollmentNumber = rawEnrollment,
                normalizedEnrollmentNumber = normalizedEnrollment,
                section = section,
                otherSection = otherSection?.trim(),
                role = Constants.ROLE_STUDENT,
                permissions = emptyList(),
                createdAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis()
            )

            // 3. Cache session locally in DataStore
            sessionManager.saveUserSession(registeredUser)

            Resource.Success(registeredUser)
        } catch (e: AppError.DuplicateEnrollmentError) {
            Resource.Error(e.message ?: "Enrollment number already registered.")
        } catch (e: Exception) {
            Log.e(TAG, "Student registration failed", e)
            val friendlyMsg = if (e.message?.contains("network", ignoreCase = true) == true) {
                "Network connection error. Please check your internet connection and try again."
            } else {
                "Unable to complete registration. Please check your network and try again."
            }
            Resource.Error(friendlyMsg, e)
        }
    }

    override suspend fun signInAdmin(email: String, password: String): Resource<User> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            return Resource.Error("Email and password are required.")
        }

        return try {
            val authResult = auth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val firebaseUser = authResult.user ?: throw AppError.AuthError("Admin authentication failed.")
            val uid = firebaseUser.uid

            val userRef = firestore.collection(Constants.COLL_USERS).document(uid)
            val adminData = mapOf(
                "uid" to uid,
                "name" to (firebaseUser.displayName?.ifBlank { "Administrator" } ?: "Administrator"),
                "enrollmentNumber" to "ADMIN",
                "normalizedEnrollmentNumber" to "ADMIN",
                "section" to "Administration",
                "role" to Constants.ROLE_ADMIN,
                "permissions" to listOf(
                    Constants.PERM_MANAGE_MATERIALS,
                    Constants.PERM_REVIEW_SUBMISSIONS,
                    Constants.PERM_MANAGE_ANNOUNCEMENTS,
                    Constants.PERM_VIEW_USERS,
                    Constants.PERM_MANAGE_SECTIONS
                ),
                "lastActiveAt" to FieldValue.serverTimestamp()
            )
            try {
                userRef.set(adminData, com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w(TAG, "Could not merge admin profile in users collection", e)
            }

            val adminUser = User(
                uid = uid,
                name = firebaseUser.displayName?.ifBlank { "Administrator" } ?: "Administrator",
                enrollmentNumber = "ADMIN",
                normalizedEnrollmentNumber = "ADMIN",
                section = "Administration",
                role = Constants.ROLE_ADMIN,
                permissions = listOf(
                    Constants.PERM_MANAGE_MATERIALS,
                    Constants.PERM_REVIEW_SUBMISSIONS,
                    Constants.PERM_MANAGE_ANNOUNCEMENTS,
                    Constants.PERM_VIEW_USERS,
                    Constants.PERM_MANAGE_SECTIONS
                ),
                createdAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis()
            )

            sessionManager.saveUserSession(adminUser)
            Resource.Success(adminUser)
        } catch (e: Exception) {
            Log.e(TAG, "Admin sign in error", e)
            val friendlyMsg = when {
                e is FirebaseAuthInvalidUserException || e is FirebaseAuthInvalidCredentialsException ->
                    "Invalid email or password. Please verify your administrator credentials."
                e.message?.contains("network", ignoreCase = true) == true || e.message?.contains("offline", ignoreCase = true) == true ->
                    "Network error. Please check your internet connection."
                else ->
                    "Unable to sign in as administrator. Please check your credentials and try again."
            }
            Resource.Error(friendlyMsg, e)
        }
    }

    override suspend fun signOut(): Resource<Unit> {
        return try {
            auth.signOut()
            sessionManager.clearSession()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error", e)
            Resource.Error("Sign out failed. Please try again.", e)
        }
    }

    override suspend fun updateLastActive() {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection(Constants.COLL_USERS).document(uid)
                .update("lastActiveAt", FieldValue.serverTimestamp())
                .await()
        } catch (_: Exception) {}
    }
}
