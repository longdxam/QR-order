package com.qros.shared.realtime;

import java.security.Principal;
import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

final class RealtimeAccess {

    private static final String KDS_PREFIX = "/topic/kds/";

    private RealtimeAccess() {
    }

    static UUID requireKdsStore(Principal principal, String address) {
        if (address == null || !address.startsWith(KDS_PREFIX)) {
            throw new IllegalArgumentException("Kênh phát lại không hợp lệ");
        }
        UUID storeId;
        try {
            storeId = UUID.fromString(address.substring(KDS_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Chi nhánh trong kênh không hợp lệ", exception);
        }
        Jwt jwt = jwtOf(principal);
        Collection<String> stores = jwt.getClaimAsStringList("stores");
        Collection<String> permissions = jwt.getClaimAsStringList("permissions");
        if (stores == null || (!stores.contains("*") && !stores.contains(storeId.toString()))
                || permissions == null || !permissions.contains("order:read:store")) {
            throw new IllegalArgumentException("Không có quyền theo dõi kênh KDS này");
        }
        return storeId;
    }

    private static Jwt jwtOf(Principal principal) {
        if (principal instanceof Authentication authentication && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalArgumentException("Phiên WebSocket chưa được xác thực");
    }
}
