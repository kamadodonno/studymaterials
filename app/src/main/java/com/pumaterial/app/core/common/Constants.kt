package com.pumaterial.app.core.common

import com.pumaterial.app.domain.model.Section

object Constants {
    const val PREFS_NAME = "pu_material_prefs"
    const val DATABASE_NAME = "pu_material_db"
    
    // Firestore Collections
    const val COLL_USERS = "users"
    const val COLL_ENROLLMENTS = "enrollmentNumbers"
    const val COLL_ADMINS = "admins"
    const val COLL_DEPARTMENTS = "departments"
    const val COLL_ACADEMIC_YEARS = "academicYears"
    const val COLL_SECTIONS = "sections"
    const val COLL_SUBJECTS = "subjects"
    const val COLL_MODULES = "modules"
    const val COLL_MATERIALS = "materials"
    const val COLL_SUBMISSIONS = "submissions"
    const val COLL_ANNOUNCEMENTS = "announcements"
    const val COLL_ROLES = "roles"
    const val COLL_APP_CONFIG = "appConfig"
    const val DOC_SETTINGS = "settings"

    // Default File Size Limit (50 MB)
    const val DEFAULT_MAX_UPLOAD_SIZE_BYTES = 52428800L // 50 MB

    // Supported File Types
    val SUPPORTED_EXTENSIONS = listOf("pdf", "ppt", "pptx", "doc", "docx")
    
    // Predefined Exact Sections (1-11, 13-18, Other)
    val DEFAULT_SECTION_NAMES = listOf(
        "Section 1", "Section 2", "Section 3", "Section 4",
        "Section 5", "Section 6", "Section 7", "Section 8",
        "Section 9", "Section 10", "Section 11", "Section 13",
        "Section 14", "Section 15", "Section 16", "Section 17",
        "Section 18", "Other"
    )

    val DEFAULT_SECTIONS: List<Section> = DEFAULT_SECTION_NAMES.mapIndexed { index, name ->
        Section(
            id = "sec_${name.replace(" ", "_").lowercase()}",
            name = name,
            order = index + 1,
            isActive = true
        )
    }

    // Roles & Permissions
    const val ROLE_STUDENT = "student"
    const val ROLE_ADMIN = "admin"
    const val ROLE_MODERATOR = "moderator"
    
    const val PERM_MANAGE_MATERIALS = "manage_materials"
    const val PERM_REVIEW_SUBMISSIONS = "review_submissions"
    const val PERM_MANAGE_ANNOUNCEMENTS = "manage_announcements"
    const val PERM_VIEW_USERS = "view_users"
    const val PERM_MANAGE_SECTIONS = "manage_sections"
}
