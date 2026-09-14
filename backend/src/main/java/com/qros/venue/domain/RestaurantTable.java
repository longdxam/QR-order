package com.qros.venue.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Một bàn trong chi nhánh. Tên lớp tránh trùng từ khoá {@code table} và tránh đụng
 * {@code jakarta.persistence.Table}.
 */
@Entity
@Table(name = "restaurant_table")
public class RestaurantTable {

    public static final String STATUS_DISABLED = "DISABLED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "label", nullable = false)
    private String label;

    // char(6) ở CSDL; columnDefinition để ddl-auto=validate không so khớp với varchar mặc định
    // của String — cùng bẫy đã gặp ở AppUser.email/citext (BL-M0-08).
    @Column(name = "short_code", nullable = false, columnDefinition = "bpchar(6)")
    private String shortCode;

    @Column(name = "status", nullable = false)
    private String status;

    // Lớp 4 mục 5.3.2 — QR xoay vòng kiểu TOTP.
    @Column(name = "rotating_qr_enabled", nullable = false)
    private boolean rotatingQrEnabled;

    @Column(name = "totp_secret_ref")
    private String totpSecretRef;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected RestaurantTable() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public String getLabel() {
        return label;
    }

    /**
     * {@code char(6)} ở CSDL đệm khoảng trắng nếu ứng dụng từng ghi ngắn hơn 6 ký tự — bàn được
     * tạo với đúng 6 ký tự nên trong thực tế không xảy ra, nhưng {@code trim} vẫn rẻ hơn một bẫy
     * ẩn nếu giả định đó sai trong tương lai.
     */
    public String getShortCode() {
        return shortCode == null ? null : shortCode.trim();
    }

    /** Bàn bị {@code DISABLED} không được cấp phiên mới — các trạng thái khác đều hợp lệ. */
    public boolean isUsable() {
        return !STATUS_DISABLED.equals(status);
    }

    public boolean isRotatingQrEnabled() {
        return rotatingQrEnabled;
    }

    public String getTotpSecretRef() {
        return totpSecretRef;
    }
}
