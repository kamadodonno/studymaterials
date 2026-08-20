package com.pumaterial.app.ui.downloads

import android.content.Context
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pumaterial.app.core.components.*
import com.pumaterial.app.core.file.FileHelper
import com.pumaterial.app.core.file.FileOpener
import com.pumaterial.app.core.file.OpenFileResult
import com.pumaterial.app.domain.model.PersonalFolder
import com.pumaterial.app.domain.model.PersonalFolderItem
import com.pumaterial.app.domain.repository.DownloadRepository
import com.pumaterial.app.domain.repository.DownloadedMaterialEntityWrapper
import com.pumaterial.app.domain.repository.PersonalFolderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class DownloadsUiState(
    val selectedTab: Int = 0, // 0: Offline Files, 1: Personal Folders
    val downloadedMaterials: List<DownloadedMaterialEntityWrapper> = emptyList(),
    val personalFolders: List<PersonalFolder> = emptyList(),
    val activeFolder: PersonalFolder? = null,
    val itemsInActiveFolder: List<PersonalFolderItem> = emptyList(),
    val showCreateFolderDialog: Boolean = false,
    val newFolderName: String = "",
    val newFolderColor: String = "#1E40AF",
    val missingAppInfo: Triple<String, String, String>? = null,
    val errorMessage: String? = null
)

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository,
    private val personalFolderRepository: PersonalFolderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeDownloads()
        observeFolders()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadRepository.observeDownloadedMaterials().collect { list ->
                _uiState.update { it.copy(downloadedMaterials = list) }
            }
        }
    }

    private fun observeFolders() {
        viewModelScope.launch {
            personalFolderRepository.observeFolders().collect { folders ->
                _uiState.update { it.copy(personalFolders = folders) }
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex, activeFolder = null) }
    }

    fun openFolder(folder: PersonalFolder) {
        _uiState.update { it.copy(activeFolder = folder) }
        viewModelScope.launch {
            personalFolderRepository.observeItemsInFolder(folder.folderId).collect { items ->
                _uiState.update { it.copy(itemsInActiveFolder = items) }
            }
        }
    }

    fun closeFolder() = _uiState.update { it.copy(activeFolder = null, itemsInActiveFolder = emptyList()) }

    fun openDownloadedFile(context: Context, item: DownloadedMaterialEntityWrapper, onOpenPdfInApp: (String, String) -> Unit) {
        val file = File(item.localFilePath)
        if (!file.exists()) {
            _uiState.update { it.copy(errorMessage = "File no longer exists on local storage.") }
            return
        }

        if (item.fileType.lowercase() == "pdf") {
            onOpenPdfInApp(file.absolutePath, item.title)
        } else {
            when (val openResult = FileOpener.openFile(context, file, item.fileType)) {
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

    fun openFolderItem(context: Context, item: PersonalFolderItem, onOpenPdfInApp: (String, String) -> Unit) {
        if (item.localFilePath == null) {
            _uiState.update { it.copy(errorMessage = "File not downloaded yet.") }
            return
        }
        val file = File(item.localFilePath)
        if (!file.exists()) {
            _uiState.update { it.copy(errorMessage = "File not found on device storage.") }
            return
        }

        if (item.fileType.lowercase() == "pdf") {
            onOpenPdfInApp(file.absolutePath, item.title)
        } else {
            when (val openResult = FileOpener.openFile(context, file, item.fileType)) {
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

    fun deleteDownloadedMaterial(materialId: String, fileName: String) {
        viewModelScope.launch {
            downloadRepository.deleteDownloadedMaterial(materialId, fileName)
        }
    }

    fun showCreateFolderDialog() = _uiState.update { it.copy(showCreateFolderDialog = true, newFolderName = "") }
    fun dismissCreateFolderDialog() = _uiState.update { it.copy(showCreateFolderDialog = false) }
    fun onNewFolderNameChange(name: String) = _uiState.update { it.copy(newFolderName = name) }
    fun onNewFolderColorChange(color: String) = _uiState.update { it.copy(newFolderColor = color) }

    fun createFolder() {
        val name = _uiState.value.newFolderName.trim()
        val color = _uiState.value.newFolderColor
        if (name.isBlank()) return

        viewModelScope.launch {
            personalFolderRepository.createFolder(name, color)
            _uiState.update { it.copy(showCreateFolderDialog = false, newFolderName = "") }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            personalFolderRepository.deleteFolder(folderId)
            if (_uiState.value.activeFolder?.folderId == folderId) {
                closeFolder()
            }
        }
    }

    fun removeItemFromFolder(folderId: String, materialId: String) {
        viewModelScope.launch {
            personalFolderRepository.removeItemFromFolder(folderId, materialId)
        }
    }

    fun dismissMissingAppDialog() = _uiState.update { it.copy(missingAppInfo = null) }
    fun dismissErrorMessage() = _uiState.update { it.copy(errorMessage = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onOpenPdfInApp: (String, String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (state.activeFolder != null) state.activeFolder!!.folderName else "Offline & Personal",
                subtitle = if (state.activeFolder != null) "Personal Folder" else "Access materials without internet",
                onNavigateBack = if (state.activeFolder != null) viewModel::closeFolder else null,
                actions = {
                    if (state.selectedTab == 1 && state.activeFolder == null) {
                        IconButton(onClick = viewModel::showCreateFolderDialog) {
                            Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                        }
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
            if (state.activeFolder == null) {
                TabRow(
                    selectedTabIndex = state.selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = state.selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = { Text("Downloaded Files (${state.downloadedMaterials.size})") },
                        icon = { Icon(Icons.Default.DownloadDone, contentDescription = null) }
                    )
                    Tab(
                        selected = state.selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = { Text("Personal Folders (${state.personalFolders.size})") },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (state.activeFolder != null) {
                    // Inside Personal Folder View
                    if (state.itemsInActiveFolder.isEmpty()) {
                        EmptyStateView(
                            title = "Folder is Empty",
                            subtitle = "Add study materials to this folder from the Subjects or Downloads tab.",
                            icon = Icons.Default.FolderOpen
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.itemsInActiveFolder) { item ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.openFolderItem(context, item, onOpenPdfInApp)
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        FileTypeIconBox(fileType = item.fileType)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = item.fileName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.removeItemFromFolder(state.activeFolder!!.folderId, item.materialId)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Remove from folder",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else when (state.selectedTab) {
                    0 -> {
                        // Offline Downloaded Materials
                        if (state.downloadedMaterials.isEmpty()) {
                            EmptyStateView(
                                title = "No Downloaded Materials",
                                subtitle = "Download study materials when online to access them anytime without internet.",
                                icon = Icons.Default.CloudDownload
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(state.downloadedMaterials) { item ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.openDownloadedFile(context, item, onOpenPdfInApp)
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(14.dp)
                                        ) {
                                            FileTypeIconBox(fileType = item.fileType)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${FileHelper.formatFileSize(item.fileSize)} • ${item.fileName}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            FilledTonalButton(
                                                onClick = {
                                                    viewModel.openDownloadedFile(context, item, onOpenPdfInApp)
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Text("Open", fontSize = 12.sp)
                                            }

                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteDownloadedMaterial(item.materialId, item.fileName)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete local copy",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Personal Folders
                        if (state.personalFolders.isEmpty()) {
                            EmptyStateView(
                                title = "No Personal Folders",
                                subtitle = "Create private folders on your phone to organize your study notes.",
                                icon = Icons.Default.CreateNewFolder
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(state.personalFolders) { folder ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.openFolder(folder) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = folder.folderName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Private on this device",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { viewModel.deleteFolder(folder.folderId) }) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete folder",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Create Folder Dialog
        if (state.showCreateFolderDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissCreateFolderDialog,
                title = { Text("New Personal Folder") },
                text = {
                    Column {
                        Text(
                            text = "Create a private folder to group your study materials:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.newFolderName,
                            onValueChange = viewModel::onNewFolderNameChange,
                            label = { Text("Folder Name") },
                            placeholder = { Text("e.g. Exam Revisions, Case Studies") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = viewModel::createFolder) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissCreateFolderDialog) {
                        Text("Cancel")
                    }
                }
            )
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

        // Error message dialog
        state.errorMessage?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::dismissErrorMessage,
                title = { Text("Error") },
                text = { Text(error) },
                confirmButton = {
                    Button(onClick = viewModel::dismissErrorMessage) { Text("OK") }
                }
            )
        }
    }
}
