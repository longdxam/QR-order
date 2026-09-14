package com.qros.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.catalog.domain.RecipeComponent;
import com.qros.catalog.domain.RecipeComponentId;

public interface RecipeComponentRepository extends JpaRepository<RecipeComponent, RecipeComponentId> {

    List<RecipeComponent> findByMenuVariantIdIn(List<UUID> menuVariantIds);
}
