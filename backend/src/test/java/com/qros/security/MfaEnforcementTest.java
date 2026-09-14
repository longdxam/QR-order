package com.qros.security;

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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.qros.identity.domain.AppUser;
import com.qros.identity.domain.AppUserFixture;
import com.qros.identity.repository.AppUserRepository;
import com.qros.identity.repository.RoleRepository;
import com.qros.identity.repository.UserRoleRepository;
import com.qros.identity.service.MfaService;
import com.qros.integration.QrosIntegrationTest;
import com.qros.shared.id.UuidV7;
import com.qros.shared.security.Totp;

/**
 * {@code FR-AUTH-02}: {@code STORE_MANAGER}/{@code ADMIN} chưa bật MFA thì chưa có quyền ghi, và
 * mã dự phòng chỉ dùng được một lần.
 *
 * <p>Chưa có endpoint đăng ký MFA tự phục vụ ({@code OPEN-09}) nên test này kích hoạt MFA thẳng
 * qua {@link MfaService#enable} — đúng cách một endpoint đăng ký thật sẽ gọi vào, chỉ thiếu bước
 * xác nhận TOTP trước khi bật.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MfaEnforcementTest extends QrosIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private String email;
    private UUID userId;

    @BeforeEach
    void chuanBi() {
        appUserRepository.deleteAll();
        UUID storeId = UuidV7.generate();
        jdbcTemplate.update("INSERT INTO store (id, code, name) VALUES (?, ?, ?)",
                storeId, "TEST-" + storeId, "Chi nhánh kiểm thử");

        email = "mfa-" + UUID.randomUUID() + "@qros.test";
        AppUser user = AppUserFixture.moi(email, PASSWORD_ENCODER.encode(MAT_KHAU_DUNG));
        appUserRepository.save(user);
        userId = user.getId();
        AppUserFixture.ganVaiTro(roleRepository, userRoleRepository, userId, storeId, "STORE_MANAGER");
    }

    @Test
    void managerChuaBatMfa_khongCoQuyenGhi_nhungVanDocDuoc() throws Exception {
        String token = tokenTuDangNhap(dangNhap(null));

        assertThat(post("/api/v1/staff/ping", token).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/staff/ping", token).statusCode()).isEqualTo(200);
    }

    @Test
    void baristaKhongCanMfa_khongBiChan() throws Exception {
        // Barista thay vì manager: xoá vai trò manager, gán barista.
        appUserRepository.deleteAll();
        UUID storeId = UuidV7.generate();
        jdbcTemplate.update("INSERT INTO store (id, code, name) VALUES (?, ?, ?)",
                storeId, "TEST-B-" + storeId, "Chi nhánh kiểm thử 2");
        email = "barista-" + UUID.randomUUID() + "@qros.test";
        AppUser user = AppUserFixture.moi(email, PASSWORD_ENCODER.encode(MAT_KHAU_DUNG));
        appUserRepository.save(user);
        AppUserFixture.ganVaiTro(roleRepository, userRoleRepository, user.getId(), storeId, "BARISTA");

        String token = tokenTuDangNhap(dangNhap(null));
        assertThat(post("/api/v1/staff/ping", token).statusCode()).isEqualTo(200);
    }

    @Test
    void batMfa_dangNhapCanTotpDung_saiTotpTinhLaMotLanSai_dungThiHetBiChan() throws Exception {
        mfaService.enable(userId);
        AppUser user = appUserRepository.findById(userId).orElseThrow();
        byte[] secret = Base64.getDecoder().decode(user.getMfaSecretRef());

        // Sai TOTP nhiều lần liên tiếp phải tính vào khoá luỹ tiến giống sai mật khẩu (FR-AUTH-03),
        // nếu không TOTP sẽ là kênh dò không giới hạn số lần thử.
        for (int lan = 1; lan <= 4; lan++) {
            assertThat(dangNhap("000000").statusCode()).isEqualTo(401);
        }
        assertThat(dangNhap("000000").statusCode()).isEqualTo(423);

        // Tài khoản giờ đang khoá; xác nhận không còn đăng nhập được kể cả với TOTP đúng, rồi kết
        // thúc test — không tua đồng hồ thật ở đây, khoá luỹ tiến đã có bộ test riêng.
        String maDung = Totp.currentCode(secret, Instant.now());
        assertThat(dangNhap(maDung).statusCode()).isEqualTo(423);
    }

    @Test
    void totpDungThi_dangNhapThanhCongVaKhongBiChanGhi() throws Exception {
        mfaService.enable(userId);
        AppUser user = appUserRepository.findById(userId).orElseThrow();
        byte[] secret = Base64.getDecoder().decode(user.getMfaSecretRef());
        String maDung = Totp.currentCode(secret, Instant.now());

        HttpResponse<String> phanHoi = dangNhap(maDung);
        assertThat(phanHoi.statusCode()).isEqualTo(204);

        String token = tokenTuDangNhap(phanHoi);
        assertThat(payloadJson(token)).contains("\"mfaBlocked\":false");
        assertThat(post("/api/v1/staff/ping", token).statusCode()).isEqualTo(200);
    }

    @Test
    void backupCode_dungMotLanRoiKhongDungLaiDuoc() {
        List<String> maDuPhong = mfaService.enable(userId);
        assertThat(maDuPhong).hasSize(10);
        String mot = maDuPhong.get(0);

        assertThat(mfaService.consumeBackupCode(userId, mot)).isTrue();
        assertThat(mfaService.consumeBackupCode(userId, mot)).isFalse();
        // Mã khác vẫn còn dùng được — tiêu một mã không ảnh hưởng các mã còn lại.
        assertThat(mfaService.consumeBackupCode(userId, maDuPhong.get(1))).isTrue();
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

    /**
     * Vùng staff bắt buộc CSRF ({@code NFR-SEC-05}) độc lập với MFA — phải mang token CSRF hợp lệ
     * thì 403 nhận được mới chắc chắn đến từ {@code MfaEnforcementFilter}, không phải từ lớp CSRF.
     * Lấy token bằng một lượt GET trước, cùng lối {@code SecurityChainSeparationTest.csrfTokenTu}.
     */
    private HttpResponse<String> post(String path, String token) throws Exception {
        HttpResponse<String> doTruoc = get(path, token);
        String csrf = csrfTokenTu(doTruoc);

        return client.send(HttpRequest.newBuilder(uri(path))
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

    private HttpResponse<String> get(String path, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path))
                        .header("Cookie", "qros_session=" + token)
                        .GET().build(),
                BodyHandlers.ofString());
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
