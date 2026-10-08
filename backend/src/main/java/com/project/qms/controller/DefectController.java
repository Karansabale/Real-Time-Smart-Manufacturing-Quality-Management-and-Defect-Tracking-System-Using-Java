package com.project.qms.controller;

import com.project.qms.dto.DefectDetailResponse;
import com.project.qms.dto.DefectHistoryResponse;
import com.project.qms.dto.DefectRequest;
import com.project.qms.dto.DefectResponse;
import com.project.qms.dto.DefectStatusRequest;
import com.project.qms.entity.User;
import com.project.qms.service.DefectService;
import com.project.qms.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Defect registration and tracking (FR-06, FR-07).
 *
 * Every write method reads the logged-in user from the session and passes it
 * to the service. The controller never decides whether a move is allowed -
 * that is DefectService.changeStatus(), one method, one place (Phase 5, C2).
 */
@RestController
@RequestMapping("/api/defects")
public class DefectController {

    private final DefectService defectService;
    private final UserService userService;

    public DefectController(DefectService defectService, UserService userService) {
        this.defectService = defectService;
        this.userService = userService;
    }

    /**
     * Filters by status, severity or product - the filter boxes on screen 7
     * (FR-06.13). The filter words are parsed by DefectService, so that this
     * list and the Defect Register report refuse a nonsense filter with the
     * same sentence.
     */
    @GetMapping
    public ResponseEntity<List<DefectResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Integer productId,
            @RequestParam(required = false) Boolean unassigned) {

        if (Boolean.TRUE.equals(unassigned)) {
            return ResponseEntity.ok(defectService.findWithoutCorrectiveAction());
        }
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(defectService.findByStatus(DefectService.parseStatusFilter(status)));
        }
        if (severity != null && !severity.isBlank()) {
            return ResponseEntity.ok(defectService.findBySeverity(DefectService.parseSeverityFilter(severity)));
        }
        if (productId != null) {
            return ResponseEntity.ok(defectService.findByProduct(productId));
        }
        return ResponseEntity.ok(defectService.findAll());
    }

    /**
     * The full detail view: defect, timeline, corrective actions AND the moves
     * this particular user is allowed to make from here.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DefectDetailResponse> getOne(@PathVariable Integer id, HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.ok(defectService.findDetail(id, me));
    }

    /** FR-06.8 - look a defect up by the reference written on the shop-floor tag. */
    @GetMapping("/lookup/{ref}")
    public ResponseEntity<DefectResponse> lookup(@PathVariable String ref) {
        return ResponseEntity.ok(defectService.findByRef(ref));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<DefectHistoryResponse>> history(@PathVariable Integer id) {
        return ResponseEntity.ok(defectService.findHistory(id));
    }

    @PostMapping
    public ResponseEntity<DefectResponse> create(@Valid @RequestBody DefectRequest request, HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.status(HttpStatus.CREATED).body(defectService.registerDefect(request, me));
    }

    /** FR-06.10 - only while the defect is still OPEN. */
    @PutMapping("/{id}")
    public ResponseEntity<DefectResponse> update(@PathVariable Integer id,
                                                 @Valid @RequestBody DefectRequest request,
                                                 HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.ok(defectService.updateDefect(id, request, me));
    }

    /** THE STATUS CHANGE (FR-07). All the rules live in the service. */
    @PutMapping("/{id}/status")
    public ResponseEntity<DefectResponse> changeStatus(@PathVariable Integer id,
                                                       @Valid @RequestBody DefectStatusRequest request,
                                                       HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.ok(defectService.changeStatus(id, request, me));
    }

    /** FR-06.14 - refused once the defect has left OPEN. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id, HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        defectService.deleteDefect(id, me);
        return ResponseEntity.noContent().build();
    }

    /** The two filter parsers now live in DefectService - one copy, two doors. */
}
