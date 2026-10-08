package com.project.qms.controller;

import com.project.qms.dto.LoginRequest;
import com.project.qms.dto.UserResponse;
import com.project.qms.entity.User;
import com.project.qms.exception.BusinessRuleException;
import com.project.qms.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, logout and "who am I" (FR-01).
 *
 * HOW SESSIONS WORK HERE. There is no Spring Security filter chain in this
 * project - only spring-security-crypto for BCrypt - so the session is
 * managed by hand, which is about eight lines and easy to follow:
 *
 *   1. login() checks the password and, if it is good, puts the user's ID,
 *      name and role into the HTTP session. Tomcat then sends a JSESSIONID
 *      cookie, marked httpOnly and SameSite=Lax by application.properties.
 *   2. Every other controller calls currentUserId(session) to find out who is
 *      asking, and refuses the request if nobody is logged in.
 *   3. logout() invalidates the session, which throws the cookie away.
 *
 * The session key lives here, in the one class that writes it, so the key is
 * never spelled out twice and can never drift.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Where the logged-in user's id is kept in the session. */
    public static final String SESSION_USER_ID = "qmsUserId";

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** FR-01.1 to FR-01.3. */
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        User user = userService.login(request.username(), request.password());
        session.setAttribute(SESSION_USER_ID, user.getUserId());
        session.setMaxInactiveInterval(30 * 60);        // NFR-02.4: 30 minutes
        return ResponseEntity.ok(toResponse(user));
    }

    /** FR-01.5. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    /** FR-01.6 - used by every screen on load to decide what to show. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(HttpSession session) {
        return ResponseEntity.ok(toResponse(userService.getById(currentUserId(session))));
    }

    /**
     * Reads the logged-in user's id out of the session.
     *
     * Every controller uses this, and it is deliberately the ONLY way to get
     * it: one key, one place, one behaviour when nobody is logged in.
     */
    public static Integer currentUserId(HttpSession session) {
        Object id = session.getAttribute(SESSION_USER_ID);
        if (id == null) {
            throw new BusinessRuleException("You are not logged in. Please log in and try again.");
        }
        return (Integer) id;
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getUserId(), user.getUsername(), user.getFullName(),
                user.getRole().name(), user.getIsActive(), user.getCreatedAt());
    }
}
