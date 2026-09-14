package com.qros.audit.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Một thao tác nhạy cảm cần ghi vết — {@code FR-MGT-12}, {@code FR-PAY-07}, {@code TM-REP-01}.
 *
 * <p>Đây là DTO duy nhất module khác cần biết để gọi vào {@code audit}; không có tham chiếu nào
 * tới {@code AuditEvent} (entity) ở đây, đúng luật "api chỉ trao đổi DTO".
 *
 * @param action     mã hành động ổn định, ví dụ {@code "payment.refund"}, {@code "menu.price.update"}.
 * @param entityType loại đối tượng bị tác động, ví dụ {@code "Order"}, {@code "MenuItem"}.
 * @param entityId   định danh đối tượng; {@code null} nếu hành động không gắn với một bản ghi cụ thể.
 * @param actorId    người thực hiện; {@code null} cho hành động hệ thống tự động.
 * @param actorRole  vai trò của người thực hiện tại thời điểm hành động — chụp lại, không tra cứu
 *                   lại sau này, vì vai trò có thể đổi nhưng lịch sử thì không.
 * @param storeId    phạm vi chi nhánh; {@code null} nghĩa là hành động ở phạm vi tổ chức.
 * @param before     trạng thái trước, tuần tự hoá thành JSON lúc ghi; {@code null} nếu không áp dụng
 *                   (ví dụ hành động tạo mới).
 * @param after      trạng thái sau, cùng quy tắc với {@code before}.
 * @param reason     lý do — bắt buộc theo nghiệp vụ cho một số hành động (ví dụ hoàn tiền), nhưng
 *                   kiểm tra bắt buộc đó là việc của module gọi vào đây, không phải của {@code audit}.
 */
public record AuditEntry(
        String action,
        String entityType,
        UUID entityId,
        UUID actorId,
        String actorRole,
        UUID storeId,
        Object before,
        Object after,
        String reason) {

    public AuditEntry {
        Objects.requireNonNull(action, "action không được null");
        Objects.requireNonNull(entityType, "entityType không được null");
    }
}
