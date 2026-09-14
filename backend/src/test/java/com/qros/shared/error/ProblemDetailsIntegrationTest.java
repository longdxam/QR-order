package com.qros.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.ObjectMapper;
import com.qros.shared.web.CorrelationId;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Bất biến số 4 chạy thật trên Tomcat: mọi lỗi rời khỏi máy chủ đều là
 * {@code application/problem+json} kèm {@code code} và {@code traceId} — {@code TM-OPS-02},
 * {@code NFR-OBS-01}.
 *
 * <p>Chạy qua cổng HTTP thật chứ không phải MockMvc: chuỗi filter, lượt chuyển tiếp lỗi của
 * servlet container và thương lượng kiểu nội dung chỉ tồn tại ở đường đi thật, mà đó đúng là
 * ba chỗ hay để lọt phản hồi lệch hợp đồng.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProblemDetailsIntegrationTest {

    private static final String ID_HOP_LE = "0198f0a1-4b2c-7def-8123-456789abcdef";
    /** Trace ID W3C: 32 ký tự hex thường — định dạng OpenTelemetry sinh ra, khác UUID. */
    private static final String OTEL_TRACE_ID_PATTERN = "[0-9a-f]{32}";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void tmOps02_loiNghiepVuTraDungMaVaTraceId() throws Exception {
        HttpResponse<String> phanHoi = get("/test/loi-nghiep-vu");

        assertThat(phanHoi.statusCode()).isEqualTo(409);
        assertThat(contentType(phanHoi)).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        Map<String, Object> than = body(phanHoi);
        assertThat(than).containsEntry("code", "ITEM_SOLD_OUT")
                .containsEntry("status", 409)
                .containsEntry("type", "urn:qros:error:ITEM_SOLD_OUT")
                .containsEntry("detail", "Món trà sữa trân châu vừa hết")
                .containsEntry("instance", "/test/loi-nghiep-vu");
        // traceId (BL-M0-11) và X-Correlation-Id (BL-M0-04) là hai định danh độc lập từ đây:
        // traceId tới từ span OpenTelemetry của chính request này, không còn là correlation ID
        // do client gửi lên — X-Correlation-Id vẫn được phản chiếu nguyên vẹn như trước.
        assertThat((String) than.get("traceId")).matches(OTEL_TRACE_ID_PATTERN);
        assertThat(phanHoi.headers().firstValue(CorrelationId.HEADER)).hasValue(ID_HOP_LE);
    }

    @Test
    void tmOps02_ngoaiLeKhongLuongTruocKhongLoChiTietNoiBo() throws Exception {
        HttpResponse<String> phanHoi = get("/test/no-tung");

        assertThat(phanHoi.statusCode()).isEqualTo(500);
        assertThat(contentType(phanHoi)).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body(phanHoi)).containsEntry("code", "INTERNAL_ERROR");
        assertThat(phanHoi.body())
                .doesNotContain("chuoi-ket-noi-noi-bo")
                .doesNotContain("IllegalStateException")
                .doesNotContain("com.qros")
                .doesNotContain("at java.");
    }

    @Test
    void tmOps02_loiXacThucLuocDoTraValidationFailed() throws Exception {
        HttpResponse<String> phanHoi = post("/test/kiem-tra", "{\"ten\":\"\"}");

        assertThat(phanHoi.statusCode()).isEqualTo(400);
        assertThat(contentType(phanHoi)).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body(phanHoi)).containsEntry("code", "VALIDATION_FAILED");
        assertThat(phanHoi.body()).doesNotContain("NotBlank").doesNotContain("YeuCauKiemTra");
    }

    @Test
    void tmOps02_payloadHongTraMalformedRequest() throws Exception {
        HttpResponse<String> phanHoi = post("/test/kiem-tra", "{khong-phai-json");

        assertThat(phanHoi.statusCode()).isEqualTo(400);
        assertThat(body(phanHoi)).containsEntry("code", "MALFORMED_REQUEST");
    }

    @Test
    void tmOps02_saiPhuongThucVaDuongDanLaKhongTonTai() throws Exception {
        HttpResponse<String> saiPhuongThuc = get("/test/kiem-tra");
        assertThat(saiPhuongThuc.statusCode()).isEqualTo(405);
        assertThat(body(saiPhuongThuc)).containsEntry("code", "METHOD_NOT_ALLOWED");

        HttpResponse<String> khongCo = get("/test/khong-ton-tai");
        assertThat(khongCo.statusCode()).isEqualTo(404);
        assertThat(contentType(khongCo)).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body(khongCo)).containsEntry("code", "NOT_FOUND");
    }

    @Test
    void tmOps02_loiNemTuFilterVanLaProblemJson() throws Exception {
        // Chuỗi filter bảo mật của BL-M0-07 nằm ngoài tầm với của @RestControllerAdvice;
        // đây là nhánh duy nhất còn lại có thể trả về HTML mặc định của container.
        HttpResponse<String> phanHoi = get("/test/filter-no-tung");

        assertThat(phanHoi.statusCode()).isEqualTo(500);
        assertThat(contentType(phanHoi)).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        Map<String, Object> than = body(phanHoi);
        assertThat(than).containsEntry("code", "INTERNAL_ERROR")
                .containsEntry("instance", "/test/filter-no-tung");
        assertThat((String) than.get("traceId")).matches(OTEL_TRACE_ID_PATTERN);
        assertThat(phanHoi.body()).doesNotContain("<html>").doesNotContain("chuoi-ket-noi-noi-bo");
    }

    @Test
    void nfrObs01_khongGuiHeaderVanCoCaTraceIdVaCorrelationId() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/test/loi-nghiep-vu")).GET().build();
        HttpResponse<String> phanHoi = client.send(request, BodyHandlers.ofString());

        String traceId = (String) body(phanHoi).get("traceId");
        assertThat(traceId).matches(OTEL_TRACE_ID_PATTERN);
        // Máy chủ tự sinh correlation ID khi client không gửi — khác định dạng UUIDv7, và khác
        // giá trị traceId vì hai cơ chế độc lập nhau (BL-M0-11).
        assertThat(phanHoi.headers().firstValue(CorrelationId.HEADER))
                .hasValueSatisfying(id -> assertThat(id).isNotEqualTo(traceId));
    }

    @Test
    void nfrObs01_phanHoiThanhCongCungMangCorrelationId() throws Exception {
        HttpResponse<String> phanHoi = get("/test/binh-thuong");

        assertThat(phanHoi.statusCode()).isEqualTo(200);
        assertThat(phanHoi.headers().firstValue(CorrelationId.HEADER)).hasValue(ID_HOP_LE);
    }

    private HttpResponse<String> get(String duongDan) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(duongDan))
                .header(CorrelationId.HEADER, ID_HOP_LE)
                .GET().build(), BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String duongDan, String than) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(duongDan))
                .header(CorrelationId.HEADER, ID_HOP_LE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(BodyPublishers.ofString(than)).build(), BodyHandlers.ofString());
    }

    private URI uri(String duongDan) {
        return URI.create("http://localhost:" + port + duongDan);
    }

    private static String contentType(HttpResponse<String> phanHoi) {
        return phanHoi.headers().firstValue("Content-Type").orElse("");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(HttpResponse<String> phanHoi) {
        return objectMapper.readValue(phanHoi.body(), Map.class);
    }

    record YeuCauKiemTra(@NotBlank String ten) {
    }

    @RestController
    @RequestMapping("/test")
    static class ControllerKiemTra {

        @GetMapping("/binh-thuong")
        Map<String, String> binhThuong() {
            return Map.of("trangThai", "OK");
        }

        @GetMapping("/loi-nghiep-vu")
        void loiNghiepVu() {
            throw new QrosException(ErrorCode.ITEM_SOLD_OUT, "Món trà sữa trân châu vừa hết");
        }

        @GetMapping("/no-tung")
        void noTung() {
            throw new IllegalStateException("chuoi-ket-noi-noi-bo=jdbc://qros");
        }

        @PostMapping("/kiem-tra")
        Map<String, String> kiemTra(@Valid @RequestBody YeuCauKiemTra yeuCau) {
            return Map.of("ten", yeuCau.ten());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CauHinhKiemTra {

        /**
         * Đường dẫn cố định của test nằm ngoài ba vùng bảo mật, nên chuỗi từ chối mặc định sẽ chặn.
         * Mở riêng ở đây thay vì nới luật trong cấu hình thật cho một đường chỉ có trong test.
         */
        @Bean
        @Order(0)
        SecurityFilterChain fixtureSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.securityMatcher("/test/**")
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .build();
        }

        @Bean
        ControllerKiemTra controllerKiemTra() {
            return new ControllerKiemTra();
        }

        @Bean
        FilterRegistrationBean<Filter> filterNoTung() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(
                    (request, response, chain) -> {
                        throw new ServletException("chuoi-ket-noi-noi-bo=jdbc://qros");
                    });
            registration.addUrlPatterns("/test/filter-no-tung");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            return registration;
        }
    }
}
