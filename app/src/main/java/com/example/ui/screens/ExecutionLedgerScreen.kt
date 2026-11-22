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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.local.ExecutionRecordEntity
import com.example.domain.model.EvidenceState
import com.example.ui.DexUiState
import com.example.ui.components.CommandPanel
import com.example.ui.components.DemoRecordBadge
import com.example.ui.components.DestructiveConfirmDialog
import com.example.ui.components.EmptyDeckState
import com.example.ui.components.EvidenceStateBadge
import com.example.ui.components.formatShortDate
import com.example.ui.theme.LocalDexColors

@Composable
fun ExecutionLedgerScreen(
    uiState: DexUiState,
    onAddRecord: (
        projectId: String,
        workItemId: String?,
        toolOrAgent: String,
        actionAttempted: String,
        claim: String,
        evidence: String,
        requestedStatus: EvidenceState,
        aiGeneratedStatement: Boolean,
        humanConfirmedEvidence: Boolean
    ) -> Unit,
    onVerifyRecord: (ExecutionRecordEntity, String) -> Unit,
    onDeleteRecord: (String) -> Unit
) {
    val dex = LocalDexColors.current
    var selectedStatusFilter by remember { mutableStateOf<EvidenceState?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var verifyingRecord by remember { mutableStateOf<ExecutionRecordEntity?>(null) }
    var deletingRecord by remember { mutableStateOf<ExecutionRecordEntity?>(null) }

    val projectById = uiState.projects.associateBy { it.id }
    val filteredRecords = uiState.visibleExecutionRecords.filter { rec ->
        selectedStatusFilter == null || rec.verificationStatus == selectedStatusFilter
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("EXECUTION LEDGER", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Never assume claimed completion is verified.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = { showAddDialog = true },
                    enabled = uiState.projects.isNotEmpty(),
                    modifier = Modifier.testTag("ledger_log_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Log Attempt")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedStatusFilter == null,
                    onClick = { selectedStatusFilter = null },
                    label = { Text("ALL (${uiState.visibleExecutionRecords.size})", style = MaterialTheme.typography.labelSmall) }
                )
                EvidenceState.entries.forEach { state ->
                    FilterChip(
                        selected = selectedStatusFilter == state,
                        onClick = { selectedStatusFilter = if (selectedStatusFilter == state) null else state },
                        label = { Text(state.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        if (filteredRecords.isEmpty()) {
            Column(modifier = Modifier.padding(16.dp)) {
                EmptyDeckState(
                    icon = Icons.Filled.FactCheck,
                    title = "No execution records match",
                    description = "Log agent attempts, claims, and independent verification evidence.",
                    actionLabel = if (uiState.projects.isNotEmpty()) "Log Attempt" else null,
                    onAction = if (uiState.projects.isNotEmpty()) ({ showAddDialog = true }) else null
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("ledger_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRecords, key = { it.id }) { rec ->
                    val project = projectById[rec.projectId]
                    CommandPanel(
                        borderColor = when (rec.verificationStatus) {
                            EvidenceState.VERIFIED -> dex.verified.copy(alpha = 0.45f)
                            EvidenceState.FAILED -> dex.blocked.copy(alpha = 0.55f)
                            EvidenceState.CLAIMED -> dex.claimed.copy(alpha = 0.45f)
                            else -> dex.hairline
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EvidenceStateBadge(rec.verificationStatus)
                                if (rec.aiGeneratedStatement) {
                                    Text(
                                        text = "AI CLAIM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = dex.review
                                    )
                                }
                                if (rec.isDemo) DemoRecordBadge()
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatShortDate(rec.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(onClick = { deletingRecord = rec }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete record", tint = dex.blocked, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Text(
                            text = "${project?.name ?: rec.projectId} · ${rec.toolOrAgent}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = rec.actionAttempted,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (rec.claim.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Claim: ${rec.claim}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (rec.evidence.isNotBlank()) "Evidence: ${rec.evidence}" else "Evidence: NONE RECORDED",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (rec.evidence.isNotBlank()) dex.verified else dex.review
                        )

                        if (rec.verificationStatus != EvidenceState.VERIFIED) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { verifyingRecord = rec },
                                modifier = Modifier.testTag("verify_record_${rec.id}")
                            ) {
                                Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Verify with Evidence")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(48.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddExecutionRecordDialog(
            uiState = uiState,
            onDismiss = { showAddDialog = false },
            onSubmit = { projectId, workItemId, agent, action, claim, evidence, status, aiStmt, humanConfirmed ->
                onAddRecord(projectId, workItemId, agent, action, claim, evidence, status, aiStmt, humanConfirmed)
                showAddDialog = false
            }
        )
    }

    verifyingRecord?.let { rec ->
        var evidenceInput by remember(rec.id) { mutableStateOf(rec.evidence) }
        AlertDialog(
            onDismissRequest = { verifyingRecord = null },
            title = { Text("Independent Evidence Verification") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Provide concrete observed logs, exit codes, or test artifacts confirming: ${rec.actionAttempted}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = evidenceInput,
                        onValueChange = { evidenceInput = it },
                        label = { Text("Observed Verification Evidence *") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onVerifyRecord(rec, evidenceInput)
                        verifyingRecord = null
                    },
                    enabled = evidenceInput.isNotBlank()
                ) {
                    Text("Mark VERIFIED")
                }
            },
            dismissButton = {
                TextButton(onClick = { verifyingRecord = null }) { Text("Cancel") }
            }
        )
    }

    deletingRecord?.let { rec ->
        DestructiveConfirmDialog(
            title = "Delete Execution Record?",
            message = "Delete ledger entry '${rec.actionAttempted}'?",
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteRecord(rec.id)
                deletingRecord = null
            },
            onDismiss = { deletingRecord = null }
        )
    }
}

@Composable
private fun AddExecutionRecordDialog(
    uiState: DexUiState,
    onDismiss: () -> Unit,
    onSubmit: (
        projectId: String,
        workItemId: String?,
        toolOrAgent: String,
        actionAttempted: String,
        claim: String,
        evidence: String,
        requestedStatus: EvidenceState,
        aiGeneratedStatement: Boolean,
        humanConfirmedEvidence: Boolean
    ) -> Unit
) {
    val defaultProjId = uiState.selectedProjectId ?: uiState.projects.firstOrNull()?.id.orEmpty()
    var projectId by remember { mutableStateOf(defaultProjId) }
    var agent by remember { mutableStateOf("Claude Code") }
    var actionAttempted by remember { mutableStateOf("") }
    var claim by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf("") }
    var requestedStatus by remember { mutableStateOf(EvidenceState.CLAIMED) }
    var aiGenerated by remember { mutableStateOf(true) }
    var humanConfirmed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Execution Attempt") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Project", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.projects.forEach { p ->
                        FilterChip(
                            selected = projectId == p.id,
                            onClick = { projectId = p.id },
                            label = { Text(p.name.take(22), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = agent,
                    onValueChange = { agent = it },
                    label = { Text("Tool or Agent *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = actionAttempted,
                    onValueChange = { actionAttempted = it },
                    label = { Text("Exact Action Attempted *") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = claim,
                    onValueChange = { claim = it },
                    label = { Text("Claimed Outcome") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = evidence,
                    onValueChange = { evidence = it },
                    label = { Text("Concrete Evidence (logs, exit code, output)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Verification Status", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EvidenceState.entries.forEach { st ->
                        FilterChip(
                            selected = requestedStatus == st,
                            onClick = { requestedStatus = st },
                            label = { Text(st.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = aiGenerated, onCheckedChange = { aiGenerated = it })
                    Text("Claim originated from AI agent statement", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = humanConfirmed, onCheckedChange = { humanConfirmed = it })
                    Text("Independently verified by operator", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(projectId, null, agent, actionAttempted, claim, evidence, requestedStatus, aiGenerated, humanConfirmed)
                },
                enabled = projectId.isNotBlank() && actionAttempted.isNotBlank()
            ) {
                Text("Record")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
