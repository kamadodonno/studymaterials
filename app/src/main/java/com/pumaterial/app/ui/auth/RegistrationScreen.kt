package com.pumaterial.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumaterial.app.core.common.Resource
import com.pumaterial.app.domain.model.Section
import com.pumaterial.app.domain.repository.AuthRepository
import com.pumaterial.app.domain.repository.MaterialRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.pumaterial.app.core.common.Constants

val DEFAULT_SECTIONS = Constants.DEFAULT_SECTIONS

data class RegistrationUiState(
    val name: String = "",
    val enrollmentNumber: String = "",
    val selectedSection: String = "",
    val otherSection: String = "",
    val sections: List<Section> = DEFAULT_SECTIONS,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRegistered: Boolean = false
) {
    val displaySections: List<Section>
        get() {
            val list = if (sections.isEmpty()) DEFAULT_SECTIONS else sections
            val hasOther = list.any { it.name.equals("Other", ignoreCase = true) }
            return if (!hasOther) list + Section(id = "sec_other", name = "Other", order = 99, isActive = true) else list
        }
}

class RegistrationViewModel(
    private val authRepository: AuthRepository,
    private val materialRepository: MaterialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSections()
    }

    private fun loadSections() {
        viewModelScope.launch {
            materialRepository.observeSections().collect { list ->
                if (list.isNotEmpty()) {
                    _uiState.update { it.copy(sections = list) }
                }
            }
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name, errorMessage = null) }
    fun onEnrollmentChange(enrollment: String) = _uiState.update { it.copy(enrollmentNumber = enrollment, errorMessage = null) }
    fun onSectionChange(section: String) = _uiState.update { it.copy(selectedSection = section, errorMessage = null) }
    fun onOtherSectionChange(other: String) = _uiState.update { it.copy(otherSection = other, errorMessage = null) }
    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    fun register() {
        val state = _uiState.value
        val name = state.name.trim()
        val enrollment = state.enrollmentNumber.trim()
        val section = state.selectedSection.trim()
        val other = if (section == "Other") state.otherSection.trim() else null

        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your full name.") }
            return
        }
        if (enrollment.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your enrollment number.") }
            return
        }
        if (section.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please select your section.") }
            return
        }
        if (section == "Other" && other.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Please specify your section name.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.registerStudent(
                name = name,
                enrollmentNumber = enrollment,
                section = section,
                otherSection = other
            )
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false, isRegistered = true) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                Resource.Loading -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    viewModel: RegistrationViewModel,
    onRegistrationSuccess: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var isDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.isRegistered) {
        if (state.isRegistered) {
            onRegistrationSuccess()
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // App Brand
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Study Materials",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Welcome! Set up your student profile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Full Name
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Full Name") },
                    placeholder = { Text("e.g. Rahul Sharma") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Unique Enrollment Number
                OutlinedTextField(
                    value = state.enrollmentNumber,
                    onValueChange = viewModel::onEnrollmentChange,
                    label = { Text("Enrollment Number") },
                    placeholder = { Text("e.g. PU123456") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Badge, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section Selection Dropdown
                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = state.selectedSection,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Section") },
                        placeholder = { Text("Select Section ▼") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Groups, contentDescription = null)
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        state.displaySections.forEach { section ->
                            DropdownMenuItem(
                                text = { Text(section.name) },
                                onClick = {
                                    viewModel.onSectionChange(section.name)
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // If "Other" selected, show write-in field
                if (state.selectedSection == "Other") {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.otherSection,
                        onValueChange = viewModel::onOtherSectionChange,
                        label = { Text("Describe Your Section / Batch") },
                        placeholder = { Text("e.g. MBA Executive Batch B") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.EditNote, contentDescription = null)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Privacy Notice Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Privacy Notice: Your name, enrollment number, and section are securely used for personalized study materials, section announcements, and classroom organization.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Continue Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.register()
                    },
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Continue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Error Dialog
            if (state.errorMessage != null) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissError,
                    icon = {
                        Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    title = { Text("Registration Failed") },
                    text = { Text(state.errorMessage ?: "") },
                    confirmButton = {
                        Button(onClick = viewModel::dismissError) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}
