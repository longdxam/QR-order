"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

import { AddToCartDialog } from "@/components/AddToCartDialog";
import { ApiProblemError, getMenu, type Menu, type MenuItem } from "@/lib/api";
import { loadCart, subscribeCartChanged } from "@/lib/cart";
import { loadGuestSession, type TableSession } from "@/lib/guestSession";
import { khopTimKiem } from "@/lib/vietnameseSearch";

const NHAN_THUOC_TINH: Record<string, string> = {
  DAIRY_FREE: "Không sữa",
  CAFFEINE_FREE: "Không caffeine",
  LOW_SUGAR: "Ít ngọt",
  VEGAN: "Thuần chay",
  HOT: "Nóng",
  COLD: "Lạnh",
};

type TrangThai =
  | { buoc: "dangTai" }
  | { buoc: "loi"; thongDiep: string }
  | { buoc: "xong"; menu: Menu };

/** {@code FR-CUS-03}, {@code FR-CUS-04}: thực đơn đọc, tìm kiếm/lọc hoàn toàn phía client. */
export default function TrangThucDon() {
  const [trangThai, setTrangThai] = useState<TrangThai>({ buoc: "dangTai" });
  const [tuKhoa, setTuKhoa] = useState("");
  const [thuocTinhLoc, setThuocTinhLoc] = useState<string | null>(null);
  const [monDangXem, setMonDangXem] = useState<MenuItem | null>(null);
  const [phien, setPhien] = useState<TableSession | null>(null);
  const [soMonTrongGio, setSoMonTrongGio] = useState(0);

  useEffect(() => {
    let huy = false;
    let dangTai = false;
    let daCoMenu = false;
    let etag: string | undefined;
    async function tai() {
      if (dangTai) return;
      const phien = loadGuestSession();
      if (!phien) return;
      dangTai = true;
      setPhien(phien);
      try {
        const ketQua = await getMenu(phien.accessToken, etag);
        if (huy || !ketQua) return;
        etag = ketQua.etag ?? undefined;
        daCoMenu = true;
        setTrangThai({ buoc: "xong", menu: ketQua.menu });
      } catch (error) {
        if (huy || daCoMenu) return;
        setTrangThai({
          buoc: "loi",
          thongDiep: error instanceof ApiProblemError ? (error.problem.detail ?? error.problem.title)
              : "Không tải được thực đơn, vui lòng thử lại.",
        });
      } finally {
        dangTai = false;
      }
    }
    void tai();
    // FR-BAR-05: ETag giữ payload rỗng khi không đổi; nhịp một giây bảo đảm món chuyển
    // sang "Tạm hết" trong cửa sổ hai giây mà không cần đưa guest JWT vào URL WebSocket.
    const timer = window.setInterval(() => void tai(), 1_000);
    return () => {
      huy = true;
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    if (!phien) return;
    let active = true;
    const refresh = () => {
      void loadCart(phien.sessionId).then((cart) => {
        if (active) setSoMonTrongGio(cart.lines.reduce((sum, line) => sum + line.quantity, 0));
      });
    };
    refresh();
    const unsubscribe = subscribeCartChanged(phien.sessionId, refresh);
    return () => {
      active = false;
      unsubscribe();
    };
  }, [phien]);

  const tatCaThuocTinh = useMemo(() => {
    if (trangThai.buoc !== "xong") return [];
    const bo = new Set<string>();
    trangThai.menu.categories.forEach((c) => c.items.forEach((i) => i.attributes?.forEach((a) => bo.add(a))));
    return [...bo];
  }, [trangThai]);

  if (trangThai.buoc === "dangTai") {
    return <main style={khungChinh}><p>Đang tải thực đơn…</p></main>;
  }
  if (trangThai.buoc === "loi") {
    return <main style={khungChinh}><p style={{ color: "#c00" }}>{trangThai.thongDiep}</p></main>;
  }

  const danhMucLoc = trangThai.menu.categories
    .map((danhMuc) => ({
      ...danhMuc,
      items: danhMuc.items.filter((mon) =>
        khopTimKiem(mon.name, tuKhoa) && (!thuocTinhLoc || mon.attributes?.includes(thuocTinhLoc as never))),
    }))
    .filter((danhMuc) => danhMuc.items.length > 0);

  return (
    <main style={{ padding: "1rem", maxWidth: "40rem", margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.8rem" }}>
        <h1 style={{ fontSize: "1.25rem", margin: 0 }}>Thực đơn</h1>
        <Link href="/cart" style={{ color: "inherit", fontWeight: 650 }}>Giỏ hàng ({soMonTrongGio})</Link>
      </div>
      <input
        value={tuKhoa}
        onChange={(e) => setTuKhoa(e.target.value)}
        placeholder="Tìm món (không cần gõ dấu)…"
        style={{ width: "100%", padding: "0.6rem", fontSize: "1rem", marginBottom: "0.75rem" }}
      />
      {tatCaThuocTinh.length > 0 && (
        <div style={{ display: "flex", gap: "0.4rem", flexWrap: "wrap", marginBottom: "1rem" }}>
          {tatCaThuocTinh.map((a) => (
            <button
              key={a}
              onClick={() => setThuocTinhLoc(thuocTinhLoc === a ? null : a)}
              style={{
                padding: "0.3rem 0.6rem",
                borderRadius: "999px",
                border: "1px solid #999",
                background: thuocTinhLoc === a ? "#0a5" : "transparent",
                color: thuocTinhLoc === a ? "#fff" : "inherit",
              }}
            >
              {NHAN_THUOC_TINH[a] ?? a}
            </button>
          ))}
        </div>
      )}

      {danhMucLoc.length === 0 && <p style={{ color: "#666" }}>Không tìm thấy món phù hợp.</p>}

      {danhMucLoc.map((danhMuc) => (
        <section key={danhMuc.id} style={{ marginBottom: "1.5rem" }}>
          <h2 style={{ fontSize: "1rem", marginBottom: "0.5rem" }}>{danhMuc.name}</h2>
          <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
            {danhMuc.items.map((mon) => (
              <button
                key={mon.id}
                disabled={!mon.available}
                aria-label={mon.available ? `Chọn ${mon.name}` : `${mon.name} tạm hết`}
                onClick={() => setMonDangXem(mon)}
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  padding: "0.75rem",
                  border: "1px solid #ddd",
                  borderRadius: "0.5rem",
                  textAlign: "left",
                  opacity: mon.available ? 1 : 0.5,
                  background: "transparent",
                }}
              >
                <span>
                  <div>{mon.name}</div>
                  {!mon.available && <div style={{ fontSize: "0.8rem", color: "#c00" }}>Tạm hết</div>}
                </span>
                <span>{formatTien(mon.basePrice.amount)}</span>
              </button>
            ))}
          </div>
        </section>
      ))}

      {monDangXem && phien && (
        <AddToCartDialog
          sessionId={phien.sessionId}
          addedBy={phien.participants?.find((participant) => participant.isSelf)?.nickname}
          item={monDangXem}
          onClose={() => setMonDangXem(null)}
          onAdded={() => setMonDangXem(null)}
        />
      )}
    </main>
  );
}

function formatTien(amountVnd: number): string {
  return amountVnd.toLocaleString("vi-VN") + "đ";
}

const khungChinh: React.CSSProperties = {
  minHeight: "100dvh",
  display: "flex",
  flexDirection: "column",
  justifyContent: "center",
  padding: "1.5rem",
  maxWidth: "28rem",
  margin: "0 auto",
};
