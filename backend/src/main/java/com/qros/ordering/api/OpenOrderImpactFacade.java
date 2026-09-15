package com.qros.ordering.api;

import java.util.List;
import java.util.UUID;

/** Cổng đọc hẹp để inventory tìm các đơn đang chờ; không lộ entity của ordering. */
public interface OpenOrderImpactFacade {

    List<AffectedOpenOrder> findAffected(UUID storeId, List<UUID> variantIds, List<UUID> optionChoiceIds);
}
