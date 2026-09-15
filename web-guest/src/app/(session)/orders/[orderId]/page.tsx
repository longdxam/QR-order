"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";

import { ApiProblemError, getOrder, type Order } from "@/lib/api";
import { loadGuestSession } from "@/lib/guestSession";

type State =
  | { step: "loading" }
  | { step: "error"; message: string }
  | { step: "done"; order: Order };

export default function OrderPage() {
  const params = useParams<{ orderId: string }>();
  const [state, setState] = useState<State>({ step: "loading" });

  useEffect(() => {
    const session = loadGuestSession();
    if (!session) return;
    const accessToken = session.accessToken;
    const orderId = params.orderId;
    let active = true;
    let loading = false;
    let loaded = false;
    async function refresh() {
      if (loading) return;
      loading = true;
      try {
        const order = await getOrder(accessToken, orderId);
        if (active) {
          loaded = true;
          setState({ step: "done", order });
        }
      } catch (caught) {
        if (!active || loaded) return;
        setState({
          step: "error",
          message: caught instanceof ApiProblemError
            ? (caught.problem.detail ?? caught.problem.title)
            : "Không tải được đơn hàng.",
        });
      } finally {
        loading = false;
      }
    }
    void refresh();
    // FR-CUS-10: đồng bộ tiến trình mỗi giây; kênh realtime phiên khách sẽ thay nhịp này ở OPEN-20.
    const timer = window.setInterval(() => void refresh(), 1_000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [params.orderId]);

  if (state.step === "loading") return <main style={mainStyle}><p>Đang tải đơn hàng…</p></main>;
  if (state.step === "error") return <main style={mainStyle}><p role="alert" style={{ color: "#b00020" }}>{state.message}</p></main>;

  const { order } = state;
  return (
    <main style={mainStyle}>
      <p style={{ color: "#087f5b", fontWeight: 700 }}>✓ Đặt món thành công</p>
      <h1 style={{ fontSize: "1.5rem" }}>Đơn {order.shortCode ?? order.id}</h1>
      {order.requiresStaffConfirmation && (
        <p style={{ padding: "0.75rem", borderRadius: "0.5rem", background: "light-dark(#fff3bf,#5c4b00)" }}>
          Đơn đầu tiên của phiên đang chờ nhân viên xác nhận.
        </p>
      )}
      <p>Trạng thái: <strong>{statusLabel(order.status)}</strong></p>
      {order.estimatedReadyAt && <p>Dự kiến sẵn sàng: <strong>{new Date(order.estimatedReadyAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}</strong></p>}
      <div style={{ display: "grid", gap: "0.65rem", margin: "1rem 0" }}>
        {order.lines.map((line) => (
          <article key={line.id} style={{ borderBottom: "1px solid #ddd", paddingBottom: "0.65rem" }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem" }}>
              <strong>{line.quantity} × {line.name}</strong>
              <span>{formatMoney(line.lineTotal.amount)}</span>
            </div>
            {line.variantName && <div style={mutedStyle}>{line.variantName}</div>}
            {line.optionNames && line.optionNames.length > 0 && <div style={mutedStyle}>{line.optionNames.join(", ")}</div>}
            {line.note && <div style={{ marginTop: "0.25rem" }}>Ghi chú: {line.note}</div>}
            <div style={mutedStyle}>Tiến trình: {lineStatusLabel(line.status)}</div>
          </article>
        ))}
      </div>
      <div style={{ display: "flex", justifyContent: "space-between", fontSize: "1.15rem" }}>
        <strong>Tổng cộng</strong>
        <strong>{formatMoney(order.total.amount)}</strong>
      </div>
      <Link href="/menu" style={{ display: "inline-block", marginTop: "1.5rem" }}>Gọi thêm món</Link>
    </main>
  );
}

function lineStatusLabel(status: Order["lines"][number]["status"]): string {
  return ({
    PENDING: "Chờ xác nhận",
    CONFIRMED: "Đã xác nhận",
    PREPARING: "Đang pha chế",
    READY: "Sẵn sàng",
    SERVED: "Đã phục vụ",
    CANCELLED: "Đã huỷ",
  })[status];
}

function statusLabel(status: Order["status"]): string {
  const labels: Record<Order["status"], string> = {
    PENDING: "Đang chờ xác nhận",
    CONFIRMED: "Đã xác nhận",
    PREPARING: "Đang pha chế",
    READY: "Sẵn sàng",
    SERVED: "Đã phục vụ",
    COMPLETED: "Hoàn tất",
    CANCELLED: "Đã huỷ",
    EXPIRED: "Đã hết hạn",
  };
  return labels[status];
}

function formatMoney(amount: number): string {
  return `${amount.toLocaleString("vi-VN")}đ`;
}

const mainStyle: React.CSSProperties = { padding: "1rem", maxWidth: "40rem", margin: "0 auto", minHeight: "100dvh" };
const mutedStyle: React.CSSProperties = { color: "#777", fontSize: "0.88rem", marginTop: "0.15rem" };
