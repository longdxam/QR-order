package com.qros.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.catalog.domain.PriceSchedule;

public interface PriceScheduleRepository extends JpaRepository<PriceSchedule, UUID> {

    List<PriceSchedule> findByMenuVariantIdIn(List<UUID> menuVariantIds);
}
