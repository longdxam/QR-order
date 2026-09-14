package com.qros.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.qros.identity.domain.AppUser;
import com.qros.identity.domain.AppUserFixture;
import com.qros.identity.repository.AppUserRepository;
import com.qros.integration.QrosIntegrationTest;

/**
 * {@code FR-AUTH-05}: "Đổi mật khẩu, đổi vai trò, hoặc quản trị viên thu hồi quyền sẽ vô hiệu hoá
 * toàn bộ token đang hoạt động của người dùng đó" — chưa có endpoint đổi mật khẩu/vai trò thật
 * (M3), nên test này gọi thẳng {@code AppUser.bumpTokenVersion()}, đúng điều một endpoint như vậy
 * sẽ làm.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenVersionEnforcementTest extends QrosIntegrationTest {

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
        email = "tv-" + UUID.randomUUID() + "@qros.test";
        appUserRepository.save(AppUserFixture.moi(email, PASSWORD_ENCODER.encode(MAT_KHAU_DUNG)));
    }

    @Test
    void tangTokenVersion_vHieuHoaTokenCu_tokenMoiVanDungDuoc() throws Exception {
        String tokenCu = dangNhapLayToken();
        assertThat(goiVungStaff(tokenCu).statusCode()).isEqualTo(200);

        AppUser user = appUserRepository.findByEmailAndActiveTrue(email).orElseThrow();
        user.bumpTokenVersion();
        appUserRepository.save(user);

        assertThat(goiVungStaff(tokenCu).statusCode())
                .as("token cũ mang token_version cũ phải bị từ chối")
                .isEqualTo(401);

        String tokenMoi = dangNhapLayToken();
        assertThat(goiVungStaff(tokenMoi).statusCode())
                .as("đăng nhập lại phát token mang token_version mới, phải dùng được")
                .isEqualTo(200);
    }

    @Test
    void chuaTangTokenVersion_tokenVanDungBinhThuong() throws Exception {
        String token = dangNhapLayToken();

        assertThat(goiVungStaff(token).statusCode()).isEqualTo(200);
        assertThat(goiVungStaff(token).statusCode()).isEqualTo(200);
    }

    private String dangNhapLayToken() throws Exception {
        String body = """
                {"email":"%s","password":"%s"}""".formatted(email, MAT_KHAU_DUNG);
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> phanHoi = client.send(request, BodyHandlers.ofString());
        assertThat(phanHoi.statusCode()).isEqualTo(204);

        String setCookie = phanHoi.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring("qros_session=".length(), setCookie.indexOf(';'));
    }

    private HttpResponse<String> goiVungStaff(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/staff/ping"))
                .header("Cookie", "qros_session=" + token)
                .GET().build();
        return client.send(request, BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
