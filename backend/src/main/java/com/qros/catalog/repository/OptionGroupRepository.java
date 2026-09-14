package com.qros.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.catalog.domain.OptionGroupEntity;

public interface OptionGroupRepository extends JpaRepository<OptionGroupEntity, UUID> {

    List<OptionGroupEntity> findByIdIn(List<UUID> ids);
}
