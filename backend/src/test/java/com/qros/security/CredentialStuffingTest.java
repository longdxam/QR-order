package com.qros.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.qros.identity.domain.AppUser;
import com.qros.identity.domain.AppUserFixture;
import com.qros.identity.repository.AppUserRepository;
import com.qros.identity.repository.RoleRepository;
import com.qros.identity.repository.UserRoleRepository;
import com.qros.integration.QrosIntegrationTest;
import com.qros.shared.id.UuidV7;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * {@code TM-AUTH-01}: dò mật khẩu, liệt kê email và khoá luỹ tiến ({@code FR-AUTH-01},
 * {@code FR-AUTH-03}).
 *
 * <p>Chạy trên PostgreSQL thật qua HTTP thật, cùng lối với {@code JwtAlgorithmConfusionTest} —
 * lockout là hành vi của tầng lưu trữ dưới tải song song, không phải thứ mock đáng tin.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CredentialStuffingTest extends QrosIntegrationTest {

    private static final Argon2PasswordEncoder PASSWORD_ENCODER =
            new Argon2PasswordEncoder(16, 32, 4, 65536, 3);
    private static final String MAT_KHAU_DUNG = "MatKhauDungCuaToi123!";

    private static final Pattern SET_COOKIE_TOKEN =
            Pattern.compile("qros_session=([^;]+)");

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private DongHoChinhDuoc dongHo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private UUID storeId;
    private String email;

    @BeforeEach
    void chuanBi() {
        appUserRepository.deleteAll();
        storeId = UuidV7.generate();
        // Module venue (BL-M1-01) chưa có entity Store; chèn thẳng hàng tối thiểu để thoả FK của
        // user_role.store_id — chỉ test này cần một store thật, không phải hành vi cần kiểm.
        jdbcTemplate.update("INSERT INTO store (id, code, name) VALUES (?, ?, ?)",
                storeId, "TEST-" + storeId, "Chi nhánh kiểm thử");
        email = "kiemtra-" + UUID.randomUUID() + "@qros.test";
        taoNguoiDung(email, MAT_KHAU_DUNG);
    }

    @Test
    void frAuth03_saiMatKhauVaEmailKhongTonTai_traCungMaLoi() throws Exception {
        HttpResponse<String> saiMatKhau = dangNhap(email, "sai-mat-khau-000");
        HttpResponse<String> khongTonTai = dangNhap("khong-ton-tai-" + UUID.randomUUID() + "@qros.test",
                "bat-ky-mat-khau");

        assertThat(saiMatKhau.statusCode()).isEqualTo(401);
        assertThat(khongTonTai.statusCode()).isEqualTo(401);
        assertThat(saiMatKhau.body()).contains("\"code\":\"INVALID_CREDENTIALS\"");
        assertThat(khongTonTai.body()).contains("\"code\":\"INVALID_CREDENTIALS\"");
        // Không phải chỉ trùng code: phần thân người dùng nhìn thấy phải giống hệt nhau, để không
        // có tín hiệu nào phân biệt hai nguyên nhân.
        assertThat(boThongTinBienDoi(saiMatKhau.body())).isEqualTo(boThongTinBienDoi(khongTonTai.body()));
    }

    @Test
    void frAuth03_khoaSauNamLanSai() throws Exception {
        for (int lan = 1; lan <= 4; lan++) {
            HttpResponse<String> phanHoi = dangNhap(email, "sai-mat-khau-du-dai-" + lan);
            assertThat(phanHoi.statusCode()).isEqualTo(401);
        }

        HttpResponse<String> lanThuNam = dangNhap(email, "sai-mat-khau-du-dai-5");
        assertThat(lanThuNam.statusCode()).isEqualTo(423);
        assertThat(lanThuNam.body()).contains("\"code\":\"ACCOUNT_LOCKED\"");

        // Khoá chặn cả mật khẩu đúng: không được để dò đúng mật khẩu giữa lúc đang khoá.
        HttpResponse<String> dungMatKhauNhungDangKhoa = dangNhap(email, MAT_KHAU_DUNG);
        assertThat(dungMatKhauNhungDangKhoa.statusCode()).isEqualTo(423);
    }

    @Test
    void frAuth03_khoaLanHaiLuyTienGapDoi() throws Exception {
        for (int lan = 1; lan <= 5; lan++) {
            dangNhap(email, "sai-mat-khau-du-dai-" + lan);
        }
        AppUser saulanKhoaMotSauKhiChay = appUserRepository.findByEmailAndActiveTrue(email).orElseThrow();
        assertThat(saulanKhoaMotSauKhiChay.isLocked(dongHo.instant())).isTrue();

        // Tua qua khỏi lần khoá đầu (15 phút) rồi lặp lại một đợt 5 lần sai nữa.
        dongHo.tien(Duration.ofMinutes(16));
        for (int lan = 1; lan <= 5; lan++) {
            dangNhap(email, "sai-lan-hai-" + lan);
        }

        AppUser sauLanKhoaHai = appUserRepository.findByEmailAndActiveTrue(email).orElseThrow();
        Instant now = dongHo.instant();
        assertThat(sauLanKhoaHai.isLocked(now)).isTrue();
        assertThat(sauLanKhoaHai.isLocked(now.plus(Duration.ofMinutes(30)).minusSeconds(5))).isTrue();
        assertThat(sauLanKhoaHai.isLocked(now.plus(Duration.ofMinutes(30)).plusSeconds(5))).isFalse();
    }

    @Test
    void frAuth01_dangNhapDungCapCookieChayDuocOVungStaffVaMangPhamViChiNhanh() throws Exception {
        ganVaiTro(storeId);

        HttpResponse<String> phanHoi = dangNhap(email, MAT_KHAU_DUNG);
        assertThat(phanHoi.statusCode()).isEqualTo(204);

        String setCookie = phanHoi.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(setCookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/api");

        String token = tokenTuCookie(setCookie);
        assertThat(payloadJson(token)).contains("\"scope\":\"staff\"")
                .contains(storeId.toString())
                .contains("\"*\""); // hàng user_role có store_id null = toàn tổ chức

        HttpResponse<String> goiVungStaff = client.send(
                HttpRequest.newBuilder(uri("/api/v1/staff/ping"))
                        .header("Cookie", "qros_session=" + token)
                        .GET().build(),
                BodyHandlers.ofString());
        assertThat(goiVungStaff.statusCode()).isEqualTo(200);
    }

    /**
     * {@code BL-M0-14}: diễn tập runbook {@code dang-nhap-that-bai-hang-loat.md} bước 1 trên dev
     * thật đã lộ ra là root logger mặc định ({@code INFO}) không có dòng nào để tra traceId ngược ra
     * IP nguồn — nhánh cũ dùng {@code log.debug} cho mọi lỗi không phải 5xx. Test này khoá lại hành
     * vi đã sửa trong {@code GlobalExceptionHandler}: đăng nhập sai phải lên log ở mức {@code INFO}
     * kèm {@code remoteAddr}, không chỉ {@code DEBUG}.
     */
    @Test
    void frAuth01_dangNhapSai_lenLogMucInfoKemIpNguon() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(
                "com.qros.shared.error.GlobalExceptionHandler");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            HttpResponse<String> phanHoi = dangNhap(email, "sai-mat-khau-log-000");
            assertThat(phanHoi.statusCode()).isEqualTo(401);

            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getFormattedMessage())
                        .contains("INVALID_CREDENTIALS")
                        .contains("remoteAddr=");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    private HttpResponse<String> dangNhap(String email, String matKhau) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}""".formatted(email, matKhau);
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
        return client.send(request, BodyHandlers.ofString());
    }

    private void taoNguoiDung(String email, String matKhau) {
        appUserRepository.save(AppUserFixture.moi(email, PASSWORD_ENCODER.encode(matKhau)));
    }

    private void ganVaiTro(UUID storeId) {
        UUID userId = appUserRepository.findByEmailAndActiveTrue(email).orElseThrow().getId();
        AppUserFixture.ganVaiTro(roleRepository, userRoleRepository, userId, storeId);
    }

    private static String tokenTuCookie(String setCookieHeader) {
        Matcher matcher = SET_COOKIE_TOKEN.matcher(setCookieHeader);
        if (!matcher.find()) {
            throw new IllegalStateException("Set-Cookie không chứa qros_session: " + setCookieHeader);
        }
        return matcher.group(1);
    }

    private static String payloadJson(String token) {
        String[] phan = token.split("\\.");
        return new String(Base64.getUrlDecoder().decode(phan[1]), StandardCharsets.UTF_8);
    }

    /** Bỏ {@code traceId} (đổi mỗi request) để so hai thân phản hồi có "giống hệt" hay không. */
    private static String boThongTinBienDoi(String body) {
        return body.replaceAll("\"traceId\":\"[^\"]*\"", "\"traceId\":\"X\"");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** Đồng hồ chỉnh được, để tua qua cửa sổ khoá thay vì chờ thật — cùng lối với {@code IdempotencyTest}. */
    static final class DongHoChinhDuoc extends Clock {
        private volatile Instant hienTai = Instant.now();

        void tien(Duration khoang) {
            hienTai = hienTai.plus(khoang);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return hienTai;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CauHinhKiemTra {
        @Bean
        @Primary
        DongHoChinhDuoc dongHoKiemTra() {
            return new DongHoChinhDuoc();
        }
    }
}
