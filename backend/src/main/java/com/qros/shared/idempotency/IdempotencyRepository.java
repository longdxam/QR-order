package com.qros.shared.idempotency;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRepository extends JpaRepository<IdempotencyEntity, UUID> {

    /**
     * Giành quyền xử lý cho khoá này.
     *
     * <p>Trả về {@code 1} nếu lượt gọi hiện tại là lượt đầu tiên, {@code 0} nếu khoá đã có chủ và
     * kết quả cũ phải được trả lại.
     *
     * <p>Hai điểm tinh tế nằm ở đây. Thứ nhất, khi một giao dịch khác đã chèn cùng khoá mà chưa
     * commit, PostgreSQL bắt lệnh này **chờ** giao dịch đó kết thúc — nhờ vậy hai yêu cầu song song
     * không thể cùng chạy tác dụng phụ, và không cần khoá phân tán nào khác. Thứ hai, nhánh
     * {@code DO UPDATE ... WHERE expires_at <= now()} cho phép dùng lại khoá đã quá hạn 24 giờ:
     * nếu chỉ {@code DO NOTHING} thì một khoá hết hạn sẽ vĩnh viễn chặn chính nó.
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_key (key, scope, session_id, request_hash, created_at, expires_at)
            VALUES (:key, :scope, :sessionId, :requestHash, :now, :expiresAt)
            ON CONFLICT (key) DO UPDATE
               SET scope = EXCLUDED.scope,
                   session_id = EXCLUDED.session_id,
                   request_hash = EXCLUDED.request_hash,
                   response_status = NULL,
                   response_body = NULL,
                   created_at = EXCLUDED.created_at,
                   expires_at = EXCLUDED.expires_at
             WHERE idempotency_key.expires_at <= :now
            """, nativeQuery = true)
    int claim(@Param("key") UUID key, @Param("scope") String scope,
            @Param("sessionId") UUID sessionId, @Param("requestHash") String requestHash,
            @Param("now") Instant now, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query(value = """
            UPDATE idempotency_key
               SET response_status = :status, response_body = CAST(:body AS jsonb)
             WHERE key = :key
            """, nativeQuery = true)
    int complete(@Param("key") UUID key, @Param("status") int status, @Param("body") String body);

    @Modifying
    @Query("DELETE FROM IdempotencyEntity e WHERE e.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
