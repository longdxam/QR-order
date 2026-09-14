package com.qros.identity.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vai trò hạt giống của {@code V1__baseline.sql}: {@code BARISTA}, {@code CASHIER},
 * {@code STORE_MANAGER}, {@code ADMIN}. Chỉ đọc ở đây — quản trị vai trò tuỳ biến thuộc M3.
 */
@Entity
@Table(name = "role")
public class Role {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    protected Role() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
