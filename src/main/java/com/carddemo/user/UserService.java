/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Core business logic service implementing COBOL user management programs (COUSR00C-03C) 
 * as Spring Boot service methods with comprehensive user CRUD operations, Spring Security 
 * integration, audit logging, and transaction management.
 * 
 * <p>This service provides enterprise-grade user management functionality that maintains
 * functional equivalence with the original mainframe COBOL programs while leveraging
 * modern Spring Boot patterns, BCrypt password security, and comprehensive audit trails.</p>
 * 
 * <p><strong>COBOL Program Mappings:</strong></p>
 * <ul>
 *   <li>COUSR00C (List users) → {@link #findUsers(Pageable)} - Paginated user retrieval with Spring Data Pageable</li>
 *   <li>COUSR01C (Add user) → {@link #createUser(UserDto)} - User creation with BCrypt password hashing</li>
 *   <li>COUSR02C (Update user) → {@link #updateUser(String, UserDto)} - User modification with optimistic locking</li>
 *   <li>COUSR03C (Delete user) → {@link #deleteUser(String)} - User deletion with audit trail generation</li>
 * </ul>
 * 
 * <p><strong>Security Integration:</strong></p>
 * <ul>
 *   <li>Implements Spring Security {@link UserDetailsService} for authentication integration</li>
 *   <li>BCrypt password hashing with strength factor 10 for secure password storage</li>
 *   <li>Role-based authorization with RACF-equivalent mapping (ROLE_ADMIN, ROLE_USER)</li>
 *   <li>Comprehensive audit logging through AuditService integration</li>
 * </ul>
 * 
 * <p><strong>Data Integrity:</strong></p>
 * <ul>
 *   <li>@Transactional boundaries matching CICS LUW (Logical Unit of Work) patterns</li>
 *   <li>Bean Validation API matching COBOL 88-level field validation conditions</li>
 *   <li>Optimistic locking through JPA @Version annotations for concurrent access control</li>
 *   <li>PostgreSQL ACID compliance with automatic rollback on exceptions</li>
 * </ul>
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 * 
 * @see User Entity class for data model
 * @see UserRepository Spring Data JPA repository interface
 * @see UserDto Data Transfer Object for API interactions
 * @see UserMapper Entity/DTO mapping utilities
 * @see UserValidator Custom validation logic
 */
@Service
@Validated
@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
public class UserService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    /**
     * Repository for user data access operations using Spring Data JPA.
     * Provides CRUD operations and custom query methods for PostgreSQL users table.
     */
    @Autowired
    private UserRepository userRepository;

    /**
     * Data Transfer Object mapper for entity/DTO conversions.
     * Handles bidirectional mapping between User entities and UserDto objects.
     */
    @Autowired
    private UserMapper userMapper;

    /**
     * Custom validator for user-specific business rules.
     * Implements COBOL 88-level validation conditions and business constraints.
     */
    @Autowired
    private UserValidator userValidator;

    /**
     * BCrypt password encoder for secure password hashing.
     * Configured with strength factor 10 for optimal security/performance balance.
     */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Audit service for comprehensive security event logging.
     * Captures all user management operations for compliance and security monitoring.
     */
    @Autowired
    private AuditService auditService;

    // ==================================================================================
    // SPRING SECURITY USERDETAILSSERVICE IMPLEMENTATION
    // ==================================================================================

    /**
     * Loads user details for Spring Security authentication.
     * 
     * <p>This method integrates with Spring Security's authentication framework to provide
     * user details for JWT token generation and validation. It maps database user records
     * to Spring Security UserDetails objects with appropriate authorities.</p>
     * 
     * <p><strong>RACF Authority Mapping:</strong></p>
     * <ul>
     *   <li>User Type 'A' (Administrative) → ROLE_ADMIN authority</li>
     *   <li>User Type 'U' (Regular User) → ROLE_USER authority</li>
     * </ul>
     * 
     * <p><strong>Security Features:</strong></p>
     * <ul>
     *   <li>Account status validation (only ACTIVE users can authenticate)</li>
     *   <li>Password verification through BCrypt checkpw() method</li>
     *   <li>Comprehensive audit logging of authentication attempts</li>
     * </ul>
     * 
     * @param username The unique username for authentication lookup
     * @return UserDetails object containing user information and authorities
     * @throws UsernameNotFoundException if user is not found or inactive
     * 
     * @see org.springframework.security.core.userdetails.UserDetailsService#loadUserByUsername(String)
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.debug("Loading user details for authentication: username={}", username);

        // Find user by username - equivalent to COBOL READ operation
        Optional<User> userOptional = userRepository.findByUsernameAndStatus(username, "ACTIVE");
        
        if (userOptional.isEmpty()) {
            logger.warn("Authentication failed - user not found or inactive: username={}", username);
            auditService.logSecurityEvent("USER_AUTHENTICATION_FAILED", username, "User not found or inactive");
            throw new UsernameNotFoundException("User not found or inactive: " + username);
        }

        User user = userOptional.get();
        
        // Build Spring Security authorities from user type - RACF mapping equivalent
        Collection<GrantedAuthority> authorities = buildUserAuthorities(user.getUserType());
        
        logger.debug("User details loaded successfully: username={}, userType={}, authorities={}", 
                    username, user.getUserType(), authorities);
        
        auditService.logSecurityEvent("USER_DETAILS_LOADED", username, 
                                     "User details loaded for authentication, userType=" + user.getUserType());

        // Return Spring Security UserDetails implementation
        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPasswordHash(), // BCrypt hashed password
            true, // account enabled
            true, // account not expired
            true, // credentials not expired
            true, // account not locked
            authorities
        );
    }

    /**
     * Builds Spring Security authorities from COBOL user type classification.
     * 
     * <p>This method implements one-to-one mapping between legacy RACF user types
     * and Spring Security authorities, maintaining identical functional permissions:</p>
     * 
     * <ul>
     *   <li>RACF User Type 'A' → Spring Security ROLE_ADMIN</li>
     *   <li>RACF User Type 'U' → Spring Security ROLE_USER</li>
     * </ul>
     * 
     * @param userType Single character user type from database ('A' or 'U')
     * @return Collection of GrantedAuthority objects for Spring Security
     */
    private Collection<GrantedAuthority> buildUserAuthorities(String userType) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        
        // Map COBOL user types to Spring Security authorities - maintain RACF compatibility
        switch (userType) {
            case "A": // Administrative User - full system access
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                authorities.add(new SimpleGrantedAuthority("ROLE_USER")); // Admin includes user privileges
                break;
            case "U": // Regular User - standard operations
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                break;
            default:
                logger.warn("Unknown user type encountered: userType={}", userType);
                // Default to no authorities for unknown user types
                break;
        }
        
        return authorities;
    }

    // ==================================================================================
    // USER CRUD OPERATIONS - COBOL PROGRAM EQUIVALENTS
    // ==================================================================================

    /**
     * Retrieves paginated list of users - equivalent to COUSR00C COBOL program.
     * 
     * <p>This method implements the functionality of the original COUSR00C program which
     * displayed users in pages of 10 records with navigation support. The Spring Boot
     * implementation uses Spring Data Pageable for efficient pagination and supports
     * flexible page sizes and sorting options.</p>
     * 
     * <p><strong>COBOL Program Equivalent:</strong> COUSR00C.cbl</p>
     * <ul>
     *   <li>Original: CICS STARTBR/READNEXT pagination with 10 records per page</li>
     *   <li>Modern: Spring Data Pageable with configurable page size and sorting</li>
     *   <li>Performance: PostgreSQL LIMIT/OFFSET queries with composite index optimization</li>
     * </ul>
     * 
     * <p><strong>Security Integration:</strong></p>
     * <ul>
     *   <li>Read-only transaction for optimal performance</li>
     *   <li>Audit logging of user list access operations</li>
     *   <li>Authority validation handled at REST controller level</li>
     * </ul>
     * 
     * @param pageable Pagination and sorting parameters (page number, size, sort criteria)
     * @return Page of UserDto objects with pagination metadata
     * 
     * @see org.springframework.data.domain.Pageable
     * @see org.springframework.data.domain.Page
     */
    @Transactional(readOnly = true)
    public Page<UserDto> findUsers(Pageable pageable) {
        logger.debug("Retrieving paginated user list: page={}, size={}, sort={}", 
                    pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());

        try {
            // Execute paginated query - equivalent to COBOL STARTBR/READNEXT pattern
            Page<User> userPage = userRepository.findAll(pageable);
            
            // Convert entities to DTOs for API response
            Page<UserDto> userDtoPage = userPage.map(userMapper::toDto);
            
            logger.info("Successfully retrieved user list: totalElements={}, totalPages={}, currentPage={}", 
                       userDtoPage.getTotalElements(), userDtoPage.getTotalPages(), userDtoPage.getNumber());
            
            // Audit log for user list access
            auditService.logDataAccess("USER_LIST_ACCESSED", null, 
                                      "User list retrieved, page=" + pageable.getPageNumber() + 
                                      ", size=" + pageable.getPageSize() + 
                                      ", totalElements=" + userDtoPage.getTotalElements());
            
            return userDtoPage;

        } catch (Exception e) {
            logger.error("Error retrieving user list: page={}, size={}", 
                        pageable.getPageNumber(), pageable.getPageSize(), e);
            auditService.logError("USER_LIST_ERROR", null, "Failed to retrieve user list: " + e.getMessage());
            throw new UserServiceException("Unable to retrieve user list", e);
        }
    }

    /**
     * Creates a new user - equivalent to COUSR01C COBOL program.
     * 
     * <p>This method implements the functionality of the original COUSR01C program which
     * added new users to the USRSEC VSAM file. The Spring Boot implementation provides
     * enhanced security through BCrypt password hashing and comprehensive validation.</p>
     * 
     * <p><strong>COBOL Program Equivalent:</strong> COUSR01C.cbl</p>
     * <ul>
     *   <li>Original: CICS WRITE operation to USRSEC dataset with duplicate key checking</li>
     *   <li>Modern: JPA save operation with PostgreSQL unique constraints and validation</li>
     *   <li>Security: BCrypt password hashing replaces plain text password storage</li>
     * </ul>
     * 
     * <p><strong>Validation Rules (COBOL 88-level equivalent):</strong></p>
     * <ul>
     *   <li>Username: Required, unique, 1-50 characters, alphanumeric</li>
     *   <li>First Name: Required, 1-50 characters</li>
     *   <li>Last Name: Required, 1-50 characters</li>
     *   <li>Password: Required, minimum 8 characters</li>
     *   <li>User Type: Required, must be 'A' (Admin) or 'U' (User)</li>
     * </ul>
     * 
     * <p><strong>Security Features:</strong></p>
     * <ul>
     *   <li>BCrypt password hashing with strength factor 10</li>
     *   <li>Comprehensive audit logging of user creation</li>
     *   <li>Duplicate username detection and handling</li>
     *   <li>Transaction rollback on validation or persistence errors</li>
     * </ul>
     * 
     * @param userDto User data transfer object containing user information
     * @return Created UserDto with generated user ID and audit information
     * @throws UserServiceException if validation fails or user already exists
     * @throws IllegalArgumentException if required fields are missing or invalid
     * 
     * @see jakarta.validation.Valid
     */
    public UserDto createUser(@Valid UserDto userDto) {
        logger.debug("Creating new user: username={}, userType={}", 
                    userDto.getUsername(), userDto.getUserType());

        try {
            // Comprehensive validation - equivalent to COBOL field validation
            userValidator.validateForCreation(userDto);
            
            // Check for duplicate username - equivalent to COBOL DUPKEY condition
            if (userRepository.existsByUsername(userDto.getUsername())) {
                logger.warn("User creation failed - username already exists: username={}", userDto.getUsername());
                auditService.logSecurityEvent("USER_CREATION_FAILED", userDto.getUsername(), "Username already exists");
                throw new UserServiceException("Username already exists: " + userDto.getUsername());
            }
            
            // Convert DTO to entity
            User user = userMapper.toEntity(userDto);
            
            // Set system-generated fields
            user.setUserId(generateUserId());
            user.setPasswordHash(passwordEncoder.encode(userDto.getPassword())); // BCrypt hashing
            user.setStatus("ACTIVE");
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            user.setVersionNumber(0L); // Initialize optimistic locking version
            
            // Save to database - equivalent to COBOL WRITE operation
            User savedUser = userRepository.save(user);
            
            logger.info("User created successfully: userId={}, username={}, userType={}", 
                       savedUser.getUserId(), savedUser.getUsername(), savedUser.getUserType());
            
            // Comprehensive audit logging for user creation
            auditService.logSecurityEvent("USER_CREATED", savedUser.getUsername(), 
                                         "New user created successfully, userId=" + savedUser.getUserId() + 
                                         ", userType=" + savedUser.getUserType());
            
            // Convert back to DTO for response (excluding password hash)
            return userMapper.toDto(savedUser);

        } catch (UserServiceException e) {
            // Re-throw service exceptions without wrapping
            throw e;
        } catch (Exception e) {
            logger.error("Error creating user: username={}", userDto.getUsername(), e);
            auditService.logError("USER_CREATION_ERROR", userDto.getUsername(), 
                                 "Failed to create user: " + e.getMessage());
            throw new UserServiceException("Unable to create user", e);
        }
    }

    /**
     * Updates an existing user - equivalent to COUSR02C COBOL program.
     * 
     * <p>This method implements the functionality of the original COUSR02C program which
     * updated user records in the USRSEC VSAM file. The Spring Boot implementation uses
     * optimistic locking for concurrent access control and maintains change tracking.</p>
     * 
     * <p><strong>COBOL Program Equivalent:</strong> COUSR02C.cbl</p>
     * <ul>
     *   <li>Original: CICS READ UPDATE followed by REWRITE operation</li>
     *   <li>Modern: JPA findById with optimistic locking and save operation</li>
     *   <li>Concurrency: @Version annotation replaces CICS record locking</li>
     * </ul>
     * 
     * <p><strong>Update Logic:</strong></p>
     * <ul>
     *   <li>Only modified fields are updated (preserves existing data)</li>
     *   <li>Password updates trigger BCrypt re-hashing</li>
     *   <li>Version number incremented for optimistic locking</li>
     *   <li>Updated timestamp automatically maintained</li>
     * </ul>
     * 
     * <p><strong>Security Features:</strong></p>
     * <ul>
     *   <li>User existence validation before update</li>
     *   <li>Optimistic locking conflict detection and resolution</li>
     *   <li>Comprehensive audit logging of field changes</li>
     *   <li>Password security through BCrypt re-hashing</li>
     * </ul>
     * 
     * @param userId Unique user identifier for update operation
     * @param userDto Updated user data (only modified fields need to be provided)
     * @return Updated UserDto with incremented version number
     * @throws UserServiceException if user not found or update conflicts occur
     * @throws OptimisticLockingFailureException if concurrent modification detected
     * 
     * @see org.springframework.orm.ObjectOptimisticLockingFailureException
     */
    public UserDto updateUser(@NotBlank @Size(max = 50) String userId, @Valid UserDto userDto) {
        logger.debug("Updating user: userId={}, username={}", userId, userDto.getUsername());

        try {
            // Find existing user - equivalent to COBOL READ UPDATE
            Optional<User> existingUserOpt = userRepository.findByUserId(userId);
            if (existingUserOpt.isEmpty()) {
                logger.warn("User update failed - user not found: userId={}", userId);
                auditService.logSecurityEvent("USER_UPDATE_FAILED", userId, "User not found");
                throw new UserServiceException("User not found: " + userId);
            }
            
            User existingUser = existingUserOpt.get();
            
            // Validation for update operation
            userValidator.validateForUpdate(userDto, existingUser);
            
            // Track changes for audit logging
            List<String> changes = new ArrayList<>();
            
            // Update only modified fields - equivalent to COBOL field-by-field comparison
            if (userDto.getFirstName() != null && !userDto.getFirstName().equals(existingUser.getFirstName())) {
                changes.add("firstName: " + existingUser.getFirstName() + " -> " + userDto.getFirstName());
                existingUser.setFirstName(userDto.getFirstName());
            }
            
            if (userDto.getLastName() != null && !userDto.getLastName().equals(existingUser.getLastName())) {
                changes.add("lastName: " + existingUser.getLastName() + " -> " + userDto.getLastName());
                existingUser.setLastName(userDto.getLastName());
            }
            
            if (userDto.getUserType() != null && !userDto.getUserType().equals(existingUser.getUserType())) {
                changes.add("userType: " + existingUser.getUserType() + " -> " + userDto.getUserType());
                existingUser.setUserType(userDto.getUserType());
            }
            
            // Password update requires BCrypt re-hashing
            if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
                changes.add("password: [UPDATED]"); // Don't log actual passwords
                existingUser.setPasswordHash(passwordEncoder.encode(userDto.getPassword()));
            }
            
            // Update system fields
            existingUser.setUpdatedAt(LocalDateTime.now());
            
            // Check if any changes were made
            if (changes.isEmpty()) {
                logger.info("No changes detected for user update: userId={}", userId);
                auditService.logDataAccess("USER_UPDATE_NO_CHANGES", userId, "No changes detected");
                return userMapper.toDto(existingUser);
            }
            
            // Save changes - equivalent to COBOL REWRITE operation
            User updatedUser = userRepository.save(existingUser);
            
            logger.info("User updated successfully: userId={}, changes={}", userId, changes);
            
            // Comprehensive audit logging for user update
            auditService.logSecurityEvent("USER_UPDATED", updatedUser.getUsername(), 
                                         "User updated successfully, changes: " + String.join(", ", changes));
            
            return userMapper.toDto(updatedUser);

        } catch (UserServiceException e) {
            // Re-throw service exceptions without wrapping
            throw e;
        } catch (Exception e) {
            logger.error("Error updating user: userId={}", userId, e);
            auditService.logError("USER_UPDATE_ERROR", userId, "Failed to update user: " + e.getMessage());
            throw new UserServiceException("Unable to update user", e);
        }
    }

    /**
     * Deletes a user - equivalent to COUSR03C COBOL program.
     * 
     * <p>This method implements the functionality of the original COUSR03C program which
     * deleted user records from the USRSEC VSAM file. The Spring Boot implementation
     * provides enhanced security through comprehensive audit logging and referential
     * integrity checking.</p>
     * 
     * <p><strong>COBOL Program Equivalent:</strong> COUSR03C.cbl</p>
     * <ul>
     *   <li>Original: CICS READ UPDATE followed by DELETE operation</li>
     *   <li>Modern: JPA findById with existence check and delete operation</li>
     *   <li>Safety: Referential integrity validation before deletion</li>
     * </ul>
     * 
     * <p><strong>Deletion Process:</strong></p>
     * <ul>
     *   <li>User existence validation</li>
     *   <li>Referential integrity checking (prevent deletion of referenced users)</li>
     *   <li>Comprehensive audit trail generation</li>
     *   <li>Physical record deletion from PostgreSQL</li>
     * </ul>
     * 
     * <p><strong>Security Features:</strong></p>
     * <ul>
     *   <li>User existence validation before deletion</li>
     *   <li>Comprehensive audit logging with user details</li>
     *   <li>Transaction rollback on deletion conflicts</li>
     *   <li>Referential integrity protection</li>
     * </ul>
     * 
     * @param userId Unique user identifier for deletion operation
     * @throws UserServiceException if user not found or deletion constraints violated
     * @throws IllegalArgumentException if userId is null or empty
     * 
     * @see org.springframework.dao.DataIntegrityViolationException
     */
    public void deleteUser(@NotBlank @Size(max = 50) String userId) {
        logger.debug("Deleting user: userId={}", userId);

        try {
            // Find existing user - equivalent to COBOL READ UPDATE
            Optional<User> existingUserOpt = userRepository.findByUserId(userId);
            if (existingUserOpt.isEmpty()) {
                logger.warn("User deletion failed - user not found: userId={}", userId);
                auditService.logSecurityEvent("USER_DELETION_FAILED", userId, "User not found");
                throw new UserServiceException("User not found: " + userId);
            }
            
            User existingUser = existingUserOpt.get();
            
            // Validation for deletion operation
            userValidator.validateForDeletion(existingUser);
            
            // Capture user details for audit logging before deletion
            String userDetails = String.format("userId=%s, username=%s, userType=%s, status=%s", 
                                              existingUser.getUserId(), existingUser.getUsername(), 
                                              existingUser.getUserType(), existingUser.getStatus());
            
            // Delete user - equivalent to COBOL DELETE operation
            userRepository.delete(existingUser);
            
            logger.info("User deleted successfully: {}", userDetails);
            
            // Comprehensive audit logging for user deletion
            auditService.logSecurityEvent("USER_DELETED", existingUser.getUsername(), 
                                         "User deleted successfully: " + userDetails);

        } catch (UserServiceException e) {
            // Re-throw service exceptions without wrapping
            throw e;
        } catch (Exception e) {
            logger.error("Error deleting user: userId={}", userId, e);
            auditService.logError("USER_DELETION_ERROR", userId, "Failed to delete user: " + e.getMessage());
            throw new UserServiceException("Unable to delete user", e);
        }
    }

    // ==================================================================================
    // USER LOOKUP AND VALIDATION METHODS
    // ==================================================================================

    /**
     * Finds a user by username for authentication and lookup operations.
     * 
     * <p>This method provides efficient user lookup by username, commonly used for
     * authentication, user detail retrieval, and validation operations. It includes
     * comprehensive error handling and audit logging.</p>
     * 
     * @param username Unique username for lookup
     * @return UserDto if found, Optional.empty() if not found
     * @throws IllegalArgumentException if username is null or empty
     */
    @Transactional(readOnly = true)
    public Optional<UserDto> findByUsername(@NotBlank @Size(max = 50) String username) {
        logger.debug("Finding user by username: username={}", username);

        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            
            if (userOpt.isPresent()) {
                UserDto userDto = userMapper.toDto(userOpt.get());
                logger.debug("User found by username: username={}, userId={}", username, userDto.getUserId());
                return Optional.of(userDto);
            } else {
                logger.debug("User not found by username: username={}", username);
                return Optional.empty();
            }

        } catch (Exception e) {
            logger.error("Error finding user by username: username={}", username, e);
            auditService.logError("USER_LOOKUP_ERROR", username, "Failed to find user by username: " + e.getMessage());
            throw new UserServiceException("Unable to find user by username", e);
        }
    }

    /**
     * Finds a user by user ID for update and deletion operations.
     * 
     * <p>This method provides direct user lookup by unique identifier, commonly used
     * for update, deletion, and detail retrieval operations.</p>
     * 
     * @param userId Unique user identifier
     * @return UserDto if found, Optional.empty() if not found
     * @throws IllegalArgumentException if userId is null or empty
     */
    @Transactional(readOnly = true)
    public Optional<UserDto> findByUserId(@NotBlank @Size(max = 50) String userId) {
        logger.debug("Finding user by user ID: userId={}", userId);

        try {
            Optional<User> userOpt = userRepository.findByUserId(userId);
            
            if (userOpt.isPresent()) {
                UserDto userDto = userMapper.toDto(userOpt.get());
                logger.debug("User found by ID: userId={}, username={}", userId, userDto.getUsername());
                return Optional.of(userDto);
            } else {
                logger.debug("User not found by ID: userId={}", userId);
                return Optional.empty();
            }

        } catch (Exception e) {
            logger.error("Error finding user by ID: userId={}", userId, e);
            auditService.logError("USER_LOOKUP_ERROR", userId, "Failed to find user by ID: " + e.getMessage());
            throw new UserServiceException("Unable to find user by ID", e);
        }
    }

    /**
     * Checks if a username is available for new user registration.
     * 
     * <p>This method provides efficient username availability checking for user
     * registration and update operations, preventing duplicate username conflicts.</p>
     * 
     * @param username Username to check for availability
     * @return true if username is available, false if already taken
     * @throws IllegalArgumentException if username is null or empty
     */
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(@NotBlank @Size(max = 50) String username) {
        logger.debug("Checking username availability: username={}", username);

        try {
            boolean isAvailable = !userRepository.existsByUsername(username);
            logger.debug("Username availability check result: username={}, available={}", username, isAvailable);
            return isAvailable;

        } catch (Exception e) {
            logger.error("Error checking username availability: username={}", username, e);
            auditService.logError("USERNAME_CHECK_ERROR", username, 
                                 "Failed to check username availability: " + e.getMessage());
            throw new UserServiceException("Unable to check username availability", e);
        }
    }

    // ==================================================================================
    // UTILITY METHODS
    // ==================================================================================

    /**
     * Generates a unique user ID for new user creation.
     * 
     * <p>This method generates unique user identifiers following the pattern
     * established by the original COBOL system. User IDs are 8-character
     * alphanumeric strings with sufficient entropy for uniqueness.</p>
     * 
     * @return Unique 8-character user identifier
     */
    private String generateUserId() {
        // Generate 8-character alphanumeric user ID - matches COBOL SEC-USR-ID format
        return java.util.UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toUpperCase();
    }

    // ==================================================================================
    // EXCEPTION HANDLING
    // ==================================================================================

    /**
     * Custom exception for user service operations.
     * Provides specific error handling for user management business logic.
     */
    public static class UserServiceException extends RuntimeException {
        public UserServiceException(String message) {
            super(message);
        }
        
        public UserServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}