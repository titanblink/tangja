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

function pastDate(msOffset = 5000) {
  return new Date(Date.now() - msOffset);
}

test("Unauthenticated user cannot read or write any user subcollection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("machines").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("machines")
      .doc("machine_01")
      .set({
        machineNumber: 1,
        name: "1번 기계",
        machineType: "FOUR_SLOT",
        active: true,
        restockThreshold: 12,
        nextRestockDate: "",
        slots: [],
        createdAt: pastDate(),
        updatedAt: pastDate(),
      })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("inventory").get()
  );
});

test("Cross-user isolation blocks Bob from accessing Alice's data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("inventory")
      .doc("prod_1")
      .set({
        productName: "산리오 쿠로미 인형",
        category: "인형",
        unitCost: 3500,
        warehouseQty: 20,
        machineQty: 0,
        totalPurchasedQty: 20,
        lowStockThreshold: 5,
        createdAt: pastDate(),
        updatedAt: pastDate(),
      })
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("inventory")
      .doc("prod_1")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("inventory").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("inventory")
      .doc("prod_1")
      .update({ warehouseQty: 0, updatedAt: pastDate(1000) })
  );
});

test("Machines CRUD & slot capacity enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const machinesCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("machines");

  // Create valid 1-slot machine
  await assertSucceeds(
    machinesCol.doc("machine_01").set({
      machineNumber: 1,
      name: "1번 입구 대형기계",
      machineType: "SINGLE_SLOT",
      active: true,
      restockThreshold: 5,
      nextRestockDate: "2026-11-20",
      slots: [
        {
          productId: "prod_1",
          productName: "대형 곰인형",
          quantity: 8,
          unitCost: 5000,
        },
      ],
      createdAt: pastDate(10000),
      updatedAt: pastDate(10000),
    })
  );

  // Reject SINGLE_SLOT with 2 slots
  await assertFails(
    machinesCol.doc("machine_02").set({
      machineNumber: 2,
      name: "2번 기계",
      machineType: "SINGLE_SLOT",
      active: true,
      restockThreshold: 5,
      nextRestockDate: "",
      slots: [
        { productId: "p1", productName: "인형1", quantity: 3, unitCost: 3000 },
        { productId: "p2", productName: "인형2", quantity: 3, unitCost: 3000 },
      ],
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Valid partial update preserving machineNumber & createdAt
  await assertSucceeds(
    machinesCol.doc("machine_01").update({
      active: false,
      machineType: "FOUR_SLOT",
      updatedAt: pastDate(1000),
    })
  );

  // Reject immutable machineNumber change
  await assertFails(
    machinesCol.doc("machine_01").update({
      machineNumber: 9,
      updatedAt: pastDate(500),
    })
  );

  // Reject ghost field
  await assertFails(
    machinesCol.doc("machine_03").set({
      id: "machine_03",
      machineNumber: 3,
      name: "3번 기계",
      machineType: "FOUR_SLOT",
      active: true,
      restockThreshold: 12,
      nextRestockDate: "",
      slots: [],
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Dynamic machine count (> 20) succeeds up to 100
  await assertSucceeds(
    machinesCol.doc("machine_21").set({
      machineNumber: 21,
      name: "21번",
      machineType: "FOUR_SLOT",
      active: true,
      restockThreshold: 12,
      nextRestockDate: "",
      slots: [
        {
          productId: "prod_1",
          productName: "산리오 인형",
          quantity: 4,
          unitCost: 3000,
          slotIndex: 3,
          sellingPrice: 1000,
          totalPicked: 12,
          todayPicked: 3,
          todayPickedDateKey: "2026-11-17",
        },
      ],
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  // Reject out-of-range slotIndex (> 40)
  await assertFails(
    machinesCol.doc("machine_22").set({
      machineNumber: 22,
      name: "22번",
      machineType: "FOUR_SLOT",
      active: true,
      restockThreshold: 12,
      nextRestockDate: "",
      slots: [
        {
          productId: "prod_1",
          productName: "산리오 인형",
          quantity: 4,
          unitCost: 3000,
          slotIndex: 41,
        },
      ],
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    machinesCol.doc("machine_101").set({
      machineNumber: 101,
      name: "101번",
      machineType: "FOUR_SLOT",
      active: true,
      restockThreshold: 12,
      nextRestockDate: "",
      slots: [],
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Inventory, purchases, supplies, and dailyRevenues CRUD & security bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);

  // Purchase record
  await assertSucceeds(
    userDoc.collection("purchases").doc("pur_1").set({
      dateKey: "2026-11-17",
      productId: "prod_1",
      productName: "치이카와 키링",
      quantity: 30,
      unitCost: 2200,
      totalAmount: 66000,
      source: "RECEIPT_AI",
      supplierName: "동대문 완구",
      createdAt: pastDate(10000),
      updatedAt: pastDate(10000),
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    userDoc.collection("purchases").doc("pur_2").set({
      dateKey: "2026-11-17",
      productId: "prod_1",
      productName: "치이카와 키링",
      quantity: 10,
      unitCost: 2200,
      totalAmount: 22000,
      source: "MANUAL",
      supplierName: "",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Supply record
  await assertSucceeds(
    userDoc.collection("supplies").doc("sup_1").set({
      dateKey: "2026-11-17",
      machineNumber: 1,
      machineName: "1번 기계",
      productId: "prod_1",
      productName: "치이카와 키링",
      quantity: 10,
      unitCost: 2200,
      totalAmount: 22000,
      createdAt: pastDate(8000),
      updatedAt: pastDate(8000),
    })
  );

  // Daily revenue record
  await assertSucceeds(
    userDoc.collection("dailyRevenues").doc("2026-11-17_m01").set({
      dateKey: "2026-11-17",
      machineNumber: 1,
      revenueAmount: 85000,
      memo: "주말 특가 운영",
      createdAt: pastDate(8000),
      updatedAt: pastDate(8000),
    })
  );

  // Update revenue amount
  await assertSucceeds(
    userDoc.collection("dailyRevenues").doc("2026-11-17_m01").update({
      revenueAmount: 92000,
      memo: "마감 정산 완료",
      updatedAt: pastDate(1000),
    })
  );

  // Reject list of machines query? Should succeed for Alice!
  await assertSucceeds(userDoc.collection("machines").get());
  await assertSucceeds(userDoc.collection("inventory").get());
  await assertSucceeds(userDoc.collection("purchases").get());
  await assertSucceeds(userDoc.collection("supplies").get());
  await assertSucceeds(userDoc.collection("dailyRevenues").get());
});
