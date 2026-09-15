package com.qros.ordering.controller;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.qros.generated.model.GetKdsQueue200Response;
import com.qros.generated.model.UpdateOrderLineStatusRequest;
import com.qros.ordering.service.KdsService;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;

import jakarta.validation.Valid;

@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
public class KdsController {

    private final KdsService kdsService;

    public KdsController(KdsService kdsService) {
        this.kdsService = kdsService;
    }

    @GetMapping("/api/v1/staff/kds/queue")
    public ResponseEntity<GetKdsQueue200Response> getQueue(
            @RequestHeader("X-Store-Id") UUID storeId,
            @RequestParam(defaultValue = "ALL") String station) {
        return ResponseEntity.ok(kdsService.getQueue(actorId(), storeId, station));
    }

    @PatchMapping("/api/v1/staff/order-lines/{lineId}/status")
    public ResponseEntity<com.qros.generated.model.OrderLine> updateStatus(
            @PathVariable UUID lineId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("X-Store-Id") UUID storeId,
            @RequestHeader("X-Device-Id") UUID deviceId,
            @Valid @RequestBody UpdateOrderLineStatusRequest request) {
        UUID actorId = actorId();
        try {
            return ResponseEntity.ok(kdsService.updateStatus(actorId, storeId, deviceId, lineId,
                    parseVersion(ifMatch), request.getStatus().getValue(), request.getReason()));
        } catch (OptimisticLockingFailureException exception) {
            throw kdsService.versionConflict(kdsService.currentLine(actorId, storeId, lineId));
        }
    }

    private static int parseVersion(String ifMatch) {
        try {
            return Integer.parseInt(ifMatch.replace("\"", "").trim());
        } catch (NumberFormatException exception) {
            throw new QrosException(ErrorCode.VALIDATION_FAILED, "If-Match phải là phiên bản số nguyên");
        }
    }

    private static UUID actorId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            throw new QrosException(ErrorCode.UNAUTHENTICATED);
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new QrosException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
