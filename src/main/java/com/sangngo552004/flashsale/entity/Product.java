package com.sangngo552004.flashsale.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    private Long id;

    private String name;

    /**
     * Main persistent stock in MySQL.
     * During an active flash sale, the flash-sale allocation is moved to Redis and this
     * field keeps only the non-flash-sale stock balance in MySQL.
     */
    private Integer stock;

    private boolean isFlashSale;

    @Version
    private Long version;
}
