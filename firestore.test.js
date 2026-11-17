const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 5000);
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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("userDirectory").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("chats").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("statuses").where("isActive", "==", true).get());
  await assertFails(unauthDb.collection("calls").where("participantIds", "array-contains", ALICE_UID).get());
});

test("Private user profile: owner can CRUD, other user cannot read PII", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      language: "bn",
      isOnline: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot read Alice's private profile (PII isolation)
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Ghost field injection fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );
});

test("User directory: discoverable users can be queried without PII", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("userDirectory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "আলিফ",
      searchName: "আলিফ",
      discoverable: true,
      isOnline: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("userDirectory").where("discoverable", "==", true).get()
  );

  // Bob cannot modify Alice's directory doc
  await assertFails(
    bobDb.collection("userDirectory").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Chats and Messages: participant isolation, reactions, and parent gate", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").set({
      createdBy: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      adminIds: [ALICE_UID],
      isGroup: false,
      lastMessage: "হ্যালো!",
      lastMessageType: "text",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Charlie (non-participant) cannot read chat, list unfiltered messages, or query Alice's messages
  await assertFails(charlieDb.collection("chats").doc("chat_1").get());
  await assertFails(charlieDb.collection("chats").doc("chat_1").collection("messages").get());
  await assertFails(
    charlieDb.collection("chats").doc("chat_1").collection("messages")
      .where("participantIds", "array-contains", ALICE_UID).get()
  );

  // Alice creates a message
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").set({
      chatId: "chat_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      participantIds: [ALICE_UID, BOB_UID],
      type: "text",
      text: "সালাম!",
      isDeleted: false,
      readBy: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob can query messages and add reaction
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_1").collection("messages")
      .where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").update({
      reactions: { [BOB_UID]: "❤️" },
      readBy: [ALICE_UID, BOB_UID],
      updatedAt: pastTimestamp(),
    })
  );

  // Bob cannot soft-delete Alice's message
  await assertFails(
    bobDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").update({
      isDeleted: true,
      text: "",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Statuses: create, list active, mark viewed, reject spoofed delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("statuses").doc("st_1").set({
      userId: ALICE_UID,
      userName: "Alice",
      type: "text",
      text: "শুভ সকাল!",
      backgroundHex: "#0B7A5C",
      viewerIds: [],
      isActive: true,
      expiresAtEpochMs: Date.now() + 86400000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("statuses").where("isActive", "==", true).get());
  await assertSucceeds(
    bobDb.collection("statuses").doc("st_1").update({
      viewerIds: [BOB_UID],
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(bobDb.collection("statuses").doc("st_1").delete());
});

test("Calls & ICE candidates: ringing -> accepted -> ended, terminal state locked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      calleeName: "Bob",
      participantIds: [ALICE_UID, BOB_UID],
      callType: "video",
      status: "ringing",
      offerSdp: "v=0...",
      durationSeconds: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice adds ICE candidate
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").collection("callerCandidates").doc("cand_1").set({
      senderId: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      sdpMid: "0",
      sdpMLineIndex: 0,
      candidate: "candidate:1 1 UDP 2122252543 192.168.1.2 5000 typ host",
      createdAt: ts,
    })
  );

  // Charlie cannot read call, direct candidate, or list candidates
  await assertFails(charlieDb.collection("calls").doc("call_1").get());
  await assertFails(
    charlieDb.collection("calls").doc("call_1").collection("callerCandidates").doc("cand_1").get()
  );
  await assertFails(
    charlieDb.collection("calls").doc("call_1").collection("callerCandidates")
      .where("participantIds", "array-contains", ALICE_UID).get()
  );

  // Bob accepts call
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "accepted",
      answerSdp: "v=0...",
      updatedAt: pastTimestamp(),
    })
  );

  // Alice ends call
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ended",
      durationSeconds: 45,
      updatedAt: pastTimestamp(),
    })
  );

  // Terminal state lock: cannot update after ended
  await assertFails(
    bobDb.collection("calls").doc("call_1").update({
      status: "accepted",
      updatedAt: pastTimestamp(),
    })
  );
});
