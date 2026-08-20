package com.pumaterial.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumaterial.app.core.designsystem.*

@Composable
fun StatusBadge(
    text: String,
    icon: ImageVector? = null,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun DownloadedBadge(modifier: Modifier = Modifier) {
    StatusBadge(
        text = "Downloaded",
        icon = Icons.Default.CheckCircle,
        containerColor = SuccessGreenContainer,
        contentColor = SuccessGreen,
        modifier = modifier
    )
}

@Composable
fun UpdateAvailableBadge(modifier: Modifier = Modifier) {
    StatusBadge(
        text = "Update Available",
        icon = Icons.Default.Sync,
        containerColor = WarningOrangeContainer,
        contentColor = WarningOrange,
        modifier = modifier
    )
}

@Composable
fun CloudAvailableBadge(modifier: Modifier = Modifier) {
    StatusBadge(
        text = "Cloud",
        icon = Icons.Default.CloudDownload,
        containerColor = InfoBlueContainer,
        contentColor = InfoBlue,
        modifier = modifier
    )
}

@Composable
fun OfflineOnlyBadge(modifier: Modifier = Modifier) {
    StatusBadge(
        text = "Requires Internet",
        icon = Icons.Default.WifiOff,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun SectionBadge(section: String, modifier: Modifier = Modifier) {
    StatusBadge(
        text = section,
        containerColor = PurpleBadgeContainer,
        contentColor = PurpleBadge,
        modifier = modifier
    )
}

@Composable
fun SubmissionStatusBadge(status: String, modifier: Modifier = Modifier) {
    when (status.lowercase()) {
        "approved" -> StatusBadge(
            text = "Approved",
            icon = Icons.Default.CheckCircle,
            containerColor = SuccessGreenContainer,
            contentColor = SuccessGreen,
            modifier = modifier
        )
        "rejected" -> StatusBadge(
            text = "Rejected",
            icon = Icons.Default.Info,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error,
            modifier = modifier
        )
        else -> StatusBadge(
            text = "Pending Review",
            icon = Icons.Default.Sync,
            containerColor = WarningOrangeContainer,
            contentColor = WarningOrange,
            modifier = modifier
        )
    }
}
