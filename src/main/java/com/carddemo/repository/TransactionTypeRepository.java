package com.carddemo.repository;

import com.carddemo.entity.TransactionType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for transaction type operations,
 * replacing VSAM TRANTYPE file with PostgreSQL transaction_types table access.
 * 
 * This repository provides transaction type validation, categorization rules,
 * status management, and cached reference data access for performance optimization.
 * Supports sub-100ms response times for cached reference data lookups through
 * Spring Cache abstraction and Redis integration.
 * 
 * Performance Requirements:
 * - Sub-100ms response times for cached reference data lookups
 * - Support for 10,000+ TPS throughput with connection pooling
 * - Optimistic locking through JPA @Version field support
 * 
 * Original COBOL Program Mapping:
 * - Replaces VSAM TRANTYPE file operations
 * - Supports transaction categorization matching COBOL EVALUATE statements
 * - Maintains reference data integrity for transaction processing
 * 
 * @since 1.0
 * @see TransactionType
 */
@Repository
public interface TransactionTypeRepository extends JpaRepository<TransactionType, String> {

    /**
     * Finds a transaction type by its unique type code with caching optimization.
     * Replaces COBOL READ operation on TRANTYPE file with direct key access.
     * 
     * @param typeCode the 2-character transaction type code (PIC X(02) equivalent)
     * @return Optional containing the transaction type if found, empty otherwise
     */
    @Cacheable(value = "transactionTypes", key = "#typeCode")
    Optional<TransactionType> findByTypeCode(String typeCode);

    /**
     * Finds all transaction types with a specific status for validation processing.
     * Supports active/inactive transaction type filtering for business rule enforcement.
     * 
     * @param status the transaction type status code
     * @return List of transaction types matching the specified status
     */
    @Cacheable(value = "transactionTypesByStatus", key = "#status")
    List<TransactionType> findByStatus(String status);

    /**
     * Finds all active transaction types for transaction processing operations.
     * Cached method for high-frequency access during transaction validation.
     * 
     * @return List of all active transaction types
     */
    @Cacheable(value = "activeTransactionTypes")
    @Query("SELECT tt FROM TransactionType tt WHERE tt.status = 'A' ORDER BY tt.typeCode")
    List<TransactionType> findAllActive();

    /**
     * Finds transaction types by description pattern for user search functionality.
     * Supports partial description matching for administrative screens.
     * 
     * @param descriptionPattern the description pattern to search for
     * @return List of transaction types matching the description pattern
     */
    @Query("SELECT tt FROM TransactionType tt WHERE UPPER(tt.description) LIKE UPPER(CONCAT('%', :pattern, '%')) ORDER BY tt.typeCode")
    List<TransactionType> findByDescriptionContainingIgnoreCase(@Param("pattern") String descriptionPattern);

    /**
     * Finds all transaction types with pagination support for administrative screens.
     * Replaces COBOL browse operations with Spring Data pagination.
     * 
     * @param pageable pagination and sorting parameters
     * @return Page of transaction types with total count information
     */
    @Query("SELECT tt FROM TransactionType tt ORDER BY tt.typeCode")
    Page<TransactionType> findAllWithPagination(Pageable pageable);

    /**
     * Checks if a transaction type exists and is active for validation operations.
     * High-performance method for transaction processing validation logic.
     * 
     * @param typeCode the transaction type code to validate
     * @return true if the transaction type exists and is active, false otherwise
     */
    @Cacheable(value = "transactionTypeValidation", key = "#typeCode")
    @Query("SELECT COUNT(tt) > 0 FROM TransactionType tt WHERE tt.typeCode = :typeCode AND tt.status = 'A'")
    boolean existsByTypeCodeAndActive(@Param("typeCode") String typeCode);

    /**
     * Finds transaction types that support specific categorization rules.
     * Implements rule engine matching COBOL EVALUATE statements for categorization logic.
     * 
     * @param categoryCode the category code to match against
     * @return List of transaction types supporting the specified category
     */
    @Cacheable(value = "transactionTypesByCategory", key = "#categoryCode")
    @Query("SELECT DISTINCT tt FROM TransactionType tt " +
           "JOIN tt.transactionCategories tc " +
           "WHERE tc.categoryCode = :categoryCode AND tt.status = 'A'")
    List<TransactionType> findByCategoryCode(@Param("categoryCode") String categoryCode);

    /**
     * Validates transaction type and category combination for business rule enforcement.
     * Replicates COBOL EVALUATE statement logic for transaction categorization validation.
     * 
     * @param typeCode the transaction type code
     * @param categoryCode the transaction category code
     * @return true if the combination is valid, false otherwise
     */
    @Cacheable(value = "transactionTypeCategoryValidation", key = "#typeCode + '_' + #categoryCode")
    @Query("SELECT COUNT(tt) > 0 FROM TransactionType tt " +
           "JOIN tt.transactionCategories tc " +
           "WHERE tt.typeCode = :typeCode AND tc.categoryCode = :categoryCode AND tt.status = 'A'")
    boolean validateTypeAndCategory(@Param("typeCode") String typeCode, @Param("categoryCode") String categoryCode);

    /**
     * Counts active transaction types for system monitoring and capacity planning.
     * Used for operational dashboards and system health checks.
     * 
     * @return count of active transaction types
     */
    @Cacheable(value = "activeTransactionTypeCount")
    @Query("SELECT COUNT(tt) FROM TransactionType tt WHERE tt.status = 'A'")
    long countActiveTransactionTypes();

    /**
     * Finds transaction types modified since a specific timestamp for cache invalidation.
     * Supports incremental cache refresh operations for maintaining data consistency.
     * 
     * @param lastModified the timestamp to compare against
     * @return List of transaction types modified since the specified time
     */
    @Query("SELECT tt FROM TransactionType tt WHERE tt.updatedAt > :lastModified ORDER BY tt.updatedAt")
    List<TransactionType> findModifiedSince(@Param("lastModified") java.time.LocalDateTime lastModified);

    /**
     * Finds transaction types by status with pagination for administrative reporting.
     * Supports filtered browsing of transaction types by status with page controls.
     * 
     * @param status the transaction type status to filter by
     * @param pageable pagination and sorting parameters
     * @return Page of transaction types matching the specified status
     */
    @Query("SELECT tt FROM TransactionType tt WHERE tt.status = :status ORDER BY tt.typeCode")
    Page<TransactionType> findByStatusWithPagination(@Param("status") String status, Pageable pageable);

    /**
     * Bulk validation method for transaction processing operations.
     * Validates multiple transaction type codes in a single database operation
     * for improved performance during batch processing.
     * 
     * @param typeCodes list of transaction type codes to validate
     * @return List of valid transaction types from the input list
     */
    @Cacheable(value = "bulkTransactionTypeValidation", key = "#typeCodes.toString()")
    @Query("SELECT tt FROM TransactionType tt WHERE tt.typeCode IN :typeCodes AND tt.status = 'A'")
    List<TransactionType> findValidTypesInList(@Param("typeCodes") List<String> typeCodes);

    /**
     * Gets reference data summary for system initialization and cache warming.
     * Loads essential transaction type data for application startup and cache preloading.
     * 
     * @return List of all active transaction types with minimal data for cache warming
     */
    @Cacheable(value = "transactionTypeReferenceData")
    @Query("SELECT tt.typeCode, tt.description, tt.status FROM TransactionType tt WHERE tt.status = 'A' ORDER BY tt.typeCode")
    List<Object[]> getReferenceDataSummary();
}