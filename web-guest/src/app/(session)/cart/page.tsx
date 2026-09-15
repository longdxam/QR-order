"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { ApiProblemError, placeOrder } from "@/lib/api";
import {
  clearCheckedOutCart,
  loadCart,
  prepareCheckout,
  removeCartLine,
  subscribeCartChanged,
  updateCartLineQuantity,
  type Cart,
} from "@/lib/cart";
import { loadGuestSession, type TableSession } from "@/lib/guestSession";

export default function CartPage() {
  const router = useRouter();
  const [session, setSession] = useState<TableSession | null>(null);
  const [cart, setCart] = useState<Cart | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [placing, setPlacing] = useState(false);

  useEffect(() => {
    const currentSession = loadGuestSession();
    if (!currentSession) return;
    let active = true;
    const refresh = () => {
      void loadCart(currentSession.sessionId)
        .then((currentCart) => {
          if (active) {
            setSession(currentSession);
            setCart(currentCart);
          }
        })
        .catch(() => {
          if (active) setError("Không đọc được giỏ hàng trên thiết bị này.");
        });
    };
    refresh();
    const unsubscribe = subscribeCartChanged(currentSession.sessionId, refresh);
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  async function changeQuantity(lineId: string, quantity: number) {
    if (!session) return;
    setError(null);
    try {
      setCart(await updateCartLineQuantity(session.sessionId, lineId, quantity));
    } catch {
      setError("Không cập nhật được giỏ hàng.");
    }
  }

  async function remove(lineId: string) {
    if (!session) return;
    setError(null);
    try {
      setCart(await removeCartLine(session.sessionId, lineId));
    } catch {
      setError("Không xoá được món khỏi giỏ hàng.");
    }
  }

  async function checkout() {
    if (!session || !cart?.lines.length) return;
    setPlacing(true);
    setError(null);
    try {
      const checkoutAttempt = await prepareCheckout(session.sessionId);
      const order = await placeOrder(session.accessToken, checkoutAttempt.idempotencyKey, checkoutAttempt.request);
      await clearCheckedOutCart(session.sessionId, checkoutAttempt.idempotencyKey);
      router.push(`/orders/${order.id}`);
    } catch (caught) {
      if (caught instanceof ApiProblemError) {
        setError(messageForProblem(caught));
      } else if (caught instanceof TypeError) {
        setError("Mất kết nối. Bạn có thể bấm đặt lại an toàn; hệ thống sẽ dùng cùng mã chống trùng đơn.");
      } else {
        setError(caught instanceof Error ? caught.message : "Không đặt được món, vui lòng thử lại.");
      }
    } finally {
      setPlacing(false);
    }
  }

  if (!cart) {
    return <main style={mainStyle}><p>Đang mở giỏ hàng…</p></main>;
  }

  const estimatedTotal = cart.lines.reduce((sum, line) => sum + line.displayUnitAmount * line.quantity, 0);

  return (
    <main style={mainStyle}>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1 style={{ fontSize: "1.35rem" }}>Giỏ hàng</h1>
        <Link href="/menu">Thêm món</Link>
      </header>

      {cart.lines.length === 0 ? (
        <section style={emptyStyle}>
          <p>Giỏ hàng đang trống.</p>
          <Link href="/menu">Xem thực đơn</Link>
        </section>
      ) : (
        <>
          <div style={{ display: "grid", gap: "0.75rem" }}>
            {cart.lines.map((line) => (
              <article key={line.id} style={lineStyle}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem" }}>
                  <div>
                    <strong>{line.menuItemName}</strong>
                    <div style={mutedStyle}>{line.variantName}</div>
                    {line.optionNames.length > 0 && <div style={mutedStyle}>{line.optionNames.join(", ")}</div>}
                    {line.note && <div style={{ marginTop: "0.35rem" }}>Ghi chú: {line.note}</div>}
                  </div>
                  <strong style={{ whiteSpace: "nowrap" }}>{formatMoney(line.displayUnitAmount * line.quantity)}</strong>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: "0.7rem" }}>
                  <label>
                    Số lượng{" "}
                    <input
                      aria-label={`Số lượng ${line.menuItemName}`}
                      type="number"
                      min={1}
                      max={20}
                      value={line.quantity}
                      disabled={placing}
                      onChange={(event) => void changeQuantity(line.id, Math.max(1, Math.min(20, Number(event.target.value) || 1)))}
                      style={{ width: "4rem", padding: "0.35rem" }}
                    />
                  </label>
                  <button type="button" disabled={placing} onClick={() => void remove(line.id)} style={removeButtonStyle}>Xoá</button>
                </div>
              </article>
            ))}
          </div>

          <section style={{ marginTop: "1rem", borderTop: "1px solid #ddd", paddingTop: "1rem" }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: "1.1rem" }}>
              <strong>Tạm tính</strong>
              <strong>{formatMoney(estimatedTotal)}</strong>
            </div>
            <p style={mutedStyle}>Giá hiển thị chỉ để tham khảo. Máy chủ sẽ tính lại toàn bộ từ thực đơn khi đặt món.</p>
          </section>
        </>
      )}

      {error && <p role="alert" style={{ color: "#b00020" }}>{error}</p>}
      {cart.lines.length > 0 && (
        <button type="button" disabled={placing} onClick={() => void checkout()} style={checkoutButtonStyle}>
          {placing ? "Đang đặt món…" : "Đặt món"}
        </button>
      )}
    </main>
  );
}

function messageForProblem(error: ApiProblemError): string {
  switch (error.problem.code) {
    case "ITEM_SOLD_OUT":
      return "Một món vừa tạm hết. Giỏ hàng vẫn được giữ để bạn quay lại thực đơn và chọn món khác.";
    case "IDEMPOTENCY_KEY_REUSED":
      return "Giỏ hàng đã thay đổi trong lúc đặt. Vui lòng thử lại.";
    case "TABLE_SESSION_EXPIRED":
    case "UNAUTHENTICATED":
      return "Phiên bàn đã hết hạn. Vui lòng quét lại mã QR.";
    default:
      return error.problem.detail ?? error.problem.title;
  }
}

function formatMoney(amount: number): string {
  return `${amount.toLocaleString("vi-VN")}đ`;
}

const mainStyle: React.CSSProperties = { padding: "1rem", maxWidth: "40rem", margin: "0 auto", minHeight: "100dvh" };
const lineStyle: React.CSSProperties = { border: "1px solid #ddd", borderRadius: "0.65rem", padding: "0.85rem" };
const emptyStyle: React.CSSProperties = { textAlign: "center", padding: "3rem 1rem", border: "1px dashed #aaa", borderRadius: "0.75rem" };
const mutedStyle: React.CSSProperties = { color: "#777", fontSize: "0.88rem", marginTop: "0.15rem" };
const removeButtonStyle: React.CSSProperties = { border: 0, background: "transparent", color: "#b00020", padding: "0.4rem" };
const checkoutButtonStyle: React.CSSProperties = { width: "100%", marginTop: "1rem", padding: "0.85rem", border: 0, borderRadius: "0.55rem", background: "#087f5b", color: "#fff", fontSize: "1rem", fontWeight: 700 };
