package com.qros.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** {@code NFR-OBS-03}, {@code OPEN-10}: log chỉ mang storeId đã được JWT cho phép. */
class StoreMdcFilterTest {

    private final StoreMdcFilter filter = new StoreMdcFilter();

    @AfterEach
    void donDep() {
        MDC.remove(StoreMdcFilter.MDC_KEY);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void nfrObs03_datStoreDaDuocClaimXacThucVaXoaSauRequest() throws Exception {
        UUID storeId = UUID.randomUUID();
        authenticate(List.of(storeId.toString()));
        String[] inChain = new String[1];

        filter.doFilter(request(storeId.toString()), new MockHttpServletResponse(), (req, res) ->
                inChain[0] = MDC.get(StoreMdcFilter.MDC_KEY));

        assertThat(inChain[0]).isEqualTo(storeId.toString());
        assertThat(MDC.get(StoreMdcFilter.MDC_KEY)).isNull();
    }

    @Test
    void nfrObs03_boQuaHeaderGiaKhongThuocStoreHoacKhongHopLe() throws Exception {
        UUID allowed = UUID.randomUUID();
        authenticate(List.of(allowed.toString()));

        for (String header : List.of(UUID.randomUUID().toString(), "not-a-uuid", allowed.toString().toUpperCase())) {
            String[] inChain = new String[1];
            filter.doFilter(request(header), new MockHttpServletResponse(), (req, res) ->
                    inChain[0] = MDC.get(StoreMdcFilter.MDC_KEY));
            assertThat(inChain[0]).as("header %s không được tin", header).isNull();
        }
    }

    @Test
    void nfrObs03_claimWildcardChiNhanUUIDChuan() throws Exception {
        UUID storeId = UUID.randomUUID();
        authenticate(List.of("*"));
        String[] inChain = new String[1];

        filter.doFilter(request(storeId.toString()), new MockHttpServletResponse(), (req, res) ->
                inChain[0] = MDC.get(StoreMdcFilter.MDC_KEY));

        assertThat(inChain[0]).isEqualTo(storeId.toString());
    }

    private static MockHttpServletRequest request(String storeId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/staff/orders");
        request.addHeader(StoreMdcFilter.HEADER, storeId);
        return request;
    }

    private static void authenticate(List<String> stores) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "EdDSA")
                .subject("actor-id")
                .claim("stores", stores)
                .build();
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
