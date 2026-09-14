package com.qros.shared.money;

/**
 * Đơn vị tiền tệ được hệ thống hỗ trợ.
 *
 * <p>Danh sách này bám đúng enum {@code Money.currency} trong {@code docs/api/openapi.yaml};
 * thêm đơn vị mới phải sửa hợp đồng trước, sinh lại code, rồi mới bổ sung ở đây.
 */
public enum Currency {

    /** Việt Nam đồng. Đơn vị nhỏ nhất là 1 đồng, không có phần lẻ. */
    VND
}
