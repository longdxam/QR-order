package com.qros.identity.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một phạm vi được gán cho người dùng: vai trò tại một chi nhánh, hoặc toàn tổ chức khi
 * {@code storeId} là {@code null} ({@code V1__baseline.sql}, bảng {@code user_role}).
 *
 * <p>Chỉ đọc ở {@code BL-M0-08} — dùng để nhúng phạm vi chi nhánh/tổ chức vào token đăng nhập.
 * Quản trị gán vai trò ({@code FR-MGT-07}) thuộc M3, chưa có ở đây.
 */
@Entity
@Table(name = "user_role")
public class UserRole {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    /** {@code null} nghĩa là áp dụng toàn tổ chức, không riêng một chi nhánh. */
    @Column(name = "store_id")
    private UUID storeId;

    protected UserRole() {
        // JPA
    }

    /** Gán một phạm vi cho người dùng. Test dùng qua {@code AppUserFixture} cùng package. */
    UserRole(UUID id, UUID userId, UUID roleId, UUID storeId) {
        this.id = id;
        this.userId = userId;
        this.roleId = roleId;
        this.storeId = storeId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public UUID getStoreId() {
        return storeId;
    }
}
