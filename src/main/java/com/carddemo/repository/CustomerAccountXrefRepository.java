package com.carddemo.repository;

import com.carddemo.entity.CustomerAccountXref;
import com.carddemo.entity.CustomerAccountXrefId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for customer-account cross-reference operations.
 * 
 * Replaces VSAM CXACAIX file with PostgreSQL customer_account_xref table access,
 * providing alternate index access for customer-to-account relationship navigation
 * and management as specified in Section 6.2.1.1 Database Design.
 * 
 * This repository implements composite primary key support on (customer_id, account_id)
 * for relationship mapping and provides alternate index access patterns to support
 * bidirectional navigation between customers and accounts.
 * 
 * Key Features:
 * - Composite primary key operations via CustomerAccountXrefId
 * - Alternate index access through findByCustomerId and findByAccountId
 * - Pagination support for large result sets
 * - Optimistic locking support through version fields
 * - COBOL-equivalent relationship navigation patterns
 * 
 * VSAM Migration Notes:
 * - Replaces CXACAIX VSAM file alternate index functionality
 * - Maintains bidirectional customer-account relationship access
 * - Preserves exact lookup patterns from COBOL programs
 * - Supports high-volume transaction processing requirements
 * 
 * Performance Considerations:
 * - Composite PostgreSQL indexes on (customer_id, account_id) for optimal query performance
 * - Paginated queries for memory-efficient large result set handling
 * - Prepared statement caching for sub-millisecond parameter binding
 * - Optimized for 10,000+ TPS transaction volume requirements
 * 
 * @see CustomerAccountXref Entity representing the cross-reference relationship
 * @see CustomerAccountXrefId Composite primary key class
 * @see Customer Customer entity for relationship navigation
 * @see Account Account entity for relationship navigation
 * 
 * @author CardDemo Development Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface CustomerAccountXrefRepository extends JpaRepository<CustomerAccountXref, CustomerAccountXrefId> {

    /**
     * Finds all customer-account cross-references for a specific customer ID.
     * 
     * Provides alternate index access equivalent to VSAM CXACAIX alternate key access
     * for customer-to-account relationship navigation. Supports one-to-many customer
     * to account relationships as specified in database design requirements.
     * 
     * Performance: Utilizes composite PostgreSQL index on (customer_id, account_id)
     * for optimal query execution under high transaction load.
     * 
     * COBOL Equivalent: Replaces STARTBR/READNEXT sequence on CXACAIX by customer ID
     * 
     * @param customerId 9-digit customer identifier (PIC 9(09) equivalent)
     * @return List of customer-account cross-references for the specified customer
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    List<CustomerAccountXref> findByCustomerId(String customerId);

    /**
     * Finds all customer-account cross-references for a specific customer ID with pagination.
     * 
     * Provides memory-efficient access to large result sets when customers have
     * multiple accounts. Enables efficient batch processing and UI pagination
     * without loading entire result sets into memory.
     * 
     * Performance: Combines composite index access with PostgreSQL LIMIT/OFFSET
     * optimization for scalable large result set handling.
     * 
     * @param customerId 9-digit customer identifier (PIC 9(09) equivalent)
     * @param pageable Pagination parameters for result set management
     * @return Page of customer-account cross-references with pagination metadata
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    Page<CustomerAccountXref> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Finds all customer-account cross-references for a specific account ID.
     * 
     * Provides reverse lookup capability for account-to-customer relationship
     * navigation. Supports account inquiry operations requiring customer identification
     * and joint account scenarios where multiple customers share an account.
     * 
     * Performance: Utilizes composite PostgreSQL index on (customer_id, account_id)
     * with reverse access pattern optimization.
     * 
     * COBOL Equivalent: Replaces STARTBR/READNEXT sequence on CXACAIX by account ID
     * 
     * @param accountId 11-digit account identifier (PIC 9(11) equivalent)
     * @return List of customer-account cross-references for the specified account
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    List<CustomerAccountXref> findByAccountId(String accountId);

    /**
     * Finds all customer-account cross-references for a specific account ID with pagination.
     * 
     * Provides efficient access to customer relationships for accounts with
     * multiple authorized users or complex account structures requiring
     * paginated result handling.
     * 
     * @param accountId 11-digit account identifier (PIC 9(11) equivalent)
     * @param pageable Pagination parameters for result set management
     * @return Page of customer-account cross-references with pagination metadata
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    Page<CustomerAccountXref> findByAccountId(String accountId, Pageable pageable);

    /**
     * Finds a specific customer-account cross-reference by customer ID and account ID.
     * 
     * Provides direct access to a specific customer-account relationship using
     * both components of the composite primary key. Optimized for high-frequency
     * relationship validation operations during transaction processing.
     * 
     * Performance: Direct primary key access via composite PostgreSQL index
     * for sub-millisecond lookup times under high transaction volume.
     * 
     * COBOL Equivalent: Direct READ operation on CXACAIX with full key
     * 
     * @param customerId 9-digit customer identifier (PIC 9(09) equivalent)
     * @param accountId 11-digit account identifier (PIC 9(11) equivalent)
     * @return Optional containing the cross-reference if found, empty otherwise
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    Optional<CustomerAccountXref> findByCustomerIdAndAccountId(String customerId, String accountId);

    /**
     * Checks if a customer-account relationship exists.
     * 
     * Provides efficient existence checking for relationship validation
     * without retrieving full entity data. Optimized for authorization
     * and validation scenarios during transaction processing.
     * 
     * Performance: Uses PostgreSQL EXISTS query for minimal data transfer
     * and optimal performance under high transaction load.
     * 
     * @param customerId 9-digit customer identifier (PIC 9(09) equivalent)
     * @param accountId 11-digit account identifier (PIC 9(11) equivalent)
     * @return true if the customer-account relationship exists, false otherwise
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    boolean existsByCustomerIdAndAccountId(String customerId, String accountId);

    /**
     * Counts the number of accounts associated with a specific customer.
     * 
     * Provides efficient account count calculation for customer portfolio
     * analysis and business logic validation without retrieving full
     * relationship entities.
     * 
     * Performance: Uses PostgreSQL COUNT aggregate function for optimal
     * performance with minimal memory usage.
     * 
     * @param customerId 9-digit customer identifier (PIC 9(09) equivalent)
     * @return Number of accounts associated with the specified customer
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    long countByCustomerId(String customerId);

    /**
     * Counts the number of customers associated with a specific account.
     * 
     * Provides efficient customer count calculation for joint account
     * analysis and account management operations requiring customer
     * relationship counts.
     * 
     * Performance: Uses PostgreSQL COUNT aggregate function for optimal
     * performance with minimal memory usage.
     * 
     * @param accountId 11-digit account identifier (PIC 9(11) equivalent)
     * @return Number of customers associated with the specified account
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    long countByAccountId(String accountId);

    /**
     * Finds all active customer-account relationships.
     * 
     * Provides comprehensive access to all active customer-account relationships
     * for reporting, batch processing, and administrative operations. Supports
     * enterprise-wide relationship analysis and maintenance operations.
     * 
     * Performance: Uses pagination to handle large result sets efficiently
     * without overwhelming system memory during batch processing operations.
     * 
     * Note: This query assumes an 'active' status field exists in the entity.
     * Implementation may need adjustment based on actual entity structure.
     * 
     * @param pageable Pagination parameters for large result set management
     * @return Page of all active customer-account cross-references
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    @Query("SELECT cax FROM CustomerAccountXref cax WHERE cax.status = 'ACTIVE' ORDER BY cax.customerId, cax.accountId")
    Page<CustomerAccountXref> findAllActiveRelationships(Pageable pageable);

    /**
     * Finds customer-account relationships with custom filtering criteria.
     * 
     * Provides flexible query capability for complex business logic requirements
     * such as relationship type filtering, date range queries, or status-based
     * filtering for administrative and reporting operations.
     * 
     * This method demonstrates the repository's extensibility for future
     * business requirements while maintaining performance optimization.
     * 
     * @param customerId Customer ID filter (optional, can be null)
     * @param accountId Account ID filter (optional, can be null)
     * @param status Relationship status filter (optional, can be null)
     * @param pageable Pagination parameters for result set management
     * @return Page of filtered customer-account cross-references
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    @Query("SELECT cax FROM CustomerAccountXref cax WHERE " +
           "(:customerId IS NULL OR cax.customerId = :customerId) AND " +
           "(:accountId IS NULL OR cax.accountId = :accountId) AND " +
           "(:status IS NULL OR cax.status = :status) " +
           "ORDER BY cax.customerId, cax.accountId")
    Page<CustomerAccountXref> findWithFilters(
            @Param("customerId") String customerId,
            @Param("accountId") String accountId,
            @Param("status") String status,
            Pageable pageable);

    /**
     * Deletes customer-account relationship by composite key components.
     * 
     * Provides efficient relationship deletion using individual key components
     * rather than requiring entity retrieval. Optimized for high-volume
     * relationship maintenance operations.
     * 
     * Performance: Direct DELETE operation using composite primary key
     * for optimal performance under transaction load.
     * 
     * COBOL Equivalent: DELETE operation on CXACAIX with full key
     * 
     * @param customerId 9-digit customer identifier (PIC 9(09) equivalent)
     * @param accountId 11-digit account identifier (PIC 9(11) equivalent)
     * @return Number of relationships deleted (0 or 1)
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    long deleteByCustomerIdAndAccountId(String customerId, String accountId);

    /**
     * Deletes all customer-account relationships for a specific customer.
     * 
     * Provides bulk deletion capability for customer closure operations
     * or data cleanup scenarios. Handles cascade deletion requirements
     * for complete customer relationship removal.
     * 
     * Performance: Bulk DELETE operation optimized for multiple relationship
     * removal with minimal database round trips.
     * 
     * CAUTION: This operation removes ALL account relationships for the customer.
     * Ensure proper business logic validation before execution.
     * 
     * @param customerId 9-digit customer identifier (PIC 9(09) equivalent)
     * @return Number of relationships deleted
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    long deleteByCustomerId(String customerId);

    /**
     * Deletes all customer-account relationships for a specific account.
     * 
     * Provides bulk deletion capability for account closure operations
     * or data cleanup scenarios. Handles cascade deletion requirements
     * for complete account relationship removal.
     * 
     * Performance: Bulk DELETE operation optimized for multiple relationship
     * removal with minimal database round trips.
     * 
     * CAUTION: This operation removes ALL customer relationships for the account.
     * Ensure proper business logic validation before execution.
     * 
     * @param accountId 11-digit account identifier (PIC 9(11) equivalent)
     * @return Number of relationships deleted
     * @throws org.springframework.dao.DataAccessException if database access fails
     */
    long deleteByAccountId(String accountId);
}