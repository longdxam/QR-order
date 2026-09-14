package com.qros.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.qros.identity.domain.MfaBackupCode;

public interface MfaBackupCodeRepository extends JpaRepository<MfaBackupCode, UUID> {

    List<MfaBackupCode> findByUserId(UUID userId);

    /**
     * Tiêu một mã dự phòng — cập nhật có điều kiện {@code WHERE used_at IS NULL} để hai yêu cầu
     * song song cùng một mã không thể cùng "thắng", cùng kỹ thuật với
     * {@code IdempotencyRepository.claim}.
     *
     * @return {@code 1} nếu lượt gọi này là lượt tiêu mã thành công, {@code 0} nếu mã không tồn
     *         tại hoặc đã dùng trước đó.
     */
    @Modifying
    @Query("UPDATE MfaBackupCode c SET c.usedAt = :now "
            + "WHERE c.userId = :userId AND c.codeHash = :codeHash AND c.usedAt IS NULL")
    int consume(@Param("userId") UUID userId, @Param("codeHash") String codeHash,
            @Param("now") Instant now);
}
