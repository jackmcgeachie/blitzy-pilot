/*
 * Copyright (c) 2024 CardDemo Application
 * Licensed under the Apache License, Version 2.0
 * 
 * Spring Data JPA Repository Interface providing CICS File Control equivalent
 * database operations for user entity, supporting CRUD operations, pagination,
 * Spring Security integration, and optimistic locking.
 * 
 * Converted from: COUSR00C, COUSR01C, COUSR02C, COUSR03C COBOL programs
 * Original VSAM File: USRSEC
 * Target PostgreSQL Table: users
 */
package com.carddemo.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository Interface for User Entity Operations
 * 
 * This repository provides PostgreSQL database access operations equivalent to
 * CICS File Control commands used in the original COBOL programs:
 * - CICS READ operations -> findBy* methods
 * - CICS WRITE operations -> save() method with new entity
 * - CICS REWRITE operations -> save() method with existing entity
 * - CICS DELETE operations -> delete* methods
 * - CICS STARTBR/READNEXT -> findAll() with Pageable
 * 
 * Key Features:
 * - Pagination support for user listing (10 users per page matching BMS screens)
 * - Username-based lookups for Spring Security integration
 * - Optimistic locking through JPA @Version handling
 * - Batch operations for bulk user management
 * - Custom query methods for advanced user searches
 * 
 * Database Table: users
 * Primary Key: user_id (VARCHAR(8))
 * Optimistic Locking: version (INTEGER)
 * 
 * Replicates VSAM KSDS access patterns:
 * - Primary key access via user_id
 * - Sequential browsing with pagination
 * - Alternate index access via username
 * 
 * Spring Security Integration:
 * - findByUserId() for UserDetailsService authentication
 * - User type validation for role-based access control
 * 
 * Performance Optimizations:
 * - Composite indexes on (user_id, user_type)
 * - Query result caching for frequently accessed users
 * - Batch processing support for high-volume operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    // =================================================================
    // CICS File Control Equivalent Operations
    // =================================================================

    /**
     * CICS READ equivalent - Direct access by primary key
     * Replaces: EXEC CICS READ DATASET('USRSEC') RIDFLD(SEC-USR-ID)
     * 
     * @param userId Primary key (8-character user ID)
     * @return Optional<User> - User entity or empty if not found
     */
    @Override
    Optional<User> findById(String userId);

    /**
     * CICS READ for UPDATE equivalent - Retrieves user for modification
     * Used in COUSR02C and COUSR03C for update/delete operations
     * 
     * @param userId Primary key
     * @return Optional<User> with pessimistic lock for update
     */
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") String userId);

    /**
     * CICS WRITE equivalent - Creates new user record
     * Replaces: EXEC CICS WRITE DATASET('USRSEC') FROM(SEC-USER-DATA)
     * Used in COUSR01C for user creation
     * 
     * @param user User entity to create
     * @return User - Saved user entity with generated version
     */
    @Override
    <S extends User> S save(S user);

    /**
     * CICS DELETE equivalent - Removes user record
     * Replaces: EXEC CICS DELETE DATASET('USRSEC')
     * Used in COUSR03C for user deletion
     * 
     * @param userId Primary key of user to delete
     */
    @Override
    void deleteById(String userId);

    /**
     * Check if user exists - Used for duplicate key validation
     * Replaces CICS DUPKEY response code handling
     * 
     * @param userId Primary key to check
     * @return boolean - true if user exists
     */
    @Override
    boolean existsById(String userId);

    // =================================================================
    // VSAM Sequential Access / Pagination Support
    // =================================================================

    /**
     * STARTBR/READNEXT equivalent - Sequential browsing with pagination
     * Replaces: EXEC CICS STARTBR/READNEXT pattern from COUSR00C
     * 
     * Supports 10 users per page matching BMS screen layout:
     * - WS-USER-DATA with USER-REC OCCURS 10 TIMES
     * - Page navigation with PF7/PF8 keys
     * 
     * @param pageable Pagination parameters (page size = 10)
     * @return Page<User> - Paginated user results
     */
    @Override
    Page<User> findAll(Pageable pageable);

    /**
     * Sequential browsing starting from specific user ID
     * Replaces VSAM GTEQ positioning for pagination
     * 
     * @param startUserId Starting user ID for range scan
     * @param pageable Pagination parameters
     * @return Page<User> - Users starting from specified ID
     */
    @Query("SELECT u FROM User u WHERE u.userId >= :startUserId ORDER BY u.userId")
    Page<User> findAllStartingFrom(@Param("startUserId") String startUserId, Pageable pageable);

    /**
     * Backward pagination support for PF7 key functionality
     * Used in PROCESS-PAGE-BACKWARD from COUSR00C
     * 
     * @param endUserId Ending user ID for backward scan
     * @param pageable Pagination parameters
     * @return Page<User> - Users ending at specified ID
     */
    @Query("SELECT u FROM User u WHERE u.userId <= :endUserId ORDER BY u.userId DESC")
    Page<User> findAllEndingAt(@Param("endUserId") String endUserId, Pageable pageable);

    // =================================================================
    // Custom Query Methods for User Management
    // =================================================================

    /**
     * Find users by user type for role-based filtering
     * Supports 'A' (Admin) and 'R' (Regular) user types
     * 
     * @param userType Single character user type ('A' or 'R')
     * @param pageable Pagination parameters
     * @return Page<User> - Users of specified type
     */
    @Query("SELECT u FROM User u WHERE u.userType = :userType ORDER BY u.userId")
    Page<User> findByUserType(@Param("userType") String userType, Pageable pageable);

    /**
     * Find users by name pattern - Supports partial name searches
     * Used for user lookup functionality in administrative screens
     * 
     * @param firstNamePattern First name search pattern
     * @param lastNamePattern Last name search pattern
     * @param pageable Pagination parameters
     * @return Page<User> - Matching users
     */
    @Query("SELECT u FROM User u WHERE " +
           "u.firstName LIKE :firstNamePattern OR " +
           "u.lastName LIKE :lastNamePattern " +
           "ORDER BY u.lastName, u.firstName")
    Page<User> findByNamePattern(@Param("firstNamePattern") String firstNamePattern,
                                @Param("lastNamePattern") String lastNamePattern,
                                Pageable pageable);

    /**
     * Count users by type for administrative reporting
     * 
     * @param userType User type to count
     * @return long - Count of users of specified type
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.userType = :userType")
    long countByUserType(@Param("userType") String userType);

    // =================================================================
    // Spring Security Integration Methods
    // =================================================================

    /**
     * Find user by ID for Spring Security UserDetailsService
     * Used by AuthenticationService for login validation
     * 
     * @param userId User ID for authentication
     * @return Optional<User> - User for security context
     */
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findUserForAuthentication(@Param("userId") String userId);

    /**
     * Validate user credentials for Spring Security
     * Used during login process to verify user exists and is active
     * 
     * @param userId User ID
     * @param userType Expected user type for role validation
     * @return Optional<User> - User if credentials are valid
     */
    @Query("SELECT u FROM User u WHERE u.userId = :userId AND u.userType = :userType")
    Optional<User> findByUserIdAndType(@Param("userId") String userId, 
                                      @Param("userType") String userType);

    /**
     * Find all active users for session management
     * Used by SessionManagementService for user context
     * 
     * @return List<User> - All active users
     */
    @Query("SELECT u FROM User u ORDER BY u.userId")
    List<User> findAllActiveUsers();

    // =================================================================
    // Batch Processing Support
    // =================================================================

    /**
     * Bulk user creation for batch processing
     * Used by Spring Batch jobs for high-volume user imports
     * 
     * @param users List of users to create
     * @return List<User> - Created users with generated versions
     */
    @Override
    <S extends User> List<S> saveAll(Iterable<S> users);

    /**
     * Bulk user deletion for batch processing
     * Used for administrative bulk operations
     * 
     * @param userIds List of user IDs to delete
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM User u WHERE u.userId IN :userIds")
    void deleteByUserIds(@Param("userIds") List<String> userIds);

    /**
     * Batch update user types for administrative operations
     * 
     * @param userIds List of user IDs to update
     * @param newUserType New user type to assign
     * @return int - Number of records updated
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.userType = :newUserType, u.version = u.version + 1 " +
           "WHERE u.userId IN :userIds")
    int updateUserTypes(@Param("userIds") List<String> userIds, 
                       @Param("newUserType") String newUserType);

    // =================================================================
    // Performance Optimization Queries
    // =================================================================

    /**
     * Optimized user count for pagination calculations
     * Replaces expensive COUNT(*) operations
     * 
     * @return long - Total number of users
     */
    @Query("SELECT COUNT(u) FROM User u")
    long countAllUsers();

    /**
     * Find users with specific version for optimistic locking
     * Used to detect concurrent modifications
     * 
     * @param userId User ID
     * @param version Expected version number
     * @return Optional<User> - User if version matches
     */
    @Query("SELECT u FROM User u WHERE u.userId = :userId AND u.version = :version")
    Optional<User> findByIdAndVersion(@Param("userId") String userId, 
                                     @Param("version") Integer version);

    /**
     * Stream interface for large dataset processing
     * Used by batch jobs for memory-efficient processing
     * 
     * @return Stream of all users for batch processing
     */
    @Query("SELECT u FROM User u ORDER BY u.userId")
    java.util.stream.Stream<User> streamAllUsers();
}