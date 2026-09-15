package com.qros.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.qros.integration.QrosIntegrationTest;
import com.qros.shared.event.DomainEvent;
import com.qros.shared.event.OutboxPoller;
import com.qros.shared.event.OutboxWriter;
import com.qros.shared.security.CookieBearerTokenResolver;
import com.qros.shared.security.JwtIssuer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "qros.outbox.scheduler-enabled=false")
class KdsRealtimeTest extends QrosIntegrationTest {

    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtIssuer staffJwtIssuer;
    @Autowired private OutboxWriter outboxWriter;
    @Autowired private OutboxPoller outboxPoller;
    @Autowired private TransactionTemplate transactionTemplate;

    private UUID storeId;
    private UUID userId;

    @BeforeEach
    void prepare() {
        storeId = UUID.randomUUID();
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO store (id, code, name, timezone, opens_at, closes_at, active) VALUES (?,?,?,'UTC','00:00','23:59',true)",
                storeId, "WS-" + storeId, "Realtime test");
        jdbc.update("INSERT INTO app_user (id, email, display_name, active) VALUES (?,?,?,true)",
                userId, userId + "@test.local", "Minh");
        jdbc.update("INSERT INTO user_role (id, user_id, role_id, store_id) SELECT ?, ?, id, ? FROM role WHERE code='BARISTA'",
                UUID.randomUUID(), userId, storeId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM outbox_event WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        jdbc.update("DELETE FROM store WHERE id = ?", storeId);
    }

    @Test
    void frBar02_va_frBar06_phatSongDuoiMotGiay_vaPhatBuTheoSeq() throws Exception {
        long replaySeq = append("OrderPlaced", Map.of("orderId", UUID.randomUUID().toString()));
        // Bộ poller dùng chung có thể còn sự kiện của test tích hợp khác; chỉ cần
        // bảo đảm lượt poll đã phát ít nhất sự kiện vừa ghi của chi nhánh này.
        assertThat(outboxPoller.pollOnce()).isGreaterThanOrEqualTo(1);
        StompSession session = connect();
        CompletableFuture<String> replayed = subscribe(session, "/user/queue/resume");
        StompHeaders resumeHeaders = new StompHeaders();
        resumeHeaders.setDestination("/app/resume");
        resumeHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        session.send(resumeHeaders, ("{\"channels\":[{\"address\":\"/topic/kds/" + storeId
                + "\",\"lastSeq\":" + (replaySeq - 1) + "}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String replayBody = replayed.get(2, TimeUnit.SECONDS);
        assertThat(replayBody).contains("\"seq\":" + replaySeq).contains("\"type\":\"OrderPlaced\"");

        CompletableFuture<String> live = subscribe(session, "/topic/kds/" + storeId);
        long start = System.nanoTime();
        long liveSeq = append("OrderLineStatusChanged", Map.of(
                "orderId", UUID.randomUUID().toString(), "lineId", UUID.randomUUID().toString(),
                "status", "READY", "version", 2));
        assertThat(outboxPoller.pollOnce()).isGreaterThanOrEqualTo(1);
        String liveBody = live.get(1, TimeUnit.SECONDS);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
        assertThat(liveBody).contains("\"seq\":" + liveSeq).contains("\"status\":\"READY\"");
        session.disconnect();
    }

    private StompSession connect() throws Exception {
        String token = staffJwtIssuer.issue(userId.toString(), Duration.ofMinutes(5), Map.of(
                "scope", "staff", "tv", 0, "stores", java.util.List.of(storeId.toString()),
                "permissions", java.util.List.of("order:read:store"), "mfaBlocked", false));
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.COOKIE, CookieBearerTokenResolver.COOKIE_NAME + "=" + token);
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        return client.connectAsync("ws://localhost:" + port + "/api/v1/staff/ws", headers,
                new StompSessionHandlerAdapter() { }).get(3, TimeUnit.SECONDS);
    }

    private static CompletableFuture<String> subscribe(StompSession session, String destination) {
        CompletableFuture<String> message = new CompletableFuture<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                message.complete(new String((byte[]) payload, java.nio.charset.StandardCharsets.UTF_8));
            }
        });
        return message;
    }

    private long append(String type, Map<String, Object> payload) {
        Long seq = transactionTemplate.execute(status -> outboxWriter.append(new TestEvent(
                DomainEvent.newEventId(), UUID.randomUUID(), storeId, Instant.now(), type, payload)));
        return java.util.Objects.requireNonNull(seq);
    }

    private record TestEvent(UUID eventId, UUID aggregateId, UUID storeId, Instant occurredAt,
            String type, Object payload) implements DomainEvent {
        @Override public String aggregateType() { return "Test"; }
        @Override public UUID sessionId() { return null; }
    }
}
