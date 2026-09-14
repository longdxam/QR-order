package com.qros.identity.service;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.qros.identity.repository.AppUserRepository;
import com.qros.shared.security.TokenVersionValidator;

/**
 * Hiện thực thật của {@link TokenVersionValidator} — {@code FR-AUTH-05}. Tra CSDL trên **mỗi**
 * request đã xác thực ở vùng staff/admin; đổi lại là thu hồi có hiệu lực ngay lập tức thay vì phải
 * chờ token hết hạn. Chấp nhận chi phí một truy vấn khoá chính mỗi request ở quy mô M0; cân nhắc
 * cache (Redis, TTL ngắn) nếu tải tăng — chưa cần ở đây.
 *
 * <p>{@code @Primary} để đè {@code defaultTokenVersionValidator} (luôn đồng ý) của
 * {@code shared/security} khi có {@code DataSource}.
 */
@Component
@Primary
@ConditionalOnProperty(name = "spring.datasource.url")
public class TokenVersionValidatorImpl implements TokenVersionValidator {

    private final AppUserRepository appUserRepository;

    public TokenVersionValidatorImpl(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public boolean isCurrent(String subject, int tokenVersion) {
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException malformedSubject) {
            return false;
        }
        // Tài khoản không còn tồn tại (hoặc chưa tồn tại) thì token cũng không còn hợp lệ —
        // an toàn hơn là mặc định chấp nhận.
        return appUserRepository.findById(userId)
                .map(user -> user.getTokenVersion() == tokenVersion)
                .orElse(false);
    }
}
