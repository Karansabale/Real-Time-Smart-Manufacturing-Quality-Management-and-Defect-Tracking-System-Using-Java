package com.project.qms.service;

import com.project.qms.dto.ProductRequest;
import com.project.qms.dto.ProductResponse;
import com.project.qms.entity.Product;
import com.project.qms.entity.User;
import com.project.qms.exception.BusinessRuleException;
import com.project.qms.exception.DuplicateResourceException;
import com.project.qms.exception.ResourceNotFoundException;
import com.project.qms.repository.ProductRepository;
import com.project.qms.repository.ProductionBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The product master list (FR-03).
 *
 * The interesting rule is deletion. FR-03.8 says a product may only be deleted
 * when no production batch references it. The database enforces that with a
 * RESTRICT foreign key, but the service asks first so the user gets
 * "P-101 cannot be deleted because 3 production batches refer to it"
 * instead of a raw constraint violation.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductionBatchRepository batchRepository;

    public ProductService(ProductRepository productRepository, ProductionBatchRepository batchRepository) {
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository.findAllByOrderByProductCodeAsc().stream().map(ProductService::toResponse).toList();
    }

    /** Search by code or name (FR-03.10). A blank term returns everything. */
    @Transactional(readOnly = true)
    public List<ProductResponse> search(String term) {
        if (term == null || term.isBlank()) {
            return findAll();
        }
        String q = term.trim();
        return productRepository
                .findByProductCodeContainingIgnoreCaseOrProductNameContainingIgnoreCase(q, q)
                .stream().map(ProductService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Product getById(Integer productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", productId));
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Integer productId) {
        return toResponse(getById(productId));
    }

    @Transactional
    public ProductResponse addProduct(ProductRequest request, User currentUser) {
        requireAdmin(currentUser);
        if (productRepository.existsByProductCode(request.productCode().trim())) {
            throw new DuplicateResourceException(
                    "Product code '" + request.productCode() + "' already exists. Product codes must be unique.");
        }
        Product product = new Product();
        apply(product, request);
        product.setCreatedBy(currentUser);
        return toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    public ProductResponse updateProduct(Integer productId, ProductRequest request, User currentUser) {
        requireAdmin(currentUser);
        Product product = getById(productId);

        if (!product.getProductCode().equals(request.productCode().trim())
                && productRepository.existsByProductCode(request.productCode().trim())) {
            throw new DuplicateResourceException(
                    "Product code '" + request.productCode() + "' already exists. Product codes must be unique.");
        }

        apply(product, request);
        product.setUpdatedBy(currentUser);
        return toResponse(productRepository.saveAndFlush(product));
    }

    /** FR-03.8 and FR-03.9. */
    @Transactional
    public void deleteProduct(Integer productId, User currentUser) {
        requireAdmin(currentUser);
        Product product = getById(productId);

        long batchCount = batchRepository.findByProductProductIdOrderByBatchNumberDesc(productId).size();
        if (batchCount > 0) {
            throw new BusinessRuleException(
                    "Product " + product.getProductCode() + " cannot be deleted because "
                    + batchCount + " production batch(es) refer to it. "
                    + "Delete or reassign those batches first.");
        }
        productRepository.delete(product);
    }

    /**
     * Phase 4 §6.2: only an Administrator may add, update or delete products.
     * Inspectors and Supervisors can view them.
     */
    private static void requireAdmin(User user) {
        if (!user.isAdmin()) {
            throw new BusinessRuleException(
                    "Only an Administrator may add, change or delete products. You are signed in as "
                    + user.getRole().name() + ".");
        }
    }

    private static void apply(Product product, ProductRequest request) {
        product.setProductCode(request.productCode().trim());
        product.setProductName(request.productName().trim());
        product.setCategory(request.category().trim());
        product.setSpecification(request.specification() == null ? null : request.specification().trim());
    }

    static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getProductId(),
                product.getProductCode(),
                product.getProductName(),
                product.getCategory(),
                product.getSpecification(),
                product.getCreatedBy() == null ? null : product.getCreatedBy().getFullName(),
                product.getCreatedAt());
    }
}
