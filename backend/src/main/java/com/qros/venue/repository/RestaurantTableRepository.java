package com.qros.venue.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.venue.domain.RestaurantTable;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {

    /** Cross-check chi nhánh sau khi chữ ký QR đã xác minh — chặn "dùng QR của chi nhánh khác". */
    Optional<RestaurantTable> findByIdAndStoreId(UUID id, UUID storeId);

    /**
     * {@code short_code} chỉ {@code UNIQUE} trong phạm vi một chi nhánh ở CSDL, không phải toàn hệ
     * thống — khách nhập mã 6 ký tự không tự biết chi nhánh nào. Trả cả danh sách để tầng service
     * tự quyết định: đúng một kết quả thì hợp lệ, còn lại (0 hoặc trùng giữa hai chi nhánh) coi như
     * mã sai, không đoán bừa.
     */
    List<RestaurantTable> findByShortCode(String shortCode);
}
