package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AuditEventEntity
import com.example.data.local.DependencyLinkEntity
import com.example.data.local.DexDatabase
import com.example.data.local.ExecutionRecordEntity
import com.example.data.local.ImportedDocumentEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.WorkItemEntity
import com.example.data.remote.FirestoreSyncRepository
import com.example.data.remote.GeminiAnalysisService
import com.example.data.remote.SyncOutcome
import com.example.data.repository.DexRepository
import com.example.data.repository.GitHubReadOnlyService
import com.example.data.repository.GitHubRepoMetadata
import com.example.domain.engine.ContinuityRecoveryGenerator
import com.example.domain.engine.DependencyResolver
import com.example.domain.engine.EvidenceClassifier
import com.example.domain.engine.ProjectStateMachine
import com.example.domain.engine.PromptGenerator
import com.example.domain.engine.StatusInconsistency
import com.example.domain.engine.TransitionResult
import com.example.domain.model.AgentTarget
import com.example.domain.model.DependencyNodeType
import com.example.domain.model.DependencyRelationType
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectSortOption
import com.example.domain.model.ProjectStatus
import com.example.worker.ReviewAuditWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AsyncPanelState<out T> {
    data object Idle : AsyncPanelState<Nothing>
    data object Loading : AsyncPanelState<Nothing>
    data class Success<T>(val data: T) : AsyncPanelState<T>
    data class Error(val message: String) : AsyncPanelState<Nothing>
}

data class ExplorerFilterState(
    val query: String = "",
    val selectedCategory: String? = null,
    val selectedTag: String? = null,
    val selectedStatus: ProjectStatus? = null,
    val sortOption: ProjectSortOption = ProjectSortOption.PRIORITY_ASC
)

data class DexUiState(
    val isLoading: Boolean = true,
    val projects: List<ProjectEntity> = emptyList(),
    val workItems: List<WorkItemEntity> = emptyList(),
    val executionRecords: List<ExecutionRecordEntity> = emptyList(),
    val dependencyLinks: List<DependencyLinkEntity> = emptyList(),
    val importedDocuments: List<ImportedDocumentEntity> = emptyList(),
    val auditEvents: List<AuditEventEntity> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val filter: ExplorerFilterState = ExplorerFilterState(),
    val selectedProjectId: String? = null,
    val isOffline: Boolean = false
) {
    val visibleProjects: List<ProjectEntity>
        get() = if (preferences.hideDemoRecords) projects.filterNot { it.isDemo } else projects

    val visibleWorkItems: List<WorkItemEntity>
        get() = if (preferences.hideDemoRecords) workItems.filterNot { it.isDemo } else workItems

    val visibleExecutionRecords: List<ExecutionRecordEntity>
        get() = if (preferences.hideDemoRecords) executionRecords.filterNot { it.isDemo } else executionRecords

    val visibleDependencyLinks: List<DependencyLinkEntity>
        get() = if (preferences.hideDemoRecords) dependencyLinks.filterNot { it.isDemo } else dependencyLinks

    val visibleImportedDocuments: List<ImportedDocumentEntity>
        get() = if (preferences.hideDemoRecords) importedDocuments.filterNot { it.isDemo } else importedDocuments

    val filteredSortedProjects: List<ProjectEntity>
        get() {
            val q = filter.query.trim().lowercase()
            return visibleProjects
                .filter { p ->
                    val matchesQuery = q.isEmpty() ||
                        p.name.lowercase().contains(q) ||
                        p.description.lowercase().contains(q) ||
                        p.purpose.lowercase().contains(q) ||
                        p.tags.any { it.lowercase().contains(q) } ||
                        p.category.lowercase().contains(q)
                    val matchesCategory = filter.selectedCategory == null || p.category == filter.selectedCategory
                    val matchesTag = filter.selectedTag == null || p.tags.any { it.equals(filter.selectedTag, ignoreCase = true) }
                    val matchesStatus = filter.selectedStatus == null || p.status == filter.selectedStatus
                    matchesQuery && matchesCategory && matchesTag && matchesStatus
                }
                .let { list ->
                    when (filter.sortOption) {
                        ProjectSortOption.PRIORITY_ASC -> list.sortedWith(compareBy<ProjectEntity> { it.priority }.thenByDescending { it.updatedAt })
                        ProjectSortOption.LAST_REVIEW_ASC -> list.sortedBy { it.lastReviewDate }
                        ProjectSortOption.UPDATED_DESC -> list.sortedByDescending { it.updatedAt }
                        ProjectSortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                    }
                }
        }

    val activeProjects: List<ProjectEntity>
        get() = visibleProjects.filter { it.status == ProjectStatus.ACTIVE }

    val blockedProjects: List<ProjectEntity>
        get() = visibleProjects.filter { it.status == ProjectStatus.BLOCKED }

    val reviewNeededProjects: List<ProjectEntity>
        get() = visibleProjects.filter { ProjectStateMachine.needsReview(it) }

    val nextActionableTasks: List<WorkItemEntity>
        get() = DependencyResolver.findNextActionableTasks(
            projects = visibleProjects,
            workItems = visibleWorkItems,
            allLinks = visibleDependencyLinks
        )

    val localInconsistencies: List<StatusInconsistency>
        get() = EvidenceClassifier.auditInconsistencies(
            projects = visibleProjects,
            workItems = visibleWorkItems,
            records = visibleExecutionRecords
        )

    val allCategories: List<String>
        get() = visibleProjects.map { it.category }.distinct().sorted()

    val allTags: List<String>
        get() = visibleProjects.flatMap { it.tags }.distinct().sorted()
}

class DexViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DexDatabase.getInstance(application)
    private val prefsRepo = UserPreferencesRepository(application)
    val repository = DexRepository(db.dexDao(), prefsRepo)
    private val geminiService = GeminiAnalysisService()
    private val githubService = GitHubReadOnlyService()
    private val firestoreSyncRepo by lazy { FirestoreSyncRepository(application) }

    private val _filter = MutableStateFlow(ExplorerFilterState())
    private val _selectedProjectId = MutableStateFlow<String?>(null)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _aiOutputState = MutableStateFlow<AsyncPanelState<String>>(AsyncPanelState.Idle)
    val aiOutputState: StateFlow<AsyncPanelState<String>> = _aiOutputState.asStateFlow()

    private val _aiPromptDraftState = MutableStateFlow<AsyncPanelState<String>>(AsyncPanelState.Idle)
    val aiPromptDraftState: StateFlow<AsyncPanelState<String>> = _aiPromptDraftState.asStateFlow()

    private val _syncState = MutableStateFlow<AsyncPanelState<SyncOutcome>>(AsyncPanelState.Idle)
    val syncState: StateFlow<AsyncPanelState<SyncOutcome>> = _syncState.asStateFlow()

    private val _githubState = MutableStateFlow<AsyncPanelState<GitHubRepoMetadata>>(AsyncPanelState.Idle)
    val githubState: StateFlow<AsyncPanelState<GitHubRepoMetadata>> = _githubState.asStateFlow()

    private val _isOffline = MutableStateFlow(!isNetworkAvailable(application))

    val uiState: StateFlow<DexUiState> = combine(
        combine(
            repository.projects,
            repository.workItems,
            repository.executionRecords,
            repository.dependencyLinks,
            repository.importedDocuments
        ) { projects, workItems, records, links, docs ->
            DataBundle(projects, workItems, records, links, docs)
        },
        combine(
            repository.auditEvents,
            prefsRepo.preferencesFlow,
            _filter,
            _selectedProjectId,
            _isOffline
        ) { audits, prefs, filter, selectedId, offline ->
            MetaBundle(audits, prefs, filter, selectedId, offline)
        }
    ) { data, meta ->
        val effectiveSelected = meta.selectedId?.takeIf { id -> data.projects.any { it.id == id } }
            ?: data.projects.firstOrNull()?.id
        DexUiState(
            isLoading = false,
            projects = data.projects,
            workItems = data.workItems,
            executionRecords = data.executionRecords,
            dependencyLinks = data.dependencyLinks,
            importedDocuments = data.importedDocuments,
            auditEvents = meta.audits,
            preferences = meta.prefs,
            filter = meta.filter,
            selectedProjectId = effectiveSelected,
            isOffline = meta.offline
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DexUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            repository.ensureFirstRunSeed()
        }
    }

    fun refreshConnectivity() {
        _isOffline.value = !isNetworkAvailable(getApplication())
    }

    fun selectProject(projectId: String?) {
        _selectedProjectId.value = projectId
        _githubState.value = AsyncPanelState.Idle
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    // Explorer Filters
    fun updateSearchQuery(query: String) {
        _filter.update { it.copy(query = query) }
    }

    fun updateCategoryFilter(category: String?) {
        _filter.update { it.copy(selectedCategory = if (it.selectedCategory == category) null else category) }
    }

    fun updateTagFilter(tag: String?) {
        _filter.update { it.copy(selectedTag = if (it.selectedTag == tag) null else tag) }
    }

    fun updateStatusFilter(status: ProjectStatus?) {
        _filter.update { it.copy(selectedStatus = if (it.selectedStatus == status) null else status) }
    }

    fun updateSortOption(option: ProjectSortOption) {
        _filter.update { it.copy(sortOption = option) }
    }

    fun clearFilters() {
        _filter.value = ExplorerFilterState()
    }

    // Project CRUD & Transitions
    fun saveProject(project: ProjectEntity, isNew: Boolean) {
        viewModelScope.launch {
            repository.saveProject(project, isNew)
            _selectedProjectId.value = project.id
            showMessage(if (isNew) "Created project '${project.name}'." else "Saved changes to '${project.name}'.")
        }
    }

    fun transitionProjectStatus(projectId: String, targetStatus: ProjectStatus) {
        viewModelScope.launch {
            when (val res = repository.transitionProjectStatus(projectId, targetStatus)) {
                is TransitionResult.Allowed -> {
                    val note = res.warning?.let { " ($it)" }.orEmpty()
                    showMessage("Status updated to ${res.nextStatus.label}$note")
                }
                is TransitionResult.Rejected -> {
                    showMessage("Transition blocked: ${res.reason}")
                }
            }
        }
    }

    fun markProjectReviewed(projectId: String) {
        viewModelScope.launch {
            repository.markProjectReviewed(projectId)
            showMessage("Project review timestamp updated.")
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            if (_selectedProjectId.value == projectId) {
                _selectedProjectId.value = null
            }
            showMessage("Project deleted.")
        }
    }

    // Work Items
    fun saveWorkItem(workItem: WorkItemEntity, isNew: Boolean) {
        viewModelScope.launch {
            repository.saveWorkItem(workItem, isNew)
            showMessage(if (isNew) "Added work item '${workItem.title}'." else "Updated '${workItem.title}'.")
        }
    }

    fun deleteWorkItem(workItemId: String) {
        viewModelScope.launch {
            repository.deleteWorkItem(workItemId)
            showMessage("Work item deleted.")
        }
    }

    // Execution Ledger
    fun addExecutionRecord(
        projectId: String,
        workItemId: String?,
        toolOrAgent: String,
        actionAttempted: String,
        claim: String,
        evidence: String,
        requestedStatus: EvidenceState,
        aiGeneratedStatement: Boolean,
        humanConfirmedEvidence: Boolean
    ) {
        viewModelScope.launch {
            val record = ExecutionRecordEntity(
                id = "exec-${UUID.randomUUID().toString().take(8)}",
                projectId = projectId,
                workItemId = workItemId,
                toolOrAgent = toolOrAgent.trim().ifBlank { "Operator" },
                actionAttempted = actionAttempted.trim(),
                claim = claim.trim(),
                evidence = evidence.trim(),
                verificationStatus = requestedStatus,
                aiGeneratedStatement = aiGeneratedStatement,
                isDemo = false
            )
            val (_, classification) = repository.recordExecution(record, humanConfirmedEvidence)
            if (classification.wasDowngraded) {
                showMessage("Guardrail applied -> ${classification.effectiveState.label}: ${classification.reason}")
            } else {
                showMessage("Execution logged as ${classification.effectiveState.label}.")
            }
        }
    }

    fun verifyExecutionRecord(record: ExecutionRecordEntity, verifiedEvidence: String) {
        viewModelScope.launch {
            repository.verifyExecutionRecord(record, verifiedEvidence)
                .onSuccess { showMessage("Record independently VERIFIED with recorded evidence.") }
                .onFailure { showMessage(it.localizedMessage ?: "Verification failed.") }
        }
    }

    fun deleteExecutionRecord(recordId: String) {
        viewModelScope.launch {
            repository.deleteExecutionRecord(recordId)
            showMessage("Execution record removed.")
        }
    }

    // Dependencies
    fun addDependencyLink(
        sourceType: DependencyNodeType,
        sourceId: String,
        targetType: DependencyNodeType,
        targetId: String,
        relationType: DependencyRelationType,
        notes: String
    ) {
        viewModelScope.launch {
            repository.addDependencyLink(sourceType, sourceId, targetType, targetId, relationType, notes)
                .onSuccess { showMessage("Added ${relationType.label} dependency link.") }
                .onFailure { showMessage(it.localizedMessage ?: "Failed to add dependency.") }
        }
    }

    fun deleteDependencyLink(linkId: String) {
        viewModelScope.launch {
            repository.deleteDependencyLink(linkId)
            showMessage("Dependency link removed.")
        }
    }

    // Continuity & Prompts
    fun buildContinuationPacket(projectId: String): String {
        val state = uiState.value
        val project = state.projects.firstOrNull { it.id == projectId } ?: return "Project not found."
        return ContinuityRecoveryGenerator.generateContinuationPacket(
            project = project,
            workItems = state.workItems.filter { it.projectId == projectId },
            executionRecords = state.executionRecords.filter { it.projectId == projectId },
            dependencyLinks = state.dependencyLinks,
            importedDocuments = state.importedDocuments,
            allProjects = state.projects,
            allWorkItems = state.workItems
        )
    }

    fun buildAgentPrompt(
        agentTarget: AgentTarget,
        projectId: String,
        workItemId: String?,
        operatorNote: String
    ): String {
        val state = uiState.value
        val project = state.projects.firstOrNull { it.id == projectId } ?: return "Project not found."
        val workItem = workItemId?.let { id -> state.workItems.firstOrNull { it.id == id } }
        return PromptGenerator.generateTaskPrompt(
            agentTarget = agentTarget,
            project = project,
            workItem = workItem,
            executionRecords = state.executionRecords.filter { it.projectId == projectId },
            dependencyLinks = state.dependencyLinks,
            allProjects = state.projects,
            allWorkItems = state.workItems,
            additionalOperatorNote = operatorNote
        )
    }

    fun buildPortfolioMarkdownReport(): String {
        val state = uiState.value
        return ContinuityRecoveryGenerator.generateMarkdownProjectReport(
            projects = state.visibleProjects,
            workItems = state.visibleWorkItems,
            executionRecords = state.visibleExecutionRecords,
            dependencyLinks = state.visibleDependencyLinks
        )
    }

    // Imports, Exports & Backups
    fun importDocument(fileName: String, content: String, targetProjectId: String?, createDraft: Boolean) {
        viewModelScope.launch {
            if (content.isBlank()) {
                showMessage("Import rejected: selected file is empty.")
                return@launch
            }
            val doc = repository.importDocumentPreservingOriginal(
                fileName = fileName,
                rawContent = content,
                targetProjectId = targetProjectId,
                createProjectIfUnlinked = createDraft
            )
            showMessage("Preserved '${doc.fileName}' verbatim (${doc.format}).")
        }
    }

    suspend fun exportBackupJson(): String = repository.exportFullBackupJson()

    fun restoreBackupJson(rawJson: String) {
        viewModelScope.launch {
            repository.restoreFromBackupJson(rawJson)
                .onSuccess { count -> showMessage("Backup restored cleanly ($count projects).") }
                .onFailure { err -> showMessage("Restore aborted safely: ${err.localizedMessage}") }
        }
    }

    fun deleteImportedDocument(docId: String) {
        viewModelScope.launch {
            repository.deleteImportedDocument(docId)
            showMessage("Imported document removed.")
        }
    }

    fun purgeAllDemoData() {
        viewModelScope.launch {
            repository.purgeAllDemoRecords()
            showMessage("All [DEMO] records purged.")
        }
    }

    // Optional Read-Only GitHub Metadata
    fun fetchGitHubMetadata(repoUrl: String, optionalReadOnlyToken: String? = null) {
        refreshConnectivity()
        viewModelScope.launch {
            _githubState.value = AsyncPanelState.Loading
            githubService.fetchPublicRepoMetadata(repoUrl, optionalReadOnlyToken)
                .onSuccess { meta -> _githubState.value = AsyncPanelState.Success(meta) }
                .onFailure { err -> _githubState.value = AsyncPanelState.Error(err.localizedMessage ?: "GitHub check failed.") }
        }
    }

    // Optional Scoped Gemini AI (Firebase AI Logic)
    fun aiSummarizeDocument(doc: ImportedDocumentEntity) {
        viewModelScope.launch {
            _aiOutputState.value = AsyncPanelState.Loading
            geminiService.summarizeImportedDocument(doc)
                .onSuccess { summary ->
                    repository.saveDocumentAiSummary(doc.id, summary)
                    _aiOutputState.value = AsyncPanelState.Success(summary)
                }
                .onFailure { err ->
                    _aiOutputState.value = AsyncPanelState.Error(err.localizedMessage ?: "Gemini summarization failed.")
                }
        }
    }

    fun aiSuggestNextTasks(projectId: String) {
        val state = uiState.value
        val project = state.projects.firstOrNull { it.id == projectId } ?: return
        viewModelScope.launch {
            _aiOutputState.value = AsyncPanelState.Loading
            geminiService.suggestNextTasks(
                project = project,
                workItems = state.workItems.filter { it.projectId == projectId },
                recentRecords = state.executionRecords.filter { it.projectId == projectId }
            )
                .onSuccess { text -> _aiOutputState.value = AsyncPanelState.Success(text) }
                .onFailure { err -> _aiOutputState.value = AsyncPanelState.Error(err.localizedMessage ?: "Gemini suggestion failed.") }
        }
    }

    fun aiRefineHandoffPrompt(basePrompt: String) {
        viewModelScope.launch {
            _aiPromptDraftState.value = AsyncPanelState.Loading
            geminiService.refineHandoffPrompt(basePrompt)
                .onSuccess { refined -> _aiPromptDraftState.value = AsyncPanelState.Success(refined) }
                .onFailure { err -> _aiPromptDraftState.value = AsyncPanelState.Error(err.localizedMessage ?: "Gemini prompt refinement failed.") }
        }
    }

    fun clearAiPromptDraft() {
        _aiPromptDraftState.value = AsyncPanelState.Idle
    }

    fun aiAuditStatusInconsistencies() {
        val state = uiState.value
        viewModelScope.launch {
            _aiOutputState.value = AsyncPanelState.Loading
            geminiService.auditStatusInconsistencies(
                projects = state.visibleProjects,
                workItems = state.visibleWorkItems,
                records = state.visibleExecutionRecords
            )
                .onSuccess { report -> _aiOutputState.value = AsyncPanelState.Success(report) }
                .onFailure { err -> _aiOutputState.value = AsyncPanelState.Error(err.localizedMessage ?: "Gemini audit failed.") }
        }
    }

    // Optional Conflict-Safe Firestore Cross-Device Sync (Requires Google Sign-In)
    fun syncWithFirestore(includeDemoRecords: Boolean = false) {
        refreshConnectivity()
        viewModelScope.launch {
            _syncState.value = AsyncPanelState.Loading
            try {
                val snap = repository.getFullSnapshot()
                val result = firestoreSyncRepo.syncWorkspace(
                    localProjects = snap.projects,
                    localWorkItems = snap.workItems,
                    localRecords = snap.executionRecords,
                    localLinks = snap.dependencyLinks,
                    includeDemoRecords = includeDemoRecords
                )
                result.onSuccess { outcome ->
                    repository.applyPulledSyncEntities(
                        projects = outcome.pulledProjectEntities,
                        workItems = outcome.pulledWorkItemEntities,
                        records = outcome.pulledRecordEntities,
                        links = outcome.pulledLinkEntities
                    )
                    prefsRepo.recordLastSync(outcome.timestamp)
                    _syncState.value = AsyncPanelState.Success(outcome)
                    showMessage("Cloud sync complete: pushed ${outcome.pushedProjects} / pulled ${outcome.pulledProjects} projects.")
                }.onFailure { err ->
                    _syncState.value = AsyncPanelState.Error(err.localizedMessage ?: "Firestore sync failed.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _syncState.value = AsyncPanelState.Error(e.localizedMessage ?: "Sync error.")
            }
        }
    }

    // Accessibility & WorkManager Preferences
    fun setLowStimulation(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setLowStimulation(enabled) }
    }

    fun setReducedMotion(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setReducedMotion(enabled) }
    }

    fun setHideDemoRecords(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setHideDemoRecords(enabled) }
    }

    fun setPeriodicAuditEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setPeriodicAuditEnabled(enabled)
            if (enabled) {
                ReviewAuditWorker.schedulePeriodic(getApplication())
            } else {
                ReviewAuditWorker.cancelPeriodic(getApplication())
            }
            showMessage(if (enabled) "Periodic offline WorkManager audit scheduled." else "Periodic audit paused.")
        }
    }

    fun triggerImmediateAudit() {
        ReviewAuditWorker.runNow(getApplication())
        showMessage("Offline workspace audit queued via WorkManager.")
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private data class DataBundle(
        val projects: List<ProjectEntity>,
        val workItems: List<WorkItemEntity>,
        val executionRecords: List<ExecutionRecordEntity>,
        val dependencyLinks: List<DependencyLinkEntity>,
        val importedDocuments: List<ImportedDocumentEntity>
    )

    private data class MetaBundle(
        val audits: List<AuditEventEntity>,
        val prefs: UserPreferences,
        val filter: ExplorerFilterState,
        val selectedId: String?,
        val offline: Boolean
    )
}
