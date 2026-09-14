package com.qros.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.qros.shared.id.UuidV7;

import tools.jackson.databind.ObjectMapper;

/**
 * Phát token của **một** vùng bảo mật — phép toán ngược của {@link JwtVerifier}, cùng một triết lý:
 * mỗi vùng một khoá riêng, và **không dùng bộ ký EdDSA của Nimbus** vì nó kéo theo Tink chỉ cho đúng
 * một thuật toán ({@code JwtVerifier}, {@code Ed25519PublicKeys}). JDK có sẵn {@link Signature}
 * {@code "Ed25519"}; phần còn lại chỉ là ghép ba đoạn base64url theo RFC 7515 — không có phép toán
 * đường cong nào tự viết ở đây.
 *
 * <p>Không dùng thư viện JOSE để dựng token vì lý do đối xứng với {@code JwsTestTokens} trong test:
 * ghép tay giữ toàn quyền kiểm soát từng trường claim, không phụ thuộc hành vi mặc định của một
 * thư viện thứ ba có thể đổi qua các phiên bản.
 */
public final class JwtIssuer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final String JCA_ALGORITHM = "Ed25519";

    private final String issuer;
    private final String audience;
    private final String kid;
    private final PrivateKey signingKey;
    private final Clock clock;

    JwtIssuer(SecurityZoneProperties.Zone zone, Clock clock) {
        SecurityZoneProperties.SigningKey signingKeyConfig = zone.signingKey();
        if (signingKeyConfig == null) {
            throw new IllegalStateException(
                    "Vùng không có signingKey — không thể phát token. Thiếu QROS_JWT_*_KID/_D?");
        }
        this.issuer = zone.issuer();
        this.audience = zone.audience();
        this.kid = signingKeyConfig.kid();
        this.signingKey = Ed25519PrivateKeys.fromJwkD(signingKeyConfig.d());
        this.clock = clock;
    }

    /**
     * @param subject     giá trị {@code sub}, thường là {@code app_user.id}.
     * @param ttl         thời hạn sống của token kể từ lúc phát.
     * @param extraClaims claim nghiệp vụ thêm vào, ví dụ {@code scope}, {@code tv}, {@code stores}.
     *                    Không được ghi đè các claim chuẩn ({@code iss/aud/sub/iat/exp/jti}).
     */
    public String issue(String subject, Duration ttl, Map<String, Object> extraClaims) {
        Instant now = Instant.now(clock);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        claims.put("sub", subject);
        // UUIDv7 làm jti có chủ ý: sắp thứ tự theo thời gian, hữu ích khi tra ngược token trong log.
        claims.put("jti", UuidV7.generate().toString());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(ttl).getEpochSecond());
        claims.putAll(extraClaims);

        String signingInput = segment(Map.of("alg", "EdDSA", "typ", "JWT", "kid", kid))
                + "." + segment(claims);
        return signingInput + "." + sign(signingInput);
    }

    private String sign(String signingInput) {
        try {
            Signature signer = Signature.getInstance(JCA_ALGORITHM);
            signer.initSign(signingKey);
            signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return BASE64URL.encodeToString(signer.sign());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không ký được token vùng: " + exception.getMessage(), exception);
        }
    }

    private static String segment(Map<String, Object> content) {
        return BASE64URL.encodeToString(JSON.writeValueAsBytes(content));
    }
}
