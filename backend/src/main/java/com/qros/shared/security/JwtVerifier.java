package com.qros.shared.security;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Xác minh token của **một** vùng bảo mật. Mỗi vùng có một thực thể riêng, với bộ khoá,
 * {@code issuer} và {@code audience} riêng ({@code NFR-SEC-02}…{@code 08}, {@code TM-AUTH-02}).
 *
 * <p>Thứ tự kiểm là cố ý — rẻ trước, đắt sau, và **không bao giờ để token tự chọn thuật toán**:
 *
 * <ol>
 *   <li>{@code alg} phải đúng bằng {@code EdDSA}, so với hằng số. Đây là chỗ chặn {@code alg:none}
 *       và chặn đánh tráo sang thuật toán đối xứng, nơi khoá công khai bị dùng làm khoá bí mật
 *       ({@code NFR-SEC-03});</li>
 *   <li>chọn khoá theo {@code kid} trong danh sách khoá của **vùng này**;</li>
 *   <li>xác minh chữ ký;</li>
 *   <li>{@code iss} và {@code aud} phải khớp cấu hình của vùng;</li>
 *   <li>{@code exp} và {@code nbf} với dung sai 60 giây ({@code NFR-SEC-08}).</li>
 * </ol>
 *
 * <p>Hiện thực {@link JwtDecoder} để cắm thẳng vào {@code oauth2ResourceServer}, nên phần còn lại
 * của Spring Security dùng token này y như mọi resource server khác.
 */
public final class JwtVerifier implements JwtDecoder {

    private static final String REQUIRED_ALGORITHM = "EdDSA";
    private static final String JCA_ALGORITHM = "Ed25519";

    private final String zoneName;
    private final String issuer;
    private final String audience;
    private final Map<String, PublicKey> keysByKid;
    private final Duration clockSkew;
    private final Clock clock;

    JwtVerifier(String zoneName, SecurityZoneProperties.Zone zone, Clock clock) {
        this.zoneName = zoneName;
        this.issuer = zone.issuer();
        this.audience = zone.audience();
        this.clockSkew = zone.clockSkew();
        this.clock = clock;
        this.keysByKid = new LinkedHashMap<>();
        for (SecurityZoneProperties.Key key : zone.keys()) {
            keysByKid.put(key.kid(), Ed25519PublicKeys.fromJwkX(key.x()));
        }
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        SignedJWT signedJwt = parse(token);

        requireAlgorithm(signedJwt);
        verifySignature(signedJwt);

        JWTClaimsSet claims = claimsOf(signedJwt);
        requireIssuerAndAudience(claims);
        requireWithinLifetime(claims);

        return toSpringJwt(token, signedJwt, claims);
    }

    private SignedJWT parse(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (ParseException exception) {
            throw reject("token không đọc được");
        }
    }

    private void requireAlgorithm(SignedJWT signedJwt) {
        if (!REQUIRED_ALGORITHM.equals(signedJwt.getHeader().getAlgorithm().getName())) {
            throw reject("thuật toán không nằm trong danh sách trắng");
        }
    }

    private void verifySignature(SignedJWT signedJwt) {
        PublicKey key = keysByKid.get(signedJwt.getHeader().getKeyID());
        if (key == null) {
            // Khoá của vùng khác cũng rơi vào đây: mỗi vùng chỉ biết khoá của chính nó.
            throw reject("không có khoá công khai khớp kid");
        }

        byte[] signingInput = signedJwt.getSigningInput();
        byte[] signature = signedJwt.getSignature().decode();
        boolean hopLe;
        try {
            Signature verifier = Signature.getInstance(JCA_ALGORITHM);
            verifier.initVerify(key);
            verifier.update(signingInput);
            hopLe = verifier.verify(signature);
        } catch (GeneralSecurityException exception) {
            throw reject("không xác minh được chữ ký");
        }

        if (!hopLe) {
            throw reject("chữ ký không hợp lệ");
        }
    }

    private JWTClaimsSet claimsOf(SignedJWT signedJwt) {
        try {
            return signedJwt.getJWTClaimsSet();
        } catch (ParseException exception) {
            throw reject("claim không đọc được");
        }
    }

    private void requireIssuerAndAudience(JWTClaimsSet claims) {
        if (!issuer.equals(claims.getIssuer())) {
            throw reject("issuer không khớp vùng");
        }
        List<String> audiences = claims.getAudience();
        if (audiences == null || !audiences.contains(audience)) {
            // Đây là hàng rào giữ token phiên bàn nằm yên ở vùng khách, kể cả khi khoá bị dùng chung.
            throw reject("audience không khớp vùng");
        }
    }

    private void requireWithinLifetime(JWTClaimsSet claims) {
        Instant now = Instant.now(clock);
        Instant expiresAt = toInstant(claims.getExpirationTime());
        if (expiresAt == null || !now.minus(clockSkew).isBefore(expiresAt)) {
            throw reject("token đã hết hạn");
        }
        Instant notBefore = toInstant(claims.getNotBeforeTime());
        if (notBefore != null && now.plus(clockSkew).isBefore(notBefore)) {
            throw reject("token chưa tới hiệu lực");
        }
    }

    private Jwt toSpringJwt(String token, SignedJWT signedJwt, JWTClaimsSet claims) {
        Map<String, Object> converted = MappedJwtClaimSetConverter
                .withDefaults(Map.of()).convert(claims.getClaims());
        return Jwt.withTokenValue(token)
                .headers(headers -> headers.putAll(signedJwt.getHeader().toJSONObject()))
                .claims(target -> target.putAll(converted))
                .build();
    }

    private static Instant toInstant(java.util.Date date) {
        return date != null ? date.toInstant() : null;
    }

    /**
     * Luôn là {@link BadJwtException}, không phải {@link JwtException} trần.
     *
     * <p>Spring Security đọc phân biệt này theo nghĩa hoàn toàn khác nhau: {@code BadJwtException}
     * là "token xấu" nên thành {@code 401}, còn {@code JwtException} trần là "bộ xác minh hỏng" nên
     * thành {@code 500}. Ném nhầm loại thì một token giả mạo lại được báo cáo như sự cố máy chủ,
     * và cảnh báo vận hành sẽ kêu vì lưu lượng tấn công bình thường.
     *
     * <p>Thông điệp lý do chỉ đi vào log máy chủ. Client luôn nhận cùng một phản hồi chung, không
     * có chi tiết nào để dò xem token hỏng ở bước nào.
     */
    private BadJwtException reject(String lyDo) {
        return new BadJwtException("Token vùng %s bị từ chối: %s".formatted(zoneName, lyDo));
    }
}
