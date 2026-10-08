package com.project.qms.repository;

import com.project.qms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Data access for the product master list (FR-03). */
public interface ProductRepository extends JpaRepository<Product, Integer> {

    Optional<Product> findByProductCode(String productCode);

    /** Duplicate check before saving (FR-03.3). */
    boolean existsByProductCode(String productCode);

    List<Product> findAllByOrderByProductCodeAsc();

    /** Search by code or name (FR-03.10). */
    List<Product> findByProductCodeContainingIgnoreCaseOrProductNameContainingIgnoreCase(
            String productCode, String productName);
}
