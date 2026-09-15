package com.qros.ordering.service;

import java.time.Clock;
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

import com.qros.catalog.api.CatalogFacade;
import com.qros.catalog.api.PricedLine;
import com.qros.generated.model.Money;
import com.qros.generated.model.Money.CurrencyEnum;
import com.qros.generated.model.Order;
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
import com.qros.shared.idempotency.IdempotencyGuard;
import com.qros.shared.idempotency.IdempotencyRequest;
import com.qros.shared.idempotency.IdempotentResponse;
import com.qros.venue.api.TableSessionFacade;
import com.qros.venue.api.TableSessionView;

/**
 * {@code FR-CUS-08}, {@code FR-CUS-09}, {@code ADR-06}: điểm nhạy cảm nhất của toàn bộ hợp đồng.
 *
 * <p>Khác {@code venue}/{@code catalog}, service này trả thẳng DTO hợp đồng
 * ({@code com.qros.generated.model.Order}) thay vì entity domain — {@link IdempotencyGuard} lưu và
 * phát lại kết quả bằng cách tuần tự hoá/giải tuần tự hoá chính kiểu trả về, nên kiểu đó phải là
 * chính xác thứ client nhận được; dựng nó hai lần (một cho lượt tạo mới, một khác cho lượt phát
 * lại) sẽ có ngày lệch nhau. Đây là ngoại lệ có chủ ý với quy ước "service trả domain, controller
 * dịch DTO" ở {@code venue}/{@code catalog}.
 *
 * <p>Chỉ nạp khi có {@code DataSource}, cùng lý do mọi service khác cần CSDL thật.
 */
@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class OrderService {

    private final CatalogFacade catalogFacade;
    private final TableSessionFacade tableSessionFacade;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderLineOptionRepository orderLineOptionRepository;
    private final OrderStatusLogRepository orderStatusLogRepository;
    private final OutboxWriter outboxWriter;
    private final IdempotencyGuard idempotencyGuard;
    private final Clock clock;

    public OrderService(CatalogFacade catalogFacade, TableSessionFacade tableSessionFacade,
            CustomerOrderRepository customerOrderRepository, OrderLineRepository orderLineRepository,
            OrderLineOptionRepository orderLineOptionRepository,
            OrderStatusLogRepository orderStatusLogRepository, OutboxWriter outboxWriter,
            IdempotencyGuard idempotencyGuard, Clock clock) {
        this.catalogFacade = catalogFacade;
        this.tableSessionFacade = tableSessionFacade;
        this.customerOrderRepository = customerOrderRepository;
        this.orderLineRepository = orderLineRepository;
        this.orderLineOptionRepository = orderLineOptionRepository;
        this.orderStatusLogRepository = orderStatusLogRepository;
        this.outboxWriter = outboxWriter;
        this.idempotencyGuard = idempotencyGuard;
        this.clock = clock;
    }

    public record LineInput(UUID menuItemId, UUID variantId, List<UUID> optionIds, int quantity, String note,
            String addedBy) {
    }

    /**
     * @param bodyJson thân yêu cầu đã tuần tự hoá — dùng để lấy vân tay idempotency
     *                 ({@code scope + sessionId + body}), không dùng cho gì khác.
     */
    // KHÔNG noRollbackFor: khác EC-02 của venue (ghi audit RỒI mới ném để giữ lại bằng chứng),
    // ở đây không có gì cần sống sót qua một lần từ chối — IdempotencyGuard cố tình để khoá biến
    // mất cùng giao dịch hỏng (xem javadoc IdempotencyGuard), nên rollback mặc định là đúng ý.
    // Từng thử thêm noRollbackFor "cho chắc" — gây UnexpectedRollbackException vì CatalogFacadeImpl
    // .priceLine() là một @Transactional lồng bên trong không có cùng noRollbackFor: interceptor
    // của lời gọi lồng đó đánh dấu rollback-only trước khi control quay lại đây, khiến giao dịch
    // ngoài cố commit (vì noRollbackFor khớp) nhưng thất bại — bài học: noRollbackFor ở phương thức
    // MỞ giao dịch chỉ có nghĩa khi lời gọi lồng bên trong KHÔNG tự là một @Transactional khác.
    @Transactional
    public IdempotentResponse<Order> placeOrder(UUID sessionId, UUID idempotencyKey, String bodyJson,
            List<LineInput> lines, String orderNote) {

        TableSessionView session = phienDangMo(sessionId);
        IdempotencyRequest request = new IdempotencyRequest(idempotencyKey, "guest.placeOrder", sessionId, bodyJson);
        return idempotencyGuard.execute(request, Order.class, () -> taoDonThat(session, lines, orderNote));
    }

    private IdempotentResponse<Order> taoDonThat(TableSessionView session, List<LineInput> lines, String orderNote) {
        Instant now = Instant.now(clock);

        record DinhGia(LineInput input, PricedLine gia) {
        }
        List<DinhGia> daDinhGia = lines.stream()
                .map(line -> new DinhGia(line,
                        catalogFacade.priceLine(session.storeId(), line.menuItemId(), line.variantId(),
                                line.optionIds())))
                .toList();

        daDinhGia.stream().filter(d -> !d.gia().available()).findFirst().ifPresent(d -> {
            throw new QrosException(ErrorCode.ITEM_SOLD_OUT,
                    "Món \"%s\" vừa hết, vui lòng chọn món khác".formatted(d.gia().itemName()));
        });

        long subtotal = daDinhGia.stream()
                .mapToLong(d -> Math.multiplyExact(d.gia().unitPriceEach(), d.input().quantity()))
                .sum();

        // EC-03: đơn đầu của một phiên chưa được thu ngân mở phải chờ nhân viên xác nhận; đơn tiếp
        // theo trong cùng phiên tự động xác nhận, kể cả khi thu ngân chưa mở bàn.
        boolean laDonDauTien = customerOrderRepository.countBySessionId(session.sessionId()) == 0;
        boolean requiresStaffConfirmation = !session.staffOpened() && laDonDauTien;

        String shortCode = sinhMaNgan(session);

        CustomerOrder order = CustomerOrder.moi(session.storeId(), session.tableId(), session.sessionId(),
                shortCode, subtotal, requiresStaffConfirmation, now);
        if (!requiresStaffConfirmation) {
            order.xacNhanTuDong();
        }
        customerOrderRepository.save(order);
        orderStatusLogRepository.save(OrderStatusLog.chuyen(order.getId(), null, order.getStatus(), null, now));

        List<OrderLine> savedLines = daDinhGia.stream()
                .map(d -> {
                    OrderLine line = OrderLine.moi(order.getId(), d.input().menuItemId(), d.input().variantId(),
                            d.gia().itemName(), d.gia().variantName(), d.gia().unitPriceEach(),
                            d.input().quantity(), lamSachGhiChu(d.input().note()), d.input().addedBy(),
                            d.gia().station());
                    if (!requiresStaffConfirmation) {
                        line.xacNhanTuDong(now);
                    }
                    orderLineRepository.save(line);
                    d.gia().options().forEach(option -> orderLineOptionRepository.save(
                            new OrderLineOption(line.getId(), option.optionChoiceId(), option.name(),
                                    option.surcharge())));
                    return line;
                })
                .toList();

        Map<UUID, List<OrderLineOption>> optionsByLine = savedLines.stream()
                .collect(Collectors.toMap(OrderLine::getId,
                        l -> orderLineOptionRepository.findByOrderLineIdIn(List.of(l.getId()))));

        ghiOutboxDatDon(order, session.tableLabel(), savedLines, optionsByLine, now);

        return new IdempotentResponse<>(201, toDto(order, savedLines, optionsByLine));
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId, UUID sessionId) {
        CustomerOrder order = donCuaPhien(orderId, sessionId);
        List<OrderLine> lines = orderLineRepository.findByOrderId(orderId);
        return toDto(order, lines, tuyChonTheoDong(lines));
    }

    @Transactional(readOnly = true)
    public List<Order> listOrders(UUID sessionId) {
        List<CustomerOrder> orders = customerOrderRepository.findBySessionIdOrderByPlacedAtDesc(sessionId);
        if (orders.isEmpty()) {
            return List.of();
        }
        List<UUID> orderIds = orders.stream().map(CustomerOrder::getId).toList();
        List<OrderLine> allLines = orderLineRepository.findByOrderIdInOrderByOrderId(orderIds);
        Map<UUID, List<OrderLine>> linesByOrder = allLines.stream()
                .collect(Collectors.groupingBy(OrderLine::getOrderId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<OrderLineOption>> optionsByLine = tuyChonTheoDong(allLines);

        return orders.stream()
                .map(order -> toDto(order, linesByOrder.getOrDefault(order.getId(), List.of()), optionsByLine))
                .toList();
    }

    // Cùng lý do placeOrder(): không có ghi nào trước INVALID_TRANSITION cần sống sót.
    @Transactional
    public Order cancelOrder(UUID orderId, UUID sessionId) {
        CustomerOrder order = donCuaPhien(orderId, sessionId);
        String truoc = order.getStatus();
        order.huyBoiKhach();
        customerOrderRepository.save(order);

        Instant now = Instant.now(clock);
        orderStatusLogRepository.save(OrderStatusLog.chuyen(orderId, truoc, order.getStatus(),
                "Khách tự huỷ", now));
        outboxWriter.append(new OrderCancelledEvent(DomainEvent.newEventId(), orderId, order.getStoreId(),
                sessionId, now, Map.of("orderId", orderId.toString(), "reason", "Khách tự huỷ")));
        outboxWriter.append(new OrderCancelledEvent(DomainEvent.newEventId(), orderId, order.getStoreId(),
                null, now, Map.of("orderId", orderId.toString(), "reason", "Khách tự huỷ")));

        List<OrderLine> lines = orderLineRepository.findByOrderId(orderId);
        return toDto(order, lines, tuyChonTheoDong(lines));
    }

    private TableSessionView phienDangMo(UUID sessionId) {
        return tableSessionFacade.find(sessionId)
                .filter(TableSessionView::open)
                .orElseThrow(() -> new QrosException(ErrorCode.TABLE_SESSION_EXPIRED));
    }

    /** Bất biến số 7: đơn phải thuộc đúng phiên — không đủ điều kiện thì 404, không phải 403. */
    private CustomerOrder donCuaPhien(UUID orderId, UUID sessionId) {
        return customerOrderRepository.findByIdAndSessionId(orderId, sessionId)
                .orElseThrow(() -> new QrosException(ErrorCode.NOT_FOUND));
    }

    private String sinhMaNgan(TableSessionView session) {
        // Khoá tư vấn theo chi nhánh trước khi đếm — tránh hai đơn cùng chi nhánh, cùng lúc, tính
        // trùng số thứ tự trong ngày (cùng kỹ thuật venue.TableSessionRepository.khoaTheoBan).
        customerOrderRepository.khoaTheoChiNhanh(session.storeId());
        long soThuTu = customerOrderRepository.demSoDonHomNay(session.storeId()) + 1;
        return "%s-%d".formatted(session.tableLabel(), soThuTu);
    }

    /** {@code FR-CUS-06}: ghi chú tự do phải được làm sạch trước khi hiển thị cho barista. */
    private static String lamSachGhiChu(String note) {
        if (note == null) {
            return null;
        }
        String khongThe = note.replaceAll("<[^>]*>", "").replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        return khongThe.isBlank() ? null : khongThe.strip();
    }

    private Map<UUID, List<OrderLineOption>> tuyChonTheoDong(List<OrderLine> lines) {
        if (lines.isEmpty()) {
            return Map.of();
        }
        List<UUID> lineIds = lines.stream().map(OrderLine::getId).toList();
        return orderLineOptionRepository.findByOrderLineIdIn(lineIds).stream()
                .collect(Collectors.groupingBy(OrderLineOption::getOrderLineId));
    }

    private void ghiOutboxDatDon(CustomerOrder order, String tableLabel, List<OrderLine> lines,
            Map<UUID, List<OrderLineOption>> optionsByLine, Instant now) {

        List<Map<String, Object>> lineMaps = lines.stream()
                .map(line -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("lineId", line.getId().toString());
                    m.put("name", line.getItemName());
                    m.put("variantName", line.getVariantName());
                    m.put("optionNames", optionsByLine.getOrDefault(line.getId(), List.of()).stream()
                            .map(OrderLineOption::getOptionName).toList());
                    m.put("quantity", line.getQuantity());
                    if (line.getNote() != null) {
                        m.put("note", line.getNote());
                    }
                    m.put("station", line.getStation());
                    return m;
                })
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId().toString());
        payload.put("shortCode", order.getShortCode());
        payload.put("tableLabel", tableLabel);
        payload.put("requiresStaffConfirmation", order.isRequiresStaffConfirmation());
        payload.put("placedAt", order.getPlacedAt().toString());
        payload.put("lines", lineMaps);

        outboxWriter.append(new OrderPlacedEvent(DomainEvent.newEventId(), order.getId(), order.getStoreId(),
                null, now, payload));
    }

    private static Order toDto(CustomerOrder order, List<OrderLine> lines,
            Map<UUID, List<OrderLineOption>> optionsByLine) {

        Order dto = new Order(order.getId(), Order.StatusEnum.fromValue(order.getStatus()), null,
                moneyOf(order.getTotalAmount()), OffsetDateTime.ofInstant(order.getPlacedAt(), ZoneOffset.UTC),
                order.getVersion());
        dto.shortCode(order.getShortCode());
        dto.subtotal(moneyOf(order.getSubtotalAmount()));
        dto.discount(moneyOf(order.getDiscountAmount()));
        dto.requiresStaffConfirmation(order.isRequiresStaffConfirmation());
        lines.forEach(line -> dto.addLinesItem(toDto(line, optionsByLine.getOrDefault(line.getId(), List.of()))));
        return dto;
    }

    private static com.qros.generated.model.OrderLine toDto(OrderLine line, List<OrderLineOption> options) {
        com.qros.generated.model.OrderLine dto = new com.qros.generated.model.OrderLine(
                line.getId(), line.getMenuItemId(), line.getItemName(), line.getQuantity(),
                moneyOf(line.getLineTotal()),
                com.qros.generated.model.OrderLine.StatusEnum.fromValue(line.getStatus()), line.getVersion());
        dto.variantName(line.getVariantName());
        options.forEach(option -> dto.addOptionNamesItem(option.getOptionName()));
        dto.unitPrice(moneyOf(line.getUnitPrice()));
        if (line.getNote() != null) {
            dto.note(line.getNote());
        }
        if (line.getAddedBy() != null) {
            dto.addedBy(line.getAddedBy());
        }
        return dto;
    }

    private static Money moneyOf(long amount) {
        return new Money(amount, CurrencyEnum.VND);
    }

    private record OrderPlacedEvent(UUID eventId, UUID aggregateId, UUID storeId, UUID sessionId,
            Instant occurredAt, Object payload) implements DomainEvent {

        @Override
        public String type() {
            return "OrderPlaced";
        }

        @Override
        public String aggregateType() {
            return "Order";
        }
    }

    private record OrderCancelledEvent(UUID eventId, UUID aggregateId, UUID storeId, UUID sessionId,
            Instant occurredAt, Object payload) implements DomainEvent {

        @Override
        public String type() {
            return "OrderCancelled";
        }

        @Override
        public String aggregateType() {
            return "Order";
        }
    }
}
