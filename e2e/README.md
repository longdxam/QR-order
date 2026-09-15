# Cổng E2E M1

Suite chạy cùng một luồng mã bàn → menu → giỏ → đặt món → KDS → `SERVED` trên Chromium và WebKit.
Fixture staging phải có một bàn trống, ít nhất một món có biến thể khả dụng và một tài khoản barista.

```powershell
$env:QROS_GUEST_URL='https://order.staging.example.com'
$env:QROS_STAFF_URL='https://staff.staging.example.com'
$env:QROS_E2E_TABLE_CODE='A1B2C3'
$env:QROS_E2E_STORE_ID='00000000-0000-0000-0000-000000000000'
$env:QROS_E2E_STAFF_EMAIL='barista@staging.example.com'
$env:QROS_E2E_STAFF_PASSWORD='<secret>'
npm ci --prefix e2e
npx --prefix e2e playwright install chromium webkit
npm test --prefix e2e
```

Không commit thông tin fixture hoặc mật khẩu staging. Khi MFA được bật, cấp thêm
`QROS_E2E_STAFF_TOTP` từ secret ngắn hạn của môi trường kiểm thử.

Trên GitHub Actions, chạy thủ công workflow `Staging gate M1` trong environment `staging`.
Environment này phải có các secrets ở trên, cùng `QROS_API_URL`, `QROS_E2E_MENU_ITEM_ID` và
`QROS_E2E_VARIANT_ID` cho hai cổng k6. Runner Ubuntu tự cài dependency hệ điều hành của browser;
lệnh cục bộ trên Windows không dùng cờ `--with-deps`.
