package com.qros.venue.service;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.text.ParseException;
import java.util.Base64;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.shared.security.Ed25519PublicKeys;
import com.qros.venue.domain.QrSigningKey;
import com.qros.venue.repository.QrSigningKeyRepository;

/**
 * Xác minh chữ ký JWS của mã QR trên mặt bàn — lớp 1 mục 5.3.2, {@code TM-QR-01}.
 *
 * <p>Khác {@code shared.security.JwtVerifier} (khoá tĩnh theo cấu hình, một bộ cho cả vùng), mỗi mã
 * QR chọn khoá theo {@code kid} trong bảng {@code qr_signing_key} — khoá đổi theo từng chi nhánh và
 * xoay theo thời gian ({@code NFR-SEC-07}). Cùng nguyên tắc "rẻ trước, đắt sau, không để token tự
 * chọn thuật toán" của {@code JwtVerifier}.
 *
 * <p>Chỉ nạp khi có {@code DataSource} — cùng lý do các service khác cần CSDL thật
 * ({@code AuthenticationService}, {@code AuditService}, {@code OutboxConfiguration}): thiếu điều
 * kiện này thì profile {@code test} (không có PostgreSQL, {@code BL-M0-02}) hỏng ngay lúc autowire.
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class QrTokenVerifier {

    private static final String REQUIRED_ALGORITHM = "EdDSA";
    private static final String REQUIRED_TYPE = "QR_TABLE";
    private static final String JCA_ALGORITHM = "Ed25519";

    private final QrSigningKeyRepository qrSigningKeyRepository;

    public QrTokenVerifier(QrSigningKeyRepository qrSigningKeyRepository) {
        this.qrSigningKeyRepository = qrSigningKeyRepository;
    }

    /**
     * @param qrToken JWS đọc từ mã QR.
     * @return claim đã xác minh — chữ ký đúng, thuật toán đúng danh sách trắng, và {@code sid} khớp
     *         đúng chi nhánh sở hữu khoá đã ký. Bất kỳ sai lệch nào cũng ném cùng một lỗi
     *         {@code QR_INVALID_SIGNATURE} — không có tín hiệu nào để phân biệt "sai chữ ký" với
     *         "đúng chữ ký nhưng sai chi nhánh", tránh làm lộ cấu trúc nội bộ cho kẻ dò.
     */
    public QrClaims verify(String qrToken) {
        SignedJWT signedJwt = parse(qrToken);

        // 1. Chỉ chấp nhận đúng một thuật toán, so với hằng số — không bao giờ đọc alg từ token
        // rồi dùng chính giá trị đó để chọn cách xác minh (NFR-SEC-03).
        if (!REQUIRED_ALGORITHM.equals(signedJwt.getHeader().getAlgorithm().getName())) {
            throw reject();
        }

        // 2. Chọn khoá theo kid, xác minh chữ ký.
        String kid = signedJwt.getHeader().getKeyID();
        QrSigningKey key = kid == null ? null : qrSigningKeyRepository.findById(kid).orElse(null);
        if (key == null || !key.isAcceptedForVerification()) {
            throw reject();
        }
        verifySignature(signedJwt, key.getPublicKey());

        JWTClaimsSet claims = claimsOf(signedJwt);
        if (!REQUIRED_TYPE.equals(stringClaim(claims, "typ"))) {
            // Chặn nhầm lẫn token: một token vùng khác (đã ký hợp lệ cho mục đích khác) không được
            // đi lạc vào đây và bị đọc như một mã QR.
            throw reject();
        }

        UUID storeId = uuidClaimOrNull(claims, "sid");
        UUID tableId = uuidClaimOrNull(claims, "tid");
        UUID zoneId = uuidClaimOrNull(claims, "zid");
        if (storeId == null || tableId == null || !storeId.equals(key.getStoreId())) {
            // Sửa sid để trỏ sang chi nhánh khác thì chữ ký (ký trên toàn bộ payload) đã sai từ
            // bước 2; nhánh này chỉ còn bắt trường hợp token ký hợp lệ bởi khoá của chi nhánh A
            // nhưng claim sid lại ghi chi nhánh B — không nên xảy ra nếu ký đúng, nhưng kiểm lại
            // cho chắc thay vì tin tưởng ngầm định.
            throw reject();
        }

        return new QrClaims(storeId, tableId, zoneId, stringClaim(claims, "otp"));
    }

    private SignedJWT parse(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (ParseException | IllegalArgumentException exception) {
            throw reject();
        }
    }

    private void verifySignature(SignedJWT signedJwt, byte[] publicKeyBytes) {
        PublicKey key = Ed25519PublicKeys.fromJwkX(
                Base64.getUrlEncoder().withoutPadding().encodeToString(publicKeyBytes));
        byte[] signingInput = signedJwt.getSigningInput();
        byte[] signature = signedJwt.getSignature().decode();
        boolean hopLe;
        try {
            Signature verifier = Signature.getInstance(JCA_ALGORITHM);
            verifier.initVerify(key);
            verifier.update(signingInput);
            hopLe = verifier.verify(signature);
        } catch (GeneralSecurityException exception) {
            throw reject();
        }
        if (!hopLe) {
            throw reject();
        }
    }

    private JWTClaimsSet claimsOf(SignedJWT signedJwt) {
        try {
            return signedJwt.getJWTClaimsSet();
        } catch (ParseException exception) {
            throw reject();
        }
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException exception) {
            return null;
        }
    }

    private static UUID uuidClaimOrNull(JWTClaimsSet claims, String name) {
        String raw = stringClaim(claims, name);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static QrosException reject() {
        return new QrosException(ErrorCode.QR_INVALID_SIGNATURE);
    }

    /** @param otp claim {@code otp} nếu QR thuộc bàn bật xoay vòng (lớp 4 mục 5.3.2); {@code null} nếu không. */
    public record QrClaims(UUID storeId, UUID tableId, UUID zoneId, String otp) {
    }
}
