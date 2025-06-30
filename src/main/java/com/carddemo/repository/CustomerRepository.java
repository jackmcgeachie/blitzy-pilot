/*
 * CustomerRepository.java
 * 
 * Spring Data JPA repository interface for customer database operations.
 * Replaces VSAM CUSTDAT file access with PostgreSQL customers table operations.
 * 
 * Migrated from:
 * - app/cpy/CUSTREC.cpy - Customer record structure
 * - app/cpy/CVCUS01Y.cpy - Customer validation copybook
 * - app/cbl/CBCUS01C.cbl - Batch customer processing program
 * 
 * This repository provides CRUD operations, custom query methods for SSN and phone 
 * lookups, and pagination support for customer management screens.
 */
package com.carddemo.repository;

import com.carddemo.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for Customer entity operations.
 * 
 * Replaces VSAM CUSTDAT file access patterns with PostgreSQL database operations:
 * - Direct access through JPA findById() operations (Section 6.2.2.4)
 * - Custom query methods for customer search by SSN, phone, and email matching COBOL alternate key access patterns
 * - Complex customer lookups replacing COBOL STARTBR/READNEXT patterns
 * - Pagination and sorting for customer listing screens per Spring Data JPA framework requirements
 * 
 * Database Design Reference:
 * - customers table with customer_id (BIGSERIAL) primary key (Section 6.2.1.1)
 * - Composite indexes on customer_id, ssn, phone for alternate key access (Section 3.5.1.2)
 * - Optimistic locking through version_number column (Section 3.5.1.3)
 * 
 * Performance Optimization:
 * - PreparedStatement caching for sub-millisecond parameter binding (Section 6.2.4.1)
 * - Composite B-tree indexes for high-performance lookups (Section 6.2.4.1)
 * - Connection pool optimization through HikariCP integration (Section 6.2.4.3)
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Find customer by customer ID string (COBOL CUST-ID equivalent).
     * Replicates VSAM direct access by primary key.
     * 
     * Maps from COBOL: READ CUSTFILE-FILE INTO CUSTOMER-RECORD KEY IS CUST-ID
     * 
     * @param customerId 9-digit customer identifier as String
     * @return Optional<Customer> containing customer if found
     */
    Optional<Customer> findByCustomerId(String customerId);

    /**
     * Find customer by Social Security Number (COBOL CUST-SSN equivalent).
     * Implements alternate index access pattern from VSAM AIX.
     * 
     * Maps from COBOL: READ CUSTFILE-FILE INTO CUSTOMER-RECORD KEY IS CUST-SSN
     * 
     * @param ssn 9-digit Social Security Number as String
     * @return Optional<Customer> containing customer if found
     */
    Optional<Customer> findBySsn(String ssn);

    /**
     * Find customer by first phone number (COBOL CUST-PHONE-NUM-1 equivalent).
     * Supports customer lookup by primary phone number.
     * 
     * @param phoneNumber Primary phone number as String (max 15 characters)
     * @return Optional<Customer> containing customer if found
     */
    Optional<Customer> findByPhoneNumber1(String phoneNumber);

    /**
     * Find customer by either phone number field.
     * Supports customer lookup by any available phone number.
     * 
     * @param phoneNumber Phone number to search in either phone field
     * @return Optional<Customer> containing customer if found
     */
    Optional<Customer> findByPhoneNumber1OrPhoneNumber2(String phoneNumber, String phoneNumber2);

    /**
     * Find customers by last name (COBOL CUST-LAST-NAME equivalent).
     * Supports customer search functionality with partial matching.
     * 
     * @param lastName Customer last name
     * @return List<Customer> containing all customers with matching last name
     */
    List<Customer> findByLastNameIgnoreCase(String lastName);

    /**
     * Find customers by state code (COBOL CUST-ADDR-STATE-CD equivalent).
     * Supports geographic customer analysis and reporting.
     * 
     * @param stateCode 2-character state code
     * @return List<Customer> containing all customers in specified state
     */
    List<Customer> findByStateCode(String stateCode);

    /**
     * Find customers by FICO credit score range.
     * Supports credit analysis and risk assessment functions.
     * 
     * @param minScore Minimum FICO score (inclusive)
     * @param maxScore Maximum FICO score (inclusive)
     * @return List<Customer> containing customers within specified FICO range
     */
    @Query("SELECT c FROM Customer c WHERE c.ficoScore >= :minScore AND c.ficoScore <= :maxScore")
    List<Customer> findByFicoScoreRange(@Param("minScore") Integer minScore, @Param("maxScore") Integer maxScore);

    /**
     * Search customers by partial name match (first, middle, or last name).
     * Implements flexible customer search replacing COBOL string search patterns.
     * 
     * @param searchTerm Partial name to search for
     * @return List<Customer> containing customers with names containing search term
     */
    @Query("SELECT c FROM Customer c WHERE " +
           "UPPER(c.firstName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
           "UPPER(c.middleName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
           "UPPER(c.lastName) LIKE UPPER(CONCAT('%', :searchTerm, '%'))")
    List<Customer> findByNameContaining(@Param("searchTerm") String searchTerm);

    /**
     * Find customers with paginated results for customer listing screens.
     * Replaces COBOL STARTBR/READNEXT sequential access patterns with Spring Data pagination.
     * 
     * Performance optimization: Uses PostgreSQL LIMIT/OFFSET for efficient pagination
     * 
     * @param pageable Pagination and sorting parameters
     * @return Page<Customer> containing paginated customer results
     */
    Page<Customer> findAll(Pageable pageable);

    /**
     * Find customers by state with pagination support.
     * Supports paginated customer listing by geographic region.
     * 
     * @param stateCode 2-character state code
     * @param pageable Pagination and sorting parameters
     * @return Page<Customer> containing paginated customers from specified state
     */
    Page<Customer> findByStateCode(String stateCode, Pageable pageable);

    /**
     * Search customers by name with pagination support.
     * Implements paginated customer search functionality.
     * 
     * @param searchTerm Partial name to search for
     * @param pageable Pagination and sorting parameters
     * @return Page<Customer> containing paginated search results
     */
    @Query("SELECT c FROM Customer c WHERE " +
           "UPPER(c.firstName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
           "UPPER(c.middleName) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
           "UPPER(c.lastName) LIKE UPPER(CONCAT('%', :searchTerm, '%'))")
    Page<Customer> findByNameContaining(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Count customers by state code.
     * Supports reporting and analytics functions.
     * 
     * @param stateCode 2-character state code
     * @return Long count of customers in specified state
     */
    Long countByStateCode(String stateCode);

    /**
     * Find customers by ZIP code for geographic analysis.
     * 
     * @param zipCode ZIP code to search for
     * @return List<Customer> containing customers in specified ZIP code
     */
    List<Customer> findByZipCode(String zipCode);

    /**
     * Check if customer exists by SSN (for duplicate checking).
     * Supports customer registration validation.
     * 
     * @param ssn Social Security Number to check
     * @return boolean indicating if customer with SSN exists
     */
    boolean existsBySsn(String ssn);

    /**
     * Check if customer exists by customer ID string.
     * Supports customer validation in other services.
     * 
     * @param customerId Customer ID to check
     * @return boolean indicating if customer exists
     */
    boolean existsByCustomerId(String customerId);

    /**
     * Find customers by date of birth for age-based analytics.
     * 
     * @param dateOfBirth Date of birth in YYYYMMDD format
     * @return List<Customer> containing customers with specified birth date
     */
    List<Customer> findByDateOfBirth(String dateOfBirth);

    /**
     * Complex customer search with multiple criteria.
     * Supports advanced customer lookup functionality replacing COBOL search logic.
     * 
     * @param firstName First name (can be null)
     * @param lastName Last name (can be null)
     * @param ssn SSN (can be null)
     * @param phoneNumber Phone number (can be null)
     * @param stateCode State code (can be null)
     * @param pageable Pagination parameters
     * @return Page<Customer> containing customers matching search criteria
     */
    @Query("SELECT c FROM Customer c WHERE " +
           "(:firstName IS NULL OR UPPER(c.firstName) LIKE UPPER(CONCAT('%', :firstName, '%'))) AND " +
           "(:lastName IS NULL OR UPPER(c.lastName) LIKE UPPER(CONCAT('%', :lastName, '%'))) AND " +
           "(:ssn IS NULL OR c.ssn = :ssn) AND " +
           "(:phoneNumber IS NULL OR c.phoneNumber1 = :phoneNumber OR c.phoneNumber2 = :phoneNumber) AND " +
           "(:stateCode IS NULL OR c.stateCode = :stateCode)")
    Page<Customer> findByMultipleCriteria(
        @Param("firstName") String firstName,
        @Param("lastName") String lastName,
        @Param("ssn") String ssn,
        @Param("phoneNumber") String phoneNumber,
        @Param("stateCode") String stateCode,
        Pageable pageable
    );

    /**
     * Get customer statistics for reporting.
     * Provides aggregate customer data for dashboard functionality.
     * 
     * @return List of customer count by state for reporting purposes
     */
    @Query("SELECT c.stateCode, COUNT(c) FROM Customer c GROUP BY c.stateCode ORDER BY c.stateCode")
    List<Object[]> getCustomerCountByState();

    /**
     * Find customers with high FICO scores for marketing campaigns.
     * 
     * @param minFicoScore Minimum FICO score threshold
     * @param pageable Pagination parameters
     * @return Page<Customer> containing customers with high FICO scores
     */
    @Query("SELECT c FROM Customer c WHERE c.ficoScore >= :minFicoScore ORDER BY c.ficoScore DESC")
    Page<Customer> findHighFicoCustomers(@Param("minFicoScore") Integer minFicoScore, Pageable pageable);

    /**
     * Delete customer by customer ID string.
     * Supports customer removal operations.
     * 
     * @param customerId Customer ID to delete
     */
    void deleteByCustomerId(String customerId);
}