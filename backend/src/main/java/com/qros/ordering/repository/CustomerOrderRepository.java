package com.qros.ordering.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.qros.ordering.domain.CustomerOrder;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    /** Bất biến số 7: đơn phải thuộc đúng phiên đang gọi. */
    Optional<CustomerOrder> findByIdAndSessionId(UUID id, UUID sessionId);

    List<CustomerOrder> findBySessionIdOrderByPlacedAtDesc(UUID sessionId);

    long countBySessionId(UUID sessionId);

    List<CustomerOrder> findByStoreIdAndStatusInOrderByPlacedAtAsc(UUID storeId, List<String> statuses);

    /**
     * Khoá tư vấn phạm vi giao dịch theo chi nhánh — dùng trước khi tính số thứ tự hàng ngày cho
     * {@code short_code}, cùng kỹ thuật {@code venue.TableSessionRepository.khoaTheoBan} (tránh hai
     * đơn cùng chi nhánh cùng lúc tính trùng số thứ tự).
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(CAST(:storeId AS text))::bigint)", nativeQuery = true)
    void khoaTheoChiNhanh(@Param("storeId") UUID storeId);

    /** Số đơn đã đặt trong ngày hôm nay theo giờ Việt Nam, dùng để sinh {@code short_code} kế tiếp. */
    @Query(value = """
            SELECT count(*) FROM customer_order
            WHERE store_id = :storeId
              AND (placed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
                  = (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date""", nativeQuery = true)
    long demSoDonHomNay(@Param("storeId") UUID storeId);
}
