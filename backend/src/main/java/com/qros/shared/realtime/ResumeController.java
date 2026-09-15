package com.qros.shared.realtime;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.qros.shared.event.OutboxEnvelope;
import com.qros.shared.event.OutboxRepository;

import tools.jackson.databind.ObjectMapper;

@Controller
@ConditionalOnProperty(name = "spring.datasource.url")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ResumeController {

    private static final Duration REPLAY_WINDOW = Duration.ofMinutes(15);

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public ResumeController(OutboxRepository repository, ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    @MessageMapping("/resume")
    public void resume(ResumeRequest request, Principal principal,
            @Header("simpSessionId") String sessionId) {
        for (ChannelCursor cursor : request.channels()) {
            UUID storeId = RealtimeAccess.requireKdsStore(principal, cursor.address());
            Instant cutoff = Instant.now(clock).minus(REPLAY_WINDOW);
            if (repository.hasExpiredEventsForStore(storeId, cursor.lastSeq(), cutoff)) {
                send(principal, sessionId, objectMapper.writeValueAsString(Map.of(
                        "type", "ResyncRequired", "address", cursor.address(), "reason", "BUFFER_EXPIRED")));
                continue;
            }
            repository.findReplayForStore(storeId, cursor.lastSeq(), cutoff)
                    .forEach(event -> send(principal, sessionId, OutboxEnvelope.json(objectMapper, event)));
        }
    }

    private void send(Principal principal, String sessionId, String body) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
        headers.setSessionId(sessionId);
        headers.setLeaveMutable(true);
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/resume", body,
                headers.getMessageHeaders());
    }

    public record ResumeRequest(List<ChannelCursor> channels) {
    }

    public record ChannelCursor(String address, long lastSeq) {
    }
}
