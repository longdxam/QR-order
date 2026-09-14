package com.qros.venue.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Chi nhánh — lớp 3 mục 5.3.2 của PRD: từ chối cấp phiên bàn ngoài giờ mở cửa.
 *
 * <p>{@code timezone} là múi giờ nghiệp vụ của chính chi nhánh (khác timezone hạ tầng UTC đã chốt ở
 * {@code BL-M0-02}) — quán ở Hà Nội và một chi nhánh giả định ở nước ngoài mở/đóng cửa theo giờ địa
 * phương của nó, không theo giờ máy chủ.
 */
@Entity
@Table(name = "store")
public class Store {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected Store() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Lớp 3 mục 5.3.2: từ chối cấp phiên nếu chi nhánh đang đóng cửa. Xử lý cả trường hợp giờ đóng
     * cửa "qua đêm" (ví dụ {@code 18:00–02:00}) bằng cách coi khoảng đó là phần bù của
     * {@code [closesAt, opensAt)} thay vì {@code [opensAt, closesAt)} thông thường.
     */
    public boolean isOpenAt(Instant instant) {
        ZonedDateTime local = instant.atZone(ZoneId.of(timezone));
        LocalTime now = local.toLocalTime();
        if (opensAt.isBefore(closesAt)) {
            return !now.isBefore(opensAt) && now.isBefore(closesAt);
        }
        // Qua đêm: mở cửa nếu KHÔNG nằm trong khoảng đóng cửa [closesAt, opensAt).
        return now.isBefore(closesAt) || !now.isBefore(opensAt);
    }
}
