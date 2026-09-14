# QROS

QROS là hệ thống đặt đồ uống qua mã QR tại bàn cho chuỗi quán cà phê và trà sữa có từ 1 đến 20
chi nhánh. Hệ thống dùng Spring Boot theo kiến trúc modular monolith, hai ứng dụng Next.js,
PostgreSQL 16 và Redis 7.

Trạng thái hiện tại: Sprint 0 đã hoàn tất; milestone M0 đang xây dựng phần nền móng.

## Yêu cầu môi trường

- Docker Desktop có Docker Compose.
- JDK 25. Không cần cài Gradle riêng vì repo có Gradle Wrapper.

## Khởi động hạ tầng phát triển

```bash
docker compose up -d --wait
docker compose ps
```

Các dịch vụ chỉ được publish trên loopback của máy phát triển:

| Dịch vụ | Địa chỉ mặc định | Tài khoản mặc định |
|---|---|---|
| PostgreSQL 16 | `localhost:5433/qros` | `qros` / `qros_dev` |
| Redis 7 | `localhost:6379` | mật khẩu `qros_dev` |

Có thể đổi cổng hoặc mật khẩu bằng các biến `QROS_POSTGRES_PORT`, `QROS_POSTGRES_PASSWORD`,
`QROS_REDIS_PORT` và `QROS_REDIS_PASSWORD`. Các giá trị mặc định trên chỉ dành cho máy phát triển,
không được dùng ở production.

Dừng dịch vụ nhưng giữ dữ liệu:

```bash
docker compose down
```

Muốn tạo lại dữ liệu phát triển từ đầu, chủ động chạy `docker compose down --volumes`. Lệnh này xoá
toàn bộ dữ liệu trong hai volume Compose và không thể hoàn tác.

## Chạy kiểm thử

PowerShell trên Windows:

```powershell
.\gradlew.bat test
```

macOS, Linux hoặc Git Bash:

```bash
./gradlew test
```

Kết quả HTML nằm tại `backend/build/reports/tests/test/index.html`. Các thư mục `build/` là artifact
sinh tự động và không được commit.

## Chạy backend

Khởi động PostgreSQL và Redis trước, sau đó chạy backend với profile `dev`:

```powershell
docker compose up -d --wait
.\gradlew.bat :backend:bootRun --args="--spring.profiles.active=dev"
```

API lắng nghe tại `http://localhost:8080`. Actuator dùng cổng quản trị riêng `8081`; hai probe là
`http://127.0.0.1:8081/actuator/health/liveness` và
`http://127.0.0.1:8081/actuator/health/readiness`.

Profile `test` tắt tự cấu hình datasource, Flyway và Redis health để kiểm thử context không phụ thuộc
hạ tầng. Mọi integration test có truy cập dữ liệu vẫn phải dùng PostgreSQL/Redis thật qua
Testcontainers, không thay PostgreSQL bằng cơ sở dữ liệu nhúng.

Có thể kiểm tra ứng dụng độc lập với profile `test` bằng lệnh:

```powershell
.\gradlew.bat :backend:bootRun --args="--spring.profiles.active=test"
```

Production phải kích hoạt profile `prod` và truyền đủ `QROS_DB_URL`, `QROS_DB_USERNAME`,
`QROS_DB_PASSWORD`, `QROS_REDIS_URL`; repo không cung cấp giá trị bí mật hoặc mật khẩu production
mặc định.

## Kiểm tra hợp đồng API

```powershell
npm ci --ignore-scripts
npm run contract:lint
npm run contract:generate:web
.\gradlew.bat :backend:openApiValidate :backend:verifyGeneratedOpenApi :backend:compileJava
```

Model Python được sinh bằng `datamodel-code-generator` đã ghim phiên bản trong
`ml/requirements-codegen.txt`; lệnh đầy đủ nằm tại `docs/api/README.md`. Khi đổi OpenAPI, phải commit
lại type của cả `web-guest`, `web-staff` và `ml/models/generated.py`. Workflow
`.github/workflows/contract-check.yml` sinh lại rồi dùng `git diff --exit-code` để chặn contract drift.

## Tài liệu nguồn

- Yêu cầu sản phẩm: `docs/PRD.md`
- Thiết kế hệ thống: `docs/architecture/SDD.md`
- Hợp đồng HTTP và realtime: `docs/api/openapi.yaml`, `docs/api/asyncapi.yaml`
- Backlog và thứ tự triển khai: `docs/backlog.md`
- Quy tắc làm việc trong repo: `CLAUDE.md`

Mọi thay đổi API phải bắt đầu từ spec. Mọi commit phải tham chiếu ít nhất một mã `FR-*`.
