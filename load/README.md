# Kịch bản tải M1

- `menu.js`: 500 virtual users revalidate menu bằng ETag; chặn nếu p95 ≥ 120 ms hoặc p99 ≥ 250 ms.
- `orders.js`: mặc định 25 virtual users đặt món; chặn nếu p95 ≥ 400 ms hoặc p99 ≥ 800 ms.

Chạy bằng k6 cài trên máy hoặc container `grafana/k6`. Các biến ID/mã bàn phải trỏ tới fixture riêng
của staging; không chạy `orders.js` trên production vì nó tạo dữ liệu thật.

```powershell
docker run --rm -i -e QROS_API_URL=http://host.docker.internal:8080 `
  -e QROS_TABLE_CODE=A1B2C3 grafana/k6 run - < load/menu.js
```
