package com.example

import com.example.data.local.DependencyLinkEntity
import com.example.data.local.ExecutionRecordEntity
import com.example.data.local.ImportedDocumentEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.WorkItemEntity
import com.example.domain.engine.BackupImportEngine
import com.example.domain.engine.BackupParseResult
import com.example.domain.engine.ContinuityRecoveryGenerator
import com.example.domain.engine.DependencyResolver
import com.example.domain.engine.DexBackupPayload
import com.example.domain.engine.EvidenceClassifier
import com.example.domain.engine.ProjectStateMachine
import com.example.domain.engine.PromptGenerator
import com.example.domain.engine.TransitionResult
import com.example.domain.model.AgentTarget
import com.example.domain.model.DependencyNodeType
import com.example.domain.model.DependencyRelationType
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DexDomainUnitTests {

    // 1. Project State Transitions
    @Test
    fun `project state transitions reject completion when blocking dependencies remain`() {
        val blocked = ProjectStateMachine.evaluateTransition(
            current = ProjectStatus.ACTIVE,
            target = ProjectStatus.COMPLETED,
            unresolvedBlockingCount = 2,
            hasVerifiedEvidence = true
        )
        assertTrue(blocked is TransitionResult.Rejected)

        val unverified = ProjectStateMachine.evaluateTransition(
            current = ProjectStatus.ACTIVE,
            target = ProjectStatus.COMPLETED,
            unresolvedBlockingCount = 0,
            hasVerifiedEvidence = false
        )
        assertTrue(unverified is TransitionResult.Rejected)

        val allowed = ProjectStateMachine.evaluateTransition(
            current = ProjectStatus.REVIEW,
            target = ProjectStatus.COMPLETED,
            unresolvedBlockingCount = 0,
            hasVerifiedEvidence = true
        )
        assertTrue(allowed is TransitionResult.Allowed)
        assertEquals(ProjectStatus.COMPLETED, (allowed as TransitionResult.Allowed).nextStatus)

        val invalidArchiveToComplete = ProjectStateMachine.evaluateTransition(
            current = ProjectStatus.ARCHIVED,
            target = ProjectStatus.COMPLETED,
            unresolvedBlockingCount = 0,
            hasVerifiedEvidence = true
        )
        assertTrue(invalidArchiveToComplete is TransitionResult.Rejected)
    }

    // 2. Evidence Status Classification
    @Test
    fun `evidence classifier prevents AI-generated statement alone from being marked VERIFIED`() {
        val aiOnly = EvidenceClassifier.classify(
            requestedState = EvidenceState.VERIFIED,
            claim = "Agent claims all tests passed",
            evidence = "Agent text output only",
            aiGeneratedStatement = true,
            humanConfirmedEvidence = false
        )
        assertTrue(aiOnly.wasDowngraded)
        assertEquals(EvidenceState.CLAIMED, aiOnly.effectiveState)

        val blankEvidence = EvidenceClassifier.classify(
            requestedState = EvidenceState.VERIFIED,
            claim = "Done",
            evidence = "   ",
            aiGeneratedStatement = false,
            humanConfirmedEvidence = true
        )
        assertTrue(blankEvidence.wasDowngraded)
        assertEquals(EvidenceState.CLAIMED, blankEvidence.effectiveState)

        val verifiedWithHumanEvidence = EvidenceClassifier.classify(
            requestedState = EvidenceState.VERIFIED,
            claim = "Bench test green",
            evidence = "exit=0, 42/42 unit tests passed",
            aiGeneratedStatement = true,
            humanConfirmedEvidence = true
        )
        assertFalse(verifiedWithHumanEvidence.wasDowngraded)
        assertEquals(EvidenceState.VERIFIED, verifiedWithHumanEvidence.effectiveState)
    }

    @Test
    fun `evidence auditor flags inconsistent completed items without verified evidence`() {
        val proj = ProjectEntity(
            id = "p1",
            name = "Test Proj",
            status = ProjectStatus.COMPLETED
        )
        val wi = WorkItemEntity(
            id = "w1",
            projectId = "p1",
            title = "Task A",
            status = ProjectStatus.COMPLETED,
            evidenceState = EvidenceState.CLAIMED
        )
        val findings = EvidenceClassifier.auditInconsistencies(listOf(proj), listOf(wi), emptyList())
        assertTrue(findings.any { it.entityType == "PROJECT" && it.entityId == "p1" })
        assertTrue(findings.any { it.entityType == "WORK_ITEM" && it.entityId == "w1" })
    }

    // 3. Dependency Resolution & Cycle Detection
    @Test
    fun `dependency resolver distinguishes blocking from informational links and detects cycles`() {
        val p1 = ProjectEntity(id = "p1", name = "P1", status = ProjectStatus.ACTIVE)
        val w1 = WorkItemEntity(id = "w1", projectId = "p1", title = "Prereq", status = ProjectStatus.ACTIVE, evidenceState = EvidenceState.PLANNED)
        val w2 = WorkItemEntity(id = "w2", projectId = "p1", title = "Dependent", status = ProjectStatus.ACTIVE, evidenceState = EvidenceState.PLANNED)
        val w3 = WorkItemEntity(id = "w3", projectId = "p1", title = "Reference Only", status = ProjectStatus.ACTIVE, evidenceState = EvidenceState.PLANNED)

        val blockingLink = DependencyLinkEntity(
            id = "d1",
            sourceType = DependencyNodeType.WORK_ITEM,
            sourceId = "w2",
            targetType = DependencyNodeType.WORK_ITEM,
            targetId = "w1",
            relationType = DependencyRelationType.BLOCKING
        )
        val infoLink = DependencyLinkEntity(
            id = "d2",
            sourceType = DependencyNodeType.WORK_ITEM,
            sourceId = "w3",
            targetType = DependencyNodeType.WORK_ITEM,
            targetId = "w1",
            relationType = DependencyRelationType.INFORMATIONAL
        )
        val links = listOf(blockingLink, infoLink)

        // Adding w1 -> w2 as BLOCKING creates a cycle
        assertTrue(
            DependencyResolver.wouldCreateCycle(
                existingLinks = links,
                sourceType = DependencyNodeType.WORK_ITEM,
                sourceId = "w1",
                targetType = DependencyNodeType.WORK_ITEM,
                targetId = "w2",
                relationType = DependencyRelationType.BLOCKING
            )
        )
        // Informational reference does NOT block cycle check
        assertFalse(
            DependencyResolver.wouldCreateCycle(
                existingLinks = links,
                sourceType = DependencyNodeType.WORK_ITEM,
                sourceId = "w1",
                targetType = DependencyNodeType.WORK_ITEM,
                targetId = "w2",
                relationType = DependencyRelationType.INFORMATIONAL
            )
        )

        // Next actionable tasks must include w1 and w3 (informational link doesn't block), but exclude w2
        val actionableIds = DependencyResolver.findNextActionableTasks(
            projects = listOf(p1),
            workItems = listOf(w1, w2, w3),
            allLinks = links
        ).map { it.id }

        assertTrue("w1" in actionableIds)
        assertTrue("w3" in actionableIds)
        assertFalse("w2" in actionableIds)
    }

    // 4. Import / Export Integrity
    @Test
    fun `backup export and import round-trip preserves integrity and rejects tampered payloads`() {
        val project = ProjectEntity(
            id = "p-real",
            name = "Signal Synth",
            purpose = "Audio DSP engine",
            protectedConstraints = listOf("Zero heap alloc in callback"),
            status = ProjectStatus.ACTIVE,
            priority = 1,
            isDemo = false
        )
        val task = WorkItemEntity(
            id = "w-real",
            projectId = "p-real",
            title = "Clamp gain stage",
            status = ProjectStatus.ACTIVE,
            evidenceState = EvidenceState.OBSERVED
        )
        val rawDoc = "# Bible\nOriginal untouched text."
        val importedDoc = BackupImportEngine.parseDocumentImport("BIBLE.md", rawDoc, targetProjectId = "p-real").document
        assertEquals(rawDoc, importedDoc.rawContent)
        assertEquals("MARKDOWN", importedDoc.format)

        val payload = DexBackupPayload(
            schemaVersion = 1,
            exportedAt = 1_700_000_000_000L,
            projects = listOf(project),
            workItems = listOf(task),
            executionRecords = emptyList(),
            dependencyLinks = emptyList(),
            importedDocuments = listOf(importedDoc),
            auditEvents = emptyList()
        )
        val exportedJson = BackupImportEngine.exportToJson(payload)

        val parsed = BackupImportEngine.parseBackupJson(exportedJson)
        assertTrue(parsed is BackupParseResult.Success)
        val restored = (parsed as BackupParseResult.Success).payload
        assertEquals(1, restored.projects.size)
        assertEquals("Signal Synth", restored.projects.first().name)
        assertEquals(rawDoc, restored.importedDocuments.first().rawContent)

        // Tamper with JSON content -> SHA-256 mismatch must fail cleanly
        val tampered = exportedJson.replace("Signal Synth", "Tampered Synth")
        val failed = BackupImportEngine.parseBackupJson(tampered)
        assertTrue(failed is BackupParseResult.Failure)
    }

    // 5. Continuity Package & Prompt Generation
    @Test
    fun `continuity recovery packet and agent prompt contain all required authoritative sections`() {
        val project = ProjectEntity(
            id = "p-synth",
            name = "PRISM Rig",
            purpose = "Deterministic live lighting bridge",
            currentObjective = "Stabilize ring buffer",
            repositoryUrl = "https://github.com/example/prism",
            protectedConstraints = listOf("No heap alloc in audio callback"),
            knownFailures = listOf("UDP drop above 120Hz"),
            openDecisions = listOf("Pick USB-C host chip"),
            status = ProjectStatus.ACTIVE,
            priority = 1,
            lastVerifiedMilestone = "3.4ms OSC median verified",
            lastVerifiedStateSummary = "42/42 fuzz tests green"
        )
        val workItem = WorkItemEntity(
            id = "wi-1",
            projectId = "p-synth",
            title = "Add bounds clamp",
            description = "Clamp malformed OSC packets",
            priority = 1,
            status = ProjectStatus.ACTIVE,
            evidenceState = EvidenceState.OBSERVED,
            acceptanceCriteria = listOf("25k malformed frames pass with 0 crashes")
        )
        val verifiedRecord = ExecutionRecordEntity(
            id = "rec-1",
            projectId = "p-synth",
            workItemId = "wi-1",
            toolOrAgent = "Human Bench",
            actionAttempted = "Ran OSC benchmark",
            claim = "Sub-4ms latency",
            evidence = "p50=3.4ms exit=0",
            verificationStatus = EvidenceState.VERIFIED,
            aiGeneratedStatement = false
        )
        val unverifiedAi = ExecutionRecordEntity(
            id = "rec-2",
            projectId = "p-synth",
            workItemId = "wi-1",
            toolOrAgent = "Codex",
            actionAttempted = "Drafted clamp patch",
            claim = "Agent claimed fixed",
            evidence = "",
            verificationStatus = EvidenceState.CLAIMED,
            aiGeneratedStatement = true
        )

        val packet = ContinuityRecoveryGenerator.generateContinuationPacket(
            project = project,
            workItems = listOf(workItem),
            executionRecords = listOf(verifiedRecord, unverifiedAi),
            dependencyLinks = emptyList(),
            importedDocuments = listOf(
                ImportedDocumentEntity("d1", "p-synth", "HANDOFF.md", "MARKDOWN", "# Notes", "abc123")
            ),
            allProjects = listOf(project),
            allWorkItems = listOf(workItem)
        )

        assertTrue(packet.contains("## 1. Project Purpose"))
        assertTrue(packet.contains("## 2. Verified Current State"))
        assertTrue(packet.contains("Unverified Claims (Do NOT Treat as Verified State)"))
        assertTrue(packet.contains("## 3. Important Constraints (Protected Behavior)"))
        assertTrue(packet.contains("## 4. Known Unresolved Issues & Open Decisions"))
        assertTrue(packet.contains("## 5. Relevant Source References"))
        assertTrue(packet.contains("## 6. Last Successful Milestone"))
        assertTrue(packet.contains("## 7. Next Bounded Task"))
        assertTrue(packet.contains("## 8. Acceptance Criteria"))

        val prompt = PromptGenerator.generateTaskPrompt(
            agentTarget = AgentTarget.CLAUDE_CODE,
            project = project,
            workItem = workItem,
            executionRecords = listOf(verifiedRecord, unverifiedAi),
            dependencyLinks = emptyList(),
            allProjects = listOf(project),
            allWorkItems = listOf(workItem)
        )

        assertTrue(prompt.contains("## 1. Project Identity"))
        assertTrue(prompt.contains("## 2. Target and Scope"))
        assertTrue(prompt.contains("## 3. Known Facts"))
        assertTrue(prompt.contains("## 4. Required Action"))
        assertTrue(prompt.contains("## 5. Protected Behavior"))
        assertTrue(prompt.contains("## 6. Validation"))
        assertTrue(prompt.contains("## 7. Stopping Conditions"))
        assertTrue(prompt.contains("## 8. Reporting Requirements"))
    }
}
