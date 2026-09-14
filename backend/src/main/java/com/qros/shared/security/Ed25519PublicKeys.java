package com.qros.shared.security;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Dựng khoá công khai Ed25519 từ tham số {@code x} của JWK loại OKP (RFC 8037).
 *
 * <p>Dự án cố tình **không** kéo Tink vào runtime chỉ để xác minh một thuật toán. JDK từ bản 15 đã
 * có Ed25519 trong nhà cung cấp mật mã chuẩn; phần còn thiếu duy nhất là chuyển 32 byte thô của JWK
 * sang {@link PublicKey}. Cách rẻ và khó sai nhất là bọc chúng trong vỏ DER {@code SubjectPublicKeyInfo}
 * cố định rồi để {@link KeyFactory} làm phần còn lại — không có phép toán đường cong nào ở đây.
 *
 * <p>Công khai (không còn giới hạn trong package) từ {@code BL-M1-01}: {@code venue} cần đúng phép
 * chuyển đổi này để xác minh chữ ký QR bằng khoá động đọc từ CSDL ({@code qr_signing_key}), khác
 * {@link JwtVerifier} đọc khoá tĩnh từ cấu hình — cùng một phép toán, không lý do gì chép lại.
 */
public final class Ed25519PublicKeys {

    /** SEQUENCE { SEQUENCE { OID 1.3.101.112 }, BIT STRING (33 byte, 0 bit thừa) } */
    private static final byte[] DER_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");
    private static final int RAW_KEY_LENGTH = 32;

    private Ed25519PublicKeys() {
    }

    public static PublicKey fromJwkX(String base64UrlX) {
        byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(base64UrlX);
        } catch (IllegalArgumentException exception) {
            // Ca hay gặp nhất là biến môi trường chưa được đặt, và chuỗi "${...}" đi thẳng tới đây.
            // Thông điệp mặc định của bộ giải base64 không nói được điều đó.
            throw new IllegalArgumentException(
                    "Khoá công khai Ed25519 không phải base64url hợp lệ (kiểm tra biến môi trường "
                            + "QROS_JWT_*_X đã được đặt chưa)", exception);
        }
        if (raw.length != RAW_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Khoá Ed25519 phải dài 32 byte, nhận được " + raw.length);
        }

        byte[] der = new byte[DER_PREFIX.length + RAW_KEY_LENGTH];
        System.arraycopy(DER_PREFIX, 0, der, 0, DER_PREFIX.length);
        System.arraycopy(raw, 0, der, DER_PREFIX.length, RAW_KEY_LENGTH);

        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalArgumentException("Không đọc được khoá công khai Ed25519", exception);
        }
    }
}
