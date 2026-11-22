package com.example.domain.engine

import com.example.data.local.DependencyLinkEntity
import com.example.data.local.ExecutionRecordEntity
import com.example.data.local.ImportedDocumentEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.WorkItemEntity
import com.example.domain.model.DependencyNodeType
import com.example.domain.model.DependencyRelationType
import com.example.domain.model.EvidenceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ContinuityRecoveryGenerator {

    fun generateContinuationPacket(
        project: ProjectEntity,
        workItems: List<WorkItemEntity>,
        executionRecords: List<ExecutionRecordEntity>,
        dependencyLinks: List<DependencyLinkEntity>,
        importedDocuments: List<ImportedDocumentEntity>,
        allProjects: List<ProjectEntity>,
        allWorkItems: List<WorkItemEntity>,
        generatedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val verifiedRecords = executionRecords
            .filter { it.verificationStatus == EvidenceState.VERIFIED || it.verificationStatus == EvidenceState.OBSERVED }
            .sortedByDescending { it.timestamp }

        val unverifiedClaims = executionRecords
            .filter { it.verificationStatus == EvidenceState.CLAIMED || it.verificationStatus == EvidenceState.UNKNOWN }
            .sortedByDescending { it.timestamp }

        val failedRecords = executionRecords
            .filter { it.verificationStatus == EvidenceState.FAILED }
            .sortedByDescending { it.timestamp }

        val nextTasks = DependencyResolver.findNextActionableTasks(
            projects = allProjects,
            workItems = allWorkItems,
            allLinks = dependencyLinks
        ).filter { it.projectId == project.id }

        val nextBoundedTask = nextTasks.firstOrNull()
        val projectBlockers = DependencyResolver.resolveUnresolvedBlockersForProject(
            projectId = project.id,
            allLinks = dependencyLinks,
            projects = allProjects,
            workItems = allWorkItems
        )

        val projectNameMap = allProjects.associate { it.id to it.name }
        val workItemTitleMap = allWorkItems.associate { it.id to it.title }

        return buildString {
            appendLine("# DEX//COMMAND CONTINUITY RECOVERY PACKET")
            appendLine()
            appendLine("- **Project:** ${project.name} (`${project.id}`)")
            appendLine("- **Status:** `${project.status.label}` | **Priority:** P${project.priority} | **Category:** ${project.category}")
            if (project.isDemo) {
                appendLine("- **Record Classification:** `[DEMONSTRATION DATA — NOT PRODUCTION USER RECORD]`")
            }
            appendLine("- **Generated (UTC):** ${formatUtc(generatedAtMillis)}")
            appendLine("- **Last Reviewed (UTC):** ${formatUtc(project.lastReviewDate)}")
            appendLine()

            appendLine("## 1. Project Purpose")
            appendLine(project.purpose.ifBlank { project.description.ifBlank { "_Not yet recorded._" } })
            if (project.currentObjective.isNotBlank()) {
                appendLine()
                appendLine("**Current Objective:** ${project.currentObjective}")
            }
            appendLine()

            appendLine("## 2. Verified Current State")
            if (project.lastVerifiedStateSummary.isNotBlank()) {
                appendLine(project.lastVerifiedStateSummary)
                appendLine()
            }
            if (verifiedRecords.isEmpty()) {
                appendLine("- _No independently VERIFIED or OBSERVED execution records recorded yet._")
            } else {
                verifiedRecords.take(5).forEach { rec ->
                    appendLine("- `[${rec.verificationStatus.label}]` (${formatUtc(rec.timestamp)} · ${rec.toolOrAgent}): ${rec.actionAttempted}")
                    if (rec.evidence.isNotBlank()) {
                        appendLine("  - **Evidence:** ${rec.evidence}")
                    }
                }
            }
            if (unverifiedClaims.isNotEmpty()) {
                appendLine()
                appendLine("### Unverified Claims (Do NOT Treat as Verified State)")
                unverifiedClaims.take(3).forEach { rec ->
                    val aiFlag = if (rec.aiGeneratedStatement) " [AI STATEMENT — UNVERIFIED]" else ""
                    appendLine("- `[${rec.verificationStatus.label}]$aiFlag` (${rec.toolOrAgent}): ${rec.claim.ifBlank { rec.actionAttempted }}")
                }
            }
            appendLine()

            appendLine("## 3. Important Constraints (Protected Behavior)")
            if (project.protectedConstraints.isEmpty()) {
                appendLine("- _No protected constraints registered._")
            } else {
                project.protectedConstraints.forEach { constraint ->
                    appendLine("- $constraint")
                }
            }
            appendLine()

            appendLine("## 4. Known Unresolved Issues & Open Decisions")
            appendLine("### Known Failures")
            if (project.knownFailures.isEmpty() && failedRecords.isEmpty()) {
                appendLine("- _No known failures logged._")
            } else {
                project.knownFailures.forEach { appendLine("- $it") }
                failedRecords.take(3).forEach { rec ->
                    appendLine("- `[FAILED]` (${rec.toolOrAgent}): ${rec.actionAttempted} — ${rec.evidence.ifBlank { rec.claim }}")
                }
            }
            appendLine()
            appendLine("### Open Decisions")
            if (project.openDecisions.isEmpty()) {
                appendLine("- _No open decisions pending._")
            } else {
                project.openDecisions.forEach { appendLine("- $it") }
            }
            if (projectBlockers.isNotEmpty()) {
                appendLine()
                appendLine("### Active Blocking Dependencies")
                projectBlockers.forEach { dep ->
                    val targetName = when (dep.targetType) {
                        DependencyNodeType.PROJECT -> projectNameMap[dep.targetId] ?: dep.targetId
                        DependencyNodeType.WORK_ITEM -> workItemTitleMap[dep.targetId] ?: dep.targetId
                    }
                    appendLine("- `[BLOCKING]` Depends on ${dep.targetType.label} `$targetName` (${dep.notes.ifBlank { "unresolved blocker" }})")
                }
            }
            appendLine()

            appendLine("## 5. Relevant Source References")
            appendLine("- **GitHub Repository:** ${project.repositoryUrl.ifBlank { "_None linked_" }}")
            appendLine("- **Google Drive Reference:** ${project.driveUrl.ifBlank { "_None linked_" }}")
            if (project.documentationRefs.isNotEmpty()) {
                appendLine("- **Documentation References:**")
                project.documentationRefs.forEach { appendLine("  - $it") }
            }
            if (project.storageRefs.isNotEmpty()) {
                appendLine("- **Storage References:**")
                project.storageRefs.forEach { appendLine("  - $it") }
            }
            val linkedDocs = importedDocuments.filter { it.projectId == project.id }
            if (linkedDocs.isNotEmpty()) {
                appendLine("- **Preserved Imported Documents (Read-Only Originals):**")
                linkedDocs.forEach { doc ->
                    appendLine("  - `${doc.fileName}` (${doc.format}, SHA-256: `${doc.contentHash.take(12)}`)")
                }
            }
            appendLine()

            appendLine("## 6. Last Successful Milestone")
            appendLine(project.lastVerifiedMilestone.ifBlank { "_No verified milestone recorded._" })
            appendLine()

            appendLine("## 7. Next Bounded Task")
            if (nextBoundedTask != null) {
                appendLine("- **Task:** ${nextBoundedTask.title} (`${nextBoundedTask.id}`)")
                appendLine("- **Priority:** P${nextBoundedTask.priority} | **Status:** `${nextBoundedTask.status.label}` | **Evidence:** `${nextBoundedTask.evidenceState.label}`")
                if (nextBoundedTask.description.isNotBlank()) {
                    appendLine("- **Scope:** ${nextBoundedTask.description}")
                }
            } else {
                appendLine("- _No unblocked actionable work item available. Resolve active blockers or define next task._")
            }
            appendLine()

            appendLine("## 8. Acceptance Criteria")
            val criteria = nextBoundedTask?.acceptanceCriteria.orEmpty()
            if (criteria.isEmpty()) {
                appendLine("- Define verifiable acceptance criteria and record concrete evidence before marking VERIFIED.")
            } else {
                criteria.forEach { c ->
                    appendLine("- [ ] $c")
                }
            }
        }
    }

    fun generateMarkdownProjectReport(
        projects: List<ProjectEntity>,
        workItems: List<WorkItemEntity>,
        executionRecords: List<ExecutionRecordEntity>,
        dependencyLinks: List<DependencyLinkEntity>,
        generatedAtMillis: Long = System.currentTimeMillis()
    ): String = buildString {
        appendLine("# DEX//COMMAND PORTFOLIO REPORT")
        appendLine("Generated (UTC): ${formatUtc(generatedAtMillis)}")
        appendLine()
        appendLine("## Summary")
        appendLine("- Total Projects: ${projects.size} (Demo: ${projects.count { it.isDemo }})")
        appendLine("- Total Work Items: ${workItems.size}")
        appendLine("- Total Execution Records: ${executionRecords.size}")
        appendLine("- Blocking Dependencies: ${dependencyLinks.count { it.relationType == DependencyRelationType.BLOCKING }}")
        appendLine()
        projects.sortedBy { it.priority }.forEach { project ->
            val pItems = workItems.filter { it.projectId == project.id }
            val pRecs = executionRecords.filter { it.projectId == project.id }
            val demoTag = if (project.isDemo) " `[DEMO]`" else ""
            appendLine("---")
            appendLine("## ${project.name}$demoTag (`${project.status.label}` · P${project.priority})")
            appendLine("- **Category:** ${project.category} | **Tags:** ${project.tags.joinToString(", ").ifBlank { "None" }}")
            appendLine("- **Purpose:** ${project.purpose.ifBlank { project.description }}")
            appendLine("- **Last Verified Milestone:** ${project.lastVerifiedMilestone.ifBlank { "None" }}")
            appendLine("- **Work Items (${pItems.size}):**")
            pItems.forEach { wi ->
                appendLine("  - `[${wi.status.label} / ${wi.evidenceState.label}]` P${wi.priority}: ${wi.title}")
            }
            appendLine("- **Recent Execution Ledger (${pRecs.size}):**")
            pRecs.sortedByDescending { it.timestamp }.take(3).forEach { rec ->
                appendLine("  - `[${rec.verificationStatus.label}]` ${rec.toolOrAgent}: ${rec.actionAttempted}")
            }
            appendLine()
        }
    }

    fun formatUtc(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMillis))
    }
}
