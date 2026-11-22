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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.domain.model.AgentTarget
import com.example.ui.AsyncPanelState
import com.example.ui.DexUiState
import com.example.ui.components.CommandPanel
import com.example.ui.components.EmptyDeckState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDexColors

@Composable
fun RecoveryAndPromptScreen(
    uiState: DexUiState,
    initialMode: Int, // 0 = Continuity Recovery, 1 = Prompt Generator
    initialProjectId: String?,
    initialWorkItemId: String?,
    aiPromptDraftState: AsyncPanelState<String>,
    buildContinuationPacket: (String) -> String,
    buildAgentPrompt: (AgentTarget, String, String?, String) -> String,
    onAiRefinePrompt: (String) -> Unit,
    onClearAiDraft: () -> Unit,
    onShareMarkdown: (title: String, body: String) -> Unit,
    onExportMarkdownFile: (suggestedFileName: String, content: String) -> Unit,
    onCopiedMessage: (String) -> Unit
) {
    val dex = LocalDexColors.current
    val clipboard = LocalClipboardManager.current

    var activeMode by remember(initialMode) { mutableIntStateOf(initialMode) }
    var selectedProjectId by remember(initialProjectId, uiState.projects) {
        mutableStateOf(
            initialProjectId?.takeIf { id -> uiState.projects.any { it.id == id } }
                ?: uiState.selectedProjectId
                ?: uiState.projects.firstOrNull()?.id
        )
    }
    var selectedWorkItemId by remember(initialWorkItemId, selectedProjectId) {
        mutableStateOf(initialWorkItemId)
    }
    var selectedAgent by remember { mutableStateOf(AgentTarget.CODEX) }
    var operatorNote by remember { mutableStateOf("") }

    LaunchedEffect(selectedProjectId) {
        onClearAiDraft()
    }

    if (uiState.projects.isEmpty() || selectedProjectId == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            EmptyDeckState(
                icon = Icons.Filled.Description,
                title = "No projects available",
                description = "Create or import a project to generate continuity packets and standalone coding-agent prompts."
            )
        }
        return
    }

    val project = uiState.projects.firstOrNull { it.id == selectedProjectId } ?: uiState.projects.first()
    val projectWorkItems = uiState.workItems.filter { it.projectId == project.id }
    val validWorkItemId = selectedWorkItemId?.takeIf { id -> projectWorkItems.any { it.id == id } }

    val deterministicOutput = remember(
        activeMode,
        project,
        validWorkItemId,
        selectedAgent,
        operatorNote,
        uiState.workItems,
        uiState.executionRecords,
        uiState.dependencyLinks
    ) {
        if (activeMode == 0) {
            buildContinuationPacket(project.id)
        } else {
            buildAgentPrompt(selectedAgent, project.id, validWorkItemId, operatorNote)
        }
    }

    val displayedOutput = if (activeMode == 1 && aiPromptDraftState is AsyncPanelState.Success) {
        aiPromptDraftState.data
    } else {
        deterministicOutput
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("recovery_prompt_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (activeMode == 0) "CONTINUITY RECOVERY" else "AGENT PROMPT GENERATOR",
                    style = MaterialTheme.typography.headlineSmall
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = activeMode == 0,
                        onClick = { activeMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("mode_continuity_btn")
                    ) {
                        Text("Continuity Packet")
                    }
                    SegmentedButton(
                        selected = activeMode == 1,
                        onClick = { activeMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("mode_prompt_btn")
                    ) {
                        Text("Agent Prompt")
                    }
                }
            }
        }

        // Project Selector
        item {
            CommandPanel {
                Text("TARGET PROJECT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.visibleProjects.forEach { p ->
                        FilterChip(
                            selected = p.id == project.id,
                            onClick = {
                                selectedProjectId = p.id
                                selectedWorkItemId = null
                            },
                            label = { Text(p.name.take(24), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (activeMode == 1) {
                    Spacer(Modifier.height(10.dp))
                    Text("TARGET CODING AGENT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AgentTarget.entries.forEach { target ->
                            FilterChip(
                                selected = selectedAgent == target,
                                onClick = {
                                    selectedAgent = target
                                    onClearAiDraft()
                                },
                                label = { Text(target.displayName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (projectWorkItems.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("BOUNDED WORK ITEM (OPTIONAL)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = validWorkItemId == null,
                                onClick = { selectedWorkItemId = null },
                                label = { Text("Project Objective", style = MaterialTheme.typography.labelSmall) }
                            )
                            projectWorkItems.forEach { wi ->
                                FilterChip(
                                    selected = validWorkItemId == wi.id,
                                    onClick = { selectedWorkItemId = wi.id },
                                    label = { Text("P${wi.priority}: ${wi.title.take(24)}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = operatorNote,
                        onValueChange = { operatorNote = it },
                        label = { Text("Operator Scope Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Export / Share / Copy / Optional Gemini Refine Controls
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(displayedOutput))
                        onCopiedMessage(if (activeMode == 0) "Continuity packet copied." else "Agent prompt copied.")
                    },
                    modifier = Modifier.testTag("recovery_copy_btn")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = {
                        val title = if (activeMode == 0) {
                            "DEX//COMMAND Continuity — ${project.name}"
                        } else {
                            "DEX//COMMAND Prompt (${selectedAgent.displayName}) — ${project.name}"
                        }
                        onShareMarkdown(title, displayedOutput)
                    },
                    modifier = Modifier.testTag("recovery_share_btn")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = {
                        val slug = project.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                        val fileName = if (activeMode == 0) "${slug}_continuity.md" else "${slug}_${selectedAgent.name.lowercase()}_prompt.md"
                        onExportMarkdownFile(fileName, displayedOutput)
                    },
                    modifier = Modifier.testTag("recovery_export_md_btn")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export .md")
                }
                if (activeMode == 1) {
                    OutlinedButton(
                        onClick = { onAiRefinePrompt(deterministicOutput) },
                        modifier = Modifier.testTag("prompt_gemini_refine_btn")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Gemini Refine")
                    }
                }
            }
        }

        if (activeMode == 1 && aiPromptDraftState is AsyncPanelState.Loading) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
        if (activeMode == 1 && aiPromptDraftState is AsyncPanelState.Error) {
            item {
                Text(
                    text = "Gemini draft error: ${aiPromptDraftState.message} (Showing grounded local prompt below.)",
                    style = MaterialTheme.typography.bodySmall,
                    color = dex.blocked
                )
            }
        }
        if (activeMode == 1 && aiPromptDraftState is AsyncPanelState.Success) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "GEMINI-REFINED DRAFT (NEVER AUTO-EXECUTED)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(onClick = onClearAiDraft) {
                        Text("Revert Local")
                    }
                }
            }
        }

        // Grounded Markdown Output Preview
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, dex.hairline)
            ) {
                SelectionContainer {
                    Text(
                        text = displayedOutput,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(14.dp)
                            .testTag("recovery_markdown_preview")
                    )
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}
