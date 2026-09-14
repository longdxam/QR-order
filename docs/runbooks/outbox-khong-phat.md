# Runbook: Outbox không phát sự kiện

| Trường | Giá trị |
|---|---|
| Mã cảnh báo | `QrosOutboxBacklogGrowing` |
| Mức độ | Nghiêm trọng (critical) |
| Tham chiếu | `ADR-05`, `TM-EVT-01`, `BL-M0-05` |

## Triệu chứng

Số dòng `outbox_event` có `published_at IS NULL` tăng liên tục thay vì giữ ở mức thấp/dao động
quanh 0. Hệ quả nghiệp vụ: KDS và các kênh realtime khác không nhận được cập nhật mới.

## Nguyên nhân khả dĩ

1. **Redis không kết nối được** — `OutboxPoller` cuộn ngược giao dịch khi phát thất bại (đúng
   thiết kế, xem `OutboxPoller` Javadoc), nên sự kiện vẫn còn nguyên trong hàng đợi chứ không mất,
   nhưng cũng không phát cho tới khi Redis sống lại.
2. **`OutboxScheduler` đã dừng** — một ngoại lệ không lường trước thoát ra khỏi
   `OutboxScheduler.pollOnce()` sẽ khiến Spring huỷ lịch `@Scheduled` vĩnh viễn cho tới khi khởi
   động lại tiến trình (đây chính là lý do `OutboxScheduler` nuốt mọi `RuntimeException`, nhưng một
   `Error` — ví dụ `OutOfMemoryError` — vẫn có thể thoát ra).
3. **Backlog quá lớn** — `OutboxProperties.batchSize` nhỏ hơn tốc độ sự kiện mới sinh ra trong giờ
   cao điểm.

## Các bước xử lý

1. `docker compose exec redis redis-cli --no-auth-warning -a "$QROS_REDIS_PASSWORD" ping` — xác
   nhận Redis còn sống. Nếu không, khởi động lại container Redis; `OutboxPoller` tự phát lại các
   dòng còn tồn đọng ở lượt kế tiếp, không cần can thiệp thêm.
2. Kiểm log tiến trình backend tìm dòng `Lượt phát outbox thất bại; sự kiện chưa phát vẫn còn
   trong bảng` (từ `OutboxScheduler`, mức `ERROR`) — nếu có, tiến trình vẫn đang thử lại mỗi nhịp
   `OutboxProperties.pollInterval`, không cần khởi động lại.
3. Nếu log KHÔNG còn xuất hiện các dòng bình thường của `OutboxScheduler` nữa (kể cả dòng lỗi) —
   nghi ngờ lịch đã bị huỷ bởi một `Error`. Khởi động lại tiến trình backend; `@Scheduled` đăng ký
   lại từ đầu khi context khởi động.
4. Backlog lớn nhưng Redis/scheduler đều khoẻ: theo dõi tốc độ giảm của backlog qua vài nhịp poll;
   nếu không giảm, tăng `qros.outbox.batch-size` tạm thời và khởi động lại.

## Xác minh đã xử lý xong

`SELECT count(*) FROM outbox_event WHERE published_at IS NULL;` giảm về gần 0 và không tăng lại
trong 10 phút liên tiếp.
