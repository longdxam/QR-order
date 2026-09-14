package com.qros.venue.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.qros.venue.domain.TableSessionEntity;

public interface TableSessionRepository extends JpaRepository<TableSessionEntity, UUID> {

    /** {@code ⚠} khớp chỉ mục riêng phần ở {@code V1__baseline.sql} ({@code uq_open_session_per_table}). */
    Optional<TableSessionEntity> findByTableIdAndStatus(UUID tableId, String status);

    /**
     * Khoá tư vấn (advisory lock) phạm vi giao dịch theo bàn — giữ tới khi giao dịch kết thúc
     * (commit hay rollback), PostgreSQL tự nhả, không cần unlock tay. Hai request cùng quét một
     * bàn cùng lúc: request thứ hai **chờ** ở đây tới khi request thứ nhất commit xong, rồi mới đọc
     * {@link #findByTableIdAndStatus}, nên luôn thấy đúng phiên vừa được tạo — khác
     * {@code IdempotencyRepository.claim} (khoá qua chính hàng CSDL bằng {@code INSERT ... ON
     * CONFLICT}), ở đây chưa có hàng nào để khoá lên trước khi phiên được tạo, nên phải khoá theo
     * giá trị {@code tableId} thay vì khoá theo hàng.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(CAST(:tableId AS text))::bigint)", nativeQuery = true)
    void khoaTheoBan(@Param("tableId") UUID tableId);
}
