package com.qros.shared.observability;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;

import ch.qos.logback.classic.spi.ILoggingEvent;

import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.JsonProvider;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;

/**
 * Thay {@link MessageJsonProvider} mặc định của logstash-logback-encoder: ghi thông điệp đã qua
 * {@link PiiMasker} thay vì nguyên văn — {@code NFR-OBS-03}. Đây là chỗ DUY NHẤT quyết định nội
 * dung trường {@code message} trong log JSON, nên che ở đây có tác dụng bất kể dòng log tới từ
 * đâu trong ứng dụng.
 */
public class MaskedMessageJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent>
        implements JsonProvider<ILoggingEvent> {

    public MaskedMessageJsonProvider() {
        setFieldName(MessageJsonProvider.FIELD_MESSAGE);
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        generator.writeStringField(getFieldName(), PiiMasker.mask(event.getFormattedMessage()));
    }
}
