package com.qros.ordering.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_line_option")
@IdClass(OrderLineOptionId.class)
public class OrderLineOption {

    @Id
    @Column(name = "order_line_id", nullable = false)
    private UUID orderLineId;

    @Id
    @Column(name = "option_choice_id", nullable = false)
    private UUID optionChoiceId;

    @Column(name = "option_name", nullable = false)
    private String optionName;

    @Column(name = "surcharge", nullable = false)
    private long surcharge;

    protected OrderLineOption() {
        // JPA
    }

    public OrderLineOption(UUID orderLineId, UUID optionChoiceId, String optionName, long surcharge) {
        this.orderLineId = orderLineId;
        this.optionChoiceId = optionChoiceId;
        this.optionName = optionName;
        this.surcharge = surcharge;
    }

    public UUID getOrderLineId() {
        return orderLineId;
    }

    public UUID getOptionChoiceId() {
        return optionChoiceId;
    }

    public String getOptionName() {
        return optionName;
    }

    public long getSurcharge() {
        return surcharge;
    }
}
