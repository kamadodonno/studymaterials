package com.pumaterial.app.ui.admin.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.core.components.AppTopBar
import com.pumaterial.app.domain.repository.AdminDashboardStats
import com.pumaterial.app.domain.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminDashboardUiState(
    val stats: AdminDashboardStats = AdminDashboardStats(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AdminDashboardViewModel(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminDashboardUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = adminRepository.getDashboardStats()
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false, stats = result.data) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                Resource.Loading -> Unit
            }
        }
    }
}

@Composable
fun AdminDashboardScreen(
    viewModel: AdminDashboardViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMaterials: () -> Unit,
    onNavigateToSubmissions: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onNavigateToAnnouncements: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Admin Dashboard",
                subtitle = "PU Material Management Center",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = viewModel::loadStats) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Stats")
                    }
                }
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
            // Metrics Overview Grid (2x2)
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricStatCard(
                        title = "Total Users",
                        value = "${state.stats.totalUsers}",
                        subtitle = "App installations",
                        icon = Icons.Default.People,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Pending Review",
                        value = "${state.stats.pendingSubmissionsCount}",
                        subtitle = "Student uploads",
                        icon = Icons.Default.PendingActions,
                        containerColor = Color(0xFFFEF3C7),
                        iconColor = Color(0xFFD97706),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToSubmissions() }
                    )
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricStatCard(
                        title = "Published Files",
                        value = "${state.stats.totalMaterialsCount}",
                        subtitle = "Cloud library items",
                        icon = Icons.Default.InsertDriveFile,
                        containerColor = Color(0xFFDCFCE7),
                        iconColor = Color(0xFF16A34A),
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Subjects",
                        value = "${state.stats.totalSubjectsCount}",
                        subtitle = "Curriculum courses",
                        icon = Icons.Default.MenuBook,
                        containerColor = Color(0xFFF3E8FF),
                        iconColor = Color(0xFF7C3AED),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Action Hub Navigation Cards
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🛠️ Management Modules",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                AdminNavCard(
                    title = "Academic Hierarchy & Materials",
                    subtitle = "Create/Edit Departments, Academic Years, Subjects, Modules & Upload Materials",
                    icon = Icons.Default.FolderSpecial,
                    onClick = onNavigateToMaterials
                )
            }

            item {
                AdminNavCard(
                    title = "Student Submissions Moderation",
                    subtitle = "Review, approve, edit metadata, or reject pending student study materials",
                    icon = Icons.Default.Grading,
                    badgeCount = state.stats.pendingSubmissionsCount,
                    onClick = onNavigateToSubmissions
                )
            }

            item {
                AdminNavCard(
                    title = "User Statistics, Sections & Roles",
                    subtitle = "Inspect installation records, manage section list, and assign custom roles",
                    icon = Icons.Default.ManageAccounts,
                    onClick = onNavigateToUsers
                )
            }

            item {
                AdminNavCard(
                    title = "Broadcast Announcements",
                    subtitle = "Create and broadcast notices to all sections or target specific sections",
                    icon = Icons.Default.Campaign,
                    onClick = onNavigateToAnnouncements
                )
            }

            // Section Distribution Breakdown
            if (state.stats.usersBySection.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "📊 App Installations by Section",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            state.stats.usersBySection.entries.sortedByDescending { it.value }.forEach { (section, count) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text(text = section, fontWeight = FontWeight.SemiBold)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "$count students",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = containerColor,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AdminNavCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
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
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (badgeCount > 0) {
                Badge(
                    containerColor = Color(0xFFD97706),
                    contentColor = Color.White
                ) {
                    Text("$badgeCount", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
