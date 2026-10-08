package com.project.qms.service;

import com.project.qms.dto.UserRequest;
import com.project.qms.dto.UserResponse;
import com.project.qms.entity.User;
import com.project.qms.exception.BusinessRuleException;
import com.project.qms.exception.DuplicateResourceException;
import com.project.qms.exception.ResourceNotFoundException;
import com.project.qms.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Everything about users: logging in, and the Admin's user management (FR-01, FR-02).
 *
 * PASSWORD HASHING. The encoder is created here rather than injected from a
 * @Configuration class, because the project has no config package (Phase 5
 * §6.2) and BCryptPasswordEncoder needs no configuration - it is one object
 * with sensible defaults. If a second class ever needed to hash a password,
 * that would be the moment to introduce a shared bean.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------

    /**
     * FR-01.1 to FR-01.4. Returns the user when the credentials are good.
     *
     * Note the two failure messages are IDENTICAL for "no such user" and
     * "wrong password". That is deliberate: a message that said "no such
     * user" would let someone discover valid usernames by trying them.
     */
    @Transactional(readOnly = true)
    public User login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessRuleException("Incorrect username or password."));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessRuleException(
                    "This account has been deactivated. Please contact the administrator.");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("Incorrect username or password.");
        }
        return user;
    }

    // ------------------------------------------------------------------
    // User management (Admin only)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAllByOrderByFullNameAsc().stream().map(UserService::toResponse).toList();
    }

    /** Active users only - used by the "responsible person" dropdown (FR-08.3). */
    @Transactional(readOnly = true)
    public List<UserResponse> findActive() {
        return userRepository.findByIsActiveTrueOrderByFullNameAsc().stream().map(UserService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public User getById(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Integer userId) {
        return toResponse(getById(userId));
    }

    /** FR-02.1 to FR-02.4. */
    @Transactional
    public UserResponse addUser(UserRequest request, User currentUser) {
        requireAdmin(currentUser);
        if (userRepository.existsByUsername(request.username().trim())) {
            throw new DuplicateResourceException(
                    "Username '" + request.username() + "' is already taken. Please choose another.");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessRuleException("A password is required when creating a new user.");
        }

        User user = new User();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setIsActive(request.isActive() == null || request.isActive());

        return toResponse(userRepository.saveAndFlush(user));
    }

    /**
     * FR-02.6 to FR-02.10.
     *
     * Two rules live here that the database cannot express:
     *   - a blank password means "leave the existing one alone"
     *   - an Admin may not deactivate their own account (FR-02.10)
     */
    @Transactional
    public UserResponse updateUser(Integer userId, UserRequest request, User currentUser) {
        requireAdmin(currentUser);
        User user = getById(userId);

        if (!user.getUsername().equals(request.username().trim())
                && userRepository.existsByUsername(request.username().trim())) {
            throw new DuplicateResourceException(
                    "Username '" + request.username() + "' is already taken. Please choose another.");
        }

        boolean deactivating = Boolean.FALSE.equals(request.isActive())
                && Boolean.TRUE.equals(user.getIsActive());

        if (deactivating && user.getUserId().equals(currentUser.getUserId())) {
            throw new BusinessRuleException(
                    "You cannot deactivate your own account. Ask another administrator to do it.");
        }

        user.setUsername(request.username().trim());
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());

        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        if (request.isActive() != null) {
            user.setIsActive(request.isActive());
        }

        return toResponse(userRepository.saveAndFlush(user));
    }

    /** FR-02.8. A soft change, not a delete - the record stays (FR-02.9). */
    @Transactional
    public UserResponse setActive(Integer userId, boolean active, User currentUser) {
        requireAdmin(currentUser);
        User user = getById(userId);

        if (!active && user.getUserId().equals(currentUser.getUserId())) {
            throw new BusinessRuleException(
                    "You cannot deactivate your own account. Ask another administrator to do it.");
        }
        user.setIsActive(active);
        return toResponse(userRepository.saveAndFlush(user));
    }

    // ------------------------------------------------------------------

    /**
     * The permission matrix in Phase 4 §6.2 says only an Administrator may add,
     * update or deactivate users. That check is here rather than in the
     * controller because it is a rule about the business, not about HTTP - and
     * because a rule in the service is one rule, however many places call it.
     */
    private static void requireAdmin(User user) {
        if (!user.isAdmin()) {
            throw new BusinessRuleException(
                    "Only an Administrator may manage users. You are signed in as "
                    + user.getRole().name() + ".");
        }
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                user.getIsActive(),
                user.getCreatedAt());
    }
}
