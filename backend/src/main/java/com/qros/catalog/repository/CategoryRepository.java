package com.qros.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.catalog.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByStoreIdAndActiveTrueOrderByDisplayOrder(UUID storeId);
}
