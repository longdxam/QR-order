package com.qros.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.qros.audit.domain.AuditEvent;
import com.qros.audit.repository.AuditEventRepository;
import com.qros.identity.domain.AppUser;
import com.qros.identity.domain.AppUserFixture;
import com.qros.identity.repository.AppUserRepository;
import com.qros.identity.repository.RoleRepository;
import com.qros.identity.repository.UserRoleRepository;
import com.qros.identity.service.MfaService;
import com.qros.shared.id.UuidV7;
import com.qros.shared.security.Totp;

/**
 * {@code BL-M0-14}: cổng thoát M0 — chuỗi thật đăng nhập → MFA → quyền ghi → audit/trace chạy qua
 * HTTP thật trong một test, không chỉ xác nhận lại từng mảnh rời rạc của {@code BL-M0-08}…{@code 13}.
 *
 * <p>Chưa có endpoint nghiệp vụ ghi thật nào tồn tại (ordering/catalog thuộc M1) nên "quyền ghi" ở
 * đây là một controller tối thiểu khai báo ở {@link E2eWriteCheckSupport}, cùng kỹ thuật
 * {@code SecurityZoneTestSupport.VungController} — khác ping ở chỗ nó thật sự gọi vào
 * {@code AuditRecorder} bằng actor lấy từ {@code Jwt} của chính request, để chứng minh toàn bộ
 * đường dây (JWT → MFA gate → audit → traceId) nối thông từ trong một request thật, không phải gọi
 * thẳng service như {@code MfaEnforcementTest}/{@code AuditImmutabilityTest} làm.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginMfaWriteAuditE2ETest extends E2eWriteCheckSupport {

    private static final Argon2PasswordEncoder PASSWORD_ENCODER =
            new Argon2PasswordEncoder(16, 32, 4, 65536, 3);
    private static final String MAT_KHAU_DUNG = "MatKhauDungCuaToi123!";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private MfaService mfaService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private String email;
    private UUID userId;
    private UUID entityId;

    @BeforeEach
    void chuanBi() {
        appUserRepository.deleteAll();
        UUID storeId = UuidV7.generate();
        jdbcTemplate.update("INSERT INTO store (id, code, name) VALUES (?, ?, ?)",
                storeId, "TEST-E2E-" + storeId, "Chi nhánh kiểm thử E2E");

        email = "e2e-" + UUID.randomUUID() + "@qros.test";
        AppUser user = AppUserFixture.moi(email, PASSWORD_ENCODER.encode(MAT_KHAU_DUNG));
        appUserRepository.save(user);
        userId = user.getId();
        AppUserFixture.ganVaiTro(roleRepository, userRoleRepository, userId, storeId, "STORE_MANAGER");
        entityId = UuidV7.generate();
    }

    @Test
    void dangNhap_mfa_quyenGhi_auditVaTraceChayDungThuTu() throws Exception {
        // Bước 1 — đăng nhập thành công nhưng manager chưa bật MFA nên mfaBlocked=true.
        String tokenChuaMfa = tokenTuDangNhap(dangNhap(null));
        assertThat(payloadJson(tokenChuaMfa)).contains("\"mfaBlocked\":true");

        // Bước 2 — quyền ghi bị chặn trước khi tới controller: không có dòng audit nào được tạo.
        assertThat(ghi(tokenChuaMfa).statusCode()).isEqualTo(403);
        assertThat(auditEventRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
                LOAI_DOI_TUONG, entityId)).isEmpty();

        // Bước 3 — bật MFA (chưa có endpoint tự phục vụ, OPEN-09 — gọi thẳng service như
        // MfaEnforcementTest đã làm) rồi đăng nhập lại với TOTP đúng.
        mfaService.enable(userId);
        AppUser user = appUserRepository.findById(userId).orElseThrow();
        byte[] secret = Base64.getDecoder().decode(user.getMfaSecretRef());
        String maDung = Totp.currentCode(secret, Instant.now());

        HttpResponse<String> dangNhapMfa = dangNhap(maDung);
        assertThat(dangNhapMfa.statusCode()).isEqualTo(204);
        String tokenCoMfa = tokenTuDangNhap(dangNhapMfa);
        assertThat(payloadJson(tokenCoMfa)).contains("\"mfaBlocked\":false");

        // Bước 4 — quyền ghi giờ đi qua được tới controller, controller tự ghi audit bằng actor
        // lấy từ chính Jwt của request (không phải tham số test truyền vào).
        HttpResponse<String> ketQuaGhi = ghi(tokenCoMfa);
        assertThat(ketQuaGhi.statusCode()).isEqualTo(200);
        // NFR-OBS-01: mọi request đều có correlation ID xuyên suốt, kể cả nhánh thành công.
        assertThat(ketQuaGhi.headers().firstValue("X-Correlation-Id")).isPresent();

        // Bước 5 — audit/trace: đúng một dòng, đúng actor/vai trò, và traceId không rỗng vì lần
        // ghi này xảy ra thật trong một request HTTP đang có span mở (khác AuditImmutabilityTest,
        // chạy với webEnvironment=NONE nên traceId ở đó luôn rỗng).
        List<AuditEvent> ghiNhan = auditEventRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc(LOAI_DOI_TUONG, entityId);
        assertThat(ghiNhan).hasSize(1);
        AuditEvent event = ghiNhan.get(0);
        assertThat(event.getAction()).isEqualTo(HANH_DONG_GHI);
        assertThat(event.getActorId()).isEqualTo(userId);
        assertThat(event.getActorRole()).isEqualTo("STORE_MANAGER");
        assertThat(event.getTraceId()).isNotNull().matches("[0-9a-f]{32}");
    }

    private HttpResponse<String> dangNhap(String totp) throws Exception {
        String totpJson = totp != null ? ",\"totp\":\"%s\"".formatted(totp) : "";
        String body = """
                {"email":"%s","password":"%s"%s}""".formatted(email, MAT_KHAU_DUNG, totpJson);
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
        return client.send(request, BodyHandlers.ofString());
    }

    /** Vùng staff bắt buộc CSRF (NFR-SEC-05) độc lập với MFA — lấy token bằng một GET trước. */
    private HttpResponse<String> ghi(String token) throws Exception {
        HttpResponse<String> doTruoc = client.send(HttpRequest.newBuilder(uri(DUONG_DAN_GHI))
                        .header("Cookie", "qros_session=" + token)
                        .GET().build(),
                BodyHandlers.ofString());
        String csrf = csrfTokenTu(doTruoc);

        return client.send(HttpRequest.newBuilder(
                        uri("/api/v1/staff/e2e-write-check?entityId=" + entityId))
                        .header("Cookie", "qros_session=" + token + "; XSRF-TOKEN=" + csrf)
                        .header("X-XSRF-TOKEN", csrf)
                        .POST(BodyPublishers.noBody())
                        .build(),
                BodyHandlers.ofString());
    }

    private static String csrfTokenTu(HttpResponse<String> phanHoi) {
        String setCookie = phanHoi.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Không thấy cookie XSRF-TOKEN"));
        return setCookie.substring("XSRF-TOKEN=".length(), setCookie.indexOf(';'));
    }

    private static String tokenTuDangNhap(HttpResponse<String> phanHoi) {
        String setCookie = phanHoi.headers().firstValue("Set-Cookie").orElseThrow();
        for (String phan : setCookie.split(";")) {
            if (phan.trim().startsWith("qros_session=")) {
                return phan.trim().substring("qros_session=".length());
            }
        }
        throw new IllegalStateException("Không thấy qros_session trong Set-Cookie: " + setCookie);
    }

    private static String payloadJson(String token) {
        String[] phan = token.split("\\.");
        return new String(Base64.getUrlDecoder().decode(phan[1]));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
