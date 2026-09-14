package com.qros.shared.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP theo RFC 6238 (và HOTP theo RFC 4226 bên dưới nó) — {@code FR-AUTH-02}.
 *
 * <p>Không kéo thư viện ngoài vào chỉ cho một thuật toán 30 dòng: JDK có sẵn {@code HmacSHA1},
 * phần còn lại là cắt 4 byte theo con trỏ động (dynamic truncation) của RFC 4226 mục 5.3. Cùng
 * triết lý với {@link Ed25519PublicKeys}/{@link Ed25519PrivateKeys}.
 */
public final class Totp {

    private static final int DIGITS = 6;
    private static final int STEP_SECONDS = 30;
    /** Dung sai một bước 30 giây mỗi chiều — tinh thần giống {@code NFR-SEC-08} cho JWT. */
    private static final int ALLOWED_DRIFT_STEPS = 1;
    private static final int SECRET_LENGTH_BYTES = 20; // 160 bit, khuyến nghị RFC 4226 mục 4

    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    public static byte[] generateSecret() {
        byte[] secret = new byte[SECRET_LENGTH_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }

    /** Mã hợp lệ tại đúng thời điểm {@code now} — dùng để test, hoặc xem trước lúc đăng ký MFA. */
    public static String currentCode(byte[] secret, Instant now) {
        return currentCode(secret, now, STEP_SECONDS);
    }

    /** So khớp mã 6 số người dùng nhập với mã tính tại thời điểm hiện tại, có dung sai lệch đồng hồ. */
    public static boolean verify(byte[] secret, String code, Instant now) {
        return verify(secret, code, now, STEP_SECONDS);
    }

    /**
     * Biến thể có bước thời gian tuỳ chọn — lớp 4 mục 5.3.2 (QR xoay vòng) dùng bước 60 giây,
     * khác 30 giây mặc định của MFA ({@code FR-AUTH-02}). Cùng một RFC 6238, chỉ khác tham số bước.
     */
    public static String currentCode(byte[] secret, Instant now, int stepSeconds) {
        return hotp(secret, now.getEpochSecond() / stepSeconds);
    }

    public static boolean verify(byte[] secret, String code, Instant now, int stepSeconds) {
        if (code == null || code.length() != DIGITS) {
            return false;
        }
        long currentStep = now.getEpochSecond() / stepSeconds;
        for (int drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
            if (constantTimeEquals(code, hotp(secret, currentStep + drift))) {
                return true;
            }
        }
        return false;
    }

    /** HOTP(K, C) — RFC 4226. */
    private static String hotp(byte[] secret, long counter) {
        byte[] counterBytes = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
        byte[] hash;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            hash = mac.doFinal(counterBytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không tính được HOTP", exception);
        }

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        int otp = binary % 1_000_000;
        return "%06d".formatted(otp);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}
