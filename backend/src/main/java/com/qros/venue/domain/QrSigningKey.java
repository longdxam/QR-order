package com.qros.venue.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Khoá ký QR của một chi nhánh — {@code NFR-SEC-07}: giữ song song khoá {@code ACTIVE} và
 * {@code PREVIOUS} để xoay khoá không làm chết QR đã in ra.
 *
 * <p>{@code privateKeyRef} đang giữ trực tiếp seed riêng Ed25519 (base64url, 32 byte) chứ không
 * phải một tham chiếu Vault thật — dự án chưa tích hợp Vault/KMS ở đâu cả (khoá ký JWT của vùng
 * cũng đang là biến môi trường thô, {@code BL-M0-07}), nên tạm nhất quán với hiện trạng đó thay vì
 * giả vờ có KMS. Việc phát hành/in QR thật (ai gọi tới trường này để ký) thuộc quản trị bàn, chưa
 * có endpoint — M3.
 */
@Entity
@Table(name = "qr_signing_key")
public class QrSigningKey {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PREVIOUS = "PREVIOUS";

    @Id
    @Column(name = "kid", nullable = false)
    private String kid;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "public_key", nullable = false)
    private byte[] publicKey;

    @Column(name = "status", nullable = false)
    private String status;

    protected QrSigningKey() {
        // JPA
    }

    public String getKid() {
        return kid;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    /** Chỉ khoá {@code ACTIVE} hoặc {@code PREVIOUS} được chấp nhận để xác minh chữ ký QR. */
    public boolean isAcceptedForVerification() {
        return STATUS_ACTIVE.equals(status) || STATUS_PREVIOUS.equals(status);
    }
}
