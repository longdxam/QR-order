# QROS — hướng dẫn làm việc trong repo

Hệ thống đặt đồ uống qua QR tại bàn cho chuỗi quán cà phê / trà sữa 1–20 chi nhánh.
Java/Spring Boot · Next.js · Claude API · PostgreSQL · Redis.

## Trạng thái hiện tại

Sprint 0 đã có **SDD + ERD + Flyway V1 + threat model + backlog** và Gradle skeleton cho backend. Threat model
`TM-QROS-001` đã được baseline ngày 10/09/2026 tại `docs/architecture/threat-model.md`: có 20 mối
đe doạ STRIDE, ranh giới tin cậy, rủi ro tồn dư và ánh xạ mỗi mối đe doạ tới ít nhất một test tự động.
Các test mang trạng thái `Kế hoạch` chưa được tính là đã kiểm chứng.

Backlog `BACKLOG-QROS-001` đã được baseline cùng ngày tại `docs/backlog.md`. Sprint 0 và M0 đã hoàn
tất; năm lát dọc đầu M1 (`BL-M1-01`…`05`: QR/phiên bàn, thực đơn, giỏ/đặt món, KDS realtime,
hết nguyên liệu lan toả) cũng đã hoàn tất. Thẻ triển khai tiếp theo là `BL-M1-06`: cổng thoát M1
trên staging, gồm E2E Chromium/WebKit, k6 và các test security liên quan.

`BL-M0-01` hoàn tất ngày 10/09/2026:

- Root repo có `README.md`, `.editorconfig`, `.gitignore` và `docker-compose.yml`; chưa tạo thư mục
  cấp cao rỗng khi chưa có artifact. `.gitignore` loại Gradle/build, Node/Next.js, Python, IDE, log và
  kết quả test sinh tự động nhưng không loại các file contract sinh cần commit.
- `docker compose config -q` thành công; `docker compose up -d --wait` chạy PostgreSQL 16.15 và
  Redis 7.4.11 ở trạng thái `healthy`. PostgreSQL trả `qros|qros|1`; Redis có xác thực trả `PONG`.
- Hai cổng chỉ bind loopback: PostgreSQL `127.0.0.1:5433`, Redis `127.0.0.1:6379`. Dữ liệu nằm trong
  named volume; mật khẩu mặc định chỉ dành cho dev và có thể ghi đè bằng biến môi trường.
- `.\gradlew.bat test` thành công với 10/10 test hiện có. Thư mục làm việc hiện không có metadata
  `.git`, nên chưa có commit; quy tắc mỗi commit tham chiếu `FR-*` vẫn áp dụng khi repo Git được gắn lại.

`BL-M0-02` hoàn tất ngày 10/09/2026:

- Backend dùng Spring Boot 4.1.1, bản stable hỗ trợ Java 25 và Gradle 9.x. Version được quản lý tại
  `backend/gradle/libs.versions.toml` và root `settings.gradle.kts` nạp catalog này.
- `com.qros.QrosApplication` là entrypoint. Tiến trình chuẩn hoá timezone hạ tầng thành UTC trước khi
  Spring khởi động để PostgreSQL không nhận alias hệ điều hành `Asia/Saigon`; timezone nghiệp vụ vẫn
  lấy từ từng chi nhánh (`Asia/Ho_Chi_Minh` trong schema).
- `application.yml` đặt graceful shutdown, không lộ chi tiết lỗi, tắt Open Session in View và tách
  Actuator sang cổng `8081`. Chỉ `health`/`info` được expose; liveness/readiness có tại
  `/actuator/health/liveness` và `/actuator/health/readiness`.
- Profile `dev` dùng PostgreSQL/Redis của Compose và Flyway. Lần chạy thật đã áp dụng V1 thành công:
  38 bảng nghiệp vụ, 4 role, 53 role-permission, 0 constraint chưa validate và có history V1 thành công.
- Profile `test` khởi động độc lập, không dùng cơ sở dữ liệu nhúng; integration test có dữ liệu về sau
  vẫn phải dùng PostgreSQL/Redis thật qua Testcontainers. Profile `prod` yêu cầu đủ `QROS_DB_URL`,
  `QROS_DB_USERNAME`, `QROS_DB_PASSWORD`, `QROS_REDIS_URL`, không có bí mật hay mật khẩu yếu mặc định;
  phép thử thiếu các biến này đã từ chối khởi động.
- `test` và `dev` đều đã chạy thật; hai probe trả `UP`, còn `/actuator/health` trên cổng ứng dụng trả
  `404`. `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành công: 11 test, 0 lỗi,
  tạo executable jar; H2 không có trong runtime classpath.

`BL-M0-03` hoàn tất ngày 11/09/2026. Phần build/test: `.github/workflows/ci.yml`
dùng Java 25, xác minh Gradle Wrapper, dùng cache dependency qua `gradle/actions/setup-gradle`, rồi luôn
chạy `./gradlew clean test bootJar --no-daemon`; `clean` bảo đảm cache không biến test/build thành
`UP-TO-DATE`. Workflow chỉ có quyền đọc source, có timeout và huỷ lượt cũ trên cùng ref.

Phần contract backend của `BL-M0-03` đã hoàn tất: OpenAPI Generator 7.22.0 validate
`docs/api/openapi.yaml`, sinh 9 interface + 35 model vào `backend/build/generated/openapi`, và
`compileJava` phụ thuộc cổng `verifyGeneratedOpenApi`. Cổng này chặn cả trường hợp generator báo thành
công nhưng sinh rỗng. Đường dẫn spec dùng file URI để hoạt động cả khi workspace Windows có dấu cách;
OpenAPI 3.1 đã bổ sung `license.identifier` bắt buộc và validate xanh.

Phần contract web/Python của `BL-M0-03`: `openapi-typescript` 7.13.0 sinh type thuộc loại phải commit
cho `web-guest/src/types/api.d.ts` và `web-staff/src/types/api.d.ts`; `datamodel-code-generator` 0.71.0
sinh `ml/models/generated.py` theo Pydantic v2/Python 3.13, tắt timestamp để kết quả xác định. Phiên bản
được ghim bằng `package-lock.json` và `ml/requirements-codegen.txt`. Spectral 6.16.3 kiểm tra cả
OpenAPI/AsyncAPI cùng hai luật riêng bắt buộc `operationId` và `x-requirements`; lint hiện có 0 error,
0 warning (chỉ còn thông tin khuyến nghị AsyncAPI 3.1, chưa tự nâng hợp đồng 3.0 đã baseline).

Phần chống trôi của `BL-M0-03`: `.github/workflows/contract-check.yml` cài generator từ lockfile,
buộc chạy lại backend generator bằng `--rerun-tasks`, sinh lại hai file TypeScript và model Python,
biên dịch model Python rồi chạy `git diff --exit-code` trên ba artifact phải commit. `.gitattributes`
ép LF để kết quả không trôi giữa Windows/Linux. Actionlint 1.7.12 xác nhận cả hai workflow hợp lệ.

Kiểm chứng cuối `BL-M0-03`: `npm ci` và `npm audit` thành công với 0 vulnerability; Spectral có
0 error/0 warning; Java generator tạo 9 interface + 35 model; `./gradlew clean test bootJar
--rerun-tasks --no-daemon` chạy 11/11 task và 11/11 test thành công. Sinh lại ba artifact giữ nguyên
SHA-256; một Git repository tạm chạy chính lệnh `git diff --exit-code` trả mã 0. Workspace chính vẫn
không có metadata `.git`, nên hai workflow chưa thể được kích hoạt trên GitHub trong máy này.

`BL-M0-04` hoàn tất ngày 13/09/2026. Shared kernel gồm bốn gói dưới `backend/src/main/java/com/qros/shared/`:

- `money/Money` là record `(long amount, Currency currency)` với `plus`/`minus`/`times` dùng
  `Math.*Exact` nên tràn số báo lỗi thay vì quay vòng. Không có phép chia: quy tắc làm tròn phần dư
  chỉ được chốt cùng `FR-CUS-15` (`OPEN-06`). `Currency` mới có `VND`, khớp enum trong hợp đồng.
- `id/UuidV7` sinh UUIDv7 theo RFC 9562 (JDK 25 chưa có API này). `generate()` tăng dần nghiêm ngặt
  toàn tiến trình nhờ bộ đếm 12 bit, tràn thì mượn mili giây; `generate(Instant)` dành cho fixture và
  dữ liệu nạp lại nên giữ nguyên mốc thời gian và không đụng bộ đếm đó.
- `error/` có `ErrorCode` (bảng ánh xạ mã ổn định → mã HTTP → tiêu đề tiếng Việt, lấy đúng mã HTTP mà
  `openapi.yaml` ghi cho từng mã), `QrosException`, `ProblemDetailFactory` là chỗ duy nhất dựng
  `ProblemDetail`, `GlobalExceptionHandler` ghi đè `handleExceptionInternal` để cả ngoại lệ của Spring MVC
  cũng bị dựng lại thân phản hồi, và `ProblemErrorController` thay `BasicErrorController` cho nhánh lỗi
  ném từ filter.
- `web/` có `CorrelationId` (MDC), `CorrelationIdFilter` chạy ở `HIGHEST_PRECEDENCE` cho
  `REQUEST/ERROR/ASYNC`, và cổng `TraceIdProvider` để `BL-M0-11` thay bằng trace ID OpenTelemetry mà
  không phải sửa `shared/error`.

Kiểm chứng ngày 13/09/2026: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành công,
45/45 test, 0 lỗi, tạo executable jar. `ProblemDetailsIntegrationTest` chạy trên Tomcat thật qua cổng HTTP
ngẫu nhiên và kiểm cả tám nhánh: lỗi nghiệp vụ, ngoại lệ không lường trước, sai lược đồ, payload hỏng,
sai phương thức, đường dẫn không tồn tại, lỗi ném từ filter, và phản hồi thành công — tất cả trả
`application/problem+json` có `code` lẫn `traceId`, không lộ tên class hay chuỗi kết nối. Hai luật
ArchUnit mới (`MoneyTypeTest`, `ProblemResponseTest`) có kèm test hành vi chứng minh luật bắt được vi phạm,
vì `archunit.properties` đặt `failOnEmptyShould=false` nên luật rỗng sẽ xanh giả.

Hai điểm còn treo sau `BL-M0-04`: correlation ID hiện đóng vai `traceId` cho tới khi `BL-M0-11` bật
OpenTelemetry; và `TM-OPS-02` vẫn ở trạng thái `Kế hoạch` vì phần Actuator/security header thuộc
`BL-M0-13` — `ProblemDetailsIntegrationTest` mới phủ nhánh rò rỉ qua thân phản hồi lỗi.

`BL-M0-05` hoàn tất ngày 13/09/2026. Outbox nằm ở `backend/src/main/java/com/qros/shared/event/`:

- `OutboxWriter.append()` **từ chối chạy ngoài giao dịch** (`TransactionSynchronizationManager`),
  nên không có đường nào ghi sự kiện tách khỏi dữ liệu sinh ra nó (`ADR-05`).
- `OutboxPoller` nhận việc bằng `FOR UPDATE SKIP LOCKED`, phát rồi mới đánh dấu `published_at`,
  tất cả trong một giao dịch. Redis lỗi thì giao dịch cuộn ngược và dòng quay lại hàng đợi. Hệ quả
  là **at-least-once**, client khử trùng lặp bằng `eventId` (`TM-EVT-01`).
- Ranh giới giao dịch của poller mở bằng `TransactionTemplate` chứ không phải `@Transactional`:
  luật ArchUnit giữ annotation đó cho tầng `service` của module nghiệp vụ, nơi mỗi giao dịch là một
  use case. Poller là hạ tầng, không phải use case.
- `OutboxScheduler` tách khỏi poller để test gọi được từng lượt; nó nuốt ngoại lệ có chủ ý, vì để
  ngoại lệ thoát ra thì Spring huỷ lịch và outbox đứng im vĩnh viễn sau một lần Redis chớp tắt.
- Kênh Redis đặt theo phạm vi người nghe: `qros:session:{sessionId}` thắng `qros:store:{storeId}`,
  còn lại là `qros:global`. Đã ghi vào SDD mục 5.
- Bảng `outbox_event` của `V1` không có cột `event_id`, nên `eventId` được lưu trong `payload` lúc
  ghi — giữ nguyên qua mọi lần phát lại, và không phải đổi lược đồ đã baseline.
- `OutboxWriter` cắt `occurredAt` về micro giây vì `timestamptz` chỉ giữ tới đó; không cắt thì giá
  trị bên gọi cầm và giá trị phát ra lệch nhau đúng phần nano bị bỏ đi.
- `OutboxConfiguration` chỉ nạp khi có `spring.datasource.url`. Điều kiện đặt trên property chứ
  không trên bean `DataSource`: điều kiện kiểu bean trong cấu hình ứng dụng được đánh giá **trước**
  auto-configuration nên sẽ luôn sai. Nhờ vậy profile `test` vẫn khởi động độc lập như `BL-M0-02`.

Kiểm chứng ngày 13/09/2026: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành
công, 56/56 test, 0 lỗi. `com.qros.integration.OutboxDeliveryTest` chạy trên PostgreSQL 16 và Redis 7
thật qua Testcontainers 2.0.5 (`org.testcontainers:testcontainers-postgresql`, coordinate đã đổi ở
2.x), phủ bảy ca: cuộn ngược giao dịch thì không còn sự kiện, ghi ngoài giao dịch bị từ chối,
`seq` chính là khoá chính và tăng dần, phát đúng kênh theo phạm vi, phong bì đủ trường theo AsyncAPI,
Redis lỗi thì `published_at` vẫn null và lượt sau phát lại đúng `eventId`, và sự kiện đã phát không
bị phát lại. `OutboxSchedulerTest` kiểm riêng nhịp `@Scheduled` — phần duy nhất mà lỗi cấu hình sẽ
làm outbox đứng im ở dev/prod mà không test nào khác thấy.

Hai điều rút ra khi chạy thật: test không đi qua `QrosApplication.main()` nên JVM giữ alias
`Asia/Saigon` của Windows và PostgreSQL từ chối kết nối — `tasks.test` nay đặt `user.timezone=UTC`.
Container dùng chung qua `QrosIntegrationTest` (mẫu singleton) thay vì `@Testcontainers` theo từng
class. `TM-EVT-01` vẫn ở trạng thái `Kế hoạch` vì còn thiếu `e2e/kds-concurrency.spec.ts` thuộc
`BL-M1-04`; nửa `integration/OutboxDeliveryTest` đã xanh.

`BL-M0-06` hoàn tất ngày 13/09/2026. Idempotency nằm ở `backend/src/main/java/com/qros/shared/idempotency/`
và **chỉ dùng PostgreSQL**, không dùng Redis:

- `IdempotencyRepository.claim` là một câu `INSERT ... ON CONFLICT (key) DO UPDATE ... WHERE
  expires_at <= now()`. Hai tính chất nằm trong đúng câu này: PostgreSQL bắt lượt thứ hai **chờ**
  khi lượt đầu chưa commit, nên hai yêu cầu song song không thể cùng chạy tác dụng phụ mà không cần
  khoá phân tán; và nhánh `DO UPDATE` cho phép chiếm lại khoá đã quá 24 giờ, nếu chỉ `DO NOTHING`
  thì khoá hết hạn sẽ vĩnh viễn tự chặn chính nó.
- `IdempotencyGuard.execute` chạy trong giao dịch nghiệp vụ của lượt gọi và từ chối nếu không có
  giao dịch. Nghiệp vụ hỏng thì khoá biến mất theo, nếu không một lần lỗi đầu tiên sẽ chặn khách
  đặt món suốt 24 giờ.
- Vân tay yêu cầu là SHA-256 trên `scope + sessionId + body`. Gộp cả ba có chủ ý: "khoá của phiên
  khác" và "nội dung khác" cho ra cùng một lỗi, không có tín hiệu nào để dò sự tồn tại của khoá
  thuộc bàn khác (bất biến số 7).
- `IDEMPOTENCY_KEY_REUSED` trả `422`, theo đúng chú thích trong `V1__baseline.sql`.
- `IdempotencySweeper` (logic) tách khỏi `IdempotencySweepScheduler` (nhịp chạy) như cặp
  `OutboxPoller`/`OutboxScheduler`; khoá hết hạn không ảnh hưởng tính đúng đắn nên dọn dẹp chỉ để
  bảng khỏi phình.

**Vì sao không có Redis ở đây.** Tiêu chí nghiệm thu của thẻ đạt đủ bằng PostgreSQL, trong khi đưa
Redis vào đường idempotency buộc phải chốt `OPEN-02` (fail-open hay fail-closed khi Redis lỗi) —
fail-open thì trùng đơn, fail-closed thì Redis chết là khách không đặt được món. `OPEN-02` nay chỉ
còn ảnh hưởng `BL-M1-01` và giới hạn tần suất; đã cập nhật lại trong `docs/backlog.md`.

Kiểm chứng ngày 13/09/2026: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành
công, 69/69 test, 0 lỗi. `com.qros.integration.IdempotencyTest` chạy trên PostgreSQL thật, phủ chín ca
gồm tám luồng song song cùng một khoá (đúng một lượt chạy tác dụng phụ), khoá quá hạn được dùng lại,
tác dụng phụ hỏng thì khoá không bị chiếm chỗ, và khoá của phiên khác trả đúng lỗi như nội dung sai.
Đồng hồ tách thành bean `Clock` để test tua qua cửa sổ 24 giờ thay vì chờ thật.

**Hai lỗ hổng hợp đồng cần vá ở `BL-M1-03`, trước khi viết endpoint đặt món.** Phần mô tả của
`createOrder` trong `openapi.yaml` nói lần gửi lặp trả `200`, nhưng bảng `responses` của operation đó
chỉ khai báo `201/400/409/429` — không có `200`. Và `422 IDEMPOTENCY_KEY_REUSED` cũng chưa được khai
báo ở bất kỳ operation nào có `Idempotency-Key`. Kernel hiện phát lại **đúng mã HTTP đã lưu** (tức
`201`), là mặc định an toàn; chọn `200` hay `201` là quyết định của hợp đồng, phải sửa YAML trước rồi
mới sinh lại code.

`BL-M0-07` hoàn tất ngày 13/09/2026. Bốn chuỗi filter ở `backend/src/main/java/com/qros/shared/security/`:
`/api/v1/guest/**`, `/api/v1/staff/**`, `/api/v1/admin/**`, và một chuỗi bắt phần còn lại **từ chối
mặc định** (chỉ mở `/error`, probe `health`/`info`, `POST /api/v1/auth/**` và `/api/v1/webhooks/**`).

- `JwtVerifier` là một thực thể cho mỗi vùng, mỗi thực thể một bộ khoá, `issuer` và `audience` riêng.
  Thứ tự kiểm: `alg` phải đúng `EdDSA` (so với hằng số, **không** đọc thuật toán từ token) → chọn khoá
  theo `kid` trong bộ khoá của chính vùng → xác minh chữ ký → `iss`/`aud` → hạn với dung sai 60 giây.
- **Không dùng Tink.** Nimbus chỉ xác minh Ed25519 qua Tink, thứ kéo theo protobuf và gson vào runtime
  cho đúng một thuật toán. JDK 25 có sẵn Ed25519; `Ed25519PublicKeys` chỉ bọc 32 byte của JWK vào vỏ DER
  `SubjectPublicKeyInfo` rồi để `KeyFactory` dựng khoá — không có phép toán đường cong nào tự viết.
- Vùng khách nhận token ở header nên tắt CSRF; hai vùng còn lại nhận token trong cookie `qros_session`
  theo `NFR-SEC-05` nên bật CSRF.

Ba cái bẫy chỉ lộ ra khi chạy thật, đều đã sửa và đều có test giữ lại:

1. Khai báo `BearerTokenResolver` thành bean khiến Spring Security áp nó cho **mọi** chuỗi resource
   server — vùng khách âm thầm đi đọc cookie và bỏ qua header. Mỗi vùng nay tự dựng bộ đọc của mình.
2. `oauth2ResourceServer` **tự miễn CSRF** cho mọi request mà nó nhận ra có bearer token, và miễn trừ
   đó cộng dồn bằng `AND` nên không gỡ được từ DSL. Với token nằm trong cookie thì đó đúng là ca cần
   bảo vệ. Hai vùng cookie vì vậy dùng `CookieSessionAuthenticationFilter` tường minh thay cho
   `oauth2ResourceServer`.
3. `JwtException` trần bị Spring dịch thành `500`; token xấu phải là `BadJwtException` để ra `401`.
   Ném nhầm loại thì lưu lượng tấn công bình thường lại báo động như sự cố máy chủ.

Kiểm chứng ngày 13/09/2026: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành
công, 86/86 test, 0 lỗi. `security/JwtAlgorithmConfusionTest` (10 ca) và `security/SecurityChainSeparationTest`
(7 ca) chạy qua HTTP thật: `alg:none`, HMAC ký bằng khoá công khai, token vùng khác, sai `kid`/`iss`/`aud`,
chữ ký bị sửa, hết hạn ngoài dung sai — tất cả `401`; hết hạn trong dung sai 60 giây vẫn đi qua; ghi ở
vùng nhân viên/quản trị thiếu token CSRF trả `403`; đường dẫn lạ trả `403` chứ không phải `404`.

Chạy thử bản đóng gói với profile `prod` (PostgreSQL/Redis qua Compose): thiếu `QROS_JWT_*` thì tiến
trình từ chối khởi động; đủ biến thì `Started QrosApplication`, probe `8081` trả `UP` không cần xác thực,
`/actuator/health` trên cổng ứng dụng trả `404`, `/api/v1/guest/menu` không token trả `401` RFC 7807.
Chính lượt chạy này lộ ra lỗi thứ tư: hai lớp tự ghi thẳng ra response dùng charset mặc định ISO-8859-1
nên tiêu đề tiếng Việt về tay khách thành dấu hỏi. Đã ép UTF-8 và bổ sung assert có dấu vào test —
test cũ chỉ so chuỗi ASCII nên không thấy.

`OPEN-05` (allowlist IP cho admin) và MFA bắt buộc cho quyền ghi (`FR-AUTH-02`, `BL-M0-09`) cố ý chưa
có trong `AdminSecurityConfig`; đã ghi chú ngay trong lớp đó.

Kiểm tra chéo tài liệu xác nhận đủ 20/20 threat có test mapping; test mang trạng thái `Kế hoạch` chưa
được tính là đã kiểm chứng. Tính tới 14/09/2026, bảng test của threat model đã có `TM-ORD-02`,
`TM-AUTH-02`, `TM-AUTH-01`, `TM-AUTH-03` và `TM-REP-01` xanh, cùng ba dòng `Một phần` (`TM-INJ-01`,
`TM-EVT-01`, `TM-OPS-02`); trạng thái chỉ được đổi khi có kết quả kiểm chứng kèm ngày, phiên bản
công cụ và môi trường.

Hai hạng mục từng chưa được kiểm chứng đã hoàn tất ngày 10/09/2026:

- `V1__baseline.sql` chạy thành công trên PostgreSQL 16 với `ON_ERROR_STOP=1`: tạo 38 bảng,
  không có constraint chưa validate, seed 4 role và 53 role-permission.
- `./gradlew test` chạy thành công 10 test ArchUnit/JUnit trên Java 25 và Gradle 9.1.0.
  Bộ test có cả ca chứng minh truy cập nội bộ cùng module được phép và truy cập chéo vào ruột
  module bị chặn.

`BL-M0-08` hoàn tất ngày 14/09/2026. Identity domain nằm ở `backend/src/main/java/com/qros/identity/`,
chỉ ba package `controller/service/domain/repository` — chưa có `api` vì chưa module nào khác cần gọi
vào identity, thêm khi có nhu cầu thật thay vì đoán trước:

- `AppUser` (domain) tự giữ bất biến khoá luỹ tiến của `FR-AUTH-03`, không phải service:
  `registerFailedAttempt`/`registerSuccessfulLogin` là hành vi của chính entity, cùng triết lý
  `Order`/`OrderStateMachine`. Hai khái niệm tách bạch: **cửa sổ dồn lỗi** (`failedAttempts`/
  `lastFailedAt`, chỉ đếm 5 lần sai liên tiếp trong 15 phút) và **luỹ tiến** (`lockoutCount`, không
  reset theo thời gian, chỉ về 0 khi đăng nhập thành công — thời gian khoá là `15 phút × 2^(lockoutCount-1)`,
  trần 24 giờ). Hai cột mới `last_failed_at`, `lockout_count` thêm ở `V2__auth_lockout_window.sql`
  vì `V1` không đủ chỗ lưu hai khái niệm này tách biệt.
- `AuthenticationService.login` chạy Argon2id thật (`m=64 MiB, t=3, p=4`, qua
  `Argon2PasswordEncoder` của Spring Security — cần thêm `org.bouncycastle:bcprov-jdk18on` vì
  Spring Security khai báo BouncyCastle là dependency tuỳ chọn) trên một **hash giả cố định** khi
  email không tồn tại, để thời gian phản hồi giống hệt nhánh "email có thật nhưng sai mật khẩu" —
  `TM-AUTH-01`. `@Transactional(noRollbackFor = QrosException.class)` là điểm dễ bỏ sót nhất: mặc
  định Spring rollback trên mọi `RuntimeException`, nên nếu thiếu dòng này thì lần ghi nhận sai
  (tăng `failedAttempts`) sẽ bị cuốn trôi cùng lúc với chính ngoại lệ báo lỗi — khoá luỹ tiến sẽ
  không bao giờ tích luỹ được, kể cả khi logic trong `AppUser` đúng tuyệt đối. Bug này từng lộ ra
  ở `CredentialStuffingTest` trước khi sửa.
- `JwtIssuer` (`shared/security`, phép toán ngược của `JwtVerifier`) và `Ed25519PrivateKeys`
  (ngược của `Ed25519PublicKeys`) phát token vùng **staff** — ký bằng JDK `Signature "Ed25519"`
  thật, không dùng bộ ký EdDSA của Nimbus, cùng lý do đã chốt ở `BL-M0-07`: tránh kéo Tink vào
  runtime. `SecurityZoneProperties.Zone` có thêm `signingKey` (kid + d), chỉ vùng staff có giá trị.
  Token phát ra chỉ thuộc vùng staff (audience `qros-staff`) bất kể vai trò — vùng admin dùng khoá
  hoàn toàn khác nên một token không tự nhiên dùng được ở cả hai vùng; cách `STORE_MANAGER`/`ADMIN`
  lấy token vùng admin chưa thiết kế, xem `OPEN-07`.
- `POST /api/v1/auth/refresh` triển khai tối thiểu: luôn trả `401 UNAUTHENTICATED` vì `BL-M0-08`
  không phát refresh token riêng nào để xoay vòng — xoay vòng có phát hiện tái sử dụng
  (`TM-AUTH-03`) là `BL-M0-09`. Trường `totp` của yêu cầu đăng nhập cũng bị bỏ qua có chủ ý: chưa
  có màn hình đăng ký MFA nào bật được `mfa_enabled`, nên nhánh đó hiện không thể chạm tới.
- `AuthenticationService`/`AuthController` chỉ nạp khi có `DataSource`
  (`@ConditionalOnProperty("spring.datasource.url")`), cùng lý do với `OutboxConfiguration`/
  `IdempotencyConfiguration`: thiếu điều kiện này thì profile `test` (không có PostgreSQL) hỏng
  ngay lúc autowire. `staffJwtIssuer` thì luôn nạp (không cần DataSource) nhưng đòi `signingKey`
  bắt buộc — mọi profile kể cả `test` đều cần một cặp khoá staff hợp lệ chỉ để context khởi động
  được, nên `QrosIntegrationTest` (nền chung của mọi test tích hợp) nay tự sinh và đăng ký một cặp
  khoá Ed25519 tự nhất quán, không riêng test nào phải tự lo việc này.
- `app_user.email` là `citext` ở CSDL; entity phải khai `columnDefinition = "citext"` trên
  `@Column`, nếu không `ddl-auto: validate` so nó với `varchar` mặc định của `String` và từ chối
  khởi động — bẫy chỉ lộ ra khi chạy trên PostgreSQL thật, không lộ lúc biên dịch.

Kiểm chứng ngày 14/09/2026: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành
công, 95/95 test, 0 lỗi. `security/CredentialStuffingTest` chạy trên PostgreSQL thật qua
Testcontainers 2.0.5, phủ bốn ca: sai mật khẩu và email không tồn tại trả về thân phản hồi giống hệt
nhau (chỉ khác `traceId`); khoá sau đúng lần sai thứ năm; khoá lần hai gấp đôi thời lượng lần đầu sau
khi tua đồng hồ qua khỏi lần khoá trước; đăng nhập đúng cấp cookie `HttpOnly; Secure; SameSite=Strict;
Path=/api`, token giải mã có `stores` chứa cả UUID chi nhánh lẫn `"*"` (hàng `user_role` toàn tổ
chức), và cookie đó gọi được `/api/v1/staff/ping` thật. `identity/domain/AppUserTest` (5 ca, không
Spring) kiểm riêng máy trạng thái khoá luỹ tiến, tách khỏi HTTP/CSDL.

Chạy thử bản đóng gói: profile `dev` qua Compose — đăng nhập thật trả cookie, token giải mã đúng
`scope: staff`, cookie gọi được vùng staff (`404` vì chưa có controller nghiệp vụ, không phải `401`),
lockout thật sau 5 lần sai qua HTTP. Profile `prod` — thiếu `QROS_JWT_STAFF_D` thì từ chối khởi động
đúng thông điệp trỏ tới biến môi trường; đủ biến (kể cả cặp khoá staff mới) thì `Started
QrosApplication` bình thường.

`BL-M0-09` hoàn tất ngày 14/09/2026. Bốn mảnh việc, vẫn trong `identity/`:

- **Token version** (`FR-AUTH-05`): `shared/security/TokenVersionValidator` là cổng trừu tượng
  (giống `TraceIdProvider`) để `CookieSessionAuthenticationFilter` kiểm claim `tv` mà không phụ
  thuộc nghiệp vụ; `identity.service.TokenVersionValidatorImpl` (`@Primary`, chỉ nạp khi có
  `DataSource`) tra `app_user.token_version` thật trên **mỗi** request đã xác thực. `AppUser.bumpTokenVersion()`
  đã sẵn sàng nhưng chưa có endpoint nào gọi tới — đổi mật khẩu/vai trò/thu hồi quyền vẫn chưa tồn
  tại (M3).
- **MFA** (`FR-AUTH-02`): `shared/security/Totp` tự cài RFC 6238/4226 bằng `HmacSHA1` của JDK,
  không kéo thư viện ngoài. `identity.service.MfaService.enable()` bật MFA và phát 10 mã dự phòng
  (SHA-256, tiêu một lần qua `UPDATE ... WHERE used_at IS NULL` cùng kỹ thuật
  `IdempotencyRepository.claim`) — **chưa có endpoint đăng ký tự phục vụ** (`OPEN-09`), test gọi
  thẳng service. `MfaEnforcementFilter` (dùng chung cho vùng staff/admin) đọc claim `mfaBlocked`
  (tính lúc đăng nhập: có vai trò `STORE_MANAGER`/`ADMIN` và chưa `mfa_enabled`) để chặn mọi
  phương thức ghi, không chặn đọc. Sai TOTP tính là một lần sai như sai mật khẩu, đi chung đường
  khoá luỹ tiến của `BL-M0-08` — nếu không TOTP sẽ là kênh dò không giới hạn số lần thử.
- **Refresh rotation** (`TM-AUTH-03`): bảng `refresh_token` (`V3__mfa_refresh_shift.sql`) — chuỗi
  ngẫu nhiên đối chiếu CSDL, không phải JWT, vì JWT ký sẵn không thu hồi được trước hạn.
  `identity.service.RefreshTokenService.rotate()` giành quyền tiêu token qua
  `UPDATE ... WHERE used_at IS NULL AND revoked_at IS NULL` (chặn đua song song), và phân biệt hai
  loại thất bại: không tìm thấy/hết hạn tự nhiên → `UNAUTHENTICATED`; đã tiêu hoặc đã thu hồi (kể
  cả thua trong đua song song) → `REFRESH_REUSED` **và thu hồi cả `family_id`**. `/auth/login` giờ
  phát cả hai cookie: `qros_session` (`Path=/api`, 15 phút) và `qros_refresh` (`Path=/api/v1/auth`
  — hẹp hơn có chủ ý, giảm bề mặt lộ — 7 ngày, con số tự chọn chưa chốt trong PRD).
- **Ca làm** (`FR-AUTH-04`): chỉ phần đóng ca tự động sau 12 giờ — `identity.service.WorkShiftAutoCloser`
  (poller pattern như `OutboxPoller`) chạy một câu `UPDATE work_shift SET closed_at = now() WHERE
  closed_at IS NULL AND opened_at < now() - 12h`. Mở ca bằng PIN trên "thiết bị đã đăng ký" **chưa
  có endpoint** — đăng ký thiết bị dùng chung chưa có UX nào được chốt, xem `OPEN-08`.

Hai bẫy chỉ lộ ra khi chạy test thật, cả hai đều là cùng một loại lỗi lặp lại hai lần độc lập:
`@Transactional` mặc định rollback trên mọi `RuntimeException`, kể cả khi ngoại lệ đó
(`QrosException`) đi kèm một thay đổi **đã đúng** cần giữ lại (tăng bộ đếm khoá, thu hồi
`family_id`). `noRollbackFor = QrosException.class` phải đặt ở **cả** phương thức mở giao dịch —
`propagation = REQUIRED` (mặc định) khiến lời gọi lồng nhau nhập chung một giao dịch vật lý, và
quy tắc rollback thật sự áp dụng là quy tắc của phương thức **mở** giao dịch đó, không phải
phương thức bên trong ném ngoại lệ. Thiếu ở tầng ngoài (`AuthenticationService.refresh` gọi
`RefreshTokenService.rotate`) thì `noRollbackFor` ở tầng trong vô nghĩa — `RefreshTokenReuseTest`
lộ ra bằng cách token bị thu hồi vẫn dùng lại được ở lượt gọi kế tiếp.

Kiểm chứng ngày 14/09/2026: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành
công, 107/107 test, 0 lỗi. Bốn test tích hợp mới, tất cả trên PostgreSQL 16 thật qua Testcontainers
2.0.5: `security/MfaEnforcementTest` (5 ca — manager chưa MFA bị chặn ghi nhưng vẫn đọc được,
barista không cần MFA, sai TOTP tính vào khoá luỹ tiến, TOTP đúng gỡ chặn, backup code dùng một
lần); `security/RefreshTokenReuseTest` (4 ca — xoay vòng hợp lệ, phát lại token đã tiêu thu hồi cả
family kể cả token mới nhất chưa từng bị dùng sai, token không tồn tại trả `UNAUTHENTICATED` khác
`REFRESH_REUSED`, thiếu cookie trả `UNAUTHENTICATED`); `integration/WorkShiftAutoCloseTest` (3 ca);
`identity/domain/AppUserTest` không đổi từ `BL-M0-08`. `V3__mfa_refresh_shift.sql` và
`V4__work_shift_cascade.sql` (thêm `ON DELETE CASCADE` cho `work_shift` — thiếu nó thì test khác
xoá `app_user` sẽ vỡ FK nếu có ca làm còn treo, một lỗi cách ly test lộ ra khi chạy cả bộ cùng lúc
chứ không lộ khi chạy từng file) đã áp dụng thành công trên cả Testcontainers lẫn Postgres dev qua
Compose.

Chạy thử bản đóng gói profile `dev`: đăng nhập trả cả hai cookie, token giải mã có `mfaBlocked`
đúng theo vai trò và tình trạng MFA; xoay vòng refresh thật qua HTTP — token cũ bị thu hồi ngay cả
sau khi đã có token mới thay thế, đúng ngữ nghĩa "thu hồi cả family". Một bài học vận hành: tiến
trình nền bỏ sót từ một lượt `taskkill` trước đó (gọi qua Git Bash, `//F` bị dịch sai đường dẫn)
vẫn giữ cổng `8080`, khiến lượt build mới không khởi động được và lượt smoke test kế tiếp âm thầm
kiểm tra nhầm bản build cũ — dùng `Stop-Process` của PowerShell thay cho `taskkill` qua Git Bash
đáng tin hơn trên máy này.

`BL-M0-10` hoàn tất ngày 14/09/2026. Module `audit` nằm ở `backend/src/main/java/com/qros/audit/`,
bốn package `api/domain/repository/service` — có `api` ngay từ đầu, khác `identity`, vì lý do tồn
tại của module này chính là để module khác gọi vào:

- `audit.api.AuditRecorder`/`AuditEntry` là mặt tiếp xúc duy nhất; `AuditEntry` không tham chiếu
  `AuditEvent` (entity), đúng luật `entity_khong_ro_ra_api`.
- **Bất biến "chỉ ghi thêm" được cưỡng chế ở cấp kiểu, không phải quy ước.**
  `AuditEventRepository` kế thừa `Repository<AuditEvent, Long>` trơn (marker rỗng của Spring Data)
  thay vì `JpaRepository`/`CrudRepository` — hai interface đó có sẵn `save`/`delete`, kế thừa
  chúng là để hở đúng cánh cửa `TM-REP-01` cấm. Ghi duy nhất qua mảnh `AuditEventWriter.append`
  (tên file `AuditEventWriterImpl`, Spring Data tự nhận theo quy ước hậu tố `Impl`), gọi thẳng
  `EntityManager.persist` — không bao giờ `merge`. `ModuleBoundaryTest` có thêm luật
  `audit_chi_ghi_them`: không lớp nào trong `audit.repository` được `assignableTo(CrudRepository)`,
  giữ bất biến này không bị nới lỏng khi có người sau này "tiện tay" đổi sang `JpaRepository`.
- `traceId` lấy từ `TraceIdProvider` (đã có sẵn từ `BL-M0-04`) chứ không phải tham số gọi vào —
  đây là ngữ cảnh của lượt gọi, không phải điều module gọi cần tự mang theo, cùng cách
  `ProblemDetailFactory` lấy `traceId` cho lỗi.
- Cột `ip_address` (`inet`) của `audit_event` chưa map — chưa có nơi gọi nào có IP khách thật để
  ghi (webhook/thao tác quản trị đều thuộc M2/M3).
- **Chưa có caller thật.** Refund, sửa giá, quản trị nhân viên — những nơi PRD đòi ghi audit — đều
  chưa có endpoint (M2/M3). `AuditService` được test gọi thẳng qua `AuditRecorder`, cùng cách
  `MfaService.enable` chưa có endpoint đăng ký ở `BL-M0-09`. Trình xem audit log lọc theo
  người dùng/hành động/khoảng thời gian (`FR-MGT-12`) là M3, không phải thẻ này.

Một bẫy schema lộ ra khi chạy cả bộ test cùng lúc: `audit_event.actor_id` tham chiếu `app_user(id)`
không `CASCADE` (khác `work_shift` đã sửa ở `BL-M0-09`), nên xoá một tài khoản còn dấu vết audit bị
FK chặn — đúng ngay chỗ test khác gọi `appUserRepository.deleteAll()`. Khác với `work_shift`
(CASCADE hợp lý vì ca làm không có giá trị độc lập với tài khoản), ở đây `V5__audit_event_actor_fk.sql`
đổi thành `ON DELETE SET NULL`: dòng audit phải sống sót qua việc xoá tài khoản — xoá tài khoản
không được phép xoá luôn bằng chứng của việc tài khoản đó đã làm. `actor_role` vẫn còn vì đó là ảnh
chụp lúc hành động, không phải tham chiếu sống.

Kiểm chứng ngày 14/09/2026: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành
công, 113/113 test, 0 lỗi. `security/AuditImmutabilityTest` (5 ca, PostgreSQL thật qua
Testcontainers 2.0.5): ghi đủ actor/vai trò/phạm vi/trước/sau/lý do; hành động hệ thống không actor
vẫn ghi được; ghi hai lần cho cùng một đối tượng tạo hai dòng độc lập (không ghi đè); tra theo actor
đúng dòng; và một ca dùng reflection xác nhận `AuditEventRepository` không có bất kỳ phương thức
nào tên `save/delete/deleteById/deleteAll/update`. `V5__audit_event_actor_fk.sql` áp dụng thành
công trên cả Testcontainers lẫn Postgres dev qua Compose.

`BL-M0-11` hoàn tất ngày 14/09/2026. Ba mảnh, không có module nghiệp vụ mới:

- **OpenTelemetry thật thay correlation ID tạm** (`NFR-OBS-01`). Thêm
  `org.springframework.boot:spring-boot-starter-opentelemetry` (starter chính thức của Spring Boot
  4, tự cấu hình `Tracer` bắc cầu Micrometer Tracing → OTel). `CorrelationIdTraceIdProvider` bị xoá,
  thay bằng `OtelTraceIdProvider` (`shared/web`) đọc `Tracer.currentSpan()`. Xuất OTLP tắt mặc định
  (`management.tracing.export.otlp.enabled`, `management.otlp.metrics.export.enabled`, cả hai
  `false`) vì repo chưa có collector nào — bật thật sẽ chỉ cần biến môi trường, không sửa code.
  **Vỡ hợp đồng có chủ ý:** `traceId` trong lỗi và `X-Correlation-Id` trong header giờ là hai giá
  trị độc lập (trước đây cùng một giá trị) — `ProblemDetailsIntegrationTest` cũ giả định chúng bằng
  nhau, đã sửa lại ba ca cho đúng kiến trúc mới.
- **Log JSON có cấu trúc, che PII ở tầng appender** (`NFR-OBS-03`). `logstash-logback-encoder` +
  `logback-spring.xml` (một cấu hình cho mọi profile, không riêng prod). Trường `message` đi
  qua `MaskedMessageJsonProvider` (`shared/observability`) — ghi đè hẳn provider mặc định của thư
  viện — gọi `PiiMasker.mask()` che mật khẩu/token/JWT/email/số thẻ bằng regex, **không** dùng
  `MaskingJsonGeneratorDecorator` có sẵn của thư viện vì hành vi match-toàn-giá-trị của nó không
  chắc che được PII nằm giữa một câu tự do — tự viết để kiểm soát được chính xác. `MDC` có
  `correlationId` (từ `BL-M0-04`) và nay thêm `actorId` (`CookieSessionAuthenticationFilter` đặt
  sau khi xác thực). `storeId` chưa đặt được — JWT mang một *danh sách* chi nhánh chứ không phải
  một chi nhánh đang hoạt động cho request, xem `OPEN-10`.
- **Runbooks + alert → runbook liên kết được kiểm** (`NFR-OBS-05`). `infra/alerts/qros-alerts.yaml`
  (định dạng luật kiểu Prometheus, **chưa có Prometheus/Alertmanager thật đọc nó** — hạ tầng chưa
  cấp) định nghĩa 4 alert khớp đúng những gì M0 đã xây (đăng nhập thất bại hàng loạt, tái sử dụng
  refresh token, outbox tồn đọng, mất kết nối PostgreSQL), mỗi alert có `runbook_url` trỏ vào
  `docs/runbooks/`. `architecture/AlertRunbookLinksTest` phân tích YAML và xác nhận mọi
  `runbook_url` khớp một file thật — phần của `NFR-OBS-05` kiểm được ngay dù chưa có hạ tầng cảnh
  báo thật. Dashboard Grafana (`NFR-OBS-04`) **chưa làm** — không có Grafana/Prometheus/Loki nào
  trong `docker-compose.yml`, và phần lớn chỉ số nghiệp vụ PRD muốn hiển thị chưa có dữ liệu vì các
  module tương ứng (ordering, aigateway) chưa tồn tại; xem `OPEN-11`.

Việc "che log" ở đây đóng góp vào mitigation "log redaction" của `TM-OPS-01`, nhưng **không** đổi
trạng thái test mapping của `TM-OPS-01` trong threat model — test được gán cho mối đe doạ đó là
gitleaks/Trivy/secret-pattern quét repo/image (`BL-M0-12`), một việc khác với che log lúc chạy.

Kiểm chứng ngày 14/09/2026: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành
công, 124/124 test, 0 lỗi. `shared/observability/PiiMaskerTest` (8 ca, thuần Java không cần Spring)
và `shared/observability/StructuredLoggingTest` (2 ca — gọi thẳng `encoder.encode(event)` của
appender `CONSOLE_JSON` đã nạp thật từ `logback-spring.xml`, không dựng lại cấu hình riêng, không
bắt `System.out` vì `ConsoleAppender` cache tham chiếu stream lúc khởi động nên bắt sau đó không
thấy gì). `architecture/AlertRunbookLinksTest` xanh. Chạy thử bản đóng gói profile `dev`: log JSON
xuất hiện ngay từ dòng khởi động đầu tiên; lỗi `401` thật trả `traceId` dạng 32 ký tự hex W3C
(`5da2af7d4cd1af8217e5589a9ca9e1b3`), khác hẳn định dạng UUID của `X-Correlation-Id` — đúng thiết
kế hai định danh độc lập.

`BL-M0-12` hoàn tất ngày 14/09/2026. Khác các thẻ trước — không phải code Java, mà là pipeline và
một artifact mới ở gốc repo:

- **`Dockerfile`** (gốc repo, mới) — hai giai đoạn: `eclipse-temurin:25-jdk-jammy` build
  `:backend:bootJar`, `eclipse-temurin:25-jre-jammy` chạy, user `qros` không phải root
  (`TM-OPS-01`). Chưa dùng distroless — chưa xác nhận có ảnh distroless cho Java 25, ghi rõ trong
  comment để không phải quyết định lại từ đầu khi xem lại. `.dockerignore` loại phần lớn repo
  nhưng **giữ `docs/api/`** — thiếu nó thì `openApiGenerate` không có spec để đọc, đã tự vấp bẫy
  này một lần khi build thật.
- **`.github/workflows/security.yml`** (mới) — năm job: `secrets` (gitleaks-action), `codeql`
  (Java/Kotlin), `dependency-check` (OWASP, `failBuildOnCVSS = 7.0f` cấu hình thẳng trong
  `backend/build.gradle.kts` qua plugin `org.owasp.dependencycheck`, không phải trong YAML — CVSS
  ngưỡng là thứ code sở hữu, không phải CI sở hữu), `sbom` (CycloneDX, tải lên artifact 90 ngày),
  `image-scan` (build ảnh rồi Trivy quét, `severity: HIGH,CRITICAL`, `exit-code: 1`). Thêm lịch quét
  hàng ngày (`schedule: cron`) vì CVE mới xuất hiện không phụ thuộc có PR hay không.
- **`infra/alerts/qros-alerts.yaml`** (thư mục `infra/` mới, artifact đầu tiên) — 4 alert khớp đúng
  những gì M0 đã xây (không phải danh sách tham vọng): đăng nhập thất bại hàng loạt, tái sử dụng
  refresh token, outbox tồn đọng, mất kết nối PostgreSQL. Mỗi alert có `runbook_url` trỏ vào bốn
  runbook mới ở `docs/runbooks/`. **Chưa có Prometheus/Alertmanager thật đọc file này** — `expr`
  ghi Ý ĐỊNH, không phải PromQL đã chạy được, vì chỉ số nghiệp vụ tương ứng chưa được xuất ra
  (`OPEN-11`).

**Quan trọng — hai thứ được xác nhận chạy thật, không chỉ đọc cấu hình:**

1. Docker image dựng thành công (`docker build`, ~2.5 phút, 610 MB) và **chạy thành công** trên
   mạng `docker-compose` thật, nối PostgreSQL/Redis dev qua tên service (`postgres`/`redis`), Flyway
   nhận diện đúng schema đã ở v5, `Started QrosApplication` — image không chỉ build được, còn khởi
   động đúng như jar chạy trực tiếp.
2. `./gradlew :backend:cyclonedxBom` sinh `bom.json` thật: `bomFormat: CycloneDX`,
   `specVersion: 1.6`, 221 thành phần — không phải cấu hình chưa thử.

**Chưa xác nhận được** (ghi rõ trong `OPEN-12`, `OPEN-13`, không giả vờ đã xong): `dependencyCheckAnalyze`
chạy trọn vẹn cục bộ — lần tải dữ liệu NVD đầu tiên mất hàng chục phút, không thực tế trong một
phiên; và **cả ba workflow** (`ci.yml`, `contract-check.yml`, `security.yml`) chưa từng chạy thật
trên GitHub vì workspace chính không có metadata `.git` (biết từ `BL-M0-01`, vẫn đúng ở đây).
`security.yml` được kiểm bằng `actionlint 1.7.12` qua một Git repository tạm dưới `.tmp/` (cùng kỹ
thuật `contract-check.yml` đã dùng ở `BL-M0-03`) — hợp lệ cú pháp, nhưng chưa có lượt chạy CI thật
nào xác nhận các cổng chặn (secret/CVSS/image finding) hoạt động đúng lúc có phát hiện thật.

`BL-M0-13` hoàn tất ngày 14/09/2026. Không phải một tính năng mới — thẻ này đối chiếu bảng test của
threat model với `FR-AUTH-01`…`05`/`TM-AUTH-01`…`03`/`TM-REP-01`/`TM-OPS-02` và vá đúng hai chỗ hổng
còn lại:

- **`TM-AUTH-01`, `TM-AUTH-02`, `TM-AUTH-03`, `TM-REP-01` đã xanh sẵn** từ `BL-M0-08`/`BL-M0-09`/`BL-M0-10`
  — không cần làm gì thêm, chỉ xác nhận lại.
- **`FR-AUTH-05` (token_version) chưa từng có test riêng** dù cơ chế đã có từ `BL-M0-09`
  (`TokenVersionValidatorImpl`, `CookieSessionAuthenticationFilter.requireCurrentTokenVersion`).
  `security/TokenVersionEnforcementTest` (2 ca) lấp đúng chỗ này: đăng nhập lấy token, gọi thẳng
  `AppUser.bumpTokenVersion()` (chưa có endpoint đổi mật khẩu/vai trò thật — M3 — nên gọi trực tiếp
  điều một endpoint như vậy sẽ làm), xác nhận token cũ bị từ chối ngay cả khi chữ ký/hạn còn hợp lệ,
  và đăng nhập lại thì token mới vẫn dùng được.
- **`TM-OPS-02` còn "Một phần"** — nhánh "không trả class/query/stack trace" đã xanh từ `BL-M0-04`
  (`ProblemDetailsIntegrationTest`), nhưng "Actuator ở cổng nội bộ" và "security headers" chưa có
  test nào trước thẻ này. `security/ErrorDisclosureTest` (5 ca) đóng nốt: Actuator không lộ ở cổng
  ứng dụng (404, do quản trị nằm ở cổng riêng từ `BL-M0-02`, không phải do chặn); ở cổng nội bộ chỉ
  `health`/`info` trả `200`, mọi endpoint nhạy cảm khác (`env`, `beans`, `mappings`, `configprops`,
  `heapdump`, `threaddump`, `shutdown`) trả **`403`** — ban đầu viết test kỳ vọng `404`, chạy thật
  mới lộ ra `DefaultSecurityConfig`'s `anyRequest().denyAll()` (`BL-M0-07`) chặn trước khi Actuator
  kịp định tuyến, nên là `403`; sửa lại test cho khớp hành vi thật thay vì ép hành vi khớp test.
  Security headers mặc định của Spring Security (`X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY`, `Cache-Control: no-store`) có mặt trên cả nhánh lỗi lẫn nhánh thành công,
  ở cả bốn chuỗi filter — không cần cấu hình thêm, chỉ cần xác nhận bằng test.

**Còn treo, không giả vờ đã xong:** phần DAST (ZAP) của `TM-INJ-01` và `TM-OPS-02` chưa có thẻ
backlog nào sở hữu — ghi `OPEN-14`.

Kiểm chứng ngày 14/09/2026: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành
công, 131/131 test, 0 lỗi. `security/TokenVersionEnforcementTest` và `security/ErrorDisclosureTest`
đều chạy trên PostgreSQL 16 thật qua Testcontainers 2.0.5, Tomcat thật, Java 25, Gradle 9.1.0.

`BL-M0-14` (cổng thoát M0) hoàn tất ngày 14/09/2026 — **M0 đã xong, tiếp theo là M1**
(`BL-M1-01`). Hai trên ba tiêu chí có kiểm chứng thật mới; tiêu chí thứ ba (CI trên GitHub) giữ
nguyên giới hạn `OPEN-13` đã chấp nhận từ `BL-M0-03`/`BL-M0-12` — cùng tiền lệ, không phải ngoại lệ
mới cho riêng thẻ này:

- **E2E login→MFA→quyền ghi→audit/trace: xong.** `e2e/LoginMfaWriteAuditE2ETest` (mới) chạy một
  chuỗi HTTP thật duy nhất — không chỉ xác nhận lại từng mảnh của `BL-M0-08`…`13` như các test cũ
  làm riêng lẻ: đăng nhập chưa MFA (`mfaBlocked:true`) → thao tác ghi bị chặn `403` và **không**
  tạo dòng audit nào → bật MFA (gọi thẳng `MfaService.enable`, vẫn `OPEN-09`) → đăng nhập lại với
  TOTP đúng (`mfaBlocked:false`) → thao tác ghi đi qua → đúng một dòng audit với `actorId`/
  `actorRole` khớp, và `traceId` là chuỗi 32 hex thật (khác `AuditImmutabilityTest`, chạy
  `webEnvironment=NONE` nên `traceId` luôn rỗng ở đó — bài test mới này chứng minh đường dây nối
  thông từ **trong một request HTTP thật**, không phải gọi thẳng service).
  - Chưa có endpoint nghiệp vụ ghi thật nào tồn tại ở M0 (ordering/catalog là M1), nên "quyền ghi"
    ở đây là một controller tối thiểu (`GhiKiemThuController`) khai báo trong
    `e2e/E2eWriteCheckSupport` — **không phải trong lớp `@SpringBootTest`**: đây là một bẫy thật
    đã gặp khi viết test này, ghi lại để không lặp lại. `SpringBootTestContextBootstrapper` loại
    lớp lồng bên trong chính lớp `@SpringBootTest` khỏi component scan; route khai báo kiểu đó
    đăng ký thất bại **âm thầm** — không lỗi biên dịch, không lỗi khởi động, chỉ 404 khi gọi, và
    dễ nhầm là lỗi khác (đã từng nhầm là lỗi tham số/CSRF trước khi lần ra đúng nguyên nhân). Nếu
    cần một controller test-only tương tự trong tương lai, đặt trong một class hỗ trợ riêng
    (`abstract class ... extends QrosIntegrationTest`) như `SecurityZoneTestSupport.VungController`
    đã làm, rồi cho lớp test `extends` class đó — không khai báo `@RestController` lồng trong chính
    `@SpringBootTest` class.
  - Bẫy thứ hai: `GhiKiemThuController` ban đầu không có `@ConditionalOnProperty("spring.datasource.url")`
    nên bị component scan nạp vào **mọi** context dựng từ `com.qros`, kể cả các test dùng profile
    `test` (không có `DataSource`, `BL-M0-02`) — 28 test không liên quan gì tới `e2e-write-check`
    (`QrosApplicationTest`, `JwtAlgorithmConfusionTest`, `ProblemDetailsIntegrationTest`,
    `StructuredLoggingTest`) đỏ hàng loạt vì autowire `AuditRecorder`/`UserRoleRepository` thất bại
    lúc dựng context. Thêm điều kiện đó (cùng lý do `AuthenticationService`/`AuditService`) sửa dứt
    điểm; giờ mọi test-only bean phụ thuộc DataSource-gated bean phải xin cùng điều kiện.
- **Diễn tập runbook sự cố đăng nhập: xong**, chạy thật trên profile `dev` (Compose + `bootJar`),
  không đọc tài liệu suông — kết quả đầy đủ ghi ở cuối
  `docs/runbooks/dang-nhap-that-bai-hang-loat.md`. Diễn tập tự nó lộ ra một lỗ hổng thật của chính
  runbook: bước 1 ("tra `traceId` để xác nhận IP nguồn") không thực hiện được, vì
  `GlobalExceptionHandler` ghi đăng nhập sai ở mức `DEBUG` trong khi root logger mặc định là
  `INFO` — không có dòng log nào tồn tại để tra. **Đã vá**: `INVALID_CREDENTIALS`/`ACCOUNT_LOCKED`
  nay lên log ở mức `INFO` kèm `remoteAddr`, các mã lỗi khác giữ nguyên `DEBUG`. Test khoá hành vi:
  `security/CredentialStuffingTest#frAuth01_dangNhapSai_lenLogMucInfoKemIpNguon` (gắn `ListAppender`
  thẳng vào logger, không phụ thuộc `System.out` — lý do tương tự ghi chú ở `StructuredLoggingTest`
  từ `BL-M0-11`). Diễn tập lại sau khi vá xác nhận `traceId` trong thân phản hồi khớp đúng dòng log.
  Bước 2 (câu `SELECT ... FROM app_user`) và bước 3 (không cần hành động thủ công) đúng ngay từ
  đầu. Bước 4 (nghi lộ mật khẩu thật, cần `bumpTokenVersion()`) chưa diễn tập được — vẫn chưa có
  endpoint quản trị gọi vào, đúng giới hạn đã ghi từ `BL-M0-09`.
- **CI đủ cổng: tương đương cục bộ đã xanh, chạy thật trên GitHub vẫn kẹt `OPEN-13`.** Ba workflow (`ci.yml`,
  `contract-check.yml`, `security.yml`) chưa từng chạy thật trên GitHub Actions vì workspace chính
  vẫn không có metadata `.git` — giới hạn này không đổi kể từ `BL-M0-01`. Đã xác nhận lại tương
  đương cục bộ: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` — 133/133 test,
  0 lỗi (132 cũ + 1 test mới ở trên); `npx spectral lint ...` — 0 error, 0 warning. Không quét lại
  Trivy/Dependency-Check/gitleaks/CodeQL riêng cho thẻ này vì code thay đổi ở đây không đụng
  dependency mới hay Dockerfile — rủi ro trôi ở nhóm đó nằm nguyên trạng từ `BL-M0-12`.

Còn lại trước khi `BL-M0-14` thật sự `DONE`: gắn lại Git remote và để ba workflow chạy thật trên
GitHub (`OPEN-13`) — đây là việc duy nhất ngoài tầm kiểm soát của một phiên làm việc cục bộ.

M0 đã xong. `BL-M1-01` (QR → phiên bàn, thẻ M1 đầu tiên) hoàn tất ngày 14/09/2026 — cả backend lẫn
frontend, đi trọn một lát dọc đúng nguyên tắc CLAUDE.md tự đặt ra:

- **Module `venue` mới** (`backend/src/main/java/com/qros/venue/`, bốn package
  `domain/repository/service/controller`, chưa có `api` — chưa module nào khác cần gọi vào, cùng lý
  do `identity` ở `BL-M0-08`): `Store`, `RestaurantTable`, `QrSigningKey`, `TableSessionEntity`,
  `SessionDevice` map đúng bảng đã có sẵn từ `V1__baseline.sql` (Sprint 0), không cần migration mới.
  `QrTokenVerifier` xác minh JWS QR (bước 1–2 mục 5.3.2, `TM-QR-01`) bằng khoá **động** đọc từ
  `qr_signing_key` — khác `shared.security.JwtVerifier` (khoá tĩnh theo cấu hình cho vùng); tái
  dùng `Ed25519PublicKeys` (mới nới `public`) để không chép lại phép chuyển JWK→PublicKey.
  `TableSessionService` điều phối bước 3–6 (bàn+chi nhánh, giờ mở cửa, TOTP xoay vòng nếu bật, giới
  hạn tần suất) và `EC-02` (tham gia/xung đột theo hai ngưỡng 90 phút/10 phút; chặn thiết bị thứ 5
  trong 30 phút kèm ghi audit qua `AuditRecorder`).
- **Guest session token là JWT thật, ký bởi vùng khách** — `SecurityZoneConfiguration.guestJwtIssuer`
  (bean mới, cùng mẫu `staffJwtIssuer`), khoá cấu hình ở `qros.security.guest.signing-key` (dev/test
  có khoá cố định, prod đòi `QROS_JWT_GUEST_KID`/`_D`). Vì bean này **luôn nạp** (không đợi
  `DataSource`, giống `staffJwtIssuer`), mọi test tích hợp — kể cả test không đụng gì tới phiên bàn —
  giờ cũng cần một cặp khoá guest hợp lệ; `QrosIntegrationTest` tự sinh thêm cặp đó.
- **Ba bẫy thật gặp khi viết `LoginMfaWriteAuditE2ETest`-kiểu test cho module này, đã sửa và có ghi
  chú tại chỗ để không lặp lại:**
  1. `GuestSessionController` tiêm `HttpServletRequest` qua constructor ban đầu — bean đó chỉ tồn
     tại khi có web context kiểu servlet, hỏng ngay các test `webEnvironment = NONE` (ví dụ
     `AuditImmutabilityTest`, vốn cũng nạp controller này qua component scan trên `com.qros`). Sửa
     bằng lấy IP qua `RequestContextHolder.currentRequestAttributes()` ngay trong thân phương thức.
  2. `RestaurantTable.shortCode` là `char(6)` ở CSDL — thiếu `columnDefinition = "bpchar(6)"` thì
     `ddl-auto: validate` từ chối khởi động, đúng bẫy `citext`/`AppUser.email` đã gặp ở `BL-M0-08`.
  3. Đua song song tạo phiên cho cùng một bàn: cách ban đầu ("ghi rồi bắt
     `DataIntegrityViolationException` khi thua chỉ mục riêng phần") **sai** — PostgreSQL đánh dấu
     cả giao dịch "aborted" ngay khi một câu lệnh vi phạm ràng buộc, mọi câu lệnh sau đó trong cùng
     giao dịch (kể cả câu đọc lại để "thử lại đường tham gia") sẽ lỗi theo. Thay bằng khoá tư vấn
     phạm vi giao dịch (`pg_advisory_xact_lock(hashtext(tableId))`,
     `TableSessionRepository.khoaTheoBan`) trước khi đọc — request thứ hai **chờ** thay vì cùng ghi.
     Có test dựng 4 luồng thật (`ExecutorService` + `CyclicBarrier`) quét cùng một bàn cùng lúc,
     xác nhận đúng một phiên được tạo.
  4. `TableScanRateLimiter`/`QrTokenVerifier`/`TableSessionService`/`GuestSessionController` đều
     phải `@ConditionalOnProperty("spring.datasource.url")` — thiếu thì 75 rồi 47 test không liên
     quan gì tới `venue` đỏ hàng loạt vì autowire thất bại lúc dựng context (đúng mẫu lỗi đã ghi ở
     `BL-M0-08`/`09`, chỉ khác là lần này phạm vi ảnh hưởng lớn hơn vì module mới đụng nhiều thứ).
  5. `EC-02` "nghi ngờ lạm dụng" ghi audit rồi ném `QrosException` để trả `409` — thiếu
     `noRollbackFor = QrosException.class` thì dòng audit bị cuốn trôi theo rollback mặc định, đúng
     bẫy đã lặp lại ở `BL-M0-08`/`09`/`BL-M0-14`. Bốn lần gặp cùng một lỗi trong bốn thẻ khác nhau —
     đáng để nhớ như một quy tắc mặc định khi viết service ném `QrosException` sau một ghi có chủ ý.
- **`web-guest` — dựng mới hoàn toàn từ tay không**, chưa từng có `package.json` trước thẻ này.
  Next.js 16.3.5 (App Router, Turbopack) + React 19.3.0, không thêm thư viện UI nào (CSS thuần,
  inline style) để giữ ngân sách 180KB gzip (`NFR-PERF-07`) — xem điểm còn treo bên dưới.
  - `next.config.ts` tự proxy `/api/**` sang backend qua `rewrites()` thay vì gọi thẳng cross-origin:
    né hẳn quyết định CORS/gateway chưa chốt (`OPEN-01`) mà không phải tự ý quyết nó — cách này vẫn
    đúng nếu sau này Next.js được dựng đứng trước backend thật.
  - `app/t/[qrToken]/page.tsx` (client component): lấy/sinh `deviceId` từ `localStorage` (lớp 2 mục
    5.3.2), gọi `POST /api/v1/guest/sessions` ngay khi tải trang — không cần khách bấm gì. Vùng
    khách nhận token qua header (`Authorization: Bearer`), không qua cookie
    (`GuestSecurityConfig`, `BL-M0-07`), nên trang tự lưu `accessToken` vào `localStorage`
    (`lib/guestSession.ts`) và tự gắn lại ở lượt gọi sau — trình duyệt không tự làm hộ như cookie.
  - `app/ma-ban/page.tsx` — đường dự phòng nhập mã 6 ký tự (`FR-CUS-02`) khi camera không quét được.
  - Kiểu dữ liệu lấy thẳng từ `src/types/api.d.ts` (đã sinh sẵn từ `BL-M0-03`) qua
    `components["schemas"][...]` — không định nghĩa lại DTO tay, lệch hợp đồng là lỗi biên dịch.
  - **Một bẫy thật khi `next dev` chạy lần đầu**: Next.js 16 tự sinh `web-guest/AGENTS.md` và
    `web-guest/CLAUDE.md` — đụng thẳng tên với `CLAUDE.md` gốc (nguồn sự thật duy nhất của dự án).
    Đã xoá hai file đó và tắt hẳn bằng `agentRules: false` trong `next.config.ts`.
- **Cố tình chưa làm/chưa đo, ghi rõ để không tưởng đã xong:**
  - **`NFR-PERF-07` (≤180KB gzip) chưa được đo bằng bundle analyzer thật.** Turbopack production
    build không in bảng "First Load JS" theo route như build cũ; cộng thô gzip mọi chunk chia sẻ
    (Next 16 + React 19, không tính JS riêng từng trang) đã ra **~176KB** — sát trần, rủi ro thật.
    Ghi `OPEN-16`.
  - **`TM-SES-01`** (token phiên bàn gắn thiết bị) — hiện chỉ gắn ở tầng dữ liệu
    (`session_device`), không gắn ở token: bất kỳ thiết bị nào giữ được token đều dùng được, chưa
    có claim `did`/header đối chiếu. Ghi `OPEN-15`.
  - **Lớp 5 mục 5.3.2** (tín hiệu vị trí) — trường `coords` trong `StartSessionRequest` được chấp
    nhận nhưng không dùng tới gì cả; PRD tự mô tả đây là "tín hiệu phụ, không phải rào chắn cứng"
    nên bỏ qua không phá vỡ tiêu chí nghiệm thu nào, nhưng cũng chưa có nơi nào đọc nó.
  - **Bước 6** (giới hạn tần suất) — `TableScanRateLimiter` là bean cho qua tất cả, cùng mẫu
    `TokenVersionValidator` mặc định. Ngưỡng thật (bao nhiêu lần/bao lâu, fail-open hay fail-closed
    khi Redis lỗi) kẹt ở `OPEN-02`, không tự chốt. Có test riêng
    (`venue/TableScanRateLimiterWiringTest`) xác nhận bước này **có nằm đúng chỗ** trong chuỗi gọi
    (trước khi chạm CSDL phiên), chỉ chưa có ngưỡng thật.
  - `session_device.user_agent_hash` chưa được ghi (lớp 2 mục 5.3.2 nhắc tới "băm User-Agent" nhưng
    không phải điều kiện chặn — cột này nullable, để trống không vi phạm bất biến nào).
  - `POST /api/v1/guest/sessions/by-code` chưa có giới hạn "5 lần thử/10 phút/IP" theo `FR-CUS-02` —
    logic mở phiên đúng, chỉ thiếu phần chống dò mã. Cùng nhóm với `OPEN-02`/bước 6.

Kiểm chứng ngày 14/09/2026: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành
công, 155/155 test, 0 lỗi — 22 test mới (`venue/TableSessionHttpFlowTest` 12,
`venue/TableScanRateLimiterWiringTest` 1, `security/QrForgeryTest` 6, `security/QrReplayTest` 3),
tất cả trên PostgreSQL 16 thật qua Testcontainers 2.0.5, Tomcat thật, Java 25, Gradle 9.1.0. `npx
spectral lint` — 0 error, 0 warning (chỉnh `openapi.yaml` chỉ thêm mô tả cho `401` của
`startTableSessionByCode`, không đổi schema, nên không cần sinh lại model Python). Chạy thử bản
đóng gói profile `dev` qua Compose: seed thật một chi nhánh/bàn/khoá QR, ký một mã QR thật bằng
JDK `Signature "Ed25519"`, gọi `POST /api/v1/guest/sessions` qua HTTP thật — `201`, token giải mã
đúng `scope: table_session`, `sid`/`tid` khớp; `GET /api/v1/guest/sessions/current` với token đó
trả `200 joined:true`; `GET /api/v1/guest/menu` (chưa xây, `BL-M1-02`) trả `404` chứ không phải
`401` — đúng hành vi mong đợi; token sai trả `401 UNAUTHENTICATED`.

**Phần frontend, kiểm chứng cùng ngày**: `npx tsc --noEmit`, `npx eslint .`, `npx next build` đều
sạch (0 lỗi/cảnh báo). Chạy thật `next dev` cạnh backend `dev` qua Compose (không mock gì): gọi
`POST /api/v1/guest/sessions` **qua proxy Next.js** (cổng 3000, không gọi thẳng cổng 8080) — cùng
kết quả `201` như gọi thẳng, xác nhận `rewrites()` hoạt động đúng; `curl` ba trang
(`/`, `/ma-ban`, `/t/[qrToken]`) xác nhận HTML server-render đúng tiêu đề/nội dung tiếng Việt và
đúng trạng thái "Đang xác minh mã QR…" lúc tải. Không có trình duyệt thật/Playwright trong môi
trường này nên **chưa xác nhận được** phần hydrate phía client (gọi `fetch` sau khi trang tải xong)
chạy đúng trong một trình duyệt thật — chỉ xác nhận API nó gọi tới hoạt động đúng và SSR đúng.

`BL-M1-02` (thực đơn read-only) hoàn tất cùng ngày 14/09/2026 — cả backend lẫn frontend:

- **Module `catalog` mới** (`domain/repository/service/controller`, chưa có `api`): map đúng
  `category`/`menu_item`/`menu_variant`/`option_group`/`option_choice`/`menu_item_option_group`/
  `ingredient`/`recipe_component`/`option_recipe_component`/`price_schedule` — toàn bộ đã có sẵn từ
  `V1__baseline.sql`, không cần migration mới. `MenuService.buildMenu` dựng một lượt (không N+1):
  nạp hàng loạt theo `itemIds`/`variantIds`/`optionGroupIds` rồi ghép nối trong bộ nhớ, tính
  "còn/hết" bằng cách đối chiếu tập `ingredient.sold_out` với `recipe_component`/
  `option_recipe_component` — món **hết hàng vẫn hiển thị** (mờ, nhãn "Tạm hết"), chỉ `published`/
  `manually_disabled` mới thật sự ẩn khỏi thực đơn, đúng `FR-CUS-03`.
- **`venue` có `api` lần đầu tiên** (`StoreFacade`/`StoreView`) — nhu cầu cross-module thật đầu tiên
  kể từ `BL-M1-01`: `catalog` cần `timezone` của chi nhánh để tính giá theo lịch
  (`EffectivePricing`, `FR-MGT-04`) đúng giờ địa phương, không phải giờ máy chủ. Thêm khi có nhu
  cầu thật, đúng nguyên tắc đã đặt ra ở `identity`/`audit`.
- **ETag theo nội dung, không theo cột "cập nhật lúc"** — `menu_item` không có cột đó (chỉ
  `created_at`). Hash SHA-256 của chính JSON sắp trả về; hai lần dựng giống hệt nhau luôn ra cùng
  ETag, và bất kỳ thay đổi nào (kể cả `ingredient.sold_out` lật cờ) đổi ETag ngay — khớp yêu cầu
  "cập nhật realtime" của `FR-CUS-03` mà vẫn tận dụng được `304`.
- **Một bẫy thật của openapi-typescript/openapi-generator**: dựng `new MenuCategoriesInner(id, name,
  List.of())` rồi gọi `addItemsItem(...)` sau đó ném `UnsupportedOperationException` — constructor
  sinh ra gán thẳng list truyền vào làm field nội bộ, còn `addXxxItem` chỉ lazy-init khi field đó là
  `null`, không phải khi nó rỗng-nhưng-immutable. Sửa bằng truyền `null` vào vị trí đó thay vì
  `List.of()` ở mọi chỗ có gọi `addXxxItem` theo sau — ba chỗ (`Menu`, `MenuCategoriesInner`,
  `OptionGroup`). Đáng nhớ cho mọi lần dùng generated model kiểu builder có list sau này.
- **Một bẫy cô lập test**: `GuestMenuHttpFlowTest` chèn `category`/`menu_item`/`ingredient` tham
  chiếu `store` mà không dọn — vô hại với chính nó (mỗi test tự sinh `storeId` mới), nhưng làm vỡ
  `WorkShiftAutoCloseTest` chạy sau trong cùng container Testcontainers dùng chung (mẫu singleton):
  test đó tự `DELETE FROM store` trong `@BeforeEach` của nó, vấp FK còn treo. Thêm `@AfterEach` dọn
  đúng chiều phụ thuộc — không đổi CASCADE ở schema vì đây là lỗi vệ sinh của test mới, không phải
  lỗi thiết kế bảng.
- **`EffectivePricing`** (`FR-MGT-04`) tách riêng, test thuần Java (8 ca: mặc định, lịch hiệu lực,
  chưa tới hạn, đã hết hạn, sai thứ trong tuần, ngoài khung giờ, trong khung giờ, nhiều lịch chồng
  lấn chọn lịch mới nhất) — chưa có endpoint quản trị nào ghi `price_schedule` (M3), nên logic này
  hiện chỉ được test qua fixture tay, không qua một luồng ghi thật nào.
- **Frontend**: `app/(session)/layout.tsx` dùng `useSyncExternalStore` (không phải
  `useState`+`useEffect`) để đọc `localStorage` — tránh đúng hai vấn đề cùng lúc: lệch cây DOM giữa
  server/client (hydration mismatch, vì server không có `localStorage`) và cảnh báo "setState đồng
  bộ trong effect" của `eslint-plugin-react-hooks` mà bản `useState` ban đầu mắc phải.
  `app/(session)/menu/page.tsx` tìm kiếm/lọc (`FR-CUS-04`) hoàn toàn phía client trên thực đơn đã
  tải — khớp thiết kế API (`GET /menu` không có tham số tìm kiếm nào, tải một lần rồi cache).
  `lib/vietnameseSearch.ts` bỏ dấu tiếng Việt để so khớp — CSDL cố tình chưa đánh index cho việc
  này (`unaccent()` không phải `IMMUTABLE`, ghi chú ở `V1__baseline.sql`), nên xử lý ở client là
  đúng chỗ, không phải giải pháp tạm.

Kiểm chứng ngày 14/09/2026: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành
công, 170/170 test, 0 lỗi (15 test mới: `catalog/GuestMenuHttpFlowTest` 7,
`catalog/service/EffectivePricingTest` 8), tất cả trên PostgreSQL 16 thật qua Testcontainers 2.0.5.
Nhân tiện vá một test cũ flaky ở `BL-M1-01`: `QrForgeryTest.chuKyBiSua_biTuChoi` sửa ký tự **cuối
cùng** của chữ ký để giả mạo — base64url không đệm khiến vài bit cuối của ký tự cuối là bit đệm
không mang dữ liệu thật, nên có xác suất khác 0 sửa "trúng" một tổ hợp bit vẫn giải mã ra đúng chữ
ký cũ. Sửa ký tự ở **giữa** đoạn chữ ký thay vì cuối cùng.

Frontend: `npx tsc --noEmit`, `npx eslint .`, `npx next build` đều sạch. Chạy thật `next dev` cạnh
backend `dev` qua Compose: `POST /api/v1/guest/sessions` rồi `GET /api/v1/guest/menu` **qua đúng
proxy Next.js** (cổng 3000) — cùng kết quả với gọi thẳng backend, xác nhận `rewrites()` chuyển tiếp
đúng cho cả POST lẫn GET kèm header tuỳ chỉnh (`Authorization`). Bật `sold_out` cho một nguyên liệu
thật rồi gọi lại — `available:false` xuất hiện ngay lập tức trong cả `GET /menu` lẫn
`GET /menu/items/{id}`, đúng yêu cầu "cập nhật realtime" của `FR-CUS-03`.

**Còn treo, ghi rõ để không tưởng đã xong**: `NFR-PERF-01` (p95/p99 qua k6 500 người dùng ảo) chưa
đo — `OPEN-17`. Không có trình duyệt thật/Playwright trong môi trường này nên phần tương tác thật
(gõ tìm kiếm, bấm lọc, mở chi tiết món) trên trang `/menu` **chưa được xác nhận** ngoài việc build/
lint/typecheck sạch và API nó gọi hoạt động đúng — cùng giới hạn đã ghi ở `BL-M1-01`.

`BL-M1-03` (giỏ offline và đặt món) **hoàn tất** ngày 15/09/2026 — backend (`ordering/`) hoàn tất
và kiểm chứng thật ngày 14/09; frontend IndexedDB/cart/order khép nốt lát dọc ngày 15/09.
Đây là thẻ nhạy cảm nhất của toàn bộ hợp đồng (`ADR-06`):

- **Module `ordering` mới** (`domain/repository/service/controller`): `CustomerOrder`, `OrderLine`,
  `OrderLineOption`, `OrderStatusLog` map đúng bảng đã có sẵn từ `V1__baseline.sql`. Máy trạng thái
  rút gọn cho thẻ này: chỉ có `PENDING → CANCELLED` (khách tự huỷ); các bước barista/thanh toán
  thuộc KDS/`payment` (M1-04/M2).
- **Hai nhu cầu cross-module thật đầu tiên cho cả hai module đã có `api` trước đó**:
  - `venue.api.TableSessionFacade` (mới) — `ordering` cần biết phiên còn mở, nhãn bàn (mã đơn/sự
    kiện), và `staffOpened` (`EC-03`). `callStaff` (`FR-CUS-12`) cũng qua cổng này dù nằm chung
    interface Java với các endpoint đặt món (`GuestOrderApi` gộp theo tag OpenAPI, không theo
    module sở hữu dữ liệu) — `staff_call` là bảng của `venue`, module thực hiện theo dữ liệu nó sở
    hữu, không theo cách interface được gộp nhóm.
  - `catalog.api.CatalogFacade` (mới) — `ordering` gọi vào để định giá một dòng đơn thay vì tự đọc
    `menu_item`/`menu_variant`/`option_choice`, đúng bất biến số 8: giá luôn tính lại phía máy chủ
    từ catalog. Validate cả số lượng chọn tối thiểu/tối đa của từng nhóm tuỳ chọn (`FR-CUS-05`).
- **`OrderService` là ngoại lệ có chủ ý với quy ước "service trả domain, controller dịch DTO"**
  của `venue`/`catalog`: trả thẳng `com.qros.generated.model.Order` vì `IdempotencyGuard` lưu/phát
  lại kết quả bằng tuần tự hoá chính kiểu trả về — dựng DTO hai lần (một cho tạo mới, một khác cho
  phát lại) sẽ có ngày lệch nhau.
- **`IdempotentResponse` thêm trường `replayed`** (tương thích ngược qua constructor phụ 2 tham
  số) — lỗ hổng hợp đồng đã ghi từ `BL-M0-06`/`13`: cơ chế cũ luôn phát lại đúng mã HTTP đã lưu
  (`201`), nhưng hợp đồng `createOrder` đòi gửi lặp trả `200`. Giờ `IdempotencyGuard` tự đánh dấu
  `replayed=true` khi đọc lại, controller tự quyết `200`/`201` theo cờ đó — quyết định thuộc về hợp
  đồng của từng endpoint, không phải của kernel idempotency dùng chung.
- **Vá đúng lỗ hổng hợp đồng còn lại từ `BL-M0-06`**: `openapi.yaml` bổ sung `422
  IDEMPOTENCY_KEY_REUSED` cho `createOrder` — trước đó không khai báo ở bất kỳ operation nào có
  `Idempotency-Key`.
- **Một lỗ hổng bảo mật thật, phát hiện khi viết `PriceTamperingTest`**: `additionalProperties:
  false` trong `openapi.yaml` chỉ là mô tả hợp đồng — Jackson mặc định **âm thầm bỏ qua** trường lạ
  thay vì từ chối, trừ khi bật `spring.jackson.deserialization.fail-on-unknown-properties` (chưa
  từng bật, giờ đã bật toàn cục ở `application.yml`). Nghĩa là suốt từ `BL-M0-03` tới ngay trước
  thẻ này, một client gửi kèm `"unitPrice": 1000` sẽ không bị từ chối — giá trị đó chỉ đơn giản
  không được đọc tới, không phải "không được chấp nhận". `GlobalExceptionHandler` mới phân biệt
  được `UnrecognizedPropertyException`: tên trường "trông như giá" (`price`/`amount`/`total`/
  `surcharge`/`discount`) → `400 PRICE_NOT_ACCEPTED` đúng hợp đồng; trường lạ khác →
  `400 VALIDATION_FAILED`. Đây là phát hiện quan trọng nhất của thẻ này — không phải lỗi trong code
  `ordering` mới viết, mà là một khoảng hở tồn tại xuyên suốt mọi endpoint có `additionalProperties:
  false` kể từ khi hợp đồng đó được baseline.
- **Một bẫy `noRollbackFor` lần thứ năm, nhưng LẦN NÀY THEO CHIỀU NGƯỢC LẠI** so với bốn lần trước
  (`BL-M0-08/09/14`, `BL-M1-01`): thêm `noRollbackFor = QrosException.class` "cho chắc" vào
  `OrderService.placeOrder` lại chính là NGUYÊN NHÂN gây `UnexpectedRollbackException` (500) —
  `CatalogFacadeImpl.priceLine` là một `@Transactional(readOnly = true)` LỒNG bên trong (join cùng
  giao dịch vật lý) không có cùng `noRollbackFor`; interceptor của lời gọi lồng đó tự đánh dấu
  giao dịch rollback-only ngay khi `QrosException` đi qua nó, trước khi control quay lại
  `placeOrder`. Giao dịch ngoài thấy `noRollbackFor` khớp nên cố COMMIT thay vì rollback — nhưng
  cờ rollback-only đã bị khoá trước đó, nên commit thất bại và Spring ném
  `UnexpectedRollbackException` thay cho `QrosException` gốc, lọt vào handler `Throwable` chung
  thay vì handler `QrosException` — client nhận `500` thay vì `400`/`409` đúng nghĩa. Sửa bằng
  **bỏ hẳn** `noRollbackFor` ở `placeOrder`/`cancelOrder`: không có ghi nào trước các điểm ném ngoại
  lệ ở đây cần sống sót (khác `EC-02` của `venue`, ghi audit RỒI mới ném) — `IdempotencyGuard` còn
  cố ý để khoá biến mất cùng giao dịch hỏng, nên rollback mặc định mới là đúng ý. **Bài học cập
  nhật vào memory**: `noRollbackFor` ở phương thức mở giao dịch chỉ có nghĩa khi lời gọi lồng bên
  trong KHÔNG tự là một `@Transactional` khác — nếu có, mọi lớp `@Transactional` trên đường gọi
  phải đồng thuận cùng một quy tắc rollback cho cùng loại ngoại lệ, không chỉ lớp ngoài cùng.
- **`short_code`** (`"A-01-1"`) sinh qua khoá tư vấn theo chi nhánh
  (`CustomerOrderRepository.khoaTheoChiNhanh`, cùng kỹ thuật `venue.khoaTheoBan`) + đếm số đơn
  trong ngày theo giờ Việt Nam — khớp đúng biểu thức đã cố định trong chỉ mục duy nhất của
  `V1__baseline.sql`, không tự chọn múi giờ khác.
- **Frontend `web-guest` hoàn tất `FR-CUS-06/08/09`**:
  - `src/lib/cart.ts` lưu một giỏ riêng cho từng `sessionId` trong IndexedDB. Dòng giỏ giữ snapshot
    tên/giá để hiển thị khi mất mạng ngắn, nhưng `prepareCheckout()` dựng một `CreateOrderRequest`
    mới chỉ gồm `menuItemId`, `variantId`, `optionIds`, `quantity`, `note`, `addedBy` — không có bất
    kỳ tên hay trường giá nào đi lên máy chủ (`ADR-06`).
  - UUIDv4 idempotency được lưu cùng giỏ ở lần đặt đầu, giữ nguyên qua mọi lần retry/lỗi mạng và
    chỉ bị xoá khi nội dung giỏ thay đổi. Sau khi nhận đơn, client chỉ xoá giỏ nếu khoá vẫn khớp,
    nên không làm mất món khách vừa thêm trong lúc request đang bay.
  - Màn chi tiết món nay chọn được biến thể, nhóm tuỳ chọn `SINGLE`/`MULTIPLE` với `minSelect`/
    `maxSelect`, số lượng và ghi chú tối đa 200 ký tự. `/cart` cho sửa/xoá, hiển thị giá tạm tính
    kèm cảnh báo máy chủ sẽ tính lại; `/orders/[orderId]` đọc đơn từ server và hiển thị giá đã chốt.
  - `src/lib/cart.test.mjs` dùng IndexedDB giả lập nhưng chạy đúng implementation thật, khoá bốn
    ca: tồn tại qua lần mở lại, payload không giá, retry giữ nguyên khoá, và không xoá nhầm giỏ đã sửa.
- **Cố tình chưa làm, ghi rõ để không tưởng đã xong**:
  - `409 PRICE_CHANGED` — hợp đồng hiện tại không có trường nào để client báo "giá tôi thấy lúc
    xem thực đơn", nên chưa có cách nào máy chủ phát hiện "giá đã đổi kể từ lúc xem" — `OPEN-19`.
  - Giới hạn nghiệp vụ của `TM-ORD-03` (3 đơn/5 phút/phiên, trần 2 triệu/bàn) — vẫn kẹt ở `OPEN-02`
    (đã ghi từ `BL-M0-06`, nay áp dụng rõ ràng cho cả `BL-M1-03`), không phải phạm vi thẻ này.

Kiểm chứng ngày 14/09/2026: `.\gradlew.bat :backend:cleanTest :backend:test :backend:bootJar` thành
công, 185/185 test, 0 lỗi (15 test mới: `ordering/GuestOrderHttpFlowTest` 10,
`security/PriceTamperingTest` 5), tất cả trên PostgreSQL 16 thật qua Testcontainers 2.0.5. `TM-ORD-01`
chuyển Xanh trong threat model. Chạy thử bản đóng gói profile `dev` qua Compose: đặt món thật qua
QR → phiên → đặt món — giá trả về đúng `45000×2=90000`; cố gửi `"unitPrice":1` trả đúng
`400 PRICE_NOT_ACCEPTED`; gửi lặp cùng `Idempotency-Key` trả `201` rồi `200` với thân **giống hệt
nhau từng byte**; `outbox_event` có đúng hai dòng `OrderPlaced`, cả hai `published_at` khác null
(đã phát qua Redis thật) — xác nhận `ADR-05` (ghi outbox cùng giao dịch nghiệp vụ) hoạt động đúng
với module mới, không chỉ với `identity`/`venue` đã kiểm từ trước.

Kiểm chứng frontend ngày 15/09/2026: `npm run test:unit` xanh 4/4 test; `npm run typecheck`,
`npm run lint`, `npm run build` đều thành công, build đủ route tĩnh `/menu`, `/cart` và route động
`/orders/[orderId]`; `npm audit` báo 0 vulnerability. Không có browser nào được kết nối với môi
trường kiểm thử nên chưa chạy được thao tác click/reload thật; Docker Desktop cũng chưa chạy nên
không lặp lại bộ 185 test backend/Testcontainers trong lượt này — bằng chứng backend ngày 14/09
ở đoạn trên vẫn giữ nguyên vì thay đổi ngày 15/09 chỉ nằm trong `web-guest`.

`BL-M1-04` hoàn tất ngày 15/09/2026 — KDS realtime đi trọn contract → backend → Redis/WebSocket →
`web-staff`:

- OpenAPI có hàng đợi KDS theo `X-Store-Id`, cập nhật dòng bằng `If-Match` + `X-Device-Id`, và
  `409 VERSION_CONFLICT` trả cả bản hiện tại. AsyncAPI có `/topic/kds/{storeId}`, `/app/resume`,
  `/user/queue/resume`, `ResyncRequired`; sự kiện đổi trạng thái/huỷ đơn được khai báo cho cả KDS
  lẫn phiên khách. Type Java/TypeScript/Python đã sinh lại từ contract.
- Backend đóng state machine `PENDING → CONFIRMED → PREPARING → READY → SERVED`; `CANCELLED` thắng
  mọi trạng thái chưa huỷ và bắt buộc lý do. `KdsService` kiểm quyền + phạm vi chi nhánh, dùng
  optimistic version, ghi `order_status_log` với nhân viên/thiết bị/thời điểm, và phát outbox cho
  cả store/session. STOMP xác thực bằng staff JWT, chỉ cho subscribe chi nhánh có quyền; Redis
  fan-out live, còn resume đọc outbox 15 phút theo `seq` và yêu cầu resync nếu khoảng trống đã hết hạn.
- `web-staff` mới có `/login` và `/kds`: cache + hàng thao tác IndexedDB, replay theo từng nấc hợp
  lệ, dedupe `eventId`, lưu `lastSeq`, tự resume/reconnect, đồng hồ SLA, ưu tiên phiếu quá hạn,
  âm báo/nháy phiếu mới và xử lý xung đột bằng bản server mới nhất. Chi nhánh đang hoạt động được
  chọn khi đăng nhập và gửi bằng `X-Store-Id`; đây mới đóng phần UX/header của `OPEN-10`, MDC log
  theo header vẫn còn phải làm.
- Quy tắc giao diện frontend từ nay được cố định ở `.cursor/rules/qros-frontend-design.mdc`, chắt
  lọc từ `sharkqwy/v0prompt` và các rule Next.js/Toss-style của `PatrickJS/awesome-cursorrules`:
  TypeScript/React semantic, responsive và accessible; giao diện sản phẩm yên, dễ quét, một màu
  nhấn, thang xám rõ, spacing token nhất quán, shadow nhẹ, không gradient trang trí hay nested card.

Kiểm chứng cuối ngày 15/09/2026: toàn bộ backend xanh **190/190 test** trên PostgreSQL 16 + Redis 7
thật qua Testcontainers; `KdsOptimisticLockingTest` cho 50 virtual thread cùng version chỉ đúng một
lượt thành công; `KdsRealtimeTest` nối STOMP thật, kiểm resume theo `seq` và live event dưới 1 giây.
`web-staff` xanh 4/4 unit test, typecheck, ESLint, production build đủ `/login` + `/kds`, audit 0
vulnerability; `web-guest` cũng hồi quy xanh 4/4, typecheck, lint, build và audit. Spectral có 0
error/0 warning (còn một info khuyến nghị AsyncAPI 3.1), OpenAPI validate/generate và Python compile
đều xanh. In-app browser runtime không cung cấp browser có thể điều khiển nên chưa kiểm tra trực
quan/click thật; không dùng Playwright riêng để tránh tạo một nguồn trạng thái trình duyệt khác —
kiểm tra này chuyển sang cổng E2E Chromium/WebKit của `BL-M1-06`.

`BL-M1-05` hoàn tất ngày 15/09/2026 — báo hết nguyên liệu đi trọn KDS → inventory/catalog →
PostgreSQL/outbox → thực đơn và cảnh báo đơn mở:

- OpenAPI 1.2 có `POST /api/v1/staff/ingredients/{ingredientId}/sold-out`, bắt buộc `X-Store-Id`,
  trả danh sách món/đơn bị ảnh hưởng; dòng KDS có danh sách nguyên liệu và trạng thái `soldOut`.
  Java/TypeScript/Python đã sinh lại từ contract. AsyncAPI có `IngredientSoldOut` cho chi nhánh và
  `ItemUnavailable` cho phiên khách, gồm tiền cần hoàn, danh sách thay thế và hạn phản hồi 3 phút.
- `InventoryService` kiểm quyền `inventory:adjust` và phạm vi chi nhánh theo bất biến 404, khoá bi quan
  đúng nguyên liệu, tìm ảnh hưởng chính xác qua cả recipe của biến thể lẫn tuỳ chọn, rồi cập nhật
  `ingredient.sold_out` cùng các outbox event trong một giao dịch. Gọi lại endpoint là idempotent, không
  phát trùng sự kiện. Các đơn mở chỉ bị đánh dấu khi đúng variant/option đã chọn dùng nguyên liệu đó.
- KDS hiển thị nguyên liệu ngay trên từng dòng món, cho barista chọn đúng nguyên liệu để báo hết và
  hiện cảnh báo nổi bật trên mọi phiếu mở bị ảnh hưởng. `web-guest` revalidate thực đơn mỗi giây bằng
  ETag (`Cache-Control: private, no-cache, must-revalidate`), giữ món ở trạng thái mờ/không bấm được với
  nhãn `Tạm hết`. Cách hiển thị này ưu tiên yêu cầu cụ thể của `FR-CUS-03`; câu “ẩn món” trong
  `FR-BAR-05`/`EC-04` được hiểu là ẩn khỏi khả năng đặt, không xoá khỏi danh sách.
- Phần server đã tạo `ItemUnavailable` theo đúng phiên khách, nhưng guest WebSocket/UI ba lựa chọn và
  API nhận phản hồi chưa tồn tại; AI gợi ý thuộc M4 và hoàn tiền thuộc M2. Khoảng trống này được ghi
  thành `OPEN-20`, không coi toàn bộ nhánh tương tác của `EC-04` là đã hoàn tất.

Kiểm chứng cuối ngày 15/09/2026: toàn bộ backend xanh **192/192 test**, 0 fail/error/skip và `bootJar`
thành công trên PostgreSQL 16 + Redis 7 qua Testcontainers. `InventorySoldOutTest` đi qua controller/JWT,
kiểm lan toả menu + KDS dưới 2 giây, đúng món/đúng đơn, hai outbox event, idempotency và bất biến 404.
Hai frontend đều xanh 4/4 unit test, typecheck, ESLint, production build và audit 0 vulnerability.
Spectral 0 error/0 warning (còn một info AsyncAPI 3.1), OpenAPI validate/generate và Python compile đều
xanh; `git diff --check` không có lỗi whitespace. In-app browser runtime trả về không có browser khả dụng,
nên visual/click QA vẫn được chuyển sang cổng Chromium/WebKit của `BL-M1-06`.

`BL-M1-06` chuyển sang hoàn thiện M1 local ngày 15/09/2026; staging/deploy không bắt buộc cho bài tập lớn:

- Có `e2e/tests/m1-order-flow.spec.ts` đi trọn mã bàn → menu → giỏ → đặt món → KDS → `SERVED`.
  `playwright test --list` nhận đúng hai lượt độc lập Chromium/WebKit. `load/menu.js` áp ngưỡng
  p95 `<120 ms`, p99 `<250 ms` với mặc định 500 VU; `load/orders.js` kiểm ngưỡng đặt món.
- Workflow chạy tay `.github/workflows/staging-gate.yml` tách security regression (`QrForgeryTest`,
  `QrReplayTest`, `PriceTamperingTest`), E2E Chromium/WebKit, rồi hai cổng k6. Nó chỉ dùng GitHub
  environment `staging` và secrets fixture, không thể vô ý tạo đơn/tải trên production.
- Preflight cục bộ xanh: guest 4/4 unit test, typecheck, ESLint và production build (đủ `/menu`,
  `/cart`, `/orders/[orderId]`, `/t/[qrToken]`); staff 4/4 unit test, typecheck, ESLint và production
  build (đủ `/login`, `/kds`). `git diff --check` xanh. Chưa cài k6 cục bộ và chưa có URL/fixture/
  credential staging. Mục tiêu hiện tại là hoàn tất mọi chức năng/test M1 local; workflow staging giữ
  lại như bằng chứng bổ sung khi dự án có hạ tầng deploy.

## Nhớ khi tiếp tục M1 (15/09/2026)

- `TM-SES-01` đã triển khai local: OpenAPI có `guestDevice`; token mới mang claim `did`; web guest
  gửi `X-Device-Id`; filter đối chiếu token/header. Token cũ không có `did` chỉ tương thích tối đa TTL
  90 phút. Test token sai thiết bị trả 401; full backend regression 200 test, 0 lỗi.
- `TM-ORD-03` đã có giới hạn domain: tối đa 8 dòng và 2.000.000 VND cho một đơn. Phần còn lại là
  limiter Redis 5 mở phiên/10 phút/IP, 3 đơn/5 phút/phiên, fail-closed cho thao tác ghi.
- `BL-M1-06` không còn là blocker staging; chỉ được đánh dấu DONE sau khi limiter Redis và test abuse
  local hoàn tất. Các artifact E2E/k6/GitHub Actions vẫn giữ cho lúc cần deploy.

`OPEN-16` được đóng ngày 15/09/2026: `web-guest/scripts/check-initial-js-budget.mjs` đọc
client-reference manifest Turbopack, gzip từng chunk JavaScript initial của mọi route guest và fail
khi vượt 180 KB (`NFR-PERF-07`). Đo production build: `/menu` 138.9 KB (lớn nhất), `/cart` 137.5 KB,
`/orders/[orderId]` 136.0 KB, `/t/[qrToken]` 132.2 KB và `/ma-ban` 131.9 KB. `guest-web` trong CI
chạy unit/typecheck/lint/build rồi cổng này; không còn dựa vào ước lượng thô từ mọi chunk chia sẻ.

`OPEN-10` được đóng ngày 15/09/2026: `StoreMdcFilter` chạy sau xác thực cookie trong cả chain staff
và admin. Nó chỉ đặt `storeId` vào MDC nếu `X-Store-Id` là UUID canonical có trong claim `stores`
(hoặc claim wildcard), và dọn giá trị sau request. `StoreMdcFilterTest` kiểm quyền/canonical/cleanup;
`StructuredLoggingTest` kiểm encoder JSON thực sự phát trường đó. Chạy
`./gradlew :backend:test --tests com.qros.shared.security.StoreMdcFilterTest --tests
com.qros.shared.observability.StructuredLoggingTest --no-daemon` xanh ngày 15/09/2026.

**Gắn Git thật, đẩy lên GitHub ngày 14/09/2026** (không phải một thẻ backlog — dọn nợ hạ tầng đã ghi
từ `BL-M0-01`/`OPEN-13`). Workspace chưa từng có metadata `.git` trước thời điểm này; `git init -b
main` tạo repo, commit gốc `f7bb3d0` (273 file, toàn bộ Sprint 0 + M0 + M1 walking skeleton tới hết
backend của `BL-M1-03`), remote `origin` trỏ `https://github.com/longdxam/QR-order`, push `main`
thành công (xác nhận qua `git ls-remote origin`). `OPEN-13` nay chỉ còn nửa sau: ba workflow
(`ci.yml`, `contract-check.yml`, `security.yml`) **có thể** chạy thật trên GitHub Actions từ giờ,
nhưng **chưa có lượt chạy nào được xác nhận xanh** trên tab Actions — đừng coi `BL-M0-03`/`BL-M0-12`
đã kiểm chứng đầy đủ trên CI thật cho tới khi việc đó xảy ra và được ghi lại ở đây.

## Nguồn sự thật

| Câu hỏi | Đọc file nào |
|---|---|
| Hệ thống phải làm gì, ràng buộc nào | `docs/PRD.md` — v1.1, **đã baseline** |
| API có những gì | `docs/api/openapi.yaml` |
| Sự kiện realtime có những gì | `docs/api/asyncapi.yaml` |
| Sinh code từ spec ra sao | `docs/api/README.md` |
| Mối đe doạ, ranh giới tin cậy và test bảo mật cần có | `docs/architecture/threat-model.md` |
| Làm hạng mục nào tiếp theo, phụ thuộc và tiêu chí hoàn thành | `docs/backlog.md` |

PRD đã baseline: mọi thay đổi phải qua kiểm soát thay đổi, và **mỗi commit tham chiếu một mã `FR-*`**.
Mọi yêu cầu đều có mã ổn định (`FR-CUS-08`, `NFR-SEC-26`, `EC-01`, `ADR-06`) — dùng chúng trong
commit message, tên test, và comment giải thích quyết định.

## ⚠ Bốn chỗ khác với PRD

PRD chưa được cập nhật những điều này. **Theo mục này, đừng theo PRD:**

1. **Không dùng Kafka/RabbitMQ ở v1.** PRD mục 3.1 còn vẽ, nhưng đã bỏ. Dùng
   Outbox + poller + Redis pub/sub để fan-out WebSocket.
2. **Không có AI service Python chạy 24/7.** Spring Boot gọi thẳng Claude API qua module
   `aigateway`. Python chỉ còn hai job theo lịch ở `ml/jobs/`.
3. **Không dùng pgvector, không dùng mô hình nhúng.** `ADR-03` nạp toàn bộ thực đơn vào
   phần đầu prompt có bật prompt caching. Không có RAG với vector search.
4. **Entity ở `domain/` mang annotation JPA**, không tách tầng ánh xạ riêng.

## Cấu trúc thư mục

```
backend/          Spring Boot, modular monolith
web-guest/        Next.js công khai — ngân sách 180KB gzip (NFR-PERF-07)
web-staff/        Next.js nội bộ — KDS + admin
packages/ui/      component dùng chung hai app web
ml/jobs/          hai job Python theo lịch, không phải service
eval/             bộ 200 câu chốt model tier
e2e/  load/       Playwright · k6
infra/  docs/
```

Tách hai app web là vì ngân sách gói JS. Gộp KDS và admin vào web khách sẽ vỡ `NFR-PERF-07`.

## Backend: package-by-feature, không phải package-by-layer

Chín module dưới `com.qros`: `identity venue catalog ordering payment inventory aigateway
analytics audit`, cộng `shared/` làm kernel.

Mỗi module có đúng năm package:

```
ordering/
├── api/            OrderFacade · OrderView · OrderPlacedEvent
├── controller/     GuestOrderController · StaffKdsController
├── service/        @Transactional nằm ở đây
├── domain/         Order · OrderStateMachine  (entity có JPA)
└── repository/     interface + hiện thực
```

Hai ngoại lệ có chủ ý: `payment/gateway/` (adapter VNPay/MoMo/ZaloPay) và
`aigateway/client/` (`LlmProvider` + `ClaudeClient`, lớp trừu tượng của `ADR-09`).

### Luật quan trọng nhất trong repo

> Module A **không bao giờ** import `controller`, `service`, `domain`, hay `repository`
> của module B. Chỉ được gọi qua `B.api.*` hoặc lắng nghe domain event của B.

Cưỡng chế bằng `backend/src/test/java/com/qros/architecture/ModuleBoundaryTest.java` (ArchUnit),
chạy trong CI. Test này **chặn merge**. Nếu nó đỏ, sửa code chứ đừng sửa luật —
không có nó thì cấu trúc thư mục chỉ là trang trí.

`SamePackageRootPredicate` nhận tên module đích trong constructor và so sánh segment thứ ba của
package (`com.qros.<module>`). Không đổi nó thành predicate không tham số: predicate như vậy không
có đủ ngữ cảnh để phân biệt truy cập cùng module với truy cập chéo module.

## Quy trình spec-first

Spec viết tay trước, code sinh sau. **Không** để `springdoc` sinh OpenAPI từ annotation —
làm vậy backend luôn "đúng" theo định nghĩa và hợp đồng mất tác dụng ràng buộc.

| Stack | Sinh vào đâu | Commit? |
|---|---|---|
| Backend | `build/generated/` — controller `implements` interface, lệch spec là lỗi biên dịch | Không |
| Frontend | `web-guest/src/types/api.d.ts` và `web-staff/src/types/api.d.ts` | **Có** |
| Python | `ml/models/generated.py` | **Có** |

Lệnh cụ thể ở `docs/api/README.md`. Đổi API thì sửa YAML trước, sinh lại, rồi mới viết code.

## Bất biến không được vi phạm

1. **Không có trường giá nào đi lên từ client.** `CreateOrderRequest` và `CreateOrderLine`
   đặt `additionalProperties: false` và không khai báo trường giá. Máy chủ tính lại toàn bộ
   từ catalog trong cùng giao dịch (`ADR-06`). Có test riêng: `PriceTamperingTest`.
2. **Tiền là `long` đơn vị đồng.** Không `double`, không `float`, không `BigDecimal` cho tiền,
   không ở đâu cả. Dùng `shared/money/Money`.
3. **Ba chuỗi filter bảo mật độc lập** cho `/guest/**`, `/staff/**`, `/admin/**`.
   Không dùng chung bean cấu hình.
4. **Mọi lỗi là RFC 7807** (`application/problem+json`) kèm `code` và `traceId`.
   Không bao giờ trả stack trace hay chi tiết nội bộ cho client.
5. **Truy vấn tham số hoá.** Cấm nối chuỗi SQL — có test ArchUnit `NoRawSqlTest`.
6. **Khoá lạc quan** (`@Version`) trên `Order` và `OrderLine`; đổi trạng thái qua `If-Match`.
7. **Kiểm tra quyền sở hữu ở cấp đối tượng** trên mọi truy vấn. Không đủ điều kiện thì trả
   `404`, **không** trả `403` — tránh xác nhận sự tồn tại của dữ liệu thuộc bàn khác.
8. **Khoá API của Claude chỉ tồn tại phía máy chủ.** Trình duyệt không bao giờ gọi thẳng
   nhà cung cấp (`NFR-AI-02`).
9. **Ghi outbox trong cùng giao dịch nghiệp vụ** (`ADR-05`). Cột `id` của bảng outbox chính là
   trường `seq` trong `asyncapi.yaml`.

## Bảo mật

Dự án đặt bảo mật ở vị trí yêu cầu chức năng. Giả định nền tảng: **kẻ tấn công đã có mã QR
trong tay** — mã QR dán công khai trên mặt bàn là dữ liệu công khai theo thiết kế.

Tám lớp phòng thủ ở PRD mục 5.3.2. Mỗi mối đe doạ trong threat model phải có **ít nhất một test
tự động** ở `backend/src/test/java/com/qros/security/`.

Bộ lọc dữ liệu ra ngoài (`NFR-SEC-26`) chặn theo mặc định: chỉ trường trong danh sách trắng
được rời `aigateway`. Văn bản tự do khách nhập phải qua rà và che PII trước khi gửi đi.

## Model AI

Ghim **mã model tường minh** trong cấu hình, không dùng bí danh trôi nổi. Mặc định hiện tại là
`claude-opus-5`. Tier vẫn **đang mở** (Opus 5 / Sonnet 5 / Haiku 4.5, chênh 5 lần chi phí) —
đây là quyết định thương mại của chủ đầu tư, đừng tự hạ tier để tiết kiệm.

## Ngôn ngữ

Tài liệu, comment giải thích quyết định, và commit message viết bằng **tiếng Việt**.
Tên class, biến, hàm, mã lỗi viết bằng **tiếng Anh**.
