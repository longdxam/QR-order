package com.qros.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nền chung cho test ba vùng bảo mật: sinh sẵn ba cặp khoá Ed25519 riêng biệt và nạp khoá công khai
 * của chúng vào ba vùng.
 *
 * <p>Ba cặp khoá khác nhau là điều kiện để phép thử có nghĩa. Nếu ba vùng dùng chung một khoá thì
 * ca "token vùng khác" chỉ còn kiểm được {@code audience}, và sẽ không phát hiện được ngày nào đó
 * ai đó gộp ba decoder làm một.
 */
abstract class SecurityZoneTestSupport {

    static final String ISSUER = "https://qros.test";
    static final KeyPair GUEST_KEYS = JwsTestTokens.ed25519KeyPair();
    static final KeyPair STAFF_KEYS = JwsTestTokens.ed25519KeyPair();
    static final KeyPair ADMIN_KEYS = JwsTestTokens.ed25519KeyPair();
    static final String GUEST_KID = "guest-2026-09";
    static final String STAFF_KID = "staff-2026-09";
    static final String ADMIN_KID = "admin-2026-09";

    @Value("${local.server.port}")
    private int port;

    /** Actuator nằm ở context quản trị riêng, cổng khác cổng ứng dụng ({@code BL-M0-02}). */
    @Value("${local.management.port}")
    private int managementPort;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @DynamicPropertySource
    static void cauHinhKhoa(DynamicPropertyRegistry registry) {
        registry.add("qros.security.guest.issuer", () -> ISSUER);
        registry.add("qros.security.staff.issuer", () -> ISSUER);
        registry.add("qros.security.admin.issuer", () -> ISSUER);
        registry.add("qros.security.guest.keys[0].kid", () -> GUEST_KID);
        registry.add("qros.security.guest.keys[0].x", () -> JwsTestTokens.jwkX(GUEST_KEYS));
        registry.add("qros.security.staff.keys[0].kid", () -> STAFF_KID);
        registry.add("qros.security.staff.keys[0].x", () -> JwsTestTokens.jwkX(STAFF_KEYS));
        registry.add("qros.security.admin.keys[0].kid", () -> ADMIN_KID);
        registry.add("qros.security.admin.keys[0].x", () -> JwsTestTokens.jwkX(ADMIN_KEYS));
    }

    static String guestToken() {
        return guestToken(Instant.now().plusSeconds(900));
    }

    static String guestToken(Instant expiresAt) {
        return JwsTestTokens.signedEd25519(GUEST_KID,
                JwsTestTokens.claims(ISSUER, "qros-guest", "table_session", expiresAt), GUEST_KEYS);
    }

    static String staffToken() {
        return JwsTestTokens.signedEd25519(STAFF_KID,
                JwsTestTokens.claims(ISSUER, "qros-staff", "staff", Instant.now().plusSeconds(900)),
                STAFF_KEYS);
    }

    static String adminToken() {
        return JwsTestTokens.signedEd25519(ADMIN_KID,
                JwsTestTokens.claims(ISSUER, "qros-admin", "admin", Instant.now().plusSeconds(900)),
                ADMIN_KEYS);
    }

    HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        headers.forEach(request::header);
        return client.send(request.build(), BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .POST(HttpRequest.BodyPublishers.noBody());
        headers.forEach(request::header);
        return client.send(request.build(), BodyHandlers.ofString());
    }

    HttpResponse<String> managementGet(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + managementPort + path)).GET().build(),
                BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /**
     * Endpoint tối thiểu ở cả ba vùng, chỉ để quan sát request đi qua hay bị chặn.
     *
     * <p>Lớp này nằm trong class hỗ trợ chứ không phải trong class test, nên component scan tự
     * đăng ký nó — không khai báo thêm {@code @Bean}, nếu không sẽ trùng ánh xạ.
     */
    @RestController
    static class VungController {

        @GetMapping("/api/v1/guest/ping")
        Map<String, String> guestGet() {
            return Map.of("zone", "guest");
        }

        @PostMapping("/api/v1/guest/ping")
        Map<String, String> guestPost() {
            return Map.of("zone", "guest");
        }

        @RequestMapping("/api/v1/staff/ping")
        Map<String, String> staff() {
            return Map.of("zone", "staff");
        }

        @RequestMapping("/api/v1/admin/ping")
        Map<String, String> admin() {
            return Map.of("zone", "admin");
        }
    }
}
