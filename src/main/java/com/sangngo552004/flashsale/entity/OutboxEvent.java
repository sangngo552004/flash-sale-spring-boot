package com.sangngo552004.flashsale.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Data
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateType;

    private String aggregateId;

    private String type;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String payload;

    private String status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // Dùng để tracking retry
    private int retryCount = 0;

    private LocalDateTime sentAt;
}
