package com.example.domain.model

enum class ProjectStatus(val label: String) {
    ACTIVE("ACTIVE"),
    PAUSED("PAUSED"),
    BLOCKED("BLOCKED"),
    REVIEW("REVIEW"),
    COMPLETED("COMPLETED"),
    ARCHIVED("ARCHIVED");

    companion object {
        fun fromNameSafe(raw: String?, fallback: ProjectStatus = ACTIVE): ProjectStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
    }
}

enum class EvidenceState(val label: String) {
    PLANNED("PLANNED"),
    CLAIMED("CLAIMED"),
    OBSERVED("OBSERVED"),
    VERIFIED("VERIFIED"),
    FAILED("FAILED"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromNameSafe(raw: String?, fallback: EvidenceState = UNKNOWN): EvidenceState =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
    }
}

enum class DependencyNodeType(val label: String) {
    PROJECT("PROJECT"),
    WORK_ITEM("WORK_ITEM");

    companion object {
        fun fromNameSafe(raw: String?, fallback: DependencyNodeType = WORK_ITEM): DependencyNodeType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
    }
}

enum class DependencyRelationType(val label: String) {
    BLOCKING("BLOCKING"),
    INFORMATIONAL("INFORMATIONAL");

    companion object {
        fun fromNameSafe(raw: String?, fallback: DependencyRelationType = INFORMATIONAL): DependencyRelationType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
    }
}

enum class ProjectSortOption(val label: String) {
    PRIORITY_ASC("Priority (Critical First)"),
    LAST_REVIEW_ASC("Review Needed (Oldest First)"),
    UPDATED_DESC("Recently Updated"),
    NAME_ASC("Name (A–Z)")
}

enum class AgentTarget(val displayName: String, val roleNote: String) {
    CODEX("Codex", "Autonomous terminal & repository coding agent"),
    CLAUDE_CODE("Claude Code", "CLI repository reasoning & multi-file refactoring agent"),
    ANTIGRAVITY("Antigravity", "Agentic workspace engineering environment"),
    GENERIC("Coding Agent", "General-purpose technical coding assistant")
}

object ProjectCategories {
    val defaults = listOf(
        "Repository",
        "AI Workflow",
        "Creative Production",
        "Hardware / Device",
        "Recurring Ops"
    )
}
