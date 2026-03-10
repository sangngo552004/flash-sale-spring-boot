package com.sangngo552004.flashsale.repository;

import com.sangngo552004.flashsale.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Product p
        SET p.stock = p.stock - :amount,
            p.version = COALESCE(p.version, 0) + 1
        WHERE p.id = :id
          AND p.isFlashSale = false
          AND p.stock >= :amount
    """)
    int decrementStockForNormalSale(@Param("id") Long id, @Param("amount") Integer amount);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Product p
        SET p.stock = p.stock + :amount,
            p.version = COALESCE(p.version, 0) + 1
        WHERE p.id = :id
    """)
    int incrementStock(@Param("id") Long id, @Param("amount") Integer amount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
