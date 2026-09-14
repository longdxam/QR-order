package com.qros.audit.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.qros.audit.domain.AuditEvent;

/**
 * Cố tình kế thừa {@link Repository} trơn — marker interface không có phương thức nào — chứ
 * không phải {@code JpaRepository}/{@code CrudRepository}. Hai interface đó có sẵn
 * {@code save}/{@code delete}/{@code deleteById}; kế thừa chúng là để hở đúng cánh cửa mà
 * {@code TM-REP-01} yêu cầu phải đóng. Chỉ có đúng những gì khai báo tường minh dưới đây, và
 * {@link AuditEventWriter#append} là cách DUY NHẤT ghi — không có cách nào sửa hay xoá.
 *
 * <p>{@code ModuleBoundaryTest} giữ bất biến này: không class nào trong {@code audit.repository}
 * được phụ thuộc {@code JpaRepository}/{@code CrudRepository}/{@code PagingAndSortingRepository}.
 */
public interface AuditEventRepository extends Repository<AuditEvent, Long>, AuditEventWriter {

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, UUID entityId);

    List<AuditEvent> findByActorIdOrderByOccurredAtDesc(UUID actorId);
}
