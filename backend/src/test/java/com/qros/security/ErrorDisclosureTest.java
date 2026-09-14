package com.qros.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.qros.integration.QrosIntegrationTest;

/**
 * {@code TM-OPS-02}: Actuator, stack trace hoặc cấu hình nội bộ không được lộ qua API.
 *
 * <p>Nhánh "không trả class/query/stack trace" đã có {@code shared/error/ProblemDetailsIntegrationTest}
 * từ {@code BL-M0-04}. File này khép nốt hai nhánh còn lại của biện pháp bắt buộc: "Actuator ở
 * cổng nội bộ" và "security headers" — cả hai đều chưa có test riêng trước {@code BL-M0-13}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorDisclosureTest extends QrosIntegrationTest {

    @Value("${local.server.port}")
    private int appPort;

    @Value("${local.management.port}")
    private int managementPort;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void actuatorKhongLoOCongUngDung() throws Exception {
        HttpResponse<String> phanHoi = get(appPort, "/actuator/health");

        assertThat(phanHoi.statusCode()).isEqualTo(404);
    }

    @Test
    void actuatorOCongNoiBoChiCoHealthVaInfo() throws Exception {
        assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/info").statusCode()).isEqualTo(200);
    }

    @Test
    void actuatorOCongNoiBoKhongLoEndpointNhayCam() throws Exception {
        // Không khai báo trong management.endpoints.web.exposure.include, nên DefaultSecurityConfig
        // của BL-M0-07 chặn ở anyRequest().denyAll() trước khi Actuator kịp định tuyến — 403, không
        // phải 404. Cả hai đều không lộ gì; 403 là cái thật sự xảy ra, không phải một quyết định.
        for (String duongDan : List.of("/actuator/env", "/actuator/beans", "/actuator/mappings",
                "/actuator/configprops", "/actuator/heapdump", "/actuator/threaddump",
                "/actuator/shutdown")) {
            HttpResponse<String> phanHoi = get(managementPort, duongDan);
            assertThat(phanHoi.statusCode())
                    .as("Actuator endpoint %s không được lộ", duongDan)
                    .isEqualTo(403);
            assertThat(phanHoi.body()).doesNotContain("qros_dev").doesNotContain("password")
                    .doesNotContain("com.zaxxer.hikari");
        }
    }

    @Test
    void moiPhanHoiDeuCoSecurityHeaders() throws Exception {
        // Cả bốn chuỗi filter (guest/staff/admin/default) đều phải có — thử một đường thuộc
        // chuỗi từ chối mặc định, không cần xác thực hợp lệ để nhìn thấy header.
        HttpResponse<String> phanHoi = get(appPort, "/api/v1/duong-dan-khong-ton-tai");

        assertThat(phanHoi.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(phanHoi.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(phanHoi.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
    }

    @Test
    void phanHoiThanhCongCungCoSecurityHeaders() throws Exception {
        // /actuator/health ở cổng nội bộ luôn permitAll (BL-M0-02) — đường 200 thật, không phải
        // đường lỗi, để xác nhận header không chỉ xuất hiện trên nhánh từ chối.
        HttpResponse<String> phanHoi = get(managementPort, "/actuator/health");

        assertThat(phanHoi.statusCode()).isEqualTo(200);
        assertThat(phanHoi.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(phanHoi.headers().firstValue("X-Frame-Options")).hasValue("DENY");
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET().build();
        return client.send(request, BodyHandlers.ofString());
    }
}
