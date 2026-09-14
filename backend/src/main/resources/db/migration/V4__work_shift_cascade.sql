-- ═══════════════════════════════════════════════════════════════════════════
--  QROS — V4: work_shift xoá theo tầng
--  Tham chiếu: FR-AUTH-04, BL-M0-09
--
--  V1 đặt work_shift.user_id/store_id là REFERENCES thường, không CASCADE — khác với user_role,
--  refresh_token, mfa_backup_code (đều CASCADE khi xoá app_user). Từ BL-M0-09, work_shift bắt đầu
--  có dữ liệu thật (bộ tự đóng ca), và một tài khoản còn ca làm gắn vào không xoá được — đây không
--  phải hành vi mong muốn cho một bảng lịch sử ca làm. Đổi lại đúng chuẩn CASCADE như các bảng con
--  khác của app_user.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE work_shift
    DROP CONSTRAINT work_shift_user_id_fkey,
    ADD CONSTRAINT work_shift_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;

ALTER TABLE work_shift
    DROP CONSTRAINT work_shift_store_id_fkey,
    ADD CONSTRAINT work_shift_store_id_fkey FOREIGN KEY (store_id) REFERENCES store(id) ON DELETE CASCADE;
