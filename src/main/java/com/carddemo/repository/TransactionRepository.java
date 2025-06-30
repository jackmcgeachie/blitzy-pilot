/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.carddemo.repository;

import com.carddemo.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for transaction database operations.
 * 
 * Replaces VSAM TRANSACT file access with PostgreSQL transactions table operations.
 * Provides high-performance transaction processing, history queries, batch posting
 * operations, and real-time authorization support.
 * 
 * Features:
 * - Primary key access via transaction ID (replacing VSAM direct key access)
 * - Composite index queries for card number and timestamp-based searches
 * - Pagination support for transaction listing (10 records per page matching BMS screen)
 * - Batch processing integration for Spring Batch chunk-oriented operations
 * - Range queries for transaction history and reporting operations
 * - Optimized performance for 10,000+ TPS requirement
 * 
 * Maps to PostgreSQL transactions table with composite indexes:
 * - (transaction_id) - Primary key index for direct access
 * - (card_number, transaction_timestamp) - Card transaction history
 * - (account_id, transaction_date) - Account transaction queries
 * - (merchant_id, transaction_timestamp) - Merchant analysis
 * 
 * Conversion from COBOL VSAM access patterns:
 * - EXEC CICS READ -> findById()
 * - EXEC CICS STARTBR/READNEXT -> findBy...Pageable
 * - EXEC CICS WRITE -> save()
 * - EXEC CICS REWRITE -> save() (with existing entity)
 * - EXEC CICS DELETE -> deleteById()
 * 
 * Performance optimization through:
 * - PreparedStatement caching via HikariCP
 * - Composite PostgreSQL B-tree indexes
 * - Pagination to prevent large result sets
 * - Query method optimization for sub-200ms response times
 * 
 * @see Transaction
 * @see com.carddemo.entity.Card
 * @see com.carddemo.entity.Account
 * @see com.carddemo.entity.TransactionType
 * @see com.carddemo.entity.TransactionCategory
 * 
 * Author: Blitzy agent - Spring Data JPA migration from COBOL VSAM TRANSACT file
 * Date: Migration from COTRN00C-02C online and CBTRN01C-03C batch processing
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    // ========================================================================
    // PRIMARY KEY AND DIRECT ACCESS METHODS
    // Replaces COBOL: EXEC CICS READ DATASET('TRANSACT') RIDFLD(TRAN-ID)
    // ========================================================================

    /**
     * Find transaction by transaction ID (primary key access).
     * 
     * Replaces COBOL VSAM direct read operation:
     * EXEC CICS READ
     *      DATASET(WS-TRANSACT-FILE)
     *      INTO(TRAN-RECORD)
     *      RIDFLD(TRAN-ID)
     * END-EXEC
     * 
     * Performance: Sub-millisecond response via primary key index
     * 
     * @param transactionId 16-character transaction identifier
     * @return Optional containing transaction if found
     */
    @Override
    Optional<Transaction> findById(String transactionId);

    /**
     * Check if transaction exists by ID.
     * Optimized for existence checks without full entity retrieval.
     * 
     * @param transactionId 16-character transaction identifier
     * @return true if transaction exists, false otherwise
     */
    @Override
    boolean existsById(String transactionId);

    // ========================================================================
    // CARD-BASED TRANSACTION QUERIES
    // Replaces COBOL: Card number cross-reference and transaction history
    // ========================================================================

    /**
     * Find all transactions for a specific card number with pagination.
     * 
     * Replaces COBOL VSAM browsing pattern:
     * EXEC CICS STARTBR DATASET('TRANSACT') 
     *      RIDFLD(CARD-NUM) GTEQ
     * EXEC CICS READNEXT...
     * 
     * Uses composite index: (card_number, transaction_timestamp)
     * Performance: Index scan access for high-volume card queries
     * 
     * @param cardNumber 16-digit card number
     * @param pageable Pagination specification (10 records per page matching BMS COTRN00 screen)
     * @return Page of transactions ordered by timestamp descending
     */
    @Query("SELECT t FROM Transaction t WHERE t.cardNumber = :cardNumber ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findByCardNumber(@Param("cardNumber") String cardNumber, Pageable pageable);

    /**
     * Find transactions for card within timestamp range.
     * 
     * Supports transaction history queries with date filtering.
     * Optimized for reporting and statement generation operations.
     * 
     * @param cardNumber 16-digit card number
     * @param startTimestamp Start of date range (inclusive)
     * @param endTimestamp End of date range (inclusive)
     * @param pageable Pagination specification
     * @return Page of transactions within specified date range
     */
    @Query("SELECT t FROM Transaction t WHERE t.cardNumber = :cardNumber " +
           "AND t.transactionTimestamp BETWEEN :startTimestamp AND :endTimestamp " +
           "ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findByCardNumberAndTimestampBetween(
            @Param("cardNumber") String cardNumber,
            @Param("startTimestamp") LocalDateTime startTimestamp,
            @Param("endTimestamp") LocalDateTime endTimestamp,
            Pageable pageable);

    /**
     * Find recent transactions for card (last N transactions).
     * 
     * Optimized for real-time authorization checks and recent activity display.
     * Limited result set for performance.
     * 
     * @param cardNumber 16-digit card number
     * @param limit Maximum number of transactions to return
     * @return List of recent transactions ordered by timestamp descending
     */
    @Query("SELECT t FROM Transaction t WHERE t.cardNumber = :cardNumber " +
           "ORDER BY t.transactionTimestamp DESC")
    List<Transaction> findRecentTransactionsByCardNumber(@Param("cardNumber") String cardNumber, Pageable pageable);

    // ========================================================================
    // ACCOUNT-BASED TRANSACTION QUERIES
    // Supports account balance calculations and reporting
    // ========================================================================

    /**
     * Find all transactions for a specific account.
     * 
     * Uses composite index: (account_id, transaction_date)
     * Supports account statement generation and balance reconciliation.
     * 
     * @param accountId 11-digit account identifier
     * @param pageable Pagination specification
     * @return Page of transactions for the account
     */
    @Query("SELECT t FROM Transaction t JOIN t.card c WHERE c.accountId = :accountId " +
           "ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findByAccountId(@Param("accountId") String accountId, Pageable pageable);

    /**
     * Find account transactions within date range.
     * 
     * Supports monthly statement generation and period-based reporting.
     * Optimized for batch processing and reporting operations.
     * 
     * @param accountId 11-digit account identifier
     * @param startDate Start of date range
     * @param endDate End of date range
     * @param pageable Pagination specification
     * @return Page of account transactions within date range
     */
    @Query("SELECT t FROM Transaction t JOIN t.card c WHERE c.accountId = :accountId " +
           "AND t.transactionTimestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findByAccountIdAndDateRange(
            @Param("accountId") String accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // ========================================================================
    // TRANSACTION TYPE AND CATEGORY QUERIES
    // Replaces COBOL EVALUATE statements for transaction categorization
    // ========================================================================

    /**
     * Find transactions by type code.
     * 
     * Supports transaction categorization and type-based reporting.
     * Uses foreign key relationship to TransactionType entity.
     * 
     * @param typeCode 2-character transaction type code
     * @param pageable Pagination specification
     * @return Page of transactions of specified type
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionType.typeCode = :typeCode " +
           "ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findByTransactionTypeCode(@Param("typeCode") String typeCode, Pageable pageable);

    /**
     * Find transactions by category code.
     * 
     * Supports category-based reporting and analytics.
     * Uses foreign key relationship to TransactionCategory entity.
     * 
     * @param categoryCode 4-digit transaction category code
     * @param pageable Pagination specification
     * @return Page of transactions in specified category
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionCategory.categoryCode = :categoryCode " +
           "ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findByTransactionCategoryCode(@Param("categoryCode") Integer categoryCode, Pageable pageable);

    // ========================================================================
    // AMOUNT-BASED QUERIES
    // Supports fraud detection and financial analysis
    // ========================================================================

    /**
     * Find transactions by amount range.
     * 
     * Supports fraud detection for unusual transaction amounts.
     * Uses BigDecimal for precise monetary calculations.
     * 
     * @param minAmount Minimum transaction amount (inclusive)
     * @param maxAmount Maximum transaction amount (inclusive)
     * @param pageable Pagination specification
     * @return Page of transactions within amount range
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionAmount BETWEEN :minAmount AND :maxAmount " +
           "ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findByAmountRange(
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            Pageable pageable);

    /**
     * Find transactions above specified amount threshold.
     * 
     * Optimized for high-value transaction monitoring and alerts.
     * 
     * @param threshold Minimum transaction amount
     * @param pageable Pagination specification
     * @return Page of transactions above threshold
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionAmount >= :threshold " +
           "ORDER BY t.transactionAmount DESC")
    Page<Transaction> findHighValueTransactions(@Param("threshold") BigDecimal threshold, Pageable pageable);

    // ========================================================================
    // MERCHANT-BASED QUERIES
    // Supports merchant analysis and transaction routing
    // ========================================================================

    /**
     * Find transactions by merchant ID.
     * 
     * Supports merchant transaction analysis and reconciliation.
     * Uses index on merchant_id for performance.
     * 
     * @param merchantId 9-digit merchant identifier
     * @param pageable Pagination specification
     * @return Page of transactions for specified merchant
     */
    @Query("SELECT t FROM Transaction t WHERE t.merchantId = :merchantId " +
           "ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findByMerchantId(@Param("merchantId") Long merchantId, Pageable pageable);

    /**
     * Find transactions by merchant name pattern.
     * 
     * Supports merchant search and analysis operations.
     * Uses case-insensitive pattern matching.
     * 
     * @param merchantNamePattern Pattern for merchant name search
     * @param pageable Pagination specification
     * @return Page of transactions matching merchant name pattern
     */
    @Query("SELECT t FROM Transaction t WHERE UPPER(t.merchantName) LIKE UPPER(:merchantNamePattern) " +
           "ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findByMerchantNameContaining(@Param("merchantNamePattern") String merchantNamePattern, Pageable pageable);

    // ========================================================================
    // AGGREGATION AND REPORTING QUERIES
    // Supports balance calculations and financial reporting
    // ========================================================================

    /**
     * Calculate total transaction amount for account within date range.
     * 
     * Supports account balance calculations and period-based summaries.
     * Used by batch posting operations for balance reconciliation.
     * 
     * @param accountId 11-digit account identifier
     * @param startDate Start of calculation period
     * @param endDate End of calculation period
     * @return Total transaction amount for period (null if no transactions)
     */
    @Query("SELECT SUM(t.transactionAmount) FROM Transaction t JOIN t.card c " +
           "WHERE c.accountId = :accountId " +
           "AND t.transactionTimestamp BETWEEN :startDate AND :endDate")
    BigDecimal calculateAccountTransactionTotal(
            @Param("accountId") String accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count transactions for account within date range.
     * 
     * Supports transaction volume analysis and reporting.
     * 
     * @param accountId 11-digit account identifier
     * @param startDate Start of count period
     * @param endDate End of count period
     * @return Number of transactions in period
     */
    @Query("SELECT COUNT(t) FROM Transaction t JOIN t.card c " +
           "WHERE c.accountId = :accountId " +
           "AND t.transactionTimestamp BETWEEN :startDate AND :endDate")
    Long countAccountTransactions(
            @Param("accountId") String accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get transaction count by card for specified date range.
     * 
     * Supports card usage analysis and fraud detection.
     * 
     * @param cardNumber 16-digit card number
     * @param startDate Start of count period
     * @param endDate End of count period
     * @return Number of transactions for card in period
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.cardNumber = :cardNumber " +
           "AND t.transactionTimestamp BETWEEN :startDate AND :endDate")
    Long countCardTransactions(
            @Param("cardNumber") String cardNumber,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // ========================================================================
    // BATCH PROCESSING SUPPORT
    // Supports Spring Batch chunk-oriented processing
    // ========================================================================

    /**
     * Find transactions for batch processing by timestamp range.
     * 
     * Supports Spring Batch ItemReader for chunk-oriented processing.
     * Optimized for high-volume batch operations with consistent ordering.
     * 
     * @param startTimestamp Start of processing window
     * @param endTimestamp End of processing window
     * @param pageable Chunk size specification for batch processing
     * @return Page of transactions for batch processing
     */
    @Query("SELECT t FROM Transaction t WHERE t.processingTimestamp IS NULL " +
           "AND t.transactionTimestamp BETWEEN :startTimestamp AND :endTimestamp " +
           "ORDER BY t.transactionTimestamp ASC")
    Page<Transaction> findUnprocessedTransactionsInRange(
            @Param("startTimestamp") LocalDateTime startTimestamp,
            @Param("endTimestamp") LocalDateTime endTimestamp,
            Pageable pageable);

    /**
     * Mark transactions as processed (batch posting operation).
     * 
     * Supports Spring Batch ItemWriter for transaction posting.
     * Updates processing timestamp to indicate completion.
     * 
     * @param transactionIds List of transaction IDs to mark as processed
     * @param processingTimestamp Timestamp when processing completed
     * @return Number of transactions updated
     */
    @Modifying
    @Transactional
    @Query("UPDATE Transaction t SET t.processingTimestamp = :processingTimestamp " +
           "WHERE t.transactionId IN :transactionIds")
    int markTransactionsAsProcessed(
            @Param("transactionIds") List<String> transactionIds,
            @Param("processingTimestamp") LocalDateTime processingTimestamp);

    /**
     * Find transactions pending batch processing.
     * 
     * Supports Spring Batch job startup and restart logic.
     * Orders by transaction timestamp for consistent processing.
     * 
     * @param pageable Chunk size for batch processing
     * @return Page of unprocessed transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.processingTimestamp IS NULL " +
           "ORDER BY t.transactionTimestamp ASC")
    Page<Transaction> findPendingTransactions(Pageable pageable);

    // ========================================================================
    // SPECIALIZED QUERIES FOR REAL-TIME AUTHORIZATION
    // Supports sub-200ms response times for authorization requests
    // ========================================================================

    /**
     * Find most recent transaction for card (authorization check).
     * 
     * Optimized for real-time authorization decisions.
     * Single record retrieval for maximum performance.
     * 
     * @param cardNumber 16-digit card number
     * @return Most recent transaction for card
     */
    @Query("SELECT t FROM Transaction t WHERE t.cardNumber = :cardNumber " +
           "ORDER BY t.transactionTimestamp DESC")
    Optional<Transaction> findMostRecentTransactionByCard(@Param("cardNumber") String cardNumber);

    /**
     * Check for duplicate transactions within time window.
     * 
     * Supports duplicate transaction detection for authorization.
     * Optimized for real-time fraud prevention.
     * 
     * @param cardNumber 16-digit card number
     * @param merchantId Merchant identifier
     * @param amount Transaction amount
     * @param timeWindow Minutes to look back for duplicates
     * @return Count of matching transactions within time window
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.cardNumber = :cardNumber " +
           "AND t.merchantId = :merchantId " +
           "AND t.transactionAmount = :amount " +
           "AND t.transactionTimestamp >= :timeWindow")
    Long findDuplicateTransactionCount(
            @Param("cardNumber") String cardNumber,
            @Param("merchantId") Long merchantId,
            @Param("amount") BigDecimal amount,
            @Param("timeWindow") LocalDateTime timeWindow);

    // ========================================================================
    // CUSTOM QUERY METHODS FOR COMPLEX REPORTING
    // Supports advanced business intelligence and analytics
    // ========================================================================

    /**
     * Find transactions by multiple criteria (complex search).
     * 
     * Supports advanced search functionality with multiple filters.
     * Optimized for user interface search operations.
     * 
     * @param cardNumber Optional card number filter
     * @param merchantName Optional merchant name filter
     * @param minAmount Optional minimum amount filter
     * @param maxAmount Optional maximum amount filter
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @param pageable Pagination specification
     * @return Page of transactions matching search criteria
     */
    @Query("SELECT t FROM Transaction t WHERE " +
           "(:cardNumber IS NULL OR t.cardNumber = :cardNumber) " +
           "AND (:merchantName IS NULL OR UPPER(t.merchantName) LIKE UPPER(CONCAT('%', :merchantName, '%'))) " +
           "AND (:minAmount IS NULL OR t.transactionAmount >= :minAmount) " +
           "AND (:maxAmount IS NULL OR t.transactionAmount <= :maxAmount) " +
           "AND (:startDate IS NULL OR t.transactionTimestamp >= :startDate) " +
           "AND (:endDate IS NULL OR t.transactionTimestamp <= :endDate) " +
           "ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findTransactionsByCriteria(
            @Param("cardNumber") String cardNumber,
            @Param("merchantName") String merchantName,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Get daily transaction summary for reporting.
     * 
     * Supports daily summary reporting with aggregation.
     * Used by batch reporting operations.
     * 
     * @param summaryDate Date for summary calculation
     * @return Array containing [transaction_count, total_amount, avg_amount]
     */
    @Query("SELECT COUNT(t), SUM(t.transactionAmount), AVG(t.transactionAmount) " +
           "FROM Transaction t WHERE DATE(t.transactionTimestamp) = DATE(:summaryDate)")
    Object[] getDailySummary(@Param("summaryDate") LocalDateTime summaryDate);

}