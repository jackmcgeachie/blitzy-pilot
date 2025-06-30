/*
 * CardRepository.java
 * 
 * Spring Data JPA repository interface for Card entity operations
 * Replaces VSAM CARDDAT file access with PostgreSQL cards table operations
 * 
 * Converted from COBOL programs:
 * - COCRDLIC.cbl: Card listing with pagination
 * - COCRDSLC.cbl: Card detail search and retrieval  
 * - COCRDUPC.cbl: Card update operations
 * 
 * Original VSAM File: CARDDAT
 * PostgreSQL Table: cards
 * 
 * This repository provides:
 * - Card lifecycle operations replacing CVCRD01Y definitions
 * - Paginated card listing matching 7-record BMS screen layout
 * - Card-account relationship navigation via account_id
 * - Card status filtering for active/inactive card management
 * - Luhn algorithm validation support for 16-digit card numbers
 */
package com.carddemo.repository;

import com.carddemo.entity.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for Card entity database operations.
 * 
 * Replaces VSAM CARDDAT file operations with PostgreSQL cards table access.
 * Provides card lifecycle management, status updates, account associations,
 * and pagination support matching original BMS screen layouts.
 * 
 * Based on COBOL programs:
 * - COCRDLIC: Card listing with 7-record pagination
 * - COCRDSLC: Card search and detail retrieval
 * - COCRDUPC: Card maintenance and updates
 * 
 * Original VSAM operations mapped to JPA methods:
 * - STARTBR/READNEXT -> findAll with Pageable
 * - READ -> findById, findByCardNumber
 * - WRITE -> save (new card)
 * - REWRITE -> save (existing card)
 * - DELETE -> deleteById
 */
@Repository
public interface CardRepository extends JpaRepository<Card, String> {

    /**
     * Find cards by account ID for card-account relationship navigation.
     * 
     * Replaces VSAM alternate index (CARDAIX) access pattern from COCRDLIC.cbl
     * where cards are filtered by account relationship.
     * 
     * Original COBOL logic:
     * - EXEC CICS STARTBR DATASET(CARDAIX) RIDFLD(ACCOUNT-ID)
     * - Browse through card records for specific account
     * 
     * @param accountId 11-digit account identifier (PIC 9(11) from COBOL)
     * @param pageable Pagination parameters for 7-record screen display
     * @return Page of cards associated with the specified account
     */
    @Query("SELECT c FROM Card c WHERE c.accountId = :accountId ORDER BY c.cardNumber")
    Page<Card> findByAccountId(@Param("accountId") String accountId, Pageable pageable);

    /**
     * Find cards by account ID without pagination.
     * 
     * Used for account-card relationship validation and batch processing
     * where all cards for an account need to be retrieved.
     * 
     * @param accountId 11-digit account identifier
     * @return List of all cards associated with the account
     */
    List<Card> findByAccountId(String accountId);

    /**
     * Find cards by card status for active/inactive card management.
     * 
     * Replaces COBOL logic from COCRDLIC.cbl where cards are filtered
     * by CARD-ACTIVE-STATUS field for operational reporting.
     * 
     * Original COBOL validation:
     * - 88 CARD-ACTIVE-STATUS-ACTIVE VALUE 'Y'
     * - 88 CARD-ACTIVE-STATUS-INACTIVE VALUE 'N'
     * 
     * @param cardStatus Single character status ('Y' = Active, 'N' = Inactive)
     * @param pageable Pagination parameters for screen display
     * @return Page of cards with specified status
     */
    @Query("SELECT c FROM Card c WHERE c.cardStatus = :cardStatus ORDER BY c.cardNumber")
    Page<Card> findByCardStatus(@Param("cardStatus") String cardStatus, Pageable pageable);

    /**
     * Find cards by account ID and card status combination.
     * 
     * Supports filtered card listing operations where both account
     * association and status filtering are required.
     * 
     * @param accountId 11-digit account identifier
     * @param cardStatus Single character status
     * @param pageable Pagination parameters
     * @return Page of cards matching both criteria
     */
    @Query("SELECT c FROM Card c WHERE c.accountId = :accountId AND c.cardStatus = :cardStatus ORDER BY c.cardNumber")
    Page<Card> findByAccountIdAndCardStatus(@Param("accountId") String accountId, 
                                          @Param("cardStatus") String cardStatus, 
                                          Pageable pageable);

    /**
     * Find card by card number with exact match.
     * 
     * Replaces COBOL READ operation from COCRDSLC.cbl:
     * - EXEC CICS READ DATASET(CARDDAT) INTO(CARD-RECORD) RIDFLD(CARD-NUM)
     * 
     * Supports 16-digit card number validation with Luhn algorithm
     * as specified in requirements.
     * 
     * @param cardNumber 16-digit card number (PIC X(16) from COBOL)
     * @return Optional Card entity if found
     */
    Optional<Card> findByCardNumber(String cardNumber);

    /**
     * Check if card number exists for duplicate validation.
     * 
     * Used during card creation to prevent duplicate card numbers
     * before WRITE operations, replacing COBOL duplicate key checking.
     * 
     * @param cardNumber 16-digit card number to validate
     * @return true if card number exists, false otherwise
     */
    boolean existsByCardNumber(String cardNumber);

    /**
     * Count total cards by account ID for pagination calculations.
     * 
     * Supports paginated display logic that needs to determine
     * total record count for pagination controls.
     * 
     * @param accountId 11-digit account identifier
     * @return Total count of cards for the account
     */
    @Query("SELECT COUNT(c) FROM Card c WHERE c.accountId = :accountId")
    long countByAccountId(@Param("accountId") String accountId);

    /**
     * Count cards by status for operational reporting.
     * 
     * @param cardStatus Single character status
     * @return Total count of cards with specified status
     */
    long countByCardStatus(String cardStatus);

    /**
     * Find cards with expiration dates approaching for maintenance operations.
     * 
     * Supports batch processing operations that identify cards
     * requiring renewal or status updates.
     * 
     * @param expirationDate Date threshold in COBOL format (PIC X(10))
     * @param pageable Pagination for batch processing chunks
     * @return Page of cards expiring before specified date
     */
    @Query("SELECT c FROM Card c WHERE c.cardExpirationDate <= :expirationDate ORDER BY c.cardExpirationDate, c.cardNumber")
    Page<Card> findByCardExpirationDateLessThanEqual(@Param("expirationDate") String expirationDate, 
                                                   Pageable pageable);

    /**
     * Find cards by embossed name for customer service operations.
     * 
     * Supports customer service inquiries where card lookup
     * is performed by cardholder name rather than card number.
     * 
     * @param embossedName Partial or complete embossed name (PIC X(50) from COBOL)
     * @param pageable Pagination parameters
     * @return Page of cards matching name criteria
     */
    @Query("SELECT c FROM Card c WHERE UPPER(c.cardEmbossedName) LIKE UPPER(CONCAT('%', :embossedName, '%')) ORDER BY c.cardEmbossedName, c.cardNumber")
    Page<Card> findByCardEmbossedNameContainingIgnoreCase(@Param("embossedName") String embossedName, 
                                                        Pageable pageable);

    /**
     * Custom query for card browse operations with flexible filtering.
     * 
     * Replicates the complex browsing logic from COCRDLIC.cbl where
     * multiple filter criteria can be applied during card selection.
     * 
     * Original COBOL logic handles:
     * - Account ID filter (when provided)
     * - Card number filter (when provided)
     * - Status filter (when provided)
     * - Pagination for 7-record screen display
     * 
     * @param accountFilter Optional account ID filter
     * @param cardNumberFilter Optional card number filter
     * @param statusFilter Optional status filter
     * @param pageable Pagination matching 7-record BMS screen
     * @return Page of cards matching all specified criteria
     */
    @Query("SELECT c FROM Card c WHERE " +
           "(:accountFilter IS NULL OR c.accountId = :accountFilter) AND " +
           "(:cardNumberFilter IS NULL OR c.cardNumber LIKE CONCAT(:cardNumberFilter, '%')) AND " +
           "(:statusFilter IS NULL OR c.cardStatus = :statusFilter) " +
           "ORDER BY c.cardNumber")
    Page<Card> findCardsWithFilters(@Param("accountFilter") String accountFilter,
                                  @Param("cardNumberFilter") String cardNumberFilter,
                                  @Param("statusFilter") String statusFilter,
                                  Pageable pageable);

    /**
     * Find all cards for batch processing operations.
     * 
     * Used by Spring Batch jobs for bulk operations such as:
     * - Interest calculation
     * - Statement generation  
     * - Card maintenance
     * - Data export operations
     * 
     * @param pageable Pagination for chunk-oriented batch processing
     * @return Page of all cards ordered by card number
     */
    @Query("SELECT c FROM Card c ORDER BY c.cardNumber")
    Page<Card> findAllCardsForBatch(Pageable pageable);

    /**
     * Find cards by CVV code for security validation operations.
     * 
     * Used during transaction authorization to validate CVV codes
     * while maintaining security for sensitive card data.
     * 
     * Note: This method should be used with appropriate security controls
     * and audit logging for PCI compliance.
     * 
     * @param cardNumber 16-digit card number
     * @param cvvCode 3-digit CVV code (PIC 9(03) from COBOL)
     * @return Optional Card if both card number and CVV match
     */
    @Query("SELECT c FROM Card c WHERE c.cardNumber = :cardNumber AND c.cardCvvCode = :cvvCode")
    Optional<Card> findByCardNumberAndCvvCode(@Param("cardNumber") String cardNumber, 
                                            @Param("cvvCode") String cvvCode);
}