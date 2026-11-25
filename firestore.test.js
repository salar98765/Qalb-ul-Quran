const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_noor_123";
const BOB_UID = "bob_noor_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function buildValidUserPayload(uid, createdDate) {
  return {
    userId: uid,
    displayName: "Amina Rahman",
    email: "amina@example.com",
    photoUrl: "https://example.com/avatar.jpg",
    city: "London",
    country: "United Kingdom",
    bio: "Seeking daily peace through Quran and dhikr.",
    preferredMethod: "MWL",
    preferredReciterId: "ar.alafasy",
    dailyDhikrGoal: 100,
    totalDhikrSynced: 330,
    bookmarkedCount: 4,
    publicVisible: true,
    createdAt: createdDate,
    updatedAt: createdDate,
  };
}

function buildValidPublicPayload(uid, createdDate, publicVisible = true) {
  return {
    userId: uid,
    displayName: "Amina Rahman",
    photoUrl: "https://example.com/avatar.jpg",
    city: "London",
    country: "United Kingdom",
    bio: "Seeking daily peace through Quran and dhikr.",
    dailyDhikrGoal: 100,
    totalDhikrSynced: 330,
    publicVisible,
    createdAt: createdDate,
    updatedAt: createdDate,
  };
}

test("Unauthenticated user cannot read or write users or publicProfiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(buildValidUserPayload(ALICE_UID, now))
  );
  await assertFails(
    unauthDb.collection("publicProfiles").where("publicVisible", "==", true).get()
  );
});

test("Owner can create and partially update private UserProfile; ghost fields and cross-user access denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(buildValidUserPayload(ALICE_UID, created))
  );

  // Partial update succeeds while createdAt remains immutable
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      city: "Istanbul",
      country: "Turkey",
      totalDhikrSynced: 500,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject mutating createdAt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject shadow/ghost field on create
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      ...buildValidUserPayload(BOB_UID, created),
      isAdmin: true,
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set(
      buildValidUserPayload(BOB_UID, new Date(Date.now() + 60000))
    )
  );

  // Cross-user PII read/write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("PublicCommunityProfile enforces PII exclusion, visibility filtering, and owner-only writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Alice creates a public profile
  await assertSucceeds(
    aliceDb
      .collection("publicProfiles")
      .doc(ALICE_UID)
      .set(buildValidPublicPayload(ALICE_UID, created, true))
  );

  // Bob creates a hidden profile
  await assertSucceeds(
    bobDb
      .collection("publicProfiles")
      .doc(BOB_UID)
      .set(buildValidPublicPayload(BOB_UID, created, false))
  );

  // Reject writing PII email to publicProfiles
  await assertFails(
    bobDb.collection("publicProfiles").doc("bob_extra").set({
      ...buildValidPublicPayload("bob_extra", created, true),
      email: "bob@example.com",
    })
  );

  // Filtered community list query succeeds
  await assertSucceeds(
    bobDb.collection("publicProfiles").where("publicVisible", "==", true).get()
  );

  // Unfiltered list query fails
  await assertFails(bobDb.collection("publicProfiles").get());

  // Bob can read Alice's visible profile, Alice cannot read Bob's hidden profile
  await assertSucceeds(bobDb.collection("publicProfiles").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("publicProfiles").doc(BOB_UID).get());

  // Bob cannot update Alice's public profile
  await assertFails(
    bobDb.collection("publicProfiles").doc(ALICE_UID).update({
      bio: "Tampered",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
