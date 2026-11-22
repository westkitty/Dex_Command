package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ProjectEntity
import com.example.domain.model.ProjectCategories
import com.example.domain.model.ProjectSortOption
import com.example.domain.model.ProjectStatus
import com.example.ui.DexUiState
import com.example.ui.components.DemoRecordBadge
import com.example.ui.components.EmptyDeckState
import com.example.ui.components.PriorityPill
import com.example.ui.components.ProjectStatusBadge
import com.example.ui.components.formatShortDate
import com.example.ui.theme.LocalDexColors
import java.util.UUID

@Composable
fun ProjectExplorerScreen(
    uiState: DexUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onTagSelect: (String?) -> Unit,
    onStatusSelect: (ProjectStatus?) -> Unit,
    onSortChange: (ProjectSortOption) -> Unit,
    onClearFilters: () -> Unit,
    onOpenProject: (String) -> Unit,
    onSaveProject: (ProjectEntity, Boolean) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val dex = LocalDexColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Search + Sort + Add Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PROJECT EXPLORER",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${uiState.filteredSortedProjects.size} of ${uiState.visibleProjects.size} projects",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("explorer_sort_btn")
                    ) {
                        Icon(Icons.Filled.Sort, contentDescription = "Sort projects", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        ProjectSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onSortChange(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("explorer_create_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }

            OutlinedTextField(
                value = uiState.filter.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explorer_search_input"),
                placeholder = { Text("Search projects, tags, categories, purpose...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (uiState.filter.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            // Status Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProjectStatus.entries.forEach { status ->
                    FilterChip(
                        selected = uiState.filter.selectedStatus == status,
                        onClick = { onStatusSelect(status) },
                        label = { Text(status.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Category + Tag Filter Chips
            if (uiState.allCategories.isNotEmpty() || uiState.allTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.allCategories.forEach { cat ->
                        FilterChip(
                            selected = uiState.filter.selectedCategory == cat,
                            onClick = { onCategorySelect(cat) },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    uiState.allTags.take(10).forEach { tag ->
                        FilterChip(
                            selected = uiState.filter.selectedTag == tag,
                            onClick = { onTagSelect(tag) },
                            label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (uiState.filter.selectedCategory != null ||
                        uiState.filter.selectedTag != null ||
                        uiState.filter.selectedStatus != null ||
                        uiState.filter.query.isNotEmpty()
                    ) {
                        TextButton(onClick = onClearFilters) {
                            Text("Reset")
                        }
                    }
                }
            }
        }

        // Project List
        val filtered = uiState.filteredSortedProjects
        if (filtered.isEmpty()) {
            Column(modifier = Modifier.padding(16.dp)) {
                EmptyDeckState(
                    icon = Icons.Filled.FolderOpen,
                    title = "No matching projects",
                    description = "Adjust your filters or register a new project into DEX//COMMAND.",
                    actionLabel = "Create Project",
                    onAction = { showCreateDialog = true }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("explorer_project_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { project ->
                    val taskCount = uiState.workItems.count { it.projectId == project.id }
                    val isSelected = uiState.selectedProjectId == project.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProject(project.id) }
                            .testTag("project_card_${project.id}"),
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else dex.hairline
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProjectStatusBadge(project.status)
                                    PriorityPill(project.priority)
                                    if (project.isDemo) DemoRecordBadge()
                                }
                                Text(
                                    text = project.category,
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
                            if (project.description.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = project.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$taskCount task(s) · Reviewed ${formatShortDate(project.lastReviewDate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (project.tags.isNotEmpty()) {
                                    Text(
                                        text = project.tags.take(3).joinToString(" ") { "#$it" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(48.dp)) }
            }
        }
    }

    if (showCreateDialog) {
        ProjectEditorDialog(
            existing = null,
            onDismiss = { showCreateDialog = false },
            onSave = { project ->
                onSaveProject(project, true)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun ProjectEditorDialog(
    existing: ProjectEntity?,
    onDismiss: () -> Unit,
    onSave: (ProjectEntity) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var purpose by remember { mutableStateOf(existing?.purpose.orEmpty()) }
    var objective by remember { mutableStateOf(existing?.currentObjective.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: ProjectCategories.defaults.first()) }
    var priority by remember { mutableIntStateOf(existing?.priority ?: 2) }
    var repoUrl by remember { mutableStateOf(existing?.repositoryUrl.orEmpty()) }
    var driveUrl by remember { mutableStateOf(existing?.driveUrl.orEmpty()) }
    var tagsText by remember { mutableStateOf(existing?.tags?.joinToString(", ").orEmpty()) }
    var constraintsText by remember { mutableStateOf(existing?.protectedConstraints?.joinToString("\n").orEmpty()) }
    var failuresText by remember { mutableStateOf(existing?.knownFailures?.joinToString("\n").orEmpty()) }
    var decisionsText by remember { mutableStateOf(existing?.openDecisions?.joinToString("\n").orEmpty()) }
    var milestone by remember { mutableStateOf(existing?.lastVerifiedMilestone.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "Register New Project" else "Edit Project",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_editor_name")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    label = { Text("Current Objective") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProjectCategories.defaults.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text("P$p", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text("GitHub Repository URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = driveUrl,
                    onValueChange = { driveUrl = it },
                    label = { Text("Google Drive URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = constraintsText,
                    onValueChange = { constraintsText = it },
                    label = { Text("Protected Constraints (one per line)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = failuresText,
                    onValueChange = { failuresText = it },
                    label = { Text("Known Failures (one per line)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = decisionsText,
                    onValueChange = { decisionsText = it },
                    label = { Text("Open Decisions (one per line)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = milestone,
                    onValueChange = { milestone = it },
                    label = { Text("Last Verified Milestone") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    if (cleanName.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        val project = (existing ?: ProjectEntity(
                            id = "proj-${UUID.randomUUID().toString().take(8)}",
                            name = cleanName,
                            isDemo = false,
                            createdAt = now,
                            lastReviewDate = now,
                            updatedAt = now
                        )).copy(
                            name = cleanName,
                            description = description.trim(),
                            purpose = purpose.trim(),
                            currentObjective = objective.trim(),
                            category = category,
                            priority = priority,
                            repositoryUrl = repoUrl.trim(),
                            driveUrl = driveUrl.trim(),
                            tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            protectedConstraints = constraintsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                            knownFailures = failuresText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                            openDecisions = decisionsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                            lastVerifiedMilestone = milestone.trim(),
                            updatedAt = now
                        )
                        onSave(project)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("project_editor_save")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
