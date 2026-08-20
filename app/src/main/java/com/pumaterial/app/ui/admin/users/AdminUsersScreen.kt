package com.pumaterial.app.ui.admin.users

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pumaterial.app.core.components.AppTopBar
import com.pumaterial.app.core.components.ConfirmDialog
import com.pumaterial.app.core.components.StatusBadge
import com.pumaterial.app.domain.model.Section
import com.pumaterial.app.domain.model.User
import com.pumaterial.app.domain.repository.AdminRepository
import com.pumaterial.app.domain.repository.MaterialRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AdminUsersUiState(
    val selectedTab: Int = 0, // 0: Users, 1: Sections, 2: Roles
    val users: List<User> = emptyList(),
    val sections: List<Section> = emptyList(),
    val selectedSectionFilter: String? = null,
    val searchQuery: String = "",
    val showAddSectionDialog: Boolean = false,
    val newSectionName: String = "",
    val editingUserRole: User? = null,
    val selectedRoleForEdit: String = "student",
    val permissionsForEdit: Set<String> = emptySet(),
    val deletingUser: User? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
) {
    val filteredUsers: List<User>
        get() {
            var list = users
            if (!selectedSectionFilter.isNullOrBlank()) {
                list = list.filter {
                    it.section.equals(selectedSectionFilter, ignoreCase = true) ||
                    it.displaySection.contains(selectedSectionFilter, ignoreCase = true)
                }
            }
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                list = list.filter {
                    it.name.lowercase().contains(q) ||
                    it.enrollmentNumber.lowercase().contains(q) ||
                    it.section.lowercase().contains(q)
                }
            }
            return list
        }
}

class AdminUsersViewModel(
    private val adminRepository: AdminRepository,
    private val materialRepository: MaterialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUsersUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeUsers()
        observeSections()
    }

    private fun observeUsers() {
        viewModelScope.launch {
            adminRepository.observeUsers().collect { list ->
                _uiState.update { it.copy(users = list) }
            }
        }
    }

    private fun observeSections() {
        viewModelScope.launch {
            materialRepository.observeSections().collect { list ->
                val effective = if (list.isEmpty()) com.pumaterial.app.core.common.Constants.DEFAULT_SECTIONS else list
                _uiState.update { it.copy(sections = effective) }
            }
        }
    }

    fun selectTab(tab: Int) = _uiState.update { it.copy(selectedTab = tab) }

    fun selectSectionFilter(sectionName: String?) {
        _uiState.update { it.copy(selectedSectionFilter = if (it.selectedSectionFilter == sectionName) null else sectionName) }
    }

    fun onSearchQueryChange(query: String) = _uiState.update { it.copy(searchQuery = query) }

    // Section Management
    fun showAddSectionDialog() = _uiState.update { it.copy(showAddSectionDialog = true, newSectionName = "") }
    fun dismissAddSectionDialog() = _uiState.update { it.copy(showAddSectionDialog = false) }
    fun onNewSectionNameChange(name: String) = _uiState.update { it.copy(newSectionName = name) }

    fun addSection() {
        val name = _uiState.value.newSectionName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val order = _uiState.value.sections.size + 1
            adminRepository.createSection(name, order)
            _uiState.update { it.copy(showAddSectionDialog = false, successMessage = "Section $name added successfully!") }
        }
    }

    fun toggleSectionActive(section: Section) {
        viewModelScope.launch {
            adminRepository.updateSectionActiveState(section.id, !section.isActive)
        }
    }

    // Role Assignment
    fun startEditUserRole(user: User) {
        _uiState.update {
            it.copy(
                editingUserRole = user,
                selectedRoleForEdit = user.role,
                permissionsForEdit = user.permissions.toSet()
            )
        }
    }

    fun dismissEditUserRole() = _uiState.update { it.copy(editingUserRole = null) }
    fun onRoleSelected(role: String) = _uiState.update { it.copy(selectedRoleForEdit = role) }

    fun togglePermission(perm: String) {
        _uiState.update { state ->
            val set = state.permissionsForEdit.toMutableSet()
            if (set.contains(perm)) set.remove(perm) else set.add(perm)
            state.copy(permissionsForEdit = set)
        }
    }

    fun saveUserRole() {
        val user = _uiState.value.editingUserRole ?: return
        val role = _uiState.value.selectedRoleForEdit
        val perms = _uiState.value.permissionsForEdit.toList()

        viewModelScope.launch {
            adminRepository.updateUserRole(user.uid, role, perms)
            _uiState.update { it.copy(editingUserRole = null, successMessage = "Role updated for ${user.name}") }
        }
    }

    // User Deletion
    fun promptDeleteUser(user: User) = _uiState.update { it.copy(deletingUser = user) }
    fun dismissDeleteUser() = _uiState.update { it.copy(deletingUser = null) }

    fun confirmDeleteUser() {
        val user = _uiState.value.deletingUser ?: return
        viewModelScope.launch {
            adminRepository.deleteUser(user.uid)
            _uiState.update { it.copy(deletingUser = null, successMessage = "User record removed.") }
        }
    }

    fun dismissSuccess() = _uiState.update { it.copy(successMessage = null) }
    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    viewModel: AdminUsersViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Students & Sections",
                subtitle = "Student registrations & section filtering",
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
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Students (${state.filteredUsers.size}/${state.users.size})") }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Sections (${state.sections.size})") }
                )
            }

            when (state.selectedTab) {
                0 -> {
                    // Registered Users List with Section Filtering & Search
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search box
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            placeholder = { Text("Search by name, enrollment or section...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.searchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        // Section filter chips
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = state.selectedSectionFilter == null,
                                    onClick = { viewModel.selectSectionFilter(null) },
                                    label = { Text("All Sections (${state.users.size})") }
                                )
                            }
                            items(state.sections) { sec ->
                                val count = state.users.count { it.section.equals(sec.name, ignoreCase = true) }
                                FilterChip(
                                    selected = state.selectedSectionFilter.equals(sec.name, ignoreCase = true),
                                    onClick = { viewModel.selectSectionFilter(sec.name) },
                                    label = { Text("${sec.name} ($count)") }
                                )
                            }
                        }

                        // Users list
                        if (state.filteredUsers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (state.selectedSectionFilter != null)
                                        "No students found registered in ${state.selectedSectionFilter}."
                                    else
                                        "No registered students found.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(state.filteredUsers) { user ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(14.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = user.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer
                                                    ) {
                                                        Text(
                                                            text = user.displaySection,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = "Enrollment: ${user.enrollmentNumber}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                val regDate = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(user.createdAt))
                                                Text(
                                                    text = "Registered: $regDate • Role: ${user.role.uppercase()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            IconButton(onClick = { viewModel.startEditUserRole(user) }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Role", tint = MaterialTheme.colorScheme.primary)
                                            }

                                            IconButton(onClick = { viewModel.promptDeleteUser(user) }) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete User", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Sections Management
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Button(
                                onClick = viewModel::showAddSectionDialog,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add New Section")
                            }
                        }

                        items(state.sections) { section ->
                            val count = state.users.count { it.section.equals(section.name, ignoreCase = true) }
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(section.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "$count registered students • ${if (section.isActive) "Active" else "Deactivated"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (section.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }

                                    Switch(
                                        checked = section.isActive,
                                        onCheckedChange = { viewModel.toggleSectionActive(section) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Section Dialog
        if (state.showAddSectionDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissAddSectionDialog,
                title = { Text("Add New Section") },
                text = {
                    OutlinedTextField(
                        value = state.newSectionName,
                        onValueChange = viewModel::onNewSectionNameChange,
                        label = { Text("Section Name") },
                        placeholder = { Text("e.g. Section 19, Batch A") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = viewModel::addSection) { Text("Add Section") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissAddSectionDialog) { Text("Cancel") }
                }
            )
        }

        // Edit User Role & Permissions Dialog
        state.editingUserRole?.let { user ->
            val roles = listOf("student", "moderator", "admin")
            val availablePerms = listOf(
                "manage_materials" to "Upload & Manage Materials",
                "review_submissions" to "Review Student Submissions",
                "manage_announcements" to "Post Announcements",
                "view_users" to "View User Statistics"
            )

            AlertDialog(
                onDismissRequest = viewModel::dismissEditUserRole,
                title = { Text("Assign Role: ${user.name}") },
                text = {
                    Column {
                        Text("Select User Role:", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            roles.forEach { r ->
                                FilterChip(
                                    selected = state.selectedRoleForEdit == r,
                                    onClick = { viewModel.onRoleSelected(r) },
                                    label = { Text(r.uppercase()) }
                                )
                            }
                        }

                        if (state.selectedRoleForEdit == "moderator") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Permissions:", fontWeight = FontWeight.SemiBold)
                            availablePerms.forEach { (permKey, permLabel) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.togglePermission(permKey) }
                                ) {
                                    Checkbox(
                                        checked = state.permissionsForEdit.contains(permKey),
                                        onCheckedChange = { viewModel.togglePermission(permKey) }
                                    )
                                    Text(permLabel, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = viewModel::saveUserRole) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissEditUserRole) { Text("Cancel") }
                }
            )
        }

        // Delete User Confirmation
        state.deletingUser?.let { user ->
            ConfirmDialog(
                title = "Remove User?",
                message = "Are you sure you want to remove ${user.name} (${user.enrollmentNumber}) from the system?",
                confirmText = "Delete",
                isDestructive = true,
                onConfirm = viewModel::confirmDeleteUser,
                onDismiss = viewModel::dismissDeleteUser
            )
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
    }
}
