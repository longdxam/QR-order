"use client";

import { useEffect, useMemo, useState } from "react";

import { ApiProblemError, getMenu, type Menu, type MenuItem } from "@/lib/api";
import { loadGuestSession } from "@/lib/guestSession";
import { khopTimKiem } from "@/lib/vietnameseSearch";

const NHAN_DI_UNG: Record<string, string> = {
  MILK: "Sữa",
  PEANUT: "Đậu phộng",
  GLUTEN: "Gluten",
  SOY: "Đậu nành",
  EGG: "Trứng",
  NUTS: "Hạt",
};

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

  useEffect(() => {
    let huy = false;
    async function tai() {
      const phien = loadGuestSession();
      if (!phien) return;
      try {
        const ketQua = await getMenu(phien.accessToken);
        if (huy || !ketQua) return;
        setTrangThai({ buoc: "xong", menu: ketQua.menu });
      } catch (error) {
        if (huy) return;
        setTrangThai({
          buoc: "loi",
          thongDiep: error instanceof ApiProblemError ? (error.problem.detail ?? error.problem.title)
              : "Không tải được thực đơn, vui lòng thử lại.",
        });
      }
    }
    void tai();
    return () => {
      huy = true;
    };
  }, []);

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

      {monDangXem && <ChiTietMon mon={monDangXem} dong={() => setMonDangXem(null)} />}
    </main>
  );
}

function ChiTietMon({ mon, dong }: { mon: MenuItem; dong: () => void }) {
  return (
    <div
      onClick={dong}
      style={{
        position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)",
        display: "flex", alignItems: "flex-end",
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{ background: "light-dark(#fff,#222)", width: "100%", padding: "1.5rem", borderRadius: "1rem 1rem 0 0", maxHeight: "80vh", overflowY: "auto" }}
      >
        <h2 style={{ marginTop: 0 }}>{mon.name}</h2>
        {mon.description && <p style={{ color: "#666" }}>{mon.description}</p>}
        {!mon.available && <p style={{ color: "#c00" }}>Tạm hết — quay lại sau nhé.</p>}
        {mon.allergens && mon.allergens.length > 0 && (
          <p>⚠ Dị ứng: {mon.allergens.map((a) => NHAN_DI_UNG[a] ?? a).join(", ")}</p>
        )}
        <h3 style={{ fontSize: "0.9rem" }}>Kích cỡ</h3>
        <ul style={{ listStyle: "none", padding: 0 }}>
          {mon.variants.map((v) => (
            <li key={v.id} style={{ display: "flex", justifyContent: "space-between", padding: "0.4rem 0", opacity: v.available === false ? 0.5 : 1 }}>
              <span>{v.name}{v.available === false ? " (tạm hết)" : ""}</span>
              <span>{formatTien(v.price.amount)}</span>
            </li>
          ))}
        </ul>
        {mon.optionGroups?.map((nhom) => (
          <div key={nhom.id} style={{ marginTop: "0.75rem" }}>
            <h3 style={{ fontSize: "0.9rem" }}>{nhom.name}</h3>
            <ul style={{ listStyle: "none", padding: 0 }}>
              {nhom.options.map((o) => (
                <li key={o.id} style={{ display: "flex", justifyContent: "space-between", padding: "0.3rem 0", opacity: o.available === false ? 0.5 : 1 }}>
                  <span>{o.name}</span>
                  <span>{o.surcharge.amount > 0 ? `+${formatTien(o.surcharge.amount)}` : ""}</span>
                </li>
              ))}
            </ul>
          </div>
        ))}
        <p style={{ color: "#666", marginTop: "1rem" }}>Đặt món đang được xây dựng, quay lại sau nhé.</p>
        <button onClick={dong} style={{ marginTop: "0.5rem", padding: "0.6rem 1rem" }}>Đóng</button>
      </div>
    </div>
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
