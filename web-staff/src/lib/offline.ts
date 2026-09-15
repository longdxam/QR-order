import type { KdsTicket, LineStatus, OrderLine, QueueResponse } from "./api.ts";

const DB_NAME = "qros-staff";
const DB_VERSION = 1;
const QUEUES = "queues";
const ACTIONS = "actions";

export type PendingAction = {
  id: string;
  storeId: string;
  lineId: string;
  version: number;
  status: LineStatus;
  reason?: string;
  createdAt: string;
};

const RANK: Record<LineStatus, number> = {
  PENDING: 0, CONFIRMED: 1, PREPARING: 2, READY: 3, SERVED: 4, CANCELLED: 5,
};

export function mergeStatus(local: LineStatus, remote: LineStatus): LineStatus {
  if (local === "CANCELLED" || remote === "CANCELLED") return "CANCELLED";
  return RANK[local] >= RANK[remote] ? local : remote;
}

export function stepsFrom(current: LineStatus, target: LineStatus): LineStatus[] {
  if (target === "CANCELLED") return current === "CANCELLED" ? [] : ["CANCELLED"];
  const path: LineStatus[] = ["PENDING", "CONFIRMED", "PREPARING", "READY", "SERVED"];
  const from = path.indexOf(current);
  const to = path.indexOf(target);
  return to > from ? path.slice(from + 1, to + 1) : [];
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(QUEUES)) db.createObjectStore(QUEUES);
      if (!db.objectStoreNames.contains(ACTIONS)) db.createObjectStore(ACTIONS, { keyPath: "id" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function transact<T>(storeName: string, mode: IDBTransactionMode,
  run: (store: IDBObjectStore, done: (value: T) => void) => void): Promise<T> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, mode);
    run(tx.objectStore(storeName), resolve);
    tx.onerror = () => reject(tx.error);
    tx.oncomplete = () => db.close();
  });
}

export function saveQueue(storeId: string, queue: QueueResponse): Promise<void> {
  return transact<void>(QUEUES, "readwrite", (store, done) => {
    const request = store.put(queue, storeId);
    request.onsuccess = () => done();
  });
}

export function loadQueue(storeId: string): Promise<QueueResponse | undefined> {
  return transact(QUEUES, "readonly", (store, done) => {
    const request = store.get(storeId);
    request.onsuccess = () => done(request.result as QueueResponse | undefined);
  });
}

export function enqueue(action: PendingAction): Promise<void> {
  return transact<void>(ACTIONS, "readwrite", (store, done) => {
    const request = store.put(action);
    request.onsuccess = () => done();
  });
}

export function pendingActions(): Promise<PendingAction[]> {
  return transact(ACTIONS, "readonly", (store, done) => {
    const request = store.getAll();
    request.onsuccess = () => done((request.result as PendingAction[])
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt)));
  });
}

export function removeAction(id: string): Promise<void> {
  return transact<void>(ACTIONS, "readwrite", (store, done) => {
    const request = store.delete(id);
    request.onsuccess = () => done();
  });
}

export function updateLine(queue: QueueResponse, changed: OrderLine): QueueResponse {
  return {
    ...queue,
    tickets: queue.tickets.map((ticket: KdsTicket) => ({
      ...ticket,
      lines: ticket.lines.map((line) => line.id === changed.id ? changed : line),
    })).filter((ticket) => ticket.lines.some((line) => !["SERVED", "CANCELLED"].includes(line.status))),
  };
}
