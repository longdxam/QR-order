package com.qros.ordering.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.ordering.domain.OrderLine;

public interface OrderLineRepository extends JpaRepository<OrderLine, UUID> {

    List<OrderLine> findByOrderIdInOrderByOrderId(List<UUID> orderIds);

    List<OrderLine> findByOrderId(UUID orderId);
}
