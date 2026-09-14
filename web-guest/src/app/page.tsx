/**
 * Trang gốc hiếm khi có người ghé thật — khách luôn tới qua liên kết QR ({@code /t/[qrToken]})
 * hoặc trang nhập mã. Chỉ để lại lối thoát cho trường hợp gõ nhầm URL.
 */
export default function TrangGoc() {
  return (
    <main
      style={{
        minHeight: "100dvh",
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        alignItems: "center",
        gap: "1rem",
        padding: "1.5rem",
        textAlign: "center",
      }}
    >
      <h1 style={{ fontSize: "1.1rem" }}>QROS</h1>
      <p>Quét mã QR trên bàn để bắt đầu đặt món.</p>
      <a href="/ma-ban" style={{ color: "#0a5", textDecoration: "underline" }}>
        Không quét được? Nhập mã bàn
      </a>
    </main>
  );
}
