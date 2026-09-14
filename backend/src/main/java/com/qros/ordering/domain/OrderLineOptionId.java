package com.qros.ordering.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class OrderLineOptionId implements Serializable {

    private UUID orderLineId;
    private UUID optionChoiceId;

    public OrderLineOptionId() {
        // JPA
    }

    public OrderLineOptionId(UUID orderLineId, UUID optionChoiceId) {
        this.orderLineId = orderLineId;
        this.optionChoiceId = optionChoiceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderLineOptionId that)) {
            return false;
        }
        return Objects.equals(orderLineId, that.orderLineId) && Objects.equals(optionChoiceId, that.optionChoiceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderLineId, optionChoiceId);
    }
}
