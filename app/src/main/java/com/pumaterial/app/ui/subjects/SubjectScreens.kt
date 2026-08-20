package com.pumaterial.app.ui.subjects

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumaterial.app.core.components.*
import com.pumaterial.app.core.file.FileOpener
import com.pumaterial.app.core.file.OpenFileResult
import com.pumaterial.app.data.local.datastore.UserSessionManager
import com.pumaterial.app.domain.model.*
import com.pumaterial.app.domain.repository.DownloadRepository
import com.pumaterial.app.domain.repository.MaterialRepository
import com.pumaterial.app.domain.repository.PersonalFolderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SubjectsUiState(
    val academicYears: List<AcademicYear> = emptyList(),
    val departments: List<Department> = emptyList(),
    val selectedYearId: String? = null,
    val selectedDeptId: String? = null,
    val subjects: List<Subject> = emptyList(),
    val customOrderIds: List<String> = emptyList(),
    val showReorderDialog: Boolean = false,
    val isLoading: Boolean = false
) {
    val sortedSubjects: List<Subject>
        get() {
            if (customOrderIds.isEmpty()) return subjects
            val orderMap = customOrderIds.mapIndexed { index, id -> id to index }.toMap()
            return subjects.sortedWith(compareBy({ orderMap[it.id] ?: Int.MAX_VALUE }, { it.order }))
        }
}

data class SubjectDetailUiState(
    val subjectId: String = "",
    val subjectName: String = "",
    val generalMaterials: List<Material> = emptyList(), // Materials directly in Subject without a unit
    val modules: List<Module> = emptyList(),
    val materialsByModule: Map<String, List<Material>> = emptyMap(),
    val expandedModuleIds: Set<String> = emptySet(),
    val downloadingMaterialId: String? = null,
    val downloadProgress: Float = 0f,
    val missingAppInfo: Triple<String, String, String>? = null,
    val personalFolders: List<PersonalFolder> = emptyList(),
    val showFolderSelectorForMaterial: Material? = null,
    val errorMessage: String? = null
)

class SubjectViewModel(
    private val materialRepository: MaterialRepository,
    private val downloadRepository: DownloadRepository,
    private val personalFolderRepository: PersonalFolderRepository,
    private val sessionManager: UserSessionManager
) : ViewModel() {

    private val _subjectsState = MutableStateFlow(SubjectsUiState())
    val subjectsState = _subjectsState.asStateFlow()

    private val _detailState = MutableStateFlow(SubjectDetailUiState())
    val detailState = _detailState.asStateFlow()

    init {
        loadFilterData()
        observeCustomOrder()
    }

    private fun observeCustomOrder() {
        viewModelScope.launch {
            sessionManager.customSubjectOrderFlow.collect { order ->
                _subjectsState.update { it.copy(customOrderIds = order) }
            }
        }
    }

    private fun loadFilterData() {
        viewModelScope.launch {
            materialRepository.observeAcademicYears().collect { years ->
                _subjectsState.update { it.copy(academicYears = years) }
            }
        }
        viewModelScope.launch {
            materialRepository.observeDepartments().collect { depts ->
                _subjectsState.update { it.copy(departments = depts) }
            }
        }
        observeFilteredSubjects()
    }

    private var subjectsJob: kotlinx.coroutines.Job? = null

    private fun observeFilteredSubjects() {
        subjectsJob?.cancel()
        subjectsJob = viewModelScope.launch {
            val state = _subjectsState.value
            materialRepository.observeSubjects(state.selectedDeptId, state.selectedYearId).collect { list ->
                _subjectsState.update { it.copy(subjects = list) }
            }
        }
    }

    fun selectAcademicYear(yearId: String?) {
        _subjectsState.update { it.copy(selectedYearId = if (it.selectedYearId == yearId) null else yearId) }
        observeFilteredSubjects()
    }

    fun selectDepartment(deptId: String?) {
        _subjectsState.update { it.copy(selectedDeptId = if (it.selectedDeptId == deptId) null else deptId) }
        observeFilteredSubjects()
    }

    // Reorder Dialog controls
    fun showReorderDialog() = _subjectsState.update { it.copy(showReorderDialog = true) }
    fun dismissReorderDialog() = _subjectsState.update { it.copy(showReorderDialog = false) }

    fun moveSubjectUp(subjectId: String) {
        val currentList = _subjectsState.value.sortedSubjects.map { it.id }.toMutableList()
        val index = currentList.indexOf(subjectId)
        if (index > 0) {
            val temp = currentList[index]
            currentList[index] = currentList[index - 1]
            currentList[index - 1] = temp
            viewModelScope.launch {
                sessionManager.saveCustomSubjectOrder(currentList)
            }
        }
    }

    fun moveSubjectDown(subjectId: String) {
        val currentList = _subjectsState.value.sortedSubjects.map { it.id }.toMutableList()
        val index = currentList.indexOf(subjectId)
        if (index != -1 && index < currentList.size - 1) {
            val temp = currentList[index]
            currentList[index] = currentList[index + 1]
            currentList[index + 1] = temp
            viewModelScope.launch {
                sessionManager.saveCustomSubjectOrder(currentList)
            }
        }
    }

    fun resetSubjectOrder() {
        viewModelScope.launch {
            sessionManager.saveCustomSubjectOrder(emptyList())
        }
    }

    fun initSubjectDetail(subjectId: String, subjectName: String) {
        _detailState.update { it.copy(subjectId = subjectId, subjectName = subjectName) }

        // Observe general direct subject materials (where moduleId is blank)
        viewModelScope.launch {
            materialRepository.observeMaterials("").collect { allMats ->
                val generals = allMats.filter { it.subjectId == subjectId && it.moduleId.isBlank() }
                _detailState.update { it.copy(generalMaterials = generals) }
            }
        }

        viewModelScope.launch {
            materialRepository.observeModules(subjectId).collect { modules ->
                _detailState.update { it.copy(modules = modules) }
                _detailState.update { it.copy(expandedModuleIds = modules.map { m -> m.id }.toSet()) }

                modules.forEach { module ->
                    launch {
                        materialRepository.observeMaterials(module.id).collect { materials ->
                            _detailState.update { current ->
                                val map = current.materialsByModule.toMutableMap()
                                map[module.id] = materials
                                current.copy(materialsByModule = map)
                            }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            personalFolderRepository.observeFolders().collect { folders ->
                _detailState.update { it.copy(personalFolders = folders) }
            }
        }
    }

    fun toggleModuleExpansion(moduleId: String) {
        _detailState.update { state ->
            val set = state.expandedModuleIds.toMutableSet()
            if (set.contains(moduleId)) set.remove(moduleId) else set.add(moduleId)
            state.copy(expandedModuleIds = set)
        }
    }

    fun downloadMaterial(material: Material) {
        viewModelScope.launch {
            _detailState.update { it.copy(downloadingMaterialId = material.id, downloadProgress = 0f) }
            val result = downloadRepository.downloadOrUpdateMaterial(
                material = material,
                onProgress = { progress ->
                    _detailState.update { it.copy(downloadProgress = progress) }
                }
            )
            _detailState.update { it.copy(downloadingMaterialId = null, downloadProgress = 0f) }
            if (result is com.pumaterial.app.core.common.Resource.Error) {
                _detailState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun openMaterial(context: Context, material: Material, onOpenPdfInApp: (String, String) -> Unit) {
        viewModelScope.launch {
            val file = downloadRepository.getLocalFileForMaterial(material.id, material.fileName)
            if (file == null || !file.exists() || file.length() == 0L) {
                downloadMaterial(material)
                return@launch
            }

            if (material.fileType.lowercase() == "pdf") {
                onOpenPdfInApp(file.absolutePath, material.title)
            } else {
                when (val openResult = FileOpener.openFile(context, file, material.fileType)) {
                    is OpenFileResult.Success -> Unit
                    is OpenFileResult.NoAppInstalled -> {
                        _detailState.update {
                            it.copy(
                                missingAppInfo = Triple(
                                    openResult.fileType,
                                    openResult.suggestedAppName,
                                    openResult.suggestedPackageName
                                )
                            )
                        }
                    }
                    is OpenFileResult.Error -> {
                        _detailState.update { it.copy(errorMessage = openResult.message) }
                    }
                }
            }
        }
    }

    fun deleteLocalMaterial(material: Material) {
        viewModelScope.launch {
            downloadRepository.deleteDownloadedMaterial(material.id, material.fileName)
        }
    }

    fun showAddToFolderDialog(material: Material) {
        _detailState.update { it.copy(showFolderSelectorForMaterial = material) }
    }

    fun addMaterialToPersonalFolder(folderId: String, material: Material) {
        viewModelScope.launch {
            personalFolderRepository.addItemToFolder(folderId, material)
            _detailState.update { it.copy(showFolderSelectorForMaterial = null) }
        }
    }

    fun dismissFolderSelector() = _detailState.update { it.copy(showFolderSelectorForMaterial = null) }
    fun dismissMissingAppDialog() = _detailState.update { it.copy(missingAppInfo = null) }
    fun dismissErrorMessage() = _detailState.update { it.copy(errorMessage = null) }
}

@Composable
fun SubjectListScreen(
    viewModel: SubjectViewModel,
    onNavigateToSubjectDetail: (String, String) -> Unit
) {
    val state by viewModel.subjectsState.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Subjects",
                subtitle = "Select a subject to access study files",
                actions = {
                    IconButton(onClick = viewModel::showReorderDialog) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Reorder Subjects",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Filter by Department and Academic Year
            if (state.departments.isNotEmpty() || state.academicYears.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (state.departments.isNotEmpty()) {
                        Text(
                            text = "Department",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = state.selectedDeptId == null,
                                    onClick = { viewModel.selectDepartment(null) },
                                    label = { Text("All Depts") }
                                )
                            }
                            items(state.departments) { dept ->
                                FilterChip(
                                    selected = state.selectedDeptId == dept.id,
                                    onClick = { viewModel.selectDepartment(dept.id) },
                                    label = { Text(dept.code) }
                                )
                            }
                        }
                    }

                    if (state.academicYears.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Academic Year",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = state.selectedYearId == null,
                                    onClick = { viewModel.selectAcademicYear(null) },
                                    label = { Text("All Years") }
                                )
                            }
                            items(state.academicYears) { year ->
                                FilterChip(
                                    selected = state.selectedYearId == year.id,
                                    onClick = { viewModel.selectAcademicYear(year.id) },
                                    label = { Text(year.code) }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }

            if (state.sortedSubjects.isEmpty()) {
                EmptyStateView(
                    title = "No Subjects Available",
                    subtitle = "No subjects match your selected filters.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.sortedSubjects) { subject ->
                        SubjectRowCard(
                            subject = subject,
                            onClick = { onNavigateToSubjectDetail(subject.id, subject.name) }
                        )
                    }
                }
            }
        }

        // Reorder Subjects Dialog with Live Drag & Drop
        if (state.showReorderDialog) {
            var draggingSubjectId by remember { mutableStateOf<String?>(null) }
            var dragAccumulator by remember { mutableFloatStateOf(0f) }
            val thresholdPx = with(LocalDensity.current) { 44.dp.toPx() }

            AlertDialog(
                onDismissRequest = viewModel::dismissReorderDialog,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DragIndicator, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reorder Subjects")
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Touch and drag the handle (⋮⋮) to arrange subjects in your preferred order.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.sortedSubjects.size) { index ->
                                val sub = state.sortedSubjects[index]
                                val isBeingDragged = draggingSubjectId == sub.id

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isBeingDragged) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = if (isBeingDragged) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    shadowElevation = if (isBeingDragged) 8.dp else 0.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        // Draggable Handle with Live Touch Drag
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .pointerInput(sub.id) {
                                                    detectVerticalDragGestures(
                                                        onDragStart = {
                                                            draggingSubjectId = sub.id
                                                            dragAccumulator = 0f
                                                        },
                                                        onDragEnd = {
                                                            draggingSubjectId = null
                                                            dragAccumulator = 0f
                                                        },
                                                        onDragCancel = {
                                                            draggingSubjectId = null
                                                            dragAccumulator = 0f
                                                        },
                                                        onVerticalDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragAccumulator += dragAmount
                                                            if (dragAccumulator > thresholdPx) {
                                                                viewModel.moveSubjectDown(sub.id)
                                                                dragAccumulator = 0f
                                                            } else if (dragAccumulator < -thresholdPx) {
                                                                viewModel.moveSubjectUp(sub.id)
                                                                dragAccumulator = 0f
                                                            }
                                                        }
                                                    )
                                                }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Drag to change place",
                                                tint = if (isBeingDragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sub.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = sub.code,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Quick Up / Down Arrows
                                        IconButton(
                                            onClick = { viewModel.moveSubjectUp(sub.id) },
                                            enabled = index > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Move Up",
                                                tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.moveSubjectDown(sub.id) },
                                            enabled = index < state.sortedSubjects.size - 1,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Move Down",
                                                tint = if (index < state.sortedSubjects.size - 1) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = viewModel::dismissReorderDialog) {
                        Text("Done")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::resetSubjectOrder) {
                        Text("Reset Default")
                    }
                }
            )
        }
    }
}

@Composable
fun SubjectDetailScreen(
    subjectId: String,
    subjectName: String,
    viewModel: SubjectViewModel,
    onNavigateBack: () -> Unit,
    onOpenPdfInApp: (String, String) -> Unit
) {
    val state by viewModel.detailState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(subjectId) {
        viewModel.initSubjectDetail(subjectId, subjectName)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = state.subjectName.ifBlank { subjectName },
                subtitle = "Modules & Study Files",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Direct Subject Materials / General Practice Notes
                if (state.generalMaterials.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("General & Practice Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                state.generalMaterials.forEach { mat ->
                                    MaterialItemCard(
                                        material = mat,
                                        isDownloading = state.downloadingMaterialId == mat.id,
                                        downloadProgress = state.downloadProgress,
                                        onOpenClick = { viewModel.openMaterial(context, mat, onOpenPdfInApp) },
                                        onDownloadClick = { viewModel.downloadMaterial(mat) },
                                        onDeleteLocalClick = { viewModel.deleteLocalMaterial(mat) },
                                        onAddToFolderClick = { viewModel.showAddToFolderDialog(mat) },
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.modules.isEmpty() && state.generalMaterials.isEmpty()) {
                    item {
                        EmptyStateView(
                            title = "No Content Yet",
                            subtitle = "Study materials and modules will appear here once published.",
                            modifier = Modifier.fillMaxWidth().height(260.dp)
                        )
                    }
                } else {
                    items(state.modules) { module ->
                        val materials = state.materialsByModule[module.id] ?: emptyList()
                        val isExpanded = state.expandedModuleIds.contains(module.id)

                        ModuleExpansionCard(
                            module = module,
                            materials = materials,
                            isExpanded = isExpanded,
                            downloadingMaterialId = state.downloadingMaterialId,
                            downloadProgress = state.downloadProgress,
                            onToggleExpand = { viewModel.toggleModuleExpansion(module.id) },
                            onOpenMaterial = { mat ->
                                viewModel.openMaterial(context, mat, onOpenPdfInApp)
                            },
                            onDownloadMaterial = { mat ->
                                viewModel.downloadMaterial(mat)
                            },
                            onDeleteLocalMaterial = { mat ->
                                viewModel.deleteLocalMaterial(mat)
                            },
                            onAddToFolder = { mat ->
                                viewModel.showAddToFolderDialog(mat)
                            }
                        )
                    }
                }
            }

            // Error banner
            state.errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::dismissErrorMessage) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.inversePrimary)
                        }
                    }
                ) {
                    Text(error)
                }
            }

            // Add to Personal Folder selector dialog
            state.showFolderSelectorForMaterial?.let { material ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissFolderSelector,
                    title = { Text("Save to Personal Folder") },
                    text = {
                        Column {
                            Text(
                                text = "Choose a private folder to save '${material.title}' for quick offline access:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (state.personalFolders.isEmpty()) {
                                Text(
                                    text = "No personal folders created yet. Go to Downloads & Folders to create one.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.heightIn(max = 240.dp)
                                ) {
                                    items(state.personalFolders) { folder ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.addMaterialToPersonalFolder(folder.folderId, material)
                                                }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(12.dp)
                                            ) {
                                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(folder.folderName, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissFolderSelector) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Missing app dialog
            state.missingAppInfo?.let { (fileType, appName, pkgName) ->
                FallbackAppDialog(
                    fileType = fileType,
                    suggestedAppName = appName,
                    suggestedPackageName = pkgName,
                    onDismiss = viewModel::dismissMissingAppDialog
                )
            }
        }
    }
}

@Composable
fun ModuleExpansionCard(
    module: Module,
    materials: List<Material>,
    isExpanded: Boolean,
    downloadingMaterialId: String?,
    downloadProgress: Float,
    onToggleExpand: () -> Unit,
    onOpenMaterial: (Material) -> Unit,
    onDownloadMaterial: (Material) -> Unit,
    onDeleteLocalMaterial: (Material) -> Unit,
    onAddToFolder: (Material) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Module Header (Clickable to toggle collapse)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${module.order}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (module.description.isNotBlank()) {
                        Text(
                            text = module.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "${materials.size} files",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Collapsible Materials List
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (materials.isEmpty()) {
                        Text(
                            text = "No study files uploaded in this module yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    } else {
                        materials.forEach { material ->
                            MaterialItemCard(
                                material = material,
                                isDownloading = downloadingMaterialId == material.id,
                                downloadProgress = downloadProgress,
                                onOpenClick = { onOpenMaterial(material) },
                                onDownloadClick = { onDownloadMaterial(material) },
                                onDeleteLocalClick = { onDeleteLocalMaterial(material) },
                                onAddToFolderClick = { onAddToFolder(material) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
fun SubjectRowCard(
    subject: Subject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${subject.code} • ${subject.moduleCount} Modules • ${subject.materialCount} Materials",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
