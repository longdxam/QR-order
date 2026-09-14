package com.qros.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Đối chiếu bảng mã lỗi với {@code docs/api/openapi.yaml} — hợp đồng là nguồn sự thật. */
class ErrorCodeTest {

    /** Mã HTTP ghi trong phần responses của từng operation trong hợp đồng. */
    private static final Map<ErrorCode, HttpStatus> THEO_HOP_DONG = Map.ofEntries(
            Map.entry(ErrorCode.QR_INVALID_SIGNATURE, HttpStatus.UNAUTHORIZED),
            Map.entry(ErrorCode.QR_OTP_EXPIRED, HttpStatus.UNAUTHORIZED),
            Map.entry(ErrorCode.STORE_CLOSED, HttpStatus.FORBIDDEN),
            Map.entry(ErrorCode.TABLE_SESSION_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.TABLE_SESSION_EXPIRED, HttpStatus.UNAUTHORIZED),
            Map.entry(ErrorCode.PRICE_NOT_ACCEPTED, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.PRICE_CHANGED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.ITEM_SOLD_OUT, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.ORDER_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS),
            Map.entry(ErrorCode.INVALID_TRANSITION, HttpStatus.UNPROCESSABLE_CONTENT),
            Map.entry(ErrorCode.VERSION_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.REASON_REQUIRED, HttpStatus.UNPROCESSABLE_CONTENT),
            Map.entry(ErrorCode.PAYMENT_ALREADY_SETTLED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.WEBHOOK_SIGNATURE_INVALID, HttpStatus.UNAUTHORIZED),
            Map.entry(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED),
            Map.entry(ErrorCode.ACCOUNT_LOCKED, HttpStatus.LOCKED),
            Map.entry(ErrorCode.REFRESH_REUSED, HttpStatus.UNAUTHORIZED),
            Map.entry(ErrorCode.ASSISTANT_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS),
            Map.entry(ErrorCode.AI_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE));

    @Test
    void moiMaAnhXaDungMaHttpGhiTrongHopDong() {
        THEO_HOP_DONG.forEach((maLoi, maHttp) ->
                assertThat(maLoi.status()).as("mã HTTP của %s", maLoi).isEqualTo(maHttp));
    }

    @Test
    void moiMaDeuLaLoiVaCoTieuDeTiengViet() {
        for (ErrorCode maLoi : ErrorCode.values()) {
            assertThat(maLoi.status().isError()).as("%s phải là 4xx hoặc 5xx", maLoi).isTrue();
            assertThat(maLoi.title()).isNotBlank();
            assertThat(maLoi.type()).hasToString("urn:qros:error:" + maLoi.name());
        }
    }

    @Test
    void loiGiaoVanDuocSuyRaTuMaHttp() {
        assertThat(ErrorCode.forStatus(HttpStatus.NOT_FOUND)).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(ErrorCode.forStatus(HttpStatus.UNAUTHORIZED)).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(ErrorCode.forStatus(HttpStatus.PRECONDITION_REQUIRED)).isEqualTo(ErrorCode.REQUEST_REJECTED);
        assertThat(ErrorCode.forStatus(HttpStatus.BAD_GATEWAY)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void tieuDeKhongLoChiTietNoiBo() {
        // TM-OPS-02: không tên class, không câu truy vấn, không đường dẫn.
        for (ErrorCode maLoi : ErrorCode.values()) {
            assertThat(maLoi.title().toLowerCase())
                    .as("tiêu đề của %s", maLoi)
                    .doesNotContain("exception", "sql", "com.qros", "java.", "select ");
        }
        assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name).distinct().count())
                .isEqualTo(ErrorCode.values().length);
    }
}
