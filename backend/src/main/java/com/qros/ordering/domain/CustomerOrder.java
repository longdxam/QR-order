package com.qros.ordering.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.shared.id.UuidV7;

/**
 * {@code ADR-06}: {@code subtotalAmount}/{@code totalAmount} do máy chủ tính, không bao giờ nhận
 * từ client. Máy trạng thái đơn giản hoá cho {@code BL-M1-03}: chỉ có bước khách tự huỷ
 * ({@code PENDING → CANCELLED}); các bước barista/thanh toán thuộc KDS/`payment` (M1-04/M2).
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "short_code", nullable = false)
    private String shortCode;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "subtotal_amount", nullable = false)
    private long subtotalAmount;

    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "requires_staff_confirmation", nullable = false)
    private boolean requiresStaffConfirmation;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected CustomerOrder() {
        // JPA
    }

    public static CustomerOrder moi(UUID storeId, UUID tableId, UUID sessionId, String shortCode,
            long subtotalAmount, boolean requiresStaffConfirmation, Instant now) {
        CustomerOrder order = new CustomerOrder();
        order.id = UuidV7.generate();
        order.storeId = storeId;
        order.tableId = tableId;
        order.sessionId = sessionId;
        order.shortCode = shortCode;
        order.status = STATUS_PENDING;
        order.subtotalAmount = subtotalAmount;
        order.discountAmount = 0;
        order.totalAmount = subtotalAmount;
        order.requiresStaffConfirmation = requiresStaffConfirmation;
        order.placedAt = now;
        return order;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getStatus() {
        return status;
    }

    public long getSubtotalAmount() {
        return subtotalAmount;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public boolean isRequiresStaffConfirmation() {
        return requiresStaffConfirmation;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public int getVersion() {
        return version;
    }

    /** {@code FR-CUS-08} kịch bản huỷ: chỉ chấp nhận khi đơn còn {@code PENDING}. */
    public void huyBoiKhach() {
        if (!STATUS_PENDING.equals(status)) {
            throw new QrosException(ErrorCode.INVALID_TRANSITION,
                    "Đơn đã rời khỏi trạng thái chờ, không tự huỷ được nữa");
        }
        this.status = STATUS_CANCELLED;
        this.cancelReason = "Khách tự huỷ";
    }
}
