-- ═══════════════════════════════════════════════════════════════════════════
--  QROS — V3: MFA, refresh token rotation, ca làm
--  Tham chiếu: FR-AUTH-02, FR-AUTH-04, FR-AUTH-05, TM-AUTH-03, BL-M0-09
-- ═══════════════════════════════════════════════════════════════════════════

-- Mã dự phòng dùng một lần cho MFA (FR-AUTH-02). Chỉ lưu hash, không lưu mã gốc — cùng nguyên
-- tắc với password_hash. used_at null = còn dùng được; đặt một lần rồi không bao giờ xoá lại.
CREATE TABLE mfa_backup_code (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    code_hash  text NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_mfa_backup_code_user ON mfa_backup_code (user_id);

-- Refresh token xoay vòng có phát hiện tái sử dụng (TM-AUTH-03). Đây là bảng riêng, không phải
-- JWT: token refresh là chuỗi ngẫu nhiên đối chiếu CSDL, vì phải thu hồi được ngay lập tức —
-- JWT ký sẵn không thu hồi được trước khi hết hạn.
--
-- ⚠ family_id là chỗ hiện thực "thu hồi cả chuỗi": mọi token sinh ra từ cùng một lượt đăng nhập
-- chia sẻ một family_id; phát hiện dùng lại một token đã tiêu (used_at hoặc revoked_at khác null)
-- thu hồi toàn bộ hàng có cùng family_id, không chỉ token đó.
CREATE TABLE refresh_token (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    family_id  uuid NOT NULL,
    token_hash text NOT NULL,
    issued_at  timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    revoked_at timestamptz
);
CREATE UNIQUE INDEX uq_refresh_token_hash ON refresh_token (token_hash);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);

-- work_shift của V1 đã có; chỉ thêm chỉ mục phục vụ đúng câu truy vấn của bộ tự đóng ca 12 giờ
-- (FR-AUTH-04) — quét các ca còn mở theo opened_at.
CREATE INDEX ix_work_shift_open_opened_at ON work_shift (opened_at) WHERE closed_at IS NULL;
