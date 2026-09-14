package com.qros.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.qros.shared.event.DomainEvent;
import com.qros.shared.event.OutboxEntity;
import com.qros.shared.event.OutboxRepository;
import com.qros.shared.event.OutboxWriter;

import org.awaitility.Awaitility;

/**
 * Nhịp chạy tự động của outbox — {@code FR-BAR-02}: đơn mới phải tới KDS trong vòng một giây.
 *
 * <p>{@code OutboxDeliveryTest} gọi từng lượt bằng tay để quan sát kết quả xác định, nên nó không
 * chứng minh được phần dễ hỏng nhất trong môi trường thật: lịch chạy có thực sự khởi động hay
 * không. Một chuỗi thời lượng mà {@code @Scheduled} không phân tích được sẽ làm outbox đứng im
 * hoàn toàn ở dev và prod, và không test nào khác nhìn thấy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "qros.outbox.poll-interval=100ms")
// Poller của context này chạy nền trên cùng cơ sở dữ liệu với các test tích hợp khác; đóng context
// ngay khi class kết thúc để nó không nhặt mất sự kiện của test khác.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxSchedulerTest extends QrosIntegrationTest {

    @Autowired
    private OutboxWriter writer;

    @Autowired
    private OutboxRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void frBar02_lichChayTuPhatSuKienMaKhongCanGoiTay() {
        TransactionTemplate giaoDich = new TransactionTemplate(transactionManager);
        UUID orderId = UUID.randomUUID();

        long seq = giaoDich.execute(status -> writer.append(new SuKienThu(
                DomainEvent.newEventId(), "OrderPlaced", "Order", orderId,
                UUID.randomUUID(), null, Instant.now(),
                Map.of("orderId", orderId.toString()))));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(seq)).get()
                        .extracting(OutboxEntity::getPublishedAt).isNotNull());
    }

    record SuKienThu(UUID eventId, String type, String aggregateType, UUID aggregateId,
            UUID storeId, UUID sessionId, Instant occurredAt, Object payload) implements DomainEvent {
    }
}
