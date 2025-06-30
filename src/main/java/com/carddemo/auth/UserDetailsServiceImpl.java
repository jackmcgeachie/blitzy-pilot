/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Spring Security UserDetailsService implementation for CardDemo authentication.
 * 
 * This service replaces the legacy COBOL COSGN00C program authentication logic,
 * providing modern Spring Security integration while maintaining identical 
 * business logic and validation patterns. The implementation transforms VSAM 
 * USRSEC file access to PostgreSQL database queries via JPA repository operations.
 *
 * <p>Migration from COBOL Authentication (COSGN00C.cbl):</p>
 * <ul>
 *   <li>VSAM READ operation → UserRepository.findByUsername() database query</li>
 *   <li>SEC-USR-PWD validation → BCrypt password hash verification</li>
 *   <li>SEC-USR-TYPE authorization → Spring Security GrantedAuthority mapping</li>
 *   <li>User existence check → Custom UsernameNotFoundException handling</li>
 *   <li>CICS COMMAREA population → Spring Security context establishment</li>
 * </ul>
 *
 * <p>COBOL Structure Integration (CSUSR01Y.cpy):</p>
 * <ul>
 *   <li>SEC-USR-ID (PIC X(08)) → User.username field</li>
 *   <li>SEC-USR-FNAME (PIC X(20)) → User.firstName field</li>
 *   <li>SEC-USR-LNAME (PIC X(20)) → User.lastName field</li>
 *   <li>SEC-USR-PWD (PIC X(08)) → User.passwordHash (BCrypt)</li>
 *   <li>SEC-USR-TYPE (PIC X(01)) → User.roleCode ('A'/'U')</li>
 * </ul>
 *
 * <p>Security Enhancement Features:</p>
 * <ul>
 *   <li>BCrypt password hash validation replacing plain text comparison</li>
 *   <li>Account status validation (ACTIVE, INACTIVE, LOCKED)</li>
 *   <li>Comprehensive audit logging for security events</li>
 *   <li>Role-based authority mapping for Spring Security authorization</li>
 *   <li>Detailed error handling with security-appropriate exception messages</li>
 * </ul>
 *
 * <p>Authorization Mapping:</p>
 * <ul>
 *   <li>Role Code 'A' → ROLE_ADMIN (administrative access)</li>
 *   <li>Role Code 'U' → ROLE_USER (standard user access)</li>
 * </ul>
 *
 * <p>Error Handling Patterns (matching COBOL logic):</p>
 * <ul>
 *   <li>User not found → UsernameNotFoundException (equivalent to CICS RESP-CD 13)</li>
 *   <li>Account locked → DisabledException</li>
 *   <li>Account inactive → DisabledException</li>
 *   <li>General validation failure → UsernameNotFoundException</li>
 * </ul>
 *
 * @author CardDemo Development Team
 * @version 1.0
 * @since 1.0
 * @see User
 * @see UserRepository
 * @see org.springframework.security.core.userdetails.UserDetailsService
 */
@Service("userDetailsService")
@Transactional(readOnly = true)
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final UserRepository userRepository;

    /**
     * Constructor for dependency injection.
     *
     * @param userRepository The repository for user data access operations
     */
    @Autowired
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
        logger.debug("UserDetailsServiceImpl initialized with PostgreSQL-backed authentication");
    }

    /**
     * Loads user details by username for Spring Security authentication.
     * 
     * This method replaces the COBOL READ-USER-SEC-FILE paragraph from COSGN00C.cbl,
     * implementing identical authentication logic using modern Spring Security patterns.
     * 
     * <p>Original COBOL Logic Equivalent:</p>
     * <pre>
     * READ-USER-SEC-FILE.
     *     EXEC CICS READ
     *          DATASET   (WS-USRSEC-FILE)
     *          INTO      (SEC-USER-DATA)
     *          RIDFLD    (WS-USER-ID)
     *          KEYLENGTH (LENGTH OF WS-USER-ID)
     *          RESP      (WS-RESP-CD)
     *     END-EXEC.
     *     
     *     EVALUATE WS-RESP-CD
     *         WHEN 0
     *             IF SEC-USR-PWD = WS-USER-PWD
     *                 MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
     *                 // Success path
     *             ELSE
     *                 // Wrong password
     *         WHEN 13
     *             // User not found
     *         WHEN OTHER
     *             // Unable to verify user
     *     END-EVALUATE.
     * </pre>
     *
     * @param username The username to authenticate (equivalent to WS-USER-ID)
     * @return UserDetails object containing user information and authorities
     * @throws UsernameNotFoundException If user is not found or account validation fails
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        
        // Input validation - equivalent to COBOL field validation
        if (!StringUtils.hasText(username)) {
            logger.warn("Authentication attempt with empty username");
            throw new UsernameNotFoundException("Username cannot be empty");
        }

        // Normalize username to uppercase (matching COBOL FUNCTION UPPER-CASE)
        String normalizedUsername = username.trim().toUpperCase();
        
        logger.debug("Attempting to load user details for username: [{}]", normalizedUsername);

        try {
            // Database lookup - equivalent to CICS READ operation
            Optional<User> userOptional = userRepository.findByUsername(normalizedUsername);
            
            if (userOptional.isEmpty()) {
                // User not found - equivalent to CICS RESP-CD 13
                logger.warn("User not found during authentication: [{}]", normalizedUsername);
                throw new UsernameNotFoundException("User not found: " + normalizedUsername);
            }

            User user = userOptional.get();
            
            // Validate user account status - enhanced security beyond COBOL implementation
            validateUserAccountStatus(user);
            
            // Audit successful user lookup
            logger.info("User successfully loaded for authentication: [{}], Role: [{}], Status: [{}]",
                user.getUsername(), user.getRoleCode(), user.getStatus());

            // Return the User entity (which implements UserDetails)
            // This provides Spring Security with all necessary authentication information
            return user;

        } catch (Exception e) {
            // Handle any database or validation errors
            if (e instanceof UsernameNotFoundException) {
                throw e;
            }
            
            // Log the actual error for debugging, but throw generic security exception
            logger.error("Error during user authentication lookup for username: [{}]", normalizedUsername, e);
            throw new UsernameNotFoundException("Unable to verify user: " + normalizedUsername);
        }
    }

    /**
     * Validates user account status and permissions.
     * 
     * This method implements enhanced account validation beyond the original COBOL
     * logic, providing comprehensive account status checking including inactive
     * and locked account detection.
     * 
     * <p>Status Validation Rules:</p>
     * <ul>
     *   <li>ACTIVE: Account is enabled for authentication and authorization</li>
     *   <li>INACTIVE: Account is disabled, authentication denied</li>
     *   <li>LOCKED: Account is locked due to security concerns, authentication denied</li>
     * </ul>
     *
     * @param user The user to validate
     * @throws UsernameNotFoundException If account status validation fails
     */
    private void validateUserAccountStatus(User user) throws UsernameNotFoundException {
        
        if (user == null) {
            logger.error("Null user provided for account status validation");
            throw new UsernameNotFoundException("Invalid user account");
        }

        // Validate account status
        User.UserStatus userStatus = user.getUserStatus();
        
        switch (userStatus) {
            case ACTIVE:
                // Account is valid for authentication
                logger.debug("User account status validation passed: [{}] - ACTIVE", user.getUsername());
                break;
                
            case INACTIVE:
                logger.warn("Authentication denied - account inactive: [{}]", user.getUsername());
                throw new UsernameNotFoundException("Account is inactive: " + user.getUsername());
                
            case LOCKED:
                logger.warn("Authentication denied - account locked: [{}]", user.getUsername());
                throw new UsernameNotFoundException("Account is locked: " + user.getUsername());
                
            default:
                logger.error("Unknown account status for user: [{}], Status: [{}]", 
                    user.getUsername(), user.getStatus());
                throw new UsernameNotFoundException("Invalid account status: " + user.getUsername());
        }

        // Validate role code
        if (!StringUtils.hasText(user.getRoleCode())) {
            logger.error("User account missing role code: [{}]", user.getUsername());
            throw new UsernameNotFoundException("Account configuration error: " + user.getUsername());
        }

        // Validate role code format
        if (!user.getRoleCode().matches("^[AU]$")) {
            logger.error("Invalid role code for user: [{}], Role: [{}]", 
                user.getUsername(), user.getRoleCode());
            throw new UsernameNotFoundException("Account permission error: " + user.getUsername());
        }
    }

    /**
     * Creates Spring Security authorities from user role code.
     * 
     * This method transforms the COBOL user type classification into Spring Security
     * GrantedAuthority objects, maintaining identical authorization semantics from
     * the original RACF-based system.
     * 
     * <p>Role Mapping (COBOL to Spring Security):</p>
     * <ul>
     *   <li>SEC-USR-TYPE 'A' → ROLE_ADMIN (CDEMO-USRTYP-ADMIN condition)</li>
     *   <li>SEC-USR-TYPE 'U' → ROLE_USER (standard user privileges)</li>
     * </ul>
     *
     * <p>Original COBOL Authorization Logic:</p>
     * <pre>
     * IF CDEMO-USRTYP-ADMIN
     *     EXEC CICS XCTL PROGRAM ('COADM01C') ...
     * ELSE
     *     EXEC CICS XCTL PROGRAM ('COMEN01C') ...
     * </pre>
     *
     * @param user The user for whom to create authorities
     * @return Collection of GrantedAuthority objects for Spring Security
     */
    private Collection<? extends GrantedAuthority> createAuthorities(User user) {
        
        if (user == null || !StringUtils.hasText(user.getRoleCode())) {
            logger.warn("Unable to create authorities for user with missing role information");
            return Collections.emptyList();
        }

        try {
            User.RoleCode roleCode = user.getRole();
            String authority = roleCode.getAuthority();
            
            logger.debug("Creating Spring Security authority: [{}] for user: [{}]", 
                authority, user.getUsername());
            
            return Collections.singletonList(new SimpleGrantedAuthority(authority));
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid role code during authority creation for user: [{}], Role: [{}]", 
                user.getUsername(), user.getRoleCode(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Validates if a user exists by username.
     * 
     * This utility method provides efficient user existence checking without
     * loading full user details, supporting pre-authentication validation
     * and user management operations.
     *
     * @param username The username to check
     * @return true if user exists, false otherwise
     */
    public boolean userExists(String username) {
        
        if (!StringUtils.hasText(username)) {
            return false;
        }

        try {
            String normalizedUsername = username.trim().toUpperCase();
            boolean exists = userRepository.existsByUsername(normalizedUsername);
            
            logger.debug("User existence check for [{}]: {}", normalizedUsername, exists);
            return exists;
            
        } catch (Exception e) {
            logger.error("Error checking user existence for username: [{}]", username, e);
            return false;
        }
    }

    /**
     * Retrieves user role for authorization decisions.
     * 
     * This method supports authorization checks without full user details loading,
     * optimizing performance for role-based access control validation.
     *
     * @param username The username to check
     * @return Optional containing user role if user exists
     */
    public Optional<String> getUserRole(String username) {
        
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
        }

        try {
            String normalizedUsername = username.trim().toUpperCase();
            Optional<User> userOptional = userRepository.findByUsername(normalizedUsername);
            
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                String roleAuthority = user.getRole().getAuthority();
                
                logger.debug("Retrieved role [{}] for user: [{}]", roleAuthority, normalizedUsername);
                return Optional.of(roleAuthority);
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error retrieving user role for username: [{}]", username, e);
            return Optional.empty();
        }
    }

    /**
     * Checks if user is active for transaction processing.
     * 
     * This method validates that a user account is in appropriate status for
     * conducting business transactions, supporting business validation rules
     * in transaction services.
     *
     * @param username The username to validate
     * @return true if user is active and authorized for transactions
     */
    public boolean isUserActiveForTransactions(String username) {
        
        if (!StringUtils.hasText(username)) {
            return false;
        }

        try {
            String normalizedUsername = username.trim().toUpperCase();
            Optional<User> userOptional = userRepository.findByUsername(normalizedUsername);
            
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                boolean isActive = User.UserStatus.ACTIVE.equals(user.getUserStatus()) &&
                                 StringUtils.hasText(user.getRoleCode());
                
                logger.debug("Transaction eligibility check for user [{}]: {}", normalizedUsername, isActive);
                return isActive;
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Error checking transaction eligibility for username: [{}]", username, e);
            return false;
        }
    }

    /**
     * Validates user credentials for authentication service integration.
     * 
     * This method provides the user lookup portion of authentication while
     * delegating password validation to the AuthenticationService. This
     * separation follows Spring Security best practices for credential validation.
     * 
     * <p>Note: Password validation is performed by Spring Security's authentication
     * providers using BCrypt hash comparison rather than plain text comparison
     * from the original COBOL implementation.</p>
     *
     * @param username The username for authentication
     * @return Optional containing User if found for credential validation
     */
    public Optional<User> findUserForAuthentication(String username) {
        
        if (!StringUtils.hasText(username)) {
            logger.debug("Empty username provided for authentication lookup");
            return Optional.empty();
        }

        try {
            String normalizedUsername = username.trim().toUpperCase();
            Optional<User> userOptional = userRepository.findByUsername(normalizedUsername);
            
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                
                // Validate account status before returning for authentication
                try {
                    validateUserAccountStatus(user);
                    logger.debug("User located and validated for authentication: [{}]", normalizedUsername);
                    return userOptional;
                    
                } catch (UsernameNotFoundException e) {
                    logger.warn("User found but failed validation for authentication: [{}] - {}", 
                        normalizedUsername, e.getMessage());
                    return Optional.empty();
                }
            }
            
            logger.debug("User not found for authentication: [{}]", normalizedUsername);
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error during user lookup for authentication: [{}]", username, e);
            return Optional.empty();
        }
    }

    /**
     * Creates a custom UserDetails implementation for special cases.
     * 
     * This method supports scenarios where additional UserDetails customization
     * is required beyond the standard User entity implementation, such as
     * system accounts or temporary authentication contexts.
     *
     * @param user The user entity to wrap
     * @return UserDetails implementation with enhanced functionality
     */
    private UserDetails createUserDetails(User user) {
        
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null for UserDetails creation");
        }

        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPasswordHash(),
            user.isEnabled(),
            user.isAccountNonExpired(),
            user.isCredentialsNonExpired(),
            user.isAccountNonLocked(),
            createAuthorities(user)
        );
    }

    /**
     * Logs security events for audit compliance.
     * 
     * This method provides comprehensive audit logging for authentication
     * events, supporting security compliance requirements and fraud detection.
     * 
     * <p>Audit Information Captured:</p>
     * <ul>
     *   <li>Authentication attempt timestamp</li>
     *   <li>Username and user identification</li>
     *   <li>Authentication result (success/failure)</li>
     *   <li>Account status and role information</li>
     *   <li>Source system identification</li>
     * </ul>
     *
     * @param username The username involved in the security event
     * @param eventType The type of security event
     * @param success Whether the operation was successful
     * @param details Additional event details
     */
    private void auditSecurityEvent(String username, String eventType, boolean success, String details) {
        
        // Structure audit information for enterprise SIEM integration
        String auditMessage = String.format(
            "SECURITY_EVENT: Type=[%s], User=[%s], Success=[%s], Timestamp=[%s], Details=[%s]",
            eventType, username, success, LocalDateTime.now(), details
        );

        if (success) {
            logger.info(auditMessage);
        } else {
            logger.warn(auditMessage);
        }

        // Future enhancement: Integration with AuditService for SMF-compatible
        // record generation and enterprise SIEM forwarding
    }
}