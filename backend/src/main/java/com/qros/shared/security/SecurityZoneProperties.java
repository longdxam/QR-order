package com.qros.shared.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình xác minh token cho ba vùng, mỗi vùng một bộ hoàn toàn riêng.
 *
 * <p>Tách rời là điểm chính của {@code NFR-SEC-02}…{@code 08} và {@code TM-AUTH-02}: khoá khác,
 * {@code issuer} khác, {@code audience} khác. Token phiên bàn có chữ ký hợp lệ vẫn không dùng được
 * ở vùng nhân viên, vì nó được kiểm bằng bộ khoá khác và đòi {@code audience} khác.
 *
 * @param guest vùng {@code /api/v1/guest/**}
 * @param staff vùng {@code /api/v1/staff/**}
 * @param admin vùng {@code /api/v1/admin/**}
 */
@ConfigurationProperties("qros.security")
public record SecurityZoneProperties(Zone guest, Zone staff, Zone admin) {

    /**
     * @param issuer     giá trị {@code iss} bắt buộc.
     * @param audience   giá trị {@code aud} bắt buộc.
     * @param scope      quyền tối thiểu để đi tiếp trong vùng.
     * @param keys       khoá công khai đang chấp nhận. Giữ song song khoá hiện tại và khoá liền
     *                   trước để xoay khoá 90 ngày không làm rớt phiên đang chạy ({@code NFR-SEC-07}).
     * @param clockSkew  dung sai lệch đồng hồ, mặc định 60 giây theo {@code NFR-SEC-08}.
     * @param signingKey khoá riêng để **phát** token của vùng này; {@code null} ở vùng chỉ xác minh.
     *                   Chỉ vùng staff có giá trị này từ {@code BL-M0-08}; guest ({@code BL-M1-01})
     *                   và admin (chưa có thiết kế phát token, xem {@code AdminSecurityConfig}) sẽ
     *                   thêm sau. {@code kid} ở đây phải trùng một phần tử trong {@link #keys()} —
     *                   đó chính là nửa công khai của cùng một cặp khoá.
     */
    public record Zone(
            String issuer,
            String audience,
            String scope,
            @DefaultValue List<Key> keys,
            @DefaultValue("60s") Duration clockSkew,
            SigningKey signingKey) {
    }

    /**
     * @param kid định danh khoá, khớp header {@code kid} của token.
     * @param x   tham số {@code x} của JWK OKP: khoá công khai Ed25519, base64url, 32 byte.
     */
    public record Key(String kid, String x) {
    }

    /**
     * @param kid định danh khoá, ghi vào header {@code kid} của token phát ra.
     * @param d   tham số {@code d} của JWK OKP: khoá riêng Ed25519, base64url, 32 byte hạt giống.
     *            Không bao giờ đặt trực tiếp trong file cấu hình đã commit — chỉ qua biến môi
     *            trường ({@code QROS_JWT_STAFF_D}) hoặc giá trị chỉ dùng dev.
     */
    public record SigningKey(String kid, String d) {
    }
}
