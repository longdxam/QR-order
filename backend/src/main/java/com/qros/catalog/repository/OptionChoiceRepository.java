package com.qros.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.catalog.domain.OptionChoice;

public interface OptionChoiceRepository extends JpaRepository<OptionChoice, UUID> {

    List<OptionChoice> findByOptionGroupIdInOrderByDisplayOrder(List<UUID> optionGroupIds);
}
