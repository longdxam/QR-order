package com.qros.venue.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.qros.shared.id.UuidV7;

/**
 * Phiên bàn — lớp 2 mục 5.3.2. Tên lớp tránh trùng {@code com.qros.generated.model.TableSession}
 * (DTO của hợp đồng OpenAPI); entity JPA và DTO API không phải cùng một kiểu, cố tình đặt tên khác
 * nhau để không ai nhầm lẫn dùng lẫn hai thứ.
 */
@Entity
@Table(name = "table_session")
public class TableSessionEntity {

    public static final String STATUS_OPEN = "OPEN";

    /** TTL trượt theo hoạt động — lớp 2 mục 5.3.2. */
    private static final Duration SESSION_TTL = Duration.ofMinutes(90);

    /** "Cùng nhóm" theo bảng tín hiệu {@code EC-02}: phiên mở dưới ngần này trước. */
    private static final Duration JOIN_MAX_SESSION_AGE = Duration.ofMinutes(90);

    /** "Cùng nhóm": có hoạt động trong ngần này gần nhất. */
    private static final Duration JOIN_MAX_INACTIVITY = Duration.ofMinutes(10);

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // EC-03: đơn đầu của phiên lạ chờ nhân viên xác nhận, trừ khi thu ngân đã mở bàn cho phiên đó.
    // Cột này chưa có nơi ghi thật (mở bàn qua thu ngân là module khác/thẻ khác) — mặc định false.
    @Column(name = "staff_opened", nullable = false)
    private boolean staffOpened;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected TableSessionEntity() {
        // JPA
    }

    /** Mở một phiên mới cho bàn — nhánh "nhóm mới" hoặc bàn chưa từng có phiên nào. */
    public static TableSessionEntity open(UUID storeId, UUID tableId, Instant now) {
        TableSessionEntity session = new TableSessionEntity();
        session.id = UuidV7.generate();
        session.storeId = storeId;
        session.tableId = tableId;
        session.status = STATUS_OPEN;
        session.openedAt = now;
        session.staffOpened = false;
        session.recordActivity(now);
        return session;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getTableId() {
        return tableId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** {@code EC-03}: đơn đầu của phiên chưa được thu ngân mở phải chờ nhân viên xác nhận. */
    public boolean isStaffOpened() {
        return staffOpened;
    }

    public boolean isOpen() {
        return STATUS_OPEN.equals(status);
    }

    /**
     * {@code EC-02}: thiết bị mới có được **tham gia** phiên đang mở này hay không — hai dấu hiệu
     * "cùng nhóm" phải đúng cả hai. Nếu sai, endpoint trả {@code 409} để chuyển sang nhân viên
     * ("nhóm mới" hoặc phiên đã nguội) — hàm này không tự quyết định đóng phiên cũ.
     */
    public boolean canJoin(Instant now) {
        boolean phienConMoi = !openedAt.plus(JOIN_MAX_SESSION_AGE).isBefore(now);
        boolean vuaCoHoatDong = !lastActivityAt.plus(JOIN_MAX_INACTIVITY).isBefore(now);
        return phienConMoi && vuaCoHoatDong;
    }

    /** TTL trượt theo hoạt động: mọi lượt tham gia/hoạt động đẩy {@code expiresAt} thêm 90 phút. */
    public void recordActivity(Instant now) {
        this.lastActivityAt = now;
        this.expiresAt = now.plus(SESSION_TTL);
    }
}
