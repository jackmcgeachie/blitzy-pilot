/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity representing user data structure converted from COBOL SEC-USER-DATA copybook.
 * 
 * This entity provides PostgreSQL persistence, Spring Security integration, and RACF-equivalent 
 * role mapping with BCrypt password security. It implements the UserDetails interface for 
 * Spring Security authentication and authorization.
 * 
 * Original COBOL Structure from CSUSR01Y.cpy:
 * - SEC-USR-ID      PIC X(08) -> user_id (UUID format)
 * - SEC-USR-FNAME   PIC X(20) -> first_name
 * - SEC-USR-LNAME   PIC X(20) -> last_name  
 * - SEC-USR-PWD     PIC X(08) -> password_hash (BCrypt with strength 10)
 * - SEC-USR-TYPE    PIC X(01) -> role_code ('A'=Admin, 'U'=User)
 * 
 * Security Enhancements:
 * - BCrypt password hashing replacing plain text storage
 * - Spring Security UserDetails integration for JWT authentication
 * - RACF-equivalent role mapping (ROLE_ADMIN/ROLE_USER)
 * - JPA optimistic locking for concurrent access control
 * - Comprehensive audit trails with created_at/updated_at timestamps
 * 
 * @author Blitzy Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_username", columnList = "username", unique = true),
    @Index(name = "idx_users_role_code", columnList = "role_code"),
    @Index(name = "idx_users_status", columnList = "status")
})
public class User implements UserDetails {

    /**
     * Primary key - UUID format replacing COBOL 8-character SEC-USR-ID.
     * Provides enhanced uniqueness and security compared to sequential identifiers.
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(name = "user_id", columnDefinition = "UUID", nullable = false)
    private UUID userId;

    /**
     * Unique username for authentication - converted from SEC-USR-ID pattern.
     * Maintains compatibility with existing 8-character user ID patterns while 
     * supporting extended username formats for modernization.
     */
    @NotNull(message = "Username is required")
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 1, max = 50, message = "Username must be between 1 and 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username can only contain letters, numbers, dots, underscores, and hyphens")
    @Column(name = "username", length = 50, nullable = false, unique = true)
    private String username;

    /**
     * BCrypt-hashed password replacing plain text SEC-USR-PWD.
     * Critical security enhancement providing industry-standard password protection
     * with strength factor 10 and automatic salt generation.
     */
    @NotNull(message = "Password hash is required")
    @Size(min = 60, max = 255, message = "Password hash must be BCrypt format")
    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    /**
     * User role code maintaining RACF-equivalent authorization patterns.
     * Maps directly to Spring Security authorities:
     * - 'A' (Administrative) -> ROLE_ADMIN
     * - 'U' (Regular User) -> ROLE_USER
     * 
     * Preserves existing mainframe authorization logic while enabling 
     * modern Spring Security @PreAuthorize annotations.
     */
    @NotNull(message = "Role code is required")
    @Pattern(regexp = "^[AU]$", message = "Role code must be 'A' (Admin) or 'U' (User)")
    @Column(name = "role_code", length = 1, nullable = false)
    private String roleCode;

    /**
     * First name from SEC-USR-FNAME - maintains original 20-character limit
     * with enhanced validation for data integrity.
     */
    @NotNull(message = "First name is required")
    @NotBlank(message = "First name cannot be blank")
    @Size(min = 1, max = 20, message = "First name must be between 1 and 20 characters")
    @Pattern(regexp = "^[A-Za-z\\s'-]+$", message = "First name can only contain letters, spaces, apostrophes, and hyphens")
    @Column(name = "first_name", length = 20, nullable = false)
    private String firstName;

    /**
     * Last name from SEC-USR-LNAME - maintains original 20-character limit
     * with enhanced validation for data integrity.
     */
    @NotNull(message = "Last name is required")
    @NotBlank(message = "Last name cannot be blank")
    @Size(min = 1, max = 20, message = "Last name must be between 1 and 20 characters")
    @Pattern(regexp = "^[A-Za-z\\s'-]+$", message = "Last name can only contain letters, spaces, apostrophes, and hyphens")
    @Column(name = "last_name", length = 20, nullable = false)
    private String lastName;

    /**
     * Account status for user management and security control.
     * Enables account deactivation and security lockout capabilities
     * not present in original COBOL implementation.
     */
    @NotNull(message = "Status is required")
    @Pattern(regexp = "^(ACTIVE|INACTIVE|LOCKED)$", message = "Status must be ACTIVE, INACTIVE, or LOCKED")
    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    /**
     * Account creation timestamp for audit trail compliance.
     * Provides comprehensive audit logging required for financial services.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last modification timestamp for audit trail compliance.
     * Automatically updated on entity modifications.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * JPA optimistic locking version field replacing CICS record locking.
     * Prevents concurrent modification conflicts in distributed Spring Boot environment.
     * Automatically managed by JPA for concurrency control.
     */
    @Version
    @Column(name = "version_number", nullable = false)
    private Long versionNumber = 0L;

    /**
     * Default constructor for JPA.
     */
    protected User() {
        // JPA requires default constructor
    }

    /**
     * Constructor for creating new User entities with required fields.
     * Automatically sets creation timestamp and default values.
     *
     * @param username Unique username for authentication
     * @param passwordHash BCrypt-hashed password
     * @param firstName User's first name
     * @param lastName User's last name
     * @param roleCode User role ('A' for Admin, 'U' for User)
     */
    public User(String username, String passwordHash, String firstName, String lastName, String roleCode) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.roleCode = roleCode;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * JPA PrePersist callback to set creation and update timestamps.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * JPA PreUpdate callback to update modification timestamp.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Spring Security UserDetails implementation methods

    /**
     * Returns the authorities granted to the user based on role_code.
     * Maps RACF-equivalent roles to Spring Security authorities:
     * - 'A' -> ROLE_ADMIN (Administrative access)
     * - 'U' -> ROLE_USER (Regular user access)
     *
     * @return Collection of GrantedAuthority objects for Spring Security
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = "A".equals(roleCode) ? "ROLE_ADMIN" : "ROLE_USER";
        return Collections.singletonList(new SimpleGrantedAuthority(role));
    }

    /**
     * Returns the password hash for Spring Security authentication.
     * BCrypt-hashed password is validated by Spring Security's BCryptPasswordEncoder.
     *
     * @return BCrypt password hash
     */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /**
     * Returns the username for Spring Security authentication.
     *
     * @return Username string
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * Indicates whether the user's account has expired.
     * Based on account status field.
     *
     * @return true if account is not expired
     */
    @Override
    public boolean isAccountNonExpired() {
        return "ACTIVE".equals(status);
    }

    /**
     * Indicates whether the user is locked or unlocked.
     * Based on account status field.
     *
     * @return true if account is not locked
     */
    @Override
    public boolean isAccountNonLocked() {
        return !"LOCKED".equals(status);
    }

    /**
     * Indicates whether the user's credentials (password) has expired.
     * Currently returns true - credential expiration not implemented per minimal change directive.
     *
     * @return true (credentials non-expired)
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Indicates whether the user is enabled or disabled.
     * Based on account status field.
     *
     * @return true if user is enabled
     */
    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(status);
    }

    // Business logic methods

    /**
     * Checks if user has administrative privileges.
     * Equivalent to RACF user type 'A' validation in COBOL programs.
     *
     * @return true if user has admin role
     */
    public boolean isAdmin() {
        return "A".equals(roleCode);
    }

    /**
     * Checks if user has regular user privileges.
     * Equivalent to RACF user type 'U' validation in COBOL programs.
     *
     * @return true if user has regular user role
     */
    public boolean isRegularUser() {
        return "U".equals(roleCode);
    }

    /**
     * Gets the full name by combining first and last names.
     * Utility method for display purposes.
     *
     * @return Combined first and last name
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Updates the password hash and modification timestamp.
     * Used for password change operations.
     *
     * @param newPasswordHash BCrypt-hashed new password
     */
    public void updatePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Deactivates the user account by setting status to INACTIVE.
     * Prevents user authentication while preserving account data.
     */
    public void deactivate() {
        this.status = "INACTIVE";
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Activates the user account by setting status to ACTIVE.
     * Enables user authentication and system access.
     */
    public void activate() {
        this.status = "ACTIVE";
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Locks the user account by setting status to LOCKED.
     * Used for security lockout scenarios.
     */
    public void lock() {
        this.status = "LOCKED";
        this.updatedAt = LocalDateTime.now();
    }

    // Standard getters and setters

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Long versionNumber) {
        this.versionNumber = versionNumber;
    }

    // equals, hashCode, and toString methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(userId, user.userId) &&
               Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username);
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", roleCode='" + roleCode + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", versionNumber=" + versionNumber +
                '}';
    }
}