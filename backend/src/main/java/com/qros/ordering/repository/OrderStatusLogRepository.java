package com.qros.ordering.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.ordering.domain.OrderStatusLog;

public interface OrderStatusLogRepository extends JpaRepository<OrderStatusLog, Long> {
}
