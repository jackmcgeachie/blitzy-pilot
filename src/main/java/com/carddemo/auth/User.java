/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.auth;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * JPA entity representing the users table in PostgreSQL database.
 * 
 * Maps the legacy COBOL SEC-USER-DATA structure from CSUSR01Y.cpy to modern 
 * object-relational model. Provides user identity, authentication credentials, 
 * role information, and account status management with proper JPA annotations 
 * and validation constraints.
 *
 * <p>COBOL Structure Mapping:</p>
 * <ul>
 *   <li>SEC-USR-ID (PIC X(08)) → username (VARCHAR(50))</li>
 *   <li>SEC-USR-FNAME (PIC X(20)) → firstName (VARCHAR(50))</li>
 *   <li>SEC-USR-LNAME (PIC X(20)) → lastName (VARCHAR(50))</li>
 *   <li>SEC-USR-PWD (PIC X(08)) → passwordHash (VARCHAR(255) BCrypt)</li>
 *   <li>SEC-USR-TYPE (PIC X(01)) → roleCode (CHAR(1) 'A'/'U')</li>
 * </ul>
 *
 * <p>Security Integration:</p>
 * <ul>
 *   <li>Implements Spring Security UserDetails for authentication</li>
 *   <li>BCrypt password hashing with strength factor 10</li>
 *   <li>Role-based authorization ('A' = ROLE_ADMIN, 'U' = ROLE_USER)</li>
 *   <li>Account status management (ACTIVE, INACTIVE, LOCKED)</li>
 * </ul>
 *
 * @author CardDemo Development Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "users", 
       uniqueConstraints = {
           @UniqueConstraint(name = "UK_users_username", columnNames = "username")
       },
       indexes = {
           @Index(name = "IDX_users_role_code", columnList = "role_code"),
           @Index(name = "IDX_users_status", columnList = "status"),
           @Index(name = "IDX_users_created_at", columnList = "created_at")
       })
public class User implements UserDetails {

    /**
     * User account status enumeration.
     * Provides typed account status management for user accounts.
     */
    public enum UserStatus {
        ACTIVE("ACTIVE"),
        INACTIVE("INACTIVE"), 
        LOCKED("LOCKED");

        private final String value;

        UserStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static UserStatus fromValue(String value) {
            for (UserStatus status : UserStatus.values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Invalid user status: " + value);
        }
    }

    /**
     * User role code enumeration.
     * Maps to legacy RACF user types for authorization.
     */
    public enum RoleCode {
        ADMIN("A", "ROLE_ADMIN"),
        USER("U", "ROLE_USER");

        private final String code;
        private final String authority;

        RoleCode(String code, String authority) {
            this.code = code;
            this.authority = authority;
        }

        public String getCode() {
            return code;
        }

        public String getAuthority() {
            return authority;
        }

        public static RoleCode fromCode(String code) {
            for (RoleCode role : RoleCode.values()) {
                if (role.code.equals(code)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("Invalid role code: " + code);
        }
    }

    @Id
    @Column(name = "user_id", nullable = false, length = 50)
    @NotBlank(message = "User ID cannot be blank")
    @Size(min = 1, max = 50, message = "User ID must be between 1 and 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "User ID can only contain alphanumeric characters, underscores, and hyphens")
    private String userId;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username can only contain alphanumeric characters, underscores, dots, and hyphens")
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    @NotBlank(message = "Password hash cannot be blank")
    @Size(max = 255, message = "Password hash cannot exceed 255 characters")
    private String passwordHash;

    @Column(name = "first_name", length = 50)
    @Size(max = 50, message = "First name cannot exceed 50 characters")
    private String firstName;

    @Column(name = "last_name", length = 50)
    @Size(max = 50, message = "Last name cannot exceed 50 characters")
    private String lastName;

    @Column(name = "role_code", nullable = false, length = 1)
    @NotBlank(message = "Role code cannot be blank")
    @Pattern(regexp = "^[AU]$", message = "Role code must be 'A' (Admin) or 'U' (User)")
    private String roleCode;

    @Column(name = "status", nullable = false, length = 20)
    @NotBlank(message = "Status cannot be blank")
    @Pattern(regexp = "^(ACTIVE|INACTIVE|LOCKED)$", message = "Status must be ACTIVE, INACTIVE, or LOCKED")
    private String status = UserStatus.ACTIVE.getValue();

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /**
     * Default constructor for JPA.
     */
    public User() {
        // Default constructor required by JPA
    }

    /**
     * Constructor for creating new user instances.
     *
     * @param userId The unique user identifier
     * @param username The unique username for authentication
     * @param passwordHash The BCrypt-hashed password
     * @param firstName The user's first name
     * @param lastName The user's last name
     * @param roleCode The user's role code ('A' for Admin, 'U' for User)
     */
    public User(String userId, String username, String passwordHash, 
                String firstName, String lastName, String roleCode) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.roleCode = roleCode;
        this.status = UserStatus.ACTIVE.getValue();
    }

    // Getters and Setters

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
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

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UserStatus getUserStatus() {
        return UserStatus.fromValue(this.status);
    }

    public void setUserStatus(UserStatus userStatus) {
        this.status = userStatus.getValue();
    }

    public RoleCode getRole() {
        return RoleCode.fromCode(this.roleCode);
    }

    public void setRole(RoleCode role) {
        this.roleCode = role.getCode();
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    // UserDetails interface implementation for Spring Security

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority = getRole().getAuthority();
        return Collections.singletonList(new SimpleGrantedAuthority(authority));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Account expiration not implemented per legacy compatibility
    }

    @Override
    public boolean isAccountNonLocked() {
        return !UserStatus.LOCKED.equals(getUserStatus());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Credential expiration not implemented per legacy compatibility
    }

    @Override
    public boolean isEnabled() {
        return UserStatus.ACTIVE.equals(getUserStatus());
    }

    // Business Logic Methods

    /**
     * Gets the user's full name by combining first and last names.
     *
     * @return The user's full name, or username if names are not available
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName.trim() + " " + lastName.trim();
        } else if (firstName != null) {
            return firstName.trim();
        } else if (lastName != null) {
            return lastName.trim();
        } else {
            return username;
        }
    }

    /**
     * Checks if the user has administrative privileges.
     *
     * @return true if the user is an administrator
     */
    public boolean isAdmin() {
        return RoleCode.ADMIN.equals(getRole());
    }

    /**
     * Checks if the user account is active.
     *
     * @return true if the account status is ACTIVE
     */
    public boolean isActive() {
        return UserStatus.ACTIVE.equals(getUserStatus());
    }

    /**
     * Activates the user account.
     */
    public void activate() {
        setUserStatus(UserStatus.ACTIVE);
    }

    /**
     * Deactivates the user account.
     */
    public void deactivate() {
        setUserStatus(UserStatus.INACTIVE);
    }

    /**
     * Locks the user account.
     */
    public void lock() {
        setUserStatus(UserStatus.LOCKED);
    }

    // toString, equals, and hashCode

    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", roleCode='" + roleCode + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", version=" + version +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        User user = (User) obj;
        return Objects.equals(userId, user.userId) &&
               Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username);
    }
}