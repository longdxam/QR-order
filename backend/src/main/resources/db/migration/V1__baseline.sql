-- ═══════════════════════════════════════════════════════════════════════════
--  QROS — V1 baseline
--  Tham chiếu: docs/PRD.md v1.1 · docs/api/openapi.yaml · CLAUDE.md
--
--  Ba quy ước áp dụng xuyên suốt:
--
--   1. Tiền luôn là BIGINT đơn vị đồng. Không NUMERIC, không FLOAT, không đâu cả.
--   2. Khoá chính là UUID sinh phía ứng dụng theo chuẩn v7 — có thứ tự thời gian nên
--      thân thiện B-tree, đồng thời không đoán được nên chặn IDOR (OWASP A01).
--      gen_random_uuid() chỉ là lưới an toàn cho seed và test.
--   3. Trạng thái dùng TEXT + CHECK thay vì ENUM gốc. Thêm giá trị mới chỉ là sửa
--      CHECK trong một migration, không phải ALTER TYPE có khoá bảng.
--
--  Các ràng buộc đánh dấu ⚠ là nơi một quyết định trong PRD được cưỡng chế ở tầng
--  CSDL thay vì trông chờ vào kỷ luật của tầng ứng dụng. Không được nới lỏng.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- tìm kiếm thực đơn không dấu, FR-CUS-04
CREATE EXTENSION IF NOT EXISTS citext;    -- email không phân biệt hoa thường
CREATE EXTENSION IF NOT EXISTS unaccent;  -- bỏ dấu tiếng Việt khi tìm kiếm

-- ───────────────────────────────────────────────────────────────────────────
--  1. CHI NHÁNH, KHU VỰC, BÀN
-- ───────────────────────────────────────────────────────────────────────────

CREATE TABLE store (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                  text NOT NULL UNIQUE,
    name                  text NOT NULL,
    timezone              text NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    -- Cổng chặn lớp 3 mục 5.3.2: QR chụp trộm không dùng được ngoài giờ mở cửa.
    opens_at              time NOT NULL DEFAULT '07:00',
    closes_at             time NOT NULL DEFAULT '23:00',
    -- Hạn mức nghiệp vụ, lớp 7 mục 5.3.2.
    max_unpaid_amount     bigint NOT NULL DEFAULT 2000000,
    prepay_threshold      bigint NOT NULL DEFAULT 500000,   -- EC-07
    -- FR-AI-13. NULL = chưa đặt trần.
    ai_daily_budget_usd   numeric(10,2),
    ai_monthly_budget_usd numeric(10,2),
    active                boolean NOT NULL DEFAULT true,
    created_at            timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE zone (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id      uuid NOT NULL REFERENCES store(id),
    name          text NOT NULL,
    display_order int  NOT NULL DEFAULT 0,
    UNIQUE (store_id, name)
);

CREATE TABLE restaurant_table (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id            uuid NOT NULL REFERENCES store(id),
    zone_id             uuid REFERENCES zone(id),
    label               text NOT NULL,
    -- Mã 6 ký tự in dưới QR, đường dự phòng khi camera không quét được (FR-CUS-02).
    short_code          char(6) NOT NULL,
    status              text NOT NULL DEFAULT 'AVAILABLE'
                        CHECK (status IN ('AVAILABLE','OCCUPIED','AWAITING_PAYMENT','CLEANING','DISABLED')),
    -- Lớp 4 mục 5.3.2 — QR xoay vòng kiểu TOTP, chống chụp ảnh mang về nhà.
    rotating_qr_enabled boolean NOT NULL DEFAULT false,
    totp_secret_ref     text,
    version             int NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (store_id, label),
    UNIQUE (store_id, short_code),
    CONSTRAINT totp_ref_present_when_enabled
        CHECK (NOT rotating_qr_enabled OR totp_secret_ref IS NOT NULL)
);

-- Giữ song song khoá hiện tại và khoá liền trước để xoay vòng không làm chết
-- mã QR đã in ra (NFR-SEC-07). Khoá riêng nằm ở Vault, đây chỉ giữ tham chiếu.
CREATE TABLE qr_signing_key (
    kid             text PRIMARY KEY,
    store_id        uuid NOT NULL REFERENCES store(id),
    public_key      bytea NOT NULL,
    private_key_ref text NOT NULL,
    status          text NOT NULL CHECK (status IN ('ACTIVE','PREVIOUS','RETIRED')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    retired_at      timestamptz
);
CREATE UNIQUE INDEX uq_qr_key_active_per_store
    ON qr_signing_key (store_id) WHERE status = 'ACTIVE';

-- ───────────────────────────────────────────────────────────────────────────
--  2. PHIÊN BÀN
-- ───────────────────────────────────────────────────────────────────────────

CREATE TABLE table_session (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id         uuid NOT NULL REFERENCES store(id),
    table_id         uuid NOT NULL REFERENCES restaurant_table(id),
    status           text NOT NULL DEFAULT 'OPEN'
                     CHECK (status IN ('OPEN','CLOSED','EXPIRED')),
    opened_at        timestamptz NOT NULL DEFAULT now(),
    last_activity_at timestamptz NOT NULL DEFAULT now(),
    expires_at       timestamptz NOT NULL,
    closed_at        timestamptz,
    -- EC-07: đóng bàn còn dư nợ bắt buộc có lý do, và lý do vào báo cáo thất thoát.
    close_reason     text CHECK (close_reason IN
                       ('PAID','PAID_CASH_UNRECORDED','GUEST_LEFT_UNPAID','SYSTEM_ERROR','OTHER')),
    closed_by        uuid,
    -- EC-03: đơn đầu của phiên lạ chờ nhân viên xác nhận, trừ khi thu ngân đã mở bàn.
    staff_opened     boolean NOT NULL DEFAULT false,
    risk_flags       text[] NOT NULL DEFAULT '{}',
    version          int NOT NULL DEFAULT 0,
    CONSTRAINT close_reason_required_when_closed
        CHECK (status <> 'CLOSED' OR close_reason IS NOT NULL)
);

-- ⚠ EC-02: một bàn chỉ được có tối đa MỘT phiên đang mở.
-- Đây là thứ chặn nhóm khách mới âm thầm chiếm phiên của nhóm trước.
CREATE UNIQUE INDEX uq_open_session_per_table
    ON table_session (table_id) WHERE status = 'OPEN';

CREATE INDEX ix_session_store_status
    ON table_session (store_id, status, last_activity_at);

-- deviceId do client sinh và tự lưu. KHÔNG phải dữ liệu cá nhân.
CREATE TABLE session_device (
    session_id      uuid NOT NULL REFERENCES table_session(id) ON DELETE CASCADE,
    device_id       uuid NOT NULL,
    nickname        text,
    user_agent_hash text,
    joined_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, device_id)
);

CREATE TABLE staff_call (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id uuid NOT NULL REFERENCES table_session(id),
    reason     text NOT NULL CHECK (reason IN
                 ('REFILL_WATER','CLEAN_TABLE','PAYMENT_HELP','OTHER')),
    note       text,
    created_at timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz
);

-- ───────────────────────────────────────────────────────────────────────────
--  3. NHÂN SỰ VÀ PHÂN QUYỀN
-- ───────────────────────────────────────────────────────────────────────────

CREATE TABLE app_user (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email           citext,
    -- Argon2id (FR-AUTH-01). Chuỗi mã hoá đầy đủ, gồm cả tham số m/t/p.
    password_hash   text,
    display_name    text NOT NULL,
    pin_hash        text,        -- PIN 6 số mở ca, FR-AUTH-04
    mfa_secret_ref  text,        -- tham chiếu Vault, không phải bí mật thật
    mfa_enabled     boolean NOT NULL DEFAULT false,
    -- NFR-SEC-06: tăng giá trị này để vô hiệu hoá hàng loạt token đang sống.
    token_version   int NOT NULL DEFAULT 0,
    failed_attempts int NOT NULL DEFAULT 0,
    locked_until    timestamptz,
    active          boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_user_email ON app_user (email) WHERE email IS NOT NULL;

CREATE TABLE role (
    id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE,
    name text NOT NULL
);

-- Quyền là chuỗi; mã nguồn chỉ kiểm tra quyền chứ không kiểm tra tên vai trò.
-- Nhờ vậy tạo vai trò tuỳ biến ("Ca trưởng") không phải sửa code — PRD mục 2.1.
CREATE TABLE role_permission (
    role_id    uuid NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission text NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE TABLE user_role (
    id       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id  uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id  uuid NOT NULL REFERENCES role(id),
    store_id uuid REFERENCES store(id)     -- NULL = áp dụng toàn tổ chức
);
-- PRIMARY KEY không nhận biểu thức, nên tính duy nhất phải nằm ở unique index.
-- COALESCE để hàng có store_id NULL không lách được ràng buộc.
CREATE UNIQUE INDEX uq_user_role_scope ON user_role
    (user_id, role_id, COALESCE(store_id, '00000000-0000-0000-0000-000000000000'::uuid));

CREATE TABLE work_shift (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES app_user(id),
    store_id      uuid NOT NULL REFERENCES store(id),
    opened_at     timestamptz NOT NULL DEFAULT now(),
    closed_at     timestamptz,
    -- FR-PAY-08: chốt ca đối chiếu tiền thực đếm với tiền hệ thống ghi nhận.
    cash_expected bigint,
    cash_counted  bigint,
    cash_variance bigint GENERATED ALWAYS AS (cash_counted - cash_expected) STORED
);
CREATE UNIQUE INDEX uq_open_shift_per_user
    ON work_shift (user_id) WHERE closed_at IS NULL;

-- ───────────────────────────────────────────────────────────────────────────
--  4. THỰC ĐƠN
-- ───────────────────────────────────────────────────────────────────────────

CREATE TABLE category (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id      uuid NOT NULL REFERENCES store(id),
    name          text NOT NULL,
    display_order int NOT NULL DEFAULT 0,
    active        boolean NOT NULL DEFAULT true
);

CREATE TABLE menu_item (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id          uuid NOT NULL REFERENCES store(id),
    category_id       uuid NOT NULL REFERENCES category(id),
    name              text NOT NULL,
    description       text,
    image_url         text,
    -- Sai ở cột này là rủi ro sức khoẻ, không phải lỗi hiển thị (FR-CUS-03).
    allergens         text[] NOT NULL DEFAULT '{}',
    attributes        text[] NOT NULL DEFAULT '{}',
    station           text NOT NULL DEFAULT 'COFFEE'
                      CHECK (station IN ('COFFEE','TEA','FOOD')),
    published         boolean NOT NULL DEFAULT false,
    manually_disabled boolean NOT NULL DEFAULT false,
    display_order     int NOT NULL DEFAULT 0,
    version           int NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_menu_item_store_published
    ON menu_item (store_id, published, display_order);

-- Tìm kiếm mờ theo tên món. LƯU Ý: unaccent() được đánh dấu STABLE chứ không phải
-- IMMUTABLE, nên KHÔNG dùng thẳng trong index được. Muốn tìm kiếm không dấu
-- (FR-CUS-04) thì V2 phải thêm một hàm bọc IMMUTABLE rồi đánh index theo nó:
--     CREATE FUNCTION immutable_unaccent(text) RETURNS text
--         LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
--         AS $$ SELECT public.unaccent('public.unaccent', $1) $$;
-- Ở V1 chỉ đánh index trên tên gốc; tìm không dấu tạm xử lý ở tầng ứng dụng.
CREATE INDEX ix_menu_item_name_trgm
    ON menu_item USING gin (name gin_trgm_ops);

CREATE TABLE menu_variant (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_item_id  uuid NOT NULL REFERENCES menu_item(id) ON DELETE CASCADE,
    name          text NOT NULL,
    price_amount  bigint NOT NULL CHECK (price_amount >= 0),
    display_order int NOT NULL DEFAULT 0,
    active        boolean NOT NULL DEFAULT true,
    UNIQUE (menu_item_id, name)
);

CREATE TABLE option_group (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id    uuid NOT NULL REFERENCES store(id),
    name        text NOT NULL,
    selection   text NOT NULL CHECK (selection IN ('SINGLE','MULTIPLE')),
    is_required boolean NOT NULL DEFAULT false,
    min_select  int NOT NULL DEFAULT 0,
    max_select  int NOT NULL DEFAULT 1,
    CONSTRAINT select_range_sane CHECK (min_select <= max_select)
);

CREATE TABLE option_choice (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    option_group_id  uuid NOT NULL REFERENCES option_group(id) ON DELETE CASCADE,
    name             text NOT NULL,
    surcharge_amount bigint NOT NULL DEFAULT 0 CHECK (surcharge_amount >= 0),
    display_order    int NOT NULL DEFAULT 0,
    active           boolean NOT NULL DEFAULT true
);

CREATE TABLE menu_item_option_group (
    menu_item_id    uuid NOT NULL REFERENCES menu_item(id) ON DELETE CASCADE,
    option_group_id uuid NOT NULL REFERENCES option_group(id),
    display_order   int NOT NULL DEFAULT 0,
    PRIMARY KEY (menu_item_id, option_group_id)
);

-- FR-MGT-04: đổi giá có hiệu lực tương lai, KHÔNG sửa đè lịch sử.
-- Đơn đã đặt giữ nguyên giá vì giá được sao chép sang order_line lúc đặt.
CREATE TABLE price_schedule (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_variant_id uuid NOT NULL REFERENCES menu_variant(id) ON DELETE CASCADE,
    price_amount    bigint NOT NULL CHECK (price_amount >= 0),
    days_of_week    int[] NOT NULL DEFAULT '{1,2,3,4,5,6,7}',
    starts_at       time,
    ends_at         time,
    effective_from  timestamptz NOT NULL,
    effective_to    timestamptz,
    CONSTRAINT effective_range_sane
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

-- ───────────────────────────────────────────────────────────────────────────
--  5. KHO VÀ ĐỊNH LƯỢNG
-- ───────────────────────────────────────────────────────────────────────────

CREATE TABLE ingredient (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id      uuid NOT NULL REFERENCES store(id),
    name          text NOT NULL,
    unit          text NOT NULL,
    -- Nhóm hạn dùng ngắn (trân châu, sữa tươi, kem cheese) là mục tiêu của FR-AI-05.
    is_perishable boolean NOT NULL DEFAULT false,
    min_stock     numeric(12,3) NOT NULL DEFAULT 0,
    current_stock numeric(12,3) NOT NULL DEFAULT 0,
    sold_out      boolean NOT NULL DEFAULT false,
    version       int NOT NULL DEFAULT 0,
    UNIQUE (store_id, name)
);

-- BOM: mỗi biến thể món khai báo lượng nguyên liệu tiêu hao (FR-MGT-05).
CREATE TABLE recipe_component (
    menu_variant_id uuid NOT NULL REFERENCES menu_variant(id) ON DELETE CASCADE,
    ingredient_id   uuid NOT NULL REFERENCES ingredient(id),
    quantity        numeric(12,3) NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (menu_variant_id, ingredient_id)
);

-- Topping cũng tiêu hao nguyên liệu, nên định lượng phải gắn cả ở tuỳ chọn.
CREATE TABLE option_recipe_component (
    option_choice_id uuid NOT NULL REFERENCES option_choice(id) ON DELETE CASCADE,
    ingredient_id    uuid NOT NULL REFERENCES ingredient(id),
    quantity         numeric(12,3) NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (option_choice_id, ingredient_id)
);

CREATE TABLE stock_movement (
    id            bigserial PRIMARY KEY,
    ingredient_id uuid NOT NULL REFERENCES ingredient(id),
    delta         numeric(12,3) NOT NULL,
    reason        text NOT NULL CHECK (reason IN
                    ('PURCHASE','CONSUMPTION','ADJUSTMENT','WASTE','STOCKTAKE')),
    order_line_id uuid,
    note          text,
    actor_id      uuid REFERENCES app_user(id),
    occurred_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_stock_movement_ingredient
    ON stock_movement (ingredient_id, occurred_at DESC);

-- ───────────────────────────────────────────────────────────────────────────
--  6. ĐƠN HÀNG
-- ───────────────────────────────────────────────────────────────────────────

CREATE TABLE customer_order (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id        uuid NOT NULL REFERENCES store(id),
    table_id        uuid NOT NULL REFERENCES restaurant_table(id),
    session_id      uuid NOT NULL REFERENCES table_session(id),
    short_code      text NOT NULL,
    status          text NOT NULL DEFAULT 'PENDING' CHECK (status IN
                      ('PENDING','CONFIRMED','PREPARING','READY','SERVED',
                       'COMPLETED','CANCELLED','EXPIRED')),
    -- ⚠ ADR-06: ba cột tiền này do MÁY CHỦ tính, không bao giờ nhận từ client.
    subtotal_amount bigint NOT NULL CHECK (subtotal_amount >= 0),
    discount_amount bigint NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    total_amount    bigint NOT NULL CHECK (total_amount >= 0),
    requires_staff_confirmation boolean NOT NULL DEFAULT false,
    placed_at       timestamptz NOT NULL DEFAULT now(),
    completed_at    timestamptz,
    cancel_reason   text,
    version         int NOT NULL DEFAULT 0,
    CONSTRAINT total_is_consistent
        CHECK (total_amount = subtotal_amount - discount_amount),
    CONSTRAINT cancel_reason_required
        CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL)
);

-- Chỉ mục cho hàng đợi KDS và báo cáo — NFR-PERF-10 cấm truy vấn quá 100 ms.
CREATE INDEX ix_order_store_status_placed
    ON customer_order (store_id, status, placed_at);
CREATE INDEX ix_order_session ON customer_order (session_id, placed_at);

-- Mã ngắn ("A04-17") tái sử dụng mỗi ngày nên chỉ duy nhất trong phạm vi một ngày
-- theo giờ địa phương. Múi giờ là hằng số văn bản để biểu thức giữ tính immutable.
CREATE UNIQUE INDEX uq_order_short_code_daily ON customer_order
    (store_id, short_code, ((placed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date));

CREATE TABLE order_line (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        uuid NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
    menu_item_id    uuid NOT NULL REFERENCES menu_item(id),
    menu_variant_id uuid NOT NULL REFERENCES menu_variant(id),
    -- Tên và giá được SAO CHÉP tại thời điểm đặt. Đổi giá hay đổi tên món về sau
    -- không được phép làm thay đổi hoá đơn đã phát hành.
    item_name       text NOT NULL,
    variant_name    text NOT NULL,
    unit_price      bigint NOT NULL CHECK (unit_price >= 0),
    quantity        int NOT NULL CHECK (quantity BETWEEN 1 AND 20),
    line_total      bigint NOT NULL CHECK (line_total >= 0),
    note            text,
    added_by        text,
    station         text NOT NULL,
    status          text NOT NULL DEFAULT 'PENDING' CHECK (status IN
                      ('PENDING','CONFIRMED','PREPARING','READY','SERVED','CANCELLED')),
    started_at      timestamptz,
    ready_at        timestamptz,
    -- FR-BAR-09: nuôi ước lượng thời gian chờ và báo cáo hiệu suất.
    prep_seconds    int GENERATED ALWAYS AS
                      (EXTRACT(EPOCH FROM (ready_at - started_at))::int) STORED,
    -- ⚠ EC-06: khoá lạc quan. Hai barista bấm cùng lúc thì một người nhận 409.
    version         int NOT NULL DEFAULT 0
);
CREATE INDEX ix_order_line_order ON order_line (order_id);
CREATE INDEX ix_order_line_station_status
    ON order_line (station, status) WHERE status IN ('CONFIRMED','PREPARING');

CREATE TABLE order_line_option (
    order_line_id    uuid NOT NULL REFERENCES order_line(id) ON DELETE CASCADE,
    option_choice_id uuid NOT NULL REFERENCES option_choice(id),
    option_name      text NOT NULL,
    surcharge        bigint NOT NULL CHECK (surcharge >= 0),
    PRIMARY KEY (order_line_id, option_choice_id)
);

-- Nhật ký chuyển trạng thái, chỉ ghi thêm. Bằng chứng chống chối bỏ,
-- mục 5.3.1 (Repudiation).
CREATE TABLE order_status_log (
    id            bigserial PRIMARY KEY,
    order_id      uuid NOT NULL REFERENCES customer_order(id),
    order_line_id uuid REFERENCES order_line(id),
    from_status   text,
    to_status     text NOT NULL,
    reason        text,
    actor_id      uuid REFERENCES app_user(id),
    device_id     uuid,
    occurred_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_status_log_order ON order_status_log (order_id, occurred_at);

CREATE TABLE order_feedback (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     uuid NOT NULL REFERENCES customer_order(id),
    order_line_id uuid REFERENCES order_line(id),
    rating       int NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment      text,
    -- FR-AI-06 ghi kết quả phân tích ngược lại đây.
    sentiment    text CHECK (sentiment IN ('POSITIVE','NEUTRAL','NEGATIVE')),
    topics       text[],
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- ───────────────────────────────────────────────────────────────────────────
--  7. THANH TOÁN
-- ───────────────────────────────────────────────────────────────────────────

CREATE TABLE payment_intent (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id           uuid NOT NULL REFERENCES store(id),
    session_id         uuid NOT NULL REFERENCES table_session(id),
    amount             bigint NOT NULL CHECK (amount > 0),
    -- MOCK là adapter bình đẳng với ba cổng thật, không phải hack cho môi trường dev.
    -- Nó là thứ duy nhất ép được kịch bản webhook muộn / webhook trùng trong CI.
    method             text NOT NULL CHECK (method IN
                         ('CASH','VNPAY','MOMO','ZALOPAY','MOCK')),
    status             text NOT NULL DEFAULT 'CREATED' CHECK (status IN
                         ('CREATED','AUTHORIZING','AUTHORIZED','SETTLED',
                          'FAILED','VOIDED','EXPIRED')),
    provider_ref       text,
    reference          text NOT NULL,
    -- EC-01: quá 15 phút không có kết luận thì chuyển EXPIRED và báo thu ngân.
    expires_at         timestamptz NOT NULL,
    -- FR-PAY-06: tác vụ đối soát quét theo cột này.
    last_reconciled_at timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    settled_at         timestamptz,
    version            int NOT NULL DEFAULT 0
);

-- ⚠ EC-08: chặn thanh toán trùng ở tầng CSDL.
-- Một phiên bàn chỉ được có tối đa MỘT ý định ở trạng thái SETTLED. Nếu webhook về
-- sau khi thu ngân đã thu tiền mặt, UPDATE sẽ vi phạm ràng buộc này; tầng ứng dụng
-- bắt lỗi rồi tạo Refund chờ duyệt thay vì im lặng bỏ qua.
CREATE UNIQUE INDEX uq_settled_intent_per_session
    ON payment_intent (session_id) WHERE status = 'SETTLED';

-- Đối soát quét các ý định treo quá 3 phút.
CREATE INDEX ix_intent_pending_reconcile
    ON payment_intent (created_at) WHERE status IN ('CREATED','AUTHORIZING');

CREATE TABLE payment_transaction (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    intent_id       uuid NOT NULL REFERENCES payment_intent(id),
    kind            text NOT NULL CHECK (kind IN ('CHARGE','REFUND')),
    amount          bigint NOT NULL CHECK (amount > 0),
    status          text NOT NULL CHECK (status IN ('PENDING','SUCCEEDED','FAILED')),
    provider_txn_id text,
    -- Tiền mặt: ai thu, ca nào. Bắt buộc cho chốt ca FR-PAY-08.
    actor_id        uuid REFERENCES app_user(id),
    shift_id        uuid REFERENCES work_shift(id),
    tendered        bigint,
    change_given    bigint,
    occurred_at     timestamptz NOT NULL DEFAULT now()
);
-- ⚠ Chống phát lại webhook: cùng một giao dịch của cổng chỉ ghi nhận được một lần.
CREATE UNIQUE INDEX uq_provider_txn
    ON payment_transaction (provider_txn_id) WHERE provider_txn_id IS NOT NULL;

CREATE TABLE refund_request (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    intent_id    uuid NOT NULL REFERENCES payment_intent(id),
    amount       bigint NOT NULL CHECK (amount > 0),
    reason_code  text NOT NULL CHECK (reason_code IN
                   ('DUPLICATE_PAYMENT','ITEM_UNAVAILABLE','CUSTOMER_REQUEST','OTHER')),
    reason_note  text,
    status       text NOT NULL DEFAULT 'PENDING_APPROVAL' CHECK (status IN
                   ('PENDING_APPROVAL','APPROVED','REJECTED','COMPLETED')),
    requested_at timestamptz NOT NULL DEFAULT now(),
    approved_by  uuid REFERENCES app_user(id),
    approved_at  timestamptz
);
-- EC-08: khu vực "Cần xử lý" chặn chốt ca khi còn mục tồn đọng.
CREATE INDEX ix_refund_pending
    ON refund_request (status) WHERE status = 'PENDING_APPROVAL';

-- ───────────────────────────────────────────────────────────────────────────
--  8. IDEMPOTENCY, OUTBOX, AUDIT
-- ───────────────────────────────────────────────────────────────────────────

-- FR-CUS-09: gửi lại cùng khoá trong 24 giờ trả về đúng kết quả cũ.
CREATE TABLE idempotency_key (
    key             uuid PRIMARY KEY,
    scope           text NOT NULL,
    session_id      uuid,
    -- Cùng khoá nhưng khác nội dung là dấu hiệu client lỗi: phải trả 422,
    -- chứ không trả nhầm kết quả của một yêu cầu khác.
    request_hash    text NOT NULL,
    response_status int,
    response_body   jsonb,
    created_at      timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL
);
CREATE INDEX ix_idempotency_expiry ON idempotency_key (expires_at);

-- ⚠ ADR-05: ghi DB và phát sự kiện là nguyên tử.
-- Cột id kiểu bigserial CHÍNH LÀ trường `seq` trong asyncapi.yaml — thứ tự tuyệt đối,
-- không phụ thuộc đồng hồ của bất kỳ máy nào.
CREATE TABLE outbox_event (
    id             bigserial PRIMARY KEY,
    aggregate_type text NOT NULL,
    aggregate_id   uuid NOT NULL,
    store_id       uuid,
    session_id     uuid,
    type           text NOT NULL,
    payload        jsonb NOT NULL,
    trace_id       text,
    occurred_at    timestamptz NOT NULL DEFAULT now(),
    published_at   timestamptz
);
CREATE INDEX ix_outbox_unpublished ON outbox_event (id) WHERE published_at IS NULL;
-- Phát lại sau khi client kết nối lại (kênh /app/resume). Bộ đệm giữ 15 phút.
CREATE INDEX ix_outbox_replay_store   ON outbox_event (store_id, id);
CREATE INDEX ix_outbox_replay_session ON outbox_event (session_id, id);

-- NFR-SEC-13 / OWASP A09: chỉ ghi thêm. Quyền UPDATE và DELETE bị thu hồi ở V2,
-- khi vai trò CSDL của ứng dụng đã được tạo.
CREATE TABLE audit_event (
    id           bigserial PRIMARY KEY,
    actor_id     uuid REFERENCES app_user(id),
    actor_role   text,
    store_id     uuid,
    action       text NOT NULL,
    entity_type  text NOT NULL,
    entity_id    uuid,
    -- Giá trị trước và sau, đã che dữ liệu cá nhân ở tầng ứng dụng.
    before_value jsonb,
    after_value  jsonb,
    reason       text,
    ip_address   inet,
    trace_id     text,
    occurred_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_actor_time ON audit_event (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_entity     ON audit_event (entity_type, entity_id, occurred_at DESC);

-- ───────────────────────────────────────────────────────────────────────────
--  9. AI — chi phí và kết quả của hai job Python
-- ───────────────────────────────────────────────────────────────────────────

-- FR-AI-14: chi phí đo được từ ngày đầu, không gắn thêm sau.
CREATE TABLE ai_usage_record (
    id                       bigserial PRIMARY KEY,
    store_id                 uuid NOT NULL REFERENCES store(id),
    session_id               uuid,
    feature                  text NOT NULL CHECK (feature IN
                               ('ASSISTANT','SENTIMENT','SUMMARY')),
    model                    text NOT NULL,
    input_tokens             int NOT NULL DEFAULT 0,
    cache_read_input_tokens  int NOT NULL DEFAULT 0,
    cache_write_input_tokens int NOT NULL DEFAULT 0,
    output_tokens            int NOT NULL DEFAULT 0,
    cost_usd                 numeric(12,6) NOT NULL DEFAULT 0,
    latency_ms               int,
    inference_geo            text,     -- NFR-AI-07, phục vụ hồ sơ tuân thủ
    occurred_at              timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_usage_store_time ON ai_usage_record (store_id, occurred_at DESC);

-- Kết quả của ml/jobs/recommendations.py, chạy mỗi giờ. FR-AI-01.
-- Backend chỉ ĐỌC bảng này; đây là lý do gợi ý món vẫn sống khi mất mạng ra Internet.
CREATE TABLE item_affinity (
    store_id       uuid NOT NULL REFERENCES store(id),
    menu_item_id   uuid NOT NULL REFERENCES menu_item(id),
    related_item_id uuid NOT NULL REFERENCES menu_item(id),
    score          numeric(6,4) NOT NULL,
    support        int NOT NULL,
    computed_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (store_id, menu_item_id, related_item_id),
    CONSTRAINT no_self_affinity CHECK (menu_item_id <> related_item_id)
);
CREATE INDEX ix_affinity_lookup ON item_affinity (store_id, menu_item_id, score DESC);

-- Dự phòng theo luật khi chưa đủ dữ liệu (FR-AI-02) hoặc khi mô hình suy giảm.
CREATE TABLE popular_item_snapshot (
    store_id     uuid NOT NULL REFERENCES store(id),
    hour_of_day  int NOT NULL CHECK (hour_of_day BETWEEN 0 AND 23),
    menu_item_id uuid NOT NULL REFERENCES menu_item(id),
    rank         int NOT NULL,
    computed_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (store_id, hour_of_day, menu_item_id)
);

-- Kết quả của ml/jobs/forecast.py, chạy 03:00. FR-AI-05.
CREATE TABLE ingredient_forecast (
    store_id        uuid NOT NULL REFERENCES store(id),
    ingredient_id   uuid NOT NULL REFERENCES ingredient(id),
    forecast_date   date NOT NULL,
    predicted_usage numeric(12,3) NOT NULL,
    lower_bound     numeric(12,3),
    upper_bound     numeric(12,3),
    model_version   text NOT NULL,
    computed_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (store_id, ingredient_id, forecast_date)
);

-- ───────────────────────────────────────────────────────────────────────────
--  10. DỮ LIỆU HẠT GIỐNG — vai trò và quyền theo ma trận PRD mục 2.2
-- ───────────────────────────────────────────────────────────────────────────

INSERT INTO role (code, name) VALUES
    ('BARISTA',       'Nhân viên pha chế'),
    ('CASHIER',       'Thu ngân'),
    ('STORE_MANAGER', 'Quản lý cửa hàng'),
    ('ADMIN',         'Quản trị hệ thống');

INSERT INTO role_permission (role_id, permission)
SELECT r.id, p.perm
FROM role r
CROSS JOIN LATERAL (
    SELECT unnest(CASE r.code
        WHEN 'BARISTA' THEN ARRAY[
            'menu:read','order:read:store','order:update-status',
            'inventory:read','inventory:adjust']
        WHEN 'CASHIER' THEN ARRAY[
            'menu:read','order:read:store','order:update-status','order:create',
            'order:cancel','inventory:read','inventory:adjust',
            'table:open','table:close','payment:confirm-cash','report:store']
        WHEN 'STORE_MANAGER' THEN ARRAY[
            'menu:read','menu:write','order:read:store','order:update-status',
            'order:create','order:cancel','order:void-after-payment',
            'inventory:read','inventory:adjust','table:manage','table:open',
            'table:close','payment:confirm-cash','payment:refund',
            'staff:manage','report:store','audit:read']
        WHEN 'ADMIN' THEN ARRAY[
            'menu:read','menu:write','order:read:store','order:update-status',
            'order:create','order:cancel','order:void-after-payment',
            'inventory:read','inventory:adjust','table:manage','table:open',
            'table:close','payment:confirm-cash','payment:refund',
            'staff:manage','report:store','report:organization','audit:read',
            'system:config','security:keys']
    END) AS perm
) p;
