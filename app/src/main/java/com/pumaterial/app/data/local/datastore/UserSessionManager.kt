package com.pumaterial.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pumaterial.app.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session_prefs")

class UserSessionManager(private val context: Context) {

    companion object {
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_UID = stringPreferencesKey("user_uid")
        val KEY_NAME = stringPreferencesKey("user_name")
        val KEY_ENROLLMENT = stringPreferencesKey("user_enrollment")
        val KEY_NORM_ENROLLMENT = stringPreferencesKey("user_norm_enrollment")
        val KEY_SECTION = stringPreferencesKey("user_section")
        val KEY_OTHER_SECTION = stringPreferencesKey("user_other_section")
        val KEY_ROLE = stringPreferencesKey("user_role")
        val KEY_PERMISSIONS = stringPreferencesKey("user_permissions") // Comma-separated
        val KEY_CREATED_AT = longPreferencesKey("user_created_at")
        val KEY_LAST_ACTIVE_AT = longPreferencesKey("user_last_active_at")
        val KEY_THEME_MODE = intPreferencesKey("theme_mode") // 0: System, 1: Light, 2: Dark
        val KEY_SUBJECT_ORDER = stringPreferencesKey("custom_subject_order")
    }

    val userSessionFlow: Flow<User?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val isLoggedIn = prefs[KEY_IS_LOGGED_IN] ?: false
            if (!isLoggedIn) return@map null

            val permsString = prefs[KEY_PERMISSIONS] ?: ""
            val permsList = if (permsString.isBlank()) emptyList() else permsString.split(",")

            User(
                uid = prefs[KEY_UID] ?: "",
                name = prefs[KEY_NAME] ?: "",
                enrollmentNumber = prefs[KEY_ENROLLMENT] ?: "",
                normalizedEnrollmentNumber = prefs[KEY_NORM_ENROLLMENT] ?: "",
                section = prefs[KEY_SECTION] ?: "",
                otherSection = prefs[KEY_OTHER_SECTION],
                role = prefs[KEY_ROLE] ?: "student",
                permissions = permsList,
                createdAt = prefs[KEY_CREATED_AT] ?: System.currentTimeMillis(),
                lastActiveAt = prefs[KEY_LAST_ACTIVE_AT] ?: System.currentTimeMillis()
            )
        }

    val themeModeFlow: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[KEY_THEME_MODE] ?: 0 }

    val customSubjectOrderFlow: Flow<List<String>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val raw = prefs[KEY_SUBJECT_ORDER] ?: ""
            if (raw.isBlank()) emptyList() else raw.split(",")
        }

    suspend fun saveUserSession(user: User) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = true
            prefs[KEY_UID] = user.uid
            prefs[KEY_NAME] = user.name
            prefs[KEY_ENROLLMENT] = user.enrollmentNumber
            prefs[KEY_NORM_ENROLLMENT] = user.normalizedEnrollmentNumber
            prefs[KEY_SECTION] = user.section
            user.otherSection?.let { prefs[KEY_OTHER_SECTION] = it } ?: prefs.remove(KEY_OTHER_SECTION)
            prefs[KEY_ROLE] = user.role
            prefs[KEY_PERMISSIONS] = user.permissions.joinToString(",")
            prefs[KEY_CREATED_AT] = user.createdAt
            prefs[KEY_LAST_ACTIVE_AT] = user.lastActiveAt
        }
    }

    suspend fun updateThemeMode(mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    suspend fun saveCustomSubjectOrder(orderedIds: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SUBJECT_ORDER] = orderedIds.joinToString(",")
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_IS_LOGGED_IN)
            prefs.remove(KEY_UID)
            prefs.remove(KEY_NAME)
            prefs.remove(KEY_ENROLLMENT)
            prefs.remove(KEY_NORM_ENROLLMENT)
            prefs.remove(KEY_SECTION)
            prefs.remove(KEY_OTHER_SECTION)
            prefs.remove(KEY_ROLE)
            prefs.remove(KEY_PERMISSIONS)
            prefs.remove(KEY_SUBJECT_ORDER)
        }
    }
}
