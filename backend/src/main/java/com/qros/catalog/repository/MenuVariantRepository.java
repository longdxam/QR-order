package com.qros.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.catalog.domain.MenuVariant;

public interface MenuVariantRepository extends JpaRepository<MenuVariant, UUID> {

    List<MenuVariant> findByMenuItemIdInOrderByDisplayOrder(List<UUID> menuItemIds);

    List<MenuVariant> findByMenuItemIdOrderByDisplayOrder(UUID menuItemId);
}
