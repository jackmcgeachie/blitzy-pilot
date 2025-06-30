package com.carddemo.card;

import com.carddemo.account.Account;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA Entity representing the Card table in PostgreSQL database.
 * 
 * This entity maps the COBOL CARD-RECORD structure from CVACT02Y.cpy copybook
 * to PostgreSQL table with comprehensive field validation, optimistic locking,
 * and entity relationships while preserving exact data precision and business 
 * rules from the original COBOL record structures.
 * 
 * Original COBOL Structure:
 * 01  CARD-RECORD.
 *     05  CARD-NUM                          PIC X(16).
 *     05  CARD-ACCT-ID                      PIC 9(11).
 *     05  CARD-CVV-CD                       PIC 9(03).
 *     05  CARD-EMBOSSED-NAME                PIC X(50).
 *     05  CARD-EXPIRAION-DATE               PIC X(10).
 *     05  CARD-ACTIVE-STATUS                PIC X(01).
 *     05  FILLER                            PIC X(59).
 * 
 * PostgreSQL Table Mapping:
 * - Primary Key: card_number (VARCHAR(16))
 * - Foreign Key: account_id references accounts(account_id)
 * - Optimistic Locking: row_version (INTEGER)
 * - Audit Fields: created_at, updated_at (TIMESTAMP)
 * 
 * @author CardDemo Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Entity
@Table(name = "cards", indexes = {
    @Index(name = "idx_cards_account_id", columnList = "account_id"),
    @Index(name = "idx_cards_status", columnList = "card_status"),
    @Index(name = "idx_cards_expiry_date", columnList = "card_expiry_date"),
    @Index(name = "idx_cards_account_status", columnList = "account_id, card_status")
})
public class Card {

    /**
     * Primary key: 16-digit card number from COBOL CARD-NUM PIC X(16)
     * 
     * Business Rules:
     * - Must be exactly 16 digits
     * - Must follow Luhn algorithm validation
     * - Maps to PostgreSQL VARCHAR(16)
     */
    @Id
    @Column(name = "card_number", length = 16, nullable = false)
    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "^[0-9]{16}$", message = "Card number must be exactly 16 digits")
    private String cardNumber;

    /**
     * Foreign key to Account entity from COBOL CARD-ACCT-ID PIC 9(11)
     * 
     * Business Rules:
     * - Must reference valid account_id in accounts table
     * - 11-digit numeric account identifier
     * - Required field, cannot be null
     */
    @Column(name = "account_id", length = 11, nullable = false)
    @NotBlank(message = "Account ID is required")
    @Pattern(regexp = "^[0-9]{11}$", message = "Account ID must be exactly 11 digits")
    private String accountId;

    /**
     * Card CVV security code from COBOL CARD-CVV-CD PIC 9(03)
     * 
     * Business Rules:
     * - Must be exactly 3 digits
     * - Sensitive security information
     * - Required for card validation
     */
    @Column(name = "card_cvv_code", length = 3, nullable = false)
    @NotBlank(message = "CVV code is required")
    @Pattern(regexp = "^[0-9]{3}$", message = "CVV code must be exactly 3 digits")
    private String cardCvvCode;

    /**
     * Embossed name on card from COBOL CARD-EMBOSSED-NAME PIC X(50)
     * 
     * Business Rules:
     * - Maximum 50 characters
     * - Must contain valid name characters
     * - Required field for card personalization
     */
    @Column(name = "card_embossed_name", length = 50, nullable = false)
    @NotBlank(message = "Embossed name is required")
    @Size(max = 50, message = "Embossed name cannot exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z\\s\\-\\.]{1,50}$", message = "Embossed name must contain only letters, spaces, hyphens, and periods")
    private String cardEmbossedName;

    /**
     * Card expiration date from COBOL CARD-EXPIRAION-DATE PIC X(10)
     * 
     * Business Rules:
     * - Date format validation
     * - Must be future date for active cards
     * - Required for transaction authorization
     */
    @Column(name = "card_expiry_date", nullable = false)
    @NotNull(message = "Card expiry date is required")
    @Future(message = "Card expiry date must be in the future")
    private LocalDate cardExpiryDate;

    /**
     * Card status from COBOL CARD-ACTIVE-STATUS PIC X(01)
     * 
     * Business Rules:
     * - A = Active
     * - I = Inactive  
     * - S = Suspended
     * - C = Cancelled
     * - E = Expired
     * 
     * Maps to PostgreSQL VARCHAR(1) with constraint check
     */
    @Column(name = "card_status", length = 1, nullable = false)
    @NotBlank(message = "Card status is required")
    @Pattern(regexp = "^[AISCE]$", message = "Card status must be A (Active), I (Inactive), S (Suspended), C (Cancelled), or E (Expired)")
    private String cardStatus;

    /**
     * Card type designation for business categorization
     * 
     * Business Rules:
     * - STANDARD = Standard credit card
     * - PREMIUM = Premium credit card with enhanced benefits
     * - CORPORATE = Corporate business card
     * - DEBIT = Debit card linked to account
     * - PREPAID = Prepaid card with loaded balance
     */
    @Column(name = "card_type", length = 10, nullable = false)
    @NotBlank(message = "Card type is required")
    @Pattern(regexp = "^(STANDARD|PREMIUM|CORPORATE|DEBIT|PREPAID)$", 
             message = "Card type must be STANDARD, PREMIUM, CORPORATE, DEBIT, or PREPAID")
    private String cardType = "STANDARD";

    /**
     * Optimistic locking version field for concurrent access control
     * 
     * Replaces CICS record-level sharing (RLS) with JPA optimistic locking.
     * Automatically managed by JPA framework for concurrent modification detection.
     */
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion = 0;

    /**
     * Audit field: Record creation timestamp
     * 
     * Automatically populated when entity is first persisted.
     * Provides audit trail for compliance and troubleshooting.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Audit field: Record last update timestamp
     * 
     * Automatically updated when entity is modified.
     * Provides audit trail for compliance and troubleshooting.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Bidirectional relationship with Account entity
     * 
     * Maps foreign key account_id to Account entity through JPA association.
     * Enables navigation from Card to Account for business operations.
     * Uses FetchType.LAZY for performance optimization.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", 
                insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_cards_account"))
    private Account account;

    /**
     * Default constructor required by JPA specification
     */
    public Card() {
        // Default constructor for JPA
    }

    /**
     * Constructor for creating new Card entity with required fields
     * 
     * @param cardNumber 16-digit card number
     * @param accountId 11-digit account identifier
     * @param cardCvvCode 3-digit CVV security code
     * @param cardEmbossedName Name embossed on card (max 50 chars)
     * @param cardExpiryDate Card expiration date
     * @param cardStatus Card status (A/I/S/C/E)
     */
    public Card(String cardNumber, String accountId, String cardCvvCode, 
                String cardEmbossedName, LocalDate cardExpiryDate, String cardStatus) {
        this.cardNumber = cardNumber;
        this.accountId = accountId;
        this.cardCvvCode = cardCvvCode;
        this.cardEmbossedName = cardEmbossedName;
        this.cardExpiryDate = cardExpiryDate;
        this.cardStatus = cardStatus;
    }

    /**
     * Constructor for creating new Card entity with card type
     * 
     * @param cardNumber 16-digit card number
     * @param accountId 11-digit account identifier
     * @param cardCvvCode 3-digit CVV security code
     * @param cardEmbossedName Name embossed on card (max 50 chars)
     * @param cardExpiryDate Card expiration date
     * @param cardStatus Card status (A/I/S/C/E)
     * @param cardType Card type (STANDARD/PREMIUM/CORPORATE/DEBIT/PREPAID)
     */
    public Card(String cardNumber, String accountId, String cardCvvCode, 
                String cardEmbossedName, LocalDate cardExpiryDate, String cardStatus, String cardType) {
        this(cardNumber, accountId, cardCvvCode, cardEmbossedName, cardExpiryDate, cardStatus);
        this.cardType = cardType;
    }

    // Getters and Setters with validation

    /**
     * Gets the 16-digit card number
     * 
     * @return card number as string
     */
    public String getCardNumber() {
        return cardNumber;
    }

    /**
     * Sets the 16-digit card number
     * 
     * @param cardNumber 16-digit card number
     */
    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    /**
     * Gets the 11-digit account identifier
     * 
     * @return account ID as string
     */
    public String getAccountId() {
        return accountId;
    }

    /**
     * Sets the 11-digit account identifier
     * 
     * @param accountId 11-digit account ID
     */
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    /**
     * Gets the 3-digit CVV security code
     * 
     * @return CVV code as string
     */
    public String getCardCvvCode() {
        return cardCvvCode;
    }

    /**
     * Sets the 3-digit CVV security code
     * 
     * @param cardCvvCode 3-digit CVV code
     */
    public void setCardCvvCode(String cardCvvCode) {
        this.cardCvvCode = cardCvvCode;
    }

    /**
     * Gets the embossed name on the card
     * 
     * @return embossed name as string
     */
    public String getCardEmbossedName() {
        return cardEmbossedName;
    }

    /**
     * Sets the embossed name on the card
     * 
     * @param cardEmbossedName name to emboss on card (max 50 chars)
     */
    public void setCardEmbossedName(String cardEmbossedName) {
        this.cardEmbossedName = cardEmbossedName;
    }

    /**
     * Gets the card expiration date
     * 
     * @return expiry date as LocalDate
     */
    public LocalDate getCardExpiryDate() {
        return cardExpiryDate;
    }

    /**
     * Sets the card expiration date
     * 
     * @param cardExpiryDate future expiry date
     */
    public void setCardExpiryDate(LocalDate cardExpiryDate) {
        this.cardExpiryDate = cardExpiryDate;
    }

    /**
     * Gets the card status
     * 
     * @return card status as single character string
     */
    public String getCardStatus() {
        return cardStatus;
    }

    /**
     * Sets the card status
     * 
     * @param cardStatus A/I/S/C/E for Active/Inactive/Suspended/Cancelled/Expired
     */
    public void setCardStatus(String cardStatus) {
        this.cardStatus = cardStatus;
    }

    /**
     * Gets the card type
     * 
     * @return card type as string
     */
    public String getCardType() {
        return cardType;
    }

    /**
     * Sets the card type
     * 
     * @param cardType STANDARD/PREMIUM/CORPORATE/DEBIT/PREPAID
     */
    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    /**
     * Gets the optimistic locking version
     * 
     * @return row version number
     */
    public Integer getRowVersion() {
        return rowVersion;
    }

    /**
     * Sets the optimistic locking version
     * 
     * Note: This should typically only be used by JPA framework
     * 
     * @param rowVersion version number
     */
    public void setRowVersion(Integer rowVersion) {
        this.rowVersion = rowVersion;
    }

    /**
     * Gets the record creation timestamp
     * 
     * @return creation timestamp
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the record last update timestamp
     * 
     * @return last update timestamp
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Gets the associated Account entity
     * 
     * @return Account entity or null if not loaded
     */
    public Account getAccount() {
        return account;
    }

    /**
     * Sets the associated Account entity
     * 
     * @param account Account entity to associate
     */
    public void setAccount(Account account) {
        this.account = account;
    }

    // Business Logic Helper Methods

    /**
     * Checks if the card is currently active
     * 
     * Replicates COBOL 88-level condition logic for status validation
     * 
     * @return true if card status is 'A' (Active)
     */
    public boolean isActive() {
        return "A".equals(cardStatus);
    }

    /**
     * Checks if the card is inactive
     * 
     * @return true if card status is 'I' (Inactive)
     */
    public boolean isInactive() {
        return "I".equals(cardStatus);
    }

    /**
     * Checks if the card is suspended
     * 
     * @return true if card status is 'S' (Suspended)
     */
    public boolean isSuspended() {
        return "S".equals(cardStatus);
    }

    /**
     * Checks if the card is cancelled
     * 
     * @return true if card status is 'C' (Cancelled)
     */
    public boolean isCancelled() {
        return "C".equals(cardStatus);
    }

    /**
     * Checks if the card is expired by status
     * 
     * @return true if card status is 'E' (Expired)
     */
    public boolean isExpiredByStatus() {
        return "E".equals(cardStatus);
    }

    /**
     * Checks if the card is expired by date
     * 
     * @return true if current date is after expiry date
     */
    public boolean isExpiredByDate() {
        return cardExpiryDate != null && LocalDate.now().isAfter(cardExpiryDate);
    }

    /**
     * Checks if the card can be used for transactions
     * 
     * Business rule: Card must be active and not expired
     * 
     * @return true if card is active and not expired
     */
    public boolean isUsableForTransactions() {
        return isActive() && !isExpiredByDate();
    }

    /**
     * Activates the card by setting status to 'A'
     * 
     * Business operation matching COBOL field assignment
     */
    public void activate() {
        this.cardStatus = "A";
    }

    /**
     * Suspends the card by setting status to 'S'
     * 
     * Business operation for security or administrative purposes
     */
    public void suspend() {
        this.cardStatus = "S";
    }

    /**
     * Cancels the card by setting status to 'C'
     * 
     * Business operation for permanent card termination
     */
    public void cancel() {
        this.cardStatus = "C";
    }

    /**
     * Marks the card as expired by setting status to 'E'
     * 
     * Business operation for expired cards
     */
    public void markExpired() {
        this.cardStatus = "E";
    }

    // Object lifecycle methods

    /**
     * Validates business rules before persistence
     * 
     * Called automatically by JPA before INSERT/UPDATE operations
     */
    @PrePersist
    @PreUpdate
    private void validateBusinessRules() {
        // Validate Luhn algorithm for card number
        if (cardNumber != null && !isValidLuhnNumber(cardNumber)) {
            throw new IllegalArgumentException("Card number fails Luhn algorithm validation");
        }
        
        // Ensure expiry date is in the future for new active cards
        if ("A".equals(cardStatus) && cardExpiryDate != null && cardExpiryDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Active cards cannot have expiry date in the past");
        }
    }

    /**
     * Validates card number using Luhn algorithm
     * 
     * Implements standard credit card validation algorithm
     * 
     * @param cardNumber 16-digit card number
     * @return true if card number is valid per Luhn algorithm
     */
    private boolean isValidLuhnNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() != 16) {
            return false;
        }
        
        int sum = 0;
        boolean alternate = false;
        
        // Process digits from right to left
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return (sum % 10 == 0);
    }

    // Standard Object methods

    /**
     * Equality based on card number (primary key)
     * 
     * @param obj object to compare
     * @return true if objects are equal
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Card card = (Card) obj;
        return Objects.equals(cardNumber, card.cardNumber);
    }

    /**
     * Hash code based on card number
     * 
     * @return hash code value
     */
    @Override
    public int hashCode() {
        return Objects.hash(cardNumber);
    }

    /**
     * String representation of Card entity
     * 
     * Note: Does not include sensitive CVV code for security
     * 
     * @return string representation
     */
    @Override
    public String toString() {
        return "Card{" +
                "cardNumber='" + (cardNumber != null ? cardNumber.substring(0, 4) + "********" + cardNumber.substring(12) : null) + '\'' +
                ", accountId='" + accountId + '\'' +
                ", cardEmbossedName='" + cardEmbossedName + '\'' +
                ", cardExpiryDate=" + cardExpiryDate +
                ", cardStatus='" + cardStatus + '\'' +
                ", cardType='" + cardType + '\'' +
                ", rowVersion=" + rowVersion +
                '}';
    }
}