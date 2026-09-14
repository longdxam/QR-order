package com.qros.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import tools.jackson.databind.ObjectMapper;

/**
 * Xưởng token cho test bảo mật: ký thật bằng Ed25519 của JDK, và chế tác được cả token độc hại.
 *
 * <p>Cố tình không dùng thư viện JOSE để dựng token. Các ca cần kiểm là token **sai chuẩn** —
 * {@code alg:none}, đánh tráo sang HMAC, {@code kid} lạ — mà thư viện tử tế thì từ chối tạo ra
 * chúng. Ghép tay ba đoạn base64url cho phép dựng đúng thứ kẻ tấn công gửi.
 */
final class JwsTestTokens {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private JwsTestTokens() {
    }

    static KeyPair ed25519KeyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Tham số {@code x} của JWK OKP: 32 byte cuối của bản mã hoá X.509. */
    static String jwkX(KeyPair keyPair) {
        byte[] encoded = keyPair.getPublic().getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
        return BASE64URL.encodeToString(raw);
    }

    static Map<String, Object> claims(String issuer, String audience, String scope, Instant expiresAt) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        claims.put("sub", "test-subject");
        claims.put("jti", "test-jti-" + expiresAt.toEpochMilli());
        claims.put("scope", scope);
        // iat luôn trước exp, kể cả với token đã hết hạn: Spring từ chối dựng Jwt có exp trước iat,
        // và một token thật thì không bao giờ ngược đời như vậy.
        claims.put("iat", expiresAt.minusSeconds(900).getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        return claims;
    }

    static String signedEd25519(String kid, Map<String, Object> claims, KeyPair keyPair) {
        String signingInput = segment(Map.of("alg", "EdDSA", "typ", "JWT", "kid", kid))
                + "." + segment(claims);
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(keyPair.getPrivate());
            signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + BASE64URL.encodeToString(signer.sign());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Token {@code alg:none}: chữ ký rỗng, thân giữ nguyên. */
    static String unsigned(String kid, Map<String, Object> claims) {
        return segment(Map.of("alg", "none", "typ", "JWT", "kid", kid)) + "." + segment(claims) + ".";
    }

    /**
     * Đánh tráo thuật toán: ký HMAC-SHA256 bằng **khoá công khai** của vùng.
     *
     * <p>Khoá công khai công bố ở {@code /.well-known/jwks.json} nên kẻ tấn công có sẵn. Bộ xác minh
     * nào đọc {@code alg} từ token rồi chọn thuật toán theo đó sẽ coi đây là chữ ký hợp lệ.
     */
    static String hmacWithPublicKey(String kid, Map<String, Object> claims, KeyPair keyPair) {
        String signingInput = segment(Map.of("alg", "HS256", "typ", "JWT", "kid", kid))
                + "." + segment(claims);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyPair.getPublic().getEncoded(), "HmacSHA256"));
            byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + BASE64URL.encodeToString(signature);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String segment(Map<String, Object> content) {
        return BASE64URL.encodeToString(JSON.writeValueAsBytes(content));
    }
}
