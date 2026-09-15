import type { CreateOrderRequest } from "@/lib/api";

const DATABASE_NAME = "qros-guest";
const DATABASE_VERSION = 1;
const CART_STORE = "carts";
const CART_CHANGED_EVENT = "qros:cart-changed";

export type CartLine = {
  id: string;
  menuItemId: string;
  menuItemName: string;
  variantId: string;
  variantName: string;
  optionIds: string[];
  optionNames: string[];
  quantity: number;
  note?: string;
  addedBy?: string;
  /** Chỉ dùng để hiển thị ước tính tại client; không bao giờ đi vào request đặt món (`ADR-06`). */
  displayUnitAmount: number;
};

export type Cart = {
  sessionId: string;
  lines: CartLine[];
  /** Giữ nguyên qua retry; mọi thay đổi nội dung giỏ sẽ xoá khoá này. */
  checkoutKey?: string;
  updatedAt: string;
};

export type NewCartLine = Omit<CartLine, "id">;

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = window.indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains(CART_STORE)) {
        database.createObjectStore(CART_STORE, { keyPath: "sessionId" });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("Không mở được giỏ hàng trên thiết bị."));
  });
}

function requestResult<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("Không đọc được giỏ hàng trên thiết bị."));
  });
}

function transactionDone(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error ?? new Error("Không lưu được giỏ hàng trên thiết bị."));
    transaction.onabort = () => reject(transaction.error ?? new Error("Không lưu được giỏ hàng trên thiết bị."));
  });
}

function emptyCart(sessionId: string): Cart {
  return { sessionId, lines: [], updatedAt: new Date().toISOString() };
}

async function mutateCart(sessionId: string, mutate: (cart: Cart) => void): Promise<Cart> {
  const database = await openDatabase();
  try {
    const transaction = database.transaction(CART_STORE, "readwrite");
    const store = transaction.objectStore(CART_STORE);
    const cart = (await requestResult(store.get(sessionId))) as Cart | undefined ?? emptyCart(sessionId);
    mutate(cart);
    cart.updatedAt = new Date().toISOString();
    store.put(cart);
    await transactionDone(transaction);
    notifyCartChanged(sessionId);
    return cart;
  } finally {
    database.close();
  }
}

function sameConfiguration(left: CartLine, right: NewCartLine): boolean {
  return left.menuItemId === right.menuItemId
    && left.variantId === right.variantId
    && left.note === right.note
    && left.addedBy === right.addedBy
    && [...left.optionIds].sort().join(",") === [...right.optionIds].sort().join(",");
}

function notifyCartChanged(sessionId: string): void {
  window.dispatchEvent(new CustomEvent(CART_CHANGED_EVENT, { detail: sessionId }));
}

/** Đọc giỏ của đúng phiên bàn; mỗi phiên có giỏ độc lập để không rò món sang bàn khác. */
export async function loadCart(sessionId: string): Promise<Cart> {
  const database = await openDatabase();
  try {
    const transaction = database.transaction(CART_STORE, "readonly");
    const cart = (await requestResult(transaction.objectStore(CART_STORE).get(sessionId))) as Cart | undefined;
    return cart ?? emptyCart(sessionId);
  } finally {
    database.close();
  }
}

export async function addCartLine(sessionId: string, line: NewCartLine): Promise<Cart> {
  return mutateCart(sessionId, (cart) => {
    const existing = cart.lines.find((candidate) => sameConfiguration(candidate, line));
    if (existing) {
      if (existing.quantity + line.quantity > 20) {
        throw new Error("Mỗi dòng chỉ được tối đa 20 món.");
      }
      existing.quantity += line.quantity;
    } else {
      if (cart.lines.length >= 8) {
        throw new Error("Mỗi đơn chỉ được tối đa 8 dòng món.");
      }
      cart.lines.push({ ...line, id: crypto.randomUUID() });
    }
    delete cart.checkoutKey;
  });
}

export async function updateCartLineQuantity(sessionId: string, lineId: string, quantity: number): Promise<Cart> {
  return mutateCart(sessionId, (cart) => {
    const line = cart.lines.find((candidate) => candidate.id === lineId);
    if (!line) return;
    if (quantity <= 0) {
      cart.lines = cart.lines.filter((candidate) => candidate.id !== lineId);
    } else {
      line.quantity = Math.min(quantity, 20);
    }
    delete cart.checkoutKey;
  });
}

export async function removeCartLine(sessionId: string, lineId: string): Promise<Cart> {
  return mutateCart(sessionId, (cart) => {
    cart.lines = cart.lines.filter((candidate) => candidate.id !== lineId);
    delete cart.checkoutKey;
  });
}

/**
 * Chuẩn bị request và khoá idempotency trong cùng giao dịch IndexedDB. Snapshot hiển thị có giá,
 * nhưng payload trả ra cố ý chỉ chứa các trường được hợp đồng cho phép (`FR-CUS-08`, `ADR-06`).
 */
export async function prepareCheckout(sessionId: string): Promise<{
  idempotencyKey: string;
  request: CreateOrderRequest;
}> {
  const cart = await mutateCart(sessionId, (current) => {
    if (current.lines.length === 0) throw new Error("Giỏ hàng đang trống.");
    current.checkoutKey ??= crypto.randomUUID();
  });

  return {
    idempotencyKey: cart.checkoutKey!,
    request: {
      lines: cart.lines.map((line) => ({
        menuItemId: line.menuItemId,
        variantId: line.variantId,
        optionIds: line.optionIds.length > 0 ? line.optionIds : undefined,
        quantity: line.quantity,
        note: line.note || undefined,
        addedBy: line.addedBy || undefined,
      })),
    },
  };
}

/** Chỉ xoá đúng snapshot vừa đặt; không làm mất món được thêm trong lúc request đang bay. */
export async function clearCheckedOutCart(sessionId: string, idempotencyKey: string): Promise<void> {
  const database = await openDatabase();
  try {
    const transaction = database.transaction(CART_STORE, "readwrite");
    const store = transaction.objectStore(CART_STORE);
    const cart = (await requestResult(store.get(sessionId))) as Cart | undefined;
    if (cart?.checkoutKey === idempotencyKey) store.delete(sessionId);
    await transactionDone(transaction);
    notifyCartChanged(sessionId);
  } finally {
    database.close();
  }
}

export function subscribeCartChanged(sessionId: string, listener: () => void): () => void {
  const handle = (event: Event) => {
    if ((event as CustomEvent<string>).detail === sessionId) listener();
  };
  window.addEventListener(CART_CHANGED_EVENT, handle);
  return () => window.removeEventListener(CART_CHANGED_EVENT, handle);
}
