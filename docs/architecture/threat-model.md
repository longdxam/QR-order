# Threat model — QROS

| Trường | Giá trị |
|---|---|
| Mã tài liệu | `TM-QROS-001` |
| Phiên bản | `1.0` |
| Ngày baseline | 2026-09-10 |
| Phương pháp | STRIDE theo ranh giới tin cậy và luồng dữ liệu |
| Tài liệu nguồn | `docs/PRD.md` v1.1 · `docs/architecture/SDD.md` v1.0 · `CLAUDE.md` |
| Phạm vi | QROS v1, từ trình duyệt tới kho dữ liệu và hai kết nối ra ngoài |

## 1. Mục đích và nguyên tắc

Tài liệu này chuyển mô hình khái quát ở PRD mục 5.3 thành các mối đe doạ có chủ sở hữu,
biện pháp giảm thiểu và kiểm thử tự động cụ thể. Một mối đe doạ chỉ được đánh dấu `Đã kiểm chứng`
khi kiểm thử được nêu trong bảng truy vết đã tồn tại và đang xanh trong CI.

Các giả định nền:

- Mã QR trên bàn là dữ liệu công khai; kẻ tấn công được giả định đã có ảnh chụp mã.
- Mọi dữ liệu từ trình duyệt, kể cả từ một phiên vừa quét QR hợp lệ, đều không đáng tin.
- Trình duyệt không gọi trực tiếp Claude API hoặc cổng thanh toán bằng khoá bí mật của QROS.
- Ở v1, Spring Boot gọi Claude API qua module `aigateway`; không có AI service Python chạy 24/7.
- Hai job trong `ml/jobs/` chỉ chạy theo lịch và không nằm trên đường tới hạn.
- Kafka/RabbitMQ không thuộc phạm vi v1; sự kiện đi qua transactional outbox, poller và Redis pub/sub.

## 2. Tài sản cần bảo vệ

| Mã | Tài sản | Mục tiêu bảo vệ |
|---|---|---|
| `AS-01` | Khoá riêng ký QR và JWT | Bí mật, toàn vẹn, khả năng xoay vòng |
| `AS-02` | Mật khẩu, MFA, refresh token, phiên bàn | Bí mật, chống phát lại và thu hồi được |
| `AS-03` | Catalog, lịch giá và phép tính tổng tiền | Toàn vẹn; catalog là nguồn giá duy nhất |
| `AS-04` | Đơn hàng, trạng thái món và hàng đợi KDS | Toàn vẹn, sẵn sàng, đúng thứ tự |
| `AS-05` | Ý định, giao dịch, hoàn tiền và webhook thanh toán | Toàn vẹn, chống trùng, truy vết được |
| `AS-06` | Dữ liệu cá nhân và nội dung hội thoại | Tối thiểu hoá, bí mật, đúng vòng đời |
| `AS-07` | Quyền, phạm vi chi nhánh và quyền sở hữu đối tượng | Cách ly tenant, bàn và vai trò |
| `AS-08` | Audit log, correlation ID và số liệu giám sát | Chỉ ghi thêm, đầy đủ, không chứa bí mật |
| `AS-09` | Outbox và `seq` realtime | Nguyên tử với nghiệp vụ, có thứ tự, khử trùng lặp |
| `AS-10` | Tính sẵn sàng của API, KDS, Redis và PostgreSQL | Chịu giới hạn tải và suy giảm an toàn |

## 3. Tác nhân và ranh giới tin cậy

### 3.1. Tác nhân

| Tác nhân | Mức tin cậy | Khả năng đáng chú ý |
|---|---|---|
| Khách hợp lệ | Không tin cậy | Sở hữu QR và phiên bàn của chính mình |
| Kẻ tấn công Internet | Không tin cậy | Có thể có QR chụp trộm, tự tạo payload và phát lại yêu cầu |
| Nhân viên | Tin cậy có giới hạn | Có tài khoản nhưng chỉ trong quyền và chi nhánh đang trực |
| Quản lý/Admin | Đặc quyền có giám sát | Thao tác nhạy cảm phải MFA và ghi audit |
| Cổng thanh toán | Bên ngoài | Gửi webhook có chữ ký; dữ liệu vẫn phải được xác minh trước khi parse |
| Claude API | Bên ngoài | Chỉ nhận dữ liệu đã qua cổng lọc của `aigateway` |
| Job Python | Nội bộ có giới hạn | Đọc dữ liệu cần thiết, ghi bảng kết quả riêng, không phục vụ request trực tiếp |
| Người vận hành/CI | Đặc quyền | Có thể triển khai, cấu hình và tiếp cận bí mật qua hệ quản lý bí mật |

### 3.2. Ranh giới

| Mã | Ranh giới | Quy tắc |
|---|---|---|
| `TB-01` | Internet → edge/API | TLS, WAF/rate limit, giới hạn kích thước và xác thực đầu vào |
| `TB-02` | Guest → `/api/v1/guest/**` | Chuỗi filter và `JwtDecoder` riêng; quyền sở hữu nằm trong truy vấn |
| `TB-03` | Staff → `/api/v1/staff/**` | Cookie an toàn, CSRF bật, quyền theo phương thức và phạm vi ca/chi nhánh |
| `TB-04` | Admin → `/api/v1/admin/**` | Chuỗi filter riêng, MFA, allowlist IP khi áp dụng, audit bắt buộc |
| `TB-05` | Spring Boot → PostgreSQL/Redis | Mạng nội bộ, tài khoản quyền tối thiểu, truy vấn tham số hoá |
| `TB-06` | `aigateway` → Claude API | Egress allowlist; payload chặn theo mặc định và che PII |
| `TB-07` | `payment` ↔ cổng thanh toán | Redirect phía khách; webhook xác minh HMAC trên raw body trước khi parse |
| `TB-08` | Outbox poller → Redis → WebSocket | `seq = outbox_event.id`, khử trùng lặp bằng `eventId`, resume có giới hạn |
| `TB-09` | CI/CD → artifact/runtime | Không chứa bí mật, quét phụ thuộc/image, ký artifact và quyền triển khai tối thiểu |

## 4. Luồng dữ liệu quan trọng

| Mã | Luồng | Dữ liệu | Kiểm soát bắt buộc |
|---|---|---|---|
| `DF-01` | QR → tạo/join phiên bàn | JWS, `deviceId`, User-Agent, IP | Ed25519, `kid`, bàn/chi nhánh, giờ mở cửa, TOTP tuỳ chọn, rate limit |
| `DF-02` | Phiên khách → đặt món | ID món/tuỳ chọn, số lượng, ghi chú | Không nhận giá; validation; ownership; idempotency; hạn mức nghiệp vụ |
| `DF-03` | Nhân viên → KDS/quản trị | JWT/cookie, thay đổi trạng thái | MFA theo vai trò, CSRF, RBAC, phạm vi chi nhánh, optimistic lock |
| `DF-04` | Giao dịch nghiệp vụ → outbox → KDS | Domain event, `eventId`, `seq` | Ghi cùng transaction, publish ít nhất một lần, khử trùng lặp |
| `DF-05` | QROS → cổng thanh toán → webhook | Intent ID, số tiền, trạng thái | Redirect hosted page, HMAC raw body, nonce/time window, idempotency |
| `DF-06` | Chat → `aigateway` → Claude | Thực đơn công khai, FAQ, nội dung lượt hiện tại | Allowlist trường, che PII, giới hạn token/lượt, output guard |
| `DF-07` | PostgreSQL → job Python → bảng kết quả | Dữ liệu tổng hợp đơn/kho | Tài khoản giới hạn, bảng đích riêng, không đọc dữ liệu thanh toán không cần thiết |

## 5. Cách chấm rủi ro

Khả năng và tác động được chấm từ 1 đến 5. Điểm rủi ro là tích của hai giá trị:

- `1–4`: Thấp.
- `5–9`: Trung bình.
- `10–16`: Cao.
- `17–25`: Nghiêm trọng.

Rủi ro tồn dư là đánh giá sau khi toàn bộ biện pháp và kiểm thử liên quan đã được triển khai.

## 6. Danh mục mối đe doạ STRIDE

| Mã | STRIDE | Kịch bản và tài sản | Ranh giới | Rủi ro gốc | Biện pháp bắt buộc | Tồn dư |
|---|---|---|---|---:|---|---:|
| `TM-QR-01` | S/T | Tự chế QR, sửa `sid`/`tid`, đổi thuật toán hoặc dùng `kid` lạ (`AS-01`, `AS-02`) | `TB-01`, `TB-02` | 4×5=20 | Chỉ EdDSA; chọn khoá theo `kid`; xác minh chữ ký trước khi đọc nghiệp vụ; ràng buộc bàn với chi nhánh | 1×5=5 |
| `TM-QR-02` | S/R | Dùng lại ảnh QR từ xa hoặc mã TOTP cũ để mở phiên (`AS-02`, `AS-10`) | `TB-01` | 5×4=20 | Tám lớp QR; phiên 90 phút gắn thiết bị; giờ mở cửa; TOTP ±1 bước; đơn đầu chờ duyệt | 2×3=6 |
| `TM-SES-01` | S/I | Đánh cắp hoặc chia sẻ token phiên để đọc/đặt món cho bàn khác (`AS-02`, `AS-07`) | `TB-02` | 4×4=16 | `aud` riêng; TTL; `deviceId`; ownership trong `WHERE`; trả 404 khi không sở hữu | 2×3=6 |
| `TM-AUTH-01` | S/D | Dò mật khẩu, liệt kê email hoặc chiếm tài khoản nhân viên (`AS-02`, `AS-07`) | `TB-03`, `TB-04` | 4×5=20 | Argon2id; lỗi đồng nhất; khoá luỹ tiến; MFA; rate limit; cảnh báo | 2×4=8 |
| `TM-AUTH-02` | S/E | `alg:none`, thuật toán đối xứng hoặc token guest được dùng ở staff/admin (`AS-02`, `AS-07`) | `TB-02`–`TB-04` | 4×5=20 | Allowlist thuật toán; ba decoder, audience và key riêng; từ chối mặc định | 1×5=5 |
| `TM-AUTH-03` | S/E | Phát lại refresh token hoặc tiếp tục dùng token sau khi đổi quyền/mật khẩu (`AS-02`, `AS-07`) | `TB-03`, `TB-04` | 3×5=15 | Rotation; phát hiện reuse; chặn `jti`; `token_version`; audit/cảnh báo | 1×5=5 |
| `TM-ACC-01` | I/E | Đổi UUID để đọc hoặc sửa dữ liệu bàn/chi nhánh khác (`AS-04`, `AS-05`, `AS-07`) | `TB-02`–`TB-04` | 4×5=20 | UUIDv7; tenant/session/store nằm trong truy vấn; permission theo phương thức; 404 chống dò tồn tại | 1×5=5 |
| `TM-ORD-01` | T | Gửi giá/phụ phí/giảm giá giả hoặc lợi dụng giá đổi giữa lúc đặt (`AS-03`, `AS-04`) | `TB-02` | 5×5=25 | Schema không có giá và `additionalProperties:false`; máy chủ định giá từ catalog trong cùng transaction | 1×5=5 |
| `TM-ORD-02` | T/R | Gửi lặp request đặt món/thanh toán do retry hoặc cố ý (`AS-04`, `AS-05`) | `TB-01`, `TB-02`, `TB-07` | 4×4=16 | `Idempotency-Key`; lưu kết quả 24 giờ; unique constraint; phát hiện payment trùng | 1×4=4 |
| `TM-ORD-03` | D | Bắn đơn, món hoặc phiên hàng loạt làm cạn tài nguyên/kho (`AS-04`, `AS-10`) | `TB-01`, `TB-02` | 5×4=20 | 3 đơn/5 phút/phiên; 8 món/đơn; trần 2 triệu/bàn; rate limit theo IP/bàn/phiên; đơn đầu chờ duyệt | 2×3=6 |
| `TM-PAY-01` | S/T | Webhook giả, sửa số tiền/trạng thái hoặc parse payload độc hại (`AS-05`) | `TB-07` | 4×5=20 | Xác minh HMAC trên raw body trước parse; allowlist provider; đối chiếu intent và số tiền phía máy chủ | 1×5=5 |
| `TM-PAY-02` | R/T | Phát lại webhook hoặc ghi nhận cả tiền mặt lẫn online (`AS-05`, `AS-08`) | `TB-07` | 3×5=15 | Nonce + cửa sổ thời gian; idempotency; state machine; `DuplicatePaymentDetector`; reconciliation | 1×5=5 |
| `TM-REP-01` | R | Nhân viên phủ nhận huỷ/hoàn tiền/sửa giá hoặc sửa/xoá dấu vết (`AS-08`) | `TB-03`–`TB-05` | 3×4=12 | Audit chỉ ghi thêm; actor, thời gian, lý do, correlation ID; quyền đọc tách biệt; che PII | 1×4=4 |
| `TM-INJ-01` | T/I/E | SQL injection, XSS qua ghi chú hoặc mass assignment (`AS-03`–`AS-08`) | `TB-01`–`TB-05` | 4×5=20 | JPA/jOOQ tham số hoá; Bean Validation; schema đóng; React escaping; CSP; cấm raw SQL nối chuỗi | 1×5=5 |
| `TM-AI-01` | T/I/E | Prompt injection làm lộ system prompt hoặc gọi công cụ ngoài quyền (`AS-03`, `AS-06`, `AS-07`) | `TB-02`, `TB-06` | 4×4=16 | Tách role; đánh dấu input là dữ liệu; đúng bốn tool; validate schema; output guard | 2×3=6 |
| `TM-AI-02` | I | Số điện thoại/email/thẻ/địa chỉ hoặc dữ liệu nội bộ rời hệ thống (`AS-06`) | `TB-06` | 4×5=20 | Egress DTO allowlist chặn mặc định; PII redaction; không gửi lịch sử đơn/payment/staff; log đã che | 1×5=5 |
| `TM-AI-03` | D | Chat dài/tần suất cao làm cạn ngân sách hoặc kết nối (`AS-10`) | `TB-01`, `TB-06` | 4×4=16 | 512 token vào/ra; 20 lượt/phiên; 6 tin/phút; `BudgetGuard`; timeout/circuit breaker; FAQ suy giảm | 2×2=4 |
| `TM-EVT-01` | T/R/D | Mất, lặp hoặc đảo thứ tự sự kiện làm KDS sai trạng thái (`AS-04`, `AS-09`) | `TB-05`, `TB-08` | 3×5=15 | Outbox cùng transaction; `seq=id`; state machine; `eventId` dedupe; resume/resync | 1×4=4 |
| `TM-OPS-01` | I/E | Bí mật lọt vào repo, log, image hoặc biến môi trường (`AS-01`, `AS-02`, `AS-06`) | `TB-09` | 3×5=15 | Vault/KMS; gitleaks; image distroless/non-root; log redaction; xoay khoá khi lộ | 1×5=5 |
| `TM-OPS-02` | I/E | Actuator, stack trace hoặc cấu hình nội bộ lộ qua API (`AS-01`, `AS-08`) | `TB-01`, `TB-09` | 3×4=12 | Actuator ở cổng nội bộ; RFC 7807; security headers; không trả class/query/stack trace | 1×4=4 |

## 7. Truy vết sang kiểm thử tự động

`Kế hoạch` nghĩa là tên và vị trí test đã được chốt nhưng mã kiểm thử chưa được hiện thực.

| Mối đe doạ | Kiểm thử bắt buộc | Loại | Trạng thái |
|---|---|---|---|
| `TM-QR-01` | `security/QrForgeryTest` — chữ ký sai, `alg:none`, `kid` lạ, chéo chi nhánh | Integration | Xanh 14/09/2026 · PostgreSQL 16 qua Testcontainers 2.0.5 · Java 25 · Gradle 9.1.0 |
| `TM-QR-02` | `security/QrReplayTest` — ngoài giờ, TOTP cũ hơn 120 giây, đơn đầu chưa duyệt | Integration | Một phần 14/09/2026 — ngoài giờ và TOTP hết hạn xanh (PostgreSQL 16 qua Testcontainers 2.0.5, Java 25, Gradle 9.1.0); "đơn đầu chờ duyệt" thuộc `ordering` (`BL-M1-03`), chưa tồn tại |
| `TM-SES-01` | `security/SessionBindingTest` — token khác thiết bị và khác audience bị từ chối | Integration | Kế hoạch |
| `TM-AUTH-01` | `security/CredentialStuffingTest` — lỗi đồng nhất và khoá sau ngưỡng | Integration | Xanh 14/09/2026 · PostgreSQL 16 qua Testcontainers 2.0.5 · Java 25 · Gradle 9.1.0 |
| `TM-AUTH-02` | `security/JwtAlgorithmConfusionTest` — thuật toán/audience/key chéo bị từ chối | Integration | Xanh 13/09/2026 · Tomcat thật · Java 25 · Spring Security 7.1.1 · Gradle 9.1.0 |
| `TM-AUTH-03` | `security/RefreshTokenReuseTest` — reuse thu hồi cả token family | Integration | Xanh 14/09/2026 · PostgreSQL 16 qua Testcontainers 2.0.5 · Java 25 · Gradle 9.1.0 |
| `TM-ACC-01` | `security/IdorTest` — mọi cặp vai trò × endpoint và ownership trả đúng 404/403 | Integration | Kế hoạch |
| `TM-ORD-01` | `security/PriceTamperingTest` — field giá bị từ chối và tổng do server tính | Integration | Xanh 14/09/2026 · PostgreSQL 16 qua Testcontainers 2.0.5 · Java 25 · Gradle 9.1.0 |
| `TM-ORD-02` | `integration/IdempotencyTest` — cùng key chỉ tạo một hiệu ứng | Integration | Xanh 13/09/2026 · PostgreSQL 16 qua Testcontainers 2.0.5 · Java 25 · Gradle 9.1.0 |
| `TM-ORD-03` | `security/OrderAbuseLimitTest` — giới hạn phiên/đơn/món/giá trị | Integration + k6 | Kế hoạch |
| `TM-PAY-01` | `security/PaymentWebhookForgeryTest` — HMAC được kiểm trước parse | Integration | Kế hoạch |
| `TM-PAY-02` | `payment/DuplicatePaymentTest` và `e2e/payment-recovery.spec.ts` | Integration + E2E | Kế hoạch |
| `TM-REP-01` | `security/AuditImmutabilityTest` — thao tác nhạy cảm có actor/lý do và không update/delete | Integration | Xanh 14/09/2026 · PostgreSQL 16 qua Testcontainers 2.0.5 · Java 25 · Gradle 9.1.0 |
| `TM-INJ-01` | `architecture/NoRawSqlTest`, ZAP và test render ghi chú độc hại | ArchUnit + DAST | Một phần: ArchUnit xanh |
| `TM-AI-01` | `security/PromptInjectionTest` — không lộ prompt, tool ngoài allowlist bị chặn | Unit + Integration | Kế hoạch |
| `TM-AI-02` | `security/AiEgressFilterTest` — field ngoài allowlist và PII không rời `aigateway` | Unit + contract | Kế hoạch |
| `TM-AI-03` | `security/AiResourceLimitTest` — token/lượt/tần suất/ngân sách | Integration + k6 | Kế hoạch |
| `TM-EVT-01` | `integration/OutboxDeliveryTest` và `e2e/kds-concurrency.spec.ts` | Integration + E2E | Một phần: integration xanh 13/09/2026 (PostgreSQL 16 + Redis 7 qua Testcontainers 2.0.5); E2E chờ `BL-M1-04` |
| `TM-OPS-01` | gitleaks, Trivy, secret-pattern test trên image/artifact | CI | Một phần: `.github/workflows/security.yml` hợp lệ theo actionlint 1.7.12 (14/09/2026), image dựng và chạy thật qua `Dockerfile`, SBOM CycloneDX 1.6 sinh thật (221 thành phần) — workflow GitHub chưa chạy được thật vì workspace chưa có `.git` (`BL-M0-12`) |
| `TM-OPS-02` | `security/ErrorDisclosureTest` và kiểm thử security header/Actuator | Integration + DAST | Một phần: integration xanh 14/09/2026 (Tomcat thật, PostgreSQL 16 qua Testcontainers 2.0.5, Java 25, Gradle 9.1.0) — phủ cả ba nhánh của biện pháp bắt buộc (Actuator ở cổng nội bộ, RFC 7807, security headers); DAST (ZAP) chưa chạy, chưa có thẻ backlog nào sở hữu |

Không đổi trạng thái trong bảng này chỉ dựa trên review mã nguồn. Phải có bằng chứng CI hoặc kết quả
kiểm chứng có ngày, phiên bản công cụ và môi trường.

## 8. Ca lạm dụng ưu tiên cho walking skeleton

1. Gửi `unitPrice`, `discount` hoặc field lạ trong `POST /guest/orders` phải bị từ chối với
   `PRICE_NOT_ACCEPTED`; không được âm thầm bỏ qua.
2. Dùng `orderId` của phiên khác phải trả `404` và thời gian phản hồi không được tạo oracle rõ ràng.
3. Gửi hai request cùng `Idempotency-Key` phải nhận cùng kết quả và chỉ có một đơn/outbox event.
4. Đơn và `OrderPlaced` phải cùng tồn tại hoặc cùng không tồn tại khi transaction rollback.
5. Token guest không được xác thực ở staff/admin dù token có chữ ký hợp lệ.
6. Webhook sai HMAC hoặc đã phát lại không được thay đổi trạng thái thanh toán.
7. Prompt chứa email, số điện thoại, số thẻ giả và câu lệnh chiếm quyền phải bị lọc trước egress.
8. Lỗi ở mọi vùng API phải là `application/problem+json`, có `code` và `traceId`, không có stack trace.

## 9. Rủi ro tồn dư và quyết định cần chủ sở hữu chấp nhận

| Mã | Rủi ro tồn dư | Chủ sở hữu | Điều kiện/chốt quyết định |
|---|---|---|---|
| `RR-01` | QR tĩnh vẫn có thể bị dùng từ xa; lớp đơn đầu cần thao tác vận hành đúng | Product + Operations | Thử nghiệm tại M1; quán rủi ro cao phải bật QR xoay vòng |
| `RR-02` | Tín hiệu vị trí có thể bị giả và cần quyền riêng tư | Product + Legal | Chỉ là tín hiệu phụ; không bật mặc định trước khi duyệt UX/pháp lý |
| `RR-03` | Nội dung chat đã lọc vẫn đi tới nhà cung cấp ngoài | Legal + Security | Hoàn tất DPA/đánh giá tác động trước khi phát hành chatbot |
| `RR-04` | Redis gián đoạn làm giảm rate limit và realtime | Architecture + Operations | Chốt fail-open/fail-closed theo endpoint trước M1; đặt cảnh báo và runbook |
| `RR-05` | Allowlist IP admin chưa phải yêu cầu Must | Security + Product | Xác nhận nâng `FR-AUTH-06` lên M hoặc ghi chấp nhận rủi ro trước GA |

## 10. Chu kỳ rà soát

Rà soát threat model khi xảy ra một trong các điều kiện sau:

- Thêm endpoint, actor, kho dữ liệu, nhà cung cấp hoặc đường egress.
- Thay đổi cơ chế QR, JWT, thanh toán, quyền sở hữu hoặc vòng đời dữ liệu.
- Thêm tool cho trợ lý AI hoặc thay đổi dữ liệu được phép rời `aigateway`.
- Có phát hiện bảo mật mức Cao/Nghiêm trọng hoặc một ca gian lận thực tế.
- Ít nhất một lần vào cuối mỗi sprint, kể cả khi không có thay đổi kiến trúc.

Mọi thay đổi phải giữ ổn định mã `TM-*`; nếu bỏ mối đe doạ thì đánh dấu `DEPRECATED`, không tái sử dụng mã.
