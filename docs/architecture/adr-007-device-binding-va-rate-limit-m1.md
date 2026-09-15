# ADR-007 — Ràng buộc thiết bị và giới hạn lạm dụng M1

**Trạng thái:** Accepted · 15/09/2026

## Quyết định

### Token phiên khách

- Khi mở/tham gia bàn, backend phát JWS guest có claim `did` là UUID `deviceId` đã được ghi vào
  `session_device`.
- Mọi request đã xác thực tại `/api/v1/guest/**` phải gửi header `X-Device-Id` là UUID đó.
- Filter guest so sánh chính xác header với claim `did`; thiếu, sai định dạng, sai giá trị hoặc token
  cũ không có `did` đều trả `401 DEVICE_BINDING_FAILED` trước controller.
- Hai endpoint mở phiên (`POST /guest/sessions` và `/guest/sessions/by-code`) chưa có token nên vẫn
  nhận `deviceId` trong JSON. Client lưu một UUID ổn định trong browser và dùng cùng UUID ở body/header.

Lý do: token bị đánh cắp/chia sẻ không còn đủ để đọc hoặc đặt món từ thiết bị khác. Không dùng
fingerprint trình duyệt hay IP vì không ổn định và xâm phạm riêng tư hơn UUID client tự sinh.

### Rate-limit khi Redis lỗi

- `POST /guest/sessions`, `POST /guest/sessions/by-code` và `POST /guest/orders` là **fail-closed**:
  Redis không khả dụng thì trả `503 RATE_LIMIT_UNAVAILABLE`, không tạo phiên/đơn.
- Endpoint đọc (`GET menu`, `GET order`, `GET session`) không bị chặn bởi limiter; nếu sau này có
  limiter đọc, chính sách là **fail-open** để thực đơn vẫn dùng được khi Redis sự cố.
- Ngưỡng M1: mở phiên theo IP 5 lần/10 phút và đặt đơn 3 lần/5 phút/phiên; giới hạn 8 dòng món/đơn,
  tổng không quá 2.000.000 VND/bàn vẫn được thực thi ở domain ngay cả khi Redis hoạt động.

Lý do: tạo phiên/đơn có tác động trạng thái và là bề mặt abuse, nên ưu tiên toàn vẹn/kho hơn tính
sẵn sàng trong sự cố Redis. Đọc menu không tạo tác động nên ưu tiên khả dụng.

## Hệ quả triển khai

1. Sửa `openapi.yaml`, sinh lại server/client type.
2. Phát claim `did`, thêm filter guest và `SessionBindingTest`.
3. Frontend gửi `X-Device-Id` cho mọi API khách sau khi có phiên.
4. Thay `TableScanRateLimiter` permissive bằng Redis implementation; thêm limiter đặt đơn,
   `OrderAbuseLimitTest` và metric/alert Redis failure.
5. Chạy lại toàn bộ test rồi staging gate, vì token fixture cũ không còn hợp lệ.
