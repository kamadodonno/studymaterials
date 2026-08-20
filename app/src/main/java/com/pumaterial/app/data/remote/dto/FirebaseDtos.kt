package com.pumaterial.app.data.remote.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.pumaterial.app.domain.model.*
import java.util.Date

/**
 * Universal timestamp parser that safely handles Firestore Timestamp, Long, Double, Date, or String
 * without throwing deserialization exceptions when schema types vary.
 */
fun parseTimestamp(value: Any?): Long {
    return when (value) {
        null -> System.currentTimeMillis()
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        is Date -> value.time
        is String -> value.toLongOrNull() ?: System.currentTimeMillis()
        else -> System.currentTimeMillis()
    }
}

fun parseNullableTimestamp(value: Any?): Long? {
    return when (value) {
        null -> null
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        is Date -> value.time
        is String -> value.toLongOrNull()
        else -> null
    }
}

@IgnoreExtraProperties
data class UserDto(
    val uid: String = "",
    val name: String = "",
    val enrollmentNumber: String = "",
    val normalizedEnrollmentNumber: String = "",
    val section: String = "",
    val otherSection: String? = null,
    val role: String = "student",
    val permissions: List<String> = emptyList(),
    val createdAt: Any? = null,
    val lastActiveAt: Any? = null,
    val submissionCount: Int = 0,
    val downloadCount: Int = 0,
    val deviceTokens: List<String> = emptyList()
) {
    fun toDomain(documentId: String = ""): User = User(
        uid = if (uid.isNotBlank()) uid else documentId,
        name = name,
        enrollmentNumber = enrollmentNumber,
        normalizedEnrollmentNumber = normalizedEnrollmentNumber,
        section = section,
        otherSection = otherSection,
        role = role,
        permissions = permissions,
        createdAt = parseTimestamp(createdAt),
        lastActiveAt = parseTimestamp(lastActiveAt),
        submissionCount = submissionCount,
        downloadCount = downloadCount,
        deviceTokens = deviceTokens
    )
}

@IgnoreExtraProperties
data class DepartmentDto(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val description: String = "",
    val order: Int = 0,
    @get:PropertyName("isActive") val isActive: Boolean = true,
    val createdAt: Any? = null
) {
    fun toDomain(documentId: String = ""): Department = Department(
        id = if (id.isNotBlank()) id else documentId,
        name = name,
        code = code,
        description = description,
        order = order,
        isActive = isActive,
        createdAt = parseTimestamp(createdAt)
    )
}

@IgnoreExtraProperties
data class AcademicYearDto(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val order: Int = 0,
    @get:PropertyName("isActive") val isActive: Boolean = true
) {
    fun toDomain(documentId: String = ""): AcademicYear = AcademicYear(
        id = if (id.isNotBlank()) id else documentId,
        name = name,
        code = code,
        order = order,
        isActive = isActive
    )
}

@IgnoreExtraProperties
data class SectionDto(
    val id: String = "",
    val name: String = "",
    val order: Int = 0,
    @get:PropertyName("isActive") val isActive: Boolean = true,
    @get:PropertyName("isDefault") val isDefault: Boolean = true
) {
    fun toDomain(documentId: String = ""): Section = Section(
        id = if (id.isNotBlank()) id else documentId,
        name = name,
        order = order,
        isActive = isActive,
        isDefault = isDefault
    )
}

@IgnoreExtraProperties
data class SubjectDto(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val departmentId: String = "",
    val academicYearId: String = "",
    val icon: String = "book",
    val order: Int = 0,
    @get:PropertyName("isActive") val isActive: Boolean = true,
    val moduleCount: Int = 0,
    val materialCount: Int = 0,
    val updatedAt: Any? = null
) {
    fun toDomain(documentId: String = ""): Subject = Subject(
        id = if (id.isNotBlank()) id else documentId,
        name = name,
        code = code,
        departmentId = departmentId,
        academicYearId = academicYearId,
        icon = icon,
        order = order,
        isActive = isActive,
        moduleCount = moduleCount,
        materialCount = materialCount,
        updatedAt = parseTimestamp(updatedAt)
    )
}

@IgnoreExtraProperties
data class ModuleDto(
    val id: String = "",
    val subjectId: String = "",
    val name: String = "",
    val order: Int = 0,
    val description: String = "",
    @get:PropertyName("isActive") val isActive: Boolean = true,
    val materialCount: Int = 0
) {
    fun toDomain(documentId: String = ""): Module = Module(
        id = if (id.isNotBlank()) id else documentId,
        subjectId = subjectId,
        name = name,
        order = order,
        description = description,
        isActive = isActive,
        materialCount = materialCount
    )
}

@IgnoreExtraProperties
data class MaterialDto(
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
    @get:PropertyName("isActive") val isActive: Boolean = true,
    val visibility: String = "all",
    val section: String? = null,
    val uploadedBy: String = "",
    val uploadedByName: String = "",
    val uploadedAt: Any? = null,
    val updatedAt: Any? = null
) {
    fun toDomain(documentId: String = ""): Material = Material(
        id = if (id.isNotBlank()) id else documentId,
        subjectId = subjectId,
        subjectName = subjectName,
        moduleId = moduleId,
        moduleName = moduleName,
        title = title,
        description = description,
        fileName = fileName,
        fileType = fileType,
        fileSize = fileSize,
        storagePath = storagePath,
        downloadUrl = downloadUrl,
        version = version,
        isActive = isActive,
        visibility = visibility,
        section = section,
        uploadedBy = uploadedBy,
        uploadedByName = uploadedByName,
        uploadedAt = parseTimestamp(uploadedAt),
        updatedAt = parseTimestamp(updatedAt)
    )
}

@IgnoreExtraProperties
data class SubmissionDto(
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
    val status: String = "pending",
    val submittedAt: Any? = null,
    val reviewedAt: Any? = null,
    val reviewedBy: String? = null,
    val rejectionReason: String? = null
) {
    fun toDomain(documentId: String = ""): Submission = Submission(
        id = if (id.isNotBlank()) id else documentId,
        submittedBy = submittedBy,
        submitterName = submitterName,
        submitterEnrollment = submitterEnrollment,
        submitterSection = submitterSection,
        fileName = fileName,
        fileType = fileType,
        fileSize = fileSize,
        title = title,
        description = description,
        subjectId = subjectId,
        subjectName = subjectName,
        moduleId = moduleId,
        moduleName = moduleName,
        storagePath = storagePath,
        status = status,
        submittedAt = parseTimestamp(submittedAt),
        reviewedAt = parseNullableTimestamp(reviewedAt),
        reviewedBy = reviewedBy,
        rejectionReason = rejectionReason
    )
}

@IgnoreExtraProperties
data class AnnouncementDto(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val targetVisibility: String = "all",
    val targetSection: String? = null,
    val priority: String = "normal",
    @get:PropertyName("isActive") val isActive: Boolean = true,
    val createdAt: Any? = null,
    val authorName: String = "Admin"
) {
    fun toDomain(documentId: String = ""): Announcement = Announcement(
        id = if (id.isNotBlank()) id else documentId,
        title = title,
        message = message,
        targetVisibility = targetVisibility,
        targetSection = targetSection,
        priority = priority,
        isActive = isActive,
        createdAt = parseTimestamp(createdAt),
        authorName = authorName
    )
}
