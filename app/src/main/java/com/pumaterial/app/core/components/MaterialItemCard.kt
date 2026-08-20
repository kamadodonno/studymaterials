package com.pumaterial.app.core.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumaterial.app.core.designsystem.*
import com.pumaterial.app.core.file.FileHelper
import com.pumaterial.app.domain.model.Material

@Composable
fun MaterialItemCard(
    material: Material,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    onOpenClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onUpdateClick: () -> Unit = onDownloadClick,
    onAddToFolderClick: (() -> Unit)? = null,
    onDeleteLocalClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (material.isDownloaded) onOpenClick() else onDownloadClick()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                // File Type Icon
                FileTypeIconBox(fileType = material.fileType)

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = material.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (material.subjectName.isNotBlank() || material.moduleName.isNotBlank()) {
                        Text(
                            text = "${material.subjectName} • ${material.moduleName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Metadata & Badges Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = FileHelper.formatFileSize(material.fileSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )

                        if (material.isSectionSpecific && material.section != null) {
                            SectionBadge(section = material.section)
                        }

                        if (material.isUpdateAvailable) {
                            UpdateAvailableBadge()
                        } else if (material.isDownloaded) {
                            DownloadedBadge()
                        }
                    }
                }
            }

            // Downloading Progress Bar
            AnimatedVisibility(visible = isDownloading) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onAddToFolderClick != null && material.isDownloaded) {
                    IconButton(
                        onClick = onAddToFolderClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = "Add to Personal Folder",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (onDeleteLocalClick != null && material.isDownloaded) {
                    IconButton(
                        onClick = onDeleteLocalClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete local copy",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                if (material.isUpdateAvailable) {
                    Button(
                        onClick = onUpdateClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningOrange
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Update", fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (material.isDownloaded) {
                    FilledTonalButton(
                        onClick = onOpenClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Open", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onDownloadClick,
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Download", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun FileTypeIconBox(
    fileType: String,
    modifier: Modifier = Modifier
) {
    val (icon, bgColor, iconColor) = when (fileType.lowercase()) {
        "pdf" -> Triple(Icons.Default.PictureAsPdf, Color(0xFFFEE2E2), Color(0xFFDC2626))
        "doc", "docx" -> Triple(Icons.Default.Description, Color(0xFFDBEAFE), Color(0xFF2563EB))
        "ppt", "pptx" -> Triple(Icons.Default.Slideshow, Color(0xFFFEF3C7), Color(0xFFD97706))
        else -> Triple(Icons.Default.InsertDriveFile, Color(0xFFF1F5F9), Color(0xFF475569))
    }

    Box(
        modifier = modifier
            .size(46.dp)
            .background(bgColor, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = fileType.uppercase(),
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}
