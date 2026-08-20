package com.pumaterial.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumaterial.app.core.components.*
import com.pumaterial.app.core.network.ConnectivityObserver
import com.pumaterial.app.data.local.datastore.UserSessionManager
import com.pumaterial.app.domain.model.*
import com.pumaterial.app.domain.repository.*
import com.pumaterial.app.ui.subjects.SubjectRowCard
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val currentUser: User? = null,
    val isOnline: Boolean = true,
    val announcements: List<Announcement> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val customOrderIds: List<String> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
) {
    val sortedSubjects: List<Subject>
        get() {
            if (customOrderIds.isEmpty()) return subjects
            val orderMap = customOrderIds.mapIndexed { index, id -> id to index }.toMap()
            return subjects.sortedWith(compareBy({ orderMap[it.id] ?: Int.MAX_VALUE }, { it.order }))
        }
}

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val materialRepository: MaterialRepository,
    private val announcementRepository: AnnouncementRepository,
    private val sessionManager: UserSessionManager,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeNetwork()
        observeUserAndData()
        observeCustomOrder()
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    private fun observeCustomOrder() {
        viewModelScope.launch {
            sessionManager.customSubjectOrderFlow.collect { order ->
                _uiState.update { it.copy(customOrderIds = order) }
            }
        }
    }

    private fun observeUserAndData() {
        viewModelScope.launch {
            authRepository.currentUserFlow.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
                if (user != null) {
                    observeAnnouncements(user.section)
                    observeSubjects()
                }
            }
        }
    }

    private fun observeAnnouncements(section: String) {
        viewModelScope.launch {
            announcementRepository.observeAnnouncements(section).collect { list ->
                _uiState.update { it.copy(announcements = list) }
            }
        }
    }

    private fun observeSubjects() {
        viewModelScope.launch {
            materialRepository.observeSubjects(null, null).collect { list ->
                _uiState.update { it.copy(subjects = list) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            authRepository.updateLastActive()
            kotlinx.coroutines.delay(600)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun dismissErrorMessage() = _uiState.update { it.copy(errorMessage = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSubject: (String, String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToAnnouncements: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold { paddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Offline status bar
                if (!state.isOnline) {
                    item { OfflineBanner() }
                }

                // Header with Google-Style Profile Avatar on Top-Right
                item {
                    HomeHeader(
                        user = state.currentUser,
                        onSearchClick = onNavigateToSearch,
                        onProfileClick = onNavigateToProfile
                    )
                }

                // Section Announcements Banner
                if (state.announcements.isNotEmpty()) {
                    item {
                        SectionAnnouncementsCard(
                            announcements = state.announcements,
                            userSection = state.currentUser?.displaySection ?: "Class",
                            onViewAllClick = onNavigateToAnnouncements
                        )
                    }
                }

                // Subjects Section Header
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "📚 Study Subjects",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${state.subjects.size} subjects",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (state.sortedSubjects.isEmpty()) {
                    item {
                        EmptyStateView(
                            title = "No Subjects Yet",
                            subtitle = "Subjects will appear here once published by faculty.",
                            modifier = Modifier.height(180.dp)
                        )
                    }
                } else {
                    items(state.sortedSubjects) { subject ->
                        SubjectRowCard(
                            subject = subject,
                            onClick = { onNavigateToSubject(subject.id, subject.name) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        // Error message dialog
        state.errorMessage?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::dismissErrorMessage,
                title = { Text("Notification") },
                text = { Text(error) },
                confirmButton = {
                    Button(onClick = viewModel::dismissErrorMessage) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
fun HomeHeader(
    user: User?,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Welcome, ${user?.name?.ifBlank { "Student" } ?: "Student"} 👋",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${user?.displaySection ?: "Section"} • Enrollment: ${user?.enrollmentNumber ?: "PU"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Google-Style Top-Right Profile Avatar Circle
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onProfileClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val initial = user?.name?.firstOrNull()?.uppercase() ?: "S"
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar Action Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSearchClick() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Search study material (title, subject, module)...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SectionAnnouncementsCard(
    announcements: List<Announcement>,
    userSection: String,
    onViewAllClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clickable { onViewAllClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "For $userSection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val latest = announcements.firstOrNull()
            if (latest != null) {
                Text(
                    text = latest.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = latest.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
