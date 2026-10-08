package com.project.qms.controller;

import com.project.qms.dto.InspectionRemarksRequest;
import com.project.qms.dto.InspectionRequest;
import com.project.qms.dto.InspectionResponse;
import com.project.qms.entity.User;
import com.project.qms.service.InspectionService;
import com.project.qms.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Quality inspections (FR-05). */
@RestController
@RequestMapping("/api/inspections")
public class InspectionController {

    private final InspectionService inspectionService;
    private final UserService userService;

    public InspectionController(InspectionService inspectionService, UserService userService) {
        this.inspectionService = inspectionService;
        this.userService = userService;
    }

    /**
     * Lists inspections, optionally filtered (FR-05.14).
     * Product and batch filters take priority; otherwise an optional date range.
     */
    @GetMapping
    public ResponseEntity<List<InspectionResponse>> list(
            @RequestParam(required = false) Integer productId,
            @RequestParam(required = false) Integer batchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (productId != null) {
            return ResponseEntity.ok(inspectionService.findByProduct(productId));
        }
        if (batchId != null) {
            return ResponseEntity.ok(inspectionService.findByBatch(batchId));
        }
        if (from != null || to != null) {
            return ResponseEntity.ok(inspectionService.findByDateRange(from, to));
        }
        return ResponseEntity.ok(inspectionService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InspectionResponse> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(inspectionService.findById(id));
    }

    @PostMapping
    public ResponseEntity<InspectionResponse> create(@Valid @RequestBody InspectionRequest request,
                                                     HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.status(HttpStatus.CREATED).body(inspectionService.createInspection(request, me));
    }

    /**
     * FR-05.12 - only the remarks may change once an inspection is saved.
     *
     * The body is InspectionRemarksRequest, not InspectionRequest: the fields
     * that are already settled on the saved row must not be required again
     * just to correct a note. It is @Valid so that a remark longer than the
     * column allows is refused here, with the field named, instead of
     * travelling to the database and coming back as an unrelated explanation
     * (D-03, Phase 12).
     */
    @PutMapping("/{id}/remarks")
    public ResponseEntity<InspectionResponse> updateRemarks(@PathVariable Integer id,
                                                            @Valid @RequestBody InspectionRemarksRequest request,
                                                            HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.ok(inspectionService.updateRemarks(id, request.remarks(), me));
    }
}
