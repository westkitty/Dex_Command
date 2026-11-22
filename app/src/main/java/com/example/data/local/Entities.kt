package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.domain.model.DependencyNodeType
import com.example.domain.model.DependencyRelationType
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus
import org.json.JSONArray

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
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
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val priority: Int = 2, // 1 = Critical, 2 = High, 3 = Normal, 4 = Low
    val lastVerifiedMilestone: String = "",
    val lastVerifiedStateSummary: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastReviewDate: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "work_items",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class WorkItemEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val description: String = "",
    val priority: Int = 2,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val evidenceState: EvidenceState = EvidenceState.PLANNED,
    val dependencies: List<String> = emptyList(),
    val acceptanceCriteria: List<String> = emptyList(),
    val evidenceRefs: List<String> = emptyList(),
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "execution_records",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index("workItemId")]
)
data class ExecutionRecordEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val workItemId: String? = null,
    val toolOrAgent: String,
    val actionAttempted: String,
    val claim: String = "",
    val evidence: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val verificationStatus: EvidenceState = EvidenceState.CLAIMED,
    val aiGeneratedStatement: Boolean = false,
    val isDemo: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dependency_links")
data class DependencyLinkEntity(
    @PrimaryKey val id: String,
    val sourceType: DependencyNodeType,
    val sourceId: String,
    val targetType: DependencyNodeType,
    val targetId: String,
    val relationType: DependencyRelationType,
    val notes: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "imported_documents")
data class ImportedDocumentEntity(
    @PrimaryKey val id: String,
    val projectId: String? = null,
    val fileName: String,
    val format: String, // MARKDOWN, TXT, JSON
    val rawContent: String,
    val contentHash: String,
    val aiSummary: String? = null,
    val importedAt: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
)

@Entity(tableName = "audit_events")
data class AuditEventEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)

class DexTypeConverters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        val array = JSONArray()
        list?.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { idx -> array.optString(idx) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromProjectStatus(status: ProjectStatus): String = status.name

    @TypeConverter
    fun toProjectStatus(raw: String?): ProjectStatus = ProjectStatus.fromNameSafe(raw)

    @TypeConverter
    fun fromEvidenceState(state: EvidenceState): String = state.name

    @TypeConverter
    fun toEvidenceState(raw: String?): EvidenceState = EvidenceState.fromNameSafe(raw)

    @TypeConverter
    fun fromNodeType(type: DependencyNodeType): String = type.name

    @TypeConverter
    fun toNodeType(raw: String?): DependencyNodeType = DependencyNodeType.fromNameSafe(raw)

    @TypeConverter
    fun fromRelationType(type: DependencyRelationType): String = type.name

    @TypeConverter
    fun toRelationType(raw: String?): DependencyRelationType = DependencyRelationType.fromNameSafe(raw)
}
