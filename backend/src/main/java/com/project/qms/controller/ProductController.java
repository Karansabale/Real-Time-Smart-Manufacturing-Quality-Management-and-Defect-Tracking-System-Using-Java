package com.project.qms.controller;

import com.project.qms.dto.ProductRequest;
import com.project.qms.dto.ProductResponse;
import com.project.qms.entity.User;
import com.project.qms.service.ProductService;
import com.project.qms.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Product master list (FR-03). Everyone may read; only an Admin may change. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final UserService userService;

    public ProductController(ProductService productService, UserService userService) {
        this.productService = productService;
        this.userService = userService;
    }

    /** ?q= searches by code or name (FR-03.10). */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> list(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(productService.search(q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request, HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addProduct(request, me));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable Integer id,
                                                  @Valid @RequestBody ProductRequest request,
                                                  HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.ok(productService.updateProduct(id, request, me));
    }

    /** FR-03.8 - refused with an explanation if any batch refers to the product. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id, HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        productService.deleteProduct(id, me);
        return ResponseEntity.noContent().build();
    }
}
