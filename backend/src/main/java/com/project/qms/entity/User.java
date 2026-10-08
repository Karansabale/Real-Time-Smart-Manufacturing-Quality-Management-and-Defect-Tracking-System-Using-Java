package com.project.qms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A person who can log in. Maps to the 'users' table.
 *
 * Users are never deleted; they are deactivated (FR-02.9), because inspections
 * and defects point at them and deleting a user would destroy the record of
 * who did what.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    /**
     * The BCrypt hash - never the password itself (NFR-02.3).
     * BCrypt output is always exactly 60 characters, which is why the column
     * is VARCHAR(60) rather than something longer "just in case".
     */
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void stampCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void stampUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Convenience read-only helper - not a business rule. */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Convenience read-only helper - not a business rule. */
    public boolean isInspector() {
        return role == Role.INSPECTOR;
    }

    /** Convenience read-only helper - not a business rule. */
    public boolean isSupervisor() {
        return role == Role.SUPERVISOR;
    }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
