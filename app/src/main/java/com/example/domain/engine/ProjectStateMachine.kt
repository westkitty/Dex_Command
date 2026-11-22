package com.example.domain.engine

import com.example.data.local.ProjectEntity
import com.example.data.local.WorkItemEntity
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus

sealed interface TransitionResult {
    data class Allowed(val nextStatus: ProjectStatus, val warning: String? = null) : TransitionResult
    data class Rejected(val reason: String) : TransitionResult
}

object ProjectStateMachine {
    private const val REVIEW_STALE_DAYS_MS = 7L * 24L * 60L * 60L * 1000L

    private val allowedMatrix: Map<ProjectStatus, Set<ProjectStatus>> = mapOf(
        ProjectStatus.ACTIVE to setOf(
            ProjectStatus.ACTIVE,
            ProjectStatus.PAUSED,
            ProjectStatus.BLOCKED,
            ProjectStatus.REVIEW,
            ProjectStatus.COMPLETED,
            ProjectStatus.ARCHIVED
        ),
        ProjectStatus.PAUSED to setOf(
            ProjectStatus.PAUSED,
            ProjectStatus.ACTIVE,
            ProjectStatus.BLOCKED,
            ProjectStatus.REVIEW,
            ProjectStatus.ARCHIVED
        ),
        ProjectStatus.BLOCKED to setOf(
            ProjectStatus.BLOCKED,
            ProjectStatus.ACTIVE,
            ProjectStatus.PAUSED,
            ProjectStatus.REVIEW,
            ProjectStatus.ARCHIVED
        ),
        ProjectStatus.REVIEW to setOf(
            ProjectStatus.REVIEW,
            ProjectStatus.ACTIVE,
            ProjectStatus.BLOCKED,
            ProjectStatus.PAUSED,
            ProjectStatus.COMPLETED,
            ProjectStatus.ARCHIVED
        ),
        ProjectStatus.COMPLETED to setOf(
            ProjectStatus.COMPLETED,
            ProjectStatus.ACTIVE,
            ProjectStatus.REVIEW,
            ProjectStatus.ARCHIVED
        ),
        ProjectStatus.ARCHIVED to setOf(
            ProjectStatus.ARCHIVED,
            ProjectStatus.ACTIVE,
            ProjectStatus.PAUSED
        )
    )

    fun evaluateTransition(
        current: ProjectStatus,
        target: ProjectStatus,
        unresolvedBlockingCount: Int = 0,
        hasVerifiedEvidence: Boolean = true
    ): TransitionResult {
        if (current == target) return TransitionResult.Allowed(target)

        val permitted = allowedMatrix[current].orEmpty()
        if (target !in permitted) {
            return TransitionResult.Rejected(
                "Transition from ${current.label} to ${target.label} is not allowed. " +
                    "Reactivate or move to REVIEW first."
            )
        }

        if (target == ProjectStatus.COMPLETED && unresolvedBlockingCount > 0) {
            return TransitionResult.Rejected(
                "Cannot mark project COMPLETED while $unresolvedBlockingCount blocking dependency(s) remain unresolved."
            )
        }

        if (target == ProjectStatus.COMPLETED && !hasVerifiedEvidence) {
            return TransitionResult.Rejected(
                "Cannot mark project COMPLETED without at least one VERIFIED execution record or milestone."
            )
        }

        val warning = when {
            target == ProjectStatus.ACTIVE && unresolvedBlockingCount > 0 ->
                "Project still has $unresolvedBlockingCount unresolved blocking dependency(s)."
            else -> null
        }

        return TransitionResult.Allowed(target, warning)
    }

    fun allowedTargets(current: ProjectStatus): List<ProjectStatus> =
        allowedMatrix[current]?.toList().orEmpty()

    fun needsReview(project: ProjectEntity, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (project.status == ProjectStatus.ARCHIVED || project.status == ProjectStatus.COMPLETED) {
            return false
        }
        return project.status == ProjectStatus.REVIEW ||
            (nowMillis - project.lastReviewDate) >= REVIEW_STALE_DAYS_MS
    }

    fun hasVerifiedEvidenceForProject(
        project: ProjectEntity,
        workItems: List<WorkItemEntity>,
        verifiedRecordCount: Int
    ): Boolean {
        return verifiedRecordCount > 0 ||
            workItems.any { it.evidenceState == EvidenceState.VERIFIED } ||
            project.lastVerifiedMilestone.isNotBlank()
    }
}
