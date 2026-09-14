package com.qros.shared.security;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Dựng khoá riêng Ed25519 từ tham số {@code d} của JWK loại OKP (RFC 8037), để {@link JwtIssuer} ký
 * token phát ra.
 *
 * <p>Cùng lý do và cùng cách làm với {@link Ed25519PublicKeys}: không kéo Tink vào runtime, chỉ bọc
 * 32 byte hạt giống (seed) thô trong vỏ DER {@code PrivateKeyInfo} tối giản của RFC 8410 rồi để
 * {@link KeyFactory} dựng khoá — không có phép toán đường cong nào ở đây.
 */
final class Ed25519PrivateKeys {

    /** SEQUENCE { INTEGER 0, SEQUENCE { OID 1.3.101.112 }, OCTET STRING (OCTET STRING 32 byte) } */
    private static final byte[] DER_PREFIX =
            HexFormat.of().parseHex("302e020100300506032b657004220420");
    private static final int RAW_KEY_LENGTH = 32;

    private Ed25519PrivateKeys() {
    }

    static PrivateKey fromJwkD(String base64UrlD) {
        byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(base64UrlD);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Khoá riêng Ed25519 không phải base64url hợp lệ (kiểm tra biến môi trường "
                            + "QROS_JWT_*_D đã được đặt chưa)", exception);
        }
        if (raw.length != RAW_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Khoá Ed25519 phải dài 32 byte, nhận được " + raw.length);
        }

        byte[] der = new byte[DER_PREFIX.length + RAW_KEY_LENGTH];
        System.arraycopy(DER_PREFIX, 0, der, 0, DER_PREFIX.length);
        System.arraycopy(raw, 0, der, DER_PREFIX.length, RAW_KEY_LENGTH);

        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalArgumentException("Không đọc được khoá riêng Ed25519", exception);
        }
    }
}
