const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_cmd_01";
const BOB_UID = "bob_cmd_02";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    name: "Kinetic Audio Rig",
    description: "Spatial DMX + audio synthesis controller",
    purpose: "Drive live stage fixtures from modular MIDI clocks",
    currentObjective: "Stabilize OSC packet parser on Tab S9",
    repositoryUrl: "https://github.com/example/kinetic-rig",
    driveUrl: "https://drive.google.com/drive/folders/demo123",
    documentationRefs: ["docs/osc-spec.md"],
    storageRefs: ["NAS://stage-rigs/v2"],
    category: "Creative Production",
    tags: ["audio", "osc", "live"],
    protectedConstraints: ["Never break 48kHz buffer alignment"],
    knownFailures: ["UDP burst drop above 120fps"],
    openDecisions: ["Choose USB-C MIDI host adapter"],
    status: "ACTIVE",
    priority: 1,
    lastVerifiedMilestone: "OSC ping latency verified under 4ms",
    lastVerifiedStateSummary: "Unit tests green on commit a12b34c",
    demoRecord: false,
    clientCreatedAt: Date.now() - 50000,
    lastReviewDate: Date.now() - 10000,
    clientUpdatedAt: Date.now() - 2000,
    createdAt: now,
    updatedAt: now,
  };
}

function validWorkItemPayload(uid, projectId, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    projectId,
    title: "Implement ring buffer bounds check",
    description: "Prevent packet overrun on malformed frames",
    priority: 1,
    status: "ACTIVE",
    evidenceState: "OBSERVED",
    dependencies: [],
    acceptanceCriteria: ["Zero crashes across 10k fuzz frames"],
    evidenceRefs: ["test-report-44.txt"],
    demoRecord: false,
    clientCreatedAt: Date.now() - 30000,
    clientUpdatedAt: Date.now() - 2000,
    createdAt: now,
    updatedAt: now,
  };
}

function validExecutionRecordPayload(uid, projectId, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    projectId,
    workItemId: "wi_01",
    toolOrAgent: "Claude Code",
    actionAttempted: "Refactored OSC parser bounds checking",
    claim: "Parser handles malformed payloads cleanly",
    evidence: "Executed ./gradlew testDebugUnitTest — 18/18 passed",
    verificationStatus: "VERIFIED",
    aiGeneratedStatement: true,
    demoRecord: false,
    recordedAt: Date.now() - 4000,
    clientUpdatedAt: Date.now() - 2000,
    createdAt: now,
    updatedAt: now,
  };
}

function validDependencyPayload(uid, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    sourceType: "WORK_ITEM",
    sourceId: "wi_02",
    targetType: "WORK_ITEM",
    targetId: "wi_01",
    relationType: "BLOCKING",
    notes: "Cannot benchmark until bounds check lands",
    demoRecord: false,
    clientCreatedAt: Date.now() - 20000,
    clientUpdatedAt: Date.now() - 2000,
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any DEX//COMMAND collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").set(validProjectPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("workItems").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("executionRecords").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("dependencyLinks").get());
});

test("Owner can create, list, get, update, and delete valid CloudProject", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_kinetic");

  await assertSucceeds(ref.set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertSucceeds(ref.get());

  await assertSucceeds(
    ref.update({
      status: "REVIEW",
      clientUpdatedAt: Date.now() - 1000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    ref.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    ref.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(ref.delete());
});

test("Cross-user access to projects, workItems, executionRecords, dependencyLinks is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).collection("projects").doc("bob_proj").set(validProjectPayload(BOB_UID));
    await db.collection("users").doc(BOB_UID).collection("workItems").doc("bob_wi").set(validWorkItemPayload(BOB_UID, "bob_proj"));
    await db.collection("users").doc(BOB_UID).collection("executionRecords").doc("bob_exec").set(validExecutionRecordPayload(BOB_UID, "bob_proj"));
    await db.collection("users").doc(BOB_UID).collection("dependencyLinks").doc("bob_dep").set(validDependencyPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("projects").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("projects").doc("bob_proj").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("projects").doc("bob_proj").update({ name: "Hijacked", updatedAt: new Date(Date.now() - 1000) }));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("workItems").doc("bob_wi").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("executionRecords").doc("bob_exec").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("dependencyLinks").doc("bob_dep").get());
});

test("WorkItems, ExecutionRecords, and DependencyLinks enforce schema, evidence rules, and timestamp bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Valid work item
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workItems").doc("wi_01").set(
      validWorkItemPayload(ALICE_UID, "proj_kinetic")
    )
  );

  // Reject extra @DocumentId 'id' field on create
  const badWorkItem = { ...validWorkItemPayload(ALICE_UID, "proj_kinetic"), id: "wi_02" };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("workItems").doc("wi_02").set(badWorkItem)
  );

  // Valid execution record (AI statement WITH non-empty evidence)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("executionRecords").doc("exec_01").set(
      validExecutionRecordPayload(ALICE_UID, "proj_kinetic")
    )
  );

  // Reject AI-generated statement marked VERIFIED with empty evidence
  const unverifiedAi = {
    ...validExecutionRecordPayload(ALICE_UID, "proj_kinetic"),
    evidence: "",
    verificationStatus: "VERIFIED",
    aiGeneratedStatement: true,
  };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("executionRecords").doc("exec_bad_ai").set(unverifiedAi)
  );

  // Reject future tampered timestamp
  const futurePayload = validDependencyPayload(ALICE_UID, new Date(Date.now() + 60000));
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("dependencyLinks").doc("dep_future").set(futurePayload)
  );

  // Valid dependency link
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("dependencyLinks").doc("dep_01").set(
      validDependencyPayload(ALICE_UID)
    )
  );
});
