package com.project.qms.controller;

import com.project.qms.dto.ActionProgressRequest;
import com.project.qms.dto.ActionVerificationRequest;
import com.project.qms.dto.CorrectiveActionRequest;
import com.project.qms.dto.CorrectiveActionResponse;
import com.project.qms.entity.User;
import com.project.qms.service.CorrectiveActionService;
import com.project.qms.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Corrective actions (FR-08).
 *
 * NOTE the paths. Assigning an action is expressed as a sub-resource of the
 * defect - POST /api/defects/{id}/corrective-actions - because that is what it
 * is: you cannot assign an action without naming the defect it belongs to.
 * The Phase 5 §8.4 API contract names exactly this path, so this class has no
 * class-level @RequestMapping; each method states its full path.
 */
@RestController
public class CorrectiveActionController {

    private final CorrectiveActionService actionService;
    private final UserService userService;

    public CorrectiveActionController(CorrectiveActionService actionService, UserService userService) {
        this.actionService = actionService;
        this.userService = userService;
    }

    @GetMapping("/api/corrective-actions")
    public ResponseEntity<List<CorrectiveActionResponse>> list(
            @RequestParam(required = false) Integer defectId,
            @RequestParam(required = false) Integer responsiblePersonId,
            @RequestParam(required = false) Boolean overdueOnly) {

        if (Boolean.TRUE.equals(overdueOnly)) {
            return ResponseEntity.ok(actionService.findOverdue());
        }
        if (defectId != null) {
            return ResponseEntity.ok(actionService.findByDefect(defectId));
        }
        if (responsiblePersonId != null) {
            return ResponseEntity.ok(actionService.findByResponsiblePerson(responsiblePersonId));
        }
        return ResponseEntity.ok(actionService.findAll());
    }

    @GetMapping("/api/corrective-actions/overdue")
    public ResponseEntity<List<CorrectiveActionResponse>> overdue() {
        return ResponseEntity.ok(actionService.findOverdue());
    }

    @GetMapping("/api/corrective-actions/{id}")
    public ResponseEntity<CorrectiveActionResponse> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(actionService.findById(id));
    }

    /** FR-08.1 - Phase 5 §8.4 names this exact path. */
    @PostMapping("/api/defects/{defectId}/corrective-actions")
    public ResponseEntity<CorrectiveActionResponse> assign(@PathVariable Integer defectId,
                                                           @Valid @RequestBody CorrectiveActionRequest request,
                                                           HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.status(HttpStatus.CREATED).body(actionService.assign(defectId, request, me));
    }

    /** FR-08.6, FR-08.7. */
    @PutMapping("/api/corrective-actions/{id}/progress")
    public ResponseEntity<CorrectiveActionResponse> updateProgress(@PathVariable Integer id,
                                                                   @Valid @RequestBody ActionProgressRequest request,
                                                                   HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.ok(actionService.updateProgress(id, request, me));
    }

    /** FR-08.9 - Phase 5 §8.4 names this exact path. */
    @PutMapping("/api/corrective-actions/{id}/verify")
    public ResponseEntity<CorrectiveActionResponse> verify(@PathVariable Integer id,
                                                           @Valid @RequestBody ActionVerificationRequest request,
                                                           HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.ok(actionService.verify(id, request, me));
    }
}
