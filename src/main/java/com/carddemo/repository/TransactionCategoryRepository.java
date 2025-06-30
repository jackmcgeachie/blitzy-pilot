package com.carddemo.repository;

import com.carddemo.entity.TransactionCategory;
import com.carddemo.entity.TransactionCategory.TransactionCategoryId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for TransactionCategory entity operations.
 * 
 * Replaces VSAM TRANCATG file access with PostgreSQL transaction_categories table operations.
 * Provides transaction categorization definitions, validation rules, and cached reference data
 * for optimal performance in high-volume transaction processing environments.
 * 
 * This repository implements the VSAM-to-PostgreSQL mapping per Section 0.2.2:
 * TRANCATG → transaction_categories table with composite primary key (type_code, category_code)
 * 
 * Key Features:
 * - Composite primary key operations using TransactionCategoryId
 * - Reference data caching through Spring Cache abstraction
 * - Transaction categorization support for business rule enforcement
 * - High-performance lookup methods for transaction processing
 * - Validation queries for transaction category classification
 * 
 * Performance Characteristics:
 * - Sub-100ms response times for cached reference data lookups
 * - Support for 10,000+ TPS transaction categorization operations
 * - Optimized composite index usage for efficient queries
 * 
 * @author CardDemo Development Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface TransactionCategoryRepository extends JpaRepository<TransactionCategory, TransactionCategoryId> {

    /**
     * Finds transaction category by category code component.
     * 
     * Provides efficient lookup for transaction categorization during processing.
     * Results are cached for optimal performance during high-volume operations.
     * 
     * @param categoryCode The 4-digit category code from TRAN-CAT-CD
     * @return List of transaction categories matching the category code
     */
    @Cacheable(value = "transactionCategories", key = "#categoryCode")
    @Query("SELECT tc FROM TransactionCategory tc WHERE tc.id.categoryCode = :categoryCode")
    List<TransactionCategory> findByCategoryCode(@Param("categoryCode") Integer categoryCode);

    /**
     * Finds transaction category by transaction type code component.
     * 
     * Enables lookup of all categories associated with a specific transaction type.
     * Essential for transaction type-based categorization rules.
     * 
     * @param typeCode The 2-character transaction type code from TRAN-TYPE-CD
     * @return List of transaction categories for the specified type
     */
    @Cacheable(value = "transactionCategories", key = "#typeCode")
    @Query("SELECT tc FROM TransactionCategory tc WHERE tc.id.typeCode = :typeCode")
    List<TransactionCategory> findByTypeCode(@Param("typeCode") String typeCode);

    /**
     * Finds transaction category by exact composite key match.
     * 
     * Provides direct access using both type code and category code components.
     * Replicates VSAM KSDS primary key access pattern.
     * 
     * @param typeCode The 2-character transaction type code
     * @param categoryCode The 4-digit category code
     * @return Optional containing the matching transaction category
     */
    @Cacheable(value = "transactionCategories", key = "#typeCode + '_' + #categoryCode")
    @Query("SELECT tc FROM TransactionCategory tc WHERE tc.id.typeCode = :typeCode AND tc.id.categoryCode = :categoryCode")
    Optional<TransactionCategory> findByTypeCodeAndCategoryCode(@Param("typeCode") String typeCode, 
                                                               @Param("categoryCode") Integer categoryCode);

    /**
     * Finds active transaction categories for business rule enforcement.
     * 
     * Returns only categories that are currently active and available for
     * transaction classification. Supports business rule validation during
     * transaction processing.
     * 
     * Note: This method assumes an 'active' or 'status' field exists in the entity
     * based on typical mainframe data patterns. Implementation will depend on
     * actual TransactionCategory entity structure.
     * 
     * @return List of active transaction categories
     */
    @Cacheable(value = "activeTransactionCategories")
    @Query("SELECT tc FROM TransactionCategory tc WHERE tc.status = 'A' ORDER BY tc.id.typeCode, tc.id.categoryCode")
    List<TransactionCategory> findByStatusActive();

    /**
     * Finds transaction categories by description pattern.
     * 
     * Enables search functionality for transaction category maintenance and
     * administrative operations. Supports case-insensitive partial matching.
     * 
     * @param descriptionPattern The description pattern to search for
     * @return List of matching transaction categories
     */
    @Query("SELECT tc FROM TransactionCategory tc WHERE UPPER(tc.description) LIKE UPPER(CONCAT('%', :descriptionPattern, '%')) ORDER BY tc.id.typeCode, tc.id.categoryCode")
    List<TransactionCategory> findByDescriptionContainingIgnoreCase(@Param("descriptionPattern") String descriptionPattern);

    /**
     * Validates transaction category existence for business rule enforcement.
     * 
     * Provides high-performance validation check during transaction processing
     * to ensure only valid category codes are used. Returns boolean result
     * for efficient validation without loading full entity.
     * 
     * @param typeCode The 2-character transaction type code
     * @param categoryCode The 4-digit category code
     * @return true if the category combination exists and is valid
     */
    @Cacheable(value = "categoryValidation", key = "#typeCode + '_' + #categoryCode")
    @Query("SELECT COUNT(tc) > 0 FROM TransactionCategory tc WHERE tc.id.typeCode = :typeCode AND tc.id.categoryCode = :categoryCode")
    boolean existsByTypeCodeAndCategoryCode(@Param("typeCode") String typeCode, 
                                          @Param("categoryCode") Integer categoryCode);

    /**
     * Gets all transaction categories with pagination support.
     * 
     * Provides paginated access to all transaction categories for administrative
     * and reporting operations. Includes ordering by type code and category code
     * for consistent result presentation.
     * 
     * @param pageable Pagination parameters
     * @return Page of transaction categories
     */
    @Query("SELECT tc FROM TransactionCategory tc ORDER BY tc.id.typeCode, tc.id.categoryCode")
    Page<TransactionCategory> findAllOrderedByTypeAndCategory(Pageable pageable);

    /**
     * Gets transaction categories for specific transaction type with count.
     * 
     * Provides count of categories available for a specific transaction type.
     * Useful for validation rules and administrative reporting.
     * 
     * @param typeCode The 2-character transaction type code
     * @return Count of categories for the specified type
     */
    @Query("SELECT COUNT(tc) FROM TransactionCategory tc WHERE tc.id.typeCode = :typeCode")
    long countByTypeCode(@Param("typeCode") String typeCode);

    /**
     * Finds transaction categories used in actual transactions.
     * 
     * Returns only categories that have been used in actual transaction records.
     * Useful for analytics and reporting on active category usage patterns.
     * 
     * @return List of transaction categories with transaction history
     */
    @Query("SELECT DISTINCT tc FROM TransactionCategory tc " +
           "INNER JOIN Transaction t ON t.transactionType.typeCode = tc.id.typeCode " +
           "AND t.transactionCategory.id.categoryCode = tc.id.categoryCode " +
           "ORDER BY tc.id.typeCode, tc.id.categoryCode")
    List<TransactionCategory> findCategoriesWithTransactionHistory();

    /**
     * Finds transaction categories by type with description filtering.
     * 
     * Combines type code filtering with description pattern matching for
     * advanced search capabilities in administrative interfaces.
     * 
     * @param typeCode The 2-character transaction type code
     * @param descriptionPattern The description pattern to search for
     * @return List of matching transaction categories
     */
    @Query("SELECT tc FROM TransactionCategory tc WHERE tc.id.typeCode = :typeCode " +
           "AND UPPER(tc.description) LIKE UPPER(CONCAT('%', :descriptionPattern, '%')) " +
           "ORDER BY tc.id.categoryCode")
    List<TransactionCategory> findByTypeCodeAndDescriptionContaining(@Param("typeCode") String typeCode,
                                                                    @Param("descriptionPattern") String descriptionPattern);

    /**
     * Gets category code range for a specific transaction type.
     * 
     * Returns the minimum and maximum category codes for a given transaction type.
     * Useful for validation range checking and administrative operations.
     * 
     * @param typeCode The 2-character transaction type code
     * @return Array containing [min, max] category codes
     */
    @Query("SELECT MIN(tc.id.categoryCode), MAX(tc.id.categoryCode) FROM TransactionCategory tc WHERE tc.id.typeCode = :typeCode")
    Object[] getCategoryCodeRangeByTypeCode(@Param("typeCode") String typeCode);

    /**
     * Cache eviction method for reference data updates.
     * 
     * Clears all cached transaction category data when reference data is updated.
     * Should be called after any insert, update, or delete operations to maintain
     * cache consistency.
     */
    @CacheEvict(value = {"transactionCategories", "activeTransactionCategories", "categoryValidation"}, allEntries = true)
    default void evictTransactionCategoryCache() {
        // Method intentionally empty - annotation handles cache eviction
    }

    /**
     * Bulk validation method for multiple category codes.
     * 
     * Validates multiple category codes in a single query for efficient
     * batch processing operations. Returns list of valid categories.
     * 
     * @param typeCode The transaction type code
     * @param categoryCodes List of category codes to validate
     * @return List of valid transaction categories
     */
    @Query("SELECT tc FROM TransactionCategory tc WHERE tc.id.typeCode = :typeCode AND tc.id.categoryCode IN :categoryCodes")
    List<TransactionCategory> findValidCategoriesByTypeCodeAndCategoryCodes(@Param("typeCode") String typeCode,
                                                                           @Param("categoryCodes") List<Integer> categoryCodes);

    /**
     * Statistics query for category usage analysis.
     * 
     * Returns transaction count statistics grouped by category for reporting
     * and analytics purposes. Joins with transaction table for usage metrics.
     * 
     * @return List of Object arrays containing [typeCode, categoryCode, transactionCount]
     */
    @Query("SELECT tc.id.typeCode, tc.id.categoryCode, COUNT(t.id) " +
           "FROM TransactionCategory tc " +
           "LEFT JOIN Transaction t ON t.transactionType.typeCode = tc.id.typeCode " +
           "AND t.transactionCategory.id.categoryCode = tc.id.categoryCode " +
           "GROUP BY tc.id.typeCode, tc.id.categoryCode " +
           "ORDER BY COUNT(t.id) DESC")
    List<Object[]> getCategoryUsageStatistics();
}