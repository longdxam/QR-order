package com.qros.venue.controller;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.qros.generated.api.GuestSessionApi;
import com.qros.generated.model.StartSessionByCodeRequest;
import com.qros.generated.model.StartSessionRequest;
import com.qros.generated.model.TableSession;
import com.qros.generated.model.TableSessionParticipantsInner;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.venue.domain.SessionDevice;
import com.qros.venue.service.TableSessionService;
import com.qros.venue.service.TableSessionService.SessionOutcome;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code FR-CUS-01}, {@code FR-CUS-02}: đổi QR/mã bàn lấy phiên, và tra trạng thái phiên hiện tại.
 *
 * <p>{@link GuestSessionApi} sinh từ {@code openapi.yaml} khai báo hai phương thức không tham số —
 * IP và danh tính (JWT) lấy qua {@link RequestContextHolder}/{@link SecurityContextHolder} ngay
 * trong thân phương thức thay vì tham số, vì chữ ký phải khớp {@code implements} tuyệt đối.
 * {@link HttpServletRequest} không được tiêm qua constructor: bean proxy request-scope đó chỉ tồn
 * tại khi có web context kiểu servlet, còn controller này còn bị nạp ở một số test tích hợp chạy
 * {@code webEnvironment = NONE} (do {@code @ConditionalOnProperty(datasource)} — có thể cùng
 * context với những test đó) — tiêm thẳng sẽ hỏng autowire ngay lúc khởi động.
 *
 * <p>Chỉ nạp khi có {@code DataSource}, cùng lý do {@code TableSessionService} nó phụ thuộc.
 */
@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
public class GuestSessionController implements GuestSessionApi {

    private final TableSessionService tableSessionService;

    public GuestSessionController(TableSessionService tableSessionService) {
        this.tableSessionService = tableSessionService;
    }

    @Override
    public ResponseEntity<TableSession> startTableSession(StartSessionRequest body) {
        SessionOutcome outcome = tableSessionService.startFromQr(
                body.getQrToken(), body.getDeviceId(), body.getNickname(), clientIp());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(outcome, body.getDeviceId()));
    }

    @Override
    public ResponseEntity<TableSession> startTableSessionByCode(StartSessionByCodeRequest body) {
        SessionOutcome outcome = tableSessionService.startFromCode(
                body.getTableCode(), body.getDeviceId(), body.getNickname(), clientIp());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(outcome, body.getDeviceId()));
    }

    private static String clientIp() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest().getRemoteAddr();
    }

    @Override
    public ResponseEntity<TableSession> getCurrentSession() {
        UUID sessionId = currentSessionId();
        SessionOutcome outcome = tableSessionService.currentSession(sessionId);
        return ResponseEntity.ok(toDto(outcome, null));
    }

    private UUID currentSessionId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            throw new QrosException(ErrorCode.TABLE_SESSION_EXPIRED);
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new QrosException(ErrorCode.TABLE_SESSION_EXPIRED);
        }
    }

    private TableSession toDto(SessionOutcome outcome, UUID callerDeviceId) {
        TableSession dto = new TableSession(
                outcome.session().getId(),
                outcome.store().getId(),
                outcome.table().getId(),
                outcome.table().getLabel(),
                outcome.accessToken(),
                OffsetDateTime.ofInstant(outcome.session().getExpiresAt(), ZoneOffset.UTC),
                outcome.joined());
        dto.storeName(outcome.store().getName());
        dto.participants(toParticipants(outcome.participants(), callerDeviceId));
        return dto;
    }

    private static List<TableSessionParticipantsInner> toParticipants(
            List<SessionDevice> devices, UUID callerDeviceId) {
        return devices.stream()
                .map(device -> new TableSessionParticipantsInner()
                        .nickname(device.getNickname())
                        .isSelf(callerDeviceId != null && callerDeviceId.equals(device.getDeviceId())))
                .toList();
    }
}
