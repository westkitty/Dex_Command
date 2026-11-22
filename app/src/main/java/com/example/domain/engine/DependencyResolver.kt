package com.example.domain.engine

import com.example.data.local.DependencyLinkEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.WorkItemEntity
import com.example.domain.model.DependencyNodeType
import com.example.domain.model.DependencyRelationType
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus

data class ResolvedDependencySummary(
    val blockingLinks: List<DependencyLinkEntity>,
    val informationalLinks: List<DependencyLinkEntity>,
    val unresolvedBlockingLinks: List<DependencyLinkEntity>
)

object DependencyResolver {

    fun isNodeResolved(
        nodeType: DependencyNodeType,
        nodeId: String,
        projects: Map<String, ProjectEntity>,
        workItems: Map<String, WorkItemEntity>
    ): Boolean {
        return when (nodeType) {
            DependencyNodeType.PROJECT -> {
                val p = projects[nodeId] ?: return false
                p.status == ProjectStatus.COMPLETED || p.status == ProjectStatus.ARCHIVED
            }
            DependencyNodeType.WORK_ITEM -> {
                val w = workItems[nodeId] ?: return false
                w.status == ProjectStatus.COMPLETED && w.evidenceState == EvidenceState.VERIFIED
            }
        }
    }

    /**
     * Checks if adding edge (source -> target) creates a directed cycle across BLOCKING links.
     */
    fun wouldCreateCycle(
        existingLinks: List<DependencyLinkEntity>,
        sourceType: DependencyNodeType,
        sourceId: String,
        targetType: DependencyNodeType,
        targetId: String,
        relationType: DependencyRelationType
    ): Boolean {
        val sourceKey = nodeKey(sourceType, sourceId)
        val targetKey = nodeKey(targetType, targetId)
        if (sourceKey == targetKey) return true
        if (relationType != DependencyRelationType.BLOCKING) return false

        val adjacency = mutableMapOf<String, MutableList<String>>()
        existingLinks
            .filter { it.relationType == DependencyRelationType.BLOCKING }
            .forEach { link ->
                adjacency.getOrPut(nodeKey(link.sourceType, link.sourceId)) { mutableListOf() }
                    .add(nodeKey(link.targetType, link.targetId))
            }
        adjacency.getOrPut(sourceKey) { mutableListOf() }.add(targetKey)

        // DFS from targetKey to see if we can reach sourceKey
        val visited = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        stack.addLast(targetKey)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current == sourceKey) return true
            if (visited.add(current)) {
                adjacency[current]?.forEach { next -> stack.addLast(next) }
            }
        }
        return false
    }

    fun resolveForNode(
        nodeType: DependencyNodeType,
        nodeId: String,
        allLinks: List<DependencyLinkEntity>,
        projects: List<ProjectEntity>,
        workItems: List<WorkItemEntity>
    ): ResolvedDependencySummary {
        val projectMap = projects.associateBy { it.id }
        val workItemMap = workItems.associateBy { it.id }
        val outgoing = allLinks.filter { it.sourceType == nodeType && it.sourceId == nodeId }
        val blocking = outgoing.filter { it.relationType == DependencyRelationType.BLOCKING }
        val informational = outgoing.filter { it.relationType == DependencyRelationType.INFORMATIONAL }
        val unresolvedBlocking = blocking.filter { link ->
            !isNodeResolved(link.targetType, link.targetId, projectMap, workItemMap)
        }
        return ResolvedDependencySummary(
            blockingLinks = blocking,
            informationalLinks = informational,
            unresolvedBlockingLinks = unresolvedBlocking
        )
    }

    fun resolveUnresolvedBlockersForProject(
        projectId: String,
        allLinks: List<DependencyLinkEntity>,
        projects: List<ProjectEntity>,
        workItems: List<WorkItemEntity>
    ): List<DependencyLinkEntity> {
        val projectMap = projects.associateBy { it.id }
        val workItemMap = workItems.associateBy { it.id }
        val projectWorkItemIds = workItems.filter { it.projectId == projectId }.map { it.id }.toSet()

        return allLinks.filter { link ->
            link.relationType == DependencyRelationType.BLOCKING &&
                (
                    (link.sourceType == DependencyNodeType.PROJECT && link.sourceId == projectId) ||
                        (link.sourceType == DependencyNodeType.WORK_ITEM && link.sourceId in projectWorkItemIds)
                    ) &&
                !isNodeResolved(link.targetType, link.targetId, projectMap, workItemMap)
        }
    }

    fun findNextActionableTasks(
        projects: List<ProjectEntity>,
        workItems: List<WorkItemEntity>,
        allLinks: List<DependencyLinkEntity>
    ): List<WorkItemEntity> {
        val projectMap = projects.associateBy { it.id }
        val workItemMap = workItems.associateBy { it.id }
        return workItems
            .filter { item ->
                val parent = projectMap[item.projectId]
                parent != null &&
                    parent.status != ProjectStatus.ARCHIVED &&
                    parent.status != ProjectStatus.COMPLETED &&
                    item.status != ProjectStatus.COMPLETED &&
                    item.status != ProjectStatus.ARCHIVED &&
                    item.status != ProjectStatus.BLOCKED
            }
            .filter { item ->
                val unresolved = resolveForNode(
                    DependencyNodeType.WORK_ITEM,
                    item.id,
                    allLinks,
                    projects,
                    workItems
                ).unresolvedBlockingLinks
                val inlineUnresolved = item.dependencies.any { depId ->
                    val targetWi = workItemMap[depId]
                    val targetProj = projectMap[depId]
                    when {
                        targetWi != null -> !(targetWi.status == ProjectStatus.COMPLETED && targetWi.evidenceState == EvidenceState.VERIFIED)
                        targetProj != null -> targetProj.status != ProjectStatus.COMPLETED
                        else -> false
                    }
                }
                unresolved.isEmpty() && !inlineUnresolved
            }
            .sortedWith(
                compareBy<WorkItemEntity> { it.priority }
                    .thenBy { projectMap[it.projectId]?.priority ?: 4 }
                    .thenByDescending { it.updatedAt }
            )
    }

    fun nodeKey(type: DependencyNodeType, id: String): String = "${type.name}:$id"
}
