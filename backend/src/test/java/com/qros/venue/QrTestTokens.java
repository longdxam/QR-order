package com.qros.venue;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

/**
 * Xưởng token QR cho test — cùng kỹ thuật {@code security.JwsTestTokens} (ký tay bằng Ed25519 của
 * JDK) nhưng riêng cho claim shape của mã QR ({@code sid/tid/zid/typ}), tách khỏi package
 * {@code com.qros.security} vì đó là package-private ở đó.
 */
final class QrTestTokens {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private QrTestTokens() {
    }

    static KeyPair ed25519KeyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 32 byte cuối của X.509 — đúng dạng {@code public_key bytea} lưu ở {@code qr_signing_key}. */
    static byte[] rawPublicKey(KeyPair keyPair) {
        byte[] encoded = keyPair.getPublic().getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
        return raw;
    }

    static Map<String, Object> claims(UUID storeId, UUID tableId, UUID zoneId) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "qros-venue-test");
        claims.put("typ", "QR_TABLE");
        claims.put("sid", storeId.toString());
        claims.put("tid", tableId.toString());
        if (zoneId != null) {
            claims.put("zid", zoneId.toString());
        }
        claims.put("iat", System.currentTimeMillis() / 1000);
        claims.put("v", 1);
        return claims;
    }

    static Map<String, Object> claimsWithOtp(UUID storeId, UUID tableId, String otp) {
        Map<String, Object> claims = claims(storeId, tableId, null);
        claims.put("otp", otp);
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

    /** Token {@code alg:none}: chữ ký rỗng. */
    static String unsigned(String kid, Map<String, Object> claims) {
        return segment(Map.of("alg", "none", "typ", "JWT", "kid", kid)) + "." + segment(claims) + ".";
    }

    private static String segment(Map<String, Object> content) {
        return BASE64URL.encodeToString(JSON.writeValueAsBytes(content));
    }
}
