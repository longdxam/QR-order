package com.qros.shared.event;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {

    /**
     * Nhận phần việc của lượt poll này và khoá đúng những dòng đó.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} là lý do nhiều bản sao ứng dụng có thể cùng chạy poller mà
     * không phát trùng: bản sao thứ hai bỏ qua dòng đang bị khoá thay vì chờ. Khoá được giữ tới khi
     * giao dịch của lượt poll kết thúc, nên nếu phát thất bại và giao dịch cuộn ngược thì dòng trở
     * lại trạng thái chưa phát cho lượt sau.
     *
     * <p>{@code ORDER BY id} giữ đúng thứ tự sinh sự kiện trong một lượt.
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEntity> claimUnpublished(@Param("batchSize") int batchSize);

    @Modifying
    @Query("UPDATE OutboxEntity o SET o.publishedAt = :publishedAt WHERE o.id IN :ids")
    int markPublished(@Param("ids") Collection<Long> ids, @Param("publishedAt") Instant publishedAt);

    @Query("""
            SELECT o FROM OutboxEntity o
            WHERE o.storeId = :storeId AND o.id > :lastSeq AND o.occurredAt >= :cutoff
            ORDER BY o.id
            """)
    List<OutboxEntity> findReplayForStore(@Param("storeId") UUID storeId,
            @Param("lastSeq") long lastSeq, @Param("cutoff") Instant cutoff);

    @Query("""
            SELECT CASE WHEN count(o) > 0 THEN true ELSE false END FROM OutboxEntity o
            WHERE o.storeId = :storeId AND o.id > :lastSeq AND o.occurredAt < :cutoff
            """)
    boolean hasExpiredEventsForStore(@Param("storeId") UUID storeId,
            @Param("lastSeq") long lastSeq, @Param("cutoff") Instant cutoff);
}
