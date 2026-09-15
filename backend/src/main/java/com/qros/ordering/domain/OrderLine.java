package com.qros.ordering.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.qros.shared.id.UuidV7;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;

@Entity
@Table(name = "order_line")
public class OrderLine {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_PREPARING = "PREPARING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_SERVED = "SERVED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    @Column(name = "menu_variant_id", nullable = false)
    private UUID menuVariantId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "variant_name", nullable = false)
    private String variantName;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false)
    private long lineTotal;

    @Column(name = "note")
    private String note;

    @Column(name = "added_by")
    private String addedBy;

    @Column(name = "station", nullable = false)
    private String station;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected OrderLine() {
        // JPA
    }

    public static OrderLine moi(UUID orderId, UUID menuItemId, UUID menuVariantId, String itemName,
            String variantName, long unitPrice, int quantity, String note, String addedBy, String station) {
        OrderLine line = new OrderLine();
        line.id = UuidV7.generate();
        line.orderId = orderId;
        line.menuItemId = menuItemId;
        line.menuVariantId = menuVariantId;
        line.itemName = itemName;
        line.variantName = variantName;
        line.unitPrice = unitPrice;
        line.quantity = quantity;
        line.lineTotal = Math.multiplyExact(unitPrice, quantity);
        line.note = note;
        line.addedBy = addedBy;
        line.station = station;
        line.status = STATUS_PENDING;
        return line;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getMenuItemId() {
        return menuItemId;
    }

    public UUID getMenuVariantId() {
        return menuVariantId;
    }

    public String getItemName() {
        return itemName;
    }

    public String getVariantName() {
        return variantName;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public long getLineTotal() {
        return lineTotal;
    }

    public String getNote() {
        return note;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public String getStation() {
        return station;
    }

    public String getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    /**
     * Máy trạng thái đóng của một món. {@code CANCELLED} có thể thắng mọi trạng thái chưa kết thúc để
     * phép hợp nhất thao tác ngoại tuyến của KDS là xác định; mọi bước tiến khác phải đi đúng một nấc.
     */
    public void chuyenTrangThai(String target, String reason, Instant now) {
        if (STATUS_CANCELLED.equals(target)) {
            if (STATUS_CANCELLED.equals(status)) {
                tuChoiChuyen(target);
            }
            if (reason == null || reason.isBlank()) {
                throw new QrosException(ErrorCode.REASON_REQUIRED,
                        "Cần nêu lý do khi huỷ món");
            }
            status = STATUS_CANCELLED;
            return;
        }

        String expected = switch (status) {
            case STATUS_PENDING -> STATUS_CONFIRMED;
            case STATUS_CONFIRMED -> STATUS_PREPARING;
            case STATUS_PREPARING -> STATUS_READY;
            case STATUS_READY -> STATUS_SERVED;
            default -> null;
        };
        if (!target.equals(expected)) {
            tuChoiChuyen(target);
        }

        status = target;
        if (STATUS_PREPARING.equals(target)) {
            startedAt = now;
        } else if (STATUS_READY.equals(target)) {
            readyAt = now;
        }
    }

    public void xacNhanTuDong(Instant now) {
        chuyenTrangThai(STATUS_CONFIRMED, null, now);
    }

    private void tuChoiChuyen(String target) {
        throw new QrosException(ErrorCode.INVALID_TRANSITION,
                "Không thể chuyển món từ %s sang %s".formatted(status, target));
    }
}
