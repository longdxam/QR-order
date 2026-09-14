package com.qros.venue.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.venue.domain.QrSigningKey;

/** {@code kid} là khoá chính — {@link #findById} đã đủ, không cần thêm phương thức truy vấn. */
public interface QrSigningKeyRepository extends JpaRepository<QrSigningKey, String> {
}
