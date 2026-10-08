package com.project.qms.controller;

import com.project.qms.dto.UserRequest;
import com.project.qms.dto.UserResponse;
import com.project.qms.entity.User;
import com.project.qms.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** User management (FR-02). Admin only - enforced in UserService. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** By default returns every user; ?active=true returns only active ones (FR-02.5, FR-08.3). */
    @GetMapping
    public ResponseEntity<List<UserResponse>> list(@RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(Boolean.TRUE.equals(active) ? userService.findActive() : userService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request, HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.addUser(request, me));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Integer id, @Valid @RequestBody UserRequest request,
                                               HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.ok(userService.updateUser(id, request, me));
    }

    /** FR-02.8. A deactivation, never a delete (FR-02.9). */
    @PutMapping("/{id}/active/{active}")
    public ResponseEntity<UserResponse> setActive(@PathVariable Integer id, @PathVariable boolean active,
                                                  HttpSession session) {
        User me = userService.getById(AuthController.currentUserId(session));
        return ResponseEntity.ok(userService.setActive(id, active, me));
    }
}
