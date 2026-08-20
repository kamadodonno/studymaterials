package com.pumaterial.app.domain.model

data class Subject(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val departmentId: String = "",
    val academicYearId: String = "",
    val icon: String = "book",
    val order: Int = 0,
    val isActive: Boolean = true,
    val moduleCount: Int = 0,
    val materialCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

data class Module(
    val id: String = "",
    val subjectId: String = "",
    val name: String = "",
    val order: Int = 0,
    val description: String = "",
    val isActive: Boolean = true,
    val materialCount: Int = 0
)

data class Material(
    val id: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val moduleId: String = "",
    val moduleName: String = "",
    val title: String = "",
    val description: String = "",
    val fileName: String = "",
    val fileType: String = "pdf",
    val fileSize: Long = 0L,
    val storagePath: String = "",
    val downloadUrl: String = "",
    val version: Int = 1,
    val isActive: Boolean = true,
    val visibility: String = "all", // "all" | "section"
    val section: String? = null,
    val uploadedBy: String = "",
    val uploadedByName: String = "",
    val uploadedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val localVersion: Int = 0
) {
    val isSectionSpecific: Boolean get() = visibility == "section" && !section.isNullOrBlank()
    val isUpdateAvailable: Boolean get() = isDownloaded && version > localVersion
}
