package com.pumaterial.app.domain.model

data class Submission(
    val id: String = "",
    val submittedBy: String = "",
    val submitterName: String = "",
    val submitterEnrollment: String = "",
    val submitterSection: String = "",
    val fileName: String = "",
    val fileType: String = "pdf",
    val fileSize: Long = 0L,
    val title: String = "",
    val description: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val moduleId: String = "",
    val moduleName: String = "",
    val storagePath: String = "",
    val status: String = "pending", // "pending" | "approved" | "rejected"
    val submittedAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null,
    val reviewedBy: String? = null,
    val rejectionReason: String? = null
) {
    val isPending: Boolean get() = status == "pending"
    val isApproved: Boolean get() = status == "approved"
    val isRejected: Boolean get() = status == "rejected"
}

data class Announcement(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val targetVisibility: String = "all", // "all" | "section"
    val targetSection: String? = null,
    val priority: String = "normal", // "normal" | "urgent"
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val authorName: String = "Admin"
) {
    val isUrgent: Boolean get() = priority == "urgent"
}

data class Role(
    val id: String = "",
    val name: String = "",
    val permissions: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class PersonalFolder(
    val folderId: String = "",
    val folderName: String = "",
    val colorHex: String = "#1E40AF",
    val createdAt: Long = System.currentTimeMillis(),
    val itemCount: Int = 0
)

data class PersonalFolderItem(
    val folderId: String = "",
    val materialId: String = "",
    val title: String = "",
    val fileName: String = "",
    val fileType: String = "pdf",
    val localFilePath: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

data class AppConfig(
    val maxUploadSizeBytes: Long = 52428800L,
    val allowedFileTypes: List<String> = listOf("pdf", "ppt", "pptx", "doc", "docx"),
    val suggestedApps: Map<String, String> = emptyMap()
)
