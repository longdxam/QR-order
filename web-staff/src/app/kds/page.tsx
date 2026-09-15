"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ApiError, getQueue, markIngredientSoldOut, patchLine, type IngredientRef,
  type KdsTicket, type LineStatus, type OrderLine,
  type QueueResponse, type VersionProblem,
} from "@/lib/api";
import {
  enqueue, loadQueue, mergeStatus, pendingActions, removeAction, saveQueue, stepsFrom, updateLine,
} from "@/lib/offline";
import { connectKds } from "@/lib/realtime";

const NEXT: Partial<Record<LineStatus, LineStatus>> = {
  PENDING: "CONFIRMED", CONFIRMED: "PREPARING", PREPARING: "READY", READY: "SERVED",
};

function deviceId(): string {
  let id = localStorage.getItem("qros-kds-device");
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem("qros-kds-device", id);
  }
  return id;
}

function findLine(queue: QueueResponse, lineId: string): OrderLine | undefined {
  return queue.tickets.flatMap((ticket) => ticket.lines).find((line) => line.id === lineId);
}

function playAlert(): void {
  const context = new AudioContext();
  const oscillator = context.createOscillator();
  const gain = context.createGain();
  oscillator.frequency.value = 880;
  gain.gain.setValueAtTime(0.12, context.currentTime);
  gain.gain.exponentialRampToValueAtTime(0.001, context.currentTime + 0.18);
  oscillator.connect(gain).connect(context.destination);
  oscillator.start();
  oscillator.stop(context.currentTime + 0.18);
  oscillator.addEventListener("ended", () => void context.close(), { once: true });
}

export default function KdsPage() {
  const router = useRouter();
  const [storeId, setStoreId] = useState("");
  const [queue, setQueue] = useState<QueueResponse>();
  const [online, setOnline] = useState(true);
  const [connected, setConnected] = useState(false);
  const [sound, setSound] = useState(true);
  const [notice, setNotice] = useState("");
  const [ingredientWarnings, setIngredientWarnings] = useState<Record<string, string[]>>({});
  const [now, setNow] = useState(0);

  const refresh = useCallback(async (id: string) => {
    const fresh = await getQueue(id);
    setQueue(fresh);
    await saveQueue(id, fresh);
    return fresh;
  }, []);

  const flush = useCallback(async (id: string) => {
    for (const action of await pendingActions()) {
      if (action.storeId !== id) continue;
      let fresh = await getQueue(id);
      let current = findLine(fresh, action.lineId);
      if (!current) {
        await removeAction(action.id);
        continue;
      }
      const target = mergeStatus(action.status, current.status);
      for (const step of stepsFrom(current.status, target)) {
        current = await patchLine(id, deviceId(), current.id, current.version, step, action.reason);
      }
      await removeAction(action.id);
      fresh = updateLine(fresh, current);
      setQueue(fresh);
      await saveQueue(id, fresh);
    }
  }, []);

  useEffect(() => {
    const id = localStorage.getItem("qros-store-id");
    if (!id) {
      router.replace("/login");
      return;
    }
    queueMicrotask(() => {
      setStoreId(id);
      void loadQueue(id).then((cached) => { if (cached) setQueue(cached); });
      void refresh(id).then(() => flush(id)).catch((error) => {
        if (error instanceof ApiError && error.status === 401) router.replace("/login");
        setOnline(false);
      });
    });
    const handleOnline = () => {
      setOnline(true);
      void refresh(id).then(() => flush(id));
    };
    const handleOffline = () => setOnline(false);
    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, [flush, refresh, router]);

  useEffect(() => {
    if (!storeId) return;
    return connectKds(storeId, (event) => {
      if (event.type === "ResyncRequired" || event.type === "OrderPlaced" || event.type === "OrderCancelled") {
        if (event.type === "OrderPlaced") {
          setNotice("Có đơn mới");
          if (sound) playAlert();
        }
        void refresh(storeId);
        return;
      }
      if (event.type === "OrderLineStatusChanged" && event.lineId && event.status && event.version !== undefined) {
        setQueue((current) => {
          if (!current) return current;
          const old = findLine(current, event.lineId!);
          if (!old) return current;
          const next = updateLine(current, { ...old, status: event.status as LineStatus, version: event.version! });
          void saveQueue(storeId, next);
          return next;
        });
        if (event.changedBy) setNotice(`Vừa được cập nhật bởi ${event.changedBy}`);
      }
      if (event.type === "IngredientSoldOut" && event.ingredientName) {
        setIngredientWarnings((current) => addWarnings(current,
          event.affectedOpenOrderIds ?? [], event.ingredientName!));
        setNotice(`${event.ingredientName} đã hết · ${event.affectedOpenOrderIds?.length ?? 0} đơn cần xử lý`);
        void refresh(storeId);
      }
    }, setConnected);
  }, [refresh, sound, storeId]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1_000);
    return () => window.clearInterval(timer);
  }, []);

  async function advance(line: OrderLine, target: LineStatus, reason?: string) {
    if (!queue || !storeId) return;
    const optimistic = { ...line, status: target, version: line.version + 1 };
    setQueue(updateLine(queue, optimistic));
    try {
      const changed = await patchLine(storeId, deviceId(), line.id, line.version, target, reason);
      setQueue((current) => {
        if (!current) return current;
        const next = updateLine(current, changed);
        void saveQueue(storeId, next);
        return next;
      });
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 409) {
        const current = (caught.problem as VersionProblem).current;
        setQueue((value) => value ? updateLine(value, current) : value);
        setNotice("Dòng này vừa được đồng đội cập nhật — đã tải bản mới nhất.");
      } else if (!navigator.onLine || !(caught instanceof ApiError)) {
        setOnline(false);
        await enqueue({
          id: crypto.randomUUID(), storeId, lineId: line.id, version: line.version,
          status: target, reason, createdAt: new Date().toISOString(),
        });
        setNotice("Đã lưu thao tác ngoại tuyến; sẽ tự đồng bộ khi có mạng.");
      } else {
        setQueue(queue);
        setNotice(caught.message);
      }
    }
  }

  async function reportSoldOut(ingredient: IngredientRef) {
    if (!storeId || ingredient.soldOut) return;
    if (!window.confirm(`Báo hết “${ingredient.name}” và cảnh báo các đơn đang chờ?`)) return;
    try {
      const result = await markIngredientSoldOut(storeId, ingredient.id);
      setIngredientWarnings((current) => addWarnings(current,
        result.affectedOpenOrderIds, result.ingredientName));
      setNotice(`${result.ingredientName} đã hết · ${result.affectedOpenOrderIds.length} đơn cần xử lý`);
      await refresh(storeId);
    } catch (caught) {
      setNotice(caught instanceof ApiError ? caught.message : "Không thể báo hết nguyên liệu.");
    }
  }

  const tickets = useMemo(() => [...(queue?.tickets ?? [])].sort((a, b) => {
    const overdueA = now - Date.parse(a.placedAt) >= a.slaSeconds * 1000;
    const overdueB = now - Date.parse(b.placedAt) >= b.slaSeconds * 1000;
    return Number(overdueB) - Number(overdueA) || Date.parse(a.placedAt) - Date.parse(b.placedAt);
  }), [now, queue]);

  return <main className={notice === "Có đơn mới" ? "kds flash" : "kds"}>
    <header>
      <div><p className="eyebrow">QROS · KDS</p><h1>Hàng pha chế</h1></div>
      <div className="toolbar">
        <span className={`connection ${online && connected ? "ok" : "off"}`}>
          {online ? (connected ? "Trực tuyến" : "Đang nối…") : "Ngoại tuyến"}
        </span>
        <button type="button" aria-pressed={sound} onClick={() => setSound((value) => !value)}>
          Âm báo: {sound ? "Bật" : "Tắt"}
        </button>
        <button type="button" onClick={() => void refresh(storeId)}>Tải lại</button>
      </div>
    </header>
    {notice && <p className="notice" role="status" aria-live="polite" onAnimationEnd={() => setNotice("")}>{notice}</p>}
    <section className="ticket-grid" aria-label="Danh sách phiếu pha chế">
      {tickets.map((ticket) => <Ticket key={ticket.orderId} ticket={ticket} now={now}
        warnings={ingredientWarnings[ticket.orderId] ?? []} onAdvance={advance} onSoldOut={reportSoldOut} />)}
      {queue && tickets.length === 0 && <div className="empty"><strong>Hàng đợi trống</strong><span>Đơn mới sẽ xuất hiện tự động.</span></div>}
      {!queue && <div className="empty"><strong>Đang tải hàng đợi…</strong></div>}
    </section>
  </main>;
}

function Ticket({ ticket, now, warnings, onAdvance, onSoldOut }: {
  ticket: KdsTicket;
  now: number;
  warnings: string[];
  onAdvance: (line: OrderLine, status: LineStatus, reason?: string) => Promise<void>;
  onSoldOut: (ingredient: IngredientRef) => Promise<void>;
}) {
  const elapsed = Math.max(0, Math.floor((now - Date.parse(ticket.placedAt)) / 1_000));
  const overdue = elapsed >= ticket.slaSeconds;
  return <article className={`ticket ${overdue ? "overdue" : ""}`}>
    <div className="ticket-head">
      <div><strong>{ticket.shortCode}</strong><span>Bàn {ticket.tableLabel}</span></div>
      <time>{Math.floor(elapsed / 60)}:{String(elapsed % 60).padStart(2, "0")}</time>
    </div>
    {ticket.requiresStaffConfirmation && <p className="confirmation">Cần xác nhận đơn đầu</p>}
    {warnings.length > 0 && <p className="ingredient-warning" role="alert">
      Hết nguyên liệu: {warnings.join(", ")} · cần xử lý với khách
    </p>}
    <div className="lines">{ticket.lines.map((line) => <div className="line" key={line.id}>
      <div>
        <strong>{line.quantity}× {line.name}</strong>
        <span>{[line.variantName, ...(line.optionNames ?? [])].filter(Boolean).join(" · ")}</span>
        {line.note && <em>“{line.note}”</em>}
        {(line.ingredients?.length ?? 0) > 0 && <div className="ingredients" aria-label="Nguyên liệu của món">
          {line.ingredients?.map((ingredient) => <button type="button" key={ingredient.id}
            disabled={ingredient.soldOut} onClick={() => void onSoldOut(ingredient)}>
            {ingredient.soldOut ? `Đã hết: ${ingredient.name}` : `Báo hết: ${ingredient.name}`}
          </button>)}
        </div>}
      </div>
      <div className="actions">
        <span className={`status s-${line.status.toLowerCase()}`}>{line.status}</span>
        {NEXT[line.status] && <button type="button" className="primary small" onClick={() => void onAdvance(line, NEXT[line.status]!)}>{label(NEXT[line.status]!)}</button>}
        {!(["SERVED", "CANCELLED"] as LineStatus[]).includes(line.status) && <button type="button" className="danger small" onClick={() => {
          const reason = window.prompt("Lý do huỷ món:");
          if (reason?.trim()) void onAdvance(line, "CANCELLED", reason.trim());
        }}>Huỷ</button>}
      </div>
    </div>)}</div>
  </article>;
}

function addWarnings(current: Record<string, string[]>, orderIds: string[], ingredientName: string) {
  const next = { ...current };
  orderIds.forEach((orderId) => {
    next[orderId] = [...new Set([...(next[orderId] ?? []), ingredientName])];
  });
  return next;
}

function label(status: LineStatus): string {
  return ({
    CONFIRMED: "Xác nhận", PREPARING: "Bắt đầu", READY: "Sẵn sàng", SERVED: "Đã phục vụ",
  } as Partial<Record<LineStatus, string>>)[status] ?? status;
}
