package com.qros.shared.event;

import java.time.Instant;
import java.util.UUID;

import com.qros.shared.id.UuidV7;

/**
 * Sự kiện miền, được ghi vào outbox trong cùng giao dịch với dữ liệu nghiệp vụ ({@code ADR-05}).
 *
 * <p>Các trường ở đây ánh xạ thẳng sang cột của bảng {@code outbox_event} và sang phong bì
 * {@code EventEnvelope} trong {@code docs/api/asyncapi.yaml}. Sửa hình dạng sự kiện thì sửa
 * AsyncAPI trước.
 *
 * <p>{@link #storeId()} và {@link #sessionId()} quyết định kênh phát: sự kiện của một phiên bàn
 * không bao giờ được đẩy sang kênh chi nhánh, vì kênh chi nhánh có nhân viên của mọi bàn nghe.
 */
public interface DomainEvent {

    /** Sinh định danh cho sự kiện mới. Dùng UUIDv7 để thứ tự sinh khớp thứ tự thời gian. */
    static UUID newEventId() {
        return UuidV7.generate();
    }

    /** Định danh ổn định qua mọi lần phát lại — cơ sở để client khử trùng lặp ({@code TM-EVT-01}). */
    UUID eventId();

    /** Tên sự kiện trong AsyncAPI, ví dụ {@code OrderPlaced}. */
    String type();

    /** Loại aggregate sinh ra sự kiện, ví dụ {@code Order}. */
    String aggregateType();

    UUID aggregateId();

    /** Chi nhánh nghe được sự kiện; {@code null} nếu sự kiện không thuộc chi nhánh nào. */
    UUID storeId();

    /** Phiên bàn nghe được sự kiện; {@code null} nếu không thuộc phiên nào. */
    UUID sessionId();

    Instant occurredAt();

    /**
     * Phần thân nghiệp vụ, sẽ được tuần tự hoá thành JSON. Chỉ chứa trường mà hợp đồng khai báo:
     * outbox là dữ liệu rời khỏi máy chủ, không phải chỗ đổ trạng thái nội bộ.
     */
    Object payload();
}
