package com.qros.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.catalog.domain.MenuItemEntity;

public interface MenuItemRepository extends JpaRepository<MenuItemEntity, UUID> {

    List<MenuItemEntity> findByStoreIdAndPublishedTrueAndManuallyDisabledFalseOrderByDisplayOrder(UUID storeId);

    /** Bất biến số 7: kiểm quyền sở hữu ở cấp đối tượng — chỉ trả về nếu đúng chi nhánh trong phiên. */
    Optional<MenuItemEntity> findByIdAndStoreId(UUID id, UUID storeId);

    List<MenuItemEntity> findByIdInAndStoreId(List<UUID> ids, UUID storeId);
}
