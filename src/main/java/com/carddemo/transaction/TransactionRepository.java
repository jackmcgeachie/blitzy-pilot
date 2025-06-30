/*
 * TransactionRepository.java
 * 
 * Spring Data JPA repository interface providing database access methods for 
 * transaction entities, replacing CICS File Control operations with modern JPA patterns.
 * 
 * This repository converts COBOL/CICS transaction data access patterns from:
 * - COTRN00C.cbl: Transaction listing with pagination (STARTBR/READNEXT/READPREV/ENDBR)  
 * - COTRN01C.cbl: Transaction view with record locking (READ with UPDATE)
 * - COTRN02C.cbl: Transaction creation (WRITE) and ID generation
 * 
 * Implements performance optimizations per Section 6.2.4:
 * - Prepared statement caching through Spring Data JPA
 * - Composite indexes for optimal query performance  
 * - Optimistic locking with JPA version fields
 * - Pagination support for high-volume result sets
 * 
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 */
package com.carddemo.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.QueryHint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Transaction entity providing database access
 * methods that replace CICS File Control operations with modern JPA patterns.
 * 
 * Key Features:
 * - Replaces VSAM STARTBR/READNEXT/READPREV patterns with Spring Data pagination
 * - Converts CICS READ/WRITE/REWRITE/DELETE to JPA repository methods
 * - Implements optimistic locking through JPA version fields
 * - Provides custom query methods for transaction history and filtering
 * - Optimizes performance through prepared statement caching per Section 6.2.4
 * 
 * Performance Targets (per Section 6.2.6):
 * - Transaction REST latency <200ms  
 * - Database query P95 <50ms
 * - Transaction processing capacity ≥10,000 TPS
 * - Connection pool utilization <80%
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    // ================================================================
    // BASIC CICS FILE CONTROL OPERATION REPLACEMENTS
    // ================================================================
    
    /**
     * Replaces CICS READ operation from COTRN01C.cbl (line 269).
     * 
     * COBOL equivalent:
     * EXEC CICS READ
     *      DATASET   (WS-TRANSACT-FILE)
     *      INTO      (TRAN-RECORD)
     *      RIDFLD    (TRAN-ID)
     *      UPDATE
     * END-EXEC
     * 
     * @param transactionId 16-digit transaction identifier (TRAN-ID from CVTRA05Y.cpy)
     * @return Optional<Transaction> for null-safe access, implementing UPDATE semantics through optimistic locking
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionId = :transactionId")
    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "false"),
        @QueryHint(name = "org.hibernate.fetchSize", value = "1")
    })
    Optional<Transaction> findByTransactionIdForUpdate(@Param("transactionId") String transactionId);

    /**
     * Replaces CICS WRITE operation from COTRN02C.cbl (line 713).
     * 
     * COBOL equivalent:  
     * EXEC CICS WRITE
     *      DATASET   (WS-TRANSACT-FILE)
     *      FROM      (TRAN-RECORD)
     *      RIDFLD    (TRAN-ID)
     * END-EXEC
     * 
     * Note: Spring Data JPA save() method automatically handles WRITE vs REWRITE
     * based on entity state (new vs existing with version field).
     * 
     * @param transaction Transaction entity to persist
     * @return Transaction persisted entity with generated/updated version
     */
    // Inherited from JpaRepository: save(Transaction transaction)

    /**
     * Replaces CICS DELETE operation.
     * 
     * COBOL equivalent:
     * EXEC CICS DELETE
     *      DATASET   (WS-TRANSACT-FILE)  
     *      RIDFLD    (TRAN-ID)
     * END-EXEC
     * 
     * @param transactionId 16-digit transaction identifier to delete
     */
    // Inherited from JpaRepository: deleteById(String transactionId)

    // ================================================================
    // PAGINATION SUPPORT - REPLACES VSAM BROWSE OPERATIONS
    // ================================================================

    /**
     * Replaces VSAM STARTBR/READNEXT pattern from COTRN00C.cbl (lines 593-654).
     * Implements pagination equivalent to COBOL 10-record page processing.
     * 
     * COBOL equivalent browse pattern:
     * PERFORM STARTBR-TRANSACT-FILE
     * PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > 10
     *    PERFORM READNEXT-TRANSACT-FILE  
     *    PERFORM POPULATE-TRAN-DATA
     * END-PERFORM
     * 
     * @param pageable Pagination parameters (size=10 to match COBOL screen layout)
     * @return Page<Transaction> with transaction data ordered by transaction ID
     */
    @Query("SELECT t FROM Transaction t ORDER BY t.transactionId")
    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "org.hibernate.cacheRegion", value = "transactionListCache")
    })
    Page<Transaction> findAllTransactionsOrderById(Pageable pageable);

    /**
     * Replaces VSAM STARTBR with GTEQ positioning from COTRN00C.cbl.
     * Provides pagination starting from specific transaction ID.
     * 
     * COBOL equivalent:
     * EXEC CICS STARTBR
     *      DATASET   (WS-TRANSACT-FILE)
     *      RIDFLD    (TRAN-ID)
     *      GTEQ
     * END-EXEC
     * 
     * @param startTransactionId Starting transaction ID for pagination  
     * @param pageable Pagination parameters
     * @return Page<Transaction> starting from specified transaction ID
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionId >= :startTransactionId ORDER BY t.transactionId")
    Page<Transaction> findTransactionsStartingFrom(@Param("startTransactionId") String startTransactionId, 
                                                   Pageable pageable);

    /**
     * Replaces VSAM READPREV pattern for backward pagination from COTRN00C.cbl (lines 658-687).
     * 
     * COBOL equivalent:
     * EXEC CICS READPREV
     *      DATASET   (WS-TRANSACT-FILE)
     *      INTO      (TRAN-RECORD)
     *      RIDFLD    (TRAN-ID)
     * END-EXEC
     * 
     * @param beforeTransactionId Transaction ID for backward pagination boundary
     * @param pageable Pagination parameters  
     * @return Page<Transaction> in reverse order from specified transaction ID
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionId < :beforeTransactionId ORDER BY t.transactionId DESC")
    Page<Transaction> findTransactionsBefore(@Param("beforeTransactionId") String beforeTransactionId,
                                           Pageable pageable);

    // ================================================================
    // TRANSACTION ID GENERATION - REPLACES COBOL HIGH-VALUES LOGIC
    // ================================================================

    /**
     * Replaces COBOL transaction ID generation logic from COTRN02C.cbl (lines 444-449).
     * 
     * COBOL equivalent:
     * MOVE HIGH-VALUES TO TRAN-ID
     * PERFORM STARTBR-TRANSACT-FILE
     * PERFORM READPREV-TRANSACT-FILE  
     * MOVE TRAN-ID TO WS-TRAN-ID-N
     * ADD 1 TO WS-TRAN-ID-N
     * 
     * @return String highest transaction ID for next ID generation
     */
    @Query("SELECT MAX(t.transactionId) FROM Transaction t")
    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "false"),
        @QueryHint(name = "org.hibernate.fetchSize", value = "1")
    })
    Optional<String> findMaxTransactionId();

    // ================================================================
    // CARD-BASED TRANSACTION QUERIES
    // ================================================================

    /**
     * Finds transactions by card number with pagination.
     * Supports transaction history display per card.
     * 
     * Uses composite index: (card_number, transaction_timestamp) per Section 6.2.4.1
     * 
     * @param cardNumber 16-digit card number (TRAN-CARD-NUM from CVTRA05Y.cpy)
     * @param pageable Pagination parameters
     * @return Page<Transaction> transactions for specified card
     */
    @Query("SELECT t FROM Transaction t WHERE t.cardNumber = :cardNumber ORDER BY t.originalTimestamp DESC")
    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "org.hibernate.cacheRegion", value = "cardTransactionCache")
    })
    Page<Transaction> findByCardNumberOrderByTimestampDesc(@Param("cardNumber") String cardNumber, 
                                                         Pageable pageable);

    /**
     * Finds transactions by card number within date range.
     * Optimized with composite index for high-performance range queries.
     * 
     * @param cardNumber 16-digit card number
     * @param startDate Start of date range
     * @param endDate End of date range  
     * @param pageable Pagination parameters
     * @return Page<Transaction> filtered transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.cardNumber = :cardNumber " +
           "AND t.originalTimestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY t.originalTimestamp DESC")
    Page<Transaction> findByCardNumberAndDateRange(@Param("cardNumber") String cardNumber,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate,
                                                  Pageable pageable);

    // ================================================================  
    // ACCOUNT-BASED TRANSACTION QUERIES
    // ================================================================

    /**
     * Finds transactions by account ID through card relationships.
     * Replaces cross-reference file lookups from COTRN02C.cbl.
     * 
     * Uses join optimization for account-to-card-to-transaction navigation.
     * 
     * @param accountId 11-digit account identifier
     * @param pageable Pagination parameters
     * @return Page<Transaction> transactions for specified account
     */
    @Query("SELECT t FROM Transaction t " +
           "JOIN Card c ON t.cardNumber = c.cardNumber " +
           "WHERE c.accountId = :accountId " +
           "ORDER BY t.originalTimestamp DESC")
    Page<Transaction> findByAccountIdOrderByTimestampDesc(@Param("accountId") String accountId,
                                                        Pageable pageable);

    // ================================================================
    // TRANSACTION FILTERING AND SEARCH QUERIES  
    // ================================================================

    /**
     * Finds transactions by type code with pagination.
     * Supports transaction categorization filtering.
     * 
     * @param typeCode 2-character transaction type (TRAN-TYPE-CD from CVTRA05Y.cpy)
     * @param pageable Pagination parameters
     * @return Page<Transaction> transactions of specified type
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionTypeCode = :typeCode ORDER BY t.originalTimestamp DESC")
    Page<Transaction> findByTransactionTypeCode(@Param("typeCode") String typeCode, Pageable pageable);

    /**
     * Finds transactions by category code with pagination.
     * Supports transaction classification filtering.
     * 
     * @param categoryCode 4-digit category code (TRAN-CAT-CD from CVTRA05Y.cpy) 
     * @param pageable Pagination parameters
     * @return Page<Transaction> transactions in specified category
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionCategoryCode = :categoryCode ORDER BY t.originalTimestamp DESC")
    Page<Transaction> findByTransactionCategoryCode(@Param("categoryCode") String categoryCode, Pageable pageable);

    /**
     * Finds transactions by amount range.
     * Supports financial analysis and reporting queries.
     * 
     * @param minAmount Minimum transaction amount (inclusive)
     * @param maxAmount Maximum transaction amount (inclusive)
     * @param pageable Pagination parameters
     * @return Page<Transaction> transactions within amount range
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionAmount BETWEEN :minAmount AND :maxAmount " +
           "ORDER BY t.transactionAmount DESC")
    Page<Transaction> findByAmountRange(@Param("minAmount") BigDecimal minAmount,
                                      @Param("maxAmount") BigDecimal maxAmount,
                                      Pageable pageable);

    /**
     * Finds transactions by merchant name with case-insensitive search.
     * Supports merchant-based transaction analysis.
     * 
     * @param merchantName Merchant name search pattern (supports % wildcards)
     * @param pageable Pagination parameters  
     * @return Page<Transaction> transactions matching merchant criteria
     */
    @Query("SELECT t FROM Transaction t WHERE UPPER(t.merchantName) LIKE UPPER(:merchantName) " +
           "ORDER BY t.originalTimestamp DESC")
    Page<Transaction> findByMerchantNameContainingIgnoreCase(@Param("merchantName") String merchantName,
                                                           Pageable pageable);

    // ================================================================
    // AGGREGATE QUERIES FOR REPORTING
    // ================================================================

    /**
     * Calculates transaction count by card number.
     * Supports account activity analysis.
     * 
     * @param cardNumber 16-digit card number
     * @return Long transaction count for specified card
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.cardNumber = :cardNumber")
    Long countByCardNumber(@Param("cardNumber") String cardNumber);

    /**
     * Calculates total transaction amount by card number within date range.
     * Supports balance calculation and statement generation.
     * 
     * @param cardNumber 16-digit card number
     * @param startDate Start of calculation period
     * @param endDate End of calculation period
     * @return BigDecimal sum of transaction amounts (null-safe)
     */
    @Query("SELECT COALESCE(SUM(t.transactionAmount), 0) FROM Transaction t " +
           "WHERE t.cardNumber = :cardNumber " +
           "AND t.originalTimestamp BETWEEN :startDate AND :endDate")
    BigDecimal sumAmountByCardNumberAndDateRange(@Param("cardNumber") String cardNumber,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);

    /**
     * Finds recent transactions for real-time monitoring.
     * Supports transaction monitoring and fraud detection.
     * 
     * @param minutes Number of minutes for "recent" transactions
     * @param pageable Pagination parameters
     * @return Page<Transaction> recent transactions ordered by timestamp
     */
    @Query("SELECT t FROM Transaction t WHERE t.originalTimestamp >= :sinceTime ORDER BY t.originalTimestamp DESC")
    Page<Transaction> findRecentTransactions(@Param("sinceTime") LocalDateTime sinceTime, Pageable pageable);

    // ================================================================
    // BATCH PROCESSING SUPPORT QUERIES
    // ================================================================

    /**
     * Finds unprocessed transactions for batch posting.
     * Supports batch transaction processing workflows.
     * 
     * @param pageable Pagination for chunk-oriented processing
     * @return Page<Transaction> unprocessed transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.processedTimestamp IS NULL ORDER BY t.originalTimestamp")
    Page<Transaction> findUnprocessedTransactions(Pageable pageable);

    /**
     * Finds transactions requiring statement generation.
     * Supports monthly statement batch processing.
     * 
     * @param statementDate Statement period cut-off date
     * @param pageable Pagination parameters
     * @return Page<Transaction> transactions for statement generation
     */
    @Query("SELECT t FROM Transaction t WHERE t.originalTimestamp < :statementDate " +
           "AND (t.statementGenerated IS NULL OR t.statementGenerated = false) " +
           "ORDER BY t.cardNumber, t.originalTimestamp")
    Page<Transaction> findTransactionsForStatement(@Param("statementDate") LocalDateTime statementDate,
                                                  Pageable pageable);

    // ================================================================
    // EXISTENCE AND VALIDATION QUERIES
    // ================================================================

    /**
     * Checks if transaction exists by ID.
     * Optimized for duplicate detection and validation.
     * 
     * @param transactionId 16-digit transaction identifier
     * @return boolean true if transaction exists
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Transaction t WHERE t.transactionId = :transactionId")
    boolean existsByTransactionId(@Param("transactionId") String transactionId);

    /**
     * Validates card number existence in transactions.
     * Supports cross-reference validation during transaction creation.
     * 
     * @param cardNumber 16-digit card number
     * @return boolean true if card has transactions
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Transaction t WHERE t.cardNumber = :cardNumber")
    boolean existsByCardNumber(@Param("cardNumber") String cardNumber);

    // ================================================================
    // CUSTOM NATIVE QUERIES FOR PERFORMANCE OPTIMIZATION
    // ================================================================

    /**
     * Optimized native query for high-volume transaction lookups.
     * Uses PostgreSQL-specific optimizations and prepared statement caching.
     * 
     * Implements composite index usage: (card_number, transaction_timestamp)
     * per Section 6.2.4.1 performance optimization strategy.
     * 
     * @param cardNumber 16-digit card number
     * @param limit Maximum number of records to return
     * @return List<Transaction> latest transactions for card
     */
    @Query(value = "SELECT * FROM transactions t " +
                   "WHERE t.card_number = :cardNumber " +
                   "ORDER BY t.original_timestamp DESC " +
                   "LIMIT :limit", 
           nativeQuery = true)
    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "org.hibernate.cacheRegion", value = "latestTransactionCache")
    })
    List<Transaction> findLatestTransactionsByCardNumberNative(@Param("cardNumber") String cardNumber,
                                                             @Param("limit") int limit);

    /**
     * Native query for transaction statistics aggregation.
     * Optimized for reporting and analytics with PostgreSQL-specific functions.
     * 
     * @param cardNumber 16-digit card number  
     * @param days Number of days for statistics calculation
     * @return Object[] containing [count, sum, avg, min, max] statistics
     */
    @Query(value = "SELECT " +
                   "  COUNT(*) as transaction_count, " +
                   "  COALESCE(SUM(transaction_amount), 0) as total_amount, " +
                   "  COALESCE(AVG(transaction_amount), 0) as average_amount, " +
                   "  COALESCE(MIN(transaction_amount), 0) as min_amount, " +
                   "  COALESCE(MAX(transaction_amount), 0) as max_amount " +
                   "FROM transactions t " +
                   "WHERE t.card_number = :cardNumber " +
                   "  AND t.original_timestamp >= CURRENT_TIMESTAMP - INTERVAL ':days days'",
           nativeQuery = true)
    Object[] getTransactionStatisticsByCardNumber(@Param("cardNumber") String cardNumber,
                                                @Param("days") int days);
}