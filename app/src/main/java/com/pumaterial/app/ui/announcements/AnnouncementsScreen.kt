package com.pumaterial.app.ui.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumaterial.app.core.components.AppTopBar
import com.pumaterial.app.core.components.EmptyStateView
import com.pumaterial.app.core.components.StatusBadge
import com.pumaterial.app.core.designsystem.WarningOrange
import com.pumaterial.app.core.designsystem.WarningOrangeContainer
import com.pumaterial.app.domain.model.Announcement
import com.pumaterial.app.domain.repository.AnnouncementRepository
import com.pumaterial.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AnnouncementsUiState(
    val announcements: List<Announcement> = emptyList(),
    val userSection: String = "",
    val isLoading: Boolean = false
)

class AnnouncementsViewModel(
    private val announcementRepository: AnnouncementRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnnouncementsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeAnnouncements()
    }

    private fun observeAnnouncements() {
        viewModelScope.launch {
            authRepository.currentUserFlow.collect { user ->
                val section = user?.section ?: ""
                _uiState.update { it.copy(userSection = section) }
                announcementRepository.observeAnnouncements(section).collect { list ->
                    _uiState.update { it.copy(announcements = list) }
                }
            }
        }
    }
}

@Composable
fun AnnouncementsScreen(
    viewModel: AnnouncementsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Announcements",
                subtitle = "Updates for ${state.userSection.ifBlank { "Class" }}",
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
            if (state.announcements.isEmpty()) {
                EmptyStateView(
                    title = "No Announcements",
                    subtitle = "There are no active announcements for your section right now.",
                    icon = Icons.Default.Campaign
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.announcements) { ann ->
                        AnnouncementItemCard(announcement = ann)
                    }
                }
            }
        }
    }
}

@Composable
fun AnnouncementItemCard(
    announcement: Announcement,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (announcement.isUrgent) WarningOrangeContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (announcement.isUrgent) {
                    StatusBadge(
                        text = "Urgent",
                        icon = Icons.Default.PriorityHigh,
                        containerColor = WarningOrangeContainer,
                        contentColor = WarningOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = announcement.message,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (announcement.targetVisibility == "section") "Section: ${announcement.targetSection}" else "All Sections",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                val dateStr = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(announcement.createdAt))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
