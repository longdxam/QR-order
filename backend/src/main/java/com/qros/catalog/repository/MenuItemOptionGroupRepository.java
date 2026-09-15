package com.qros.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.catalog.domain.MenuItemOptionGroup;
import com.qros.catalog.domain.MenuItemOptionGroupId;

public interface MenuItemOptionGroupRepository extends JpaRepository<MenuItemOptionGroup, MenuItemOptionGroupId> {

    List<MenuItemOptionGroup> findByMenuItemIdInOrderByDisplayOrder(List<UUID> menuItemIds);

    List<MenuItemOptionGroup> findByOptionGroupIdIn(List<UUID> optionGroupIds);
}
