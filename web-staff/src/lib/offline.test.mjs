import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";
import "fake-indexeddb/auto";

const offline = await import("./offline.ts");

beforeEach(async () => {
  await new Promise((resolve, reject) => {
    const request = indexedDB.deleteDatabase("qros-staff");
    request.onsuccess = resolve;
    request.onerror = () => reject(request.error);
  });
});

test("EC-06: trạng thái xa hơn thắng khi hợp nhất", () => {
  assert.equal(offline.mergeStatus("PREPARING", "CONFIRMED"), "PREPARING");
  assert.equal(offline.mergeStatus("READY", "PREPARING"), "READY");
});

test("EC-06: CANCELLED luôn thắng", () => {
  assert.equal(offline.mergeStatus("READY", "CANCELLED"), "CANCELLED");
  assert.equal(offline.mergeStatus("CANCELLED", "SERVED"), "CANCELLED");
});

test("offline replay đi từng nấc hợp lệ", () => {
  assert.deepEqual(offline.stepsFrom("CONFIRMED", "SERVED"), ["PREPARING", "READY", "SERVED"]);
});

test("hàng đợi thao tác tồn tại trong IndexedDB", async () => {
  const action = { id: crypto.randomUUID(), storeId: crypto.randomUUID(), lineId: crypto.randomUUID(),
    version: 2, status: "READY", createdAt: new Date().toISOString() };
  await offline.enqueue(action);
  assert.deepEqual(await offline.pendingActions(), [action]);
  await offline.removeAction(action.id);
  assert.deepEqual(await offline.pendingActions(), []);
});
