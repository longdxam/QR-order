# Chạy staging gate M1

Runbook này là điều kiện cuối của `BL-M1-06`: xác nhận một đơn đi từ mã bàn tới
`SERVED` trên staging và lưu bằng chứng E2E, tải và security regression. Không chạy k6 trên
production vì kịch bản tạo đơn thật.

## 1. Điều kiện trước khi chạy

- Bản `main` đã được deploy vào **staging**, gồm backend, web guest và web staff.
- Có một store staging riêng, một table code E2E còn dùng được, ít nhất một menu item published có
  variant active (và option active nếu dùng `QROS_E2E_OPTION_ID`).
- Có một tài khoản `BARISTA` thuộc đúng store, đăng nhập được vào KDS và có thể chuyển trạng thái
  `PENDING → CONFIRMED → PREPARING → READY → SERVED`.
- Fixture được reset về trạng thái không có order/session đang chặn trước mỗi lần chạy. Workflow đã
  tuần tự Chromium rồi WebKit; không cho chạy song song lại trước khi có fixture riêng theo run.
- Nếu staging dùng MFA, tạo TOTP/credential automation có vòng đời ngắn. Không dùng mật khẩu hay
  TOTP tài khoản nhân viên thật.

## 2. Secrets trong GitHub Environment `staging`

Mở `Settings → Environments → staging → Environment secrets`, thêm đúng các tên sau. Giá trị trong
bảng chỉ là kiểu dữ liệu, không commit giá trị thật.

| Secret | Giá trị cần cấp | Dùng bởi |
|---|---|---|
| `QROS_GUEST_URL` | URL web guest, không có slash cuối | Playwright |
| `QROS_STAFF_URL` | URL web staff, không có slash cuối | Playwright |
| `QROS_API_URL` | URL public của backend API, không có slash cuối | k6 |
| `QROS_E2E_TABLE_CODE` | short code của bàn fixture | Playwright + k6 |
| `QROS_E2E_STORE_ID` | UUID store fixture | Playwright |
| `QROS_E2E_STAFF_EMAIL` | email barista automation | Playwright |
| `QROS_E2E_STAFF_PASSWORD` | mật khẩu barista automation | Playwright |
| `QROS_E2E_STAFF_TOTP` | TOTP automation, chỉ khi MFA bật | Playwright |
| `QROS_E2E_MENU_ITEM_ID` | UUID menu item fixture published | k6 order |
| `QROS_E2E_VARIANT_ID` | UUID variant active của item | k6 order |
| `QROS_E2E_OPTION_ID` | UUID option active, hoặc để trống khi item không cần option | k6 order |

`staging-gate.yml` chỉ đọc các secret này. Không cần tạo secret tên khác để workflow chạy.

## 3. Preflight thủ công

Trước workflow, kiểm tra trên trình duyệt bình thường:

1. Mở `QROS_GUEST_URL/ma-ban`, nhập `QROS_E2E_TABLE_CODE`, tới menu được.
2. Đăng nhập `QROS_STAFF_URL` bằng barista và `QROS_E2E_STORE_ID`, mở được `/kds`.
3. Xác nhận guest/staff/API đều dùng HTTPS công khai để GitHub-hosted runner truy cập được.
4. Nếu table code đã có phiên/order lỗi từ lượt trước, reset fixture trước; không sửa dữ liệu
   production để làm việc này.

## 4. Chạy và đọc kết quả

Vào `Actions → Staging gate M1 → Run workflow → Run workflow` trên nhánh `main`.

Gate chỉ đạt khi cả ba job xanh:

- `Security regressions for M1`: QR forgery/replay, giá giả và IDOR M1.
- `QR to SERVED (chromium)` và `QR to SERVED (webkit)`: cả hai browser hoàn tất luồng tới `SERVED`.
- `k6 menu and ordering`: menu p95 < 120 ms, p99 < 250 ms; đặt món p95 < 400 ms, p99 < 800 ms;
  lỗi HTTP dưới 1%.

Tải artifact `playwright-chromium`, `playwright-webkit`, `security-regressions` và `k6-summary` từ
workflow run. Lưu URL run GitHub, ngày giờ, commit SHA và tóm tắt k6 vào bằng chứng phát hành. Lượt
fail phải giữ artifact để điều tra; không chạy lại rồi xoá dấu vết lượt fail.

## 5. Khi gate đỏ

- **Thiếu biến môi trường / đăng nhập fail:** kiểm tra đúng tên secret, URL và vai trò barista; không
  in secret vào log hay issue.
- **Chromium hoặc WebKit fail sau một lượt thành công:** reset fixture, rồi kiểm tra artifact. Không
  tăng `max-parallel` để "chạy nhanh".
- **k6 fail threshold:** giữ output run, kiểm tra metrics backend/DB/Redis theo cùng khoảng thời gian;
  không nới threshold nếu chưa có quyết định NFR.
- **Security regression fail:** dừng đánh dấu `BL-M1-06`; sửa code/contract, thêm test hồi quy rồi chạy
  lại toàn bộ workflow.
