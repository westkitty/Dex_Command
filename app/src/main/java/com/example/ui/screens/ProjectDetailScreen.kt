package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.ProjectEntity
import com.example.data.local.WorkItemEntity
import com.example.data.repository.GitHubRepoMetadata
import com.example.domain.engine.DependencyResolver
import com.example.domain.engine.ProjectStateMachine
import com.example.domain.model.DependencyNodeType
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus
import com.example.ui.AsyncPanelState
import com.example.ui.DexUiState
import com.example.ui.components.CommandPanel
import com.example.ui.components.DemoRecordBadge
import com.example.ui.components.DependencyRelationBadge
import com.example.ui.components.DestructiveConfirmDialog
import com.example.ui.components.EmptyDeckState
import com.example.ui.components.EvidenceStateBadge
import com.example.ui.components.PriorityPill
import com.example.ui.components.ProjectStatusBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.formatShortDate
import com.example.ui.theme.LocalDexColors
import java.util.UUID

@Composable
fun ProjectDetailScreen(
    projectId: String?,
    uiState: DexUiState,
    githubState: AsyncPanelState<GitHubRepoMetadata>,
    aiOutputState: AsyncPanelState<String>,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onSaveProject: (ProjectEntity, Boolean) -> Unit,
    onTransitionStatus: (String, ProjectStatus) -> Unit,
    onMarkReviewed: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onSaveWorkItem: (WorkItemEntity, Boolean) -> Unit,
    onDeleteWorkItem: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCheckGitHub: (String) -> Unit,
    onOpenRecovery: (String) -> Unit,
    onOpenPromptForTask: (String, String?) -> Unit,
    onAiSuggestTasks: (String) -> Unit
) {
    val project = uiState.projects.firstOrNull { it.id == projectId }
    if (project == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            EmptyDeckState(
                icon = Icons.Filled.FolderOff,
                title = "No project selected",
                description = "Select a project from the navigator or create a new project."
            )
        }
        return
    }

    val dex = LocalDexColors.current
    val workItems = uiState.workItems.filter { it.projectId == project.id }
    val records = uiState.executionRecords.filter { it.projectId == project.id }
    val importedDocs = uiState.importedDocuments.filter { it.projectId == project.id }
    val projectDeps = DependencyResolver.resolveForNode(
        nodeType = DependencyNodeType.PROJECT,
        nodeId = project.id,
        allLinks = uiState.dependencyLinks,
        projects = uiState.projects,
        workItems = uiState.workItems
    )
    val allowedTransitions = ProjectStateMachine.allowedTargets(project.status)

    var showEditProject by remember { mutableStateOf(false) }
    var showDeleteProjectConfirm by remember { mutableStateOf(false) }
    var editingWorkItem by remember { mutableStateOf<WorkItemEntity?>(null) }
    var showCreateWorkItem by remember { mutableStateOf(false) }
    var deletingWorkItem by remember { mutableStateOf<WorkItemEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("project_detail_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (showBackButton) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProjectStatusBadge(project.status)
                            PriorityPill(project.priority)
                            if (project.isDemo) DemoRecordBadge()
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${project.category} · ID: ${project.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = { showEditProject = true }, modifier = Modifier.testTag("detail_edit_btn")) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit project")
                    }
                    IconButton(onClick = { showDeleteProjectConfirm = true }, modifier = Modifier.testTag("detail_delete_btn")) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete project", tint = dex.blocked)
                    }
                }
            }
        }

        // Quick Actions Strip
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onOpenRecovery(project.id) },
                    modifier = Modifier.testTag("detail_recovery_btn")
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Continuity Packet")
                }
                OutlinedButton(
                    onClick = { onOpenPromptForTask(project.id, workItems.firstOrNull()?.id) },
                    modifier = Modifier.testTag("detail_prompt_btn")
                ) {
                    Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agent Prompt")
                }
                OutlinedButton(onClick = { onMarkReviewed(project.id) }) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mark Reviewed")
                }
                OutlinedButton(onClick = { onAiSuggestTasks(project.id) }) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gemini Suggest")
                }
            }
        }

        // Status Transition Controls
        item {
            CommandPanel {
                Text("AUTHORITATIVE STATUS TRANSITION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allowedTransitions.forEach { target ->
                        FilterChip(
                            selected = project.status == target,
                            onClick = { onTransitionStatus(project.id, target) },
                            label = { Text(target.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Last reviewed: ${formatShortDate(project.lastReviewDate)} · Updated: ${formatShortDate(project.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Current Objective & Most Recent Verified State
        item {
            CommandPanel {
                SectionHeader(title = "Current Objective & Purpose")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = project.currentObjective.ifBlank { "No active objective recorded." },
                    style = MaterialTheme.typography.titleSmall
                )
                if (project.purpose.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = project.purpose,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(14.dp))
                SectionHeader(title = "Most Recent Verified State")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = project.lastVerifiedStateSummary.ifBlank { "No independently verified state recorded yet." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = dex.verified
                )
                if (project.lastVerifiedMilestone.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Milestone: ${project.lastVerifiedMilestone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Optional Gemini Next-Task Advisory Panel
        if (aiOutputState !is AsyncPanelState.Idle) {
            item {
                CommandPanel(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) {
                    Text(
                        text = "GEMINI ADVISORY OUTPUT (NON-AUTHORITATIVE · NEVER AUTO-EXECUTED)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    when (aiOutputState) {
                        AsyncPanelState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        is AsyncPanelState.Success -> Text(aiOutputState.data, style = MaterialTheme.typography.bodySmall)
                        is AsyncPanelState.Error -> Text(aiOutputState.message, style = MaterialTheme.typography.bodySmall, color = dex.blocked)
                        AsyncPanelState.Idle -> Unit
                    }
                }
            }
        }

        // Protected Behavior, Known Failures, Open Decisions
        item {
            CommandPanel {
                SectionHeader(title = "Protected Behavior & Constraints")
                Spacer(Modifier.height(8.dp))
                if (project.protectedConstraints.isEmpty()) {
                    Text("No protected constraints registered.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    project.protectedConstraints.forEach { c ->
                        Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(c, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionHeader(title = "Known Failures")
                Spacer(Modifier.height(8.dp))
                if (project.knownFailures.isEmpty()) {
                    Text("No known failures.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    project.knownFailures.forEach { f ->
                        Text("• $f", style = MaterialTheme.typography.bodySmall, color = dex.blocked)
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionHeader(title = "Open Decisions")
                Spacer(Modifier.height(8.dp))
                if (project.openDecisions.isEmpty()) {
                    Text("No open decisions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    project.openDecisions.forEach { d ->
                        Text("• $d", style = MaterialTheme.typography.bodySmall, color = dex.review)
                    }
                }
            }
        }

        // Work Items Section
        item {
            SectionHeader(
                title = "Work Items (${workItems.size})",
                subtitle = "Bounded tasks with acceptance criteria & evidence state",
                trailing = {
                    OutlinedButton(
                        onClick = { showCreateWorkItem = true },
                        modifier = Modifier.testTag("detail_add_task_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Task")
                    }
                }
            )
        }
        if (workItems.isEmpty()) {
            item {
                EmptyDeckState(
                    icon = Icons.Filled.Add,
                    title = "No work items yet",
                    description = "Add bounded work items with verifiable acceptance criteria.",
                    actionLabel = "Add Work Item",
                    onAction = { showCreateWorkItem = true }
                )
            }
        } else {
            items(workItems, key = { it.id }) { item ->
                CommandPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            PriorityPill(item.priority)
                            ProjectStatusBadge(item.status)
                            EvidenceStateBadge(item.evidenceState)
                        }
                        Row {
                            IconButton(onClick = { editingWorkItem = item }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit task", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { deletingWorkItem = item }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete task", tint = dex.blocked, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.acceptanceCriteria.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Acceptance Criteria:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        item.acceptanceCriteria.forEach { ac ->
                            Text("  □ $ac", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onOpenPromptForTask(project.id, item.id) }) {
                        Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generate Prompt for Task")
                    }
                }
            }
        }

        // Linked Repositories, Drive & Optional Read-Only GitHub Sync Check
        item {
            CommandPanel {
                SectionHeader(title = "Linked Repositories & Documents")
                Spacer(Modifier.height(10.dp))
                if (project.repositoryUrl.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GITHUB REPOSITORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(project.repositoryUrl, style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            IconButton(onClick = { onCheckGitHub(project.repositoryUrl) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Check public GitHub metadata")
                            }
                            IconButton(onClick = { onOpenUrl(project.repositoryUrl) }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open GitHub URL")
                            }
                        }
                    }
                    when (githubState) {
                        AsyncPanelState.Idle -> Unit
                        AsyncPanelState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        is AsyncPanelState.Success -> {
                            val meta = githubState.data
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, dex.hairline),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("READ-ONLY GITHUB METADATA: ${meta.fullName}", style = MaterialTheme.typography.labelSmall, color = dex.verified)
                                    Text("Default branch: ${meta.defaultBranch} · Open issues: ${meta.openIssuesCount}", style = MaterialTheme.typography.bodySmall)
                                    if (meta.pushedAt.isNotBlank()) {
                                        Text("Last push: ${meta.pushedAt}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        is AsyncPanelState.Error -> {
                            Text(
                                text = githubState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = dex.review,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    Text("No GitHub repository linked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(8.dp))
                if (project.driveUrl.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GOOGLE DRIVE REFERENCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(project.driveUrl, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onOpenUrl(project.driveUrl) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Google Drive URL")
                        }
                    }
                }

                if (project.documentationRefs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("DOCUMENTATION REFS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    project.documentationRefs.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (project.storageRefs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("STORAGE REFS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    project.storageRefs.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (importedDocs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("PRESERVED IMPORTED DOCUMENTS (${importedDocs.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    importedDocs.forEach { doc ->
                        Text("• ${doc.fileName} (${doc.format} · SHA256 ${doc.contentHash.take(10)})", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Dependencies Section
        item {
            CommandPanel {
                SectionHeader(title = "Project Dependencies")
                Spacer(Modifier.height(8.dp))
                val allOut = projectDeps.blockingLinks + projectDeps.informationalLinks
                if (allOut.isEmpty()) {
                    Text("No outgoing project-level dependencies.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    allOut.forEach { dep ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DependencyRelationBadge(dep.relationType)
                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("${dep.targetId}: ${dep.notes.ifBlank { dep.targetType.label }}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Action History (Execution Records for this Project)
        item {
            SectionHeader(title = "Action History (${records.size})")
        }
        if (records.isEmpty()) {
            item {
                Text("No execution records recorded for this project yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(records.take(10), key = { it.id }) { rec ->
                CommandPanel(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EvidenceStateBadge(rec.verificationStatus)
                        Text("${rec.toolOrAgent} · ${formatShortDate(rec.timestamp)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(rec.actionAttempted, style = MaterialTheme.typography.titleSmall)
                    if (rec.claim.isNotBlank()) {
                        Text("Claim: ${rec.claim}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (rec.evidence.isNotBlank()) {
                        Text("Evidence: ${rec.evidence}", style = MaterialTheme.typography.bodySmall, color = dex.verified)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }

    if (showEditProject) {
        ProjectEditorDialog(
            existing = project,
            onDismiss = { showEditProject = false },
            onSave = { updated ->
                onSaveProject(updated, false)
                showEditProject = false
            }
        )
    }

    if (showDeleteProjectConfirm) {
        DestructiveConfirmDialog(
            title = "Delete Project?",
            message = "This permanently removes '${project.name}' along with its linked work items and execution records. Audit history is preserved.",
            confirmLabel = "Delete Project",
            onConfirm = {
                onDeleteProject(project.id)
                showDeleteProjectConfirm = false
                if (showBackButton) onBack()
            },
            onDismiss = { showDeleteProjectConfirm = false }
        )
    }

    if (showCreateWorkItem || editingWorkItem != null) {
        WorkItemEditorDialog(
            projectId = project.id,
            existing = editingWorkItem,
            onDismiss = {
                showCreateWorkItem = false
                editingWorkItem = null
            },
            onSave = { wi, isNew ->
                onSaveWorkItem(wi, isNew)
                showCreateWorkItem = false
                editingWorkItem = null
            }
        )
    }

    deletingWorkItem?.let { item ->
        DestructiveConfirmDialog(
            title = "Delete Work Item?",
            message = "Remove '${item.title}' from ${project.name}?",
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteWorkItem(item.id)
                deletingWorkItem = null
            },
            onDismiss = { deletingWorkItem = null }
        )
    }
}

@Composable
private fun WorkItemEditorDialog(
    projectId: String,
    existing: WorkItemEntity?,
    onDismiss: () -> Unit,
    onSave: (WorkItemEntity, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var priority by remember { mutableIntStateOf(existing?.priority ?: 2) }
    var status by remember { mutableStateOf(existing?.status ?: ProjectStatus.ACTIVE) }
    var evidenceState by remember { mutableStateOf(existing?.evidenceState ?: EvidenceState.PLANNED) }
    var criteriaText by remember { mutableStateOf(existing?.acceptanceCriteria?.joinToString("\n").orEmpty()) }
    var evidenceRefsText by remember { mutableStateOf(existing?.evidenceRefs?.joinToString("\n").orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Work Item" else "Edit Work Item") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Bounded Scope") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text("P$p") })
                    }
                }
                Text("Status", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProjectStatus.entries.forEach { st ->
                        FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st.label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Text("Evidence State", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EvidenceState.entries.forEach { ev ->
                        FilterChip(selected = evidenceState == ev, onClick = { evidenceState = ev }, label = { Text(ev.label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                OutlinedTextField(
                    value = criteriaText,
                    onValueChange = { criteriaText = it },
                    label = { Text("Acceptance Criteria (one per line)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = evidenceRefsText,
                    onValueChange = { evidenceRefsText = it },
                    label = { Text("Evidence References (one per line)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    val item = (existing ?: WorkItemEntity(
                        id = "wi-${UUID.randomUUID().toString().take(8)}",
                        projectId = projectId,
                        title = title.trim(),
                        createdAt = now,
                        updatedAt = now
                    )).copy(
                        title = title.trim(),
                        description = description.trim(),
                        priority = priority,
                        status = status,
                        evidenceState = evidenceState,
                        acceptanceCriteria = criteriaText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                        evidenceRefs = evidenceRefsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                        updatedAt = now
                    )
                    onSave(item, existing == null)
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
