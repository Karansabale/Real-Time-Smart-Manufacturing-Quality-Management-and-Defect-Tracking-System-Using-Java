package com.project.qms.dto;

import com.project.qms.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Used for both creating and updating a user (FR-02.1, FR-02.6).
 *
 * On UPDATE the password may be left blank, meaning "keep the existing one".
 * That is why it has no @NotBlank - the service decides, because only it
 * knows whether this is a create or an update.
 */
public record UserRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be 3 to 50 characters")
        String username,

        @Size(max = 72, message = "Password must be at most 72 characters")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(min = 3, max = 100, message = "Full name must be 3 to 100 characters")
        String fullName,

        @NotNull(message = "Role is required") Role role,

        Boolean isActive) {
}
