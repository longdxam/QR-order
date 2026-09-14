package com.qros.audit.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qros.audit.api.AuditEntry;
import com.qros.audit.api.AuditRecorder;
import com.qros.audit.domain.AuditEvent;
import com.qros.audit.repository.AuditEventRepository;
import com.qros.shared.web.TraceIdProvider;

import tools.jackson.databind.ObjectMapper;

/**
 * Hiện thực {@link AuditRecorder} — {@code TM-REP-01}.
 *
 * <p>{@code traceId} lấy từ {@link TraceIdProvider} chứ không phải tham số của
 * {@link AuditEntry}: đây là dữ liệu ngữ cảnh của lượt gọi, không phải điều module gọi vào cần tự
 * mang theo — cùng cách {@code ProblemDetailFactory} lấy {@code traceId} cho lỗi.
 *
 * <p>Chỉ nạp khi có {@code DataSource} — cùng lý do các service khác của {@code identity}.
 */
@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class AuditService implements AuditRecorder {

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final TraceIdProvider traceIdProvider;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, ObjectMapper objectMapper,
            TraceIdProvider traceIdProvider, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.traceIdProvider = traceIdProvider;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(AuditEntry entry) {
        Instant now = Instant.now(clock);
        AuditEvent event = AuditEvent.record(
                entry.actorId(), entry.actorRole(), entry.storeId(),
                entry.action(), entry.entityType(), entry.entityId(),
                toJson(entry.before()), toJson(entry.after()), entry.reason(),
                traceIdProvider.currentTraceIdOrNull(), now);
        repository.append(event);
    }

    private String toJson(Object value) {
        return value != null ? objectMapper.writeValueAsString(value) : null;
    }
}
