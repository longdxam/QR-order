package com.qros.ordering.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qros.ordering.api.AffectedOpenOrder;
import com.qros.ordering.api.AffectedOpenOrder.AffectedOrderLine;
import com.qros.ordering.api.OpenOrderImpactFacade;
import com.qros.ordering.domain.CustomerOrder;
import com.qros.ordering.domain.OrderLine;
import com.qros.ordering.domain.OrderLineOption;
import com.qros.ordering.repository.CustomerOrderRepository;
import com.qros.ordering.repository.OrderLineOptionRepository;
import com.qros.ordering.repository.OrderLineRepository;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class OpenOrderImpactFacadeImpl implements OpenOrderImpactFacade {

    private static final List<String> OPEN_ORDER_STATUSES = List.of("PENDING", "CONFIRMED", "PREPARING", "READY");
    private static final Set<String> AFFECTABLE_LINE_STATUSES = Set.of("PENDING", "CONFIRMED", "PREPARING");

    private final CustomerOrderRepository orderRepository;
    private final OrderLineRepository lineRepository;
    private final OrderLineOptionRepository optionRepository;

    public OpenOrderImpactFacadeImpl(CustomerOrderRepository orderRepository, OrderLineRepository lineRepository,
            OrderLineOptionRepository optionRepository) {
        this.orderRepository = orderRepository;
        this.lineRepository = lineRepository;
        this.optionRepository = optionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AffectedOpenOrder> findAffected(UUID storeId, List<UUID> variantIds,
            List<UUID> optionChoiceIds) {
        List<CustomerOrder> orders = orderRepository
                .findByStoreIdAndStatusInOrderByPlacedAtAsc(storeId, OPEN_ORDER_STATUSES);
        if (orders.isEmpty()) return List.of();

        List<OrderLine> lines = lineRepository.findByOrderIdInOrderByOrderId(
                orders.stream().map(CustomerOrder::getId).toList()).stream()
                .filter(line -> AFFECTABLE_LINE_STATUSES.contains(line.getStatus()))
                .toList();
        Map<UUID, Set<UUID>> optionIdsByLine = lines.isEmpty() ? Map.of()
                : optionRepository.findByOrderLineIdIn(lines.stream().map(OrderLine::getId).toList()).stream()
                        .collect(Collectors.groupingBy(OrderLineOption::getOrderLineId,
                                Collectors.mapping(OrderLineOption::getOptionChoiceId, Collectors.toSet())));
        Set<UUID> affectedVariants = Set.copyOf(variantIds);
        Set<UUID> affectedOptions = Set.copyOf(optionChoiceIds);
        Map<UUID, List<OrderLine>> affectedByOrder = lines.stream()
                .filter(line -> affectedVariants.contains(line.getMenuVariantId())
                        || optionIdsByLine.getOrDefault(line.getId(), Set.of()).stream().anyMatch(affectedOptions::contains))
                .collect(Collectors.groupingBy(OrderLine::getOrderId, LinkedHashMap::new, Collectors.toList()));

        return orders.stream().filter(order -> affectedByOrder.containsKey(order.getId()))
                .map(order -> new AffectedOpenOrder(order.getId(), order.getSessionId(),
                        affectedByOrder.get(order.getId()).stream()
                                .map(line -> new AffectedOrderLine(line.getId(), line.getItemName(), line.getLineTotal()))
                                .toList()))
                .toList();
    }
}
