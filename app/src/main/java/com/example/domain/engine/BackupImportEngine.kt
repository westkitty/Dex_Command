package com.example.domain.engine

import com.example.data.local.AuditEventEntity
import com.example.data.local.DependencyLinkEntity
import com.example.data.local.ExecutionRecordEntity
import com.example.data.local.ImportedDocumentEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.WorkItemEntity
import com.example.domain.model.DependencyNodeType
import com.example.domain.model.DependencyRelationType
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class DexBackupPayload(
    val schemaVersion: Int,
    val exportedAt: Long,
    val projects: List<ProjectEntity>,
    val workItems: List<WorkItemEntity>,
    val executionRecords: List<ExecutionRecordEntity>,
    val dependencyLinks: List<DependencyLinkEntity>,
    val importedDocuments: List<ImportedDocumentEntity>,
    val auditEvents: List<AuditEventEntity>
)

data class ParsedTextImport(
    val document: ImportedDocumentEntity,
    val suggestedProject: ProjectEntity?
)

sealed interface BackupParseResult {
    data class Success(val payload: DexBackupPayload) : BackupParseResult
    data class Failure(val error: String) : BackupParseResult
}

object BackupImportEngine {
    const val CURRENT_SCHEMA_VERSION = 1

    fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun exportToJson(payload: DexBackupPayload): String {
        val root = JSONObject()
        root.put("app", "DEX//COMMAND")
        root.put("schemaVersion", payload.schemaVersion)
        root.put("exportedAt", payload.exportedAt)

        val dataObj = JSONObject().apply {
            put("projects", JSONArray().apply {
                payload.projects.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("description", p.description)
                        put("purpose", p.purpose)
                        put("currentObjective", p.currentObjective)
                        put("repositoryUrl", p.repositoryUrl)
                        put("driveUrl", p.driveUrl)
                        put("documentationRefs", p.documentationRefs.toJsonArray())
                        put("storageRefs", p.storageRefs.toJsonArray())
                        put("category", p.category)
                        put("tags", p.tags.toJsonArray())
                        put("protectedConstraints", p.protectedConstraints.toJsonArray())
                        put("knownFailures", p.knownFailures.toJsonArray())
                        put("openDecisions", p.openDecisions.toJsonArray())
                        put("status", p.status.name)
                        put("priority", p.priority)
                        put("lastVerifiedMilestone", p.lastVerifiedMilestone)
                        put("lastVerifiedStateSummary", p.lastVerifiedStateSummary)
                        put("isDemo", p.isDemo)
                        put("createdAt", p.createdAt)
                        put("lastReviewDate", p.lastReviewDate)
                        put("updatedAt", p.updatedAt)
                    })
                }
            })
            put("workItems", JSONArray().apply {
                payload.workItems.forEach { w ->
                    put(JSONObject().apply {
                        put("id", w.id)
                        put("projectId", w.projectId)
                        put("title", w.title)
                        put("description", w.description)
                        put("priority", w.priority)
                        put("status", w.status.name)
                        put("evidenceState", w.evidenceState.name)
                        put("dependencies", w.dependencies.toJsonArray())
                        put("acceptanceCriteria", w.acceptanceCriteria.toJsonArray())
                        put("evidenceRefs", w.evidenceRefs.toJsonArray())
                        put("isDemo", w.isDemo)
                        put("createdAt", w.createdAt)
                        put("updatedAt", w.updatedAt)
                    })
                }
            })
            put("executionRecords", JSONArray().apply {
                payload.executionRecords.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id)
                        put("projectId", r.projectId)
                        put("workItemId", r.workItemId ?: JSONObject.NULL)
                        put("toolOrAgent", r.toolOrAgent)
                        put("actionAttempted", r.actionAttempted)
                        put("claim", r.claim)
                        put("evidence", r.evidence)
                        put("timestamp", r.timestamp)
                        put("verificationStatus", r.verificationStatus.name)
                        put("aiGeneratedStatement", r.aiGeneratedStatement)
                        put("isDemo", r.isDemo)
                        put("updatedAt", r.updatedAt)
                    })
                }
            })
            put("dependencyLinks", JSONArray().apply {
                payload.dependencyLinks.forEach { d ->
                    put(JSONObject().apply {
                        put("id", d.id)
                        put("sourceType", d.sourceType.name)
                        put("sourceId", d.sourceId)
                        put("targetType", d.targetType.name)
                        put("targetId", d.targetId)
                        put("relationType", d.relationType.name)
                        put("notes", d.notes)
                        put("isDemo", d.isDemo)
                        put("createdAt", d.createdAt)
                        put("updatedAt", d.updatedAt)
                    })
                }
            })
            put("importedDocuments", JSONArray().apply {
                payload.importedDocuments.forEach { doc ->
                    put(JSONObject().apply {
                        put("id", doc.id)
                        put("projectId", doc.projectId ?: JSONObject.NULL)
                        put("fileName", doc.fileName)
                        put("format", doc.format)
                        put("rawContent", doc.rawContent)
                        put("contentHash", doc.contentHash)
                        put("aiSummary", doc.aiSummary ?: JSONObject.NULL)
                        put("importedAt", doc.importedAt)
                        put("isDemo", doc.isDemo)
                    })
                }
            })
            put("auditEvents", JSONArray().apply {
                payload.auditEvents.forEach { evt ->
                    put(JSONObject().apply {
                        put("id", evt.id)
                        put("entityType", evt.entityType)
                        put("entityId", evt.entityId)
                        put("action", evt.action)
                        put("summary", evt.summary)
                        put("timestamp", evt.timestamp)
                    })
                }
            })
        }

        val canonicalData = dataObj.toString()
        root.put("checksumSha256", sha256(canonicalData))
        root.put("data", dataObj)
        return root.toString(2)
    }

    fun parseBackupJson(rawJson: String): BackupParseResult {
        if (rawJson.isBlank()) {
            return BackupParseResult.Failure("Backup file is empty.")
        }
        return try {
            val root = JSONObject(rawJson)
            val appMarker = root.optString("app", "")
            if (appMarker != "DEX//COMMAND") {
                return BackupParseResult.Failure("Unrecognized JSON backup format (missing DEX//COMMAND header).")
            }
            val schemaVersion = root.optInt("schemaVersion", -1)
            if (schemaVersion < 1 || schemaVersion > CURRENT_SCHEMA_VERSION) {
                return BackupParseResult.Failure("Unsupported backup schemaVersion: $schemaVersion.")
            }
            val dataObj = root.optJSONObject("data")
                ?: return BackupParseResult.Failure("Corrupt backup: missing 'data' payload object.")

            val expectedChecksum = root.optString("checksumSha256", "")
            if (expectedChecksum.isNotBlank()) {
                val actualChecksum = sha256(dataObj.toString())
                if (!expectedChecksum.equals(actualChecksum, ignoreCase = true)) {
                    return BackupParseResult.Failure("Backup integrity check failed (SHA-256 checksum mismatch). Existing records were not modified.")
                }
            }

            val projectsArray = dataObj.optJSONArray("projects")
                ?: return BackupParseResult.Failure("Corrupt backup: missing 'projects' array.")

            val projects = (0 until projectsArray.length()).map { idx ->
                val obj = projectsArray.getJSONObject(idx)
                val id = obj.optString("id").trim()
                val name = obj.optString("name").trim()
                if (id.isEmpty() || name.isEmpty()) {
                    return BackupParseResult.Failure("Corrupt backup: project at index $idx is missing required id or name.")
                }
                ProjectEntity(
                    id = id,
                    name = name,
                    description = obj.optString("description", ""),
                    purpose = obj.optString("purpose", ""),
                    currentObjective = obj.optString("currentObjective", ""),
                    repositoryUrl = obj.optString("repositoryUrl", ""),
                    driveUrl = obj.optString("driveUrl", ""),
                    documentationRefs = obj.optJSONArray("documentationRefs").toStringList(),
                    storageRefs = obj.optJSONArray("storageRefs").toStringList(),
                    category = obj.optString("category", "Repository"),
                    tags = obj.optJSONArray("tags").toStringList(),
                    protectedConstraints = obj.optJSONArray("protectedConstraints").toStringList(),
                    knownFailures = obj.optJSONArray("knownFailures").toStringList(),
                    openDecisions = obj.optJSONArray("openDecisions").toStringList(),
                    status = ProjectStatus.fromNameSafe(obj.optString("status")),
                    priority = obj.optInt("priority", 2).coerceIn(1, 4),
                    lastVerifiedMilestone = obj.optString("lastVerifiedMilestone", ""),
                    lastVerifiedStateSummary = obj.optString("lastVerifiedStateSummary", ""),
                    isDemo = obj.optBoolean("isDemo", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    lastReviewDate = obj.optLong("lastReviewDate", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            }
            val projectIds = projects.map { it.id }.toSet()

            val workItemsArray = dataObj.optJSONArray("workItems") ?: JSONArray()
            val workItems = (0 until workItemsArray.length()).map { idx ->
                val obj = workItemsArray.getJSONObject(idx)
                val id = obj.optString("id").trim()
                val projectId = obj.optString("projectId").trim()
                val title = obj.optString("title").trim()
                if (id.isEmpty() || title.isEmpty() || projectId !in projectIds) {
                    return BackupParseResult.Failure("Corrupt backup: work item '$id' references missing project '$projectId'.")
                }
                WorkItemEntity(
                    id = id,
                    projectId = projectId,
                    title = title,
                    description = obj.optString("description", ""),
                    priority = obj.optInt("priority", 2).coerceIn(1, 4),
                    status = ProjectStatus.fromNameSafe(obj.optString("status")),
                    evidenceState = EvidenceState.fromNameSafe(obj.optString("evidenceState")),
                    dependencies = obj.optJSONArray("dependencies").toStringList(),
                    acceptanceCriteria = obj.optJSONArray("acceptanceCriteria").toStringList(),
                    evidenceRefs = obj.optJSONArray("evidenceRefs").toStringList(),
                    isDemo = obj.optBoolean("isDemo", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            }

            val recordsArray = dataObj.optJSONArray("executionRecords") ?: JSONArray()
            val records = (0 until recordsArray.length()).map { idx ->
                val obj = recordsArray.getJSONObject(idx)
                val id = obj.optString("id").trim()
                val projectId = obj.optString("projectId").trim()
                if (id.isEmpty() || projectId !in projectIds) {
                    return BackupParseResult.Failure("Corrupt backup: execution record '$id' references missing project '$projectId'.")
                }
                val rawWorkItemId = if (obj.isNull("workItemId")) null else obj.optString("workItemId").takeIf { it.isNotBlank() }
                ExecutionRecordEntity(
                    id = id,
                    projectId = projectId,
                    workItemId = rawWorkItemId,
                    toolOrAgent = obj.optString("toolOrAgent", "Unknown"),
                    actionAttempted = obj.optString("actionAttempted", ""),
                    claim = obj.optString("claim", ""),
                    evidence = obj.optString("evidence", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    verificationStatus = EvidenceState.fromNameSafe(obj.optString("verificationStatus")),
                    aiGeneratedStatement = obj.optBoolean("aiGeneratedStatement", false),
                    isDemo = obj.optBoolean("isDemo", false),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            }

            val linksArray = dataObj.optJSONArray("dependencyLinks") ?: JSONArray()
            val links = (0 until linksArray.length()).map { idx ->
                val obj = linksArray.getJSONObject(idx)
                DependencyLinkEntity(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    sourceType = DependencyNodeType.fromNameSafe(obj.optString("sourceType")),
                    sourceId = obj.optString("sourceId"),
                    targetType = DependencyNodeType.fromNameSafe(obj.optString("targetType")),
                    targetId = obj.optString("targetId"),
                    relationType = DependencyRelationType.fromNameSafe(obj.optString("relationType")),
                    notes = obj.optString("notes", ""),
                    isDemo = obj.optBoolean("isDemo", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            }

            val docsArray = dataObj.optJSONArray("importedDocuments") ?: JSONArray()
            val docs = (0 until docsArray.length()).map { idx ->
                val obj = docsArray.getJSONObject(idx)
                val rawContent = obj.optString("rawContent", "")
                ImportedDocumentEntity(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    projectId = if (obj.isNull("projectId")) null else obj.optString("projectId").takeIf { it.isNotBlank() },
                    fileName = obj.optString("fileName", "imported.txt"),
                    format = obj.optString("format", "TXT"),
                    rawContent = rawContent,
                    contentHash = obj.optString("contentHash").ifBlank { sha256(rawContent) },
                    aiSummary = if (obj.isNull("aiSummary")) null else obj.optString("aiSummary").takeIf { it.isNotBlank() },
                    importedAt = obj.optLong("importedAt", System.currentTimeMillis()),
                    isDemo = obj.optBoolean("isDemo", false)
                )
            }

            val auditsArray = dataObj.optJSONArray("auditEvents") ?: JSONArray()
            val audits = (0 until auditsArray.length()).map { idx ->
                val obj = auditsArray.getJSONObject(idx)
                AuditEventEntity(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    entityType = obj.optString("entityType", "SYSTEM"),
                    entityId = obj.optString("entityId", ""),
                    action = obj.optString("action", "IMPORTED"),
                    summary = obj.optString("summary", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }

            BackupParseResult.Success(
                DexBackupPayload(
                    schemaVersion = schemaVersion,
                    exportedAt = root.optLong("exportedAt", System.currentTimeMillis()),
                    projects = projects,
                    workItems = workItems,
                    executionRecords = records,
                    dependencyLinks = links,
                    importedDocuments = docs,
                    auditEvents = audits
                )
            )
        } catch (e: Exception) {
            BackupParseResult.Failure("Invalid JSON backup: ${e.localizedMessage ?: "parse error"}")
        }
    }

    /**
     * Parses Markdown, TXT, or non-backup JSON documents while preserving the verbatim original
     * content immutably inside ImportedDocumentEntity.
     */
    fun parseDocumentImport(
        fileName: String,
        rawContent: String,
        targetProjectId: String? = null,
        extractProjectDraft: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): ParsedTextImport {
        val normalizedExt = fileName.substringAfterLast('.', "").lowercase()
        val format = when (normalizedExt) {
            "md", "markdown" -> "MARKDOWN"
            "json" -> "JSON"
            else -> "TXT"
        }

        val docId = "doc-${UUID.randomUUID().toString().take(8)}"
        val hash = sha256(rawContent)

        val suggestedProject = if (extractProjectDraft && targetProjectId == null) {
            extractProjectFromText(fileName, rawContent, nowMillis)
        } else null

        val document = ImportedDocumentEntity(
            id = docId,
            projectId = targetProjectId ?: suggestedProject?.id,
            fileName = fileName,
            format = format,
            rawContent = rawContent,
            contentHash = hash,
            aiSummary = null,
            importedAt = nowMillis,
            isDemo = false
        )

        return ParsedTextImport(document = document, suggestedProject = suggestedProject)
    }

    private fun extractProjectFromText(
        fileName: String,
        rawContent: String,
        nowMillis: Long
    ): ProjectEntity {
        val lines = rawContent.lines()
        val headingTitle = lines.firstOrNull { it.trim().startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            ?.take(120)
        val name = headingTitle?.takeIf { it.isNotBlank() }
            ?: fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim().ifBlank { "Imported Project" }

        val githubUrl = Regex("""https://github\.com/[A-Za-z0-9_.\-]+/[A-Za-z0-9_.\-]+""")
            .find(rawContent)?.value.orEmpty()
        val driveUrl = Regex("""https://drive\.google\.com/\S+""")
            .find(rawContent)?.value.orEmpty()

        val firstParagraph = lines
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("-") }
            .orEmpty()
            .take(500)

        return ProjectEntity(
            id = "proj-${UUID.randomUUID().toString().take(8)}",
            name = name,
            description = firstParagraph.ifBlank { "Imported from $fileName" },
            purpose = firstParagraph.ifBlank { "Imported from $fileName. Review preserved source document to verify scope." },
            currentObjective = "Review imported source document '$fileName' and confirm verified baseline.",
            repositoryUrl = githubUrl,
            driveUrl = driveUrl,
            documentationRefs = listOf("Imported: $fileName"),
            category = "Repository",
            tags = listOf("imported"),
            status = ProjectStatus.REVIEW,
            priority = 2,
            isDemo = false,
            createdAt = nowMillis,
            lastReviewDate = nowMillis,
            updatedAt = nowMillis
        )
    }

    private fun List<String>.toJsonArray(): JSONArray = JSONArray().also { arr ->
        forEach { arr.put(it) }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { idx -> optString(idx)?.takeIf { it.isNotEmpty() } }
    }
}
