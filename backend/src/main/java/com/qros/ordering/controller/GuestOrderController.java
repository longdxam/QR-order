package com.qros.ordering.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import com.qros.generated.api.GuestOrderApi;
import com.qros.generated.model.CallStaffRequest;
import com.qros.generated.model.CreateOrderLine;
import com.qros.generated.model.CreateOrderRequest;
import com.qros.generated.model.ListSessionOrders200Response;
import com.qros.generated.model.Money;
import com.qros.generated.model.Money.CurrencyEnum;
import com.qros.generated.model.Order;
import com.qros.ordering.service.OrderService;
import com.qros.ordering.service.OrderService.LineInput;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.shared.idempotency.IdempotentResponse;
import com.qros.venue.api.TableSessionFacade;

import tools.jackson.databind.ObjectMapper;

/**
 * {@code FR-CUS-08}, {@code FR-CUS-09}, {@code FR-CUS-12}: đặt/xem/huỷ đơn và gọi nhân viên.
 *
 * <p>{@code callStaff} nằm ở đây vì {@link GuestOrderApi} gộp chung một interface (hợp đồng OpenAPI
 * gắn thẳng cả năm endpoint vào tag {@code guest-order}), nhưng tự thân thao tác lại uỷ quyền cho
 * {@code venue} qua {@link TableSessionFacade} — {@code staff_call} là bảng của {@code venue}, không
 * phải của {@code ordering}; module thực hiện chỉ theo dữ liệu nó sở hữu, không theo cách interface
 * Java được gộp nhóm.
 *
 * <p>Chỉ nạp khi có {@code DataSource}, cùng lý do {@code OrderService} nó phụ thuộc.
 */
@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
public class GuestOrderController implements GuestOrderApi {

    private final OrderService orderService;
    private final TableSessionFacade tableSessionFacade;
    private final ObjectMapper objectMapper;

    public GuestOrderController(OrderService orderService, TableSessionFacade tableSessionFacade,
            ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.tableSessionFacade = tableSessionFacade;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<Order> placeOrder(UUID idempotencyKey, CreateOrderRequest createOrderRequest) {
        UUID sessionId = sessionIdCuaPhien();
        List<LineInput> lines = createOrderRequest.getLines().stream().map(GuestOrderController::toLineInput).toList();
        String bodyJson = objectMapper.writeValueAsString(createOrderRequest);

        IdempotentResponse<Order> ketQua = orderService.placeOrder(
                sessionId, idempotencyKey, bodyJson, lines, createOrderRequest.getNote());

        HttpStatus status = ketQua.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (!ketQua.replayed()) {
            response.location(URI.create("/api/v1/guest/orders/" + ketQua.body().getId()));
        }
        return response.body(ketQua.body());
    }

    @Override
    public ResponseEntity<ListSessionOrders200Response> listSessionOrders() {
        UUID sessionId = sessionIdCuaPhien();
        List<Order> orders = orderService.listOrders(sessionId);
        long tongTien = orders.stream().mapToLong(o -> o.getTotal().getAmount()).sum();

        ListSessionOrders200Response body = new ListSessionOrders200Response(
                orders, new Money(tongTien, CurrencyEnum.VND));
        // Thanh toán chưa xây (M2) — mọi phiên đều "chưa thanh toán" cho tới khi có module payment.
        body.setPaymentStatus(ListSessionOrders200Response.PaymentStatusEnum.UNPAID);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Order> getOrder(UUID orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId, sessionIdCuaPhien()));
    }

    @Override
    public ResponseEntity<Order> cancelOrderByGuest(UUID orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId, sessionIdCuaPhien()));
    }

    @Override
    public ResponseEntity<Void> callStaff(CallStaffRequest callStaffRequest) {
        tableSessionFacade.recordStaffCall(sessionIdCuaPhien(),
                callStaffRequest.getReason().getValue(), callStaffRequest.getNote());
        return ResponseEntity.accepted().build();
    }

    private static LineInput toLineInput(CreateOrderLine line) {
        return new LineInput(line.getMenuItemId(), line.getVariantId(),
                line.getOptionIds() == null ? List.of() : line.getOptionIds(),
                line.getQuantity(), line.getNote(), line.getAddedBy());
    }

    private UUID sessionIdCuaPhien() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            throw new QrosException(ErrorCode.TABLE_SESSION_EXPIRED);
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new QrosException(ErrorCode.TABLE_SESSION_EXPIRED);
        }
    }
}
