package com.pumaterial.app.ui.submit

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.pumaterial.app.core.common.Constants
import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.core.components.AppTopBar
import com.pumaterial.app.core.components.FileTypeIconBox
import com.pumaterial.app.core.components.SubmissionStatusBadge
import com.pumaterial.app.core.file.FileHelper
import com.pumaterial.app.domain.model.Module
import com.pumaterial.app.domain.model.Subject
import com.pumaterial.app.domain.model.Submission
import com.pumaterial.app.domain.repository.MaterialRepository
import com.pumaterial.app.domain.repository.SubmissionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SubmitMaterialUiState(
    val selectedFileUri: Uri? = null,
    val selectedFileName: String = "",
    val selectedFileType: String = "",
    val selectedFileSize: Long = 0L,
    val title: String = "",
    val description: String = "",
    val subjects: List<Subject> = emptyList(),
    val selectedSubject: Subject? = null,
    val modules: List<Module> = emptyList(),
    val selectedModule: Module? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val mySubmissions: List<Submission> = emptyList(),
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class SubmitMaterialViewModel(
    private val submissionRepository: SubmissionRepository,
    private val materialRepository: MaterialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubmitMaterialUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSubjects()
        observeMySubmissions()
    }

    private fun loadSubjects() {
        viewModelScope.launch {
            materialRepository.observeSubjects(null, null).collect { list ->
                _uiState.update { it.copy(subjects = list) }
            }
        }
    }

    private fun observeMySubmissions() {
        viewModelScope.launch {
            submissionRepository.observeMySubmissions().collect { list ->
                _uiState.update { it.copy(mySubmissions = list) }
            }
        }
    }

    fun onFileSelected(uri: Uri, name: String, size: Long) {
        val ext = FileHelper.getFileExtension(name)
        if (ext !in Constants.SUPPORTED_EXTENSIONS) {
            _uiState.update { it.copy(errorMessage = "Unsupported file type .$ext. Only PDF, PPT, PPTX, DOC, and DOCX are allowed.") }
            return
        }
        if (size > Constants.DEFAULT_MAX_UPLOAD_SIZE_BYTES) {
            _uiState.update { it.copy(errorMessage = "File size (${FileHelper.formatFileSize(size)}) exceeds the 50 MB limit.") }
            return
        }

        _uiState.update {
            it.copy(
                selectedFileUri = uri,
                selectedFileName = name,
                selectedFileType = ext,
                selectedFileSize = size,
                title = if (it.title.isBlank()) name.substringBeforeLast('.') else it.title,
                errorMessage = null
            )
        }
    }

    fun onTitleChange(title: String) = _uiState.update { it.copy(title = title, errorMessage = null) }
    fun onDescriptionChange(desc: String) = _uiState.update { it.copy(description = desc) }

    fun onSubjectSelect(subject: Subject) {
        _uiState.update { it.copy(selectedSubject = subject, selectedModule = null) }
        viewModelScope.launch {
            materialRepository.observeModules(subject.id).collect { list ->
                _uiState.update { it.copy(modules = list) }
            }
        }
    }

    fun onModuleSelect(module: Module) = _uiState.update { it.copy(selectedModule = module) }

    fun submitMaterial() {
        val state = _uiState.value
        val uri = state.selectedFileUri
        val subject = state.selectedSubject
        val module = state.selectedModule

        if (uri == null) {
            _uiState.update { it.copy(errorMessage = "Please choose a study file to submit.") }
            return
        }
        if (state.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a title for the material.") }
            return
        }
        if (subject == null) {
            _uiState.update { it.copy(errorMessage = "Please select the corresponding subject.") }
            return
        }
        if (module == null) {
            _uiState.update { it.copy(errorMessage = "Please select the module/chapter.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, uploadProgress = 0f, errorMessage = null) }

            val result = submissionRepository.submitStudyMaterial(
                fileUri = uri,
                fileName = state.selectedFileName,
                fileType = state.selectedFileType,
                fileSize = state.selectedFileSize,
                title = state.title,
                description = state.description,
                subjectId = subject.id,
                subjectName = subject.name,
                moduleId = module.id,
                moduleName = module.name,
                onProgress = { progress ->
                    _uiState.update { it.copy(uploadProgress = progress) }
                }
            )

            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            selectedFileUri = null,
                            selectedFileName = "",
                            selectedFileType = "",
                            selectedFileSize = 0L,
                            title = "",
                            description = "",
                            selectedSubject = null,
                            selectedModule = null,
                            successMessage = "Material submitted successfully for administrator review!"
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isUploading = false, errorMessage = result.message) }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun deletePendingSubmission(submissionId: String) {
        viewModelScope.launch {
            submissionRepository.deleteMyPendingSubmission(submissionId)
        }
    }

    fun dismissSuccess() = _uiState.update { it.copy(successMessage = null) }
    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitMaterialScreen(
    viewModel: SubmitMaterialViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var subjectDropdownExpanded by remember { mutableStateOf(false) }
    var moduleDropdownExpanded by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "document"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
            viewModel.onFileSelected(uri, fileName, fileSize)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Submit Material",
                subtitle = "Share useful study notes with your class"
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Upload Banner / File Picker Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            filePickerLauncher.launch("*/*")
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        if (state.selectedFileUri != null) {
                            FileTypeIconBox(fileType = state.selectedFileType)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = state.selectedFileName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${state.selectedFileType.uppercase()} • ${FileHelper.formatFileSize(state.selectedFileSize)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap to choose a different file",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Choose Study File",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Supported: PDF, PPT, PPTX, DOC, DOCX (Max 50 MB)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Material Metadata Form
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    label = { Text("Material Title") },
                    placeholder = { Text("e.g. Unit 3 Revision Notes") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Subject Selector Dropdown
            item {
                ExposedDropdownMenuBox(
                    expanded = subjectDropdownExpanded,
                    onExpandedChange = { subjectDropdownExpanded = !subjectDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = state.selectedSubject?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Subject") },
                        placeholder = { Text("Choose subject ▼") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectDropdownExpanded) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = subjectDropdownExpanded,
                        onDismissRequest = { subjectDropdownExpanded = false }
                    ) {
                        state.subjects.forEach { subject ->
                            DropdownMenuItem(
                                text = { Text(subject.name) },
                                onClick = {
                                    viewModel.onSubjectSelect(subject)
                                    subjectDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Module Selector Dropdown
            if (state.selectedSubject != null) {
                item {
                    ExposedDropdownMenuBox(
                        expanded = moduleDropdownExpanded,
                        onExpandedChange = { moduleDropdownExpanded = !moduleDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = state.selectedModule?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Module / Chapter") },
                            placeholder = { Text("Choose module ▼") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = moduleDropdownExpanded) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = moduleDropdownExpanded,
                            onDismissRequest = { moduleDropdownExpanded = false }
                        ) {
                            state.modules.forEach { module ->
                                DropdownMenuItem(
                                    text = { Text(module.name) },
                                    onClick = {
                                        viewModel.onModuleSelect(module)
                                        moduleDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Optional Note
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("Optional Note for Reviewer") },
                    placeholder = { Text("e.g. Contains solved past year exam questions") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Upload Progress Bar
            item {
                AnimatedVisibility(visible = state.isUploading) {
                    Column {
                        LinearProgressIndicator(
                            progress = { state.uploadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Uploading... ${(state.uploadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Submit Button
            item {
                Button(
                    onClick = viewModel::submitMaterial,
                    enabled = !state.isUploading && state.selectedFileUri != null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isUploading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submit for Review", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // My Past Submissions Section
            if (state.mySubmissions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "📋 My Submissions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.mySubmissions) { sub ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = sub.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                SubmissionStatusBadge(status = sub.status)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "${sub.subjectName} • ${sub.moduleName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (sub.isRejected && !sub.rejectionReason.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Feedback: ${sub.rejectionReason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            if (sub.isPending) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { viewModel.deletePendingSubmission(sub.id) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cancel Submission")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Success Alert Dialog
        if (state.successMessage != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissSuccess,
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Submission Sent") },
                text = { Text(state.successMessage ?: "") },
                confirmButton = {
                    Button(onClick = viewModel::dismissSuccess) { Text("Done") }
                }
            )
        }

        // Error Alert Dialog
        if (state.errorMessage != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Submission Error") },
                text = { Text(state.errorMessage ?: "") },
                confirmButton = {
                    Button(onClick = viewModel::dismissError) { Text("OK") }
                }
            )
        }
    }
}
