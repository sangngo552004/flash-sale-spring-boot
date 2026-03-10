package com.sangngo552004.flashsale.repository;

import com.sangngo552004.flashsale.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Lấy các event PENDING cũ nhất.
     * FOR UPDATE SKIP LOCKED:
     * - Khóa các dòng đọc được để thread khác không đọc trùng.
     * - Nếu dòng nào đang bị khóa bởi thread khác, bỏ qua (SKIP) và lấy dòng tiếp theo.
     * -> Giúp chạy nhiều Worker song song mà không dẫm chân nhau.
     * (Cú pháp này chạy tốt trên MySQL 8.0+ và PostgreSQL)
     */
    @Query(value = """
        SELECT * FROM outbox_event 
        WHERE status = 'PENDING' 
        ORDER BY created_at ASC 
        LIMIT 50 
        FOR UPDATE SKIP LOCKED 
    """, nativeQuery = true)
    List<OutboxEvent> findEventsToProcess();

    // Query fallback nếu database cũ không hỗ trợ SKIP LOCKED
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(String status);
}
