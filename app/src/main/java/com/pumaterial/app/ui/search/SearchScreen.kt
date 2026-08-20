package com.pumaterial.app.ui.search

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumaterial.app.core.components.*
import com.pumaterial.app.core.file.FileOpener
import com.pumaterial.app.core.file.OpenFileResult
import com.pumaterial.app.domain.model.Material
import com.pumaterial.app.domain.repository.DownloadRepository
import com.pumaterial.app.domain.repository.MaterialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val selectedFileType: String? = null,
    val downloadedOnly: Boolean = false,
    val results: List<Material> = emptyList(),
    val isSearching: Boolean = false,
    val downloadingMaterialId: String? = null,
    val downloadProgress: Float = 0f,
    val missingAppInfo: Triple<String, String, String>? = null,
    val errorMessage: String? = null
)

class SearchViewModel(
    private val materialRepository: MaterialRepository,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    init {
        performSearch()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        performSearch()
    }

    fun selectFileTypeFilter(type: String?) {
        _uiState.update { it.copy(selectedFileType = if (it.selectedFileType == type) null else type) }
        performSearch()
    }

    fun toggleDownloadedOnly() {
        _uiState.update { it.copy(downloadedOnly = !it.downloadedOnly) }
        performSearch()
    }

    private fun performSearch() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val state = _uiState.value
            val result = materialRepository.searchMaterials(
                query = state.query,
                subjectId = null,
                fileType = state.selectedFileType
            )
            when (result) {
                is com.pumaterial.app.core.common.Resource.Success -> {
                    var filtered = result.data
                    if (state.downloadedOnly) {
                        filtered = filtered.filter { it.isDownloaded }
                    }
                    _uiState.update { it.copy(isSearching = false, results = filtered) }
                }
                is com.pumaterial.app.core.common.Resource.Error -> {
                    _uiState.update { it.copy(isSearching = false, errorMessage = result.message) }
                }
                com.pumaterial.app.core.common.Resource.Loading -> Unit
            }
        }
    }

    fun downloadMaterial(material: Material) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingMaterialId = material.id, downloadProgress = 0f) }
            val result = downloadRepository.downloadOrUpdateMaterial(
                material = material,
                onProgress = { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
            )
            _uiState.update { it.copy(downloadingMaterialId = null, downloadProgress = 0f) }
            if (result is com.pumaterial.app.core.common.Resource.Success) {
                performSearch()
            }
        }
    }

    fun openMaterial(context: Context, material: Material, onOpenPdfInApp: (String, String) -> Unit) {
        viewModelScope.launch {
            val file = downloadRepository.getLocalFileForMaterial(material.id, material.fileName)
            if (file == null || !file.exists()) {
                downloadMaterial(material)
                return@launch
            }

            if (material.fileType.lowercase() == "pdf") {
                onOpenPdfInApp(file.absolutePath, material.title)
            } else {
                when (val openResult = FileOpener.openFile(context, file, material.fileType)) {
                    is OpenFileResult.Success -> Unit
                    is OpenFileResult.NoAppInstalled -> {
                        _uiState.update {
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
                        _uiState.update { it.copy(errorMessage = openResult.message) }
                    }
                }
            }
        }
    }

    fun dismissMissingAppDialog() = _uiState.update { it.copy(missingAppInfo = null) }
    fun dismissErrorMessage() = _uiState.update { it.copy(errorMessage = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onOpenPdfInApp: (String, String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search materials, subjects, modules...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.query.isNotBlank()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Filter Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.selectedFileType == null,
                        onClick = { viewModel.selectFileTypeFilter(null) },
                        label = { Text("All Types") }
                    )
                }
                item {
                    FilterChip(
                        selected = state.selectedFileType == "pdf",
                        onClick = { viewModel.selectFileTypeFilter("pdf") },
                        label = { Text("PDF") }
                    )
                }
                item {
                    FilterChip(
                        selected = state.selectedFileType == "pptx" || state.selectedFileType == "ppt",
                        onClick = { viewModel.selectFileTypeFilter("pptx") },
                        label = { Text("PowerPoint") }
                    )
                }
                item {
                    FilterChip(
                        selected = state.selectedFileType == "docx" || state.selectedFileType == "doc",
                        onClick = { viewModel.selectFileTypeFilter("docx") },
                        label = { Text("Word Docs") }
                    )
                }
                item {
                    FilterChip(
                        selected = state.downloadedOnly,
                        onClick = viewModel::toggleDownloadedOnly,
                        label = { Text("Downloaded Only") }
                    )
                }
            }

            if (state.results.isEmpty() && !state.isSearching) {
                EmptyStateView(
                    title = "No Materials Found",
                    subtitle = "Try searching for a different subject, keyword, or clear your filters.",
                    icon = Icons.Default.Search
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.results) { material ->
                        MaterialItemCard(
                            material = material,
                            isDownloading = state.downloadingMaterialId == material.id,
                            downloadProgress = state.downloadProgress,
                            onOpenClick = { viewModel.openMaterial(context, material, onOpenPdfInApp) },
                            onDownloadClick = { viewModel.downloadMaterial(material) }
                        )
                    }
                }
            }
        }

        // Missing app fallback dialog
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
