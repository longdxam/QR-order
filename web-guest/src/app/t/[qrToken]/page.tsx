"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";

import { ApiProblemError, startTableSession, type TableSession } from "@/lib/api";
import { getOrCreateDeviceId } from "@/lib/device";
import { saveGuestSession } from "@/lib/guestSession";

type TrangThai =
  | { buoc: "dangXacMinh" }
  | { buoc: "thanhCong"; phien: TableSession }
  | { buoc: "loi"; thongDiep: string; ma: string | undefined };

/**
 * {@code FR-CUS-01}: quét QR trên mặt bàn mở thẳng trang này — không yêu cầu cài app hay đăng ký.
 * Tự động đổi {@code qrToken} lấy phiên bàn ngay khi trang tải xong, không cần khách bấm gì thêm.
 */
export default function TrangQuetQr() {
  const params = useParams<{ qrToken: string }>();
  const [trangThai, setTrangThai] = useState<TrangThai>({ buoc: "dangXacMinh" });

  useEffect(() => {
    let huy = false;

    async function xacMinh() {
      try {
        const deviceId = getOrCreateDeviceId();
        const phien = await startTableSession({ qrToken: params.qrToken, deviceId });
        if (huy) return;
        saveGuestSession(phien);
        setTrangThai({ buoc: "thanhCong", phien });
      } catch (error) {
        if (huy) return;
        if (error instanceof ApiProblemError) {
          setTrangThai({
            buoc: "loi",
            thongDiep: error.problem.detail ?? error.problem.title,
            ma: error.problem.code,
          });
        } else {
          setTrangThai({ buoc: "loi", thongDiep: "Không kết nối được tới máy chủ, vui lòng thử lại.", ma: undefined });
        }
      }
    }

    void xacMinh();
    return () => {
      huy = true;
    };
  }, [params.qrToken]);

  if (trangThai.buoc === "dangXacMinh") {
    return <TrangThaiDangTai />;
  }

  if (trangThai.buoc === "loi") {
    return <TrangThaiLoi thongDiep={trangThai.thongDiep} ma={trangThai.ma} />;
  }

  return <TrangThaiThanhCong phien={trangThai.phien} />;
}

function TrangThaiDangTai() {
  return (
    <main style={khungChinh}>
      <p>Đang xác minh mã QR…</p>
    </main>
  );
}

function TrangThaiLoi({ thongDiep, ma }: { thongDiep: string; ma: string | undefined }) {
  const goiYThem = goiYTheoMa(ma);
  return (
    <main style={khungChinh}>
      <h1 style={{ fontSize: "1.1rem" }}>Không mở được bàn</h1>
      <p>{thongDiep}</p>
      {goiYThem && <p style={{ color: "#666" }}>{goiYThem}</p>}
      <a href="/ma-ban" style={lienKet}>
        Nhập mã bàn thay vào đó
      </a>
    </main>
  );
}

function goiYTheoMa(ma: string | undefined): string | null {
  switch (ma) {
    case "STORE_CLOSED":
      return "Vui lòng quay lại trong giờ mở cửa của quán.";
    case "TABLE_SESSION_CONFLICT":
      return "Bàn đang có hoá đơn chưa đóng — vui lòng gọi nhân viên hỗ trợ.";
    case "QR_OTP_EXPIRED":
      return "Mã QR đổi liên tục để chống chụp lại — vui lòng quét ngay mã hiện có trên bàn.";
    case "RATE_LIMITED":
      return "Vui lòng thử lại sau ít phút.";
    default:
      return null;
  }
}

function TrangThaiThanhCong({ phien }: { phien: TableSession }) {
  return (
    <main style={khungChinh}>
      <h1 style={{ fontSize: "1.1rem" }}>
        Đã vào bàn {phien.tableLabel}
        {phien.storeName ? ` — ${phien.storeName}` : ""}
      </h1>
      {phien.joined && <p>Bạn vừa tham gia cùng những người đang ngồi ở bàn này.</p>}
      {phien.participants && phien.participants.length > 1 && (
        <p style={{ color: "#666" }}>
          {phien.participants.length} thiết bị đang cùng bàn.
        </p>
      )}
      <a href="/menu" style={{ ...lienKet, display: "inline-block", marginTop: "1.5rem" }}>
        Xem thực đơn →
      </a>
    </main>
  );
}

const khungChinh: React.CSSProperties = {
  minHeight: "100dvh",
  display: "flex",
  flexDirection: "column",
  justifyContent: "center",
  gap: "0.75rem",
  padding: "1.5rem",
  maxWidth: "28rem",
  margin: "0 auto",
};

const lienKet: React.CSSProperties = {
  marginTop: "1rem",
  color: "#0a5",
  textDecoration: "underline",
};
