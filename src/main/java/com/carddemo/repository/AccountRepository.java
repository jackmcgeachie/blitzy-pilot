/*
 * AccountRepository.java
 * 
 * Spring Data JPA repository interface for account database operations,
 * replacing VSAM ACCTDAT file with PostgreSQL accounts table access.
 * Provides account CRUD operations, balance management, customer account 
 * relationships, and pagination for account listing functionality.
 * 
 * Migrated from COBOL programs:
 * - COACTVWC.cbl (Account View operations)
 * - COACTUPC.cbl (Account Update operations)
 * - CBACT01C.cbl-CBACT04C.cbl (Account Batch processing)
 * 
 * Original copybook structures:
 * - CVACT01Y.cpy (Account record layout)
 * - CVACT02Y.cpy (Card record layout)  
 * - CVACT03Y.cpy (Card cross-reference layout)
 * 
 * This repository implements Spring Data JPA patterns that replace
 * COBOL File Control commands with PostgreSQL database operations:
 * - READ operations -> findById(), findBy*() methods
 * - WRITE operations -> save() with new entities
 * - REWRITE operations -> save() with existing entities  
 * - DELETE operations -> deleteById(), delete() methods
 * - STARTBR/READNEXT -> Pageable and Slice operations
 * 
 * Optimistic locking with @Version annotations replaces CICS 
 * record locking mechanisms for concurrent access control.
 * 
 * Copyright (c) 2024 CardDemo Application
 */
package com.carddemo.repository;

import com.carddemo.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for Account entity operations.
 * 
 * Replaces VSAM ACCTDAT file access with PostgreSQL database operations
 * while maintaining identical business logic and data access patterns
 * from the original COBOL implementation.
 * 
 * Key Features:
 * - Composite index support for (customer_id, account_status) queries
 * - Pagination support for account listing with 7-record pages  
 * - Cross-reference lookups through PostgreSQL views
 * - Optimistic locking with version fields for concurrent access
 * - Balance management with NUMERIC(15,2) precision
 * - Account status and relationship management
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since 2024-01-01
 */
@Repository
@Transactional(readOnly = true)
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Find account by customer ID.
     * 
     * Replaces COBOL logic from COACTVWC for customer account lookup
     * using alternate index access patterns equivalent to VSAM AIX.
     * 
     * Uses composite index: (customer_id, account_status) for optimal performance.
     * 
     * @param customerId 9-digit customer identifier from original CUSTREC
     * @return Optional Account entity or empty if not found
     */
    Optional<Account> findByCustomerId(@Param("customerId") String customerId);

    /**
     * Find all accounts for a specific customer with pagination.
     * 
     * Supports multiple accounts per customer with 7-record pagination
     * matching original BMS screen display patterns from COCRDLI.bms.
     * 
     * @param customerId 9-digit customer identifier
     * @param pageable Pagination parameters (size=7 for BMS compatibility)
     * @return Page of Account entities with pagination metadata
     */
    Page<Account> findByCustomerIdOrderByAccountId(@Param("customerId") String customerId, Pageable pageable);

    /**
     * Find account by account status.
     * 
     * Implements COBOL 88-level conditions for account status validation:
     * - 'A' = Active account
     * - 'C' = Closed account  
     * - 'S' = Suspended account
     * - 'P' = Pending account
     * 
     * Uses composite index: (customer_id, account_status) for performance.
     * 
     * @param accountStatus Single character status code from ACCT-ACTIVE-STATUS
     * @return List of Account entities matching status
     */
    List<Account> findByAccountStatus(@Param("accountStatus") String accountStatus);

    /**
     * Find accounts by customer ID and status with pagination.
     * 
     * Combines customer lookup with status filtering using composite index
     * for optimal query performance. Replaces COBOL EVALUATE statements
     * for status-based account filtering.
     * 
     * @param customerId 9-digit customer identifier
     * @param accountStatus Single character status code
     * @param pageable Pagination parameters
     * @return Page of filtered Account entities
     */
    Page<Account> findByCustomerIdAndAccountStatusOrderByAccountId(
        @Param("customerId") String customerId, 
        @Param("accountStatus") String accountStatus,
        Pageable pageable);

    /**
     * Find accounts with balance greater than specified amount.
     * 
     * Supports balance-based queries for reporting and analysis.
     * Uses NUMERIC(15,2) precision matching COBOL COMP-3 fields
     * from ACCT-CURR-BAL (PIC S9(10)V99).
     * 
     * @param minimumBalance Minimum balance threshold
     * @return List of Account entities above balance threshold
     */
    List<Account> findByCurrentBalanceGreaterThan(@Param("minimumBalance") BigDecimal minimumBalance);

    /**
     * Find accounts by credit limit range.
     * 
     * Supports credit management queries matching COBOL logic from
     * ACCT-CREDIT-LIMIT field processing in account management programs.
     * 
     * @param minLimit Minimum credit limit
     * @param maxLimit Maximum credit limit  
     * @return List of Account entities within credit limit range
     */
    List<Account> findByCreditLimitBetween(
        @Param("minLimit") BigDecimal minLimit, 
        @Param("maxLimit") BigDecimal maxLimit);

    /**
     * Find accounts opened within date range.
     * 
     * Supports date range queries for account analysis and reporting.
     * Handles COBOL date formats converted to LocalDate in Java.
     * 
     * @param startDate Beginning of date range
     * @param endDate End of date range
     * @return List of Account entities opened in date range
     */
    List<Account> findByOpenDateBetween(
        @Param("startDate") LocalDate startDate, 
        @Param("endDate") LocalDate endDate);

    /**
     * Count active accounts for customer.
     * 
     * Provides account count functionality for business rule validation
     * and customer relationship management. Replaces COBOL counting
     * logic from batch processing programs.
     * 
     * @param customerId 9-digit customer identifier
     * @return Count of active accounts for customer
     */
    @Query("SELECT COUNT(a) FROM Account a WHERE a.customerId = :customerId AND a.accountStatus = 'A'")
    Long countActiveAccountsByCustomerId(@Param("customerId") String customerId);

    /**
     * Find accounts requiring expiration processing.
     * 
     * Supports batch processing for account expiration handling,
     * replacing logic from CBACT02C.cbl batch program.
     * 
     * @param expirationDate Date threshold for expiration processing
     * @return List of Account entities requiring expiration processing
     */
    @Query("SELECT a FROM Account a WHERE a.expiryDate <= :expirationDate AND a.accountStatus = 'A'")
    List<Account> findAccountsForExpirationProcessing(@Param("expirationDate") LocalDate expirationDate);

    /**
     * Update account balance with optimistic locking.
     * 
     * Performs balance updates with version checking to prevent
     * concurrent modification conflicts. Replaces CICS REWRITE
     * operations with PostgreSQL optimistic locking.
     * 
     * @param accountId 11-digit account identifier  
     * @param newBalance Updated balance amount
     * @param currentVersion Current version for optimistic locking
     * @return Number of affected records (1 if successful, 0 if version conflict)
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.currentBalance = :newBalance, a.version = a.version + 1 " +
           "WHERE a.accountId = :accountId AND a.version = :currentVersion")
    int updateAccountBalanceWithVersion(
        @Param("accountId") String accountId,
        @Param("newBalance") BigDecimal newBalance, 
        @Param("currentVersion") Integer currentVersion);

    /**
     * Update account status with audit trail.
     * 
     * Updates account status while maintaining version control
     * for audit and concurrency management. Supports account
     * lifecycle state transitions.
     * 
     * @param accountId 11-digit account identifier
     * @param newStatus New status code ('A', 'C', 'S', 'P')
     * @param currentVersion Current version for optimistic locking
     * @return Number of affected records (1 if successful, 0 if version conflict)
     */
    @Modifying
    @Transactional  
    @Query("UPDATE Account a SET a.accountStatus = :newStatus, a.version = a.version + 1 " +
           "WHERE a.accountId = :accountId AND a.version = :currentVersion")
    int updateAccountStatusWithVersion(
        @Param("accountId") String accountId,
        @Param("newStatus") String newStatus,
        @Param("currentVersion") Integer currentVersion);

    /**
     * Find accounts for batch balance reconciliation.
     * 
     * Supports Spring Batch processing for account balance reconciliation,
     * replacing batch logic from CBACT03C.cbl program. Uses Slice for
     * memory-efficient processing of large datasets.
     * 
     * @param pageable Pagination parameters for chunk processing
     * @return Slice of Account entities for batch processing
     */
    @Query("SELECT a FROM Account a WHERE a.accountStatus IN ('A', 'S') ORDER BY a.accountId")
    Slice<Account> findAccountsForBatchProcessing(Pageable pageable);

    /**
     * Calculate total balance by customer.
     * 
     * Aggregates account balances for customer relationship management
     * and credit analysis. Supports reporting requirements from
     * original COBOL reporting programs.
     * 
     * @param customerId 9-digit customer identifier
     * @return Total balance across all customer accounts
     */
    @Query("SELECT COALESCE(SUM(a.currentBalance), 0) FROM Account a " +
           "WHERE a.customerId = :customerId AND a.accountStatus = 'A'")
    BigDecimal calculateTotalBalanceByCustomerId(@Param("customerId") String customerId);

    /**
     * Find accounts with low balance alert.
     * 
     * Identifies accounts below minimum balance threshold for
     * alert processing and customer notification. Supports
     * proactive account management features.
     * 
     * @param alertThreshold Minimum balance threshold for alerts
     * @return List of Account entities requiring balance alerts
     */
    @Query("SELECT a FROM Account a WHERE a.currentBalance < :alertThreshold " +
           "AND a.accountStatus = 'A' ORDER BY a.currentBalance ASC")
    List<Account> findAccountsWithLowBalance(@Param("alertThreshold") BigDecimal alertThreshold);

    /**
     * Find accounts by account ID prefix for partial matching.
     * 
     * Supports STARTBR/READNEXT equivalent functionality for
     * account browsing and lookup operations. Enables partial
     * account number searches matching COBOL partial key logic.
     * 
     * @param accountIdPrefix Partial account identifier
     * @param pageable Pagination parameters  
     * @return Page of Account entities matching prefix
     */
    Page<Account> findByAccountIdStartingWithOrderByAccountId(
        @Param("accountIdPrefix") String accountIdPrefix, 
        Pageable pageable);

    /**
     * Check if account exists for customer.
     * 
     * Provides existence checking for business rule validation
     * and duplicate prevention logic. Replaces COBOL record
     * existence checking patterns.
     * 
     * @param customerId 9-digit customer identifier
     * @param accountId 11-digit account identifier
     * @return True if account exists for customer, false otherwise
     */
    boolean existsByCustomerIdAndAccountId(
        @Param("customerId") String customerId, 
        @Param("accountId") String accountId);

    /**
     * Find accounts with available credit.
     * 
     * Calculates available credit (credit limit - current balance)
     * for credit authorization and limit management. Supports
     * real-time credit checking functionality.
     * 
     * @param minimumAvailableCredit Minimum available credit threshold
     * @return List of Account entities with sufficient available credit
     */
    @Query("SELECT a FROM Account a WHERE (a.creditLimit - a.currentBalance) >= :minimumAvailableCredit " +
           "AND a.accountStatus = 'A' ORDER BY (a.creditLimit - a.currentBalance) DESC")
    List<Account> findAccountsWithAvailableCredit(@Param("minimumAvailableCredit") BigDecimal minimumAvailableCredit);
}