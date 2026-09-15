package com.qros.shared.event;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Dựng phong bì {@code EventEnvelope} của {@code docs/api/asyncapi.yaml} từ một dòng outbox.
 *
 * <p>Hợp đồng gộp phong bì và thân sự kiện thành **một** đối tượng phẳng ({@code allOf}), nên phần
 * thân đã lưu được trải ra trước rồi mới tới các trường phong bì — trường phong bì thắng nếu trùng
 * tên, vì {@code seq} và {@code occurredAt} là của máy chủ, không phải của nơi tạo sự kiện.
 */
public final class OutboxEnvelope {

    private static final TypeReference<Map<String, Object>> BODY_TYPE = new TypeReference<>() {
    };

    private OutboxEnvelope() {
    }

    public static String json(ObjectMapper objectMapper, OutboxEntity event) {
        Map<String, Object> message = new LinkedHashMap<>(
                objectMapper.readValue(event.getPayload(), BODY_TYPE));

        message.put("type", event.getType());
        // seq chính là khoá chính của dòng outbox — thứ tự tuyệt đối để client phát lại.
        message.put("seq", event.getId());
        message.put("occurredAt", event.getOccurredAt().toString());
        if (event.getTraceId() != null) {
            message.put("traceId", event.getTraceId());
        }
        return objectMapper.writeValueAsString(message);
    }
}
