package com.qros.venue.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Khoá phức hợp của {@link SessionDevice} — khớp {@code PRIMARY KEY (session_id, device_id)}. */
public final class SessionDeviceId implements Serializable {

    private UUID sessionId;
    private UUID deviceId;

    public SessionDeviceId() {
        // JPA
    }

    public SessionDeviceId(UUID sessionId, UUID deviceId) {
        this.sessionId = sessionId;
        this.deviceId = deviceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionDeviceId that)) {
            return false;
        }
        return Objects.equals(sessionId, that.sessionId) && Objects.equals(deviceId, that.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, deviceId);
    }
}
