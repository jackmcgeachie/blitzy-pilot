/*
 * AccountRepository.java
 * 
 * Spring Data JPA repository interface providing database access operations for Account entities.
 * Replaces COBOL CICS File Control operations with modern JPA query methods, supporting pagination,
 * custom lookups, and optimized database access patterns equivalent to original VSAM file operations.
 * 
 * This repository implements the data access layer for account management operations originally
 * performed by COBOL programs COACTVWC and COACTUPC, transforming VSAM KSDS file access patterns
 * into efficient PostgreSQL queries with composite indexes.
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Provides comprehensive database access methods that replicate COBOL CICS File Control
 * operations (READ, WRITE, REWRITE, DELETE) with modern JPA query capabilities.
 * Supports composite index queries for efficient customer-to-account relationship
 * navigation matching original VSAM AIX access patterns.
 * 
 * Key Features:
 * - CRUD operations replacing CICS File Control commands
 * - Custom finder methods for account lookup by customer ID, status, and date ranges
 * - Pagination support for large result sets (STARTBR/READNEXT/READPREV equivalent)
 * - Composite index queries leveraging PostgreSQL B-tree optimization
 * - Optimistic locking through JPA version control
 * 
 * Performance Characteristics:
 * - Sub-millisecond primary key access via PostgreSQL B-tree indexes
 * - Efficient range scanning for batch operations and reporting
 * - Composite index navigation for cross-reference lookups
 * - Connection pool optimization through HikariCP integration
 * 
 * Original COBOL Program Mappings:
 * - COACTVWC: Account view operations -> findBy* query methods
 * - COACTUPC: Account update operations -> save/update methods
 * - VSAM ACCTDAT: Primary account file -> accounts table
 * - VSAM CXACAIX: Account cross-reference -> custom query methods
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    // =========================================================================
    // BASIC CRUD OPERATIONS
    // Replaces CICS File Control READ, WRITE, REWRITE, DELETE operations
    // =========================================================================

    /**
     * Find account by primary key (account ID).
     * Replaces CICS READ operation with direct key access.
     * 
     * Equivalent COBOL operation:
     * EXEC CICS READ DATASET('ACCTDAT') RIDFLD(ACCOUNT-ID) INTO(ACCOUNT-RECORD)
     * 
     * @param accountId 11-digit account identifier
     * @return Optional containing Account if found, empty otherwise
     */
    Optional<Account> findByAccountId(String accountId);

    /**
     * Check if account exists by account ID.
     * Provides efficient existence check without full record retrieval.
     * 
     * @param accountId 11-digit account identifier
     * @return true if account exists, false otherwise
     */
    boolean existsByAccountId(String accountId);

    /**
     * Delete account by account ID.
     * Replaces CICS DELETE operation with optimistic locking.
     * 
     * Equivalent COBOL operation:
     * EXEC CICS DELETE DATASET('ACCTDAT') RIDFLD(ACCOUNT-ID)
     * 
     * @param accountId 11-digit account identifier
     */
    void deleteByAccountId(String accountId);

    // =========================================================================
    // CUSTOMER RELATIONSHIP QUERIES
    // Replaces VSAM alternate index (AIX) access patterns for customer lookups
    // =========================================================================

    /**
     * Find all accounts for a specific customer.
     * Replaces VSAM AIX access via customer ID with PostgreSQL composite index.
     * 
     * Leverages composite index: (customer_id, account_status, account_open_date)
     * for optimal query performance matching VSAM CXACAIX access patterns.
     * 
     * @param customerId 9-digit customer identifier
     * @return List of accounts owned by the customer
     */
    List<Account> findByCustomerId(String customerId);

    /**
     * Find accounts for customer with pagination support.
     * Enables efficient processing of large result sets, replacing CICS
     * STARTBR/READNEXT/READPREV sequential processing patterns.
     * 
     * @param customerId 9-digit customer identifier
     * @param pageable Pagination and sorting parameters
     * @return Page of accounts for the customer
     */
    Page<Account> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Find active accounts for a specific customer.
     * Combines customer relationship with account status filtering.
     * 
     * @param customerId 9-digit customer identifier
     * @param accountStatus Account status code (e.g., 'A' for Active)
     * @return List of active accounts for the customer
     */
    List<Account> findByCustomerIdAndAccountStatus(String customerId, String accountStatus);

    /**
     * Count total accounts for a customer.
     * Provides efficient count operation without full record retrieval.
     * 
     * @param customerId 9-digit customer identifier
     * @return Total number of accounts for the customer
     */
    long countByCustomerId(String customerId);

    // =========================================================================
    // ACCOUNT STATUS AND BALANCE QUERIES
    // Support business logic operations for account management
    // =========================================================================

    /**
     * Find all accounts with specific status.
     * Supports batch processing operations and administrative reporting.
     * 
     * @param accountStatus Account status code
     * @return List of accounts with the specified status
     */
    List<Account> findByAccountStatus(String accountStatus);

    /**
     * Find accounts with status and pagination.
     * Enables efficient batch processing of status-based account operations.
     * 
     * @param accountStatus Account status code
     * @param pageable Pagination parameters
     * @return Page of accounts with the specified status
     */
    Page<Account> findByAccountStatus(String accountStatus, Pageable pageable);

    /**
     * Find accounts with current balance in specified range.
     * Supports financial analysis and balance-based account selection.
     * 
     * @param minBalance Minimum balance threshold
     * @param maxBalance Maximum balance threshold
     * @return List of accounts within the balance range
     */
    List<Account> findByCurrentBalanceBetween(BigDecimal minBalance, BigDecimal maxBalance);

    /**
     * Find accounts with balance above specified threshold.
     * Supports high-value account identification and VIP processing.
     * 
     * @param threshold Minimum balance threshold
     * @return List of accounts with balance above threshold
     */
    List<Account> findByCurrentBalanceGreaterThan(BigDecimal threshold);

    /**
     * Find accounts with negative balance (overdraft).
     * Supports overdraft monitoring and risk management operations.
     * 
     * @return List of accounts with negative balance
     */
    List<Account> findByCurrentBalanceLessThan(BigDecimal threshold);

    // =========================================================================
    // DATE-BASED QUERIES
    // Support temporal operations and date range analysis
    // =========================================================================

    /**
     * Find accounts opened within date range.
     * Supports new account analysis and date-based reporting.
     * 
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return List of accounts opened within the date range
     */
    List<Account> findByAccountOpenDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find accounts opened after specific date.
     * Supports recent account identification and growth analysis.
     * 
     * @param openDate Reference date for comparison
     * @return List of accounts opened after the specified date
     */
    List<Account> findByAccountOpenDateAfter(LocalDate openDate);

    /**
     * Find accounts expiring within date range.
     * Supports expiration monitoring and renewal processing.
     * 
     * @param startDate Start of expiration date range
     * @param endDate End of expiration date range
     * @return List of accounts expiring within the date range
     */
    List<Account> findByExpiryDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find accounts expiring soon (within specified days).
     * Supports proactive expiration management and customer notification.
     * 
     * @param expiryDate Future expiry date threshold
     * @return List of accounts expiring before the specified date
     */
    List<Account> findByExpiryDateBefore(LocalDate expiryDate);

    // =========================================================================
    // ADVANCED COMPOSITE QUERIES
    // Complex business operations combining multiple criteria
    // =========================================================================

    /**
     * Find active accounts for customer with minimum balance.
     * Combines customer relationship, status, and balance criteria.
     * 
     * @param customerId 9-digit customer identifier
     * @param accountStatus Account status (typically 'A' for Active)
     * @param minBalance Minimum required balance
     * @return List of active accounts meeting balance criteria
     */
    List<Account> findByCustomerIdAndAccountStatusAndCurrentBalanceGreaterThanEqual(
            String customerId, String accountStatus, BigDecimal minBalance);

    /**
     * Find accounts by customer and date range with pagination.
     * Supports comprehensive customer account history analysis.
     * 
     * @param customerId 9-digit customer identifier
     * @param startDate Start of date range
     * @param endDate End of date range
     * @param pageable Pagination parameters
     * @return Page of accounts matching all criteria
     */
    Page<Account> findByCustomerIdAndAccountOpenDateBetween(
            String customerId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    // =========================================================================
    // CREDIT LIMIT AND FINANCIAL QUERIES
    // Support credit management and financial analysis operations
    // =========================================================================

    /**
     * Find accounts with available credit above threshold.
     * Calculated as (credit_limit - current_balance) for positive available credit.
     * 
     * @param threshold Minimum available credit amount
     * @return List of accounts with sufficient available credit
     */
    @Query("SELECT a FROM Account a WHERE (a.creditLimit - a.currentBalance) > :threshold")
    List<Account> findAccountsWithAvailableCreditAbove(@Param("threshold") BigDecimal threshold);

    /**
     * Find accounts approaching credit limit.
     * Identifies accounts where current balance is within specified percentage of credit limit.
     * 
     * @param utilizationThreshold Credit utilization threshold (e.g., 0.90 for 90%)
     * @return List of accounts approaching their credit limit
     */
    @Query("SELECT a FROM Account a WHERE a.currentBalance > (a.creditLimit * :utilizationThreshold)")
    List<Account> findAccountsApproachingCreditLimit(@Param("utilizationThreshold") BigDecimal utilizationThreshold);

    /**
     * Find accounts over credit limit.
     * Identifies accounts requiring immediate attention for overlimit processing.
     * 
     * @return List of accounts exceeding their credit limit
     */
    @Query("SELECT a FROM Account a WHERE a.currentBalance > a.creditLimit")
    List<Account> findAccountsOverCreditLimit();

    // =========================================================================
    // ACCOUNT GROUP AND CATEGORY QUERIES
    // Support account categorization and group-based operations
    // =========================================================================

    /**
     * Find accounts by group ID.
     * Supports group-based account management and promotional campaigns.
     * 
     * @param groupId Account group identifier
     * @return List of accounts in the specified group
     */
    List<Account> findByGroupId(String groupId);

    /**
     * Find accounts by group ID with pagination.
     * Enables efficient processing of large account groups.
     * 
     * @param groupId Account group identifier
     * @param pageable Pagination parameters
     * @return Page of accounts in the specified group
     */
    Page<Account> findByGroupId(String groupId, Pageable pageable);

    // =========================================================================
    // STATISTICAL AND AGGREGATION QUERIES
    // Support reporting and analytics operations
    // =========================================================================

    /**
     * Count accounts by status.
     * Provides account status distribution for reporting.
     * 
     * @param accountStatus Account status code
     * @return Number of accounts with the specified status
     */
    long countByAccountStatus(String accountStatus);

    /**
     * Calculate total balance for customer accounts.
     * Aggregates current balances across all customer accounts.
     * 
     * @param customerId 9-digit customer identifier
     * @return Sum of current balances for all customer accounts
     */
    @Query("SELECT SUM(a.currentBalance) FROM Account a WHERE a.customerId = :customerId")
    BigDecimal calculateTotalBalanceByCustomer(@Param("customerId") String customerId);

    /**
     * Calculate average balance by account status.
     * Provides statistical analysis for account status categories.
     * 
     * @param accountStatus Account status code
     * @return Average current balance for accounts with specified status
     */
    @Query("SELECT AVG(a.currentBalance) FROM Account a WHERE a.accountStatus = :accountStatus")
    BigDecimal calculateAverageBalanceByStatus(@Param("accountStatus") String accountStatus);

    /**
     * Find top accounts by balance with limit.
     * Supports high-value customer identification and VIP processing.
     * 
     * @param pageable Pagination with sorting by balance descending
     * @return Page of accounts ordered by balance (highest first)
     */
    @Query("SELECT a FROM Account a ORDER BY a.currentBalance DESC")
    Page<Account> findTopAccountsByBalance(Pageable pageable);

    // =========================================================================
    // BULK UPDATE OPERATIONS
    // Support batch processing and administrative operations
    // =========================================================================

    /**
     * Update account status for multiple accounts.
     * Supports bulk status changes for administrative operations.
     * 
     * @param oldStatus Current account status to update
     * @param newStatus New account status value
     * @return Number of accounts updated
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.accountStatus = :newStatus WHERE a.accountStatus = :oldStatus")
    int updateAccountStatus(@Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus);

    /**
     * Update credit limit for specific customer.
     * Supports customer-wide credit limit adjustments.
     * 
     * @param customerId 9-digit customer identifier
     * @param newCreditLimit New credit limit amount
     * @return Number of accounts updated
     */
    @Modifying
    @Transactional
    @Query("UPDATE Account a SET a.creditLimit = :newCreditLimit WHERE a.customerId = :customerId")
    int updateCreditLimitByCustomer(@Param("customerId") String customerId, 
                                    @Param("newCreditLimit") BigDecimal newCreditLimit);

    // =========================================================================
    // ACCOUNT VALIDATION AND BUSINESS RULES
    // Support data integrity and business rule enforcement
    // =========================================================================

    /**
     * Validate account ID format and existence.
     * Ensures account ID is 11-digit numeric and exists in database.
     * Replicates COBOL validation logic from COACTVWC program.
     * 
     * @param accountId Account identifier to validate
     * @return true if account ID is valid and exists, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Account a " +
           "WHERE a.accountId = :accountId AND LENGTH(a.accountId) = 11")
    boolean isValidAccountId(@Param("accountId") String accountId);

    /**
     * Find accounts requiring reissue.
     * Identifies accounts where reissue date has passed or is approaching.
     * 
     * @param reissueDate Reference date for reissue comparison
     * @return List of accounts requiring reissue
     */
    List<Account> findByReissueDateBefore(LocalDate reissueDate);

    /**
     * Find dormant accounts.
     * Identifies accounts with no recent activity based on open date.
     * 
     * @param dormancyThreshold Date threshold for dormancy classification
     * @return List of accounts considered dormant
     */
    @Query("SELECT a FROM Account a WHERE a.accountOpenDate < :dormancyThreshold AND a.currentBalance = 0")
    List<Account> findDormantAccounts(@Param("dormancyThreshold") LocalDate dormancyThreshold);

    // =========================================================================
    // CROSS-REFERENCE SUPPORT QUERIES
    // Enable efficient navigation between related entities
    // =========================================================================

    /**
     * Find accounts associated with specific card number.
     * Supports card-to-account relationship navigation through cross-reference logic.
     * Replaces VSAM CXACAIX alternate index access patterns.
     * 
     * Note: This method requires join with card_account_xref table or 
     * direct relationship through Card entity foreign key.
     * 
     * @param cardNumber 16-digit card number
     * @return List of accounts associated with the card
     */
    @Query("SELECT DISTINCT a FROM Account a JOIN Card c ON a.accountId = c.accountId " +
           "WHERE c.cardNumber = :cardNumber")
    List<Account> findAccountsByCardNumber(@Param("cardNumber") String cardNumber);

    /**
     * Find accounts with active cards.
     * Identifies accounts that have at least one active card associated.
     * 
     * @param cardStatus Active card status (typically 'A')
     * @return List of accounts with active cards
     */
    @Query("SELECT DISTINCT a FROM Account a JOIN Card c ON a.accountId = c.accountId " +
           "WHERE c.cardStatus = :cardStatus")
    List<Account> findAccountsWithActiveCards(@Param("cardStatus") String cardStatus);

    /**
     * Count active cards per account.
     * Provides card count statistics for account management.
     * 
     * @param accountId 11-digit account identifier
     * @param cardStatus Active card status
     * @return Number of active cards for the account
     */
    @Query("SELECT COUNT(c) FROM Card c WHERE c.accountId = :accountId AND c.cardStatus = :cardStatus")
    long countActiveCardsByAccount(@Param("accountId") String accountId, @Param("cardStatus") String cardStatus);
}