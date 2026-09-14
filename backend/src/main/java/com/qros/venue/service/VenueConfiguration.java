package com.qros.venue.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class VenueConfiguration {

    /**
     * Luôn cho qua — chỗ giữ chỗ tới khi {@code OPEN-02} chốt ngưỡng và chính sách fail-open/closed
     * thật. Cùng lối {@code shared.security.SecurityZoneConfiguration.defaultTokenVersionValidator}.
     */
    @Bean
    public TableScanRateLimiter permissiveTableScanRateLimiter() {
        return (tableId, clientIp) -> {
            // Cố ý không làm gì — xem javadoc TableScanRateLimiter.
        };
    }
}
