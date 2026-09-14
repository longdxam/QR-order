package com.qros.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Sinh và băm chuỗi ngẫu nhiên đối chiếu CSDL: refresh token, mã dự phòng MFA. SHA-256 chứ không
 * phải Argon2id — đây là chuỗi entropy cao do máy sinh, không phải mật khẩu người chọn
 * ({@code FR-AUTH-01} chỉ bắt buộc Argon2id cho mật khẩu). Cùng lựa chọn với
 * {@code RequestFingerprint} của {@code shared/idempotency}.
 */
public final class OpaqueTokenHashes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RAW_TOKEN_BYTES = 32; // 256 bit

    private OpaqueTokenHashes() {
    }

    /** Chuỗi ngẫu nhiên base64url, đủ entropy làm refresh token hoặc mã dự phòng dạng dài. */
    public static String randomToken() {
        byte[] raw = new byte[RAW_TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM không có SHA-256", exception);
        }
    }
}
