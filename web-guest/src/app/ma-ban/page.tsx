"use client";

import { useState, type FormEvent } from "react";

import { ApiProblemError, startTableSessionByCode, type TableSession } from "@/lib/api";
import { getOrCreateDeviceId } from "@/lib/device";
import { saveGuestSession } from "@/lib/guestSession";

/** {@code FR-CUS-02}: đường dự phòng khi camera không quét được QR — nhập mã bàn 6 ký tự in kèm. */
export default function TrangNhapMaBan() {
  const [maBan, setMaBan] = useState("");
  const [dangGui, setDangGui] = useState(false);
  const [loi, setLoi] = useState<string | null>(null);
  const [phien, setPhien] = useState<TableSession | null>(null);

  async function guiMa(event: FormEvent) {
    event.preventDefault();
    setDangGui(true);
    setLoi(null);
    try {
      const deviceId = getOrCreateDeviceId();
      const ketQua = await startTableSessionByCode({ tableCode: maBan.toUpperCase(), deviceId });
      saveGuestSession(ketQua);
      setPhien(ketQua);
    } catch (error) {
      setLoi(
        error instanceof ApiProblemError
          ? (error.problem.detail ?? error.problem.title)
          : "Không kết nối được tới máy chủ.",
      );
    } finally {
      setDangGui(false);
    }
  }

  if (phien) {
    return (
      <main style={khungChinh}>
        <h1 style={{ fontSize: "1.1rem" }}>
          Đã vào bàn {phien.tableLabel}
          {phien.storeName ? ` — ${phien.storeName}` : ""}
        </h1>
        <a href="/menu" style={{ color: "#0a5", textDecoration: "underline" }}>
          Xem thực đơn →
        </a>
      </main>
    );
  }

  return (
    <main style={khungChinh}>
      <h1 style={{ fontSize: "1.1rem" }}>Nhập mã bàn</h1>
      <p style={{ color: "#666" }}>Mã 6 ký tự in ngay dưới mã QR trên bàn.</p>
      <form onSubmit={guiMa} style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
        <input
          value={maBan}
          onChange={(event) => setMaBan(event.target.value.toUpperCase())}
          maxLength={6}
          pattern="[A-Za-z0-9]{6}"
          required
          autoFocus
          placeholder="VD: A1B2C3"
          style={{
            fontSize: "1.5rem",
            letterSpacing: "0.3em",
            textAlign: "center",
            padding: "0.75rem",
            textTransform: "uppercase",
          }}
        />
        <button type="submit" disabled={dangGui || maBan.length !== 6} style={{ padding: "0.75rem", fontSize: "1rem" }}>
          {dangGui ? "Đang mở bàn…" : "Vào bàn"}
        </button>
      </form>
      {loi && <p style={{ color: "#c00" }}>{loi}</p>}
    </main>
  );
}

const khungChinh: React.CSSProperties = {
  minHeight: "100dvh",
  display: "flex",
  flexDirection: "column",
  justifyContent: "center",
  padding: "1.5rem",
  maxWidth: "24rem",
  margin: "0 auto",
  gap: "1rem",
};
