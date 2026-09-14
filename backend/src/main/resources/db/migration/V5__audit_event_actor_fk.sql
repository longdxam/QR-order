-- ═══════════════════════════════════════════════════════════════════════════
--  QROS — V5: audit_event.actor_id sống sót qua việc xoá tài khoản
--  Tham chiếu: TM-REP-01, BL-M0-10
--
--  V1 đặt actor_id REFERENCES app_user(id) không CASCADE, nên xoá một tài khoản còn để lại dấu
--  vết audit sẽ bị chặn bởi FK — sai hướng cho một bảng vốn phải "chỉ ghi thêm": xoá tài khoản
--  không được phép xoá luôn bằng chứng của những gì tài khoản đó đã làm. Khác với work_shift
--  (V4, CASCADE hợp lý vì ca làm không có giá trị độc lập với tài khoản), ở đây dùng SET NULL —
--  dòng audit tồn tại mãi, chỉ mất tham chiếu actor cụ thể; actor_role vẫn còn vì đó là ảnh chụp
--  tại thời điểm hành động, không phải tham chiếu.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE audit_event
    DROP CONSTRAINT audit_event_actor_id_fkey,
    ADD CONSTRAINT audit_event_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES app_user(id) ON DELETE SET NULL;
