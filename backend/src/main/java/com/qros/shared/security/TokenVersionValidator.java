package com.qros.shared.security;

/**
 * Cổng kiểm {@code token_version} — {@code FR-AUTH-05}: đổi mật khẩu, đổi vai trò, hoặc quản trị
 * viên thu hồi quyền phải vô hiệu hoá **toàn bộ** token đang hoạt động của người dùng đó bằng cách
 * tăng {@code token_version} trong CSDL; token cũ mang giá trị cũ bị từ chối dù chữ ký/hạn vẫn hợp lệ.
 *
 * <p>Tách thành cổng thay vì gọi thẳng {@code AppUserRepository} từ {@code shared/security} — luật
 * quan trọng nhất của repo cấm {@code shared} phụ thuộc nghiệp vụ. Cùng khuôn với
 * {@link com.qros.shared.web.TraceIdProvider}: identity cung cấp hiện thực thật, đè hiện thực mặc
 * định luôn đồng ý (đủ để mọi profile — kể cả {@code test} không có CSDL — khởi động được).
 */
public interface TokenVersionValidator {

    boolean isCurrent(String subject, int tokenVersion);
}
