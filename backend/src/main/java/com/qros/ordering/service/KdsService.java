package com.qros.ordering.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qros.generated.model.GetKdsQueue200Response;
import com.qros.generated.model.IngredientRef;
import com.qros.generated.model.KdsTicket;
import com.qros.generated.model.Money;
import com.qros.generated.model.Money.CurrencyEnum;
import com.qros.identity.api.StaffAccessFacade;
import com.qros.identity.api.StaffIdentity;
import com.qros.catalog.api.CatalogFacade;
import com.qros.catalog.api.IngredientView;
import com.qros.catalog.api.RecipeSelection;
import com.qros.ordering.domain.CustomerOrder;
import com.qros.ordering.domain.OrderLine;
import com.qros.ordering.domain.OrderLineOption;
import com.qros.ordering.domain.OrderStatusLog;
import com.qros.ordering.repository.CustomerOrderRepository;
import com.qros.ordering.repository.OrderLineOptionRepository;
import com.qros.ordering.repository.OrderLineRepository;
import com.qros.ordering.repository.OrderStatusLogRepository;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.shared.event.DomainEvent;
import com.qros.shared.event.OutboxWriter;
import com.qros.venue.api.TableSessionFacade;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class KdsService {

    private static final String READ_PERMISSION = "order:read:store";
    private static final String UPDATE_PERMISSION = "order:update-status";
    private static final int SLA_SECONDS = 480;
    private static final List<String> ACTIVE_ORDER_STATUSES = List.of("PENDING", "CONFIRMED", "PREPARING", "READY");
    private static final List<String> ACTIVE_LINE_STATUSES = List.of("PENDING", "CONFIRMED", "PREPARING", "READY");

    private final StaffAccessFacade staffAccessFacade;
    private final CatalogFacade catalogFacade;
    private final CustomerOrderRepository orderRepository;
    private final OrderLineRepository lineRepository;
    private final OrderLineOptionRepository optionRepository;
    private final OrderStatusLogRepository statusLogRepository;
    private final TableSessionFacade tableSessionFacade;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    public KdsService(StaffAccessFacade staffAccessFacade, CatalogFacade catalogFacade,
            CustomerOrderRepository orderRepository,
            OrderLineRepository lineRepository, OrderLineOptionRepository optionRepository,
            OrderStatusLogRepository statusLogRepository, TableSessionFacade tableSessionFacade,
            OutboxWriter outboxWriter, Clock clock) {
        this.staffAccessFacade = staffAccessFacade;
        this.catalogFacade = catalogFacade;
        this.orderRepository = orderRepository;
        this.lineRepository = lineRepository;
        this.optionRepository = optionRepository;
        this.statusLogRepository = statusLogRepository;
        this.tableSessionFacade = tableSessionFacade;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GetKdsQueue200Response getQueue(UUID actorId, UUID storeId, String station) {
        requireAccess(actorId, storeId, READ_PERMISSION);
        Instant now = Instant.now(clock);
        List<CustomerOrder> orders = orderRepository
                .findByStoreIdAndStatusInOrderByPlacedAtAsc(storeId, ACTIVE_ORDER_STATUSES);
        if (orders.isEmpty()) {
            return new GetKdsQueue200Response(List.of(), atUtc(now));
        }

        List<OrderLine> allLines = lineRepository.findByOrderIdInOrderByOrderId(
                orders.stream().map(CustomerOrder::getId).toList());
        List<OrderLine> visibleLines = allLines.stream()
                .filter(line -> ACTIVE_LINE_STATUSES.contains(line.getStatus()))
                .filter(line -> station == null || "ALL".equalsIgnoreCase(station)
                        || line.getStation().equalsIgnoreCase(station))
                .toList();
        Map<UUID, List<OrderLine>> byOrder = visibleLines.stream()
                .collect(Collectors.groupingBy(OrderLine::getOrderId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<OrderLineOption>> options = optionsByLine(visibleLines);
        Map<UUID, List<IngredientView>> ingredients = ingredientsByLine(storeId, visibleLines, options);

        List<KdsTicket> tickets = orders.stream()
                .filter(order -> byOrder.containsKey(order.getId()))
                .map(order -> toTicket(order, byOrder.get(order.getId()), options, ingredients, now))
                .toList();
        return new GetKdsQueue200Response(tickets, atUtc(now));
    }

    @Transactional
    public com.qros.generated.model.OrderLine updateStatus(UUID actorId, UUID storeId, UUID deviceId,
            UUID lineId, int expectedVersion, String target, String reason) {
        StaffIdentity actor = requireAccess(actorId, storeId, UPDATE_PERMISSION);
        CustomerOrder order = orderRepository.findAllById(List.of(
                        lineRepository.findById(lineId).orElseThrow(() -> new QrosException(ErrorCode.NOT_FOUND))
                                .getOrderId()))
                .stream().filter(candidate -> candidate.getStoreId().equals(storeId)).findFirst()
                .orElseThrow(() -> new QrosException(ErrorCode.NOT_FOUND));
        OrderLine line = lineRepository.findByIdAndOrderId(lineId, order.getId())
                .orElseThrow(() -> new QrosException(ErrorCode.NOT_FOUND));
        if (line.getVersion() != expectedVersion) {
            throw versionConflict(line, storeId);
        }

        String previous = line.getStatus();
        Instant now = Instant.now(clock);
        line.chuyenTrangThai(target, reason, now);
        lineRepository.saveAndFlush(line);
        statusLogRepository.save(OrderStatusLog.chuyenDong(order.getId(), lineId, previous, target,
                reason, actorId, deviceId, now));

        List<OrderLine> orderLines = lineRepository.findByOrderId(order.getId());
        String previousOrderStatus = order.getStatus();
        if (order.dongBoTrangThaiDong(orderLines.stream().map(OrderLine::getStatus).toList())) {
            orderRepository.saveAndFlush(order);
            statusLogRepository.save(OrderStatusLog.chuyen(order.getId(), previousOrderStatus,
                    order.getStatus(), reason, now));
            appendOrderStatusEvents(order, now, actor.displayName());
        }

        com.qros.generated.model.OrderLine dto = toDto(line,
                optionsByLine(List.of(line)).getOrDefault(lineId, List.of()), ingredientsForLine(storeId, line));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId().toString());
        payload.put("lineId", lineId.toString());
        payload.put("status", target);
        payload.put("version", line.getVersion());
        payload.put("changedBy", actor.displayName());
        appendStatusEvents(order, lineId, now, payload);
        return dto;
    }

    @Transactional(readOnly = true)
    public com.qros.generated.model.OrderLine currentLine(UUID actorId, UUID storeId, UUID lineId) {
        requireAccess(actorId, storeId, READ_PERMISSION);
        OrderLine line = lineRepository.findById(lineId).orElseThrow(() -> new QrosException(ErrorCode.NOT_FOUND));
        CustomerOrder order = orderRepository.findById(line.getOrderId())
                .filter(candidate -> candidate.getStoreId().equals(storeId))
                .orElseThrow(() -> new QrosException(ErrorCode.NOT_FOUND));
        return toDto(line, optionsByLine(List.of(line)).getOrDefault(lineId, List.of()),
                ingredientsForLine(storeId, line));
    }

    public QrosException versionConflict(com.qros.generated.model.OrderLine current) {
        return new QrosException(ErrorCode.VERSION_CONFLICT, null, Map.of("current", current));
    }

    private QrosException versionConflict(OrderLine current, UUID storeId) {
        return versionConflict(toDto(current,
                optionsByLine(List.of(current)).getOrDefault(current.getId(), List.of()),
                ingredientsForLine(storeId, current)));
    }

    private StaffIdentity requireAccess(UUID actorId, UUID storeId, String permission) {
        return staffAccessFacade.findAuthorized(actorId, storeId, permission)
                .orElseThrow(() -> new QrosException(ErrorCode.NOT_FOUND));
    }

    private KdsTicket toTicket(CustomerOrder order, List<OrderLine> lines,
            Map<UUID, List<OrderLineOption>> options, Map<UUID, List<IngredientView>> ingredients, Instant now) {
        String tableLabel = tableSessionFacade.find(order.getSessionId())
                .map(session -> session.tableLabel()).orElse("—");
        KdsTicket ticket = new KdsTicket(order.getId(), order.getShortCode(), tableLabel,
                atUtc(order.getPlacedAt()), SLA_SECONDS, order.isRequiresStaffConfirmation(),
                lines.stream().map(line -> toDto(line, options.getOrDefault(line.getId(), List.of()),
                        ingredients.getOrDefault(line.getId(), List.of()))).toList());
        ticket.setIsOverdue(Duration.between(order.getPlacedAt(), now).getSeconds() >= SLA_SECONDS);
        return ticket;
    }

    private Map<UUID, List<OrderLineOption>> optionsByLine(List<OrderLine> lines) {
        if (lines.isEmpty()) {
            return Map.of();
        }
        return optionRepository.findByOrderLineIdIn(lines.stream().map(OrderLine::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderLineOption::getOrderLineId));
    }

    private Map<UUID, List<IngredientView>> ingredientsByLine(UUID storeId, List<OrderLine> lines,
            Map<UUID, List<OrderLineOption>> options) {
        return catalogFacade.ingredientsFor(storeId, lines.stream()
                .map(line -> new RecipeSelection(line.getId(), line.getMenuVariantId(),
                        options.getOrDefault(line.getId(), List.of()).stream()
                                .map(OrderLineOption::getOptionChoiceId).toList()))
                .toList());
    }

    private List<IngredientView> ingredientsForLine(UUID storeId, OrderLine line) {
        Map<UUID, List<OrderLineOption>> options = optionsByLine(List.of(line));
        return ingredientsByLine(storeId, List.of(line), options).getOrDefault(line.getId(), List.of());
    }

    private com.qros.generated.model.OrderLine toDto(OrderLine line, List<OrderLineOption> options,
            List<IngredientView> ingredients) {
        com.qros.generated.model.OrderLine dto = new com.qros.generated.model.OrderLine(
                line.getId(), line.getMenuItemId(), line.getItemName(), line.getQuantity(),
                new Money(line.getLineTotal(), CurrencyEnum.VND),
                com.qros.generated.model.OrderLine.StatusEnum.fromValue(line.getStatus()), line.getVersion());
        dto.setVariantName(line.getVariantName());
        dto.setOptionNames(options.stream().map(OrderLineOption::getOptionName).toList());
        dto.setIngredients(ingredients.stream()
                .map(ingredient -> new IngredientRef(ingredient.id(), ingredient.name(), ingredient.soldOut()))
                .toList());
        dto.setUnitPrice(new Money(line.getUnitPrice(), CurrencyEnum.VND));
        dto.setNote(line.getNote());
        dto.setAddedBy(line.getAddedBy());
        return dto;
    }

    private void appendStatusEvents(CustomerOrder order, UUID lineId, Instant now, Map<String, Object> payload) {
        outboxWriter.append(new LineStatusChangedEvent(DomainEvent.newEventId(), lineId, order.getStoreId(),
                null, now, payload));
        outboxWriter.append(new LineStatusChangedEvent(DomainEvent.newEventId(), lineId, order.getStoreId(),
                order.getSessionId(), now, payload));
    }

    private void appendOrderStatusEvents(CustomerOrder order, Instant now, String changedBy) {
        Map<String, Object> payload = Map.of(
                "orderId", order.getId().toString(),
                "status", order.getStatus(),
                "version", order.getVersion(),
                "changedBy", changedBy);
        outboxWriter.append(new OrderStatusChangedEvent(DomainEvent.newEventId(), order.getId(),
                order.getStoreId(), null, now, payload));
        outboxWriter.append(new OrderStatusChangedEvent(DomainEvent.newEventId(), order.getId(),
                order.getStoreId(), order.getSessionId(), now, payload));
    }

    private static OffsetDateTime atUtc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record LineStatusChangedEvent(UUID eventId, UUID aggregateId, UUID storeId, UUID sessionId,
            Instant occurredAt, Object payload) implements DomainEvent {
        @Override public String type() { return "OrderLineStatusChanged"; }
        @Override public String aggregateType() { return "OrderLine"; }
    }

    private record OrderStatusChangedEvent(UUID eventId, UUID aggregateId, UUID storeId, UUID sessionId,
            Instant occurredAt, Object payload) implements DomainEvent {
        @Override public String type() { return "OrderStatusChanged"; }
        @Override public String aggregateType() { return "Order"; }
    }
}
