package com.qros.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Vân tay yêu cầu — {@code TM-ORD-02}, bất biến số 7. */
class RequestFingerprintTest {

    private static final UUID KEY = UUID.fromString("0198f0a1-4b2c-4def-8123-456789abcdef");
    private static final UUID SESSION = UUID.fromString("0198f0a1-4b2c-7def-8123-000000000001");
    private static final String SCOPE = "guest.createOrder";
    private static final String BODY = "{\"items\":[{\"menuItemId\":\"abc\",\"quantity\":2}]}";

    @Test
    void frCus09_cungYeuCauChoCungVanTay() {
        IdempotencyRequest mot = new IdempotencyRequest(KEY, SCOPE, SESSION, BODY);
        IdempotencyRequest hai = new IdempotencyRequest(KEY, SCOPE, SESSION, BODY);

        assertThat(RequestFingerprint.of(mot)).isEqualTo(RequestFingerprint.of(hai))
                .hasSize(64);
    }

    @Test
    void tmOrd02_doiThanYeuCauLaDoiVanTay() {
        String vanTay = RequestFingerprint.of(new IdempotencyRequest(KEY, SCOPE, SESSION, BODY));

        assertThat(RequestFingerprint.of(new IdempotencyRequest(KEY, SCOPE, SESSION,
                BODY.replace("\"quantity\":2", "\"quantity\":20")))).isNotEqualTo(vanTay);
    }

    @Test
    void tmOrd02_doiThaoTacHoacPhienCungLaDoiVanTay() {
        String vanTay = RequestFingerprint.of(new IdempotencyRequest(KEY, SCOPE, SESSION, BODY));

        // Phiên và thao tác nằm trong vân tay, nên khoá của phiên khác cho ra đúng một loại lỗi
        // với nội dung sai — không có tín hiệu nào để dò xem khoá đó có tồn tại hay không.
        assertThat(RequestFingerprint.of(new IdempotencyRequest(KEY, SCOPE, UUID.randomUUID(), BODY)))
                .isNotEqualTo(vanTay);
        assertThat(RequestFingerprint.of(new IdempotencyRequest(KEY, "guest.createPayment", SESSION, BODY)))
                .isNotEqualTo(vanTay);
    }

    @Test
    void tmOrd02_khongTronRanhGioiGiuaCacTruong() {
        // Nối chuỗi mà không có dấu tách thì "ab"+"c" và "a"+"bc" cho cùng vân tay.
        assertThat(RequestFingerprint.of(new IdempotencyRequest(KEY, "guest.create", SESSION, "Order")))
                .isNotEqualTo(RequestFingerprint.of(
                        new IdempotencyRequest(KEY, "guest.createOrder", SESSION, "")));
    }
}
