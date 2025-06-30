package com.carddemo.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for User entity operations.
 * 
 * This repository replaces VSAM USRSEC file operations from the mainframe COBOL 
 * program COSGN00C with modern Spring Data JPA query methods. It provides 
 * comprehensive user data access operations supporting authentication, validation,
 * and user management functions.
 * 
 * Migration from COBOL CICS File Control:
 * - CICS READ with RIDFLD(WS-USER-ID) → findByUserId()
 * - User existence validation → existsByUserId()
 * - User status checks → various query methods
 * - Sequential browsing → findAll() with pagination
 * 
 * Original COBOL copybook structure (CSUSR01Y):
 * - SEC-USR-ID         PIC X(08) → userId (String, 8 chars)
 * - SEC-USR-FNAME      PIC X(20) → firstName (String, 20 chars) 
 * - SEC-USR-LNAME      PIC X(20) → lastName (String, 20 chars)
 * - SEC-USR-PWD        PIC X(08) → userPassword (String, 8 chars)
 * - SEC-USR-TYPE       PIC X(01) → userType (String, 1 char)
 * 
 * PostgreSQL table mapping:
 * - Primary key: user_id (VARCHAR(8))
 * - Optimistic locking: row_version (INTEGER)
 * - Additional fields: user_name, last_signon_date, last_signon_time
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Finds a user by their user ID.
     * 
     * Replaces COBOL CICS operation:
     * EXEC CICS READ
     *      DATASET   (WS-USRSEC-FILE)
     *      INTO      (SEC-USER-DATA)
     *      RIDFLD    (WS-USER-ID)
     *      KEYLENGTH (LENGTH OF WS-USER-ID)
     * END-EXEC
     * 
     * This is the primary authentication method equivalent to the 
     * READ-USER-SEC-FILE paragraph in COSGN00C.
     * 
     * @param userId the 8-character user identifier
     * @return Optional containing User if found, empty if not found (equivalent to RESP-CD 13)
     */
    Optional<User> findByUserId(String userId);

    /**
     * Finds a user by username (alternative lookup method).
     * 
     * Provides username-based lookup supporting modern authentication patterns
     * while maintaining compatibility with userId-based mainframe patterns.
     * 
     * @param username the username to search for
     * @return Optional containing User if found, empty otherwise
     */
    Optional<User> findByUserName(String username);

    /**
     * Checks if a user exists by user ID.
     * 
     * Provides efficient existence validation replacing COBOL error handling
     * patterns from CICS READ operations that check for RESP-CD values.
     * 
     * @param userId the user ID to check
     * @return true if user exists, false otherwise
     */
    boolean existsByUserId(String userId);

    /**
     * Checks if a user exists by username.
     * 
     * @param username the username to check
     * @return true if user exists, false otherwise
     */
    boolean existsByUserName(String username);

    /**
     * Finds users by user type.
     * 
     * Supports user type filtering equivalent to COBOL 88-level conditions:
     * - 'A' for Administrative users (CDEMO-USRTYP-ADMIN)
     * - 'R' for Regular users
     * 
     * Used for role-based access control and administrative functions.
     * 
     * @param userType the user type code ('A' or 'R')
     * @return List of users with the specified type
     */
    List<User> findByUserType(String userType);

    /**
     * Finds users by user type with pagination support.
     * 
     * Provides paginated user management for administrative functions,
     * supporting the user management screens from COUSR00C-03C programs.
     * 
     * @param userType the user type code
     * @param pageable pagination parameters
     * @return Page of users with the specified type
     */
    Page<User> findByUserType(String userType, Pageable pageable);

    /**
     * Finds all active users (non-deleted users).
     * 
     * Supports user management operations requiring active user lists.
     * 
     * @param pageable pagination parameters
     * @return Page of active users
     */
    @Query("SELECT u FROM User u WHERE u.userType IS NOT NULL ORDER BY u.userId")
    Page<User> findAllActiveUsers(Pageable pageable);

    /**
     * Finds users who have signed on within the specified date range.
     * 
     * Supports audit reporting and user activity analysis equivalent to
     * mainframe SMF record analysis.
     * 
     * @param startDate the start date for the range
     * @param endDate the end date for the range
     * @return List of users who signed on within the date range
     */
    @Query("SELECT u FROM User u WHERE u.lastSignonDate BETWEEN :startDate AND :endDate ORDER BY u.lastSignonDate DESC")
    List<User> findUsersWithSignonDateBetween(@Param("startDate") LocalDateTime startDate, 
                                               @Param("endDate") LocalDateTime endDate);

    /**
     * Finds users by partial user ID match.
     * 
     * Supports user search functionality in administrative screens,
     * providing partial match capabilities for user lookup operations.
     * 
     * @param userIdPattern the partial user ID pattern (case insensitive)
     * @param pageable pagination parameters
     * @return Page of users matching the pattern
     */
    @Query("SELECT u FROM User u WHERE UPPER(u.userId) LIKE UPPER(CONCAT('%', :userIdPattern, '%')) ORDER BY u.userId")
    Page<User> findByUserIdContainingIgnoreCase(@Param("userIdPattern") String userIdPattern, Pageable pageable);

    /**
     * Finds users by partial username match.
     * 
     * @param usernamePattern the partial username pattern (case insensitive)
     * @param pageable pagination parameters  
     * @return Page of users matching the pattern
     */
    @Query("SELECT u FROM User u WHERE UPPER(u.userName) LIKE UPPER(CONCAT('%', :usernamePattern, '%')) ORDER BY u.userName")
    Page<User> findByUserNameContainingIgnoreCase(@Param("usernamePattern") String usernamePattern, Pageable pageable);

    /**
     * Updates the last signon date and time for a user.
     * 
     * Replaces COBOL update logic for tracking user access, equivalent to
     * updating SEC-USER-DATA after successful authentication in COSGN00C.
     * 
     * @param userId the user ID to update
     * @param signonDate the signon date
     * @param signonTime the signon time
     * @return number of records updated
     */
    @Modifying
    @Query("UPDATE User u SET u.lastSignonDate = :signonDate, u.lastSignonTime = :signonTime WHERE u.userId = :userId")
    int updateLastSignonDateTime(@Param("userId") String userId, 
                                 @Param("signonDate") LocalDateTime signonDate,
                                 @Param("signonTime") LocalTime signonTime);

    /**
     * Finds users requiring password reset.
     * 
     * Supports administrative password management functions by identifying
     * users who haven't signed on within a specified number of days.
     * 
     * @param cutoffDate the cutoff date for determining inactive users
     * @return List of users requiring password reset
     */
    @Query("SELECT u FROM User u WHERE u.lastSignonDate IS NULL OR u.lastSignonDate < :cutoffDate ORDER BY u.userId")
    List<User> findUsersRequiringPasswordReset(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Counts users by user type.
     * 
     * Provides user statistics for administrative reporting and dashboard
     * functions, supporting user type distribution analysis.
     * 
     * @param userType the user type to count
     * @return count of users with the specified type
     */
    long countByUserType(String userType);

    /**
     * Finds administrative users for system administration functions.
     * 
     * Convenience method for finding all administrative users, equivalent to
     * checking CDEMO-USRTYP-ADMIN condition in COBOL programs.
     * 
     * @return List of administrative users
     */
    default List<User> findAdministrativeUsers() {
        return findByUserType("A");
    }

    /**
     * Finds regular users for general operations.
     * 
     * Convenience method for finding all regular users, supporting user
     * management and reporting functions.
     * 
     * @return List of regular users
     */
    default List<User> findRegularUsers() {
        return findByUserType("R");
    }

    /**
     * Validates user credentials for authentication.
     * 
     * Note: This method provides the user lookup portion of authentication.
     * Password validation should be performed by the AuthenticationService
     * using secure password hashing techniques rather than plain-text comparison.
     * 
     * Original COBOL logic:
     * IF SEC-USR-PWD = WS-USER-PWD
     *     ... authentication successful
     * ELSE
     *     ... wrong password
     * 
     * @param userId the user ID for authentication
     * @return Optional containing User if found for credential validation
     */
    default Optional<User> findForAuthentication(String userId) {
        return findByUserId(userId);
    }

    /**
     * Gets user type for authorization decisions.
     * 
     * Supports authorization checks equivalent to COBOL user type validation:
     * IF CDEMO-USRTYP-ADMIN
     *     EXEC CICS XCTL PROGRAM ('COADM01C') ...
     * ELSE
     *     EXEC CICS XCTL PROGRAM ('COMEN01C') ...
     * 
     * @param userId the user ID
     * @return Optional containing user type if user exists
     */
    @Query("SELECT u.userType FROM User u WHERE u.userId = :userId")
    Optional<String> findUserTypeByUserId(@Param("userId") String userId);

    /**
     * Validates user status for transaction processing.
     * 
     * Ensures user is active and authorized for transaction processing,
     * supporting business validation rules in transaction services.
     * 
     * @param userId the user ID to validate
     * @return true if user is active and valid for transactions
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.userId = :userId AND u.userType IS NOT NULL")
    boolean isUserActiveForTransactions(@Param("userId") String userId);
}