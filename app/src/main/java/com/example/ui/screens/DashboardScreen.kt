package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus
import com.example.ui.DexUiState
import com.example.ui.components.CommandPanel
import com.example.ui.components.DemoRecordBadge
import com.example.ui.components.EmptyDeckState
import com.example.ui.components.EvidenceStateBadge
import com.example.ui.components.MetricTile
import com.example.ui.components.PriorityPill
import com.example.ui.components.ProjectStatusBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.formatShortDate
import com.example.ui.theme.LocalDexColors

@Composable
fun DashboardScreen(
    uiState: DexUiState,
    onOpenProject: (String) -> Unit,
    onOpenExplorerWithStatus: (ProjectStatus?) -> Unit,
    onOpenCreateProject: () -> Unit,
    onOpenRecovery: (String) -> Unit
) {
    val dex = LocalDexColors.current

    if (uiState.isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("INITIALIZING CONTROL DECK...", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        return
    }

    val projectById = uiState.visibleProjects.associateBy { it.id }
    val verifiedRecords = uiState.visibleExecutionRecords
        .filter { it.verificationStatus == EvidenceState.VERIFIED }
        .take(5)
    val openDecisions = uiState.visibleProjects
        .flatMap { p -> p.openDecisions.map { decision -> p to decision } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DEX//COMMAND",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (uiState.isOffline) "OFFLINE-FIRST · LOCAL ROOM AUTHORITATIVE" else "AUTHORITATIVE PROJECT CONTROL DECK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = onOpenCreateProject,
                    modifier = Modifier.testTag("dashboard_new_project_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Project")
                }
            }
        }

        if (uiState.isOffline) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, dex.hairline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = "Offline mode active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Offline operation active. All Room records, exports, and continuity tools remain fully operational.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Demo data transparency banner
        val demoCount = uiState.visibleProjects.count { it.isDemo }
        if (demoCount > 0) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = dex.demoBg,
                    border = BorderStroke(1.dp, dex.demoAccent.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DemoRecordBadge()
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "$demoCount demonstration project(s) visible. Demo items are explicitly tagged [DEMO] and can be hidden or purged in Tools.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Key Status Metrics Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "Active",
                    value = uiState.activeProjects.size.toString(),
                    accent = dex.verified,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_active"),
                    onClick = { onOpenExplorerWithStatus(ProjectStatus.ACTIVE) }
                )
                MetricTile(
                    label = "Blocked",
                    value = uiState.blockedProjects.size.toString(),
                    accent = dex.blocked,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_blocked"),
                    onClick = { onOpenExplorerWithStatus(ProjectStatus.BLOCKED) }
                )
                MetricTile(
                    label = "Review",
                    value = uiState.reviewNeededProjects.size.toString(),
                    accent = dex.review,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_review"),
                    onClick = { onOpenExplorerWithStatus(ProjectStatus.REVIEW) }
                )
            }
        }

        // Integrity Warning Banner (if local evidence inconsistencies exist)
        if (uiState.localInconsistencies.isNotEmpty()) {
            item {
                CommandPanel(
                    borderColor = dex.blocked.copy(alpha = 0.6f),
                    containerColor = dex.blockedBg
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Status inconsistencies detected",
                            tint = dex.blocked,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "EVIDENCE AUDIT: ${uiState.localInconsistencies.size} INCONSISTENCY(S) DETECTED",
                            style = MaterialTheme.typography.labelLarge,
                            color = dex.blocked
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    uiState.localInconsistencies.take(3).forEach { inc ->
                        Text(
                            text = "• [${inc.severity}] ${inc.title}: ${inc.detail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 1. Today's Prioritized Next Actions
        item {
            SectionHeader(
                title = "Today's Prioritized Next Actions",
                subtitle = "Unblocked work items sorted by task & project priority"
            )
        }
        if (uiState.nextActionableTasks.isEmpty()) {
            item {
                EmptyDeckState(
                    icon = Icons.Filled.PlayArrow,
                    title = "No unblocked tasks queued",
                    description = "Resolve active blockers or add a bounded work item to a project."
                )
            }
        } else {
            items(uiState.nextActionableTasks.take(5), key = { it.id }) { task ->
                val parent = projectById[task.projectId]
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProject(task.projectId) }
                        .testTag("next_action_${task.id}"),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, dex.hairline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PriorityPill(task.priority)
                                EvidenceStateBadge(task.evidenceState)
                                if (task.isDemo) DemoRecordBadge()
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open parent project",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (parent != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = parent.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (task.description.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 2. Blocked & Review-Needed Projects
        if (uiState.blockedProjects.isNotEmpty() || uiState.reviewNeededProjects.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Attention Required",
                    subtitle = "Blocked projects and projects overdue for review"
                )
            }
            val attentionList = (uiState.blockedProjects + uiState.reviewNeededProjects).distinctBy { it.id }
            items(attentionList, key = { "attn_${it.id}" }) { project ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProject(project.id) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (project.status == ProjectStatus.BLOCKED) dex.blocked.copy(alpha = 0.55f) else dex.review.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProjectStatusBadge(project.status)
                                PriorityPill(project.priority)
                                if (project.isDemo) DemoRecordBadge()
                            }
                            Text(
                                text = "Reviewed ${formatShortDate(project.lastReviewDate)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (project.currentObjective.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = project.currentObjective,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onOpenProject(project.id) }) {
                                Text("Inspect")
                            }
                            OutlinedButton(onClick = { onOpenRecovery(project.id) }) {
                                Text("Continuity Packet")
                            }
                        }
                    }
                }
            }
        }

        // 3. Outstanding Decisions
        item {
            SectionHeader(
                title = "Outstanding Decisions",
                subtitle = "${openDecisions.size} unresolved decision(s) across projects"
            )
        }
        if (openDecisions.isEmpty()) {
            item {
                CommandPanel(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "No outstanding decisions pending.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(openDecisions.take(6)) { (project, decision) ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProject(project.id) },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, dex.hairline)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Gavel, contentDescription = null, tint = dex.review, modifier = Modifier.size(15.dp))
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (project.isDemo) DemoRecordBadge()
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = decision,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 4. Recent Verified Milestones
        item {
            SectionHeader(
                title = "Recent Verified Milestones",
                subtitle = "Strictly backed by VERIFIED execution evidence"
            )
        }
        if (verifiedRecords.isEmpty()) {
            item {
                EmptyDeckState(
                    icon = Icons.Filled.Verified,
                    title = "No verified milestones yet",
                    description = "Record concrete verification evidence in the Execution Ledger to establish verified milestones."
                )
            }
        } else {
            items(verifiedRecords, key = { it.id }) { rec ->
                val project = projectById[rec.projectId]
                CommandPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            EvidenceStateBadge(rec.verificationStatus)
                            if (rec.isDemo) DemoRecordBadge()
                        }
                        Text(
                            text = formatShortDate(rec.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = rec.actionAttempted,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (project != null) {
                        Text(
                            text = "${project.name} · ${rec.toolOrAgent}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (rec.evidence.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Evidence: ${rec.evidence}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}
