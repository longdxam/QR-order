package com.qros.shared.event;

import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.qros.shared.web.TraceIdProvider;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Đường duy nhất để ghi sự kiện miền ({@code ADR-05}).
 *
 * <p>Bắt buộc phải gọi bên trong giao dịch nghiệp vụ. Nếu không có giao dịch nào đang mở thì lệnh
 * ghi này bị từ chối ngay thay vì âm thầm tự mở giao dịch riêng: một sự kiện commit độc lập với dữ
 * liệu sinh ra nó chính là ca hỏng mà outbox sinh ra để chặn — KDS nhận đơn không tồn tại, hoặc
 * đơn đã lưu mà KDS không bao giờ thấy ({@code TM-EVT-01}).
 */
public class OutboxWriter {

    private static final TypeReference<Map<String, Object>> BODY_TYPE = new TypeReference<>() {
    };

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final TraceIdProvider traceIdProvider;

    public OutboxWriter(OutboxRepository repository, ObjectMapper objectMapper,
            TraceIdProvider traceIdProvider) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.traceIdProvider = traceIdProvider;
    }

    /**
     * @return {@code seq} của sự kiện — cũng là khoá chính của dòng outbox.
     */
    public long append(DomainEvent event) {
        Objects.requireNonNull(event, "event không được null");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Sự kiện phải được ghi trong cùng giao dịch nghiệp vụ (ADR-05): " + event.type());
        }

        OutboxEntity entity = new OutboxEntity(
                event.aggregateType(),
                event.aggregateId(),
                event.storeId(),
                event.sessionId(),
                event.type(),
                serializeBody(event),
                // Không bịa traceId cho tiến trình nền: cột này nullable, và một ID bịa ra
                // sẽ dẫn người trực sự cố tới một truy vết không tồn tại.
                traceIdProvider.currentTraceIdOrNull(),
                // PostgreSQL timestamptz chỉ giữ tới micro giây. Cắt ngay ở đây để giá trị lưu,
                // giá trị phát ra Redis và giá trị bên gọi cầm trên tay là một — nếu không,
                // so sánh mốc thời gian giữa hai phía sẽ lệch đúng phần nano bị âm thầm bỏ đi.
                event.occurredAt().truncatedTo(ChronoUnit.MICROS));

        return repository.save(entity).getId();
    }

    /**
     * Thân sự kiện được lưu kèm {@code eventId} vì bảng {@code outbox_event} của {@code V1} không
     * có cột riêng cho nó, mà hợp đồng AsyncAPI lại bắt buộc trường này để client khử trùng lặp.
     * Lưu trong payload giữ cho ID không đổi qua mọi lần phát lại — đúng thứ dedupe cần.
     */
    private String serializeBody(DomainEvent event) {
        Map<String, Object> body = new LinkedHashMap<>(objectMapper.convertValue(
                Objects.requireNonNull(event.payload(), "payload không được null"), BODY_TYPE));
        body.put("eventId", event.eventId().toString());
        return objectMapper.writeValueAsString(body);
    }
}
