/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * JPA Entity representing user authentication and authorization data converted from 
 * COBOL SEC-USER-DATA structure (CSUSR01Y.cpy). This entity implements Spring Security 
 * UserDetails interface for JWT token-based authentication and provides role-based 
 * access control maintaining RACF user type compatibility.
 * 
 * Original COBOL Structure Mapping:
 * - SEC-USR-ID (PIC X(08)) → userId (String, 8 characters)
 * - SEC-USR-FNAME (PIC X(20)) → firstName (String, 20 characters)
 * - SEC-USR-LNAME (PIC X(20)) → lastName (String, 20 characters)
 * - SEC-USR-PWD (PIC X(08)) → passwordHash (String, BCrypt hashed)
 * - SEC-USR-TYPE (PIC X(01)) → roleCode (String, 'A'/'U' for Admin/User)
 * 
 * Enhanced with Spring Security integration and optimistic locking.
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_username", columnList = "username", unique = true),
    @Index(name = "idx_users_status", columnList = "status"),
    @Index(name = "idx_users_role_code", columnList = "role_code")
})
public class User implements UserDetails {

    /**
     * Primary key - 8-character user identifier maintaining COBOL field length
     * Maps from SEC-USR-ID PIC X(08) field in COBOL structure
     */
    @Id
    @Column(name = "user_id", length = 8, nullable = false)
    @Size(min = 1, max = 8, message = "User ID must be between 1 and 8 characters")
    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * Unique username for authentication - enhanced from legacy 8-character limitation
     * Supports Spring Security authentication with variable length usernames
     */
    @Column(name = "username", length = 50, nullable = false, unique = true)
    @Size(min = 1, max = 50, message = "Username must be between 1 and 50 characters")
    @NotBlank(message = "Username is required")
    private String username;

    /**
     * First name from SEC-USR-FNAME PIC X(20) field
     * Maintains 20-character maximum length from COBOL structure
     */
    @Column(name = "first_name", length = 20)
    @Size(max = 20, message = "First name cannot exceed 20 characters")
    private String firstName;

    /**
     * Last name from SEC-USR-LNAME PIC X(20) field
     * Maintains 20-character maximum length from COBOL structure
     */
    @Column(name = "last_name", length = 20)
    @Size(max = 20, message = "Last name cannot exceed 20 characters")
    private String lastName;

    /**
     * BCrypt-hashed password replacing plain text SEC-USR-PWD PIC X(08) field
     * Enhanced security with industry-standard BCrypt hashing (strength factor 10)
     */
    @Column(name = "password_hash", length = 255, nullable = false)
    @NotBlank(message = "Password hash is required")
    private String passwordHash;

    /**
     * Role code maintaining RACF user type compatibility
     * Maps from SEC-USR-TYPE PIC X(01): 'A' = Administrative, 'U' = Regular User
     * Supports Spring Security role-based authorization through authorities
     */
    @Column(name = "role_code", length = 1, nullable = false)
    @Pattern(regexp = "[AU]", message = "Role code must be 'A' (Admin) or 'U' (User)")
    @NotBlank(message = "Role code is required")
    private String roleCode;

    /**
     * Account status for user management and authentication control
     * Supports ACTIVE, INACTIVE, and LOCKED states for comprehensive user lifecycle
     */
    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Account creation timestamp for audit trail and user management
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last modification timestamp for change tracking
     * Updated automatically on entity modifications
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Last successful signon date for audit trail
     * Tracks user authentication activity per COSGN00C conversion requirements
     */
    @Column(name = "last_signon_date")
    private LocalDateTime lastSignonDate;

    /**
     * Last successful signon time for detailed audit tracking
     * Maintains separate time field for compatibility with mainframe audit patterns
     */
    @Column(name = "last_signon_time")
    private LocalDateTime lastSignonTime;

    /**
     * JPA optimistic locking version field
     * Prevents concurrent modification conflicts during user updates
     * Replaces CICS record locking with modern optimistic concurrency control
     */
    @Version
    @Column(name = "version_number", nullable = false)
    private Long versionNumber = 0L;

    /**
     * User status enumeration for type-safe status management
     */
    public enum UserStatus {
        ACTIVE("Active user account"),
        INACTIVE("Inactive user account"),
        LOCKED("Locked user account");

        private final String description;

        UserStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Default constructor for JPA
     */
    public User() {}

    /**
     * Constructor for creating new user with essential fields
     * 
     * @param userId Unique 8-character user identifier
     * @param username Unique username for authentication
     * @param firstName User's first name (max 20 characters)
     * @param lastName User's last name (max 20 characters)
     * @param passwordHash BCrypt-hashed password
     * @param roleCode Role code ('A' for Admin, 'U' for User)
     */
    public User(String userId, String username, String firstName, String lastName, 
                String passwordHash, String roleCode) {
        this.userId = userId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
        this.roleCode = roleCode;
        this.status = UserStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * JPA PrePersist callback to set creation and modification timestamps
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * JPA PreUpdate callback to update modification timestamp
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates last signon tracking fields for audit trail
     * Called by AuthenticationService upon successful authentication
     */
    public void updateLastSignon() {
        LocalDateTime now = LocalDateTime.now();
        this.lastSignonDate = now;
        this.lastSignonTime = now;
        this.updatedAt = now;
    }

    // Spring Security UserDetails implementation

    /**
     * Returns Spring Security authorities based on role code
     * Maps RACF user types to Spring Security authorities:
     * - 'A' (Administrative) → ROLE_ADMIN
     * - 'U' (Regular User) → ROLE_USER
     * 
     * @return Collection of granted authorities for Spring Security
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = "A".equals(roleCode) ? "ROLE_ADMIN" : "ROLE_USER";
        return Collections.singletonList(new SimpleGrantedAuthority(role));
    }

    /**
     * Returns the password hash for Spring Security authentication
     * BCrypt-hashed password used for secure credential validation
     * 
     * @return BCrypt-hashed password
     */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /**
     * Returns the username for Spring Security authentication
     * 
     * @return Username for authentication
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * Account non-expiration status for Spring Security
     * Currently returns true as account expiration is not implemented
     * 
     * @return true (accounts do not expire)
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Account non-locked status based on user status
     * 
     * @return false if status is LOCKED, true otherwise
     */
    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    /**
     * Credentials non-expiration status for Spring Security
     * Currently returns true as password expiration is not implemented
     * 
     * @return true (credentials do not expire)
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Account enabled status based on user status
     * 
     * @return true if status is ACTIVE, false otherwise
     */
    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    // Getters and Setters

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
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

    /**
     * Returns full name by concatenating first and last names
     * Provides compatibility with COBOL display name formatting
     * 
     * @return Full name (first + last) or empty string if both are null
     */
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return "";
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
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

    /**
     * Convenience method to check if user has administrative privileges
     * 
     * @return true if role code is 'A' (Administrative)
     */
    public boolean isAdmin() {
        return "A".equals(roleCode);
    }

    /**
     * Convenience method to check if user has regular user privileges
     * 
     * @return true if role code is 'U' (Regular User)
     */
    public boolean isRegularUser() {
        return "U".equals(roleCode);
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
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

    public LocalDateTime getLastSignonDate() {
        return lastSignonDate;
    }

    public void setLastSignonDate(LocalDateTime lastSignonDate) {
        this.lastSignonDate = lastSignonDate;
    }

    public LocalDateTime getLastSignonTime() {
        return lastSignonTime;
    }

    public void setLastSignonTime(LocalDateTime lastSignonTime) {
        this.lastSignonTime = lastSignonTime;
    }

    public Long getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Long versionNumber) {
        this.versionNumber = versionNumber;
    }

    // Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return Objects.equals(userId, user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    /**
     * String representation excluding sensitive password information
     * Provides safe logging and debugging output
     * 
     * @return String representation of user without password hash
     */
    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", roleCode='" + roleCode + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", lastSignonDate=" + lastSignonDate +
                ", versionNumber=" + versionNumber +
                '}';
    }
}