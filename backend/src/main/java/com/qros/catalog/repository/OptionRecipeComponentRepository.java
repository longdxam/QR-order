package com.qros.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.catalog.domain.OptionRecipeComponent;
import com.qros.catalog.domain.OptionRecipeComponentId;

public interface OptionRecipeComponentRepository
        extends JpaRepository<OptionRecipeComponent, OptionRecipeComponentId> {

    List<OptionRecipeComponent> findByOptionChoiceIdIn(List<UUID> optionChoiceIds);

    List<OptionRecipeComponent> findByIngredientId(UUID ingredientId);
}
