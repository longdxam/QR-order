package com.qros.audit.repository;

import com.qros.audit.domain.AuditEvent;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Spring Data tự nhận diện lớp này qua quy ước tên {@code <fragment>Impl} và ghép vào
 * {@link AuditEventRepository}. Chỉ {@link EntityManager#persist} — không bao giờ {@code merge}:
 * {@code persist} là INSERT, gọi lại trên một entity đã có khoá chính là lỗi lập trình bị JPA
 * ném ngoại lệ ngay, không âm thầm ghi đè.
 */
public class AuditEventWriterImpl implements AuditEventWriter {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public AuditEvent append(AuditEvent event) {
        entityManager.persist(event);
        return event;
    }
}
