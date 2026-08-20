package com.pumaterial.app.ui.admin.materials

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.core.components.*
import com.pumaterial.app.core.file.FileHelper
import com.pumaterial.app.domain.model.*
import com.pumaterial.app.domain.repository.AdminRepository
import com.pumaterial.app.domain.repository.MaterialRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AdminMaterialsUiState(
    val selectedTab: Int = 0, // 0: Subjects & Modules, 1: Depts & Years, 2: Upload / Add Link
    val uploadMode: Int = 1,  // 0: Upload File, 1: Google Drive / Web Link (Default $0 free unlimited)
    val departments: List<Department> = emptyList(),
    val academicYears: List<AcademicYear> = emptyList(),
    val sections: List<Section> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val modules: List<Module> = emptyList(),
    val selectedSubjectForModule: Subject? = null,
    val selectedModuleForMaterials: Module? = null,
    val moduleMaterials: List<Material> = emptyList(),
    // Subject level general materials
    val subjectGeneralMaterials: List<Material> = emptyList(),
    // Editing states
    val editingSubject: Subject? = null,
    val editingModule: Module? = null,
    val editingMaterial: Material? = null,
    val editingDepartment: Department? = null,
    val editingAcademicYear: AcademicYear? = null,
    // Deletion states
    val deletingSubject: Subject? = null,
    val deletingModule: Module? = null,
    val deletingMaterial: Material? = null,
    // Create Dept Dialog
    val showCreateDeptDialog: Boolean = false,
    // Create Year Dialog
    val showCreateYearDialog: Boolean = false,
    // Create Subject Dialog
    val showCreateSubjectDialog: Boolean = false,
    // Create Module Dialog
    val showCreateModuleDialog: Boolean = false,
    // Material Upload / Link fields
    val uploadFileUri: Uri? = null,
    val uploadFileName: String = "",
    val uploadFileType: String = "pdf",
    val uploadFileSize: Long = 0L,
    val uploadLinkUrl: String = "",
    val uploadTitle: String = "",
    val uploadDescription: String = "",
    val uploadSubject: Subject? = null,
    val uploadModule: Module? = null, // null = Direct Subject Note
    val uploadVisibility: String = "all",
    val uploadSection: String = "",
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    // Success / Error
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class AdminMaterialsViewModel(
    private val adminRepository: AdminRepository,
    private val materialRepository: MaterialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminMaterialsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            materialRepository.observeDepartments().collect { list ->
                _uiState.update { it.copy(departments = list) }
            }
        }
        viewModelScope.launch {
            materialRepository.observeAcademicYears().collect { list ->
                _uiState.update { it.copy(academicYears = list) }
            }
        }
        viewModelScope.launch {
            materialRepository.observeSections().collect { list ->
                _uiState.update { it.copy(sections = list) }
            }
        }
        viewModelScope.launch {
            materialRepository.observeSubjects(null, null).collect { list ->
                _uiState.update { it.copy(subjects = list) }
            }
        }
    }

    fun selectTab(index: Int) = _uiState.update { it.copy(selectedTab = index) }
    fun selectUploadMode(mode: Int) = _uiState.update { it.copy(uploadMode = mode) }

    fun selectSubjectForModules(subject: Subject) {
        if (_uiState.value.selectedSubjectForModule?.id == subject.id) {
            _uiState.update { it.copy(selectedSubjectForModule = null, modules = emptyList(), selectedModuleForMaterials = null, moduleMaterials = emptyList(), subjectGeneralMaterials = emptyList()) }
            return
        }
        _uiState.update { it.copy(selectedSubjectForModule = subject, selectedModuleForMaterials = null, moduleMaterials = emptyList()) }
        viewModelScope.launch {
            materialRepository.observeModules(subject.id).collect { list ->
                _uiState.update { it.copy(modules = list) }
            }
        }
        viewModelScope.launch {
            materialRepository.observeMaterials("").collect { allMats ->
                val generals = allMats.filter { it.subjectId == subject.id && it.moduleId.isBlank() }
                _uiState.update { it.copy(subjectGeneralMaterials = generals) }
            }
        }
    }

    fun selectModuleForMaterials(module: Module) {
        if (_uiState.value.selectedModuleForMaterials?.id == module.id) {
            _uiState.update { it.copy(selectedModuleForMaterials = null, moduleMaterials = emptyList()) }
            return
        }
        _uiState.update { it.copy(selectedModuleForMaterials = module) }
        viewModelScope.launch {
            materialRepository.observeMaterials(module.id).collect { list ->
                _uiState.update { it.copy(moduleMaterials = list) }
            }
        }
    }

    // Departments
    fun showCreateDeptDialog() = _uiState.update { it.copy(showCreateDeptDialog = true) }
    fun dismissCreateDeptDialog() = _uiState.update { it.copy(showCreateDeptDialog = false) }
    fun createDepartment(name: String, code: String, desc: String) {
        viewModelScope.launch {
            val count = _uiState.value.departments.size + 1
            val result = adminRepository.createDepartment(name, code, desc, count)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(showCreateDeptDialog = false, successMessage = "Department created successfully!") }
                is Resource.Error -> _uiState.update { it.copy(errorMessage = "Failed to create department: ${result.message}") }
                Resource.Loading -> Unit
            }
        }
    }

    fun startEditDepartment(dept: Department) = _uiState.update { it.copy(editingDepartment = dept) }
    fun dismissEditDepartment() = _uiState.update { it.copy(editingDepartment = null) }
    fun saveEditDepartment(name: String, code: String, desc: String) {
        val dept = _uiState.value.editingDepartment ?: return
        viewModelScope.launch {
            val result = adminRepository.updateDepartment(dept.id, name, code, desc)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(editingDepartment = null, successMessage = "Department updated!") }
                is Resource.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    // Academic Years
    fun showCreateYearDialog() = _uiState.update { it.copy(showCreateYearDialog = true) }
    fun dismissCreateYearDialog() = _uiState.update { it.copy(showCreateYearDialog = false) }
    fun createAcademicYear(name: String, code: String) {
        viewModelScope.launch {
            val count = _uiState.value.academicYears.size + 1
            val result = adminRepository.createAcademicYear(name, code, count)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(showCreateYearDialog = false, successMessage = "Academic Year created successfully!") }
                is Resource.Error -> _uiState.update { it.copy(errorMessage = "Failed to create academic year: ${result.message}") }
                Resource.Loading -> Unit
            }
        }
    }

    fun startEditAcademicYear(year: AcademicYear) = _uiState.update { it.copy(editingAcademicYear = year) }
    fun dismissEditAcademicYear() = _uiState.update { it.copy(editingAcademicYear = null) }
    fun saveEditAcademicYear(name: String, code: String) {
        val year = _uiState.value.editingAcademicYear ?: return
        viewModelScope.launch {
            val result = adminRepository.updateAcademicYear(year.id, name, code)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(editingAcademicYear = null, successMessage = "Academic Year updated!") }
                is Resource.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    // Subjects
    fun showCreateSubjectDialog() = _uiState.update { it.copy(showCreateSubjectDialog = true) }
    fun dismissCreateSubjectDialog() = _uiState.update { it.copy(showCreateSubjectDialog = false) }
    fun createSubject(name: String, code: String, deptId: String, yearId: String) {
        viewModelScope.launch {
            val count = _uiState.value.subjects.size + 1
            val result = adminRepository.createSubject(name, code, deptId, yearId, "book", count)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(showCreateSubjectDialog = false, successMessage = "Subject created successfully!") }
                is Resource.Error -> _uiState.update { it.copy(errorMessage = "Failed to create subject: ${result.message}") }
                Resource.Loading -> Unit
            }
        }
    }

    fun startEditSubject(subject: Subject) = _uiState.update { it.copy(editingSubject = subject) }
    fun dismissEditSubject() = _uiState.update { it.copy(editingSubject = null) }
    fun saveEditSubject(name: String, code: String, deptId: String, yearId: String) {
        val sub = _uiState.value.editingSubject ?: return
        viewModelScope.launch {
            val result = adminRepository.updateSubject(sub.id, name, code, deptId, yearId)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(editingSubject = null, successMessage = "Subject updated!") }
                is Resource.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    // Modules
    fun showCreateModuleDialog() = _uiState.update { it.copy(showCreateModuleDialog = true) }
    fun dismissCreateModuleDialog() = _uiState.update { it.copy(showCreateModuleDialog = false) }
    fun createModule(name: String, desc: String) {
        val subject = _uiState.value.selectedSubjectForModule ?: return
        viewModelScope.launch {
            val count = _uiState.value.modules.size + 1
            val result = adminRepository.createModule(subject.id, name, desc, count)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(showCreateModuleDialog = false, successMessage = "Module created successfully!") }
                is Resource.Error -> _uiState.update { it.copy(errorMessage = "Failed to create module: ${result.message}") }
                Resource.Loading -> Unit
            }
        }
    }

    fun startEditModule(module: Module) = _uiState.update { it.copy(editingModule = module) }
    fun dismissEditModule() = _uiState.update { it.copy(editingModule = null) }
    fun saveEditModule(name: String, desc: String) {
        val mod = _uiState.value.editingModule ?: return
        viewModelScope.launch {
            val result = adminRepository.updateModule(mod.id, name, desc)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(editingModule = null, successMessage = "Module updated!") }
                is Resource.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    // Materials
    fun startEditMaterial(material: Material) = _uiState.update { it.copy(editingMaterial = material) }
    fun dismissEditMaterial() = _uiState.update { it.copy(editingMaterial = null) }
    fun saveEditMaterial(title: String, desc: String, visibility: String, section: String?) {
        val mat = _uiState.value.editingMaterial ?: return
        viewModelScope.launch {
            val result = adminRepository.updateMaterial(mat.id, title, desc, visibility, section)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(editingMaterial = null, successMessage = "Material updated!") }
                is Resource.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    // Deletions
    fun promptDeleteSubject(subject: Subject) = _uiState.update { it.copy(deletingSubject = subject) }
    fun dismissDeleteSubject() = _uiState.update { it.copy(deletingSubject = null) }
    fun confirmDeleteSubject() {
        val subject = _uiState.value.deletingSubject ?: return
        viewModelScope.launch {
            val result = adminRepository.deleteSubject(subject.id)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(deletingSubject = null, selectedSubjectForModule = null, successMessage = "Subject deleted successfully.") }
                is Resource.Error -> _uiState.update { it.copy(deletingSubject = null, errorMessage = "Failed to delete subject: ${result.message}") }
                Resource.Loading -> Unit
            }
        }
    }

    fun promptDeleteModule(module: Module) = _uiState.update { it.copy(deletingModule = module) }
    fun dismissDeleteModule() = _uiState.update { it.copy(deletingModule = null) }
    fun confirmDeleteModule() {
        val module = _uiState.value.deletingModule ?: return
        viewModelScope.launch {
            val result = adminRepository.deleteModule(module.id)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(deletingModule = null, selectedModuleForMaterials = null, successMessage = "Module deleted successfully.") }
                is Resource.Error -> _uiState.update { it.copy(deletingModule = null, errorMessage = "Failed to delete module: ${result.message}") }
                Resource.Loading -> Unit
            }
        }
    }

    fun promptDeleteMaterial(material: Material) = _uiState.update { it.copy(deletingMaterial = material) }
    fun dismissDeleteMaterial() = _uiState.update { it.copy(deletingMaterial = null) }
    fun confirmDeleteMaterial() {
        val material = _uiState.value.deletingMaterial ?: return
        viewModelScope.launch {
            val result = adminRepository.deleteMaterial(material.id, material.storagePath)
            when (result) {
                is Resource.Success -> _uiState.update { it.copy(deletingMaterial = null, successMessage = "Study material removed.") }
                is Resource.Error -> _uiState.update { it.copy(deletingMaterial = null, errorMessage = "Failed to delete material: ${result.message}") }
                Resource.Loading -> Unit
            }
        }
    }

    // Material Direct Upload & Link
    fun onUploadFileSelected(uri: Uri, name: String, size: Long) {
        val ext = FileHelper.getFileExtension(name).ifBlank { "pdf" }
        _uiState.update {
            it.copy(
                uploadFileUri = uri,
                uploadFileName = name,
                uploadFileType = ext,
                uploadFileSize = size,
                uploadTitle = if (it.uploadTitle.isBlank()) name.substringBeforeLast('.') else it.uploadTitle
            )
        }
    }

    fun onUploadTitleChange(t: String) = _uiState.update { it.copy(uploadTitle = t) }
    fun onUploadDescChange(d: String) = _uiState.update { it.copy(uploadDescription = d) }
    fun onUploadLinkUrlChange(u: String) = _uiState.update { it.copy(uploadLinkUrl = u) }
    fun onUploadFileTypeChange(ft: String) = _uiState.update { it.copy(uploadFileType = ft) }
    fun onUploadSubjectSelect(s: Subject) {
        _uiState.update { it.copy(uploadSubject = s, uploadModule = null) }
        viewModelScope.launch {
            materialRepository.observeModules(s.id).collect { list ->
                _uiState.update { it.copy(modules = list) }
            }
        }
    }
    fun onUploadModuleSelect(m: Module?) = _uiState.update { it.copy(uploadModule = m) }
    fun onUploadVisibilityChange(v: String) = _uiState.update { it.copy(uploadVisibility = v) }
    fun onUploadSectionChange(s: String) = _uiState.update { it.copy(uploadSection = s) }

    fun publishDirectMaterial() {
        val state = _uiState.value
        val subject = state.uploadSubject ?: return
        val moduleId = state.uploadModule?.id ?: ""
        val moduleName = state.uploadModule?.name ?: "General Notes"

        if (state.uploadTitle.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please provide a material title.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, uploadProgress = 0f) }

            if (state.uploadMode == 1) {
                // Publish via Google Drive / Web link
                val url = state.uploadLinkUrl.trim()
                if (url.isBlank()) {
                    _uiState.update { it.copy(isUploading = false, errorMessage = "Please enter a valid Google Drive or web link.") }
                    return@launch
                }

                val result = adminRepository.publishMaterialLink(
                    title = state.uploadTitle,
                    description = state.uploadDescription,
                    url = url,
                    fileType = state.uploadFileType,
                    subjectId = subject.id,
                    subjectName = subject.name,
                    moduleId = moduleId,
                    moduleName = moduleName,
                    visibility = state.uploadVisibility,
                    section = if (state.uploadVisibility == "section") state.uploadSection else null
                )

                when (result) {
                    is Resource.Success -> {
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadLinkUrl = "",
                                uploadTitle = "",
                                uploadDescription = "",
                                successMessage = "Material added to library successfully!"
                            )
                        }
                    }
                    is Resource.Error -> _uiState.update { it.copy(isUploading = false, errorMessage = result.message) }
                    Resource.Loading -> Unit
                }
            } else {
                // Publish via Firebase Storage file upload
                val uri = state.uploadFileUri
                if (uri == null) {
                    _uiState.update { it.copy(isUploading = false, errorMessage = "Please select a file to upload.") }
                    return@launch
                }

                val result = adminRepository.publishMaterial(
                    fileUri = uri,
                    fileName = state.uploadFileName,
                    fileType = state.uploadFileType,
                    fileSize = state.uploadFileSize,
                    title = state.uploadTitle,
                    description = state.uploadDescription,
                    subjectId = subject.id,
                    subjectName = subject.name,
                    moduleId = moduleId,
                    moduleName = moduleName,
                    visibility = state.uploadVisibility,
                    section = if (state.uploadVisibility == "section") state.uploadSection else null,
                    onProgress = { p -> _uiState.update { it.copy(uploadProgress = p) } }
                )

                when (result) {
                    is Resource.Success -> {
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadFileUri = null,
                                uploadFileName = "",
                                uploadTitle = "",
                                uploadDescription = "",
                                successMessage = "Material published to study library!"
                            )
                        }
                    }
                    is Resource.Error -> _uiState.update { it.copy(isUploading = false, errorMessage = result.message) }
                    Resource.Loading -> Unit
                }
            }
        }
    }

    fun dismissSuccess() = _uiState.update { it.copy(successMessage = null) }
    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMaterialsScreen(
    viewModel: AdminMaterialsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            var name = "file"
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx != -1) name = cursor.getString(nameIdx)
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                }
            }
            viewModel.onUploadFileSelected(uri, name, size)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Curriculum & Library",
                subtitle = "Manage departments, subjects & study files",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(selected = state.selectedTab == 0, onClick = { viewModel.selectTab(0) }, text = { Text("Subjects (${state.subjects.size})") })
                Tab(selected = state.selectedTab == 1, onClick = { viewModel.selectTab(1) }, text = { Text("Depts & Years") })
                Tab(selected = state.selectedTab == 2, onClick = { viewModel.selectTab(2) }, text = { Text("Add Material") })
            }

            when (state.selectedTab) {
                0 -> {
                    // Subjects & Modules Management
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Button(
                                onClick = viewModel::showCreateSubjectDialog,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create New Subject")
                            }
                        }

                        if (state.subjects.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No subjects created yet. Click above to add your first subject!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        items(state.subjects) { subject ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.selectSubjectForModules(subject) }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = subject.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = subject.code,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${subject.moduleCount} Modules • ${subject.materialCount} Materials (Tap to view)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(onClick = { viewModel.startEditSubject(subject) }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Subject", tint = MaterialTheme.colorScheme.primary)
                                        }

                                        IconButton(onClick = { viewModel.promptDeleteSubject(subject) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Subject", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }

                                    if (state.selectedSubjectForModule?.id == subject.id) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // General Direct Files in Subject
                                        if (state.subjectGeneralMaterials.isNotEmpty()) {
                                            Text("General & Practice Files (Direct Subject Notes):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                            state.subjectGeneralMaterials.forEach { mat ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 3.dp)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                        .padding(8.dp)
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(mat.title, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                                        Text("${mat.fileType.uppercase()} • Direct Subject File", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    IconButton(onClick = { viewModel.startEditMaterial(mat) }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(onClick = { viewModel.promptDeleteMaterial(mat) }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Modules in this Subject:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            TextButton(onClick = viewModel::showCreateModuleDialog) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add Module", fontSize = 12.sp)
                                            }
                                        }

                                        if (state.modules.isEmpty()) {
                                            Text("No modules added yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        state.modules.forEach { mod ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (state.selectedModuleForMaterials?.id == mod.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .clickable { viewModel.selectModuleForMaterials(mod) }
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(mod.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                            Text("${mod.materialCount} materials (Tap to inspect)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                        IconButton(
                                                            onClick = { viewModel.startEditModule(mod) },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Edit,
                                                                contentDescription = "Edit Module",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { viewModel.promptDeleteModule(mod) },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.DeleteOutline,
                                                                contentDescription = "Delete Module",
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }

                                                    // Material list inside this module
                                                    if (state.selectedModuleForMaterials?.id == mod.id) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        if (state.moduleMaterials.isEmpty()) {
                                                            Text("No files published in this module.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        } else {
                                                            state.moduleMaterials.forEach { mat ->
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(vertical = 3.dp)
                                                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                                                        .padding(8.dp)
                                                                ) {
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text(mat.title, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                                                        Text("${mat.fileType.uppercase()} • ${if (mat.fileSize > 0) FileHelper.formatFileSize(mat.fileSize) else "Cloud Link"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                    }
                                                                    IconButton(
                                                                        onClick = { viewModel.startEditMaterial(mat) },
                                                                        modifier = Modifier.size(24.dp)
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Default.Edit,
                                                                            contentDescription = "Edit Material",
                                                                            tint = MaterialTheme.colorScheme.primary,
                                                                            modifier = Modifier.size(16.dp)
                                                                        )
                                                                    }
                                                                    IconButton(
                                                                        onClick = { viewModel.promptDeleteMaterial(mat) },
                                                                        modifier = Modifier.size(24.dp)
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Default.DeleteOutline,
                                                                            contentDescription = "Delete Material",
                                                                            tint = MaterialTheme.colorScheme.error,
                                                                            modifier = Modifier.size(16.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Departments & Academic Years
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = viewModel::showCreateDeptDialog,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("+ Department")
                                }
                                Button(
                                    onClick = viewModel::showCreateYearDialog,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("+ Academic Year")
                                }
                            }
                        }

                        item {
                            Text("Departments (${state.departments.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(state.departments) { dept ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dept.name, fontWeight = FontWeight.Bold)
                                        Text(dept.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(dept.code, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = { viewModel.startEditDepartment(dept) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Dept", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        item {
                            Text("Academic Years (${state.academicYears.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(state.academicYears) { year ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(year.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(year.code, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = { viewModel.startEditAcademicYear(year) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Year", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Material Creation (Google Drive Link or File Upload)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Mode Selection
                        Text("Add Method:", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilterChip(
                                selected = state.uploadMode == 1,
                                onClick = { viewModel.selectUploadMode(1) },
                                label = { Text("Google Drive / Web Link (100% Free)") },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = state.uploadMode == 0,
                                onClick = { viewModel.selectUploadMode(0) },
                                label = { Text("File Upload") },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (state.uploadMode == 1) {
                            // Link Mode: $0 Cost, unlimited storage
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Paste any Google Drive share link, Google Slides link, OneDrive link, or direct PDF/PPT URL.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = state.uploadLinkUrl,
                                onValueChange = viewModel::onUploadLinkUrlChange,
                                label = { Text("Google Drive / Cloud Share Link") },
                                placeholder = { Text("https://drive.google.com/file/d/...") },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("File Format:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("pdf", "pptx", "docx", "ppt", "doc").forEach { ext ->
                                    FilterChip(
                                        selected = state.uploadFileType.equals(ext, ignoreCase = true),
                                        onClick = { viewModel.onUploadFileTypeChange(ext) },
                                        label = { Text(ext.uppercase()) }
                                    )
                                }
                            }
                        } else {
                            // File upload mode
                            OutlinedCard(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { filePicker.launch("*/*") }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (state.uploadFileName.isBlank()) "Choose Study Material File (PDF, PPT, DOC)" else state.uploadFileName,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (state.uploadFileSize > 0) {
                                        Text(FileHelper.formatFileSize(state.uploadFileSize), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.uploadTitle,
                            onValueChange = viewModel::onUploadTitleChange,
                            label = { Text("Material Title") },
                            placeholder = { Text("e.g. Unit 1 Lecture Notes") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = state.uploadDescription,
                            onValueChange = viewModel::onUploadDescChange,
                            label = { Text("Description (Optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Select Subject:", fontWeight = FontWeight.SemiBold)
                        if (state.subjects.isEmpty()) {
                            Text("No subjects found. Create a subject in the Subjects tab first.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.subjects) { sub ->
                                    FilterChip(
                                        selected = state.uploadSubject?.id == sub.id,
                                        onClick = { viewModel.onUploadSubjectSelect(sub) },
                                        label = { Text(sub.name) }
                                    )
                                }
                            }
                        }

                        if (state.uploadSubject != null) {
                            Text("Select Module (Optional - Direct Subject Note by default):", fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = state.uploadModule == null,
                                        onClick = { viewModel.onUploadModuleSelect(null) },
                                        label = { Text("Direct Subject File (No Module)") }
                                    )
                                }
                                items(state.modules) { mod ->
                                    FilterChip(
                                        selected = state.uploadModule?.id == mod.id,
                                        onClick = { viewModel.onUploadModuleSelect(mod) },
                                        label = { Text(mod.name) }
                                    )
                                }
                            }
                        }

                        Text("Visibility:", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.uploadVisibility == "all",
                                onClick = { viewModel.onUploadVisibilityChange("all") },
                                label = { Text("All Sections") }
                            )
                            FilterChip(
                                selected = state.uploadVisibility == "section",
                                onClick = { viewModel.onUploadVisibilityChange("section") },
                                label = { Text("Specific Section") }
                            )
                        }

                        if (state.uploadVisibility == "section") {
                            OutlinedTextField(
                                value = state.uploadSection,
                                onValueChange = viewModel::onUploadSectionChange,
                                label = { Text("Target Section (e.g. Section 19)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        AnimatedVisibility(visible = state.isUploading) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Adding material to cloud study library...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                LinearProgressIndicator(
                                    progress = { state.uploadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }

                        Button(
                            onClick = viewModel::publishDirectMaterial,
                            enabled = !state.isUploading && state.uploadTitle.isNotBlank() && state.uploadSubject != null && (state.uploadMode == 1 && state.uploadLinkUrl.isNotBlank() || state.uploadMode == 0 && state.uploadFileUri != null),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(if (state.uploadMode == 1) "Save Study Material" else "Publish Uploaded File")
                        }
                    }
                }
            }
        }

        // Create Dept Dialog
        if (state.showCreateDeptDialog) {
            var name by remember { mutableStateOf("") }
            var code by remember { mutableStateOf("") }
            var desc by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = viewModel::dismissCreateDeptDialog,
                title = { Text("Create Department") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code (e.g. DOMS)") }, singleLine = true)
                        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                    }
                },
                confirmButton = { Button(onClick = { viewModel.createDepartment(name, code, desc) }) { Text("Create") } },
                dismissButton = { TextButton(onClick = viewModel::dismissCreateDeptDialog) { Text("Cancel") } }
            )
        }

        // Edit Dept Dialog
        state.editingDepartment?.let { dept ->
            var name by remember { mutableStateOf(dept.name) }
            var code by remember { mutableStateOf(dept.code) }
            var desc by remember { mutableStateOf(dept.description) }
            AlertDialog(
                onDismissRequest = viewModel::dismissEditDepartment,
                title = { Text("Edit Department") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code") }, singleLine = true)
                        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                    }
                },
                confirmButton = { Button(onClick = { viewModel.saveEditDepartment(name, code, desc) }) { Text("Save Changes") } },
                dismissButton = { TextButton(onClick = viewModel::dismissEditDepartment) { Text("Cancel") } }
            )
        }

        // Create Year Dialog
        if (state.showCreateYearDialog) {
            var name by remember { mutableStateOf("") }
            var code by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = viewModel::dismissCreateYearDialog,
                title = { Text("Create Academic Year") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Year (e.g. 1st Year)") }, singleLine = true)
                        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code (e.g. Y1)") }, singleLine = true)
                    }
                },
                confirmButton = { Button(onClick = { viewModel.createAcademicYear(name, code) }) { Text("Create") } },
                dismissButton = { TextButton(onClick = viewModel::dismissCreateYearDialog) { Text("Cancel") } }
            )
        }

        // Edit Year Dialog
        state.editingAcademicYear?.let { year ->
            var name by remember { mutableStateOf(year.name) }
            var code by remember { mutableStateOf(year.code) }
            AlertDialog(
                onDismissRequest = viewModel::dismissEditAcademicYear,
                title = { Text("Edit Academic Year") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code") }, singleLine = true)
                    }
                },
                confirmButton = { Button(onClick = { viewModel.saveEditAcademicYear(name, code) }) { Text("Save Changes") } },
                dismissButton = { TextButton(onClick = viewModel::dismissEditAcademicYear) { Text("Cancel") } }
            )
        }

        // Create Subject Dialog
        if (state.showCreateSubjectDialog) {
            var name by remember { mutableStateOf("") }
            var code by remember { mutableStateOf("") }
            var deptId by remember { mutableStateOf(state.departments.firstOrNull()?.id ?: "") }
            var yearId by remember { mutableStateOf(state.academicYears.firstOrNull()?.id ?: "") }

            AlertDialog(
                onDismissRequest = viewModel::dismissCreateSubjectDialog,
                title = { Text("Create Subject") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Subject Name") }, singleLine = true)
                        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Subject Code") }, singleLine = true)

                        if (state.departments.isNotEmpty()) {
                            Text("Department:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(state.departments) { d ->
                                    FilterChip(
                                        selected = deptId == d.id,
                                        onClick = { deptId = d.id },
                                        label = { Text(d.code) }
                                    )
                                }
                            }
                        }

                        if (state.academicYears.isNotEmpty()) {
                            Text("Academic Year:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(state.academicYears) { y ->
                                    FilterChip(
                                        selected = yearId == y.id,
                                        onClick = { yearId = y.id },
                                        label = { Text(y.code) }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.createSubject(name, code, deptId, yearId) },
                        enabled = name.isNotBlank() && code.isNotBlank()
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = { TextButton(onClick = viewModel::dismissCreateSubjectDialog) { Text("Cancel") } }
            )
        }

        // Edit Subject Dialog
        state.editingSubject?.let { sub ->
            var name by remember { mutableStateOf(sub.name) }
            var code by remember { mutableStateOf(sub.code) }
            var deptId by remember { mutableStateOf(sub.departmentId) }
            var yearId by remember { mutableStateOf(sub.academicYearId) }

            AlertDialog(
                onDismissRequest = viewModel::dismissEditSubject,
                title = { Text("Edit Subject: ${sub.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Subject Name") }, singleLine = true)
                        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Subject Code") }, singleLine = true)

                        if (state.departments.isNotEmpty()) {
                            Text("Department:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(state.departments) { d ->
                                    FilterChip(selected = deptId == d.id, onClick = { deptId = d.id }, label = { Text(d.code) })
                                }
                            }
                        }

                        if (state.academicYears.isNotEmpty()) {
                            Text("Academic Year:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(state.academicYears) { y ->
                                    FilterChip(selected = yearId == y.id, onClick = { yearId = y.id }, label = { Text(y.code) })
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.saveEditSubject(name, code, deptId, yearId) },
                        enabled = name.isNotBlank() && code.isNotBlank()
                    ) {
                        Text("Save Changes")
                    }
                },
                dismissButton = { TextButton(onClick = viewModel::dismissEditSubject) { Text("Cancel") } }
            )
        }

        // Create Module Dialog
        if (state.showCreateModuleDialog) {
            var name by remember { mutableStateOf("") }
            var desc by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = viewModel::dismissCreateModuleDialog,
                title = { Text("Add Module to ${state.selectedSubjectForModule?.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Module Name (e.g. Unit 1)") }, singleLine = true)
                        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.createModule(name, desc) },
                        enabled = name.isNotBlank()
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = { TextButton(onClick = viewModel::dismissCreateModuleDialog) { Text("Cancel") } }
            )
        }

        // Edit Module Dialog
        state.editingModule?.let { mod ->
            var name by remember { mutableStateOf(mod.name) }
            var desc by remember { mutableStateOf(mod.description) }
            AlertDialog(
                onDismissRequest = viewModel::dismissEditModule,
                title = { Text("Edit Module: ${mod.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Module Name") }, singleLine = true)
                        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                    }
                },
                confirmButton = {
                    Button(onClick = { viewModel.saveEditModule(name, desc) }, enabled = name.isNotBlank()) {
                        Text("Save Changes")
                    }
                },
                dismissButton = { TextButton(onClick = viewModel::dismissEditModule) { Text("Cancel") } }
            )
        }

        // Edit Material Dialog
        state.editingMaterial?.let { mat ->
            var title by remember { mutableStateOf(mat.title) }
            var desc by remember { mutableStateOf(mat.description) }
            var visibility by remember { mutableStateOf(mat.visibility) }
            var section by remember { mutableStateOf(mat.section ?: "") }

            AlertDialog(
                onDismissRequest = viewModel::dismissEditMaterial,
                title = { Text("Edit Material: ${mat.title}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })

                        Text("Visibility:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = visibility == "all", onClick = { visibility = "all" }, label = { Text("All Sections") })
                            FilterChip(selected = visibility == "section", onClick = { visibility = "section" }, label = { Text("Specific Section") })
                        }
                        if (visibility == "section") {
                            OutlinedTextField(value = section, onValueChange = { section = it }, label = { Text("Section (e.g. Section 19)") }, singleLine = true)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.saveEditMaterial(title, desc, visibility, section) },
                        enabled = title.isNotBlank()
                    ) {
                        Text("Save Changes")
                    }
                },
                dismissButton = { TextButton(onClick = viewModel::dismissEditMaterial) { Text("Cancel") } }
            )
        }

        // Deletion confirmation dialogs
        state.deletingSubject?.let { sub ->
            ConfirmDialog(
                title = "Delete Subject?",
                message = "Are you sure you want to delete '${sub.name}' (${sub.code})? All its modules and materials will also be removed.",
                confirmText = "Delete Subject",
                isDestructive = true,
                onConfirm = viewModel::confirmDeleteSubject,
                onDismiss = viewModel::dismissDeleteSubject
            )
        }

        state.deletingModule?.let { mod ->
            ConfirmDialog(
                title = "Delete Module?",
                message = "Are you sure you want to delete '${mod.name}'? All study materials inside it will also be deleted.",
                confirmText = "Delete Module",
                isDestructive = true,
                onConfirm = viewModel::confirmDeleteModule,
                onDismiss = viewModel::dismissDeleteModule
            )
        }

        state.deletingMaterial?.let { mat ->
            ConfirmDialog(
                title = "Delete Study Material?",
                message = "Are you sure you want to delete '${mat.title}' from the study library?",
                confirmText = "Delete Material",
                isDestructive = true,
                onConfirm = viewModel::confirmDeleteMaterial,
                onDismiss = viewModel::dismissDeleteMaterial
            )
        }

        // Success / Error dialogs
        if (state.successMessage != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissSuccess,
                title = { Text("Success") },
                text = { Text(state.successMessage ?: "") },
                confirmButton = { Button(onClick = viewModel::dismissSuccess) { Text("OK") } }
            )
        }
        if (state.errorMessage != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text("Notification") },
                text = { Text(state.errorMessage ?: "") },
                confirmButton = { Button(onClick = viewModel::dismissError) { Text("OK") } }
            )
        }
    }
}
