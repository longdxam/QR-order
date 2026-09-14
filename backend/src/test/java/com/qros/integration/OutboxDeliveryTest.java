package com.qros.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.qros.shared.event.DomainEvent;
import com.qros.shared.event.OutboxEntity;
import com.qros.shared.event.OutboxPoller;
import com.qros.shared.event.OutboxPublisher;
import com.qros.shared.event.OutboxRepository;
import com.qros.shared.event.OutboxWriter;
import com.qros.shared.event.RedisOutboxPublisher;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Outbox chạy trên PostgreSQL và Redis thật — {@code FR-BAR-02}, {@code ADR-05},
 * {@code TM-EVT-01}.
 *
 * <p>Hạ tầng lấy từ {@link QrosIntegrationTest}. Flyway dựng lược đồ V1 và
 * {@code ddl-auto: validate} tự đối chiếu {@link OutboxEntity} với bảng thật: entity lệch cột thì
 * context không khởi động được — một phép kiểm miễn phí đi kèm mọi test tích hợp.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "qros.outbox.scheduler-enabled=false")
class OutboxDeliveryTest extends QrosIntegrationTest {

    private static final TypeReference<Map<String, Object>> MESSAGE_TYPE = new TypeReference<>() {
    };

    @Autowired
    private OutboxWriter writer;

    @Autowired
    private OutboxPoller poller;

    @Autowired
    private OutboxRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ControllableOutboxPublisher publisher;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate giaoDich;
    private RedisMessageListenerContainer listenerContainer;
    private final BlockingQueue<String> daNhan = new LinkedBlockingQueue<>();

    @BeforeEach
    void chuanBi() {
        giaoDich = new TransactionTemplate(transactionManager);
        repository.deleteAll();
        publisher.reset();
        daNhan.clear();

        listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(redisConnectionFactory);
        listenerContainer.addMessageListener(
                (message, pattern) -> daNhan.add(new String(message.getBody())),
                new PatternTopic("qros:*"));
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
    }

    @AfterEach
    void donDep() throws Exception {
        listenerContainer.stop();
        listenerContainer.destroy();
    }

    @Test
    void frBar02_suKienVaDuLieuNghiepVuChungMotGiaoDich() {
        SuKienThu suKien = SuKienThu.moi(UUID.randomUUID(), null);

        // Giao dịch cuộn ngược: sự kiện không được sống sót một mình.
        assertThatThrownBy(() -> giaoDich.executeWithoutResult(status -> {
            writer.append(suKien);
            throw new IllegalStateException("nghiệp vụ thất bại sau khi ghi sự kiện");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repository.count()).isZero();

        long seq = giaoDich.execute(status -> writer.append(suKien));

        assertThat(repository.findById(seq)).isPresent();
    }

    @Test
    void adr05_tuChoiGhiSuKienNgoaiGiaoDich() {
        // Nếu writer tự mở giao dịch riêng thì sự kiện có thể commit trong khi nghiệp vụ hỏng.
        assertThatThrownBy(() -> writer.append(SuKienThu.moi(UUID.randomUUID(), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-05");

        assertThat(repository.count()).isZero();
    }

    @Test
    void frBar06_seqChinhLaKhoaChinhVaTangDan() {
        UUID storeId = UUID.randomUUID();

        List<Long> seqs = giaoDich.execute(status -> List.of(
                writer.append(SuKienThu.moi(storeId, null)),
                writer.append(SuKienThu.moi(storeId, null)),
                writer.append(SuKienThu.moi(storeId, null))));

        assertThat(seqs).isSorted().doesNotHaveDuplicates();
        assertThat(repository.findAllById(seqs)).extracting(OutboxEntity::getId)
                .containsExactlyElementsOf(seqs);
    }

    @Test
    void frBar02_phatDungKenhTheoPhamViNguoiNghe() throws Exception {
        UUID storeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        long seqChiNhanh = giaoDich.execute(status -> writer.append(SuKienThu.moi(storeId, null)));
        long seqPhien = giaoDich.execute(status -> writer.append(SuKienThu.moi(storeId, sessionId)));

        assertThat(poller.pollOnce()).isEqualTo(2);

        Map<String, Object> chiNhanh = nhanMotTinNhan();
        Map<String, Object> phien = nhanMotTinNhan();

        assertThat(chiNhanh).containsEntry("seq", (int) seqChiNhanh);
        assertThat(phien).containsEntry("seq", (int) seqPhien);
        // Sự kiện của một phiên bàn không được rơi vào kênh mà mọi nhân viên đều nghe.
        assertThat(kenhDaNhan()).containsExactlyInAnyOrder(
                "qros:store:" + storeId, "qros:session:" + sessionId);
    }

    @Test
    void frBar02_phongBiDayDuTheoAsyncapi() throws Exception {
        UUID storeId = UUID.randomUUID();
        SuKienThu suKien = SuKienThu.moi(storeId, null);
        long seq = giaoDich.execute(status -> writer.append(suKien));

        poller.pollOnce();
        Map<String, Object> tinNhan = nhanMotTinNhan();

        assertThat(tinNhan).containsEntry("eventId", suKien.eventId().toString())
                .containsEntry("type", "OrderPlaced")
                .containsEntry("seq", (int) seq)
                // Mốc thời gian được cắt về micro giây, đúng độ phân giải của timestamptz.
                .containsEntry("occurredAt", suKien.occurredAt().truncatedTo(ChronoUnit.MICROS).toString())
                // Trường nghiệp vụ đi kèm phong bì trong cùng một đối tượng phẳng (allOf).
                .containsEntry("shortCode", "A04-17");
        assertThat(repository.findById(seq)).get()
                .extracting(OutboxEntity::getPublishedAt).isNotNull();
    }

    @Test
    void tmEvt01_redisLoiThiSuKienKhongMat() throws Exception {
        SuKienThu suKien = SuKienThu.moi(UUID.randomUUID(), null);
        long seq = giaoDich.execute(status -> writer.append(suKien));
        publisher.failNext();

        assertThatThrownBy(() -> poller.pollOnce()).isInstanceOf(RuntimeException.class);

        // Phát hỏng thì giao dịch cuộn ngược: dòng phải quay lại hàng đợi, không bị đánh dấu.
        assertThat(repository.findById(seq)).get()
                .extracting(OutboxEntity::getPublishedAt).isNull();
        assertThat(daNhan).isEmpty();

        assertThat(poller.pollOnce()).isEqualTo(1);

        Map<String, Object> tinNhan = nhanMotTinNhan();
        // Cùng eventId qua mọi lần phát lại — điều kiện để client khử trùng lặp.
        assertThat(tinNhan).containsEntry("eventId", suKien.eventId().toString())
                .containsEntry("seq", (int) seq);
    }

    @Test
    void tmEvt01_khongPhatLaiSuKienDaPhat() throws Exception {
        giaoDich.execute(status -> writer.append(SuKienThu.moi(UUID.randomUUID(), null)));

        assertThat(poller.pollOnce()).isEqualTo(1);
        nhanMotTinNhan();

        assertThat(poller.pollOnce()).isZero();
        assertThat(daNhan.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    private Map<String, Object> nhanMotTinNhan() throws InterruptedException {
        String tinNhan = daNhan.poll(10, TimeUnit.SECONDS);
        assertThat(tinNhan).as("không nhận được tin nhắn nào từ Redis").isNotNull();
        return objectMapper.readValue(tinNhan, MESSAGE_TYPE);
    }

    private List<String> kenhDaNhan() {
        return publisher.kenhDaPhat();
    }

    /** Sự kiện mẫu; hình dạng payload lấy theo {@code OrderPlacedPayload} của AsyncAPI. */
    record SuKienThu(UUID eventId, String type, String aggregateType, UUID aggregateId,
            UUID storeId, UUID sessionId, Instant occurredAt, Object payload) implements DomainEvent {

        static SuKienThu moi(UUID storeId, UUID sessionId) {
            UUID orderId = UUID.randomUUID();
            return new SuKienThu(DomainEvent.newEventId(), "OrderPlaced", "Order", orderId,
                    storeId, sessionId, Instant.now(),
                    Map.of("orderId", orderId.toString(), "shortCode", "A04-17"));
        }
    }

    /** Bọc publisher thật để dựng được ca Redis lỗi mà không phải làm hỏng container. */
    static final class ControllableOutboxPublisher implements OutboxPublisher {

        private final OutboxPublisher delegate;
        private final List<String> kenhDaPhat = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile boolean failNext;

        ControllableOutboxPublisher(OutboxPublisher delegate) {
            this.delegate = delegate;
        }

        void failNext() {
            failNext = true;
        }

        void reset() {
            failNext = false;
            kenhDaPhat.clear();
        }

        List<String> kenhDaPhat() {
            return List.copyOf(kenhDaPhat);
        }

        @Override
        public void publish(String channel, String message) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("Redis mất kết nối trong thử nghiệm");
            }
            kenhDaPhat.add(channel);
            delegate.publish(channel, message);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CauHinhKiemTra {

        @Bean
        @Primary
        ControllableOutboxPublisher controllableOutboxPublisher(StringRedisTemplate redisTemplate) {
            return new ControllableOutboxPublisher(new RedisOutboxPublisher(redisTemplate));
        }
    }
}
