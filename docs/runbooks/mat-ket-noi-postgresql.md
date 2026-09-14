# Runbook: Mất kết nối PostgreSQL

| Trường | Giá trị |
|---|---|
| Mã cảnh báo | `QrosDatabaseDown` |
| Mức độ | Nghiêm trọng (critical) |
| Tham chiếu | `BL-M0-02`, `NFR-AVL-03` |

## Triệu chứng

Probe `GET /actuator/health/readiness` (cổng `8081`) trả `503`/`DOWN`. Phần lớn request ghi/đọc
CSDL trả `500 INTERNAL_ERROR` (RFC 7807, không lộ chi tiết kết nối — xem `TM-OPS-02`).

## Nguyên nhân khả dĩ

1. PostgreSQL container/instance ngừng chạy hoặc đang khởi động lại.
2. Hết kết nối trong pool HikariCP (tải cao bất thường hoặc rò kết nối).
3. Sai thông tin xác thực sau khi xoay mật khẩu mà chưa cập nhật `QROS_DB_PASSWORD`/`QROS_DB_URL`.

## Các bước xử lý

1. `docker compose ps postgres` — xác nhận container ở trạng thái `healthy`. Nếu không:
   `docker compose up -d --wait postgres` và theo dõi log khởi động.
2. `GET /actuator/health/readiness` sau khi PostgreSQL khoẻ lại — probe tự phục hồi khi
   `DataSource` kết nối lại được, không cần khởi động lại tiến trình backend.
3. Nếu PostgreSQL khoẻ nhưng backend vẫn báo lỗi: kiểm log tìm
   `HikariPool-1 - Connection is not available, request timed out` — dấu hiệu hết pool. Tra xem có
   giao dịch treo bất thường không (`SELECT * FROM pg_stat_activity WHERE state <> 'idle' AND
   query_start < now() - interval '1 minute';`).
4. Nếu nghi lỗi xác thực: xác nhận `QROS_DB_URL`/`QROS_DB_USERNAME`/`QROS_DB_PASSWORD` khớp với
   PostgreSQL đang chạy — profile `prod` từ chối khởi động nếu thiếu biến, nhưng sai giá trị (mật
   khẩu cũ) vẫn khởi động được và chỉ lộ ra khi thử kết nối thật.

## Xác minh đã xử lý xong

`GET /actuator/health/readiness` trả `200`/`UP` liên tục trong 5 phút, và tỷ lệ `500` trên các
endpoint nghiệp vụ trở lại mức nền.
