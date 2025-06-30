/*
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.carddemo.repository;

import com.carddemo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for user database operations.
 * 
 * Replaces VSAM USRSEC file access with PostgreSQL users table operations.
 * Supports Spring Security JWT authentication and role-based access control
 * maintaining RACF user type compatibility ('A' for Administrative, 'U' for Regular User).
 * 
 * This repository implements the user data access layer for:
 * - User authentication (converting COSGN00C sign-on logic)
 * - User management operations (COUSR00C-03C program functionality)
 * - Spring Security JWT token validation
 * - Role-based authorization with RACF-equivalent user types
 * 
 * Technical Implementation:
 * - Primary key access via 8-character user_id (matching COBOL SEC-USR-ID)
 * - Username-based authentication queries for Spring Security integration
 * - User type queries supporting two-tier classification (Admin/Regular)
 * - Optimistic locking support through JPA @Version field
 * - Paginated user listing for administrative operations
 * 
 * Database Mapping:
 * - PostgreSQL users table with composite indexes
 * - BCrypt password hash storage replacing plain text COBOL passwords
 * - Role code mapping: 'A' = ROLE_ADMIN, 'U' = ROLE_USER
 * - Audit trail support with creation and modification timestamps
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Find user by username for Spring Security authentication.
     * 
     * Replaces COBOL EXEC CICS READ operation against USRSEC file in COSGN00C.
     * Used by Spring Security UserDetailsService to load user details during
     * JWT token authentication process.
     * 
     * COBOL Equivalent:
     * EXEC CICS READ
     *      DATASET   ('USRSEC  ')
     *      INTO      (SEC-USER-DATA)
     *      RIDFLD    (WS-USER-ID)
     *      KEYLENGTH (LENGTH OF WS-USER-ID)
     *      RESP      (WS-RESP-CD)
     * END-EXEC
     * 
     * @param username unique username identifier
     * @return Optional containing User if found, empty if not found
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    Optional<User> findByUsername(String username);

    /**
     * Find users by role type for administrative operations.
     * 
     * Supports role-based queries to retrieve users by classification:
     * - 'A' = Administrative users (ROLE_ADMIN)
     * - 'U' = Regular users (ROLE_USER)
     * 
     * Maps to COBOL user type validation in COADM01C and user management screens.
     * Enables filtered user listings in administrative interfaces.
     * 
     * @param roleCode single-character role code ('A' or 'U')
     * @return List of users matching the specified role type
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    List<User> findByRoleCode(String roleCode);

    /**
     * Find users by role type with pagination support.
     * 
     * Implements paginated user listing equivalent to COUSR00C browsing functionality.
     * Supports administrative user management with configurable page sizes
     * matching COBOL 10-record pagination patterns.
     * 
     * @param roleCode single-character role code ('A' or 'U')
     * @param pageable pagination and sorting parameters
     * @return Page containing users of specified role type
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    Page<User> findByRoleCode(String roleCode, Pageable pageable);

    /**
     * Find users by status for account management operations.
     * 
     * Supports user status filtering:
     * - 'ACTIVE' = Active user accounts
     * - 'INACTIVE' = Disabled user accounts
     * - 'LOCKED' = Locked user accounts (failed authentication attempts)
     * 
     * @param status user account status
     * @return List of users with specified status
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    List<User> findByStatus(String status);

    /**
     * Find active users by role type for security operations.
     * 
     * Combines role-based and status filtering to retrieve only active users
     * of a specific type. Used for security auditing and user administration.
     * 
     * @param roleCode single-character role code ('A' or 'U')
     * @param status user account status (typically 'ACTIVE')
     * @return List of active users with specified role
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    List<User> findByRoleCodeAndStatus(String roleCode, String status);

    /**
     * Check if username exists for unique constraint validation.
     * 
     * Validates username uniqueness during user creation operations
     * (COUSR01C add user functionality). Prevents duplicate username creation.
     * 
     * @param username username to check for existence
     * @return true if username exists, false otherwise
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    boolean existsByUsername(String username);

    /**
     * Find users created after specified date for auditing.
     * 
     * Supports audit reporting and user account tracking by creation date.
     * Used for security compliance and user activity monitoring.
     * 
     * @param createdAt minimum creation timestamp
     * @return List of users created after the specified date
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    List<User> findByCreatedAtAfter(LocalDateTime createdAt);

    /**
     * Count total users by role type for dashboard statistics.
     * 
     * Provides user count metrics for administrative dashboards and reporting.
     * Supports system monitoring and capacity planning operations.
     * 
     * @param roleCode single-character role code ('A' or 'U')
     * @return count of users with specified role type
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    long countByRoleCode(String roleCode);

    /**
     * Count active users for system monitoring.
     * 
     * Provides active user count for system health monitoring and capacity metrics.
     * Used in operational dashboards and system status reporting.
     * 
     * @param status user account status (typically 'ACTIVE')
     * @return count of users with specified status
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    long countByStatus(String status);

    /**
     * Find users for administrative listing with search capability.
     * 
     * Implements search functionality for user management screens,
     * supporting partial name matching for improved user experience.
     * Replaces COBOL STARTBR/READNEXT browse patterns with modern SQL queries.
     * 
     * @param firstName partial or complete first name for search
     * @param lastName partial or complete last name for search
     * @param pageable pagination and sorting parameters
     * @return Page containing users matching search criteria
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    @Query("SELECT u FROM User u WHERE " +
           "UPPER(u.firstName) LIKE UPPER(CONCAT('%', :firstName, '%')) AND " +
           "UPPER(u.lastName) LIKE UPPER(CONCAT('%', :lastName, '%')) " +
           "ORDER BY u.lastName, u.firstName")
    Page<User> findByNameContaining(@Param("firstName") String firstName, 
                                   @Param("lastName") String lastName, 
                                   Pageable pageable);

    /**
     * Find administrative users for security operations.
     * 
     * Convenience method to retrieve all administrative users (role code 'A').
     * Used for security auditing, administrative notifications, and access control validation.
     * 
     * @return List of all administrative users
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    default List<User> findAdministrativeUsers() {
        return findByRoleCode("A");
    }

    /**
     * Find regular users for standard operations.
     * 
     * Convenience method to retrieve all regular users (role code 'U').
     * Used for user management operations and standard user processing.
     * 
     * @return List of all regular users
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    default List<User> findRegularUsers() {
        return findByRoleCode("U");
    }

    /**
     * Validate user authentication credentials.
     * 
     * Performs username existence check for authentication pre-validation.
     * Used in conjunction with BCrypt password verification in AuthenticationService.
     * 
     * Note: Password validation is handled by Spring Security BCrypt encoder,
     * not in repository layer for security best practices.
     * 
     * @param username username to validate
     * @return true if user exists and can be authenticated
     * @throws org.springframework.dao.DataAccessException if database error occurs
     */
    default boolean canAuthenticate(String username) {
        return findByUsername(username)
                .map(user -> "ACTIVE".equals(user.getStatus()))
                .orElse(false);
    }

}