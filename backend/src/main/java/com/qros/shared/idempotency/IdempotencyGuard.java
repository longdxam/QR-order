package com.qros.shared.idempotency;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;

import tools.jackson.databind.ObjectMapper;

/**
 * Chạy một thao tác có tác dụng phụ đúng một lần cho mỗi {@code Idempotency-Key}
 * ({@code FR-CUS-09}, {@code TM-ORD-02}).
 *
 * <p>Ba hành vi, đúng theo tiêu chí nghiệm thu của thẻ:
 * <ul>
 *   <li>cùng khoá và cùng nội dung trong 24 giờ — trả lại nguyên kết quả cũ, không chạy lại;</li>
 *   <li>cùng khoá nhưng khác nội dung — báo lỗi, vì trả kết quả của một yêu cầu khác còn tệ hơn
 *       là từ chối;</li>
 *   <li>hai yêu cầu song song — chỉ một lượt chạy, lượt còn lại chờ rồi nhận kết quả của lượt đầu.</li>
 * </ul>
 *
 * <p>Toàn bộ nằm trong giao dịch nghiệp vụ của lượt gọi, nên khoá và tác dụng phụ commit cùng nhau.
 * Nếu nghiệp vụ hỏng thì khoá cũng biến mất và client thử lại được — một khoá "chết" mắc kẹt sau
 * lần lỗi đầu sẽ chặn khách đặt món cho tới khi hết hạn 24 giờ.
 *
 * <p>Cửa chờ ở lượt song song là cố ý: lượt thứ hai bị PostgreSQL giữ tới khi lượt đầu commit, rồi
 * đọc được kết quả. Cách khác là trả ngay {@code 409 đang xử lý}, nhưng như vậy đẩy việc thử lại
 * về phía khách đúng lúc mạng của khách vừa chập chờn.
 */
public class IdempotencyGuard {

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final IdempotencyProperties properties;
    private final Clock clock;

    public IdempotencyGuard(IdempotencyRepository repository, ObjectMapper objectMapper,
            IdempotencyProperties properties, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param bodyType kiểu thân phản hồi, dùng khi đọc lại kết quả cũ từ cơ sở dữ liệu.
     * @param action   thao tác thật; chỉ chạy ở lượt đầu tiên của khoá.
     */
    public <T> IdempotentResponse<T> execute(IdempotencyRequest request, Class<T> bodyType,
            Supplier<IdempotentResponse<T>> action) {

        Objects.requireNonNull(request, "request không được null");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Khoá idempotency phải nằm trong cùng giao dịch với tác dụng phụ: " + request.scope());
        }

        Instant now = Instant.now(clock);
        String fingerprint = RequestFingerprint.of(request);
        boolean gianhDuoc = repository.claim(request.key(), request.scope(), request.sessionId(),
                fingerprint, now, now.plus(properties.retention())) == 1;

        if (gianhDuoc) {
            IdempotentResponse<T> response = action.get();
            repository.complete(request.key(), response.status(),
                    objectMapper.writeValueAsString(response.body()));
            return response;
        }

        return phatLai(request, bodyType, fingerprint);
    }

    private <T> IdempotentResponse<T> phatLai(IdempotencyRequest request, Class<T> bodyType,
            String fingerprint) {

        IdempotencyEntity daLuu = repository.findById(request.key()).orElseThrow(() ->
                new IllegalStateException("Khoá idempotency biến mất giữa hai câu lệnh: " + request.key()));

        if (!fingerprint.equals(daLuu.getRequestHash())) {
            throw new QrosException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Khoá idempotency này đã dùng cho một yêu cầu khác");
        }
        if (daLuu.getResponseStatus() == null) {
            // Khoá và kết quả commit cùng một giao dịch, nên trạng thái rỗng nghĩa là dữ liệu hỏng.
            throw new IllegalStateException("Khoá idempotency không có kết quả đã lưu: " + request.key());
        }

        return new IdempotentResponse<>(daLuu.getResponseStatus(),
                objectMapper.readValue(daLuu.getResponseBody(), bodyType), true);
    }
}
