package com.qros.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.qros.identity.domain.AppUserFixture;
import com.qros.identity.repository.AppUserRepository;
import com.qros.integration.QrosIntegrationTest;

/**
 * {@code TM-AUTH-03}: xoay vòng refresh token có phát hiện tái sử dụng — dùng lại một token đã
 * tiêu thu hồi toàn bộ chuỗi ({@code family_id}), không chỉ token đó.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RefreshTokenReuseTest extends QrosIntegrationTest {

    private static final Argon2PasswordEncoder PASSWORD_ENCODER =
            new Argon2PasswordEncoder(16, 32, 4, 65536, 3);
    private static final String MAT_KHAU_DUNG = "MatKhauDungCuaToi123!";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AppUserRepository appUserRepository;

    private final HttpClient client = HttpClient.newHttpClient();
    private String email;

    @BeforeEach
    void chuanBi() {
        appUserRepository.deleteAll();
        email = "refresh-" + UUID.randomUUID() + "@qros.test";
        appUserRepository.save(AppUserFixture.moi(email, PASSWORD_ENCODER.encode(MAT_KHAU_DUNG)));
    }

    @Test
    void xoayVongHopLe_capAccessTokenMoiVaRefreshTokenMoi() throws Exception {
        String refreshToken1 = refreshTokenTu(dangNhap());

        HttpResponse<String> phanHoi = refresh(refreshToken1);
        assertThat(phanHoi.statusCode()).isEqualTo(204);

        String refreshToken2 = refreshTokenTu(phanHoi);
        assertThat(refreshToken2).isNotEqualTo(refreshToken1);
    }

    @Test
    void dungLaiTokenDaTieu_traReusedVaThuHoiCaChuoi() throws Exception {
        String refreshToken1 = refreshTokenTu(dangNhap());

        HttpResponse<String> lanMot = refresh(refreshToken1);
        assertThat(lanMot.statusCode()).isEqualTo(204);
        String refreshToken2 = refreshTokenTu(lanMot);

        // Phát lại token đã tiêu (refreshToken1): đây là tín hiệu tái sử dụng.
        HttpResponse<String> phatLai = refresh(refreshToken1);
        assertThat(phatLai.statusCode()).isEqualTo(401);
        assertThat(phatLai.body()).contains("\"code\":\"REFRESH_REUSED\"");

        // Cả chuỗi đã bị thu hồi — token MỚI NHẤT (refreshToken2, chưa từng bị dùng sai) cũng
        // không còn dùng được, dù bản thân nó chưa hề bị phát lại.
        HttpResponse<String> dungTokenMoiNhat = refresh(refreshToken2);
        assertThat(dungTokenMoiNhat.statusCode()).isEqualTo(401);
        assertThat(dungTokenMoiNhat.body()).contains("\"code\":\"REFRESH_REUSED\"");
    }

    @Test
    void tokenKhongTonTai_traUnauthenticatedKhongPhaiReused() throws Exception {
        HttpResponse<String> phanHoi = refresh("token-khong-ton-tai-" + UUID.randomUUID());

        assertThat(phanHoi.statusCode()).isEqualTo(401);
        assertThat(phanHoi.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    void khongCoCookieRefresh_traUnauthenticated() throws Exception {
        HttpResponse<String> phanHoi = client.send(
                HttpRequest.newBuilder(uri("/api/v1/auth/refresh")).POST(BodyPublishers.noBody()).build(),
                BodyHandlers.ofString());

        assertThat(phanHoi.statusCode()).isEqualTo(401);
        assertThat(phanHoi.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    private HttpResponse<String> dangNhap() throws Exception {
        String body = """
                {"email":"%s","password":"%s"}""".formatted(email, MAT_KHAU_DUNG);
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> phanHoi = client.send(request, BodyHandlers.ofString());
        assertThat(phanHoi.statusCode()).isEqualTo(204);
        return phanHoi;
    }

    private HttpResponse<String> refresh(String rawRefreshToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/refresh"))
                .header("Cookie", "qros_refresh=" + rawRefreshToken)
                .POST(BodyPublishers.noBody())
                .build();
        return client.send(request, BodyHandlers.ofString());
    }

    private static String refreshTokenTu(HttpResponse<String> phanHoi) {
        List<String> setCookies = phanHoi.headers().allValues("Set-Cookie");
        return setCookies.stream()
                .filter(value -> value.startsWith("qros_refresh="))
                .findFirst()
                .map(value -> value.substring("qros_refresh=".length(), value.indexOf(';')))
                .orElseThrow(() -> new IllegalStateException(
                        "Không thấy cookie qros_refresh trong: " + setCookies));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
