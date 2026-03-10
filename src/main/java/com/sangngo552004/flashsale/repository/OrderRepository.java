package com.sangngo552004.flashsale.repository;

import com.sangngo552004.flashsale.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    boolean existsByIdempotencyKey(String key);

    @Query(value = """
        SELECT *
        FROM orders
        WHERE status = 'PENDING'
          AND expire_time <= :now
        ORDER BY expire_time ASC
        LIMIT 50
        FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<Order> findExpiredPendingOrdersForUpdate(@Param("now") LocalDateTime now);
}
