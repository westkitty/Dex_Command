package com.example.data.local

import com.example.domain.engine.BackupImportEngine
import com.example.domain.model.DependencyNodeType
import com.example.domain.model.DependencyRelationType
import com.example.domain.model.EvidenceState
import com.example.domain.model.ProjectStatus

/**
 * Realistic fictional demonstration projects clearly flagged with `isDemo = true`.
 * Demonstrates mixed verification states, blockers, milestones, AI-claimed-unverified
 * guardrails, and preserved imported source documents.
 */
object DemoSeedData {

    fun buildSeed(now: Long = System.currentTimeMillis()): SeedBundle {
        val dayMs = 24L * 60L * 60L * 1000L
        val hourMs = 60L * 60L * 1000L

        val p1 = ProjectEntity(
            id = "demo-proj-prism-osc",
            name = "PRISM // Spatial OSC Engine",
            description = "Low-latency C++/Kotlin bridge translating modular synth clocks into stage DMX and shader uniforms.",
            purpose = "Provide deterministic sub-5ms audio-to-lighting synchronization across live performances without laptop cloud dependency.",
            currentObjective = "Finalize ring-buffer overflow guard and verify 48kHz jitter floor on Tab S9 Ultra.",
            repositoryUrl = "https://github.com/octocat/Hello-World",
            driveUrl = "https://drive.google.com/drive/folders/demo-prism-specs",
            documentationRefs = listOf("docs/osc-framing-v2.md", "hardware/dmx-pinout-rev3.pdf"),
            storageRefs = listOf("NAS://productions/prism-2026", "SD-CARD://rig-presets"),
            category = "Creative Production",
            tags = listOf("osc", "audio", "dmx", "live-rig"),
            protectedConstraints = listOf(
                "Never allocate heap memory inside the 48kHz audio callback path",
                "Maintain backwards compatibility with v1.4 OSC address namespace",
                "Zero internet dependency during live show execution"
            ),
            knownFailures = listOf(
                "UDP burst packets exceeding 120Hz drop frame sync on USB-C hub rev A"
            ),
            openDecisions = listOf(
                "Select dual-interface USB-C MIDI/DMX host vs dedicated Art-Net node for touring case"
            ),
            status = ProjectStatus.ACTIVE,
            priority = 1,
            lastVerifiedMilestone = "Verified 3.4ms median OSC round-trip across 50,000 packets on local bench rig.",
            lastVerifiedStateSummary = "Core framing parser passes fuzz suite (42/42 tests green); jitter floor verified at 3.4ms median.",
            isDemo = true,
            createdAt = now - 24 * dayMs,
            lastReviewDate = now - 2 * dayMs,
            updatedAt = now - 3 * hourMs
        )

        val p2 = ProjectEntity(
            id = "demo-proj-agent-harness",
            name = "FORGE // Multi-Agent Eval Harness",
            description = "Deterministic replay harness comparing Codex, Claude Code, and Antigravity patches against golden repo snapshots.",
            purpose = "Prevent unverified agent completion claims from entering main branches by requiring reproducible test artifacts.",
            currentObjective = "Unblock sandbox container runner timeout and audit claimed patch pass rates.",
            repositoryUrl = "https://github.com/octocat/Spoon-Knife",
            driveUrl = "https://drive.google.com/drive/folders/demo-forge-benchmarks",
            documentationRefs = listOf("PROJECT_BIBLE.md", "eval/rubric-v3.json"),
            storageRefs = listOf("S3-ARCHIVE://forge-golden-repos"),
            category = "AI Workflow",
            tags = listOf("agents", "evals", "ci", "verification"),
            protectedConstraints = listOf(
                "Never auto-merge or push agent-generated commits without human signature",
                "Always separate agent self-reported claim from container test exit code"
            ),
            knownFailures = listOf(
                "Git worktree cleanup leaves stale lockfile after OOM kill in runner"
            ),
            openDecisions = listOf(
                "Standardize diff artifact format across Codex and Claude Code runners"
            ),
            status = ProjectStatus.BLOCKED,
            priority = 1,
            lastVerifiedMilestone = "Golden repo checksum validator verified across 14 benchmark suites.",
            lastVerifiedStateSummary = "Checksum validator green; runner timeout fix is CLAIMED by coding agent but NOT yet verified.",
            isDemo = true,
            createdAt = now - 40 * dayMs,
            lastReviewDate = now - 9 * dayMs, // Stale review (>7 days)
            updatedAt = now - 6 * hourMs
        )

        val p3 = ProjectEntity(
            id = "demo-proj-field-deck",
            name = "TACTILE // Field Recorder Firmware",
            description = "RP2040 + OLED handheld field recorder controller with rotary encoder bank and SD metadata tagging.",
            purpose = "Build a rugged pocket capture unit for industrial Foley sampling with instant JSON cue-sheet export.",
            currentObjective = "Review power draw measurements before ordering rev-C PCB batch.",
            repositoryUrl = "https://github.com/octocat/linguist",
            driveUrl = "",
            documentationRefs = listOf("pcb/schematic-revB.pdf", "firmware/sleep-budget.md"),
            storageRefs = listOf("LOCAL://kicad/tactile-revB"),
            category = "Hardware / Device",
            tags = listOf("rp2040", "firmware", "hardware", "foley"),
            protectedConstraints = listOf(
                "Keep active draw under 85mA to guarantee 6-hour field battery life",
                "Flush WAV header atomically before deep sleep transition"
            ),
            knownFailures = listOf(
                "OLED contrast flickers briefly during SD card SPI burst write"
            ),
            openDecisions = listOf(
                "Approve rev-C 4-layer board fab quote ($148) or iterate capacitor placement first"
            ),
            status = ProjectStatus.REVIEW,
            priority = 2,
            lastVerifiedMilestone = "Rev-B prototype recorded 4 hours continuous 24-bit/48kHz audio with zero dropped samples.",
            lastVerifiedStateSummary = "Bench power draw observed at 79mA average; awaiting final review of SPI noise fix.",
            isDemo = true,
            createdAt = now - 60 * dayMs,
            lastReviewDate = now - 11 * dayMs, // Needs review
            updatedAt = now - 1 * dayMs
        )

        val p4 = ProjectEntity(
            id = "demo-proj-archive-ops",
            name = "VAULT // Quarterly Cold-Storage Ritual",
            description = "Recurring checksum verification, ZFS scrub, and encrypted off-site drive rotation for master project archives.",
            purpose = "Ensure zero silent bit-rot across 18TB of creative masters, repositories, and device firmware images.",
            currentObjective = "Complete Q4 parity check and sign off drive rotation log.",
            repositoryUrl = "",
            driveUrl = "https://drive.google.com/drive/folders/demo-vault-checklist",
            documentationRefs = listOf("ops/backup-runbook-2026.md"),
            storageRefs = listOf("ZFS-POOL://vault-alpha", "OFFSITE-HDD://vault-bravo-04"),
            category = "Recurring Ops",
            tags = listOf("backup", "zfs", "recurring", "integrity"),
            protectedConstraints = listOf(
                "Verify SHA-256 manifest before rotating off-site enclosure",
                "Never prune prior quarterly snapshot until scrub reports 0 errors"
            ),
            knownFailures = emptyList(),
            openDecisions = emptyList(),
            status = ProjectStatus.PAUSED,
            priority = 3,
            lastVerifiedMilestone = "Q3 ZFS scrub completed with 0 checksum errors and verified off-site copy.",
            lastVerifiedStateSummary = "Q3 verified clean. Q4 window opens next week.",
            isDemo = true,
            createdAt = now - 90 * dayMs,
            lastReviewDate = now - 4 * dayMs,
            updatedAt = now - 2 * dayMs
        )

        val workItems = listOf(
            WorkItemEntity(
                id = "demo-wi-101",
                projectId = p1.id,
                title = "Add ring-buffer bounds clamping for malformed OSC bundles",
                description = "Guard against oversized OSC bundle payloads without dynamic allocation in the audio thread.",
                priority = 1,
                status = ProjectStatus.ACTIVE,
                evidenceState = EvidenceState.OBSERVED,
                acceptanceCriteria = listOf(
                    "Fuzz harness passes 25,000 malformed OSC frames with zero crashes",
                    "Zero heap allocations confirmed via allocation profiler"
                ),
                evidenceRefs = listOf("bench-fuzz-run-09.log"),
                isDemo = true,
                createdAt = now - 5 * dayMs,
                updatedAt = now - 4 * hourMs
            ),
            WorkItemEntity(
                id = "demo-wi-102",
                projectId = p1.id,
                title = "Benchmark USB-C hub rev B under 120Hz DMX burst",
                description = "Measure packet loss over 30-minute continuous stress test on Galaxy Tab S9 Ultra.",
                priority = 2,
                status = ProjectStatus.ACTIVE,
                evidenceState = EvidenceState.PLANNED,
                dependencies = listOf("demo-wi-101"),
                acceptanceCriteria = listOf(
                    "Packet loss < 0.01% over 30-minute 120Hz burst",
                    "Thermal throttling remains absent"
                ),
                isDemo = true,
                createdAt = now - 4 * dayMs,
                updatedAt = now - 8 * hourMs
            ),
            WorkItemEntity(
                id = "demo-wi-201",
                projectId = p2.id,
                title = "Fix stale git worktree lockfile after OOM runner termination",
                description = "Ensure harness detects orphaned .git/worktrees lockfiles and cleans them safely before starting next eval.",
                priority = 1,
                status = ProjectStatus.BLOCKED,
                evidenceState = EvidenceState.CLAIMED, // Agent claimed fix, NOT verified yet!
                acceptanceCriteria = listOf(
                    "Simulated SIGKILL mid-eval leaves zero stale lockfiles on subsequent run",
                    "Verified locally by operator with reproducible command log"
                ),
                evidenceRefs = listOf("agent-claim-patch-19.diff"),
                isDemo = true,
                createdAt = now - 10 * dayMs,
                updatedAt = now - 6 * hourMs
            ),
            WorkItemEntity(
                id = "demo-wi-202",
                projectId = p2.id,
                title = "Run golden 14-repo regression suite after lockfile fix",
                description = "Execute full deterministic replay comparison across Codex and Claude Code outputs.",
                priority = 1,
                status = ProjectStatus.BLOCKED,
                evidenceState = EvidenceState.PLANNED,
                dependencies = listOf("demo-wi-201"),
                acceptanceCriteria = listOf(
                    "All 14 benchmark suites exit with deterministic checksum match"
                ),
                isDemo = true,
                createdAt = now - 8 * dayMs,
                updatedAt = now - 7 * hourMs
            ),
            WorkItemEntity(
                id = "demo-wi-301",
                projectId = p3.id,
                title = "Verify 100uF decoupling capacitor eliminates OLED SPI flicker",
                description = "Capture oscilloscope rail trace during sustained 48kHz WAV write bursts.",
                priority = 2,
                status = ProjectStatus.REVIEW,
                evidenceState = EvidenceState.OBSERVED,
                acceptanceCriteria = listOf(
                    "3.3V rail ripple stays under 40mVpp during SD write burst",
                    "Visual OLED inspection shows zero flicker across 20 cycles"
                ),
                evidenceRefs = listOf("scope-trace-revB2.png"),
                isDemo = true,
                createdAt = now - 12 * dayMs,
                updatedAt = now - 1 * dayMs
            ),
            WorkItemEntity(
                id = "demo-wi-401",
                projectId = p4.id,
                title = "Generate Q4 SHA-256 master archive manifest",
                description = "Run offline hash verification across master stems, repo mirrors, and firmware images.",
                priority = 3,
                status = ProjectStatus.PAUSED,
                evidenceState = EvidenceState.PLANNED,
                acceptanceCriteria = listOf(
                    "Manifest diff shows zero unexpected mutations",
                    "Off-site drive rotation logged in audit history"
                ),
                isDemo = true,
                createdAt = now - 14 * dayMs,
                updatedAt = now - 2 * dayMs
            )
        )

        val records = listOf(
            ExecutionRecordEntity(
                id = "demo-exec-01",
                projectId = p1.id,
                workItemId = "demo-wi-101",
                toolOrAgent = "Human / Bench Rig",
                actionAttempted = "Ran 50,000-packet OSC latency benchmark over local loopback",
                claim = "Median latency is below 4ms with zero dropped frames",
                evidence = "osc_bench_v2 exit=0 | p50=3.4ms | p99=4.6ms | drops=0/50000",
                timestamp = now - 3 * hourMs,
                verificationStatus = EvidenceState.VERIFIED,
                aiGeneratedStatement = false,
                isDemo = true,
                updatedAt = now - 3 * hourMs
            ),
            ExecutionRecordEntity(
                id = "demo-exec-02",
                projectId = p2.id,
                workItemId = "demo-wi-201",
                toolOrAgent = "Codex",
                actionAttempted = "Patched worktree cleanup trap in runner/cleanup.sh",
                claim = "Agent stated: 'Worktree cleanup race is completely fixed and ready to ship.'",
                evidence = "",
                timestamp = now - 6 * hourMs,
                verificationStatus = EvidenceState.CLAIMED, // Properly NOT verified!
                aiGeneratedStatement = true,
                isDemo = true,
                updatedAt = now - 6 * hourMs
            ),
            ExecutionRecordEntity(
                id = "demo-exec-03",
                projectId = p2.id,
                workItemId = "demo-wi-201",
                toolOrAgent = "CI Runner (Local)",
                actionAttempted = "Simulated SIGKILL during worktree checkout",
                claim = "Verify runner recovers cleanly on restart",
                evidence = "EXIT 128: fatal: '/tmp/forge/.git/worktrees/eval-4/index.lock': File exists.",
                timestamp = now - 18 * hourMs,
                verificationStatus = EvidenceState.FAILED,
                aiGeneratedStatement = false,
                isDemo = true,
                updatedAt = now - 18 * hourMs
            ),
            ExecutionRecordEntity(
                id = "demo-exec-04",
                projectId = p3.id,
                workItemId = "demo-wi-301",
                toolOrAgent = "Human / Oscilloscope",
                actionAttempted = "Measured 3.3V power rail ripple with 100uF tantalum bypass",
                claim = "Ripple reduced below 35mVpp during SD write burst",
                evidence = "Rigol capture ch1_vpp=31.2mV during 512KB write burst; awaiting 20-cycle thermal soak.",
                timestamp = now - 1 * dayMs,
                verificationStatus = EvidenceState.OBSERVED,
                aiGeneratedStatement = false,
                isDemo = true,
                updatedAt = now - 1 * dayMs
            ),
            ExecutionRecordEntity(
                id = "demo-exec-05",
                projectId = p4.id,
                workItemId = null,
                toolOrAgent = "Human / ZFS Scrub",
                actionAttempted = "Completed Q3 ZFS pool scrub on vault-alpha",
                claim = "Zero checksum or read errors",
                evidence = "zpool status vault-alpha: scan repaired 0B in 04:18:12 with 0 errors.",
                timestamp = now - 15 * dayMs,
                verificationStatus = EvidenceState.VERIFIED,
                aiGeneratedStatement = false,
                isDemo = true,
                updatedAt = now - 15 * dayMs
            )
        )

        val dependencies = listOf(
            DependencyLinkEntity(
                id = "demo-dep-01",
                sourceType = DependencyNodeType.WORK_ITEM,
                sourceId = "demo-wi-102",
                targetType = DependencyNodeType.WORK_ITEM,
                targetId = "demo-wi-101",
                relationType = DependencyRelationType.BLOCKING,
                notes = "Do not benchmark USB-C hub until OSC bounds clamping is verified.",
                isDemo = true,
                createdAt = now - 4 * dayMs,
                updatedAt = now - 4 * dayMs
            ),
            DependencyLinkEntity(
                id = "demo-dep-02",
                sourceType = DependencyNodeType.WORK_ITEM,
                sourceId = "demo-wi-202",
                targetType = DependencyNodeType.WORK_ITEM,
                targetId = "demo-wi-201",
                relationType = DependencyRelationType.BLOCKING,
                notes = "Regression suite cannot run reliably until stale worktree lockfile bug is verified fixed.",
                isDemo = true,
                createdAt = now - 8 * dayMs,
                updatedAt = now - 8 * dayMs
            ),
            DependencyLinkEntity(
                id = "demo-dep-03",
                sourceType = DependencyNodeType.PROJECT,
                sourceId = p1.id,
                targetType = DependencyNodeType.PROJECT,
                targetId = p3.id,
                relationType = DependencyRelationType.INFORMATIONAL,
                notes = "Shares 48kHz audio clock framing conventions with Field Recorder firmware.",
                isDemo = true,
                createdAt = now - 12 * dayMs,
                updatedAt = now - 12 * dayMs
            ),
            DependencyLinkEntity(
                id = "demo-dep-04",
                sourceType = DependencyNodeType.PROJECT,
                sourceId = p2.id,
                targetType = DependencyNodeType.PROJECT,
                targetId = p4.id,
                relationType = DependencyRelationType.INFORMATIONAL,
                notes = "Golden eval snapshots are mirrored during quarterly VAULT cold-storage ritual.",
                isDemo = true,
                createdAt = now - 20 * dayMs,
                updatedAt = now - 20 * dayMs
            )
        )

        val sampleBible = """
            # FORGE // Operational Handoff (DEMO)
            
            ## Ground Truth Baseline
            - Last verified benchmark run: 14/14 golden suites checksum-matched.
            - Known blocker: SIGKILL during container OOM leaves `.git/worktrees/*/index.lock`.
            - Agent Claim Warning: Codex submitted a trap handler patch, but local SIGKILL reproduction has NOT yet been re-run.
            
            ## Protected Rules
            - Never auto-push agent patches.
            - Keep evaluation harness 100% reproducible offline.
        """.trimIndent()

        val importedDocs = listOf(
            ImportedDocumentEntity(
                id = "demo-doc-01",
                projectId = p2.id,
                fileName = "FORGE_HANDOFF_DEMO.md",
                format = "MARKDOWN",
                rawContent = sampleBible,
                contentHash = BackupImportEngine.sha256(sampleBible),
                aiSummary = null,
                importedAt = now - 5 * dayMs,
                isDemo = true
            )
        )

        val audits = listOf(
            AuditEventEntity(
                id = "demo-audit-01",
                entityType = "SYSTEM",
                entityId = "seed",
                action = "DEMO_SEED_LOADED",
                summary = "Initialized 4 fictional demonstration projects (tagged [DEMO]). Purge anytime in Deck Tools.",
                timestamp = now - 1 * hourMs
            )
        )

        return SeedBundle(
            projects = listOf(p1, p2, p3, p4),
            workItems = workItems,
            executionRecords = records,
            dependencyLinks = dependencies,
            importedDocuments = importedDocs,
            auditEvents = audits
        )
    }

    data class SeedBundle(
        val projects: List<ProjectEntity>,
        val workItems: List<WorkItemEntity>,
        val executionRecords: List<ExecutionRecordEntity>,
        val dependencyLinks: List<DependencyLinkEntity>,
        val importedDocuments: List<ImportedDocumentEntity>,
        val auditEvents: List<AuditEventEntity>
    )
}
