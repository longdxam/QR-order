package com.qros.shared.event;

import java.time.Instant;
import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

/**
 * Đọc sự kiện chưa phát rồi đẩy sang Redis ({@code ADR-05}).
 *
 * <p>Cả lượt nằm trong **một** giao dịch, và đó là chỗ bảo đảm "retry không mất sự kiện": dòng
 * được khoá bằng {@code FOR UPDATE SKIP LOCKED}, phát xong mới đánh dấu {@code published_at}, và
 * nếu Redis lỗi thì giao dịch cuộn ngược nên dòng trở lại hàng đợi cho lượt sau. Đổi lại, hệ thống
 * bảo đảm **at-least-once**: một sự kiện có thể tới hai lần nếu Redis nhận được nhưng giao dịch
 * chưa kịp commit. Client khử trùng lặp bằng {@code eventId} ({@code TM-EVT-01}).
 *
 * <p>Ranh giới giao dịch ở đây mở bằng {@link TransactionTemplate} chứ không bằng
 * {@code @Transactional}: luật ArchUnit giữ annotation đó cho tầng {@code service} của module
 * nghiệp vụ, nơi mỗi giao dịch là một use case. Poller không phải use case — nó là hạ tầng, và
 * ranh giới của nó là một chi tiết cần đọc thấy ngay trong thân hàm.
 */
public class OutboxPoller {

    private final TransactionTemplate transactionTemplate;
    private final OutboxRepository repository;
    private final OutboxPublisher publisher;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;

    public OutboxPoller(TransactionTemplate transactionTemplate, OutboxRepository repository,
            OutboxPublisher publisher, ObjectMapper objectMapper, OutboxProperties properties) {
        this.transactionTemplate = transactionTemplate;
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Chạy đúng một lượt.
     *
     * @return số sự kiện đã phát; {@code 0} nghĩa là hàng đợi rỗng.
     */
    public int pollOnce() {
        Integer published = transactionTemplate.execute(status -> {
            List<OutboxEntity> batch = repository.claimUnpublished(properties.batchSize());
            if (batch.isEmpty()) {
                return 0;
            }
            for (OutboxEntity event : batch) {
                publisher.publish(OutboxChannels.of(event), OutboxEnvelope.json(objectMapper, event));
            }
            repository.markPublished(batch.stream().map(OutboxEntity::getId).toList(), Instant.now());
            return batch.size();
        });
        return published != null ? published : 0;
    }
}
