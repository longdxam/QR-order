package com.qros.inventory.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.qros.inventory.domain.InventoryIngredient;

import jakarta.persistence.LockModeType;

public interface InventoryIngredientRepository extends JpaRepository<InventoryIngredient, UUID> {

    /** Khoá dòng làm cho hai thao tác báo hết đồng thời trở thành một chuyển đổi idempotent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InventoryIngredient> findByIdAndStoreId(UUID id, UUID storeId);
}
