package com.qros.venue;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.qros.audit.domain.AuditEvent;
import com.qros.audit.repository.AuditEventRepository;
import com.qros.integration.QrosIntegrationTest;
import com.qros.shared.id.UuidV7;
import com.qros.shared.security.Totp;

/**
 * {@code BL-M1-01}: sáu bước xác minh QR (mục 5.3.2 PRD) và {@code EC-02} chạy qua HTTP thật —
 * không gọi thẳng service, để cả chuỗi Spring Security vùng khách/CSRF/JSON tham gia vào phép thử.
 *
 * <p>Tấn công vào chữ ký/thuật toán (bước 1–2, {@code TM-QR-01}) và replay TOTP/ngoài giờ
 * ({@code TM-QR-02}) nằm riêng ở {@code security/QrForgeryTest} và {@code security/QrReplayTest} —
 * ở đây tập trung vào luồng nghiệp vụ đúng/gần-đúng: bước 3–6 và {@code EC-02}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TableSessionHttpFlowTest extends QrosIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private DongHoChinhDuoc dongHo;

    private final HttpClient client = HttpClient.newHttpClient();
    private UUID storeId;
    private UUID tableId;
    private KeyPair keyPair;
    private String kid;

    @BeforeEach
    void chuanBi() {
        // Giờ mở cửa cả ngày (UTC) để các test không phụ thuộc múi giờ hệ thống chạy CI — test
        // riêng cho "ngoài giờ" tự tạo chi nhánh khác với khung giờ hẹp.
        storeId = chiNhanh("00:00:00", "23:59:59");
        tableId = ban(storeId, "A-01", false, null);
        keyPair = QrTestTokens.ed25519KeyPair();
        // kid là khoá chính toàn cục (qr_signing_key), không riêng theo chi nhánh — phải khác nhau
        // giữa các test, không thì @BeforeEach của lượt sau đụng UNIQUE VIOLATION với lượt trước.
        kid = "qr-test-key-" + UUID.randomUUID();
        khoaKy(storeId, kid, keyPair);
    }

    @Test
    void taoPhienMoi_thietBiDauTienLaChuPhien() throws Exception {
        HttpResponse<String> phanHoi = quet(UuidV7.generate());
        assertThat(phanHoi.statusCode()).isEqualTo(201);
        assertThat(phanHoi.body()).contains("\"joined\":false").contains(tableId.toString());
    }

    @Test
    void cungThietBiQuetLai_khongTaoThemThietBi() throws Exception {
        UUID deviceId = UuidV7.generate();
        quet(deviceId);
        HttpResponse<String> lanHai = quet(deviceId);

        assertThat(lanHai.statusCode()).isEqualTo(201);
        assertThat(lanHai.body()).contains("\"joined\":true");
        assertThat(demThietBi(lanHai.body())).isEqualTo(1);
    }

    @Test
    void ecO2_thietBiKhacTrongCuaSoThamGia_duocThamGiaChungPhien() throws Exception {
        quet(UuidV7.generate());
        HttpResponse<String> thietBiHai = quet(UuidV7.generate());

        assertThat(thietBiHai.statusCode()).isEqualTo(201);
        assertThat(thietBiHai.body()).contains("\"joined\":true");
        assertThat(demThietBi(thietBiHai.body())).isEqualTo(2);
    }

    @Test
    void ecO2_ngoaiCuaSoThamGia_traConflict409() throws Exception {
        quet(UuidV7.generate());
        dongHo.tien(Duration.ofMinutes(11)); // qua khỏi cửa sổ "cùng nhóm" 10 phút

        HttpResponse<String> thietBiMoi = quet(UuidV7.generate());

        assertThat(thietBiMoi.statusCode()).isEqualTo(409);
        assertThat(thietBiMoi.body()).contains("\"code\":\"TABLE_SESSION_CONFLICT\"");
    }

    @Test
    void ecO2_qua4ThietBi_chanThietBiThu5VaGhiAudit() throws Exception {
        for (int i = 0; i < 4; i++) {
            HttpResponse<String> phanHoi = quet(UuidV7.generate());
            assertThat(phanHoi.statusCode()).isEqualTo(201);
        }

        HttpResponse<String> thietBiThu5 = quet(UuidV7.generate());
        assertThat(thietBiThu5.statusCode()).isEqualTo(409);

        List<AuditEvent> canhBao = auditEventRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc("TableSession",
                        sessionIdCuaBan());
        assertThat(canhBao).anyMatch(
                event -> "venue.session.device_abuse_suspected".equals(event.getAction()));
    }

    /**
     * ⚠ {@code EC-02}: nhiều thiết bị cùng quét một bàn ĐÚNG lúc chỉ được tạo đúng một phiên —
     * khoá tư vấn theo bàn ({@code TableSessionRepository.khoaTheoBan}) phải chặn đua song song,
     * không chỉ chỉ mục riêng phần ở CSDL (thứ chỉ bắt được sau khi đã ghi xong, quá trễ để trả về
     * đúng {@code joined:true} cho các thiết bị thua cuộc). Dùng đúng 4 thiết bị để không chạm
     * ngưỡng "nghi ngờ lạm dụng" (cũng là 4) — phép thử này chỉ nhắm vào race, không phải EC-02
     * abuse cap.
     */
    @Test
    void ecO2_nhieuThietBiQuetDungLucChiTaoDungMotPhien() throws Exception {
        int soLuong = 4;
        CyclicBarrier vach = new CyclicBarrier(soLuong);
        List<Callable<HttpResponse<String>>> viec = new ArrayList<>();
        for (int i = 0; i < soLuong; i++) {
            viec.add(() -> {
                vach.await();
                return quet(UuidV7.generate());
            });
        }

        List<HttpResponse<String>> ketQua = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(soLuong)) {
            for (Future<HttpResponse<String>> future : pool.invokeAll(viec)) {
                ketQua.add(future.get());
            }
        }

        assertThat(ketQua).allMatch(phanHoi -> phanHoi.statusCode() == 201);
        long soPhienChuTao = ketQua.stream().filter(phanHoi -> phanHoi.body().contains("\"joined\":false")).count();
        assertThat(soPhienChuTao).as("chỉ đúng một thiết bị là người tạo phiên").isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM table_session WHERE table_id = ? AND status = 'OPEN'",
                Integer.class, tableId)).isEqualTo(1);
    }

    @Test
    void chiNhanhDongCua_tra403() throws Exception {
        // Khung giờ mở cửa 5 phút, đặt cách "bây giờ" (đồng hồ test) đúng 2 giờ — chắc chắn nằm
        // ngoài giờ hiện tại bất kể context đã bị tua bao nhiêu bởi các test khác chạy trước
        // (đồng hồ test là bean singleton dùng chung cả lớp, cùng lối CredentialStuffingTest).
        LocalTime moBatDau = LocalTime.ofInstant(dongHo.instant(), ZoneOffset.UTC).plusHours(2);
        UUID storeDong = chiNhanh(moBatDau.toString(), moBatDau.plusMinutes(5).toString());
        UUID banDong = ban(storeDong, "B-01", false, null);
        KeyPair khoaDong = QrTestTokens.ed25519KeyPair();
        khoaKy(storeDong, "kid-store-dong", khoaDong);

        HttpResponse<String> phanHoi = guiQr(storeDong, banDong, "kid-store-dong", khoaDong,
                UuidV7.generate(), null);

        assertThat(phanHoi.statusCode()).isEqualTo(403);
        assertThat(phanHoi.body()).contains("\"code\":\"STORE_CLOSED\"");
    }

    @Test
    void banBiVoHieuHoa_traQrInvalidSignature401() throws Exception {
        jdbcTemplate.update("UPDATE restaurant_table SET status = 'DISABLED' WHERE id = ?", tableId);

        HttpResponse<String> phanHoi = quet(UuidV7.generate());

        assertThat(phanHoi.statusCode()).isEqualTo(401);
        assertThat(phanHoi.body()).contains("\"code\":\"QR_INVALID_SIGNATURE\"");
    }

    @Test
    void maBanSai_traTableCodeInvalid401() throws Exception {
        HttpResponse<String> phanHoi = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/sessions/by-code"))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(
                                "{\"tableCode\":\"ZZZZZZ\",\"deviceId\":\"%s\"}".formatted(UuidV7.generate())))
                        .build(),
                BodyHandlers.ofString());

        assertThat(phanHoi.statusCode()).isEqualTo(401);
        assertThat(phanHoi.body()).contains("\"code\":\"TABLE_CODE_INVALID\"");
    }

    @Test
    void maBanDung_moPhienGiongDuongQr() throws Exception {
        String maBan = maBanCuaBan();

        HttpResponse<String> phanHoi = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/sessions/by-code"))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(
                                "{\"tableCode\":\"%s\",\"deviceId\":\"%s\"}".formatted(maBan, UuidV7.generate())))
                        .build(),
                BodyHandlers.ofString());

        assertThat(phanHoi.statusCode()).isEqualTo(201);
        assertThat(phanHoi.body()).contains(tableId.toString());
    }

    @Test
    void qrXoayVong_totpDungThiMoDuoc_saiThiQrOtpExpired() throws Exception {
        byte[] secret = Totp.generateSecret();
        UUID banXoayVong = ban(storeId, "C-01", true, Base64.getEncoder().encodeToString(secret));
        String maDung = Totp.currentCode(secret, dongHo.instant(), 60);

        HttpResponse<String> saiOtp = guiQr(storeId, banXoayVong, kid, keyPair, UuidV7.generate(), "000000");
        assertThat(saiOtp.statusCode()).isEqualTo(401);
        assertThat(saiOtp.body()).contains("\"code\":\"QR_OTP_EXPIRED\"");

        HttpResponse<String> dungOtp = guiQr(storeId, banXoayVong, kid, keyPair, UuidV7.generate(), maDung);
        assertThat(dungOtp.statusCode()).isEqualTo(201);
    }

    @Test
    void layPhienHienTai_dungTokenDaCap() throws Exception {
        UUID deviceId = UuidV7.generate();
        HttpResponse<String> phienMoi = quet(deviceId);
        String token = accessTokenTu(phienMoi.body());

        HttpResponse<String> hienTai = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/sessions/current"))
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Id", deviceId.toString())
                        .GET().build(),
                BodyHandlers.ofString());

        assertThat(hienTai.statusCode()).isEqualTo(200);
        assertThat(hienTai.body()).contains(sessionIdCuaBan().toString());
    }

    // ─────────────────────────────────────────────────────────────────────

    private HttpResponse<String> quet(UUID deviceId) throws Exception {
        return guiQr(storeId, tableId, kid, keyPair, deviceId, null);
    }

    private HttpResponse<String> guiQr(UUID storeId, UUID tableId, String kid, KeyPair keyPair,
            UUID deviceId, String otp) throws Exception {
        Map<String, Object> claims = otp != null
                ? QrTestTokens.claimsWithOtp(storeId, tableId, otp)
                : QrTestTokens.claims(storeId, tableId, null);
        String qrToken = QrTestTokens.signedEd25519(kid, claims, keyPair);
        String body = "{\"qrToken\":\"%s\",\"deviceId\":\"%s\"}".formatted(qrToken, deviceId);
        return client.send(HttpRequest.newBuilder(uri("/api/v1/guest/sessions"))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(body))
                        .build(),
                BodyHandlers.ofString());
    }

    private UUID chiNhanh(String opensAt, String closesAt) {
        UUID id = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO store (id, code, name, timezone, opens_at, closes_at, active)
                VALUES (?, ?, ?, 'UTC', ?::time, ?::time, true)""",
                id, "ST-" + id, "Chi nhánh kiểm thử", opensAt, closesAt);
        return id;
    }

    private UUID ban(UUID storeId, String label, boolean rotatingQr, String totpSecretBase64) {
        UUID id = UuidV7.generate();
        // randomUUID (không phải UuidV7 tuần tự theo thời gian) để mã 6 ký tự không đụng độ khi
        // nhiều bàn được tạo liền nhau trong cùng một test — UuidV7 dồn phần đầu theo mili giây
        // nên hai lời gọi liên tiếp dễ trùng 5-6 ký tự đầu.
        String maBan = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 6).toUpperCase(java.util.Locale.ROOT);
        jdbcTemplate.update("""
                INSERT INTO restaurant_table
                    (id, store_id, label, short_code, status, rotating_qr_enabled, totp_secret_ref)
                VALUES (?, ?, ?, ?, 'AVAILABLE', ?, ?)""",
                id, storeId, label, maBan, rotatingQr, totpSecretBase64);
        return id;
    }

    private void khoaKy(UUID storeId, String kid, KeyPair keyPair) {
        jdbcTemplate.update("""
                INSERT INTO qr_signing_key (kid, store_id, public_key, private_key_ref, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')""",
                kid, storeId, QrTestTokens.rawPublicKey(keyPair), "test-only");
    }

    private String maBanCuaBan() {
        return jdbcTemplate.queryForObject(
                "SELECT short_code FROM restaurant_table WHERE id = ?", String.class, tableId).trim();
    }

    private UUID sessionIdCuaBan() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM table_session WHERE table_id = ? AND status = 'OPEN'", UUID.class, tableId);
    }

    /** Đếm phần tử {@code participants} qua số lần xuất hiện {@code isSelf} — trường luôn có mặt. */
    private static int demThietBi(String responseBody) {
        return countOccurrences(responseBody, "\"isSelf\"");
    }

    private static int countOccurrences(String haystack, String needle) {
        int dem = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            dem++;
            idx += needle.length();
        }
        return dem;
    }

    private static String accessTokenTu(String responseBody) {
        String marker = "\"accessToken\":\"";
        int start = responseBody.indexOf(marker) + marker.length();
        int end = responseBody.indexOf('"', start);
        return responseBody.substring(start, end);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** Đồng hồ chỉnh được — cùng lối {@code CredentialStuffingTest.DongHoChinhDuoc}. */
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
    static class CauHinhDongHo {
        @Bean
        @Primary
        DongHoChinhDuoc dongHoKiemTra() {
            return new DongHoChinhDuoc();
        }
    }
}
