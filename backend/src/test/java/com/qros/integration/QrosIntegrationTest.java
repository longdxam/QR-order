package com.qros.integration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Nền chung cho test tích hợp: PostgreSQL và Redis thật, không dùng cơ sở dữ liệu nhúng.
 *
 * <p>Container khởi động một lần cho cả lượt chạy (mẫu singleton của Testcontainers) thay vì dùng
 * {@code @Testcontainers}, vốn dựng container riêng cho từng class. Với một bộ test tích hợp đang
 * lớn dần, khác biệt đó là hàng chục giây mỗi lần chạy CI. Ryuk dọn container khi JVM kết thúc.
 *
 * <p>Các test này cố tình **không** dùng profile {@code test}: profile đó chạy không có DataSource
 * để context khởi động độc lập ({@code BL-M0-02}). Ở đây container cấp cấu hình, Flyway dựng lược
 * đồ V1, và {@code ddl-auto: validate} tự đối chiếu entity với bảng thật.
 */
public abstract class QrosIntegrationTest {

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("qros")
                    .withUsername("qros")
                    .withPassword("qros_test");

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    /**
     * Cặp khoá staff/guest tự nhất quán: khoá riêng ở đây ký ra token mà khoá công khai cùng cặp ở
     * đây xác minh được. Không có gì trong {@code application.yml} cấp cặp này ở profile mặc định
     * (không có profile nào active trong nhóm test này), nên thiếu nó thì {@code staffJwtIssuer}/
     * {@code guestJwtIssuer} ném lỗi ngay lúc dựng context — hỏng mọi test tích hợp, kể cả test
     * không đụng tới đăng nhập hay phiên bàn.
     */
    private static final KeyPair STAFF_KEYS = generateEd25519();
    private static final String STAFF_KID = "staff-integration-test";
    private static final KeyPair GUEST_KEYS = generateEd25519();
    private static final String GUEST_KID = "guest-integration-test";

    @DynamicPropertySource
    static void cauHinhHaTang(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("qros.security.staff.keys[0].kid", () -> STAFF_KID);
        registry.add("qros.security.staff.keys[0].x", () -> jwkPart(STAFF_KEYS.getPublic().getEncoded()));
        registry.add("qros.security.staff.signing-key.kid", () -> STAFF_KID);
        registry.add("qros.security.staff.signing-key.d",
                () -> jwkPart(STAFF_KEYS.getPrivate().getEncoded()));

        registry.add("qros.security.guest.keys[0].kid", () -> GUEST_KID);
        registry.add("qros.security.guest.keys[0].x", () -> jwkPart(GUEST_KEYS.getPublic().getEncoded()));
        registry.add("qros.security.guest.signing-key.kid", () -> GUEST_KID);
        registry.add("qros.security.guest.signing-key.d",
                () -> jwkPart(GUEST_KEYS.getPrivate().getEncoded()));
    }

    private static KeyPair generateEd25519() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 32 byte cuối của phần mã hoá X.509/PKCS8 — dạng thô của tham số {@code x}/{@code d} JWK. */
    private static String jwkPart(byte[] encoded) {
        byte[] raw = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
