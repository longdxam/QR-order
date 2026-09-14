# Runbook: Đăng nhập thất bại hàng loạt

| Trường | Giá trị |
|---|---|
| Mã cảnh báo | `QrosLoginFailureSpike` |
| Mức độ | Cảnh báo (warning) |
| Tham chiếu | `FR-AUTH-03`, `TM-AUTH-01` |

## Triệu chứng

Tỷ lệ phản hồi `401 INVALID_CREDENTIALS` hoặc `423 ACCOUNT_LOCKED` từ `POST /api/v1/auth/login`
tăng bất thường trong một khoảng thời gian ngắn, có thể tập trung vào một nhóm nhỏ tài khoản hoặc
trải rộng nhiều tài khoản khác nhau.

## Nguyên nhân khả dĩ

1. **Dò mật khẩu/credential stuffing** — kẻ tấn công thử một danh sách email/mật khẩu rò rỉ từ nơi
   khác. Khoá luỹ tiến (`AppUser.registerFailedAttempt`, `BL-M0-08`) đã tự chặn từng tài khoản sau
   5 lần sai, nhưng không chặn được việc thử nhiều tài khoản khác nhau từ cùng một nguồn.
2. **Sự cố phía khách hàng** — một bản cập nhật ứng dụng nhân viên gửi sai mật khẩu đã lưu, hoặc
   đồng hồ thiết bị lệch làm TOTP luôn sai (`FR-AUTH-02`).
3. **Báo động giả** — một tài khoản thật quên mật khẩu và thử nhiều lần liên tiếp; một mình việc
   này không đáng báo động trừ khi tương quan với nhiều tài khoản.

## Các bước xử lý

1. Tra theo `traceId` của một vài request lỗi mẫu (xem `NFR-OBS-01`) để xác nhận IP nguồn — hạ
   tầng chặn IP theo tầng mạng chưa có ở M0, ghi lại IP để làm bằng chứng cho bước tiếp theo.
2. Kiểm `docker compose exec postgres psql -U qros -d qros -c "SELECT email, failed_attempts,
   lockout_count, locked_until FROM app_user WHERE failed_attempts > 0 OR locked_until >
   now() ORDER BY failed_attempts DESC LIMIT 20;"` — xác nhận khoá luỹ tiến đang hoạt động đúng
   (`lockout_count` tăng dần qua các lượt, không có tài khoản nào bỏ qua được khoá).
3. Nếu là credential stuffing thật: không có hành động thủ công nào cần làm ở tầng ứng dụng — khoá
   luỹ tiến đã tự xử lý theo thiết kế. Cân nhắc chặn IP nguồn ở tầng mạng/WAF nếu có (`OPEN-01`,
   chưa chốt Nginx hay Spring Cloud Gateway).
4. Nếu nghi ngờ một tài khoản thật bị lộ mật khẩu (không phải bị dò ngẫu nhiên): sau khi xác minh
   danh tính nhân viên qua kênh khác, dùng `AppUser.bumpTokenVersion()` (chưa có endpoint quản trị,
   `FR-AUTH-05`) để vô hiệu hoá mọi phiên đang mở, rồi yêu cầu đặt lại mật khẩu.

## Xác minh đã xử lý xong

Tỷ lệ `401`/`423` trên `/api/v1/auth/login` trở lại mức nền trong 15 phút liên tiếp.

## Diễn tập ngày 14/09/2026 (`BL-M0-14`)

Chạy thật trên profile `dev` (Compose PostgreSQL/Redis, bản đóng gói `bootJar`), không phải đọc
tài liệu suông:

1. Tạo một tài khoản `STORE_MANAGER` tối thiểu thẳng trong CSDL (chưa có endpoint đăng ký, `M3`),
   gửi 5 yêu cầu `POST /api/v1/auth/login` sai mật khẩu liên tiếp — bốn lần đầu trả `401`, lần thứ
   năm trả `423 ACCOUNT_LOCKED`, đúng kịch bản của mục "Triệu chứng".
2. **Bước 1 của runbook ban đầu không thực hiện được**: ở mức log mặc định (root `INFO`),
   `GlobalExceptionHandler` ghi đăng nhập sai ở mức `DEBUG` — không có dòng log nào để tra `traceId`
   ngược ra IP nguồn. Đây là một lỗ hổng thật của runbook, không phải giả định lý thuyết.
   - **Đã vá**: `GlobalExceptionHandler` nay ghi `INVALID_CREDENTIALS`/`ACCOUNT_LOCKED` ở mức
     `INFO` kèm `remoteAddr`, giữ nguyên các mã lỗi khác ở `DEBUG` (không đổi hành vi ngoài phạm vi
     runbook này). Có test khoá hành vi:
     `security/CredentialStuffingTest#frAuth01_dangNhapSai_lenLogMucInfoKemIpNguon`.
   - Diễn tập lại sau khi vá: `traceId` trong thân phản hồi (`7b6b3e1c...`) khớp đúng dòng log
     `"Đăng nhập thất bại: ACCOUNT_LOCKED remoteAddr=127.0.0.1"` mang cùng `traceId`.
3. **Bước 2 xác nhận đúng**: `SELECT email, failed_attempts, lockout_count, locked_until FROM
   app_user ...` trả `lockout_count=1`, `locked_until` khoảng 15 phút sau, `failed_attempts=0` (đã
   reset khi khoá kích hoạt) — đúng máy trạng thái `AppUser` mô tả ở `BL-M0-08`.
4. **Bước 3** (credential stuffing thật): xác nhận không cần hành động thủ công — khoá luỹ tiến tự
   xử lý, khớp thiết kế.
5. **Bước 4** (tài khoản thật bị lộ mật khẩu): chưa diễn tập được — `AppUser.bumpTokenVersion()`
   chưa có endpoint quản trị gọi vào (ghi ở `BL-M0-09`, vẫn đúng).
6. Xoá dữ liệu diễn tập khỏi CSDL dev sau khi xong (không để lại tài khoản/chi nhánh giả).

Kết luận: runbook dùng được cho bước 2–3 ngay từ đầu; bước 1 cần bản vá log ở trên mới dùng được;
bước 4 phụ thuộc `OPEN-07`-adjacent (endpoint quản trị chưa tồn tại, ngoài phạm vi M0).
