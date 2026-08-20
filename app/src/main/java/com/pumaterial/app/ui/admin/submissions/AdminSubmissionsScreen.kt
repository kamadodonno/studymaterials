package com.pumaterial.app.ui.admin.submissions

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.core.components.*
import com.pumaterial.app.core.file.FileHelper
import com.pumaterial.app.core.file.FileOpener
import com.pumaterial.app.core.file.OpenFileResult
import com.pumaterial.app.domain.model.Section
import com.pumaterial.app.domain.model.Submission
import com.pumaterial.app.domain.repository.AdminRepository
import com.pumaterial.app.domain.repository.MaterialRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AdminSubmissionsUiState(
    val pendingSubmissions: List<Submission> = emptyList(),
    val sections: List<Section> = emptyList(),
    val isProcessing: Boolean = false,
    val approvingSubmission: Submission? = null,
    val editTitle: String = "",
    val editDescription: String = "",
    val editVisibility: String = "all", // "all" | "section"
    val editSection: String = "",
    val rejectingSubmission: Submission? = null,
    val rejectionReason: String = "Not relevant",
    val otherRejectionReason: String = "",
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class AdminSubmissionsViewModel(
    private val adminRepository: AdminRepository,
    private val materialRepository: MaterialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminSubmissionsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observePendingSubmissions()
        loadSections()
    }

    private fun observePendingSubmissions() {
        viewModelScope.launch {
            adminRepository.observePendingSubmissions().collect { list ->
                _uiState.update { it.copy(pendingSubmissions = list) }
            }
        }
    }

    private fun loadSections() {
        viewModelScope.launch {
            materialRepository.observeSections().collect { list ->
                _uiState.update { it.copy(sections = list) }
            }
        }
    }

    fun startApproval(submission: Submission) {
        _uiState.update {
            it.copy(
                approvingSubmission = submission,
                editTitle = submission.title,
                editDescription = submission.description,
                editVisibility = "all",
                editSection = submission.submitterSection
            )
        }
    }

    fun dismissApproval() = _uiState.update { it.copy(approvingSubmission = null) }
    fun onEditTitleChange(title: String) = _uiState.update { it.copy(editTitle = title) }
    fun onEditDescriptionChange(desc: String) = _uiState.update { it.copy(editDescription = desc) }
    fun onEditVisibilityChange(vis: String) = _uiState.update { it.copy(editVisibility = vis) }
    fun onEditSectionChange(sec: String) = _uiState.update { it.copy(editSection = sec) }

    fun confirmApproval() {
        val state = _uiState.value
        val sub = state.approvingSubmission ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val result = adminRepository.approveSubmissionSafely(
                submission = sub,
                editedTitle = state.editTitle,
                editedDescription = state.editDescription,
                subjectId = sub.subjectId,
                subjectName = sub.subjectName,
                moduleId = sub.moduleId,
                moduleName = sub.moduleName,
                visibility = state.editVisibility,
                section = if (state.editVisibility == "section") state.editSection else null
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            approvingSubmission = null,
                            successMessage = "Material approved and published to cloud study library!"
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = result.message
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun startRejection(submission: Submission) {
        _uiState.update { it.copy(rejectingSubmission = submission, rejectionReason = "Not relevant", otherRejectionReason = "") }
    }

    fun dismissRejection() = _uiState.update { it.copy(rejectingSubmission = null) }
    fun onRejectionReasonChange(reason: String) = _uiState.update { it.copy(rejectionReason = reason) }
    fun onOtherRejectionReasonChange(reason: String) = _uiState.update { it.copy(otherRejectionReason = reason) }

    fun confirmRejection() {
        val state = _uiState.value
        val sub = state.rejectingSubmission ?: return
        val finalReason = if (state.rejectionReason == "Other") state.otherRejectionReason.ifBlank { "Other" } else state.rejectionReason

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val result = adminRepository.rejectSubmission(sub.id, finalReason)
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            rejectingSubmission = null,
                            successMessage = "Submission rejected and temporary file cleaned up."
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = result.message
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun dismissSuccess() = _uiState.update { it.copy(successMessage = null) }
    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSubmissionsScreen(
    viewModel: AdminSubmissionsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var sectionDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Moderate Submissions",
                subtitle = "${state.pendingSubmissions.size} pending review",
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
            if (state.pendingSubmissions.isEmpty()) {
                EmptyStateView(
                    title = "No Pending Submissions",
                    subtitle = "All student submissions have been reviewed and moderated.",
                    icon = Icons.Default.DoneAll
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.pendingSubmissions) { submission ->
                        PendingSubmissionCard(
                            submission = submission,
                            onApprove = { viewModel.startApproval(submission) },
                            onReject = { viewModel.startRejection(submission) }
                        )
                    }
                }
            }

            // Approval Modal Dialog
            state.approvingSubmission?.let { sub ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissApproval,
                    title = { Text("Approve & Publish Study Material") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                            Text(
                                text = "File: ${sub.fileName} (${FileHelper.formatFileSize(sub.fileSize)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = state.editTitle,
                                onValueChange = viewModel::onEditTitleChange,
                                label = { Text("Material Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = state.editDescription,
                                onValueChange = viewModel::onEditDescriptionChange,
                                label = { Text("Description / Notes") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text("Target Visibility:", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.editVisibility == "all",
                                    onClick = { viewModel.onEditVisibilityChange("all") },
                                    label = { Text("All Sections") }
                                )
                                FilterChip(
                                    selected = state.editVisibility == "section",
                                    onClick = { viewModel.onEditVisibilityChange("section") },
                                    label = { Text("Specific Section") }
                                )
                            }

                            if (state.editVisibility == "section") {
                                Spacer(modifier = Modifier.height(10.dp))
                                ExposedDropdownMenuBox(
                                    expanded = sectionDropdownExpanded,
                                    onExpandedChange = { sectionDropdownExpanded = !sectionDropdownExpanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = state.editSection,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Target Section") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectionDropdownExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = sectionDropdownExpanded,
                                        onDismissRequest = { sectionDropdownExpanded = false }
                                    ) {
                                        state.sections.forEach { sec ->
                                            DropdownMenuItem(
                                                text = { Text(sec.name) },
                                                onClick = {
                                                    viewModel.onEditSectionChange(sec.name)
                                                    sectionDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = viewModel::confirmApproval,
                            enabled = !state.isProcessing
                        ) {
                            if (state.isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Publish to Cloud")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissApproval) { Text("Cancel") }
                    }
                )
            }

            // Rejection Modal Dialog
            state.rejectingSubmission?.let { sub ->
                val reasons = listOf("Duplicate", "Not relevant", "Poor quality", "Wrong subject", "Other")

                AlertDialog(
                    onDismissRequest = viewModel::dismissRejection,
                    title = { Text("Reject Submission") },
                    text = {
                        Column {
                            Text("Select feedback reason for ${sub.submitterName}:", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(12.dp))

                            reasons.forEach { reason ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.onRejectionReasonChange(reason) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = state.rejectionReason == reason,
                                        onClick = { viewModel.onRejectionReasonChange(reason) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(reason)
                                }
                            }

                            if (state.rejectionReason == "Other") {
                                OutlinedTextField(
                                    value = state.otherRejectionReason,
                                    onValueChange = viewModel::onOtherRejectionReasonChange,
                                    label = { Text("Specify Reason") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = viewModel::confirmRejection,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = !state.isProcessing
                        ) {
                            Text("Reject & Delete Pending File")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissRejection) { Text("Cancel") }
                    }
                )
            }

            // Success Dialog
            if (state.successMessage != null) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissSuccess,
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = { Text("Moderation Complete") },
                    text = { Text(state.successMessage ?: "") },
                    confirmButton = { Button(onClick = viewModel::dismissSuccess) { Text("OK") } }
                )
            }

            // Error Dialog
            if (state.errorMessage != null) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissError,
                    icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Error") },
                    text = { Text(state.errorMessage ?: "") },
                    confirmButton = { Button(onClick = viewModel::dismissError) { Text("OK") } }
                )
            }
        }
    }
}

@Composable
fun PendingSubmissionCard(
    submission: Submission,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeIconBox(fileType = submission.fileType)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = submission.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${submission.fileName} • ${FileHelper.formatFileSize(submission.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Submitted by: ${submission.submitterName} (Enrollment: ${submission.submitterEnrollment})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Section: ${submission.submitterSection}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Course: ${submission.subjectName} → ${submission.moduleName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    if (submission.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Note: \"${submission.description}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reject")
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onApprove,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Approve & Publish")
                }
            }
        }
    }
}
