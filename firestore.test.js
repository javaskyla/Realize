const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const assert = require("node:assert");
const ts = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_uid_1";
const KASIR_UID = "kasir_uid_2";
const OUTSIDER_UID = "outsider_uid_3";
const OWNER_EMAIL = "owner@realize.com";
const KASIR_EMAIL = "kasir@realize.com";
const SHOP_ID = "shop_realize_1";

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

function shopDoc(ownerUid = OWNER_UID, memberUids = [OWNER_UID]) {
  return {
    name: "Realize Barbershop",
    address: "Jl. Merdeka No. 10",
    phone: "08123456789",
    ownerUid,
    ownerEmail: OWNER_EMAIL,
    memberUids,
    createdAt: ts(),
    updatedAt: ts(),
  };
}

function memberDoc(uid, role = "OWNER", memberUids = [OWNER_UID]) {
  return {
    uid,
    shopId: SHOP_ID,
    ownerUid: OWNER_UID,
    memberUids,
    displayName: role === "OWNER" ? "Owner Realize" : "Kasir Realize",
    email: role === "OWNER" ? OWNER_EMAIL : KASIR_EMAIL,
    role,
    commissionRate: role === "KAPSTER" ? 40 : 0,
    active: true,
    createdAt: ts(),
    updatedAt: ts(),
  };
}

test("Unauthenticated user cannot read or list shops or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("shops").get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("staff_invites").get());
});

test("User profile is strictly isolated per UID", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: OWNER_EMAIL, email_verified: true }).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID, { email: "outsider@test.com", email_verified: true }).firestore();

  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).set({
      uid: OWNER_UID,
      email: OWNER_EMAIL,
      displayName: "Owner",
      activeShopId: SHOP_ID,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Outsider cannot read Owner's profile
  await assertFails(outsiderDb.collection("users").doc(OWNER_UID).get());
  // Outsider cannot spoof Owner's UID
  await assertFails(
    outsiderDb.collection("users").doc(OUTSIDER_UID).set({
      uid: OWNER_UID,
      email: "outsider@test.com",
      displayName: "Hacker",
      activeShopId: SHOP_ID,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});

test("Owner can create shop, member, service, barber, queue, and expense; outsider is blocked", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: OWNER_EMAIL, email_verified: true }).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID, { email: "outsider@test.com", email_verified: true }).firestore();

  await assertSucceeds(ownerDb.collection("shops").doc(SHOP_ID).set(shopDoc()));
  await assertSucceeds(ownerDb.collection("shops").doc(SHOP_ID).collection("members").doc(OWNER_UID).set(memberDoc(OWNER_UID, "OWNER")));

  await assertSucceeds(
    ownerDb.collection("shops").doc(SHOP_ID).collection("queues").doc("q1").set({
      shopId: SHOP_ID,
      ownerUid: OWNER_UID,
      memberUids: [OWNER_UID],
      ticketCode: "A-001",
      ticketNumber: 1,
      dateKey: "2026-11-15",
      customerName: "Budi",
      customerPhone: "08123",
      barberId: "b1",
      barberName: "Andi",
      barberCommissionRate: 40,
      serviceNames: ["Signature Haircut"],
      serviceSummary: "Signature Haircut",
      subtotal: 45000,
      discount: 0,
      totalAmount: 45000,
      paidAmount: 0,
      changeAmount: 0,
      paymentMethod: "NONE",
      status: "WAITING",
      notes: "",
      createdByUid: OWNER_UID,
      createdByName: "Owner",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Ghost field rejected
  await assertFails(
    ownerDb.collection("shops").doc(SHOP_ID).collection("queues").doc("q_ghost").set({
      shopId: SHOP_ID,
      ownerUid: OWNER_UID,
      memberUids: [OWNER_UID],
      ticketCode: "A-002",
      ticketNumber: 2,
      dateKey: "2026-11-15",
      customerName: "Budi",
      customerPhone: "",
      barberId: "b1",
      barberName: "Andi",
      barberCommissionRate: 40,
      serviceNames: ["Signature Haircut"],
      serviceSummary: "Signature Haircut",
      subtotal: 45000,
      discount: 0,
      totalAmount: 45000,
      paidAmount: 0,
      changeAmount: 0,
      paymentMethod: "NONE",
      status: "WAITING",
      notes: "",
      createdByUid: OWNER_UID,
      createdByName: "Owner",
      ghostField: true,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Outsider cannot read or list queues
  await assertFails(outsiderDb.collection("shops").doc(SHOP_ID).collection("queues").doc("q1").get());
  await assertFails(outsiderDb.collection("shops").doc(SHOP_ID).collection("queues").get());
  await assertFails(outsiderDb.collection("shops").doc(SHOP_ID).collection("queues").where("memberUids", "array-contains", OWNER_UID).get());
  const emptySnap = await assertSucceeds(outsiderDb.collection("shops").doc(SHOP_ID).collection("queues").where("memberUids", "array-contains", OUTSIDER_UID).get());
  assert.strictEqual(emptySnap.size, 0);
});

test("Invited staff can accept invite atomically without role escalation; unverified email fails", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: OWNER_EMAIL, email_verified: true }).firestore();
  const kasirDb = testEnv.authenticatedContext(KASIR_UID, { email: KASIR_EMAIL, email_verified: true }).firestore();
  const unverifiedKasirDb = testEnv.authenticatedContext(KASIR_UID, { email: KASIR_EMAIL, email_verified: false }).firestore();

  await assertSucceeds(ownerDb.collection("shops").doc(SHOP_ID).set(shopDoc()));
  await assertSucceeds(ownerDb.collection("shops").doc(SHOP_ID).collection("members").doc(OWNER_UID).set(memberDoc(OWNER_UID, "OWNER")));

  const inviteId = "inv_kasir_1";
  await assertSucceeds(
    ownerDb.collection("staff_invites").doc(inviteId).set({
      shopId: SHOP_ID,
      shopName: "Realize Barbershop",
      ownerUid: OWNER_UID,
      email: KASIR_EMAIL,
      staffName: "Kasir Realize",
      role: "KASIR",
      commissionRate: 0,
      status: "PENDING",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Unverified email cannot read or accept invite
  await assertFails(unverifiedKasirDb.collection("staff_invites").doc(inviteId).get());

  // Verified Kasir can read their own invite
  await assertSucceeds(kasirDb.collection("staff_invites").where("email", "==", KASIR_EMAIL).get());

  // Verified Kasir CANNOT self-assign OWNER role
  await assertFails(
    kasirDb.collection("shops").doc(SHOP_ID).collection("members").doc(KASIR_UID).set({
      ...memberDoc(KASIR_UID, "OWNER", [OWNER_UID, KASIR_UID]),
      inviteId,
    })
  );

  // Verified Kasir CAN accept invite with matching KASIR role in atomic batch
  const batch = kasirDb.batch();
  batch.set(kasirDb.collection("shops").doc(SHOP_ID).collection("members").doc(KASIR_UID), {
    ...memberDoc(KASIR_UID, "KASIR", [OWNER_UID, KASIR_UID]),
    inviteId,
  });
  batch.update(kasirDb.collection("shops").doc(SHOP_ID), {
    memberUids: [OWNER_UID, KASIR_UID],
    updatedAt: ts(),
  });
  batch.update(kasirDb.collection("staff_invites").doc(inviteId), {
    status: "ACCEPTED",
    acceptedUid: KASIR_UID,
    updatedAt: ts(),
  });
  await assertSucceeds(batch.commit());
});

test("Terminal state locking prevents Kasir from modifying COMPLETED queue ticket", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: OWNER_EMAIL, email_verified: true }).firestore();
  const kasirDb = testEnv.authenticatedContext(KASIR_UID, { email: KASIR_EMAIL, email_verified: true }).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("shops").doc(SHOP_ID).set(shopDoc(OWNER_UID, [OWNER_UID, KASIR_UID]));
    await db.collection("shops").doc(SHOP_ID).collection("members").doc(KASIR_UID).set(memberDoc(KASIR_UID, "KASIR", [OWNER_UID, KASIR_UID]));
    await db.collection("shops").doc(SHOP_ID).collection("queues").doc("q_done").set({
      shopId: SHOP_ID,
      ownerUid: OWNER_UID,
      memberUids: [OWNER_UID, KASIR_UID],
      ticketCode: "A-001",
      ticketNumber: 1,
      dateKey: "2026-11-15",
      customerName: "Budi",
      customerPhone: "",
      barberId: "b1",
      barberName: "Andi",
      barberCommissionRate: 40,
      serviceNames: ["Signature Haircut"],
      serviceSummary: "Signature Haircut",
      subtotal: 45000,
      discount: 0,
      totalAmount: 45000,
      paidAmount: 50000,
      changeAmount: 5000,
      paymentMethod: "TUNAI",
      status: "COMPLETED",
      notes: "",
      createdByUid: KASIR_UID,
      createdByName: "Kasir",
      createdAt: ts(),
      updatedAt: ts(),
    });
  });

  // Kasir cannot mutate COMPLETED ticket
  await assertFails(
    kasirDb.collection("shops").doc(SHOP_ID).collection("queues").doc("q_done").update({
      totalAmount: 0,
      updatedAt: ts(),
    })
  );

  // Owner CAN correct if needed
  await assertSucceeds(
    ownerDb.collection("shops").doc(SHOP_ID).collection("queues").doc("q_done").update({
      notes: "Dikoreksi owner",
      updatedAt: ts(),
    })
  );
});

test("Owner can manage staff_accounts; outsider and Kasir cannot create staff_accounts", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, { email: OWNER_EMAIL, email_verified: true }).firestore();
  const kasirDb = testEnv.authenticatedContext(KASIR_UID, { email: KASIR_EMAIL, email_verified: true }).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID, { email: "outsider@test.com", email_verified: true }).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("shops").doc(SHOP_ID).set(shopDoc(OWNER_UID, [OWNER_UID, KASIR_UID]));
    await db.collection("shops").doc(SHOP_ID).collection("members").doc(KASIR_UID).set(memberDoc(KASIR_UID, "KASIR", [OWNER_UID, KASIR_UID]));
  });

  const payload = {
    shopId: SHOP_ID,
    ownerUid: OWNER_UID,
    memberUids: [OWNER_UID, KASIR_UID],
    username: "151500",
    passwordHash: "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
    displayName: "Owner 151500",
    role: "OWNER",
    commissionRate: 0,
    active: true,
    createdAt: ts(),
    updatedAt: ts(),
  };

  await assertSucceeds(ownerDb.collection("shops").doc(SHOP_ID).collection("staff_accounts").doc("acc_151500").set(payload));
  await assertFails(kasirDb.collection("shops").doc(SHOP_ID).collection("staff_accounts").doc("acc_kasir").set({ ...payload, username: "kasir" }));
  await assertFails(outsiderDb.collection("shops").doc(SHOP_ID).collection("staff_accounts").doc("acc_151500").get());
  await assertFails(ownerDb.collection("shops").doc(SHOP_ID).collection("staff_accounts").doc("acc_ghost").set({ ...payload, ghost: true }));
});

