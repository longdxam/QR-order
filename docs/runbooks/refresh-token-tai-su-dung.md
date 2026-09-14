# Runbook: Phát hiện tái sử dụng refresh token

| Trường | Giá trị |
|---|---|
| Mã cảnh báo | `QrosRefreshTokenReuseDetected` |
| Mức độ | Nghiêm trọng (critical) — khả năng chiếm phiên |
| Tham chiếu | `TM-AUTH-03`, `BL-M0-09` |

## Triệu chứng

`POST /api/v1/auth/refresh` trả `401 REFRESH_REUSED`. Khác với `401 UNAUTHENTICATED` (phiên hết
hạn tự nhiên, bình thường) — `REFRESH_REUSED` chỉ xảy ra khi có người dùng lại một refresh token
**đã tiêu** (xem `RefreshTokenService.rotate`), và toàn bộ `family_id` của phiên đó đã bị thu hồi
ngay khi phát hiện.

## Nguyên nhân khả dĩ

1. **Token bị đánh cắp thật** — kẻ tấn công có bản sao cookie `qros_refresh` (qua thiết bị dùng
   chung, XSS ở nơi khác, hoặc log/proxy vô tình ghi lại cookie) và dùng song song với chủ tài
   khoản thật. Bên nào tiêu token trước "thắng"; bên còn lại nhận `REFRESH_REUSED`.
2. **Race benign phía khách** — một tab/thiết bị gọi `/auth/refresh` hai lần gần như đồng thời
   (ví dụ do mất mạng rồi client tự động thử lại trong khi lượt đầu vẫn đang xử lý). Cơ chế
   `StaffRefreshTokenRepository.claim` đảm bảo chỉ một lượt thắng; lượt thua nhận đúng cùng tín
   hiệu như bị tấn công thật — đây là đánh đổi có chủ ý của thiết kế, xem Javadoc
   `RefreshTokenService.rotate`.

## Các bước xử lý

1. Xác nhận đây không phải nhiễu diện rộng: nếu NHIỀU tài khoản cùng báo `REFRESH_REUSED` trong
   cùng khung giờ, nghi ngờ (2) — một bản dựng client mới có lỗi gọi refresh trùng lặp, kiểm log
   phía `web-staff` trước khi coi là sự cố bảo mật.
2. Nếu chỉ một vài tài khoản riêng lẻ: tra `traceId` của request `REFRESH_REUSED` để lấy IP/thời
   điểm, đối chiếu với lịch sử đăng nhập gần nhất của tài khoản đó.
3. Chuỗi token (`family_id`) đã tự động bị thu hồi — tài khoản đó **đã bị đăng xuất khỏi mọi
   thiết bị** ngay khi phát hiện, không cần thao tác thủ công thêm ở bước này.
4. Liên hệ nhân viên qua kênh khác (điện thoại/gặp trực tiếp) xác nhận họ có vừa đăng nhập từ thiết
   bị lạ hay không. Nếu xác nhận bị lộ: yêu cầu đổi mật khẩu ngay khi có endpoint đổi mật khẩu
   (`FR-AUTH-05`, chưa có ở M0) — trong lúc chưa có, tài khoản vẫn an toàn vì phiên cũ đã mất hiệu
   lực, nhưng mật khẩu (nếu cũng bị lộ) vẫn dùng đăng nhập lại được.

## Xác minh đã xử lý xong

Nhân viên xác nhận đăng nhập lại thành công bằng thiết bị của họ; không còn `REFRESH_REUSED` mới
cho cùng tài khoản trong 30 phút.
