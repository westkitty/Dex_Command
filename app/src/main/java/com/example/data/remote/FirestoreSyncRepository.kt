package com.example.data.remote

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.data.local.DependencyLinkEntity
import com.example.data.local.ExecutionRecordEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.WorkItemEntity
import com.example.domain.model.DependencyNodeType
import com.example.domain.model.DependencyRelationType
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.DocumentId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

@Keep
@IgnoreExtraProperties
data class CloudProjectDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val purpose: String = "",
    val currentObjective: String = "",
    val repositoryUrl: String = "",
    val driveUrl: String = "",
    val documentationRefs: List<String> = emptyList(),
    val storageRefs: List<String> = emptyList(),
    val category: String = "Repository",
    val tags: List<String> = emptyList(),
    val protectedConstraints: List<String> = emptyList(),
    val knownFailures: List<String> = emptyList(),
    val openDecisions: List<String> = emptyList(),
    val status: String = ProjectStatus.ACTIVE.name,
    val priority: Long = 2L,
    val lastVerifiedMilestone: String = "",
    val lastVerifiedStateSummary: String = "",
    val demoRecord: Boolean = false,
    val clientCreatedAt: Long = 0L,
    val lastReviewDate: Long = 0L,
    val clientUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudWorkItemDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val title: String = "",
    val description: String = "",
    val priority: Long = 2L,
    val status: String = ProjectStatus.ACTIVE.name,
    val evidenceState: String = EvidenceState.PLANNED.name,
    val dependencies: List<String> = emptyList(),
    val acceptanceCriteria: List<String> = emptyList(),
    val evidenceRefs: List<String> = emptyList(),
    val demoRecord: Boolean = false,
    val clientCreatedAt: Long = 0L,
    val clientUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudExecutionRecordDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val workItemId: String? = null,
    val toolOrAgent: String = "",
    val actionAttempted: String = "",
    val claim: String = "",
    val evidence: String = "",
    val verificationStatus: String = EvidenceState.CLAIMED.name,
    val aiGeneratedStatement: Boolean = false,
    val demoRecord: Boolean = false,
    val recordedAt: Long = 0L,
    val clientUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudDependencyLinkDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceType: String = DependencyNodeType.WORK_ITEM.name,
    val sourceId: String = "",
    val targetType: String = DependencyNodeType.WORK_ITEM.name,
    val targetId: String = "",
    val relationType: String = DependencyRelationType.INFORMATIONAL.name,
    val notes: String = "",
    val demoRecord: Boolean = false,
    val clientCreatedAt: Long = 0L,
    val clientUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SyncOutcome(
    val pushedProjects: Int,
    val pulledProjects: Int,
    val skippedNewerLocal: Int,
    val skippedNewerRemote: Int,
    val pushedWorkItems: Int,
    val pulledWorkItems: Int,
    val pushedRecords: Int,
    val pulledRecords: Int,
    val pushedLinks: Int,
    val pulledLinks: Int,
    val pulledProjectEntities: List<ProjectEntity>,
    val pulledWorkItemEntities: List<WorkItemEntity>,
    val pulledRecordEntities: List<ExecutionRecordEntity>,
    val pulledLinkEntities: List<DependencyLinkEntity>,
    val timestamp: Long = System.currentTimeMillis()
)

class FirestoreSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must sign in with Google before synchronizing with Firestore.")
        }
        return uid
    }

    /**
     * Conflict-safe bidirectional sync:
     * - Never silently overwrites newer local data with older remote data.
     * - Never silently overwrites newer remote data with older local data.
     * - Preserves immutable createdAt / clientCreatedAt on updates.
     */
    suspend fun syncWorkspace(
        localProjects: List<ProjectEntity>,
        localWorkItems: List<WorkItemEntity>,
        localRecords: List<ExecutionRecordEntity>,
        localLinks: List<DependencyLinkEntity>,
        includeDemoRecords: Boolean = false
    ): Result<SyncOutcome> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val basePath = "users/$uid"
        return try {
            val userDoc = db.collection("users").document(uid)

            // 1. Fetch remote collections scoped to authenticated userId
            val remoteProjectsSnap = userDoc.collection("projects").get().await()
            val remoteProjects = remoteProjectsSnap.documents.mapNotNull { doc ->
                doc.toObject(CloudProjectDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.associateBy { it.id }

            val remoteWorkItemsSnap = userDoc.collection("workItems").get().await()
            val remoteWorkItems = remoteWorkItemsSnap.documents.mapNotNull { doc ->
                doc.toObject(CloudWorkItemDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.associateBy { it.id }

            val remoteRecordsSnap = userDoc.collection("executionRecords").get().await()
            val remoteRecords = remoteRecordsSnap.documents.mapNotNull { doc ->
                doc.toObject(CloudExecutionRecordDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.associateBy { it.id }

            val remoteLinksSnap = userDoc.collection("dependencyLinks").get().await()
            val remoteLinks = remoteLinksSnap.documents.mapNotNull { doc ->
                doc.toObject(CloudDependencyLinkDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.associateBy { it.id }

            val filteredLocalProjects = localProjects.filter { includeDemoRecords || !it.isDemo }
            val filteredLocalWorkItems = localWorkItems.filter { includeDemoRecords || !it.isDemo }
            val filteredLocalRecords = localRecords.filter { includeDemoRecords || !it.isDemo }
            val filteredLocalLinks = localLinks.filter { includeDemoRecords || !it.isDemo }

            var pushedProjects = 0
            var skippedNewerRemote = 0
            val pulledProjects = mutableListOf<ProjectEntity>()
            var skippedNewerLocal = 0

            val localProjectMap = filteredLocalProjects.associateBy { it.id }
            for (local in filteredLocalProjects) {
                val safeId = sanitizeDocId(local.id)
                val remote = remoteProjects[safeId]
                val ref = userDoc.collection("projects").document(safeId)
                if (remote == null) {
                    ref.set(local.toCreateMap(uid)).await()
                    pushedProjects++
                } else if (local.updatedAt > remote.clientUpdatedAt) {
                    ref.update(local.toUpdateMap()).await()
                    pushedProjects++
                } else if (remote.clientUpdatedAt > local.updatedAt) {
                    skippedNewerRemote++
                }
            }
            for ((id, remote) in remoteProjects) {
                val local = localProjectMap[id]
                if (local == null || remote.clientUpdatedAt > local.updatedAt) {
                    pulledProjects += remote.toEntity()
                } else if (local.updatedAt > remote.clientUpdatedAt) {
                    skippedNewerLocal++
                }
            }

            var pushedWorkItems = 0
            val pulledWorkItems = mutableListOf<WorkItemEntity>()
            val localWiMap = filteredLocalWorkItems.associateBy { it.id }
            for (local in filteredLocalWorkItems) {
                val safeId = sanitizeDocId(local.id)
                val remote = remoteWorkItems[safeId]
                val ref = userDoc.collection("workItems").document(safeId)
                if (remote == null) {
                    ref.set(local.toCreateMap(uid)).await()
                    pushedWorkItems++
                } else if (local.updatedAt > remote.clientUpdatedAt) {
                    ref.update(local.toUpdateMap()).await()
                    pushedWorkItems++
                }
            }
            for ((id, remote) in remoteWorkItems) {
                val local = localWiMap[id]
                if (local == null || remote.clientUpdatedAt > local.updatedAt) {
                    pulledWorkItems += remote.toEntity()
                }
            }

            var pushedRecords = 0
            val pulledRecords = mutableListOf<ExecutionRecordEntity>()
            val localRecMap = filteredLocalRecords.associateBy { it.id }
            for (local in filteredLocalRecords) {
                val safeId = sanitizeDocId(local.id)
                val remote = remoteRecords[safeId]
                val ref = userDoc.collection("executionRecords").document(safeId)
                if (remote == null) {
                    ref.set(local.toCreateMap(uid)).await()
                    pushedRecords++
                } else if (local.updatedAt > remote.clientUpdatedAt) {
                    ref.update(local.toUpdateMap()).await()
                    pushedRecords++
                }
            }
            for ((id, remote) in remoteRecords) {
                val local = localRecMap[id]
                if (local == null || remote.clientUpdatedAt > local.updatedAt) {
                    pulledRecords += remote.toEntity()
                }
            }

            var pushedLinks = 0
            val pulledLinks = mutableListOf<DependencyLinkEntity>()
            val localLinkMap = filteredLocalLinks.associateBy { it.id }
            for (local in filteredLocalLinks) {
                val safeId = sanitizeDocId(local.id)
                val remote = remoteLinks[safeId]
                val ref = userDoc.collection("dependencyLinks").document(safeId)
                if (remote == null) {
                    ref.set(local.toCreateMap(uid)).await()
                    pushedLinks++
                } else if (local.updatedAt > remote.clientUpdatedAt) {
                    ref.update(local.toUpdateMap()).await()
                    pushedLinks++
                }
            }
            for ((id, remote) in remoteLinks) {
                val local = localLinkMap[id]
                if (local == null || remote.clientUpdatedAt > local.updatedAt) {
                    pulledLinks += remote.toEntity()
                }
            }

            Result.success(
                SyncOutcome(
                    pushedProjects = pushedProjects,
                    pulledProjects = pulledProjects.size,
                    skippedNewerLocal = skippedNewerLocal,
                    skippedNewerRemote = skippedNewerRemote,
                    pushedWorkItems = pushedWorkItems,
                    pulledWorkItems = pulledWorkItems.size,
                    pushedRecords = pushedRecords,
                    pulledRecords = pulledRecords.size,
                    pushedLinks = pushedLinks,
                    pulledLinks = pulledLinks.size,
                    pulledProjectEntities = pulledProjects,
                    pulledWorkItemEntities = pulledWorkItems,
                    pulledRecordEntities = pulledRecords,
                    pulledLinkEntities = pulledLinks
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }

    private fun sanitizeDocId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "item_0" }

    private fun ProjectEntity.toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.take(160).ifBlank { "Untitled" },
        "description" to description.take(4000),
        "purpose" to purpose.take(2000),
        "currentObjective" to currentObjective.take(2000),
        "repositoryUrl" to repositoryUrl.take(512),
        "driveUrl" to driveUrl.take(512),
        "documentationRefs" to documentationRefs.take(50),
        "storageRefs" to storageRefs.take(50),
        "category" to category.take(80).ifBlank { "Repository" },
        "tags" to tags.take(30),
        "protectedConstraints" to protectedConstraints.take(50),
        "knownFailures" to knownFailures.take(50),
        "openDecisions" to openDecisions.take(50),
        "status" to status.name,
        "priority" to priority.coerceIn(1, 4),
        "lastVerifiedMilestone" to lastVerifiedMilestone.take(1000),
        "lastVerifiedStateSummary" to lastVerifiedStateSummary.take(2000),
        "demoRecord" to isDemo,
        "clientCreatedAt" to createdAt.coerceAtLeast(0L),
        "lastReviewDate" to lastReviewDate.coerceAtLeast(0L),
        "clientUpdatedAt" to updatedAt.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun ProjectEntity.toUpdateMap(): Map<String, Any> = mapOf(
        "name" to name.take(160).ifBlank { "Untitled" },
        "description" to description.take(4000),
        "purpose" to purpose.take(2000),
        "currentObjective" to currentObjective.take(2000),
        "repositoryUrl" to repositoryUrl.take(512),
        "driveUrl" to driveUrl.take(512),
        "documentationRefs" to documentationRefs.take(50),
        "storageRefs" to storageRefs.take(50),
        "category" to category.take(80).ifBlank { "Repository" },
        "tags" to tags.take(30),
        "protectedConstraints" to protectedConstraints.take(50),
        "knownFailures" to knownFailures.take(50),
        "openDecisions" to openDecisions.take(50),
        "status" to status.name,
        "priority" to priority.coerceIn(1, 4),
        "lastVerifiedMilestone" to lastVerifiedMilestone.take(1000),
        "lastVerifiedStateSummary" to lastVerifiedStateSummary.take(2000),
        "demoRecord" to isDemo,
        "lastReviewDate" to lastReviewDate.coerceAtLeast(0L),
        "clientUpdatedAt" to updatedAt.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun CloudProjectDto.toEntity(): ProjectEntity = ProjectEntity(
        id = id,
        name = name,
        description = description,
        purpose = purpose,
        currentObjective = currentObjective,
        repositoryUrl = repositoryUrl,
        driveUrl = driveUrl,
        documentationRefs = documentationRefs,
        storageRefs = storageRefs,
        category = category,
        tags = tags,
        protectedConstraints = protectedConstraints,
        knownFailures = knownFailures,
        openDecisions = openDecisions,
        status = ProjectStatus.fromNameSafe(status),
        priority = priority.toInt().coerceIn(1, 4),
        lastVerifiedMilestone = lastVerifiedMilestone,
        lastVerifiedStateSummary = lastVerifiedStateSummary,
        isDemo = demoRecord,
        createdAt = clientCreatedAt,
        lastReviewDate = lastReviewDate,
        updatedAt = clientUpdatedAt
    )

    private fun WorkItemEntity.toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "projectId" to sanitizeDocId(projectId),
        "title" to title.take(200).ifBlank { "Untitled Task" },
        "description" to description.take(4000),
        "priority" to priority.coerceIn(1, 4),
        "status" to status.name,
        "evidenceState" to evidenceState.name,
        "dependencies" to dependencies.take(50),
        "acceptanceCriteria" to acceptanceCriteria.take(50),
        "evidenceRefs" to evidenceRefs.take(50),
        "demoRecord" to isDemo,
        "clientCreatedAt" to createdAt.coerceAtLeast(0L),
        "clientUpdatedAt" to updatedAt.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun WorkItemEntity.toUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.take(200).ifBlank { "Untitled Task" },
        "description" to description.take(4000),
        "priority" to priority.coerceIn(1, 4),
        "status" to status.name,
        "evidenceState" to evidenceState.name,
        "dependencies" to dependencies.take(50),
        "acceptanceCriteria" to acceptanceCriteria.take(50),
        "evidenceRefs" to evidenceRefs.take(50),
        "demoRecord" to isDemo,
        "clientUpdatedAt" to updatedAt.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun CloudWorkItemDto.toEntity(): WorkItemEntity = WorkItemEntity(
        id = id,
        projectId = projectId,
        title = title,
        description = description,
        priority = priority.toInt().coerceIn(1, 4),
        status = ProjectStatus.fromNameSafe(status),
        evidenceState = EvidenceState.fromNameSafe(evidenceState),
        dependencies = dependencies,
        acceptanceCriteria = acceptanceCriteria,
        evidenceRefs = evidenceRefs,
        isDemo = demoRecord,
        createdAt = clientCreatedAt,
        updatedAt = clientUpdatedAt
    )

    private fun ExecutionRecordEntity.toCreateMap(uid: String): Map<String, Any> {
        val safeStatus = if (aiGeneratedStatement && verificationStatus == EvidenceState.VERIFIED && evidence.isBlank()) {
            EvidenceState.CLAIMED
        } else verificationStatus
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "projectId" to sanitizeDocId(projectId),
            "workItemId" to workItemId?.let { sanitizeDocId(it) },
            "toolOrAgent" to toolOrAgent.take(120).ifBlank { "Operator" },
            "actionAttempted" to actionAttempted.take(2000).ifBlank { "Recorded action" },
            "claim" to claim.take(2000),
            "evidence" to evidence.take(4000),
            "verificationStatus" to safeStatus.name,
            "aiGeneratedStatement" to aiGeneratedStatement,
            "demoRecord" to isDemo,
            "recordedAt" to timestamp.coerceAtLeast(0L),
            "clientUpdatedAt" to updatedAt.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    private fun ExecutionRecordEntity.toUpdateMap(): Map<String, Any> {
        val safeStatus = if (aiGeneratedStatement && verificationStatus == EvidenceState.VERIFIED && evidence.isBlank()) {
            EvidenceState.CLAIMED
        } else verificationStatus
        val raw = mutableMapOf<String, Any?>(
            "workItemId" to workItemId?.let { sanitizeDocId(it) },
            "toolOrAgent" to toolOrAgent.take(120).ifBlank { "Operator" },
            "actionAttempted" to actionAttempted.take(2000).ifBlank { "Recorded action" },
            "claim" to claim.take(2000),
            "evidence" to evidence.take(4000),
            "verificationStatus" to safeStatus.name,
            "aiGeneratedStatement" to aiGeneratedStatement,
            "demoRecord" to isDemo,
            "clientUpdatedAt" to updatedAt.coerceAtLeast(0L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    private fun CloudExecutionRecordDto.toEntity(): ExecutionRecordEntity = ExecutionRecordEntity(
        id = id,
        projectId = projectId,
        workItemId = workItemId,
        toolOrAgent = toolOrAgent,
        actionAttempted = actionAttempted,
        claim = claim,
        evidence = evidence,
        timestamp = recordedAt,
        verificationStatus = EvidenceState.fromNameSafe(verificationStatus),
        aiGeneratedStatement = aiGeneratedStatement,
        isDemo = demoRecord,
        updatedAt = clientUpdatedAt
    )

    private fun DependencyLinkEntity.toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "sourceType" to sourceType.name,
        "sourceId" to sanitizeDocId(sourceId),
        "targetType" to targetType.name,
        "targetId" to sanitizeDocId(targetId),
        "relationType" to relationType.name,
        "notes" to notes.take(1000),
        "demoRecord" to isDemo,
        "clientCreatedAt" to createdAt.coerceAtLeast(0L),
        "clientUpdatedAt" to updatedAt.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun DependencyLinkEntity.toUpdateMap(): Map<String, Any> = mapOf(
        "sourceType" to sourceType.name,
        "sourceId" to sanitizeDocId(sourceId),
        "targetType" to targetType.name,
        "targetId" to sanitizeDocId(targetId),
        "relationType" to relationType.name,
        "notes" to notes.take(1000),
        "demoRecord" to isDemo,
        "clientUpdatedAt" to updatedAt.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun CloudDependencyLinkDto.toEntity(): DependencyLinkEntity = DependencyLinkEntity(
        id = id,
        sourceType = DependencyNodeType.fromNameSafe(sourceType),
        sourceId = sourceId,
        targetType = DependencyNodeType.fromNameSafe(targetType),
        targetId = targetId,
        relationType = DependencyRelationType.fromNameSafe(relationType),
        notes = notes,
        isDemo = demoRecord,
        createdAt = clientCreatedAt,
        updatedAt = clientUpdatedAt
    )
}
