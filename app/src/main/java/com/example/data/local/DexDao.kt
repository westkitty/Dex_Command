package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DexDao {
    // Projects
    @Query("SELECT * FROM projects ORDER BY priority ASC, updatedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY priority ASC, updatedAt DESC")
    suspend fun getAllProjectsSnapshot(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjects(projects: List<ProjectEntity>)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("DELETE FROM projects WHERE isDemo = 1")
    suspend fun deleteDemoProjects()

    // Work Items
    @Query("SELECT * FROM work_items ORDER BY priority ASC, updatedAt DESC")
    fun observeAllWorkItems(): Flow<List<WorkItemEntity>>

    @Query("SELECT * FROM work_items ORDER BY priority ASC, updatedAt DESC")
    suspend fun getAllWorkItemsSnapshot(): List<WorkItemEntity>

    @Query("SELECT * FROM work_items WHERE projectId = :projectId ORDER BY priority ASC, updatedAt DESC")
    fun observeWorkItemsForProject(projectId: String): Flow<List<WorkItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkItem(workItem: WorkItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkItems(workItems: List<WorkItemEntity>)

    @Query("DELETE FROM work_items WHERE id = :id")
    suspend fun deleteWorkItemById(id: String)

    // Execution Records
    @Query("SELECT * FROM execution_records ORDER BY timestamp DESC")
    fun observeAllExecutionRecords(): Flow<List<ExecutionRecordEntity>>

    @Query("SELECT * FROM execution_records ORDER BY timestamp DESC")
    suspend fun getAllExecutionRecordsSnapshot(): List<ExecutionRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExecutionRecord(record: ExecutionRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExecutionRecords(records: List<ExecutionRecordEntity>)

    @Query("DELETE FROM execution_records WHERE id = :id")
    suspend fun deleteExecutionRecordById(id: String)

    // Dependency Links
    @Query("SELECT * FROM dependency_links ORDER BY createdAt DESC")
    fun observeAllDependencyLinks(): Flow<List<DependencyLinkEntity>>

    @Query("SELECT * FROM dependency_links ORDER BY createdAt DESC")
    suspend fun getAllDependencyLinksSnapshot(): List<DependencyLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDependencyLink(link: DependencyLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDependencyLinks(links: List<DependencyLinkEntity>)

    @Query("DELETE FROM dependency_links WHERE id = :id")
    suspend fun deleteDependencyLinkById(id: String)

    @Query("DELETE FROM dependency_links WHERE isDemo = 1")
    suspend fun deleteDemoDependencyLinks()

    // Imported Documents (verbatim original preserved)
    @Query("SELECT * FROM imported_documents ORDER BY importedAt DESC")
    fun observeAllImportedDocuments(): Flow<List<ImportedDocumentEntity>>

    @Query("SELECT * FROM imported_documents ORDER BY importedAt DESC")
    suspend fun getAllImportedDocumentsSnapshot(): List<ImportedDocumentEntity>

    @Query("SELECT * FROM imported_documents WHERE id = :id")
    suspend fun getImportedDocumentById(id: String): ImportedDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportedDocument(doc: ImportedDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportedDocuments(docs: List<ImportedDocumentEntity>)

    @Query("UPDATE imported_documents SET aiSummary = :summary WHERE id = :docId")
    suspend fun updateImportedDocumentAiSummary(docId: String, summary: String)

    @Query("DELETE FROM imported_documents WHERE id = :id")
    suspend fun deleteImportedDocumentById(id: String)

    @Query("DELETE FROM imported_documents WHERE isDemo = 1")
    suspend fun deleteDemoImportedDocuments()

    // Audit History (append-only for operations)
    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT 200")
    fun observeRecentAuditEvents(): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC")
    suspend fun getAllAuditEventsSnapshot(): List<AuditEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditEvent(event: AuditEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditEvents(events: List<AuditEventEntity>)

    @Query("DELETE FROM projects")
    suspend fun clearAllProjects()

    @Query("DELETE FROM dependency_links")
    suspend fun clearAllDependencyLinks()

    @Query("DELETE FROM imported_documents")
    suspend fun clearAllImportedDocuments()

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun countProjects(): Int

    /**
     * Atomic transactional restore. Because projects cascade-delete work_items & execution_records,
     * any error rolls back completely so existing records are never corrupted.
     */
    @Transaction
    suspend fun replaceWorkspaceFromBackup(
        projects: List<ProjectEntity>,
        workItems: List<WorkItemEntity>,
        executionRecords: List<ExecutionRecordEntity>,
        dependencyLinks: List<DependencyLinkEntity>,
        importedDocuments: List<ImportedDocumentEntity>,
        auditEvents: List<AuditEventEntity>
    ) {
        clearAllProjects()
        clearAllDependencyLinks()
        clearAllImportedDocuments()
        upsertProjects(projects)
        upsertWorkItems(workItems)
        upsertExecutionRecords(executionRecords)
        upsertDependencyLinks(dependencyLinks)
        upsertImportedDocuments(importedDocuments)
        insertAuditEvents(auditEvents)
    }

    @Transaction
    suspend fun purgeAllDemoData() {
        deleteDemoProjects()
        deleteDemoDependencyLinks()
        deleteDemoImportedDocuments()
    }
}
