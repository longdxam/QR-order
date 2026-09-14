package com.qros.e2e;

import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.qros.audit.api.AuditEntry;
import com.qros.audit.api.AuditRecorder;
import com.qros.identity.repository.UserRoleRepository;
import com.qros.integration.QrosIntegrationTest;

/**
 * Nền chung cho {@link LoginMfaWriteAuditE2ETest}: khai báo controller tối thiểu ở đây, không phải
 * trong lớp {@code @SpringBootTest}, cùng lý do đã ghi ở {@code SecurityZoneTestSupport} —
 * {@code SpringBootTestContextBootstrapper} loại lớp lồng bên trong chính lớp test khỏi component
 * scan, nên đăng ký thất bại âm thầm (route trả {@code 404} thay vì lỗi biên dịch/khởi động) nếu
 * controller nằm ngay trong class test.
 */
abstract class E2eWriteCheckSupport extends QrosIntegrationTest {

    static final String HANH_DONG_GHI = "e2e.write-check";
    static final String LOAI_DOI_TUONG = "E2eWriteCheck";
    static final String DUONG_DAN_GHI = "/api/v1/staff/e2e-write-check";

    /**
     * Đại diện cho "một endpoint ghi thật" chưa tồn tại ở M0 (ordering/catalog thuộc M1) — khác
     * {@code VungController.staff()} ở chỗ nó thật sự gọi vào {@link AuditRecorder} bằng actor lấy
     * từ {@link Jwt} của chính request, để chứng minh đường dây JWT → MFA gate → audit → traceId
     * nối thông từ trong một request thật.
     *
     * <p>{@code @ConditionalOnProperty} cùng lý do {@code AuthenticationService}/{@code AuditService}:
     * {@code AuditRecorder}/{@code UserRoleRepository} chỉ tồn tại khi có {@code DataSource}. Thiếu
     * điều kiện này thì mọi test dùng profile {@code test} (không có PostgreSQL, {@code BL-M0-02})
     * cũng nạp lớp này qua component scan trên {@code com.qros} và autowire thất bại ngay lúc dựng
     * context — kể cả những test không đụng gì tới {@code e2e-write-check}.
     */
    @RestController
    @ConditionalOnProperty(name = "spring.datasource.url")
    static class GhiKiemThuController {

        private final AuditRecorder auditRecorder;
        private final UserRoleRepository userRoleRepository;

        GhiKiemThuController(AuditRecorder auditRecorder, UserRoleRepository userRoleRepository) {
            this.auditRecorder = auditRecorder;
            this.userRoleRepository = userRoleRepository;
        }

        @GetMapping(DUONG_DAN_GHI)
        Map<String, Boolean> doc() {
            return Map.of("ok", true);
        }

        @PostMapping(DUONG_DAN_GHI)
        Map<String, Boolean> ghi(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID entityId) {
            UUID actorId = UUID.fromString(jwt.getSubject());
            String vaiTro = userRoleRepository.findRoleCodesByUserId(actorId).stream()
                    .findFirst()
                    .orElse(null);
            auditRecorder.record(new AuditEntry(
                    HANH_DONG_GHI, LOAI_DOI_TUONG, entityId, actorId, vaiTro, null,
                    null, Map.of("ok", true), null));
            return Map.of("ok", true);
        }
    }
}
