package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DependencyRelationType
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus
import com.example.ui.theme.LocalDexColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommandPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = LocalDexColors.current.hairline,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 1.dp, color = LocalDexColors.current.hairline)
    }
}

@Composable
fun ProjectStatusBadge(status: ProjectStatus, modifier: Modifier = Modifier) {
    val dex = LocalDexColors.current
    val (fg, bg, icon) = when (status) {
        ProjectStatus.ACTIVE -> Triple(dex.verified, dex.verifiedBg, Icons.Filled.PlayArrow)
        ProjectStatus.BLOCKED -> Triple(dex.blocked, dex.blockedBg, Icons.Filled.Block)
        ProjectStatus.REVIEW -> Triple(dex.review, dex.reviewBg, Icons.Filled.RateReview)
        ProjectStatus.PAUSED -> Triple(dex.paused, dex.pausedBg, Icons.Filled.PauseCircle)
        ProjectStatus.COMPLETED -> Triple(dex.claimed, dex.claimedBg, Icons.Filled.CheckCircle)
        ProjectStatus.ARCHIVED -> Triple(dex.archived, dex.archivedBg, Icons.Filled.Archive)
    }
    SemanticBadge(
        text = status.label,
        icon = icon,
        fg = fg,
        bg = bg,
        accessibilityLabel = "Project status ${status.label}",
        modifier = modifier
    )
}

@Composable
fun EvidenceStateBadge(state: EvidenceState, modifier: Modifier = Modifier) {
    val dex = LocalDexColors.current
    val (fg, bg, icon) = when (state) {
        EvidenceState.VERIFIED -> Triple(dex.verified, dex.verifiedBg, Icons.Filled.Verified)
        EvidenceState.OBSERVED -> Triple(dex.review, dex.reviewBg, Icons.Filled.Visibility)
        EvidenceState.CLAIMED -> Triple(dex.claimed, dex.claimedBg, Icons.Filled.Info)
        EvidenceState.PLANNED -> Triple(dex.paused, dex.pausedBg, Icons.Filled.Schedule)
        EvidenceState.FAILED -> Triple(dex.blocked, dex.blockedBg, Icons.Filled.Error)
        EvidenceState.UNKNOWN -> Triple(dex.archived, dex.archivedBg, Icons.Filled.HelpOutline)
    }
    SemanticBadge(
        text = state.label,
        icon = icon,
        fg = fg,
        bg = bg,
        accessibilityLabel = "Evidence state ${state.label}",
        modifier = modifier
    )
}

@Composable
fun DependencyRelationBadge(relation: DependencyRelationType, modifier: Modifier = Modifier) {
    val dex = LocalDexColors.current
    val (fg, bg, icon) = when (relation) {
        DependencyRelationType.BLOCKING -> Triple(dex.blocked, dex.blockedBg, Icons.Filled.Block)
        DependencyRelationType.INFORMATIONAL -> Triple(dex.claimed, dex.claimedBg, Icons.Filled.Info)
    }
    SemanticBadge(
        text = relation.label,
        icon = icon,
        fg = fg,
        bg = bg,
        accessibilityLabel = "Dependency type ${relation.label}",
        modifier = modifier
    )
}

@Composable
fun DemoRecordBadge(modifier: Modifier = Modifier) {
    val dex = LocalDexColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(dex.demoBg)
            .border(1.dp, dex.demoAccent.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = "Demonstration record, not user production data" }
    ) {
        Text(
            text = "DEMO",
            style = MaterialTheme.typography.labelSmall,
            color = dex.demoAccent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PriorityPill(priority: Int, modifier: Modifier = Modifier) {
    val dex = LocalDexColors.current
    val (label, color) = when (priority) {
        1 -> "P1·CRIT" to dex.blocked
        2 -> "P2·HIGH" to dex.review
        3 -> "P3·NORM" to MaterialTheme.colorScheme.primary
        else -> "P4·LOW" to dex.paused
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SemanticBadge(
    text: String,
    icon: ImageVector,
    fg: Color,
    bg: Color,
    accessibilityLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .semantics { contentDescription = accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, LocalDexColors.current.hairline),
        onClick = { onClick?.invoke() },
        enabled = onClick != null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun EmptyDeckState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    CommandPanel(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun DestructiveConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatShortDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Never"
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
    return sdf.format(Date(epochMillis))
}
