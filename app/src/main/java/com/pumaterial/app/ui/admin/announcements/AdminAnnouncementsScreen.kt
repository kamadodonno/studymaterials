package com.pumaterial.app.ui.admin.announcements

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.core.components.AppTopBar
import com.pumaterial.app.domain.model.Announcement
import com.pumaterial.app.domain.model.Section
import com.pumaterial.app.domain.repository.AnnouncementRepository
import com.pumaterial.app.domain.repository.MaterialRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AdminAnnouncementsUiState(
    val announcements: List<Announcement> = emptyList(),
    val sections: List<Section> = emptyList(),
    val title: String = "",
    val message: String = "",
    val targetVisibility: String = "all", // "all" | "section"
    val targetSection: String = "",
    val priority: String = "normal", // "normal" | "urgent"
    val isPosting: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class AdminAnnouncementsViewModel(
    private val announcementRepository: AnnouncementRepository,
    private val materialRepository: MaterialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminAnnouncementsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeAnnouncements()
        loadSections()
    }

    private fun observeAnnouncements() {
        viewModelScope.launch {
            announcementRepository.observeAnnouncements("").collect { list ->
                _uiState.update { it.copy(announcements = list) }
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

    fun onTitleChange(t: String) = _uiState.update { it.copy(title = t) }
    fun onMessageChange(m: String) = _uiState.update { it.copy(message = m) }
    fun onVisibilityChange(v: String) = _uiState.update { it.copy(targetVisibility = v) }
    fun onSectionChange(s: String) = _uiState.update { it.copy(targetSection = s) }
    fun onPriorityChange(p: String) = _uiState.update { it.copy(priority = p) }

    fun postAnnouncement() {
        val state = _uiState.value
        val title = state.title.trim()
        val message = state.message.trim()

        if (title.isBlank() || message.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Title and message are required.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true) }
            val result = announcementRepository.createAnnouncement(
                title = title,
                message = message,
                targetVisibility = state.targetVisibility,
                targetSection = if (state.targetVisibility == "section") state.targetSection else null,
                priority = state.priority
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isPosting = false,
                            title = "",
                            message = "",
                            successMessage = "Announcement broadcasted successfully!"
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isPosting = false, errorMessage = result.message) }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun deleteAnnouncement(id: String) {
        viewModelScope.launch {
            announcementRepository.deleteAnnouncement(id)
        }
    }

    fun dismissSuccess() = _uiState.update { it.copy(successMessage = null) }
    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAnnouncementsScreen(
    viewModel: AdminAnnouncementsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var sectionDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Broadcast Notice",
                subtitle = "Send notices to students across sections",
                onNavigateBack = onNavigateBack
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
            // New Announcement Form Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "📢 Post Announcement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = state.title,
                            onValueChange = viewModel::onTitleChange,
                            label = { Text("Announcement Title") },
                            placeholder = { Text("e.g. Internal Exam Syllabus Uploaded") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = state.message,
                            onValueChange = viewModel::onMessageChange,
                            label = { Text("Message Body") },
                            placeholder = { Text("Write the notice details here...") },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Target Audience:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.targetVisibility == "all",
                                onClick = { viewModel.onVisibilityChange("all") },
                                label = { Text("All Sections") }
                            )
                            FilterChip(
                                selected = state.targetVisibility == "section",
                                onClick = { viewModel.onVisibilityChange("section") },
                                label = { Text("Specific Section") }
                            )
                        }

                        if (state.targetVisibility == "section") {
                            ExposedDropdownMenuBox(
                                expanded = sectionDropdownExpanded,
                                onExpandedChange = { sectionDropdownExpanded = !sectionDropdownExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = state.targetSection,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select Target Section") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectionDropdownExpanded) },
                                    shape = RoundedCornerShape(12.dp),
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
                                                viewModel.onSectionChange(sec.name)
                                                sectionDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Text("Priority:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.priority == "normal",
                                onClick = { viewModel.onPriorityChange("normal") },
                                label = { Text("Normal") }
                            )
                            FilterChip(
                                selected = state.priority == "urgent",
                                onClick = { viewModel.onPriorityChange("urgent") },
                                label = { Text("Urgent Notice") }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = viewModel::postAnnouncement,
                            enabled = !state.isPosting,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (state.isPosting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Broadcast Announcement")
                            }
                        }
                    }
                }
            }

            // Existing Announcements List
            item {
                Text(
                    text = "Active Broadcasts (${state.announcements.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(state.announcements) { ann ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ann.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(ann.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (ann.targetVisibility == "section") "Section: ${ann.targetSection}" else "All Sections",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = { viewModel.deleteAnnouncement(ann.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Success Dialog
        if (state.successMessage != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissSuccess,
                title = { Text("Success") },
                text = { Text(state.successMessage ?: "") },
                confirmButton = { Button(onClick = viewModel::dismissSuccess) { Text("OK") } }
            )
        }

        // Error Dialog
        if (state.errorMessage != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text("Error") },
                text = { Text(state.errorMessage ?: "") },
                confirmButton = { Button(onClick = viewModel::dismissError) { Text("OK") } }
            )
        }
    }
}
