package com.qros.ordering.domain;

import java.time.Instant;
import java.util.List;
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
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_PREPARING = "PREPARING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_SERVED = "SERVED";
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

    public UUID getTableId() {
        return tableId;
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

    /** Đơn không thuộc trường hợp EC-03 được đưa thẳng vào hàng pha chế. */
    public void xacNhanTuDong() {
        if (!STATUS_PENDING.equals(status) || requiresStaffConfirmation) {
            throw new QrosException(ErrorCode.INVALID_TRANSITION,
                    "Đơn này không thể tự động xác nhận");
        }
        status = STATUS_CONFIRMED;
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

    /**
     * Đồng bộ trạng thái tổng từ các dòng món. Dòng chậm nhất còn hoạt động quyết định trạng thái đơn;
     * các dòng đã huỷ không kéo lùi tiến trình, còn tất cả cùng huỷ thì đơn mới thành {@code CANCELLED}.
     */
    public boolean dongBoTrangThaiDong(List<String> lineStatuses) {
        if (lineStatuses.isEmpty()) return false;
        List<String> active = lineStatuses.stream()
                .filter(lineStatus -> !OrderLine.STATUS_CANCELLED.equals(lineStatus))
                .toList();
        String next = active.isEmpty() ? STATUS_CANCELLED : active.stream()
                .min((left, right) -> Integer.compare(thuTu(left), thuTu(right)))
                .orElseThrow();
        requiresStaffConfirmation = STATUS_PENDING.equals(next);
        if (STATUS_CANCELLED.equals(next) && cancelReason == null) {
            cancelReason = "Tất cả món trong đơn đã bị huỷ";
        }
        if (status.equals(next)) return false;
        status = next;
        return true;
    }

    private static int thuTu(String status) {
        return switch (status) {
            case STATUS_PENDING -> 0;
            case STATUS_CONFIRMED -> 1;
            case STATUS_PREPARING -> 2;
            case STATUS_READY -> 3;
            case STATUS_SERVED -> 4;
            default -> throw new IllegalArgumentException("Trạng thái dòng không hợp lệ: " + status);
        };
    }
}
