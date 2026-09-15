# Backlog triển khai — QROS

| Trường | Giá trị |
|---|---|
| Mã tài liệu | `BACKLOG-QROS-001` |
| Phiên bản | `1.0` |
| Ngày baseline | 2026-09-10 |
| Nguồn | `docs/PRD.md` v1.1 · `docs/architecture/SDD.md` · `docs/architecture/threat-model.md` |

## 1. Quy ước

Backlog này biến lộ trình PRD thành các lát dọc có thể kiểm chứng. “Sprint 0” là giai đoạn thiết kế
repo hiện tại; `M0` là milestone Nền móng kéo dài ba tuần trong PRD và bắt đầu sau Sprint 0.

Trạng thái:

- `DONE`: có artifact và đã chạy kiểm chứng tương ứng.
- `READY`: đủ yêu cầu và tiêu chí nghiệm thu để bắt đầu.
- `BLOCKED`: thiếu quyết định hoặc phụ thuộc bên ngoài được ghi rõ.
- `LATER`: chưa nằm trong lát triển khai hiện tại.

Mỗi commit phải tham chiếu ít nhất một mã `FR-*`. Mã `NFR-*`, `EC-*`, `ADR-*` và `TM-*` được
ghi thêm khi liên quan nhưng không thay thế mã `FR-*` trong commit.

## 2. Cổng vào và cổng hoàn thành

Một thẻ chỉ vào trạng thái `READY` khi:

- Có mã yêu cầu nguồn và đường dẫn đích trong cây thư mục chuẩn.
- Hợp đồng API/event liên quan đã có trong OpenAPI/AsyncAPI hoặc thẻ ghi rõ phải sửa spec trước.
- Có tiêu chí nghiệm thu quan sát được, kể cả hành vi lỗi.
- Mối đe doạ liên quan đã có mã `TM-*` và test dự kiến trong threat model.

Một thẻ chỉ vào trạng thái `DONE` khi:

- Code, migration, spec và tài liệu nhất quán; code sinh lại không tạo diff ngoài dự kiến.
- Unit test tầng domain đạt ít nhất 80%; đường nghiệp vụ then chốt đạt 100%.
- Integration test dùng PostgreSQL/Redis thật qua Testcontainers khi có truy cập hạ tầng.
- Test bảo mật và kiến trúc liên quan xanh; lỗi trả RFC 7807 với `code` và `traceId`.
- Không còn cảnh báo chặn từ formatter, SAST, dependency scan và secret scan áp dụng cho thẻ.
- `CLAUDE.md` đã cập nhật trạng thái kiểm chứng và quyết định mới, nếu có.

## 3. Sprint 0 — thiết kế và kiểm chứng nền

| ID | Hạng mục | Tham chiếu | Artifact/kiểm chứng | Trạng thái |
|---|---|---|---|---|
| `BL-S0-01` | Baseline PRD, SDD, ERD và hợp đồng API/event | `FR-CUS-01`, `FR-CUS-08`, `ADR-01`…`ADR-09` | `docs/`, OpenAPI, AsyncAPI | DONE |
| `BL-S0-02` | Baseline lược đồ PostgreSQL | `FR-MGT-01`, `FR-CUS-08`, `FR-PAY-06` | Flyway V1: 38 bảng, 0 constraint chưa validate | DONE |
| `BL-S0-03` | Cổng luật module và cấm raw SQL | `FR-CUS-08`, `NFR-SEC-03` | 10/10 ArchUnit/JUnit xanh | DONE |
| `BL-S0-04` | Threat model có truy vết test | `FR-CUS-01`, `FR-CUS-08`, `FR-AUTH-01` | `docs/architecture/threat-model.md` | DONE |
| `BL-S0-05` | Backlog lát dọc và thứ tự phụ thuộc | `FR-AUTH-01`, `FR-CUS-01` | Tài liệu này | DONE |

**Cổng thoát Sprint 0:** năm thẻ trên `DONE`. Walking skeleton chỉ bắt đầu từ `BL-M0-01`.

## 4. M0 — Nền móng

Mục tiêu milestone: nhân viên đăng nhập có MFA; pipeline chạy đủ cổng bảo mật; audit và quan sát
được lát nghiệp vụ xác thực. Không triển khai catalog/ordering giả chỉ để lấp đầy cây thư mục.

### 4.1. Thứ tự thực hiện

```text
BL-M0-01 ─┬─> BL-M0-02 ─> BL-M0-04 ─┬─> BL-M0-07 ─> BL-M0-08 ─> BL-M0-09
          │                          └─> BL-M0-05 ─> BL-M0-06
          ├─> BL-M0-03 ───────────────────────────────────────────┐
          └─> BL-M0-10 ─> BL-M0-11                               │
BL-M0-04 ─────> BL-M0-12                                         │
BL-M0-06 + BL-M0-09 + BL-M0-11 + BL-M0-12 ─> BL-M0-13 ─> BL-M0-14
```

### 4.2. Thẻ triển khai

| ID | P | Hạng mục và đường dẫn chính | Tham chiếu | Tiêu chí nghiệm thu | Trạng thái |
|---|:--:|---|---|---|:--:|
| `BL-M0-01` | M | Chuẩn hoá repo: `README.md`, `.editorconfig`, `.gitignore`, `docker-compose.yml`; tạo các thư mục cấp cao trong cây chuẩn khi có artifact đầu tiên | `FR-AUTH-01`, `NFR-AVL-02` | PostgreSQL 16 và Redis 7 khởi động bằng Compose; README có lệnh dev/test; không commit thư mục sinh | DONE |
| `BL-M0-02` | M | Spring Boot entrypoint và version catalog: `backend/gradle/libs.versions.toml`, `QrosApplication.java`, `application*.yml` | `FR-AUTH-01`, `NFR-AVL-03` | App khởi động với profile test/dev; config prod không chứa bí mật/mặc định yếu; health probe riêng | DONE |
| `BL-M0-03` | M | CI nền: `.github/workflows/ci.yml`, `contract-check.yml` | `FR-CUS-01`, `NFR-AVL-06` | Build/test chạy; sinh lại contract rồi `git diff --exit-code`; cache không che lỗi | DONE |
| `BL-M0-04` | M | Shared kernel: `shared/money`, `shared/id`, `shared/error`, `shared/web` | `FR-CUS-08`, `NFR-OBS-01` | `Money` chỉ dùng `long`; UUIDv7; RFC 7807 luôn có `code`/`traceId`; correlation ID xuyên request | DONE |
| `BL-M0-05` | M | Transactional outbox: `shared/event/DomainEvent`, `OutboxEntity`, `OutboxWriter`, `OutboxPoller` | `FR-BAR-02`, `ADR-05`, `TM-EVT-01` | Ghi nghiệp vụ và event nguyên tử; `id=seq`; retry không mất event; consumer dedupe | DONE |
| `BL-M0-06` | M | Idempotency dùng Redis/DB: `shared/idempotency/` | `FR-CUS-09`, `TM-ORD-02` | Cùng key+payload trả cùng response trong 24h; cùng key khác payload trả conflict; chạy đồng thời chỉ có một hiệu ứng | DONE |
| `BL-M0-07` | M | Ba chuỗi bảo mật độc lập: `shared/security/GuestSecurityConfig`, `StaffSecurityConfig`, `AdminSecurityConfig`, `JwtVerifier` | `FR-AUTH-01`, `NFR-SEC-02`…`08`, `TM-AUTH-02` | Ba matcher/decoder/key/audience độc lập; guest token không qua staff/admin; staff/admin bật CSRF | DONE |
| `BL-M0-08` | M | Identity domain và đăng nhập: `identity/{controller,service,domain,repository}` | `FR-AUTH-01`, `FR-AUTH-03`, `FR-MGT-07`, `TM-AUTH-01` | Argon2id đúng tham số; lỗi không liệt kê email; khoá luỹ tiến sau 5 lần sai; phạm vi tổ chức/chi nhánh | DONE |
| `BL-M0-09` | M | MFA, refresh rotation, token version và ca làm | `FR-AUTH-02`, `FR-AUTH-04`, `FR-AUTH-05`, `TM-AUTH-03` | Manager/admin chưa MFA không có quyền ghi; backup code một lần; refresh reuse thu hồi family; ca tối đa 12h | DONE |
| `BL-M0-10` | M | Audit append-only: `audit/{api,domain,repository,service}` | `FR-MGT-12`, `FR-PAY-07`, `TM-REP-01` | Thao tác nhạy cảm ghi actor, scope, trước/sau, lý do, correlation ID; app không có đường update/delete | DONE |
| `BL-M0-11` | M | Quan sát nền: OpenTelemetry, log JSON đã che PII, dashboard/cảnh báo và `docs/runbooks/` | `FR-MGT-11`, `NFR-OBS-01`…`05` | Mọi lỗi tra được bằng traceId; alert thử nghiệm liên kết đúng runbook; log không chứa token/mật khẩu/PII mẫu | DONE |
| `BL-M0-12` | M | Pipeline bảo mật: `.github/workflows/security.yml`, cấu hình CodeQL, Dependency-Check, Trivy, gitleaks và SBOM | `FR-AUTH-01`, `NFR-SEC-25`, `TM-OPS-01` | Secret/CVSS≥7/image high finding chặn pipeline; artifact có CycloneDX SBOM | DONE |
| `BL-M0-13` | M | Bộ test bảo mật M0: auth confusion/reuse/credential stuffing, audit immutability, error disclosure | `FR-AUTH-01`…`05`, `TM-AUTH-01`…`03`, `TM-REP-01`, `TM-OPS-02` | Testcontainers với PostgreSQL/Redis thật; mọi test được ánh xạ lại trong threat model và xanh | DONE |
| `BL-M0-14` | M | Cổng thoát M0 và walking skeleton đăng nhập | `FR-AUTH-01`…`05`, `NFR-OBS-01` | Login → MFA → quyền ghi → audit/trace chạy E2E; CI đủ cổng; runbook sự cố đăng nhập có diễn tập | DONE |

### 4.3. Lát triển khai đề xuất theo tuần

| Tuần | Kết quả quan sát được | Thẻ |
|---|---|---|
| 1 | Repo/app/dev stack chạy; contract và CI nền xanh; lỗi/correlation ID chuẩn | `BL-M0-01`…`04` |
| 2 | Outbox/idempotency và ba vùng bảo mật hoạt động; đăng nhập chống dò mật khẩu | `BL-M0-05`…`08` |
| 3 | MFA/rotation/ca làm, audit, observability, security pipeline và E2E M0 | `BL-M0-09`…`14` |

## 5. M1 — Walking skeleton đặt món

Mỗi lát phải đi trọn frontend → API → domain → PostgreSQL/outbox → realtime, không xây toàn bộ một
tầng rồi mới nối. Các thẻ ở đây ở trạng thái `LATER` cho tới khi `BL-M0-14` hoàn tất.

| ID | Lát dọc và đường dẫn chính | Tham chiếu | Tiêu chí thoát | Trạng thái |
|---|---|---|---|:--:|
| `BL-M1-01` | QR → phiên bàn: `venue/`, `web-guest/app/t/[qrToken]`, trang nhập mã | `FR-CUS-01`, `FR-CUS-02`, `EC-02`, `EC-03`, `TM-QR-01`, `TM-QR-02` | Sáu bước xác minh đúng thứ tự; tám lớp có test; phiên gắn thiết bị | DONE |
| `BL-M1-02` | Thực đơn read-only: `catalog/`, `web-guest/app/(session)/menu` | `FR-CUS-03`, `FR-CUS-04`, `FR-CUS-05`, `NFR-PERF-01` | Giá từ catalog; hết hàng/ETag/cache; p95 đạt ngưỡng | DONE |
| `BL-M1-03` | Giỏ offline và đặt món: `ordering/`, cart, order pages | `FR-CUS-06`, `FR-CUS-08`, `FR-CUS-09`, `TM-ORD-01`…`03` | Client không gửi giá; idempotent; order+outbox nguyên tử; test tampering xanh | DONE |
| `BL-M1-04` | KDS realtime: `web-staff/app/kds`, WebSocket resume/dedupe | `FR-BAR-01`…`04`, `FR-BAR-06`, `EC-06`, `TM-EVT-01` | Đơn tới KDS ≤1s; optimistic lock; reconnect/resume không mất/lặp hiệu ứng | DONE |
| `BL-M1-05` | Hết nguyên liệu lan toả: `inventory/`, catalog event, KDS | `FR-BAR-05`, `EC-04` | Báo hết → món thành `Tạm hết` ≤2s; đơn đang chờ được cảnh báo | DONE |
| `BL-M1-06` | Cổng exit M1 local (staging là tuỳ chọn cho bài tập lớn) | `FR-CUS-01`…`11`, `FR-BAR-01`…`06` | Backend/frontend test local xanh; E2E/k6 staging chỉ là bằng chứng bổ sung khi có hạ tầng | IN PROGRESS |

## 6. M2–M5 — hàng đợi cấp cao

| Milestone | Nội dung | Tham chiếu chính | Trạng thái |
|---|---|---|:--:|
| `M2` | Thanh toán, webhook/đối soát, tiền mặt, hoàn tiền, kho và chốt ca | `FR-CUS-13`…`15`, `FR-PAY-01`…`09`, `FR-MGT-05`…`06`, `EC-01`, `EC-08` | LATER |
| `M3` | Quản trị menu/bàn/nhân viên, báo cáo, xuất file và audit viewer | `FR-MGT-01`…`12` | LATER |
| `M4` | Gợi ý, trợ lý qua `aigateway`, hai job Python, eval và kiểm soát chi phí | `FR-AI-01`…`15`, `NFR-SEC-16`…`27`, `EC-05` | LATER |
| `M5` | Tải, xâm nhập, accessibility, backup/restore, runbook và phát hành | `NFR-PERF-*`, `NFR-UX-*`, `NFR-BCP-*` | LATER |

## 7. Quyết định và phụ thuộc đang mở

| Mã | Nội dung | Ảnh hưởng | Hành động/chủ sở hữu |
|---|---|---|---|
| `OPEN-01` | Nginx hay Spring Cloud Gateway | `BL-M0-01`, cấu hình edge | Architecture chốt trước khi đưa staging ra Internet |
| `OPEN-02` | **Đã chốt 15/09/2026**: Redis fail-closed cho mở phiên/đặt đơn; fail-open cho endpoint đọc. Ngưỡng M1: 5 mở phiên/10 phút/IP, 3 đơn/5 phút/phiên, 8 dòng/đơn và trần 2.000.000 VND/bàn | `BL-M1-01`, `TM-ORD-03`, `RR-04` — không còn ảnh hưởng `BL-M0-06`: idempotency chỉ dùng PostgreSQL | Hiện thực Redis limiter + `OrderAbuseLimitTest` theo [ADR-007](architecture/adr-007-device-binding-va-rate-limit-m1.md) trước test tải M1 |
| `OPEN-03` | Model tier Opus 5/Sonnet 5/Haiku 4.5 | M4, chi phí và eval | Chủ đầu tư chốt sau khi chạy `eval/`; mặc định giữ `claude-opus-5` |
| `OPEN-04` | DPA và hồ sơ chuyển dữ liệu ra nước ngoài | Phát hành chatbot M4 | Legal; chatbot giữ sau feature flag cho tới khi được xác nhận |
| `OPEN-05` | Allowlist IP admin là Must hay Should | `BL-M0-07`, `RR-05` | Product + Security chốt trước `BL-M0-14` |
| `OPEN-06` | Chia hoá đơn chưa được mô hình hoá | M2 | Giữ ưu tiên C; không chặn walking skeleton |
| `OPEN-07` | Thiết kế phát token vùng admin chưa chốt: `/auth/login` (`BL-M0-08`) chỉ phát token vùng staff (audience `qros-staff`); vùng admin dùng khoá và `JwtVerifier` hoàn toàn khác (`TM-AUTH-02`) nên một token không tự nhiên dùng được ở cả hai vùng | `AdminSecurityConfig`, `BL-M0-09` hoặc M1 | Product + Architecture chốt cách `STORE_MANAGER`/`ADMIN` lấy được token vùng admin trước khi xây UI quản trị |
| `OPEN-08` | Mở ca bằng PIN trên "thiết bị đã đăng ký" (`FR-AUTH-04`) chưa có UX: `BL-M0-09` mới làm phần tự đóng ca sau 12 giờ, chưa có endpoint mở ca vì chưa rõ đăng ký thiết bị hoạt động thế nào (thiết bị dùng chung chọn nhân viên ra sao, ai duyệt đăng ký) | `identity`, `web-staff` | Product chốt luồng UX mở ca trước khi thiết kế endpoint và contract |
| `OPEN-09` | Đăng ký MFA tự phục vụ chưa có endpoint HTTP: `MfaService.enable` tồn tại và được test trực tiếp, nhưng bật MFA ngay không có bước xác nhận đã cấu hình đúng ứng dụng TOTP trước khi kích hoạt | `identity`, `web-staff` | Thiết kế luồng đăng ký (enroll → xác nhận TOTP → hiện mã dự phòng) trước khi thêm endpoint, sửa YAML trước theo quy trình spec-first |
| `OPEN-10` | **Đóng 15/09/2026**: KDS chọn chi nhánh và gửi `X-Store-Id`; `StoreMdcFilter` chỉ ghi `storeId` vào MDC khi UUID chuẩn thuộc claim `stores` (hoặc wildcard) của JWT staff/admin, rồi luôn xoá sau request. `StoreMdcFilterTest` và `StructuredLoggingTest` xác nhận giá trị được tin/có trong JSON log và không rò sang request kế tiếp | `shared/security`, `web-staff` | Không còn hành động cho KDS hiện tại; màn admin tương lai dùng cùng filter đã gắn ở admin chain |
| `OPEN-11` | Dashboards Grafana (`NFR-OBS-04`) chưa xây: repo chưa có Grafana/Prometheus/Loki trong hạ tầng (`docker-compose.yml`), và phần lớn chỉ số nghiệp vụ PRD muốn hiển thị (kênh chuyển đổi, chi phí LLM) chưa có dữ liệu vì các module tương ứng chưa tồn tại | hạ tầng, M4 | Dựng khi có nhu cầu triển khai thật hoặc khi các chỉ số nghiệp vụ đầu tiên (`BL-M1-*`) có dữ liệu để hiển thị |
| `OPEN-12` | Secret `NVD_API_KEY` chưa được cấp trong GitHub Actions — `dependencyCheckAnalyze` vẫn quét được không có key nhưng tải dữ liệu NVD chậm hơn nhiều, dễ vượt `timeout-minutes: 30` của job `dependency-check`. Cũng chưa chạy `dependencyCheckAnalyze` trọn vẹn cục bộ (tải NVD lần đầu mất hàng chục phút, không thực tế trong một phiên làm việc) | `.github/workflows/security.yml` | Người có quyền quản trị repo GitHub tạo secret `NVD_API_KEY` (đăng ký miễn phí tại nvd.nist.gov) trước khi bật workflow |
| `OPEN-13` | **Nửa đã đóng 14/09/2026**: repo đã gắn Git thật (`git init -b main`, commit `f7bb3d0`) và đẩy lên `https://github.com/longdxam/QR-order` (`main`, xác nhận qua `git ls-remote`). Ba workflow (`ci.yml`, `contract-check.yml`, `security.yml`) giờ CÓ THỂ chạy trên GitHub Actions thật, nhưng **chưa có lượt chạy nào được xác nhận xanh** — cần vào tab Actions của repo kiểm tận mắt, và `OPEN-12` (`NVD_API_KEY`) vẫn chặn `dependency-check` chạy trong thời hạn `timeout-minutes: 30` | `.github/workflows/*.yml` | Kiểm tab Actions sau lượt push đầu; nếu `security.yml` timeout ở `dependency-check`, giải quyết `OPEN-12` trước khi coi `BL-M0-12`/`BL-M0-03` đã kiểm chứng đầy đủ trên CI thật |
| `OPEN-14` | Kiểm thử động (DAST/ZAP) cho `TM-INJ-01` và `TM-OPS-02` chưa có thẻ backlog nào sở hữu — cả hai chỉ mới xanh phần Integration/ArchUnit | `security-review`, backlog M0 hoặc M5 | Thêm một thẻ backlog rõ ràng (hoặc gộp vào `BL-M0-13`/`BL-M5-*`) trước khi coi hai mối đe doạ này đã đóng hoàn toàn |
| `OPEN-15` | **Đã chốt 15/09/2026**: guest JWS có claim `did`; mọi request guest đã xác thực gửi `X-Device-Id` khớp claim, nếu không trả `401 DEVICE_BINDING_FAILED` | `venue`, `shared/security`, `web-guest` | Sửa OpenAPI trước, rồi implementation + `SessionBindingTest` theo [ADR-007](architecture/adr-007-device-binding-va-rate-limit-m1.md) |
| `OPEN-16` | **Đóng 15/09/2026**: `web-guest/scripts/check-initial-js-budget.mjs` đọc client-reference manifest thật của Turbopack, gzip từng chunk initial và chặn mọi route guest vượt 180 KB. Preflight: `/menu` 138.9 KB (lớn nhất), `/cart` 137.5 KB, `/orders/[orderId]` 136.0 KB, `/t/[qrToken]` 132.2 KB, `/ma-ban` 131.9 KB | `web-guest`, `.github/workflows/ci.yml` | CI chạy build rồi `npm run bundle:check`; có thể đặt `QROS_INITIAL_JS_BUDGET_KB` chỉ để thử ngưỡng chặt hơn, không tăng ngân sách khi chưa qua kiểm soát thay đổi |
| `OPEN-17` | `NFR-PERF-01` (`GET /menu` p95 ≤ 120ms, p99 ≤ 250ms) chưa đo bằng k6 500 người dùng ảo như tiêu chí nghiệm thu `BL-M1-02` đòi — mới xác nhận đúng/đủ dữ liệu qua test tích hợp và chạy thật một request đơn lẻ, không phải tải thật | `load/` (chưa có thư mục này) | Viết kịch bản k6, dựng dữ liệu catalog đủ lớn để đo có ý nghĩa, trước khi coi `NFR-PERF-01` đã kiểm chứng |
| `OPEN-18` | **Đóng 15/09/2026**: `web-guest` đã có giỏ IndexedDB theo `sessionId`, màn chọn biến thể/tuỳ chọn/ghi chú, `/cart`, `/orders/{orderId}` và gọi `POST /guest/orders`. Snapshot giá chỉ dùng hiển thị; `prepareCheckout` dựng payload không có trường giá. UUIDv4 idempotency được giữ qua retry và chỉ đổi khi nội dung giỏ đổi | `web-guest` | Không còn hành động; bằng chứng và giới hạn kiểm thử ghi tại `CLAUDE.md` trong `BL-M1-03` |
| `OPEN-19` | Kịch bản `PRICE_CHANGED` (`FR-CUS-08` Gherkin thứ hai trong PRD, "giá đổi giữa lúc xem và lúc đặt") không có cơ chế nào trong hợp đồng để client báo "giá tôi thấy lúc xem thực đơn" — `CreateOrderRequest` cố tình không có trường giá nào (`ADR-06`), nên máy chủ không có gì để so sánh. `BL-M1-03` chỉ hiện thực `PRICE_NOT_ACCEPTED` (chặn giá client gửi) và `ITEM_SOLD_OUT`; `409 PRICE_CHANGED` có khai báo trong `openapi.yaml` nhưng chưa có nhánh code nào trả về nó | `docs/PRD.md`, `docs/api/openapi.yaml`, `ordering` | Product + Architecture chốt cơ chế phát hiện (ETag thực đơn client gửi lại? mốc thời gian xem?) trước khi hiện thực, sửa hợp đồng trước theo quy trình spec-first |
| `OPEN-20` | `BL-M1-05` đã phát `ItemUnavailable` theo phiên khách với món, số tiền, `respondBy` 3 phút và đã cảnh báo KDS, nhưng `web-guest` chưa có WebSocket/notification UI và chưa có API nhận ba lựa chọn của `EC-04`. Danh sách thay thế phụ thuộc `FR-AI-01`; hoàn tiền phụ thuộc M2 | `shared/realtime`, `web-guest`, `ordering`, M2, M4 | Thiết kế contract phản hồi của khách và timeout escalation trước; nối kênh realtime phiên khách, UI ba lựa chọn, rồi tích hợp AI/hoàn tiền theo milestone sở hữu |

## 8. Cách duy trì backlog

- Không đổi mã `BL-*`; thẻ bỏ đi được đánh dấu `DEPRECATED` kèm lý do.
- Khi bắt đầu thẻ, cập nhật trạng thái ở đây và ghi “đang làm gì” trong `CLAUDE.md`.
- Khi hoàn tất, ghi lệnh kiểm chứng, ngày và kết quả vào `CLAUDE.md`; không chỉ đổi nhãn `DONE`.
- Nếu hợp đồng thay đổi, sửa YAML trước, sinh lại code, rồi mới sửa implementation và backlog.
- Mỗi cuối sprint đối chiếu bảng test ở threat model; không để `DONE` nếu mối đe doạ liên quan chưa có test xanh.
