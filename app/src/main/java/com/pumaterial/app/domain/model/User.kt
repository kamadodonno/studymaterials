package com.pumaterial.app.domain.model

data class User(
    val uid: String = "",
    val name: String = "",
    val enrollmentNumber: String = "",
    val normalizedEnrollmentNumber: String = "",
    val section: String = "",
    val otherSection: String? = null,
    val role: String = "student",
    val permissions: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val submissionCount: Int = 0,
    val downloadCount: Int = 0,
    val deviceTokens: List<String> = emptyList()
) {
    val isAdmin: Boolean get() = role == "admin"
    val isModerator: Boolean get() = role == "moderator" || isAdmin
    
    fun hasPermission(permission: String): Boolean {
        return isAdmin || permissions.contains(permission)
    }

    val displaySection: String get() = if (section == "Other" && !otherSection.isNullOrBlank()) {
        "Other ($otherSection)"
    } else {
        section
    }
}
