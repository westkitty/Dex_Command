package com.example.domain.engine

import com.example.data.local.ExecutionRecordEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.WorkItemEntity
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus

data class EvidenceClassificationResult(
    val effectiveState: EvidenceState,
    val wasDowngraded: Boolean,
    val reason: String? = null
)

data class StatusInconsistency(
    val entityType: String,
    val entityId: String,
    val title: String,
    val severity: String,
    val detail: String
)

object EvidenceClassifier {

    /**
     * Authoritative rule:
     * Project or execution status MUST NEVER be inferred as VERIFIED solely from an
     * AI-generated statement without independent observed/verified evidence.
     */
    fun classify(
        requestedState: EvidenceState,
        claim: String,
        evidence: String,
        aiGeneratedStatement: Boolean,
        humanConfirmedEvidence: Boolean
    ): EvidenceClassificationResult {
        val trimmedEvidence = evidence.trim()
        val trimmedClaim = claim.trim()

        if (requestedState == EvidenceState.VERIFIED) {
            if (trimmedEvidence.isEmpty()) {
                return EvidenceClassificationResult(
                    effectiveState = if (trimmedClaim.isNotEmpty()) EvidenceState.CLAIMED else EvidenceState.UNKNOWN,
                    wasDowngraded = true,
                    reason = "VERIFIED requires concrete verification evidence. Downgraded to prevent unverified assumption."
                )
            }
            if (aiGeneratedStatement && !humanConfirmedEvidence) {
                return EvidenceClassificationResult(
                    effectiveState = EvidenceState.CLAIMED,
                    wasDowngraded = true,
                    reason = "Status cannot be marked VERIFIED solely from an AI-generated statement. Requires independent human/tool verification."
                )
            }
            return EvidenceClassificationResult(EvidenceState.VERIFIED, wasDowngraded = false)
        }

        if (requestedState == EvidenceState.OBSERVED && trimmedEvidence.isEmpty()) {
            return EvidenceClassificationResult(
                effectiveState = if (trimmedClaim.isNotEmpty()) EvidenceState.CLAIMED else EvidenceState.UNKNOWN,
                wasDowngraded = true,
                reason = "OBSERVED requires recorded observation notes or output."
            )
        }

        return EvidenceClassificationResult(requestedState, wasDowngraded = false)
    }

    fun auditInconsistencies(
        projects: List<ProjectEntity>,
        workItems: List<WorkItemEntity>,
        records: List<ExecutionRecordEntity>
    ): List<StatusInconsistency> {
        val findings = mutableListOf<StatusInconsistency>()
        val itemsByProject = workItems.groupBy { it.projectId }
        val recordsByProject = records.groupBy { it.projectId }

        for (project in projects) {
            val pItems = itemsByProject[project.id].orEmpty()
            val pRecords = recordsByProject[project.id].orEmpty()

            if (project.status == ProjectStatus.COMPLETED) {
                val openOrBlocked = pItems.filter {
                    it.status == ProjectStatus.BLOCKED || it.status == ProjectStatus.ACTIVE
                }
                if (openOrBlocked.isNotEmpty()) {
                    findings += StatusInconsistency(
                        entityType = "PROJECT",
                        entityId = project.id,
                        title = project.name,
                        severity = "HIGH",
                        detail = "Marked COMPLETED while ${openOrBlocked.size} work item(s) remain ACTIVE or BLOCKED."
                    )
                }
                val hasVerified = pRecords.any { it.verificationStatus == EvidenceState.VERIFIED } ||
                    pItems.any { it.evidenceState == EvidenceState.VERIFIED }
                if (!hasVerified) {
                    findings += StatusInconsistency(
                        entityType = "PROJECT",
                        entityId = project.id,
                        title = project.name,
                        severity = "HIGH",
                        detail = "Marked COMPLETED with zero VERIFIED execution records or work items."
                    )
                }
            }

            for (item in pItems) {
                if (item.status == ProjectStatus.COMPLETED && item.evidenceState != EvidenceState.VERIFIED) {
                    findings += StatusInconsistency(
                        entityType = "WORK_ITEM",
                        entityId = item.id,
                        title = item.title,
                        severity = "MEDIUM",
                        detail = "Work item marked COMPLETED, but evidence state is ${item.evidenceState.label} (not VERIFIED)."
                    )
                }
                if (item.evidenceState == EvidenceState.FAILED && item.status == ProjectStatus.ACTIVE) {
                    findings += StatusInconsistency(
                        entityType = "WORK_ITEM",
                        entityId = item.id,
                        title = item.title,
                        severity = "MEDIUM",
                        detail = "Work item has FAILED evidence state but remains ACTIVE instead of BLOCKED/REVIEW."
                    )
                }
            }

            for (record in pRecords) {
                if (record.aiGeneratedStatement &&
                    record.verificationStatus == EvidenceState.VERIFIED &&
                    record.evidence.isBlank()
                ) {
                    findings += StatusInconsistency(
                        entityType = "EXECUTION_RECORD",
                        entityId = record.id,
                        title = "${record.toolOrAgent}: ${record.actionAttempted.take(48)}",
                        severity = "HIGH",
                        detail = "AI-generated statement is marked VERIFIED without independent evidence."
                    )
                }
            }
        }
        return findings
    }
}
