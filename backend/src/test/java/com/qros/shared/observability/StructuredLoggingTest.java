package com.qros.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;

import tools.jackson.databind.ObjectMapper;

/**
 * {@code NFR-OBS-03}: log JSON có cấu trúc, mọi dòng có {@code correlationId}/{@code actorId} khi
 * có, và dữ liệu nhạy cảm bị che ở tầng appender.
 *
 * <p>Gọi thẳng {@code encoder.encode(event)} của appender {@code CONSOLE_JSON} đã nạp từ
 * {@code logback-spring.xml} thật — không dựng lại cấu hình riêng cho test, và không bắt
 * {@code System.out} (appender Logback cache tham chiếu stream lúc khởi động, bắt sau đó không
 * thấy gì). Đây là cách kiểm đúng pipeline sản xuất mà không phụ thuộc cách nó ghi ra đâu.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StructuredLoggingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void log_cheMatKhauTokenPii_vaMangDuTruongBatBuoc() {
        MDC.put("correlationId", "test-correlation-nfr-obs-03");
        MDC.put("storeId", "0198f0a1-4b2c-7def-8123-456789abcdef");
        try {
            Encoder<ILoggingEvent> encoder = consoleJsonEncoder();

            LoggingEvent event = new LoggingEvent(
                    Logger.class.getName(), rootLogger(), Level.INFO,
                    "Đăng nhập thất bại cho user@qros.test: password=hunter2, "
                            + "token=eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiJ4In0.c2ln-that",
                    null, null);

            String json = new String(encoder.encode(event), StandardCharsets.UTF_8);
            Map<String, Object> parsed = JSON.readValue(json, Map.class);

            String message = (String) parsed.get("message");
            assertThat(message).doesNotContain("hunter2")
                    .doesNotContain("user@qros.test")
                    .doesNotContain("eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiJ4In0.c2ln-that")
                    .contains("***");

            assertThat(parsed).containsEntry("level", "INFO")
                    .containsEntry("logger_name", rootLogger().getName())
                    .containsEntry("correlationId", "test-correlation-nfr-obs-03")
                    .containsEntry("storeId", "0198f0a1-4b2c-7def-8123-456789abcdef");
        } finally {
            MDC.remove("correlationId");
            MDC.remove("storeId");
        }
    }

    @Test
    void log_khongCoGiCanCheThiGiuNguyen() {
        Encoder<ILoggingEvent> encoder = consoleJsonEncoder();
        LoggingEvent event = new LoggingEvent(
                Logger.class.getName(), rootLogger(), Level.INFO,
                "Đăng nhập thành công", null, null);

        String json = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(json).contains("Đăng nhập thành công");
    }

    @SuppressWarnings("unchecked")
    private static Encoder<ILoggingEvent> consoleJsonEncoder() {
        Object appender = rootLogger().getAppender("CONSOLE_JSON");
        assertThat(appender).as("logback-spring.xml phải khai báo appender CONSOLE_JSON").isNotNull();
        return ((OutputStreamAppender<ILoggingEvent>) appender).getEncoder();
    }

    private static Logger rootLogger() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return context.getLogger(Logger.ROOT_LOGGER_NAME);
    }
}
