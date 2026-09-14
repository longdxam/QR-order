# Runbooks

Sổ tay xử lý sự cố — `NFR-OBS-05`: mỗi cảnh báo trong `infra/alerts/qros-alerts.yaml` phải kèm
liên kết tới đúng một file trong thư mục này (`security/AlertRunbookLinksTest` kiểm liên kết
không trỏ chết, không kiểm nội dung).

| Runbook | Cảnh báo tương ứng |
|---|---|
| [Đăng nhập thất bại hàng loạt](dang-nhap-that-bai-hang-loat.md) | `QrosLoginFailureSpike` |
| [Phát hiện tái sử dụng refresh token](refresh-token-tai-su-dung.md) | `QrosRefreshTokenReuseDetected` |
| [Outbox không phát sự kiện](outbox-khong-phat.md) | `QrosOutboxBacklogGrowing` |
| [Mất kết nối PostgreSQL](mat-ket-noi-postgresql.md) | `QrosDatabaseDown` |

Thêm runbook mới thì thêm luôn dòng cảnh báo trỏ tới nó trong `infra/alerts/qros-alerts.yaml` —
test sẽ bắt lỗi thiếu ở chiều ngược lại (cảnh báo trỏ tới runbook không tồn tại), nhưng không bắt
được runbook mồ côi không có cảnh báo nào trỏ tới.
