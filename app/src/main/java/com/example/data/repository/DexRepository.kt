package com.example.data.repository

import com.example.data.local.AuditEventEntity
import com.example.data.local.DemoSeedData
import com.example.data.local.DependencyLinkEntity
import com.example.data.local.DexDao
import com.example.data.local.ExecutionRecordEntity
import com.example.data.local.ImportedDocumentEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.WorkItemEntity
import com.example.domain.engine.BackupImportEngine
import com.example.domain.engine.BackupParseResult
import com.example.domain.engine.DependencyResolver
import com.example.domain.engine.DexBackupPayload
import com.example.domain.engine.EvidenceClassificationResult
import com.example.domain.engine.EvidenceClassifier
import com.example.domain.engine.ProjectStateMachine
import com.example.domain.engine.TransitionResult
import com.example.domain.model.DependencyNodeType
import com.example.domain.model.DependencyRelationType
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class DexRepository(
    private val dao: DexDao,
    private val prefs: UserPreferencesRepository
) {
    val projects: Flow<List<ProjectEntity>> = dao.observeAllProjects()
    val workItems: Flow<List<WorkItemEntity>> = dao.observeAllWorkItems()
    val executionRecords: Flow<List<ExecutionRecordEntity>> = dao.observeAllExecutionRecords()
    val dependencyLinks: Flow<List<DependencyLinkEntity>> = dao.observeAllDependencyLinks()
    val importedDocuments: Flow<List<ImportedDocumentEntity>> = dao.observeAllImportedDocuments()
    val auditEvents: Flow<List<AuditEventEntity>> = dao.observeRecentAuditEvents()

    suspend fun ensureFirstRunSeed() {
        val currentPrefs = prefs.preferencesFlow.first()
        if (!currentPrefs.demoSeededOnce && dao.countProjects() == 0) {
            val seed = DemoSeedData.buildSeed()
            dao.upsertProjects(seed.projects)
            dao.upsertWorkItems(seed.workItems)
            dao.upsertExecutionRecords(seed.executionRecords)
            dao.upsertDependencyLinks(seed.dependencyLinks)
            dao.upsertImportedDocuments(seed.importedDocuments)
            dao.insertAuditEvents(seed.auditEvents)
            prefs.markDemoSeeded()
        }
    }

    suspend fun saveProject(project: ProjectEntity, isNew: Boolean): Result<Unit> {
        val now = System.currentTimeMillis()
        val updated = project.copy(updatedAt = now, createdAt = if (isNew) now else project.createdAt)
        dao.upsertProject(updated)
        logAudit(
            entityType = "PROJECT",
            entityId = updated.id,
            action = if (isNew) "CREATED" else "UPDATED",
            summary = "${if (isNew) "Created" else "Updated"} project '${updated.name}' [${updated.status.label}]"
        )
        return Result.success(Unit)
    }

    suspend fun transitionProjectStatus(
        projectId: String,
        targetStatus: ProjectStatus
    ): TransitionResult {
        val project = dao.getProjectById(projectId)
            ?: return TransitionResult.Rejected("Project not found.")
        val allProjects = dao.getAllProjectsSnapshot()
        val allWorkItems = dao.getAllWorkItemsSnapshot()
        val allLinks = dao.getAllDependencyLinksSnapshot()
        val allRecords = dao.getAllExecutionRecordsSnapshot()

        val blockers = DependencyResolver.resolveUnresolvedBlockersForProject(
            projectId = projectId,
            allLinks = allLinks,
            projects = allProjects,
            workItems = allWorkItems
        )
        val projectWorkItems = allWorkItems.filter { it.projectId == projectId }
        val verifiedCount = allRecords.count {
            it.projectId == projectId && it.verificationStatus == EvidenceState.VERIFIED
        }
        val hasVerified = ProjectStateMachine.hasVerifiedEvidenceForProject(
            project = project,
            workItems = projectWorkItems,
            verifiedRecordCount = verifiedCount
        )

        val evaluation = ProjectStateMachine.evaluateTransition(
            current = project.status,
            target = targetStatus,
            unresolvedBlockingCount = blockers.size,
            hasVerifiedEvidence = hasVerified
        )

        if (evaluation is TransitionResult.Allowed) {
            val now = System.currentTimeMillis()
            dao.upsertProject(
                project.copy(
                    status = evaluation.nextStatus,
                    lastReviewDate = now,
                    updatedAt = now
                )
            )
            logAudit(
                entityType = "PROJECT",
                entityId = project.id,
                action = "STATUS_TRANSITION",
                summary = "'${project.name}': ${project.status.label} -> ${evaluation.nextStatus.label}"
            )
        }
        return evaluation
    }

    suspend fun markProjectReviewed(projectId: String) {
        val project = dao.getProjectById(projectId) ?: return
        val now = System.currentTimeMillis()
        dao.upsertProject(project.copy(lastReviewDate = now, updatedAt = now))
        logAudit(
            entityType = "PROJECT",
            entityId = project.id,
            action = "REVIEWED",
            summary = "Marked '${project.name}' reviewed."
        )
    }

    suspend fun deleteProject(projectId: String) {
        val project = dao.getProjectById(projectId) ?: return
        dao.deleteProjectById(projectId)
        logAudit(
            entityType = "PROJECT",
            entityId = projectId,
            action = "DELETED",
            summary = "Deleted project '${project.name}' and linked items."
        )
    }

    suspend fun saveWorkItem(workItem: WorkItemEntity, isNew: Boolean) {
        val now = System.currentTimeMillis()
        val updated = workItem.copy(updatedAt = now, createdAt = if (isNew) now else workItem.createdAt)
        dao.upsertWorkItem(updated)
        logAudit(
            entityType = "WORK_ITEM",
            entityId = updated.id,
            action = if (isNew) "CREATED" else "UPDATED",
            summary = "${if (isNew) "Created" else "Updated"} work item '${updated.title}' [${updated.status.label}/${updated.evidenceState.label}]"
        )
    }

    suspend fun deleteWorkItem(workItemId: String) {
        dao.deleteWorkItemById(workItemId)
        logAudit(
            entityType = "WORK_ITEM",
            entityId = workItemId,
            action = "DELETED",
            summary = "Deleted work item $workItemId."
        )
    }

    suspend fun recordExecution(
        record: ExecutionRecordEntity,
        humanConfirmedEvidence: Boolean
    ): Pair<ExecutionRecordEntity, EvidenceClassificationResult> {
        val classification = EvidenceClassifier.classify(
            requestedState = record.verificationStatus,
            claim = record.claim,
            evidence = record.evidence,
            aiGeneratedStatement = record.aiGeneratedStatement,
            humanConfirmedEvidence = humanConfirmedEvidence
        )
        val now = System.currentTimeMillis()
        val persisted = record.copy(
            verificationStatus = classification.effectiveState,
            updatedAt = now
        )
        dao.upsertExecutionRecord(persisted)

        // Update project lastVerifiedStateSummary ONLY when independently verified
        if (persisted.verificationStatus == EvidenceState.VERIFIED) {
            dao.getProjectById(persisted.projectId)?.let { project ->
                dao.upsertProject(
                    project.copy(
                        lastVerifiedStateSummary = "${persisted.actionAttempted} — Evidence: ${persisted.evidence}",
                        lastReviewDate = now,
                        updatedAt = now
                    )
                )
            }
        }

        logAudit(
            entityType = "EXECUTION_RECORD",
            entityId = persisted.id,
            action = "RECORDED",
            summary = "${persisted.toolOrAgent} [${persisted.verificationStatus.label}]: ${persisted.actionAttempted.take(80)}"
        )
        return persisted to classification
    }

    suspend fun verifyExecutionRecord(
        record: ExecutionRecordEntity,
        verifiedEvidence: String
    ): Result<ExecutionRecordEntity> {
        val trimmed = verifiedEvidence.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Independent verification evidence cannot be blank."))
        }
        val now = System.currentTimeMillis()
        val updated = record.copy(
            evidence = trimmed,
            verificationStatus = EvidenceState.VERIFIED,
            updatedAt = now
        )
        dao.upsertExecutionRecord(updated)
        dao.getProjectById(updated.projectId)?.let { project ->
            dao.upsertProject(
                project.copy(
                    lastVerifiedStateSummary = "${updated.actionAttempted} — Verified evidence: $trimmed",
                    lastReviewDate = now,
                    updatedAt = now
                )
            )
        }
        logAudit(
            entityType = "EXECUTION_RECORD",
            entityId = updated.id,
            action = "VERIFIED",
            summary = "Operator independently verified '${updated.actionAttempted.take(64)}'."
        )
        return Result.success(updated)
    }

    suspend fun deleteExecutionRecord(recordId: String) {
        dao.deleteExecutionRecordById(recordId)
        logAudit("EXECUTION_RECORD", recordId, "DELETED", "Deleted execution record $recordId.")
    }

    suspend fun addDependencyLink(
        sourceType: DependencyNodeType,
        sourceId: String,
        targetType: DependencyNodeType,
        targetId: String,
        relationType: DependencyRelationType,
        notes: String
    ): Result<DependencyLinkEntity> {
        val existing = dao.getAllDependencyLinksSnapshot()
        if (DependencyResolver.wouldCreateCycle(existing, sourceType, sourceId, targetType, targetId, relationType)) {
            return Result.failure(
                IllegalArgumentException("Cannot add BLOCKING dependency: circular dependency cycle detected.")
            )
        }
        val now = System.currentTimeMillis()
        val link = DependencyLinkEntity(
            id = "dep-${UUID.randomUUID().toString().take(8)}",
            sourceType = sourceType,
            sourceId = sourceId,
            targetType = targetType,
            targetId = targetId,
            relationType = relationType,
            notes = notes.trim(),
            isDemo = false,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertDependencyLink(link)
        logAudit(
            entityType = "DEPENDENCY",
            entityId = link.id,
            action = "LINKED",
            summary = "Added ${relationType.label} dependency $sourceId -> $targetId"
        )
        return Result.success(link)
    }

    suspend fun deleteDependencyLink(linkId: String) {
        dao.deleteDependencyLinkById(linkId)
        logAudit("DEPENDENCY", linkId, "DELETED", "Removed dependency link $linkId.")
    }

    suspend fun importDocumentPreservingOriginal(
        fileName: String,
        rawContent: String,
        targetProjectId: String? = null,
        createProjectIfUnlinked: Boolean = true
    ): ImportedDocumentEntity {
        val parsed = BackupImportEngine.parseDocumentImport(
            fileName = fileName,
            rawContent = rawContent,
            targetProjectId = targetProjectId,
            extractProjectDraft = createProjectIfUnlinked
        )
        parsed.suggestedProject?.let { draft ->
            dao.upsertProject(draft)
            logAudit(
                entityType = "PROJECT",
                entityId = draft.id,
                action = "IMPORTED_DRAFT",
                summary = "Created draft project '${draft.name}' from $fileName (original preserved)."
            )
        }
        dao.upsertImportedDocument(parsed.document)
        logAudit(
            entityType = "IMPORT",
            entityId = parsed.document.id,
            action = "PRESERVED",
            summary = "Imported '$fileName' (${parsed.document.format}, SHA-256 ${parsed.document.contentHash.take(10)})."
        )
        return parsed.document
    }

    suspend fun saveDocumentAiSummary(docId: String, summary: String) {
        dao.updateImportedDocumentAiSummary(docId, summary)
        logAudit(
            entityType = "IMPORT",
            entityId = docId,
            action = "AI_SUMMARIZED",
            summary = "Saved advisory Gemini summary for document $docId (original source untouched)."
        )
    }

    suspend fun deleteImportedDocument(docId: String) {
        dao.deleteImportedDocumentById(docId)
        logAudit("IMPORT", docId, "DELETED", "Deleted imported document $docId.")
    }

    suspend fun exportFullBackupJson(): String {
        val payload = DexBackupPayload(
            schemaVersion = BackupImportEngine.CURRENT_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            projects = dao.getAllProjectsSnapshot(),
            workItems = dao.getAllWorkItemsSnapshot(),
            executionRecords = dao.getAllExecutionRecordsSnapshot(),
            dependencyLinks = dao.getAllDependencyLinksSnapshot(),
            importedDocuments = dao.getAllImportedDocumentsSnapshot(),
            auditEvents = dao.getAllAuditEventsSnapshot()
        )
        logAudit("BACKUP", "export", "EXPORTED", "Exported full JSON backup (${payload.projects.size} projects).")
        return BackupImportEngine.exportToJson(payload)
    }

    suspend fun restoreFromBackupJson(rawJson: String): Result<Int> {
        return when (val parsed = BackupImportEngine.parseBackupJson(rawJson)) {
            is BackupParseResult.Failure -> {
                logAudit("BACKUP", "restore", "FAILED", "Rejected corrupt/invalid backup: ${parsed.error}")
                Result.failure(IllegalArgumentException(parsed.error))
            }
            is BackupParseResult.Success -> {
                val p = parsed.payload
                dao.replaceWorkspaceFromBackup(
                    projects = p.projects,
                    workItems = p.workItems,
                    executionRecords = p.executionRecords,
                    dependencyLinks = p.dependencyLinks,
                    importedDocuments = p.importedDocuments,
                    auditEvents = p.auditEvents
                )
                logAudit(
                    entityType = "BACKUP",
                    entityId = "restore",
                    action = "RESTORED",
                    summary = "Restored ${p.projects.size} projects, ${p.workItems.size} work items, ${p.executionRecords.size} records."
                )
                Result.success(p.projects.size)
            }
        }
    }

    suspend fun applyPulledSyncEntities(
        projects: List<ProjectEntity>,
        workItems: List<WorkItemEntity>,
        records: List<ExecutionRecordEntity>,
        links: List<DependencyLinkEntity>
    ) {
        if (projects.isNotEmpty()) dao.upsertProjects(projects)
        if (workItems.isNotEmpty()) dao.upsertWorkItems(workItems)
        if (records.isNotEmpty()) dao.upsertExecutionRecords(records)
        if (links.isNotEmpty()) dao.upsertDependencyLinks(links)
        logAudit(
            entityType = "SYNC",
            entityId = "firestore",
            action = "MERGED",
            summary = "Merged ${projects.size} projects, ${workItems.size} tasks, ${records.size} records from Firestore."
        )
    }

    suspend fun purgeAllDemoRecords() {
        dao.purgeAllDemoData()
        logAudit(
            entityType = "SYSTEM",
            entityId = "demo",
            action = "PURGED_DEMO",
            summary = "Purged all [DEMO] demonstration records per operator confirmation."
        )
    }

    suspend fun getFullSnapshot(): DexBackupPayload = DexBackupPayload(
        schemaVersion = BackupImportEngine.CURRENT_SCHEMA_VERSION,
        exportedAt = System.currentTimeMillis(),
        projects = dao.getAllProjectsSnapshot(),
        workItems = dao.getAllWorkItemsSnapshot(),
        executionRecords = dao.getAllExecutionRecordsSnapshot(),
        dependencyLinks = dao.getAllDependencyLinksSnapshot(),
        importedDocuments = dao.getAllImportedDocumentsSnapshot(),
        auditEvents = dao.getAllAuditEventsSnapshot()
    )

    private suspend fun logAudit(
        entityType: String,
        entityId: String,
        action: String,
        summary: String
    ) {
        dao.insertAuditEvent(
            AuditEventEntity(
                id = "aud-${UUID.randomUUID().toString().take(8)}",
                entityType = entityType,
                entityId = entityId,
                action = action,
                summary = summary,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
