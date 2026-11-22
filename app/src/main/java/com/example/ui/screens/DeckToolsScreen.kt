package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.local.ImportedDocumentEntity
import com.example.data.remote.FirebaseAuthHelper
import com.example.data.remote.SyncOutcome
import com.example.ui.AsyncPanelState
import com.example.ui.DexUiState
import com.example.ui.components.CommandPanel
import com.example.ui.components.DemoRecordBadge
import com.example.ui.components.DestructiveConfirmDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.formatShortDate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalDexColors
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

@Composable
fun DeckToolsScreen(
    uiState: DexUiState,
    syncState: AsyncPanelState<SyncOutcome>,
    aiOutputState: AsyncPanelState<String>,
    onImportDocumentClick: () -> Unit,
    onExportJsonBackupClick: () -> Unit,
    onRestoreJsonBackupClick: () -> Unit,
    onExportMarkdownReportClick: () -> Unit,
    onAiSummarizeDocument: (ImportedDocumentEntity) -> Unit,
    onAiAuditInconsistencies: () -> Unit,
    onDeleteImportedDocument: (String) -> Unit,
    onSyncWithFirestore: () -> Unit,
    onSetLowStimulation: (Boolean) -> Unit,
    onSetReducedMotion: (Boolean) -> Unit,
    onSetHideDemoRecords: (Boolean) -> Unit,
    onSetPeriodicAudit: (Boolean) -> Unit,
    onRunAuditNow: () -> Unit,
    onPurgeDemoData: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val dex = LocalDexColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    var viewingDocument by remember { mutableStateOf<ImportedDocumentEntity?>(null) }
    var deletingDocument by remember { mutableStateOf<ImportedDocumentEntity?>(null) }
    var showPurgeDemoConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tools_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("DECK TOOLS · DATA · SYNC · AI", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Offline-first SAF import/export, optional Firebase sync, and scoped Gemini analysis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. File Import & Backup / Export (SAF Zero-Permission)
        item {
            CommandPanel {
                SectionHeader(
                    title = "Import, Export & Backup (SAF)",
                    subtitle = "Imports preserve raw original content immutably; failed restores never corrupt data"
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onImportDocumentClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tools_import_doc_btn")
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import MD/TXT/JSON")
                    }
                    OutlinedButton(
                        onClick = onExportMarkdownReportClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tools_export_report_btn")
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("MD Report")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportJsonBackupClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tools_export_backup_btn")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("JSON Backup")
                    }
                    OutlinedButton(
                        onClick = onRestoreJsonBackupClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tools_restore_backup_btn")
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restore JSON")
                    }
                }
            }
        }

        // 2. Preserved Imported Documents
        item {
            SectionHeader(
                title = "Preserved Imported Documents (${uiState.visibleImportedDocuments.size})",
                subtitle = "Original source files are stored read-only and never overwritten automatically"
            )
        }
        if (uiState.visibleImportedDocuments.isEmpty()) {
            item {
                Text(
                    text = "No imported project bibles, operational-state files, or handoffs yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(uiState.visibleImportedDocuments, key = { it.id }) { doc ->
                CommandPanel(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(doc.fileName, style = MaterialTheme.typography.titleSmall)
                                if (doc.isDemo) DemoRecordBadge()
                            }
                            Text(
                                text = "${doc.format} · SHA-256 ${doc.contentHash.take(12)} · ${formatShortDate(doc.importedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(onClick = { viewingDocument = doc }) {
                                Icon(Icons.Filled.Visibility, contentDescription = "View raw document")
                            }
                            IconButton(onClick = { deletingDocument = doc }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete document", tint = dex.blocked)
                            }
                        }
                    }
                    if (!doc.aiSummary.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Advisory Gemini Summary:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(doc.aiSummary, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onAiSummarizeDocument(doc) }) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Summarize with Gemini (Preserves Original)")
                    }
                }
            }
        }

        // 3. Optional Firebase AI Logic Analysis
        item {
            CommandPanel(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) {
                SectionHeader(
                    title = "Optional Gemini Analysis (Firebase AI Logic)",
                    subtitle = "Advisory only · Never auto-executes prompts or marks records VERIFIED"
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onAiAuditInconsistencies,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tools_gemini_audit_btn")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Audit Possibly Inconsistent Status Records")
                }
                Spacer(Modifier.height(8.dp))
                when (aiOutputState) {
                    AsyncPanelState.Idle -> {
                        Text(
                            text = "Run an on-demand Gemini audit to flag mismatched project statuses, unverified AI claims, or summarize imported docs above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AsyncPanelState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    is AsyncPanelState.Success -> {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.dp, dex.hairline)
                        ) {
                            Text(
                                text = aiOutputState.data,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    is AsyncPanelState.Error -> {
                        Text(
                            text = aiOutputState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = dex.blocked
                        )
                    }
                }
            }
        }

        // 4. Optional Cross-Device Firebase Sync (Strictly Gated Behind Google Sign-In)
        item {
            CommandPanel {
                SectionHeader(
                    title = "Optional Cloud Sync (Firebase Firestore)",
                    subtitle = "User-approved only · Conflict-safe timestamp merge never overwrites newer records"
                )
                Spacer(Modifier.height(10.dp))

                if (currentUser == null) {
                    Text(
                        text = "Not signed in. DEX//COMMAND operates 100% offline locally. Sign in with Google only if you want optional cross-device Firestore synchronization.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (authError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(authError!!, style = MaterialTheme.typography.bodySmall, color = dex.blocked)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            isSigningIn = true
                            authError = null
                            FirebaseAuthHelper.onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isSigningIn = false
                                    currentUser = Firebase.auth.currentUser
                                    onShowMessage("Signed in with Google for optional Firestore sync.")
                                },
                                onAuthError = { msg ->
                                    isSigningIn = false
                                    authError = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isSigningIn = false
                                }
                            )
                        },
                        enabled = !isSigningIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tools_google_signin_btn")
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in with Google")
                        }
                    }
                } else {
                    val user = currentUser!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SIGNED IN FOR OPTIONAL SYNC", style = MaterialTheme.typography.labelSmall, color = dex.verified)
                            Text(user.email ?: user.uid, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Last sync: ${formatShortDate(uiState.preferences.lastSyncTimestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                FirebaseAuthHelper.signOut(credentialManager, scope) {
                                    currentUser = null
                                    onShowMessage("Signed out of optional cloud sync.")
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sign Out")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onSyncWithFirestore,
                        enabled = syncState !is AsyncPanelState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tools_sync_now_btn")
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync Non-Demo Records Now (Conflict-Safe)")
                    }
                    when (syncState) {
                        AsyncPanelState.Loading -> {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        is AsyncPanelState.Success -> {
                            val r = syncState.data
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Synced cleanly: Pushed ${r.pushedProjects}P/${r.pushedWorkItems}T/${r.pushedRecords}R · Pulled ${r.pulledProjects}P/${r.pulledWorkItems}T · Preserved newer local (${r.skippedNewerLocal}) & remote (${r.skippedNewerRemote}).",
                                style = MaterialTheme.typography.bodySmall,
                                color = dex.verified
                            )
                        }
                        is AsyncPanelState.Error -> {
                            Spacer(Modifier.height(8.dp))
                            Text(syncState.message, style = MaterialTheme.typography.bodySmall, color = dex.blocked)
                        }
                        AsyncPanelState.Idle -> Unit
                    }
                }
            }
        }

        // 5. Accessibility, WorkManager & Demo Management
        item {
            CommandPanel {
                SectionHeader(title = "Accessibility & Reliability Controls")
                Spacer(Modifier.height(10.dp))

                PreferenceSwitchRow(
                    title = "Low-Stimulation Theme",
                    subtitle = "Softer matte contrast with muted desaturated accents and zero visual glare.",
                    checked = uiState.preferences.lowStimulation,
                    onCheckedChange = onSetLowStimulation,
                    testTag = "switch_low_stim"
                )
                PreferenceSwitchRow(
                    title = "Reduced Motion",
                    subtitle = "Disables non-essential transitions and animated effects.",
                    checked = uiState.preferences.reducedMotion,
                    onCheckedChange = onSetReducedMotion,
                    testTag = "switch_reduced_motion"
                )
                PreferenceSwitchRow(
                    title = "Hide [DEMO] Demonstration Records",
                    subtitle = "Filter fictional first-run demo data out of all views without deleting.",
                    checked = uiState.preferences.hideDemoRecords,
                    onCheckedChange = onSetHideDemoRecords,
                    testTag = "switch_hide_demo"
                )
                PreferenceSwitchRow(
                    title = "WorkManager Periodic Review Audit",
                    subtitle = "Runs offline 12-hour integrity checks for overdue reviews and status mismatches.",
                    checked = uiState.preferences.periodicAuditEnabled,
                    onCheckedChange = onSetPeriodicAudit,
                    testTag = "switch_periodic_audit"
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Last Audit (${formatShortDate(uiState.preferences.lastAuditTimestamp)}): ${uiState.preferences.lastAuditSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRunAuditNow) {
                        Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Run Offline Audit")
                    }
                    if (uiState.projects.any { it.isDemo }) {
                        OutlinedButton(
                            onClick = { showPurgeDemoConfirm = true },
                            modifier = Modifier.testTag("tools_purge_demo_btn")
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = dex.blocked, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Purge Demo Data", color = dex.blocked)
                        }
                    }
                }
            }
        }

        // 6. Immutable Audit History
        item {
            SectionHeader(
                title = "Audit History (${uiState.auditEvents.size})",
                subtitle = "Chronological local operational audit log"
            )
        }
        items(uiState.auditEvents.take(15), key = { it.id }) { evt ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, dex.hairline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "[${evt.entityType} · ${evt.action}] ${formatShortDate(evt.timestamp)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(evt.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }

    viewingDocument?.let { doc ->
        AlertDialog(
            onDismissRequest = { viewingDocument = null },
            title = { Text("${doc.fileName} (Read-Only Original)") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "SHA-256: ${doc.contentHash}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = doc.rawContent,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingDocument = null }) { Text("Close") }
            }
        )
    }

    deletingDocument?.let { doc ->
        DestructiveConfirmDialog(
            title = "Delete Imported Document?",
            message = "Remove preserved original '${doc.fileName}' from local storage?",
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteImportedDocument(doc.id)
                deletingDocument = null
            },
            onDismiss = { deletingDocument = null }
        )
    }

    if (showPurgeDemoConfirm) {
        DestructiveConfirmDialog(
            title = "Purge All Demonstration Data?",
            message = "This removes all fictional [DEMO] projects, tasks, execution records, dependencies, and demo documents. Your own user records are untouched.",
            confirmLabel = "Purge Demo Data",
            onConfirm = {
                onPurgeDemoData()
                showPurgeDemoConfirm = false
            },
            onDismiss = { showPurgeDemoConfirm = false }
        )
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
