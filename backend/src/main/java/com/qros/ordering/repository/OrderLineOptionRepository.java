package com.qros.ordering.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.ordering.domain.OrderLineOption;
import com.qros.ordering.domain.OrderLineOptionId;

public interface OrderLineOptionRepository extends JpaRepository<OrderLineOption, OrderLineOptionId> {

    List<OrderLineOption> findByOrderLineIdIn(List<UUID> orderLineIds);
}
