package com.qros.identity.domain;

import java.time.Instant;
import java.util.UUID;

import com.qros.shared.id.UuidV7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một ca làm ({@code FR-AUTH-04}, bảng {@code work_shift} của {@code V1__baseline.sql}).
 *
 * <p>Mở ca bằng PIN trên thiết bị đã đăng ký chưa có ở {@code BL-M0-09} — cần một quyết định UX
 * (đăng ký thiết bị kiểu gì, ai chọn nhân viên trên thiết bị dùng chung) chưa có trong PRD/SDD, xem
 * {@code OPEN-08} ở {@code docs/backlog.md}. Thẻ này chỉ làm phần có thể kiểm được ngay: **đóng ca
 * tự động sau 12 giờ**, độc lập với việc ca được mở bằng cách nào.
 */
@Entity
@Table(name = "work_shift")
public class WorkShift {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected WorkShift() {
        // JPA
    }

    private WorkShift(UUID id, UUID userId, UUID storeId, Instant openedAt, Instant closedAt) {
        this.id = id;
        this.userId = userId;
        this.storeId = storeId;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
    }

    /** Mở một ca. Test dùng qua fixture cùng package cho tới khi có endpoint mở ca thật. */
    public static WorkShift open(UUID userId, UUID storeId, Instant openedAt) {
        return new WorkShift(UuidV7.generate(), userId, storeId, openedAt, null);
    }

    /** Như trên, nhưng dựng luôn ở trạng thái đã đóng — chỉ fixture test dùng. */
    public static WorkShift closed(UUID userId, UUID storeId, Instant openedAt, Instant closedAt) {
        return new WorkShift(UuidV7.generate(), userId, storeId, openedAt, closedAt);
    }

    public UUID getId() {
        return id;
    }

    public boolean isOpen() {
        return closedAt == null;
    }
}
