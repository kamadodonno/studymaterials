package com.pumaterial.app.domain.repository

import android.net.Uri
import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.io.File

interface AuthRepository {
    val currentUserFlow: Flow<User?>
    suspend fun getCurrentUser(): User?
    suspend fun registerStudent(
        name: String,
        enrollmentNumber: String,
        section: String,
        otherSection: String?
    ): Resource<User>
    suspend fun signInAdmin(email: String, password: String): Resource<User>
    suspend fun signOut(): Resource<Unit>
    suspend fun updateLastActive()
}

interface MaterialRepository {
    fun observeDepartments(): Flow<List<Department>>
    fun observeAcademicYears(): Flow<List<AcademicYear>>
    fun observeSections(): Flow<List<Section>>
    fun observeSubjects(departmentId: String?, yearId: String?): Flow<List<Subject>>
    fun observeModules(subjectId: String): Flow<List<Module>>
    fun observeMaterials(moduleId: String): Flow<List<Material>>
    fun observeRecentlyAdded(userSection: String, limit: Int = 10): Flow<List<Material>>
    fun observeRecentlyUpdated(userSection: String, limit: Int = 10): Flow<List<Material>>
    suspend fun searchMaterials(query: String, subjectId: String?, fileType: String?): Resource<List<Material>>
    suspend fun getMaterialById(materialId: String): Resource<Material>
}

interface DownloadRepository {
    fun observeDownloadedMaterials(): Flow<List<DownloadedMaterialEntityWrapper>>
    fun observeDownloadedMaterialIds(): Flow<List<String>>
    fun isMaterialDownloaded(materialId: String): Flow<Boolean>
    suspend fun getLocalFileForMaterial(materialId: String, fileName: String): File?
    suspend fun downloadOrUpdateMaterial(
        material: Material,
        onProgress: (Float) -> Unit = {}
    ): Resource<File>
    suspend fun deleteDownloadedMaterial(materialId: String, fileName: String): Resource<Unit>
}

data class DownloadedMaterialEntityWrapper(
    val materialId: String,
    val subjectId: String,
    val moduleId: String,
    val title: String,
    val fileName: String,
    val fileType: String,
    val localFilePath: String,
    val localVersion: Int,
    val cloudVersion: Int,
    val fileSize: Long,
    val downloadedAt: Long
)

interface PersonalFolderRepository {
    fun observeFolders(): Flow<List<PersonalFolder>>
    fun observeItemsInFolder(folderId: String): Flow<List<PersonalFolderItem>>
    suspend fun createFolder(name: String, colorHex: String): Resource<Unit>
    suspend fun updateFolder(folderId: String, name: String, colorHex: String): Resource<Unit>
    suspend fun deleteFolder(folderId: String): Resource<Unit>
    suspend fun addItemToFolder(folderId: String, material: Material): Resource<Unit>
    suspend fun removeItemFromFolder(folderId: String, materialId: String): Resource<Unit>
}

interface SubmissionRepository {
    fun observeMySubmissions(): Flow<List<Submission>>
    suspend fun submitStudyMaterial(
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
        onProgress: (Float) -> Unit = {}
    ): Resource<Submission>
    suspend fun deleteMyPendingSubmission(submissionId: String): Resource<Unit>
}

interface AnnouncementRepository {
    fun observeAnnouncements(userSection: String): Flow<List<Announcement>>
    suspend fun createAnnouncement(
        title: String,
        message: String,
        targetVisibility: String,
        targetSection: String?,
        priority: String
    ): Resource<Announcement>
    suspend fun deleteAnnouncement(announcementId: String): Resource<Unit>
}

interface AdminRepository {
    suspend fun getDashboardStats(): Resource<AdminDashboardStats>
    fun observeUsers(): Flow<List<User>>
    fun observePendingSubmissions(): Flow<List<Submission>>
    suspend fun approveSubmissionSafely(
        submission: Submission,
        editedTitle: String,
        editedDescription: String,
        subjectId: String,
        subjectName: String,
        moduleId: String,
        moduleName: String,
        visibility: String,
        section: String?
    ): Resource<Material>
    suspend fun rejectSubmission(submissionId: String, reason: String): Resource<Unit>
    suspend fun createDepartment(name: String, code: String, description: String, order: Int): Resource<Department>
    suspend fun createAcademicYear(name: String, code: String, order: Int): Resource<AcademicYear>
    suspend fun createSection(name: String, order: Int): Resource<Section>
    suspend fun updateSectionActiveState(sectionId: String, isActive: Boolean): Resource<Unit>
    suspend fun createSubject(
        name: String,
        code: String,
        departmentId: String,
        academicYearId: String,
        icon: String,
        order: Int
    ): Resource<Subject>
    suspend fun createModule(subjectId: String, name: String, description: String, order: Int): Resource<Module>
    suspend fun publishMaterial(
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
        onProgress: (Float) -> Unit = {}
    ): Resource<Material>
    suspend fun publishMaterialLink(
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
    ): Resource<Material>
    suspend fun replaceMaterial(
        materialId: String,
        fileUri: Uri,
        fileName: String,
        fileType: String,
        fileSize: Long,
        subjectId: String,
        moduleId: String,
        newVersion: Int,
        onProgress: (Float) -> Unit = {}
    ): Resource<Unit>
    suspend fun deactivateMaterial(materialId: String, isActive: Boolean): Resource<Unit>
    suspend fun updateMaterial(
        materialId: String,
        title: String,
        description: String,
        visibility: String,
        section: String?
    ): Resource<Unit>
    suspend fun deleteMaterial(materialId: String, storagePath: String): Resource<Unit>
    suspend fun updateSubject(
        subjectId: String,
        name: String,
        code: String,
        deptId: String,
        yearId: String
    ): Resource<Unit>
    suspend fun deleteSubject(subjectId: String): Resource<Unit>
    suspend fun updateModule(
        moduleId: String,
        name: String,
        description: String
    ): Resource<Unit>
    suspend fun deleteModule(moduleId: String): Resource<Unit>
    suspend fun updateDepartment(
        departmentId: String,
        name: String,
        code: String,
        description: String
    ): Resource<Unit>
    suspend fun deleteDepartment(departmentId: String): Resource<Unit>
    suspend fun updateAcademicYear(
        yearId: String,
        name: String,
        code: String
    ): Resource<Unit>
    suspend fun deleteAcademicYear(yearId: String): Resource<Unit>
    suspend fun updateUserRole(userId: String, newRole: String, permissions: List<String>): Resource<Unit>
    suspend fun deleteUser(userId: String): Resource<Unit>
}

data class AdminDashboardStats(
    val totalUsers: Int = 0,
    val usersBySection: Map<String, Int> = emptyMap(),
    val pendingSubmissionsCount: Int = 0,
    val totalMaterialsCount: Int = 0,
    val totalSubjectsCount: Int = 0
)
