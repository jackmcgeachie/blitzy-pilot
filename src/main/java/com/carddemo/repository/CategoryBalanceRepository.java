/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 * CategoryBalanceRepository.java
 *
 * Spring Data JPA repository interface for transaction category balance operations,
 * replacing VSAM TCATBAL file with PostgreSQL category_balances table access.
 * Manages category-specific balance tracking, reconciliation reporting, and
 * concurrent balance updates with version control.
 *
 * Converted from: VSAM TCATBAL file and related COBOL copybooks
 * Target table: PostgreSQL category_balances table
 * 
 * This repository implements enterprise-grade category balance management with:
 * - Composite primary key support on (account_id, category_code)
 * - Optimistic locking for concurrent balance modifications  
 * - High-performance query methods for balance reporting
 * - Cache integration for frequently accessed balance data
 * - Comprehensive error handling and validation
 */

package com.carddemo.repository;

import com.carddemo.entity.CategoryBalance;
import com.carddemo.entity.CategoryBalanceId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for CategoryBalance entity operations.
 * 
 * This repository replaces VSAM TCATBAL file access with PostgreSQL category_balances
 * table operations, maintaining identical business logic and data access patterns.
 * Implements composite primary key support for account-category balance tracking
 * with optimistic locking for concurrent modification safety.
 * 
 * <p>Composite Key Structure:</p>
 * - account_id: String (11-digit account identifier)
 * - category_code: Integer (4-digit category code)
 * 
 * <p>Business Functions Supported:</p>
 * - Category balance tracking and reporting
 * - Account-level balance reconciliation
 * - Transaction category analysis
 * - Balance modification with optimistic locking
 * - High-performance lookup operations
 * 
 * <p>Performance Characteristics:</p>
 * - Composite B-tree indexes for optimal query performance
 * - Query result caching for frequently accessed data
 * - Batch operations for bulk balance updates
 * - Optimistic locking prevents concurrent modification conflicts
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface CategoryBalanceRepository extends JpaRepository<CategoryBalance, CategoryBalanceId> {

    /**
     * Retrieves all category balances for a specific account.
     * 
     * This method replaces COBOL STARTBR/READNEXT patterns for account-based
     * balance enumeration. Uses composite index on (account_id, category_code)
     * for optimal query performance.
     * 
     * @param accountId the 11-digit account identifier
     * @return List of CategoryBalance entities for the specified account
     * @throws IllegalArgumentException if accountId is null or empty
     */
    @Cacheable(value = "accountCategoryBalances", key = "#accountId")
    List<CategoryBalance> findByAccountId(@Param("accountId") String accountId);

    /**
     * Retrieves all category balances for a specific account with pagination.
     * 
     * Supports large account portfolios with pagination to prevent memory
     * exhaustion and improve response times for balance reporting screens.
     * 
     * @param accountId the 11-digit account identifier
     * @param pageable pagination and sorting parameters
     * @return Page of CategoryBalance entities for the specified account
     */
    Page<CategoryBalance> findByAccountId(@Param("accountId") String accountId, Pageable pageable);

    /**
     * Retrieves all balances for a specific transaction category across accounts.
     * 
     * Enables category-level balance analysis and reporting across the entire
     * customer portfolio. Uses category_code index for efficient lookups.
     * 
     * @param categoryCode the 4-digit transaction category code
     * @return List of CategoryBalance entities for the specified category
     */
    @Cacheable(value = "categoryBalances", key = "#categoryCode")
    List<CategoryBalance> findByCategoryCode(@Param("categoryCode") Integer categoryCode);

    /**
     * Retrieves category balances for an account above a minimum threshold.
     * 
     * Supports balance reporting and analytics by filtering significant balances
     * for account analysis and reconciliation operations.
     * 
     * @param accountId the 11-digit account identifier
     * @param minimumBalance the minimum balance threshold (inclusive)
     * @return List of CategoryBalance entities above the threshold
     */
    @Query("SELECT cb FROM CategoryBalance cb WHERE cb.id.accountId = :accountId AND cb.balance >= :minimumBalance ORDER BY cb.balance DESC")
    List<CategoryBalance> findByAccountIdAndBalanceGreaterThanEqual(
            @Param("accountId") String accountId,
            @Param("minimumBalance") BigDecimal minimumBalance);

    /**
     * Retrieves category balances within a specified range for an account.
     * 
     * Enables precise balance range analysis for risk assessment and
     * portfolio management operations.
     * 
     * @param accountId the 11-digit account identifier
     * @param minBalance the minimum balance (inclusive)
     * @param maxBalance the maximum balance (inclusive)
     * @return List of CategoryBalance entities within the specified range
     */
    @Query("SELECT cb FROM CategoryBalance cb WHERE cb.id.accountId = :accountId AND cb.balance BETWEEN :minBalance AND :maxBalance ORDER BY cb.id.categoryCode")
    List<CategoryBalance> findByAccountIdAndBalanceBetween(
            @Param("accountId") String accountId,
            @Param("minBalance") BigDecimal minBalance,
            @Param("maxBalance") BigDecimal maxBalance);

    /**
     * Calculates total balance across all categories for an account.
     * 
     * Provides aggregate balance calculations for account-level reporting
     * and reconciliation processes.
     * 
     * @param accountId the 11-digit account identifier
     * @return Optional containing the total balance, empty if no balances exist
     */
    @Query("SELECT SUM(cb.balance) FROM CategoryBalance cb WHERE cb.id.accountId = :accountId")
    Optional<BigDecimal> calculateTotalBalanceByAccountId(@Param("accountId") String accountId);

    /**
     * Retrieves the count of categories with non-zero balances for an account.
     * 
     * Supports portfolio analysis by indicating the diversity of transaction
     * categories utilized by an account.
     * 
     * @param accountId the 11-digit account identifier
     * @return count of categories with balances greater than zero
     */
    @Query("SELECT COUNT(cb) FROM CategoryBalance cb WHERE cb.id.accountId = :accountId AND cb.balance > 0")
    Long countNonZeroBalancesByAccountId(@Param("accountId") String accountId);

    /**
     * Updates balance for a specific account-category combination.
     * 
     * Implements optimistic locking through version number increment to prevent
     * concurrent modification conflicts. Maintains COBOL-equivalent precision
     * for all monetary calculations.
     * 
     * @param accountId the 11-digit account identifier
     * @param categoryCode the 4-digit transaction category code
     * @param newBalance the new balance amount with scale 2 precision
     * @param currentVersion the current version for optimistic locking
     * @return number of rows updated (should be 1 for successful update)
     * @throws org.springframework.orm.ObjectOptimisticLockingFailureException if version mismatch
     */
    @Modifying
    @Transactional
    @Query("UPDATE CategoryBalance cb SET cb.balance = :newBalance, cb.updatedAt = CURRENT_TIMESTAMP, cb.version = cb.version + 1 " +
           "WHERE cb.id.accountId = :accountId AND cb.id.categoryCode = :categoryCode AND cb.version = :currentVersion")
    int updateBalanceWithOptimisticLock(
            @Param("accountId") String accountId,
            @Param("categoryCode") Integer categoryCode,
            @Param("newBalance") BigDecimal newBalance,
            @Param("currentVersion") Long currentVersion);

    /**
     * Retrieves category balances modified after a specified timestamp.
     * 
     * Supports incremental synchronization and audit trail analysis for
     * balance changes across the system.
     * 
     * @param timestamp the cutoff timestamp for modifications
     * @return List of CategoryBalance entities modified after the timestamp
     */
    @Query("SELECT cb FROM CategoryBalance cb WHERE cb.updatedAt > :timestamp ORDER BY cb.updatedAt ASC")
    List<CategoryBalance> findBalancesModifiedAfter(@Param("timestamp") LocalDateTime timestamp);

    /**
     * Retrieves top N categories by balance for portfolio analysis.
     * 
     * Enables risk analysis and portfolio optimization by identifying
     * categories with the highest balance concentrations.
     * 
     * @param limit the maximum number of results to return
     * @return List of CategoryBalance entities ordered by balance descending
     */
    @Query(value = "SELECT * FROM category_balances ORDER BY balance DESC LIMIT :limit", nativeQuery = true)
    List<CategoryBalance> findTopCategoriesByBalance(@Param("limit") int limit);

    /**
     * Checks if a specific account-category balance exists.
     * 
     * Optimized existence check without loading the full entity,
     * supporting validation operations and conditional processing.
     * 
     * @param accountId the 11-digit account identifier
     * @param categoryCode the 4-digit transaction category code
     * @return true if balance record exists, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(cb) > 0 THEN true ELSE false END FROM CategoryBalance cb WHERE cb.id.accountId = :accountId AND cb.id.categoryCode = :categoryCode")
    boolean existsByAccountIdAndCategoryCode(
            @Param("accountId") String accountId,
            @Param("categoryCode") Integer categoryCode);

    /**
     * Retrieves category balances for reconciliation reporting.
     * 
     * Provides comprehensive balance data for regulatory reporting and
     * internal reconciliation processes with consistent ordering.
     * 
     * @param accountIds list of account identifiers for batch processing
     * @return List of CategoryBalance entities for all specified accounts
     */
    @Query("SELECT cb FROM CategoryBalance cb WHERE cb.id.accountId IN :accountIds ORDER BY cb.id.accountId, cb.id.categoryCode")
    List<CategoryBalance> findBalancesForReconciliation(@Param("accountIds") List<String> accountIds);

    /**
     * Deletes all category balances for a specific account.
     * 
     * Supports account closure operations by removing all associated
     * category balance records with proper transaction boundaries.
     * 
     * @param accountId the 11-digit account identifier
     * @return number of CategoryBalance records deleted
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM CategoryBalance cb WHERE cb.id.accountId = :accountId")
    int deleteByAccountId(@Param("accountId") String accountId);

    /**
     * Retrieves balances for accounts with activity in a date range.
     * 
     * Supports period-end reporting and audit operations by identifying
     * balances that have been modified within specified timeframes.
     * 
     * @param startDate the start of the date range (inclusive)
     * @param endDate the end of the date range (inclusive)
     * @return List of CategoryBalance entities modified within the date range
     */
    @Query("SELECT cb FROM CategoryBalance cb WHERE cb.updatedAt BETWEEN :startDate AND :endDate ORDER BY cb.updatedAt DESC")
    List<CategoryBalance> findBalancesUpdatedInDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}