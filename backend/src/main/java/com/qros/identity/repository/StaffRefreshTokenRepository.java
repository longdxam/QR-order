package com.qros.identity.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.qros.identity.domain.StaffRefreshToken;

public interface StaffRefreshTokenRepository extends JpaRepository<StaffRefreshToken, UUID> {

    Optional<StaffRefreshToken> findByTokenHash(String tokenHash);

    /**
     * Giành quyền tiêu một token — {@code WHERE used_at IS NULL AND revoked_at IS NULL} chặn hai
     * yêu cầu song song cùng dùng một token cùng "thắng" (chính kịch bản {@code TM-AUTH-03} phải
     * bắt được).
     *
     * @return {@code 1} nếu lượt gọi này thắng, {@code 0} nếu token đã bị tiêu hoặc thu hồi.
     */
    @Modifying
    @Query("UPDATE StaffRefreshToken t SET t.usedAt = :now "
            + "WHERE t.id = :id AND t.usedAt IS NULL AND t.revokedAt IS NULL")
    int claim(@Param("id") UUID id, @Param("now") Instant now);

    /** Thu hồi toàn bộ chuỗi token — hành động khi phát hiện tái sử dụng. */
    @Modifying
    @Query("UPDATE StaffRefreshToken t SET t.revokedAt = :now "
            + "WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
