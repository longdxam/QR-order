import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";

import "fake-indexeddb/auto";

const events = new EventTarget();
Object.defineProperty(globalThis, "window", {
  configurable: true,
  value: {
    indexedDB: globalThis.indexedDB,
    addEventListener: events.addEventListener.bind(events),
    removeEventListener: events.removeEventListener.bind(events),
    dispatchEvent: events.dispatchEvent.bind(events),
  },
});

const cartStore = await import("./cart.ts");
const sessionId = "01994b2e-f5f7-7000-8000-000000000001";

beforeEach(async () => {
  await new Promise((resolve, reject) => {
    const request = indexedDB.deleteDatabase("qros-guest");
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
    request.onblocked = () => reject(new Error("Cơ sở dữ liệu kiểm thử đang bị giữ mở."));
  });
});

function exampleLine(overrides = {}) {
  return {
    menuItemId: "01994b2e-f5f7-7000-8000-000000000010",
    menuItemName: "Trà sữa trân châu",
    variantId: "01994b2e-f5f7-7000-8000-000000000011",
    variantName: "Size L",
    optionIds: ["01994b2e-f5f7-7000-8000-000000000012"],
    optionNames: ["Đường 50%"],
    quantity: 2,
    note: "Ít đá",
    addedBy: "Long",
    displayUnitAmount: 55_000,
    ...overrides,
  };
}

test("FR-CUS-06: giỏ IndexedDB tồn tại qua lần mở cơ sở dữ liệu kế tiếp", async () => {
  await cartStore.addCartLine(sessionId, exampleLine());

  const reloaded = await cartStore.loadCart(sessionId);

  assert.equal(reloaded.lines.length, 1);
  assert.equal(reloaded.lines[0].menuItemName, "Trà sữa trân châu");
  assert.equal(reloaded.lines[0].quantity, 2);
});

test("ADR-06: payload đặt món không bao giờ chứa tên hoặc giá hiển thị", async () => {
  await cartStore.addCartLine(sessionId, exampleLine());

  const checkout = await cartStore.prepareCheckout(sessionId);

  assert.deepEqual(checkout.request, {
    lines: [{
      menuItemId: "01994b2e-f5f7-7000-8000-000000000010",
      variantId: "01994b2e-f5f7-7000-8000-000000000011",
      optionIds: ["01994b2e-f5f7-7000-8000-000000000012"],
      quantity: 2,
      note: "Ít đá",
      addedBy: "Long",
    }],
  });
  assert.equal(JSON.stringify(checkout.request).includes("55000"), false);
  assert.equal(JSON.stringify(checkout.request).includes("unitPrice"), false);
});

test("FR-CUS-09: retry giữ nguyên UUID idempotency cho tới khi giỏ thay đổi", async () => {
  await cartStore.addCartLine(sessionId, exampleLine());

  const first = await cartStore.prepareCheckout(sessionId);
  const retry = await cartStore.prepareCheckout(sessionId);
  assert.equal(retry.idempotencyKey, first.idempotencyKey);

  const current = await cartStore.loadCart(sessionId);
  await cartStore.updateCartLineQuantity(sessionId, current.lines[0].id, 3);
  const changed = await cartStore.prepareCheckout(sessionId);
  assert.notEqual(changed.idempotencyKey, first.idempotencyKey);
});

test("không xoá nhầm giỏ đã được sửa trong lúc request đang gửi", async () => {
  await cartStore.addCartLine(sessionId, exampleLine());
  const inFlight = await cartStore.prepareCheckout(sessionId);
  const current = await cartStore.loadCart(sessionId);
  await cartStore.updateCartLineQuantity(sessionId, current.lines[0].id, 3);

  await cartStore.clearCheckedOutCart(sessionId, inFlight.idempotencyKey);

  const preserved = await cartStore.loadCart(sessionId);
  assert.equal(preserved.lines[0].quantity, 3);
});
