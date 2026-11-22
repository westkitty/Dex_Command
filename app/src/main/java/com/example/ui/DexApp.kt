package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.ProjectEntity
import com.example.ui.components.DemoRecordBadge
import com.example.ui.components.DestructiveConfirmDialog
import com.example.ui.components.PriorityPill
import com.example.ui.components.ProjectStatusBadge
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeckToolsScreen
import com.example.ui.screens.DependencyViewScreen
import com.example.ui.screens.ExecutionLedgerScreen
import com.example.ui.screens.ProjectDetailScreen
import com.example.ui.screens.ProjectEditorDialog
import com.example.ui.screens.ProjectExplorerScreen
import com.example.ui.screens.RecoveryAndPromptScreen
import com.example.ui.theme.DexCommandTheme
import com.example.ui.theme.LocalDexColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_EXPLORER = "explorer"
const val ROUTE_DETAIL = "detail"
const val ROUTE_LEDGER = "ledger"
const val ROUTE_DEPENDENCIES = "dependencies"
const val ROUTE_RECOVERY = "recovery"
const val ROUTE_TOOLS = "tools"

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val phoneDestinations = listOf(
    NavDestinationItem(ROUTE_DASHBOARD, "Deck", Icons.Filled.Dashboard, "nav_dashboard"),
    NavDestinationItem(ROUTE_EXPLORER, "Projects", Icons.Filled.Folder, "nav_explorer"),
    NavDestinationItem(ROUTE_LEDGER, "Ledger", Icons.Filled.FactCheck, "nav_ledger"),
    NavDestinationItem(ROUTE_DEPENDENCIES, "Deps", Icons.Filled.AccountTree, "nav_dependencies"),
    NavDestinationItem(ROUTE_TOOLS, "Tools", Icons.Filled.Build, "nav_tools")
)

private val tabletDestinations = listOf(
    NavDestinationItem(ROUTE_DASHBOARD, "Deck", Icons.Filled.Dashboard, "nav_dashboard"),
    NavDestinationItem(ROUTE_EXPLORER, "Projects", Icons.Filled.Folder, "nav_explorer"),
    NavDestinationItem(ROUTE_LEDGER, "Ledger", Icons.Filled.FactCheck, "nav_ledger"),
    NavDestinationItem(ROUTE_DEPENDENCIES, "Deps", Icons.Filled.AccountTree, "nav_dependencies"),
    NavDestinationItem(ROUTE_RECOVERY, "Recovery", Icons.Filled.Description, "nav_recovery"),
    NavDestinationItem(ROUTE_TOOLS, "Tools", Icons.Filled.Build, "nav_tools")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexCommandApp(
    viewModel: DexViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val aiOutputState by viewModel.aiOutputState.collectAsStateWithLifecycle()
    val aiPromptDraftState by viewModel.aiPromptDraftState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val githubState by viewModel.githubState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recovery & Prompt Studio deep-link parameters
    var recoveryInitialMode by remember { mutableIntStateOf(0) }
    var recoveryProjectId by remember { mutableStateOf<String?>(null) }
    var recoveryWorkItemId by remember { mutableStateOf<String?>(null) }

    // Global Create Project & Quick Switcher dialogs
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var showQuickSwitcherSheet by remember { mutableStateOf(false) }
    var showRightInspectorPane by remember { mutableStateOf(true) }

    // Pending SAF Export Content
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }

    // SAF Launcher: Import Markdown / TXT / JSON Document
    val importDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val (fileName, content) = readTextFromUri(context, uri)
                if (content != null) {
                    viewModel.importDocument(
                        fileName = fileName,
                        content = content,
                        targetProjectId = null,
                        createDraft = true
                    )
                } else {
                    viewModel.showMessage("Unable to read selected document.")
                }
            }
        }
    }

    // SAF Launcher: Restore JSON Backup (requires explicit confirmation before overwriting)
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val (_, content) = readTextFromUri(context, uri)
                if (!content.isNullOrBlank()) {
                    pendingRestoreJson = content
                } else {
                    viewModel.showMessage("Selected backup file is empty or unreadable.")
                }
            }
        }
    }

    // SAF Launcher: Export JSON Backup
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val content = pendingExportContent
        pendingExportContent = null
        if (uri != null && content != null) {
            scope.launch {
                val ok = writeTextToUri(context, uri, content)
                viewModel.showMessage(if (ok) "JSON backup exported cleanly." else "Failed to write backup file.")
            }
        }
    }

    // SAF Launcher: Export Markdown File
    val exportMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        val content = pendingExportContent
        pendingExportContent = null
        if (uri != null && content != null) {
            scope.launch {
                val ok = writeTextToUri(context, uri, content)
                viewModel.showMessage(if (ok) "Markdown file exported cleanly." else "Failed to write Markdown file.")
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    DexCommandTheme(
        lowStimulation = uiState.preferences.lowStimulation,
        reducedMotion = uiState.preferences.reducedMotion
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_DASHBOARD

        fun navigateTopLevel(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        fun openProjectDetail(projectId: String) {
            viewModel.selectProject(projectId)
            navController.navigate(ROUTE_DETAIL) {
                launchSingleTop = true
            }
        }

        fun openRecoveryStudio(mode: Int, projectId: String?, workItemId: String? = null) {
            recoveryInitialMode = mode
            recoveryProjectId = projectId
            recoveryWorkItemId = workItemId
            if (projectId != null) viewModel.selectProject(projectId)
            navController.navigate(ROUTE_RECOVERY) {
                launchSingleTop = true
            }
        }

        fun shareMarkdownText(title: String, body: String) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(Intent.createChooser(sendIntent, title))
        }

        fun openExternalUrl(url: String) {
            val trimmed = url.trim()
            if (!trimmed.startsWith("https://")) {
                viewModel.showMessage("Blocked non-HTTPS URL for safety: $trimmed")
                return
            }
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
                context.startActivity(intent)
            } catch (_: Exception) {
                viewModel.showMessage("No application available to open $trimmed")
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isExpandedTablet = maxWidth >= 840.dp
            val isUltraWide = maxWidth >= 1120.dp
            val useRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (!useRail) {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "DEX//COMMAND",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            actions = {
                                IconButton(
                                    onClick = {
                                        openRecoveryStudio(0, uiState.selectedProjectId)
                                    },
                                    modifier = Modifier.testTag("topbar_recovery_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Description,
                                        contentDescription = "Continuity Recovery and Prompt Generator",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { showQuickSwitcherSheet = true },
                                    modifier = Modifier.testTag("topbar_switcher_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SwapHoriz,
                                        contentDescription = "Quick Project Switcher"
                                    )
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            phoneDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route ||
                                    (dest.route == ROUTE_EXPLORER && currentRoute == ROUTE_DETAIL)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Tablet & Medium NavigationRail
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            header = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "DEX//",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (isUltraWide) {
                                        IconButton(
                                            onClick = { showRightInspectorPane = !showRightInspectorPane },
                                            modifier = Modifier.testTag("tablet_toggle_inspector")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.VerticalSplit,
                                                contentDescription = "Toggle right continuity inspector pane"
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            tabletDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route ||
                                    (dest.route == ROUTE_EXPLORER && currentRoute == ROUTE_DETAIL)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                        VerticalDivider(thickness = 1.dp, color = LocalDexColors.current.hairline)
                    }

                    // Expanded Tablet Persistent Project Navigator (Pane 1)
                    if (isExpandedTablet) {
                        PersistentProjectNavigator(
                            projects = uiState.visibleProjects,
                            selectedProjectId = uiState.selectedProjectId,
                            onSelectProject = { id ->
                                viewModel.selectProject(id)
                                if (currentRoute != ROUTE_DETAIL && currentRoute != ROUTE_RECOVERY) {
                                    openProjectDetail(id)
                                }
                            },
                            modifier = Modifier
                                .width(272.dp)
                                .fillMaxHeight()
                        )
                        VerticalDivider(thickness = 1.dp, color = LocalDexColors.current.hairline)
                    }

                    // Primary Workspace (Pane 2)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        DexNavHost(
                            navController = navController,
                            uiState = uiState,
                            isExpandedTablet = isExpandedTablet,
                            aiOutputState = aiOutputState,
                            aiPromptDraftState = aiPromptDraftState,
                            syncState = syncState,
                            githubState = githubState,
                            recoveryInitialMode = recoveryInitialMode,
                            recoveryProjectId = recoveryProjectId,
                            recoveryWorkItemId = recoveryWorkItemId,
                            viewModel = viewModel,
                            onOpenProject = { openProjectDetail(it) },
                            onOpenExplorerWithStatus = { status ->
                                viewModel.updateStatusFilter(status)
                                navigateTopLevel(ROUTE_EXPLORER)
                            },
                            onOpenCreateProject = { showCreateProjectDialog = true },
                            onOpenRecovery = { openRecoveryStudio(0, it) },
                            onOpenPromptForTask = { pid, wid -> openRecoveryStudio(1, pid, wid) },
                            onOpenUrl = ::openExternalUrl,
                            onShareMarkdown = ::shareMarkdownText,
                            onExportMarkdownFile = { fileName, content ->
                                pendingExportContent = content
                                exportMarkdownLauncher.launch(fileName)
                            },
                            onImportDocumentClick = {
                                importDocLauncher.launch(arrayOf("text/*", "application/json", "application/octet-stream"))
                            },
                            onExportJsonBackupClick = {
                                scope.launch {
                                    val json = viewModel.exportBackupJson()
                                    pendingExportContent = json
                                    exportJsonLauncher.launch("dex_command_backup.json")
                                }
                            },
                            onRestoreJsonBackupClick = {
                                restoreBackupLauncher.launch(arrayOf("application/json", "text/*"))
                            },
                            onExportMarkdownReportClick = {
                                val report = viewModel.buildPortfolioMarkdownReport()
                                pendingExportContent = report
                                exportMarkdownLauncher.launch("dex_command_portfolio_report.md")
                            }
                        )
                    }

                    // Ultra-Wide Tablet Third Pane (Tab S9 Ultra Inspector: Instant Continuity & Prompt Preview)
                    if (isUltraWide && showRightInspectorPane && currentRoute != ROUTE_RECOVERY && uiState.selectedProjectId != null) {
                        VerticalDivider(thickness = 1.dp, color = LocalDexColors.current.hairline)
                        Box(
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight()
                        ) {
                            RecoveryAndPromptScreen(
                                uiState = uiState,
                                initialMode = 0,
                                initialProjectId = uiState.selectedProjectId,
                                initialWorkItemId = null,
                                aiPromptDraftState = aiPromptDraftState,
                                buildContinuationPacket = viewModel::buildContinuationPacket,
                                buildAgentPrompt = viewModel::buildAgentPrompt,
                                onAiRefinePrompt = viewModel::aiRefineHandoffPrompt,
                                onClearAiDraft = viewModel::clearAiPromptDraft,
                                onShareMarkdown = ::shareMarkdownText,
                                onExportMarkdownFile = { fileName, content ->
                                    pendingExportContent = content
                                    exportMarkdownLauncher.launch(fileName)
                                },
                                onCopiedMessage = viewModel::showMessage
                            )
                        }
                    }
                }
            }
        }

        if (showCreateProjectDialog) {
            ProjectEditorDialog(
                existing = null,
                onDismiss = { showCreateProjectDialog = false },
                onSave = { project ->
                    viewModel.saveProject(project, isNew = true)
                    showCreateProjectDialog = false
                }
            )
        }

        if (showQuickSwitcherSheet) {
            ModalBottomSheet(onDismissRequest = { showQuickSwitcherSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Text(
                        text = "FAST PROJECT SWITCHER",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(uiState.visibleProjects, key = { it.id }) { project ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showQuickSwitcherSheet = false
                                        openProjectDetail(project.id)
                                    },
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, LocalDexColors.current.hairline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(project.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(project.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (project.isDemo) DemoRecordBadge()
                                        ProjectStatusBadge(project.status)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        pendingRestoreJson?.let { rawJson ->
            DestructiveConfirmDialog(
                title = "Restore JSON Backup?",
                message = "Restoring validates SHA-256 integrity first and then replaces local projects, tasks, and ledger records. Failed or corrupt files will not alter existing records.",
                confirmLabel = "Validate & Restore",
                onConfirm = {
                    viewModel.restoreBackupJson(rawJson)
                    pendingRestoreJson = null
                },
                onDismiss = { pendingRestoreJson = null }
            )
        }
    }
}

@Composable
private fun PersistentProjectNavigator(
    projects: List<ProjectEntity>,
    selectedProjectId: String?,
    onSelectProject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dex = LocalDexColors.current
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        Text(
            text = "PROJECT NAVIGATOR",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "${projects.size} tracked",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = dex.hairline)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(projects, key = { it.id }) { project ->
                val isSelected = project.id == selectedProjectId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectProject(project.id) },
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else dex.hairline
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PriorityPill(project.priority)
                            ProjectStatusBadge(project.status)
                            if (project.isDemo) DemoRecordBadge()
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DexNavHost(
    navController: NavHostController,
    uiState: DexUiState,
    isExpandedTablet: Boolean,
    aiOutputState: AsyncPanelState<String>,
    aiPromptDraftState: AsyncPanelState<String>,
    syncState: AsyncPanelState<com.example.data.remote.SyncOutcome>,
    githubState: AsyncPanelState<com.example.data.repository.GitHubRepoMetadata>,
    recoveryInitialMode: Int,
    recoveryProjectId: String?,
    recoveryWorkItemId: String?,
    viewModel: DexViewModel,
    onOpenProject: (String) -> Unit,
    onOpenExplorerWithStatus: (com.example.domain.model.ProjectStatus?) -> Unit,
    onOpenCreateProject: () -> Unit,
    onOpenRecovery: (String) -> Unit,
    onOpenPromptForTask: (String, String?) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShareMarkdown: (String, String) -> Unit,
    onExportMarkdownFile: (String, String) -> Unit,
    onImportDocumentClick: () -> Unit,
    onExportJsonBackupClick: () -> Unit,
    onRestoreJsonBackupClick: () -> Unit,
    onExportMarkdownReportClick: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_DASHBOARD
    ) {
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(
                uiState = uiState,
                onOpenProject = onOpenProject,
                onOpenExplorerWithStatus = onOpenExplorerWithStatus,
                onOpenCreateProject = onOpenCreateProject,
                onOpenRecovery = onOpenRecovery
            )
        }
        composable(ROUTE_EXPLORER) {
            ProjectExplorerScreen(
                uiState = uiState,
                onQueryChange = viewModel::updateSearchQuery,
                onCategorySelect = viewModel::updateCategoryFilter,
                onTagSelect = viewModel::updateTagFilter,
                onStatusSelect = viewModel::updateStatusFilter,
                onSortChange = viewModel::updateSortOption,
                onClearFilters = viewModel::clearFilters,
                onOpenProject = onOpenProject,
                onSaveProject = viewModel::saveProject
            )
        }
        composable(ROUTE_DETAIL) {
            ProjectDetailScreen(
                projectId = uiState.selectedProjectId,
                uiState = uiState,
                githubState = githubState,
                aiOutputState = aiOutputState,
                showBackButton = !isExpandedTablet,
                onBack = { navController.popBackStack() },
                onSaveProject = viewModel::saveProject,
                onTransitionStatus = viewModel::transitionProjectStatus,
                onMarkReviewed = viewModel::markProjectReviewed,
                onDeleteProject = viewModel::deleteProject,
                onSaveWorkItem = viewModel::saveWorkItem,
                onDeleteWorkItem = viewModel::deleteWorkItem,
                onOpenUrl = onOpenUrl,
                onCheckGitHub = { url -> viewModel.fetchGitHubMetadata(url) },
                onOpenRecovery = onOpenRecovery,
                onOpenPromptForTask = onOpenPromptForTask,
                onAiSuggestTasks = viewModel::aiSuggestNextTasks
            )
        }
        composable(ROUTE_LEDGER) {
            ExecutionLedgerScreen(
                uiState = uiState,
                onAddRecord = viewModel::addExecutionRecord,
                onVerifyRecord = viewModel::verifyExecutionRecord,
                onDeleteRecord = viewModel::deleteExecutionRecord
            )
        }
        composable(ROUTE_DEPENDENCIES) {
            DependencyViewScreen(
                uiState = uiState,
                onAddDependency = viewModel::addDependencyLink,
                onDeleteDependency = viewModel::deleteDependencyLink
            )
        }
        composable(ROUTE_RECOVERY) {
            RecoveryAndPromptScreen(
                uiState = uiState,
                initialMode = recoveryInitialMode,
                initialProjectId = recoveryProjectId ?: uiState.selectedProjectId,
                initialWorkItemId = recoveryWorkItemId,
                aiPromptDraftState = aiPromptDraftState,
                buildContinuationPacket = viewModel::buildContinuationPacket,
                buildAgentPrompt = viewModel::buildAgentPrompt,
                onAiRefinePrompt = viewModel::aiRefineHandoffPrompt,
                onClearAiDraft = viewModel::clearAiPromptDraft,
                onShareMarkdown = onShareMarkdown,
                onExportMarkdownFile = onExportMarkdownFile,
                onCopiedMessage = viewModel::showMessage
            )
        }
        composable(ROUTE_TOOLS) {
            DeckToolsScreen(
                uiState = uiState,
                syncState = syncState,
                aiOutputState = aiOutputState,
                onImportDocumentClick = onImportDocumentClick,
                onExportJsonBackupClick = onExportJsonBackupClick,
                onRestoreJsonBackupClick = onRestoreJsonBackupClick,
                onExportMarkdownReportClick = onExportMarkdownReportClick,
                onAiSummarizeDocument = viewModel::aiSummarizeDocument,
                onAiAuditInconsistencies = viewModel::aiAuditStatusInconsistencies,
                onDeleteImportedDocument = viewModel::deleteImportedDocument,
                onSyncWithFirestore = { viewModel.syncWithFirestore(includeDemoRecords = false) },
                onSetLowStimulation = viewModel::setLowStimulation,
                onSetReducedMotion = viewModel::setReducedMotion,
                onSetHideDemoRecords = viewModel::setHideDemoRecords,
                onSetPeriodicAudit = viewModel::setPeriodicAuditEnabled,
                onRunAuditNow = viewModel::triggerImmediateAudit,
                onPurgeDemoData = viewModel::purgeAllDemoData,
                onShowMessage = viewModel::showMessage
            )
        }
    }
}

private suspend fun readTextFromUri(context: Context, uri: Uri): Pair<String, String?> =
    withContext(Dispatchers.IO) {
        val fileName = resolveFileName(context, uri)
        val content = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
        fileName to content
    }

private suspend fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(content)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

private fun resolveFileName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "imported_document.txt"
    } catch (_: Exception) {
        "imported_document.txt"
    }
}
