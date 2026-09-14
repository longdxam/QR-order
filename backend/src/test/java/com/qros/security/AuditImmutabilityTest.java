package com.qros.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.qros.audit.api.AuditEntry;
import com.qros.audit.api.AuditRecorder;
import com.qros.audit.domain.AuditEvent;
import com.qros.audit.repository.AuditEventRepository;
import com.qros.identity.domain.AppUser;
import com.qros.identity.domain.AppUserFixture;
import com.qros.identity.repository.AppUserRepository;
import com.qros.integration.QrosIntegrationTest;
import com.qros.shared.id.UuidV7;

/**
 * {@code TM-REP-01}: nhân viên không phủ nhận được huỷ/hoàn tiền/sửa giá, và dấu vết không sửa/xoá
 * được — "Audit chỉ ghi thêm".
 *
 * <p>Phần "app không có đường update/delete" được chứng minh ở cấp kiểu, không phải ở test này:
 * {@link AuditEventRepository} không có phương thức {@code save}/{@code delete} nào để gọi (xem
 * {@code architecture/ModuleBoundaryTest#audit_chi_ghi_them}). Test ở đây chứng minh phần hành vi
 * — ghi đúng dữ liệu, và hai lần ghi cho cùng một đối tượng tạo ra hai dòng độc lập chứ không phải
 * một dòng bị ghi đè.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuditImmutabilityTest extends QrosIntegrationTest {

    @Autowired
    private AuditRecorder auditRecorder;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    private UUID actorId;
    private UUID storeId;
    private UUID entityId;

    @BeforeEach
    void chuanBi() {
        // audit_event.actor_id có FK tới app_user(id) — cần một hàng thật, không chỉ một UUID
        // ngẫu nhiên hợp lệ về mặt cú pháp.
        AppUser actor = AppUserFixture.moi("audit-" + UUID.randomUUID() + "@qros.test", null);
        appUserRepository.save(actor);
        actorId = actor.getId();
        storeId = UuidV7.generate();
        entityId = UuidV7.generate();
    }

    @Test
    void ghiDuThongTin_actorScopeTruocSauLyDoTraceId() {
        auditRecorder.record(new AuditEntry(
                "payment.refund", "Order", entityId, actorId, "STORE_MANAGER", storeId,
                Map.of("status", "SETTLED", "amount", 50000),
                Map.of("status", "REFUNDED", "amount", 50000),
                "Khách huỷ đơn sau khi đã thanh toán"));

        List<AuditEvent> ghiNhan = auditEventRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Order", entityId);

        assertThat(ghiNhan).hasSize(1);
        AuditEvent event = ghiNhan.get(0);
        assertThat(event.getAction()).isEqualTo("payment.refund");
        assertThat(event.getActorId()).isEqualTo(actorId);
        assertThat(event.getActorRole()).isEqualTo("STORE_MANAGER");
        assertThat(event.getStoreId()).isEqualTo(storeId);
        assertThat(event.getReason()).isEqualTo("Khách huỷ đơn sau khi đã thanh toán");
        assertThat(event.getBeforeValue()).contains("\"status\"").contains("SETTLED");
        assertThat(event.getAfterValue()).contains("\"status\"").contains("REFUNDED");
        assertThat(event.getOccurredAt()).isNotNull();
        // Ngoài phạm vi một request HTTP thật (test này webEnvironment=NONE) nên không có span nào
        // đang mở — OtelTraceIdProvider trả traceId rỗng; hành vi đó có test riêng ở BL-M0-11,
        // ở đây chỉ cần không ném lỗi.
        assertThat(event.getId()).isNotNull();
    }

    @Test
    void ghiHaiLan_taoHaiDongDocLap_khongGhiDe() {
        Stream.of("PENDING", "REFUNDED").forEach(trangThai ->
                auditRecorder.record(new AuditEntry(
                        "payment.refund.status", "Order", entityId, actorId, "STORE_MANAGER", storeId,
                        null, Map.of("status", trangThai), "cập nhật trạng thái hoàn tiền")));

        List<AuditEvent> ghiNhan = auditEventRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Order", entityId);

        assertThat(ghiNhan).hasSize(2);
        assertThat(ghiNhan).extracting(AuditEvent::getId).doesNotHaveDuplicates();
        assertThat(ghiNhan).extracting(AuditEvent::getAfterValue)
                .anyMatch(json -> json.contains("PENDING"))
                .anyMatch(json -> json.contains("REFUNDED"));
    }

    @Test
    void hanhDongHeThong_khongCoActor_vanGhiDuoc() {
        auditRecorder.record(new AuditEntry(
                "shift.auto-close", "WorkShift", entityId, null, null, storeId,
                null, null, null));

        List<AuditEvent> ghiNhan = auditEventRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc("WorkShift", entityId);

        assertThat(ghiNhan).hasSize(1);
        assertThat(ghiNhan.get(0).getActorId()).isNull();
    }

    @Test
    void auditEventRepository_khongCoPhuongThucSuaHayXoa() {
        List<String> tenPhuongThucCam = List.of("save", "delete", "deleteById", "deleteAll", "update");

        List<String> phuongThucCoTrongInterface = Stream.of(AuditEventRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(phuongThucCoTrongInterface).noneMatch(tenPhuongThucCam::contains);
    }

    @Test
    void tracVetTheoNguoiThucHien() {
        auditRecorder.record(new AuditEntry(
                "menu.price.update", "MenuItem", UuidV7.generate(), actorId, "ADMIN", null,
                Map.of("price", 30000), Map.of("price", 35000), null));

        List<AuditEvent> cuaActor = auditEventRepository.findByActorIdOrderByOccurredAtDesc(actorId);

        assertThat(cuaActor).isNotEmpty();
        assertThat(cuaActor).allMatch(event -> event.getActorId().equals(actorId));
    }
}
