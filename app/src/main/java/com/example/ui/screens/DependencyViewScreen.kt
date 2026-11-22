package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.DependencyLinkEntity
import com.example.domain.engine.DependencyResolver
import com.example.domain.model.DependencyNodeType
import com.example.domain.model.DependencyRelationType
import com.example.ui.DexUiState
import com.example.ui.components.CommandPanel
import com.example.ui.components.DemoRecordBadge
import com.example.ui.components.DependencyRelationBadge
import com.example.ui.components.DestructiveConfirmDialog
import com.example.ui.components.EmptyDeckState
import com.example.ui.components.SectionHeader
import com.example.ui.theme.LocalDexColors

@Composable
fun DependencyViewScreen(
    uiState: DexUiState,
    onAddDependency: (
        sourceType: DependencyNodeType,
        sourceId: String,
        targetType: DependencyNodeType,
        targetId: String,
        relationType: DependencyRelationType,
        notes: String
    ) -> Unit,
    onDeleteDependency: (String) -> Unit
) {
    val dex = LocalDexColors.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var deletingLink by remember { mutableStateOf<DependencyLinkEntity?>(null) }

    val projectMap = uiState.projects.associateBy { it.id }
    val workItemMap = uiState.workItems.associateBy { it.id }

    fun resolveLabel(type: DependencyNodeType, id: String): String = when (type) {
        DependencyNodeType.PROJECT -> projectMap[id]?.name ?: id
        DependencyNodeType.WORK_ITEM -> workItemMap[id]?.title ?: id
    }

    val blockingLinks = uiState.visibleDependencyLinks.filter { it.relationType == DependencyRelationType.BLOCKING }
    val infoLinks = uiState.visibleDependencyLinks.filter { it.relationType == DependencyRelationType.INFORMATIONAL }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dependency_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DEPENDENCY VIEW", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "${blockingLinks.size} blocking · ${infoLinks.size} informational references",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = { showCreateDialog = true },
                    enabled = uiState.projects.size + uiState.workItems.size >= 2,
                    modifier = Modifier.testTag("dependency_add_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Link")
                }
            }
        }

        // Section 1: Blocking Dependencies
        item {
            SectionHeader(
                title = "Blocking Dependencies (${blockingLinks.size})",
                subtitle = "Source cannot complete until Target is VERIFIED / COMPLETED"
            )
        }
        if (blockingLinks.isEmpty()) {
            item {
                EmptyDeckState(
                    icon = Icons.Filled.AccountTree,
                    title = "No blocking dependencies",
                    description = "All tracked work items are currently free of explicit blocking edges."
                )
            }
        } else {
            items(blockingLinks, key = { it.id }) { link ->
                val resolved = DependencyResolver.isNodeResolved(link.targetType, link.targetId, projectMap, workItemMap)
                CommandPanel(
                    borderColor = if (resolved) dex.verified.copy(alpha = 0.45f) else dex.blocked.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            DependencyRelationBadge(link.relationType)
                            Text(
                                text = if (resolved) "RESOLVED" else "UNRESOLVED BLOCKER",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (resolved) dex.verified else dex.blocked
                            )
                            if (link.isDemo) DemoRecordBadge()
                        }
                        IconButton(onClick = { deletingLink = link }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove link", tint = dex.blocked, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(link.sourceType.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(resolveLabel(link.sourceType, link.sourceId), style = MaterialTheme.typography.titleSmall)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "blocked by",
                            tint = dex.blocked,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("DEPENDS ON ${link.targetType.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(resolveLabel(link.targetType, link.targetId), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    if (link.notes.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(link.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Section 2: Informational References
        item {
            SectionHeader(
                title = "Informational References (${infoLinks.size})",
                subtitle = "Shared context or architectural links that do not block execution"
            )
        }
        if (infoLinks.isEmpty()) {
            item {
                Text(
                    text = "No informational cross-references recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(infoLinks, key = { it.id }) { link ->
                CommandPanel(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            DependencyRelationBadge(link.relationType)
                            if (link.isDemo) DemoRecordBadge()
                        }
                        IconButton(onClick = { deletingLink = link }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove link", tint = dex.blocked, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${resolveLabel(link.sourceType, link.sourceId)}  ↔  ${resolveLabel(link.targetType, link.targetId)}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (link.notes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(link.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }

    if (showCreateDialog) {
        CreateDependencyDialog(
            uiState = uiState,
            onDismiss = { showCreateDialog = false },
            onCreate = { sType, sId, tType, tId, rel, notes ->
                onAddDependency(sType, sId, tType, tId, rel, notes)
                showCreateDialog = false
            }
        )
    }

    deletingLink?.let { link ->
        DestructiveConfirmDialog(
            title = "Remove Dependency Link?",
            message = "Remove this ${link.relationType.label} dependency relationship?",
            confirmLabel = "Remove",
            onConfirm = {
                onDeleteDependency(link.id)
                deletingLink = null
            },
            onDismiss = { deletingLink = null }
        )
    }
}

@Composable
private fun CreateDependencyDialog(
    uiState: DexUiState,
    onDismiss: () -> Unit,
    onCreate: (
        sourceType: DependencyNodeType,
        sourceId: String,
        targetType: DependencyNodeType,
        targetId: String,
        relationType: DependencyRelationType,
        notes: String
    ) -> Unit
) {
    val allNodes = buildList {
        uiState.projects.forEach { add(Triple(DependencyNodeType.PROJECT, it.id, "PROJ: ${it.name}")) }
        uiState.workItems.forEach { add(Triple(DependencyNodeType.WORK_ITEM, it.id, "TASK: ${it.title}")) }
    }
    var sourceNode by remember { mutableStateOf(allNodes.firstOrNull()) }
    var targetNode by remember { mutableStateOf(allNodes.getOrNull(1) ?: allNodes.firstOrNull()) }
    var relationType by remember { mutableStateOf(DependencyRelationType.BLOCKING) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Dependency Link") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Relation Type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DependencyRelationType.entries.forEach { rel ->
                        FilterChip(
                            selected = relationType == rel,
                            onClick = { relationType = rel },
                            label = { Text(rel.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text("Source (Blocked / Referencing Item)", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allNodes.forEach { node ->
                        FilterChip(
                            selected = sourceNode == node,
                            onClick = { sourceNode = node },
                            label = { Text(node.third.take(26), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text("Target (Required Prerequisite / Reference)", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allNodes.forEach { node ->
                        FilterChip(
                            selected = targetNode == node,
                            onClick = { targetNode = node },
                            label = { Text(node.third.take(26), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Dependency Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = sourceNode
                    val t = targetNode
                    if (s != null && t != null) {
                        onCreate(s.first, s.second, t.first, t.second, relationType, notes)
                    }
                },
                enabled = sourceNode != null && targetNode != null
            ) {
                Text("Create Link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
