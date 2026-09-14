package com.qros.venue.api;

import java.util.UUID;

/** DTO duy nhất module khác cần biết để gọi vào {@code venue} — không tham chiếu {@code Store} (entity). */
public record StoreView(UUID id, String name, String timezone) {
}
