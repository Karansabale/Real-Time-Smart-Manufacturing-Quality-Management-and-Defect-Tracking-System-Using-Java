package com.project.qms.controller;

import com.project.qms.dto.BatchRequest;
import com.project.qms.dto.BatchResponse;
import com.project.qms.dto.BatchStatusRequest;
import com.project.qms.entity.User;
import com.project.qms.service.BatchService;
import com.project.qms.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Production batches (FR-04). Supervisor creates and updates; everyone reads. */
@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;
    private final UserService userService;

    public BatchController(BatchService batchService, UserService userService) {
        this.batchService = batchService;
        this.userService = userService;
    }

    /** ?productId= filters to one product's batches. */
    @GetMapping
    public ResponseEntity<List<BatchResponse>> list(@RequestParam(required = false) Integer productId) {
        return ResponseEntity.ok(productId == null ? batchService.findAll() : batchService.findByProduct(productId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchResponse> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(batchService.findById(id));
    }

    @PostMapping
    public ResponseEntity<BatchResponse> create(@Valid @RequestBody BatchRequest request, HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.createBatch(request, me));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<BatchResponse> updateStatus(@PathVariable Integer id,
                                                      @Valid @RequestBody BatchStatusRequest request,
                                                      HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.ok(batchService.updateStatus(id, request.productionStatus(), me));
    }
}
