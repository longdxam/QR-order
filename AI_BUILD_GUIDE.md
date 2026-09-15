# Hướng dẫn AI dựng QROS

## Mục tiêu

QROS là hệ thống đặt đồ uống qua QR tại bàn cho chuỗi 1–20 chi nhánh. Khách quét QR hoặc nhập mã bàn, xem menu, đặt món; barista dùng KDS để đưa đơn tới `SERVED`.

## Đọc theo thứ tự

1. `docs/PRD.md`: yêu cầu và mã `FR-*`, `NFR-*`, `EC-*`, `ADR-*`.
2. `docs/architecture/SDD.md`: kiến trúc và dữ liệu.
3. `docs/api/openapi.yaml`, `docs/api/asyncapi.yaml`: contract HTTP/realtime.
4. `docs/architecture/threat-model.md`: threat và test bắt buộc.
5. `docs/backlog.md`: dependency, tiêu chí READY/DONE.
6. `CLAUDE.md`: quyết định và bằng chứng hiện tại.

Nếu có mâu thuẫn, không tự đoán. Nêu mã yêu cầu, ảnh hưởng và xin quyết định. Các mục “Khác với PRD” trong `CLAUDE.md` được ưu tiên.

## Kiến trúc

```text
backend/          Spring Boot Java 25, modular monolith
web-guest/        Next.js công khai, JS initial gzip ≤180 KB
web-staff/        Next.js nội bộ, KDS/admin
ml/jobs/          Python jobs theo lịch, không phải service
e2e/              Playwright Chromium/WebKit
load/             k6
```

Backend gồm `identity venue catalog ordering payment inventory aigateway analytics audit` và kernel `shared`.

Mỗi module chỉ gồm `api/`, `controller/`, `service/`, `domain/`, `repository/`. Module A không import controller/service/domain/repository của B; chỉ gọi `B.api.*` hoặc lắng nghe event. Không sửa luật ArchUnit để làm test xanh.

## Bất biến bắt buộc

1. Spec-first: sửa OpenAPI/AsyncAPI trước, sinh code sau. Không springdoc code-first.
2. Client không gửi giá. Server tính lại giá từ catalog trong cùng transaction.
3. Tiền là `long` VND qua `shared/money/Money`; không double/float/BigDecimal.
4. Ba filter chain độc lập cho `/guest/**`, `/staff/**`, `/admin/**`.
5. Mọi lỗi là RFC 7807, có `code` và `traceId`, không lộ nội bộ.
6. SQL phải tham số hoá; không nối chuỗi SQL.
7. Authorization luôn kiểm ownership/session/store/tenant; không đủ scope trả 404.
8. Order/line dùng `@Version`, state change qua `If-Match`.
9. Outbox ghi trong cùng transaction; at-least-once, consumer dedupe `eventId`.
10. Khóa Claude chỉ ở server; egress allowlist + PII redaction.
11. V1 không Kafka/RabbitMQ/pgvector/RAG; dùng Outbox + Redis pub/sub + WebSocket.

## Quy trình thực hiện

1. Chạy `git status --short`; giữ nguyên thay đổi không thuộc thẻ.
2. Chọn đúng một `BL-*` READY trong backlog.
3. Xác định mã FR/NFR/EC/ADR/TM liên quan.
4. Nếu đổi contract: sửa YAML, lint/generate, commit type TS/Python được sinh; không commit `backend/build/generated`.
5. Làm lát dọc UI → controller → API/service transaction → domain/repository → outbox/realtime.
6. Viết test domain, integration Testcontainers, security/architecture test liên quan.
7. Chạy kiểm chứng; không skip/disable test.
8. Khi có bằng chứng, cập nhật `CLAUDE.md` và backlog. Commit message tiếng Việt phải chứa ít nhất một `FR-*`.

## Lộ trình

- Sprint 0: baseline docs, Flyway, contracts, threat model, backlog, CI/ArchUnit.
- M0: shared kernel, outbox/idempotency, security zones, identity/MFA, audit/observability/security CI.
- M1: QR/session, menu, cart/order, KDS realtime, sold-out, staging E2E/k6/security gate.
- M2: payment/refund/inventory/close shift.
- M3: admin/report/audit viewer.
- M4: AI gateway, jobs, eval/cost control.
- M5: load, DAST, accessibility, backup/release.

## Lệnh kiểm chứng

```powershell
docker compose up -d --wait
.\gradlew.bat clean test bootJar
npm ci --ignore-scripts
npm run contract:lint
npm run contract:generate:web

# Trong web-guest/ hoặc web-staff/
npm ci
npm run test:unit
npm run typecheck
npm run lint
npm run build
```

Integration test có database/Redis phải dùng Testcontainers, không H2. Production/staging không dùng secret mặc định dev.

## Khi phải dừng hỏi

Dừng khi cần thay đổi breaking API, quyết định OPEN-* đang chặn, secret/hosting/deploy, dữ liệu thật, hoặc thao tác xoá migration/data/volume. Báo rõ mã yêu cầu, rủi ro và lựa chọn cần chủ sở hữu chốt.
