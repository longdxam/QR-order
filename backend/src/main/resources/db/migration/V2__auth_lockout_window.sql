-- ═══════════════════════════════════════════════════════════════════════════
--  QROS — V2: cửa sổ khoá tài khoản luỹ tiến
--  Tham chiếu: FR-AUTH-03, TM-AUTH-01, BL-M0-08
--
--  V1 đã có failed_attempts và locked_until nhưng thiếu chỗ lưu "lần sai gần nhất xảy ra khi
--  nào" và "đã từng bị khoá bao nhiêu lần" — hai dữ kiện tách biệt cần cho đúng hai vế của
--  FR-AUTH-03:
--
--   1. "5 lần sai TRONG 15 PHÚT" — chỉ đếm dồn các lần sai liên tiếp trong một cửa sổ 15 phút.
--      last_failed_at cho biết lần sai trước cách hiện tại bao lâu, để quyết định có tính dồn
--      tiếp hay bắt đầu lại từ 1.
--   2. "Thời gian khoá TĂNG LUỸ TIẾN" — không được reset chỉ vì 15 phút đã trôi qua kể từ lần
--      khoá trước (khoá lần đầu thường đã dài hơn 15 phút), nếu không sẽ không bao giờ luỹ tiến.
--      lockout_count đếm số lần đã khoá và chỉ về 0 khi đăng nhập thành công.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE app_user
    ADD COLUMN last_failed_at timestamptz,
    ADD COLUMN lockout_count  int NOT NULL DEFAULT 0;
