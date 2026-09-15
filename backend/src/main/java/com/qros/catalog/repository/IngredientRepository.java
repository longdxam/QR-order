package com.qros.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.catalog.domain.Ingredient;

public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {

    List<Ingredient> findByStoreIdAndSoldOutTrue(UUID storeId);

    Optional<Ingredient> findByIdAndStoreId(UUID id, UUID storeId);
}
