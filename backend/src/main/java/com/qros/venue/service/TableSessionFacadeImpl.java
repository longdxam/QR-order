package com.qros.venue.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.shared.event.DomainEvent;
import com.qros.shared.event.OutboxWriter;
import com.qros.venue.api.TableSessionFacade;
import com.qros.venue.api.TableSessionView;
import com.qros.venue.domain.RestaurantTable;
import com.qros.venue.domain.StaffCall;
import com.qros.venue.domain.TableSessionEntity;
import com.qros.venue.repository.RestaurantTableRepository;
import com.qros.venue.repository.StaffCallRepository;
import com.qros.venue.repository.TableSessionRepository;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class TableSessionFacadeImpl implements TableSessionFacade {

    private static final Duration STAFF_CALL_COOLDOWN = Duration.ofSeconds(90);

    private final TableSessionRepository tableSessionRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final StaffCallRepository staffCallRepository;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    public TableSessionFacadeImpl(TableSessionRepository tableSessionRepository,
            RestaurantTableRepository restaurantTableRepository, StaffCallRepository staffCallRepository,
            OutboxWriter outboxWriter, Clock clock) {
        this.tableSessionRepository = tableSessionRepository;
        this.restaurantTableRepository = restaurantTableRepository;
        this.staffCallRepository = staffCallRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TableSessionView> find(UUID sessionId) {
        return tableSessionRepository.findById(sessionId).flatMap(session ->
                restaurantTableRepository.findById(session.getTableId()).map(table ->
                        toView(session, table)));
    }

    @Override
    @Transactional
    public void recordStaffCall(UUID sessionId, String reason, String note) {
        Instant now = Instant.now(clock);
        TableSessionEntity session = tableSessionRepository.findById(sessionId)
                .orElseThrow(() -> new QrosException(ErrorCode.TABLE_SESSION_EXPIRED));
        RestaurantTable table = restaurantTableRepository.findById(session.getTableId())
                .orElseThrow(() -> new QrosException(ErrorCode.TABLE_SESSION_EXPIRED));

        staffCallRepository.findFirstBySessionIdOrderByCreatedAtDesc(sessionId).ifPresent(lanTruoc -> {
            if (lanTruoc.getCreatedAt().plus(STAFF_CALL_COOLDOWN).isAfter(now)) {
                throw new QrosException(ErrorCode.RATE_LIMITED);
            }
        });

        staffCallRepository.save(StaffCall.moi(sessionId, reason, note, now));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tableLabel", table.getLabel());
        payload.put("reason", reason);
        if (note != null) {
            payload.put("note", note);
        }
        outboxWriter.append(new StaffCalledEvent(DomainEvent.newEventId(), sessionId,
                session.getStoreId(), sessionId, now, payload));
    }

    private static TableSessionView toView(TableSessionEntity session, RestaurantTable table) {
        return new TableSessionView(session.getId(), session.getStoreId(), session.getTableId(),
                table.getLabel(), session.isOpen(), session.isStaffOpened());
    }

    private record StaffCalledEvent(
            UUID eventId, UUID aggregateId, UUID storeId, UUID sessionId,
            Instant occurredAt, Object payload) implements DomainEvent {

        @Override
        public String type() {
            return "StaffCalled";
        }

        @Override
        public String aggregateType() {
            return "TableSession";
        }
    }
}
