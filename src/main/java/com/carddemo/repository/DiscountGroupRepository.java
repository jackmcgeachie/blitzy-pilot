/*
 * DiscountGroupRepository.java
 * 
 * Spring Data JPA repository interface for discount group operations,
 * replacing VSAM DISCGRP file with PostgreSQL discount_groups table access.
 * 
 * Manages discount group configuration, rules validation, account assignments,
 * and reference data caching for performance optimization.
 * 
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

import com.carddemo.entity.DiscountGroup;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for DiscountGroup entity operations.
 * 
 * This repository provides CRUD operations for discount group management,
 * replacing VSAM DISCGRP file access with PostgreSQL database operations.
 * Implements composite primary key support and reference data caching
 * for optimal performance in high-volume transaction processing.
 * 
 * Key Features:
 * - Composite primary key support on (group_code, type_code, category_code)
 * - Reference data caching through Spring Cache abstraction
 * - Discount rule validation and group assignment queries
 * - Performance optimization for frequently accessed lookup tables
 * - Support for account discount group assignment operations
 * 
 * Performance Characteristics:
 * - Sub-100ms response times for cached reference data lookups
 * - Composite index access patterns replicating VSAM KSDS functionality
 * - Optimized query methods for discount rule validation
 * - Cache hit ratio target >95% for reference data operations
 */
@Repository
public interface DiscountGroupRepository extends JpaRepository<DiscountGroup, String> {

    /**
     * Finds discount group by group code with caching support.
     * 
     * This method replaces VSAM DISCGRP primary key access patterns
     * and provides cached results for frequently accessed discount groups.
     * 
     * @param groupCode the discount group code (max 10 characters)
     * @return Optional containing the discount group if found
     */
    @Cacheable(value = "discountGroups", key = "#groupCode")
    @Query("SELECT dg FROM DiscountGroup dg WHERE dg.groupCode = :groupCode")
    Optional<DiscountGroup> findByGroupCode(@Param("groupCode") String groupCode);

    /**
     * Finds all discount groups by status with caching support.
     * 
     * This method supports discount group configuration management
     * by enabling status-based filtering with cached results.
     * 
     * @param status the discount group status indicator
     * @return List of discount groups matching the specified status
     */
    @Cacheable(value = "discountGroupsByStatus", key = "#status")
    @Query("SELECT dg FROM DiscountGroup dg WHERE dg.status = :status ORDER BY dg.groupCode")
    List<DiscountGroup> findByStatus(@Param("status") String status);

    /**
     * Finds active discount groups for quick reference data access.
     * 
     * This method provides cached access to active discount groups
     * for real-time transaction processing and validation.
     * 
     * @return List of active discount groups sorted by group code
     */
    @Cacheable(value = "activeDiscountGroups")
    @Query("SELECT dg FROM DiscountGroup dg WHERE dg.status = 'A' ORDER BY dg.groupCode")
    List<DiscountGroup> findActiveDiscountGroups();

    /**
     * Finds discount groups by transaction type code.
     * 
     * This method supports discount rule validation by enabling
     * transaction type-specific discount group lookups.
     * 
     * @param typeCode the transaction type code (2 characters)
     * @return List of discount groups for the specified transaction type
     */
    @Query("SELECT dg FROM DiscountGroup dg WHERE dg.transactionTypeCode = :typeCode AND dg.status = 'A' ORDER BY dg.groupCode")
    List<DiscountGroup> findByTransactionTypeCode(@Param("typeCode") String typeCode);

    /**
     * Finds discount groups by transaction category code.
     * 
     * This method supports transaction categorization and discount
     * rule application by category-specific lookups.
     * 
     * @param categoryCode the transaction category code (4 digits)
     * @return List of discount groups for the specified category
     */
    @Query("SELECT dg FROM DiscountGroup dg WHERE dg.transactionCategoryCode = :categoryCode AND dg.status = 'A' ORDER BY dg.groupCode")
    List<DiscountGroup> findByTransactionCategoryCode(@Param("categoryCode") Integer categoryCode);

    /**
     * Finds discount groups by interest rate range.
     * 
     * This method supports discount group assignment queries
     * by enabling interest rate-based filtering for account assignments.
     * 
     * @param minRate minimum interest rate (inclusive)
     * @param maxRate maximum interest rate (inclusive)
     * @return List of discount groups within the specified rate range
     */
    @Query("SELECT dg FROM DiscountGroup dg WHERE dg.interestRate >= :minRate AND dg.interestRate <= :maxRate AND dg.status = 'A' ORDER BY dg.interestRate")
    List<DiscountGroup> findByInterestRateRange(@Param("minRate") BigDecimal minRate, @Param("maxRate") BigDecimal maxRate);

    /**
     * Finds discount groups by composite key components.
     * 
     * This method provides composite primary key access patterns
     * equivalent to VSAM KSDS key access for discount group validation.
     * 
     * @param groupCode the discount group code
     * @param typeCode the transaction type code
     * @param categoryCode the transaction category code
     * @return Optional containing the discount group if found
     */
    @Cacheable(value = "discountGroupsComposite", key = "#groupCode + '_' + #typeCode + '_' + #categoryCode")
    @Query("SELECT dg FROM DiscountGroup dg WHERE dg.groupCode = :groupCode AND dg.transactionTypeCode = :typeCode AND dg.transactionCategoryCode = :categoryCode")
    Optional<DiscountGroup> findByCompositeKey(
            @Param("groupCode") String groupCode,
            @Param("typeCode") String typeCode,
            @Param("categoryCode") Integer categoryCode);

    /**
     * Checks if a discount group exists for the given parameters.
     * 
     * This method provides efficient existence checking for
     * discount group validation without loading full entity data.
     * 
     * @param groupCode the discount group code
     * @param typeCode the transaction type code
     * @param categoryCode the transaction category code
     * @return true if discount group exists, false otherwise
     */
    @Query("SELECT COUNT(dg) > 0 FROM DiscountGroup dg WHERE dg.groupCode = :groupCode AND dg.transactionTypeCode = :typeCode AND dg.transactionCategoryCode = :categoryCode")
    boolean existsByCompositeKey(
            @Param("groupCode") String groupCode,
            @Param("typeCode") String typeCode,
            @Param("categoryCode") Integer categoryCode);

    /**
     * Finds discount groups with pagination support.
     * 
     * This method provides paginated access to discount groups
     * for administrative screens and bulk operations.
     * 
     * @param pageable pagination information
     * @return Page of discount groups with pagination metadata
     */
    @Query("SELECT dg FROM DiscountGroup dg ORDER BY dg.groupCode, dg.transactionTypeCode, dg.transactionCategoryCode")
    Page<DiscountGroup> findAllWithPagination(Pageable pageable);

    /**
     * Finds discount groups by partial group code match.
     * 
     * This method supports search functionality for discount group
     * lookup screens with wildcard matching capabilities.
     * 
     * @param partialGroupCode partial group code for wildcard search
     * @param pageable pagination information
     * @return Page of discount groups matching the search criteria
     */
    @Query("SELECT dg FROM DiscountGroup dg WHERE dg.groupCode LIKE %:partialGroupCode% ORDER BY dg.groupCode")
    Page<DiscountGroup> findByGroupCodeContaining(@Param("partialGroupCode") String partialGroupCode, Pageable pageable);

    /**
     * Counts active discount groups by transaction type.
     * 
     * This method provides statistical information for
     * discount group distribution analysis and reporting.
     * 
     * @param typeCode the transaction type code
     * @return count of active discount groups for the transaction type
     */
    @Query("SELECT COUNT(dg) FROM DiscountGroup dg WHERE dg.transactionTypeCode = :typeCode AND dg.status = 'A'")
    long countActiveByTransactionType(@Param("typeCode") String typeCode);

    /**
     * Finds all distinct group codes for reference data population.
     * 
     * This method supports dropdown population and validation
     * by providing all available discount group codes.
     * 
     * @return List of distinct group codes sorted alphabetically
     */
    @Cacheable(value = "discountGroupCodes")
    @Query("SELECT DISTINCT dg.groupCode FROM DiscountGroup dg WHERE dg.status = 'A' ORDER BY dg.groupCode")
    List<String> findAllDistinctGroupCodes();

    /**
     * Evicts discount group cache entries.
     * 
     * This method provides cache management capabilities for
     * maintaining cache consistency when discount groups are modified.
     * 
     * Use this method after discount group create, update, or delete operations.
     */
    @CacheEvict(value = {"discountGroups", "discountGroupsByStatus", "activeDiscountGroups", 
                         "discountGroupsComposite", "discountGroupCodes"}, allEntries = true)
    default void evictDiscountGroupCaches() {
        // Cache eviction is handled by Spring Cache abstraction
        // This method serves as a trigger for cache invalidation
    }

    /**
     * Custom method to refresh cache for a specific discount group.
     * 
     * This method supports selective cache refresh operations
     * for individual discount group updates.
     * 
     * @param groupCode the discount group code to refresh in cache
     */
    @CacheEvict(value = "discountGroups", key = "#groupCode")
    default void evictDiscountGroupFromCache(String groupCode) {
        // Individual cache entry eviction
        // Used when specific discount group data is updated
    }
}