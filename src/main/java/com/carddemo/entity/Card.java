/*
 * Card Entity
 * 
 * Converted from COBOL copybook: app/cpy/CVACT02Y.cpy
 * Original structure: CARD-RECORD (Record Length = 150)
 * 
 * This JPA entity represents card master data and status information transformed from COBOL 
 * CARD-RECORD structure. Features 16-digit card number validation with Luhn algorithm, 
 * optimistic locking through @Version field, and ManyToOne relationship with Account entity. 
 * Implements composite PostgreSQL indexes for efficient card lookup and validation operations.
 * 
 * COBOL Structure Mapping:
 * - CARD-NUM (PIC X(16)) -> cardNumber
 * - CARD-ACCT-ID (PIC 9(11)) -> accountId (Foreign Key to Account entity)
 * - CARD-CVV-CD (PIC 9(03)) -> cardCvvCode
 * - CARD-EMBOSSED-NAME (PIC X(50)) -> cardEmbossedName
 * - CARD-EXPIRAION-DATE (PIC X(10)) -> cardExpirationDate
 * - CARD-ACTIVE-STATUS (PIC X(01)) -> cardActiveStatus
 * - FILLER (PIC X(59)) -> Not mapped (unused space)
 * 
 * Features:
 * - JPA entity mapping to PostgreSQL cards table with composite indexes
 * - Luhn algorithm validator for 16-digit card number validation
 * - Optimistic locking with @Version for concurrent access control
 * - ManyToOne relationship with Account entity maintaining referential integrity
 * - Bean validation annotations for data integrity enforcement
 * - Card lifecycle state management with status validation
 * - Business methods for card validation and processing
 * - CVV code secure handling with appropriate validation constraints
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since CardDemo v1.0-15-g27d6c6f-68
 */

package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * JPA Entity representing card master data converted from COBOL CARD-RECORD structure.
 * 
 * This entity implements comprehensive card management with 16-digit card number validation,
 * CVV code security, and lifecycle state management for enterprise card processing.
 * 
 * Maps to PostgreSQL cards table with composite indexes on:
 * - (card_number) - Primary key access
 * - (account_id, card_active_status) - Account card management queries
 * - (card_active_status) - Status-based card filtering
 * - (card_expiration_date) - Expiration monitoring and processing
 */
@Entity
@Table(name = "cards", 
       indexes = {
           @Index(name = "idx_cards_primary", columnList = "card_number"),
           @Index(name = "idx_cards_account_status", columnList = "account_id, card_active_status"),
           @Index(name = "idx_cards_status", columnList = "card_active_status"),
           @Index(name = "idx_cards_expiration", columnList = "card_expiration_date"),
           @Index(name = "idx_cards_account", columnList = "account_id")
       })
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Cacheable
public class Card implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Date formatter for COBOL date compatibility (YYYY-MM-DD format)
     * Supports COBOL CARD-EXPIRAION-DATE PIC X(10) field conversion
     */
    private static final DateTimeFormatter COBOL_DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Card number - 16-character unique identifier (Primary Key)
     * Converted from COBOL CARD-NUM PIC X(16)
     * 
     * Validation rules:
     * - Must be exactly 16 digits
     * - Cannot be null or blank
     * - Must pass Luhn algorithm validation
     * - Used for direct access and transaction processing
     */
    @Id
    @Column(name = "card_number", length = 16, nullable = false)
    @NotBlank(message = "Card number cannot be blank")
    @Size(min = 16, max = 16, message = "Card number must be exactly 16 digits")
    @Pattern(regexp = "^[0-9]{16}$", 
             message = "Card number must contain only 16 digits")
    private String cardNumber;

    /**
     * Account ID with foreign key relationship to Account entity
     * Converted from COBOL CARD-ACCT-ID PIC 9(11)
     * 
     * Establishes relationship for card-account association and cross-reference navigation
     */
    @Column(name = "account_id", length = 11, nullable = false)
    @NotBlank(message = "Account ID is required")
    @Size(min = 11, max = 11, message = "Account ID must be exactly 11 characters")
    @Pattern(regexp = "^[0-9]{11}$", 
             message = "Account ID must contain only 11 digits")
    private String accountId;

    /**
     * ManyToOne relationship to Account entity for card-account association
     * Enables navigation from card to account for balance and customer information
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", 
                insertable = false, updatable = false)
    @NotNull(message = "Account is required for card")
    private Account account;

    /**
     * Card CVV code - 3-digit security code
     * Converted from COBOL CARD-CVV-CD PIC 9(03)
     * 
     * Security considerations:
     * - 3-digit numeric validation
     * - Secure handling in processing
     * - Required for card validation operations
     */
    @Column(name = "card_cvv_code", length = 3, nullable = false)
    @NotBlank(message = "CVV code is required")
    @Size(min = 3, max = 3, message = "CVV code must be exactly 3 digits")
    @Pattern(regexp = "^[0-9]{3}$", 
             message = "CVV code must contain only 3 digits")
    private String cardCvvCode;

    /**
     * Card embossed name - name printed on the card
     * Converted from COBOL CARD-EMBOSSED-NAME PIC X(50)
     * 
     * Used for card personalization and customer identification
     */
    @Column(name = "card_embossed_name", length = 50, nullable = false)
    @NotBlank(message = "Card embossed name is required")
    @Size(max = 50, message = "Card embossed name cannot exceed 50 characters")
    @Pattern(regexp = "^[A-Z\\s\\-\\.]{1,50}$", 
             message = "Card embossed name must contain only uppercase letters, spaces, hyphens, and periods")
    private String cardEmbossedName;

    /**
     * Card expiration date - expiration date in YYYY-MM-DD format
     * Converted from COBOL CARD-EXPIRAION-DATE PIC X(10)
     * 
     * Maintains COBOL-compatible date format for system integration
     */
    @Column(name = "card_expiration_date", length = 10, nullable = false)
    @NotBlank(message = "Card expiration date is required")
    @Size(min = 10, max = 10, message = "Card expiration date must be exactly 10 characters")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", 
             message = "Card expiration date must be in YYYY-MM-DD format")
    private String cardExpirationDate;

    /**
     * Card active status - single character status indicator
     * Converted from COBOL CARD-ACTIVE-STATUS PIC X(01)
     * 
     * Valid status values:
     * - 'Y' = Active/Enabled
     * - 'N' = Inactive/Disabled
     * - 'B' = Blocked/Suspended
     * - 'E' = Expired
     * - 'C' = Closed/Cancelled
     */
    @Column(name = "card_active_status", length = 1, nullable = false)
    @NotBlank(message = "Card active status is required")
    @Size(min = 1, max = 1, message = "Card active status must be exactly 1 character")
    @Pattern(regexp = "^[YNBEC]$", 
             message = "Card active status must be Y (Active), N (Inactive), B (Blocked), E (Expired), or C (Closed)")
    private String cardActiveStatus;

    /**
     * Version field for optimistic locking per Section 6.2.1.1 database design
     * Implements concurrent access control replacing CICS record-level sharing (RLS)
     * 
     * This field is automatically managed by JPA and incremented on each update operation
     * to prevent concurrent modification conflicts in high-throughput environments.
     */
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    /**
     * Creation timestamp for audit trail
     * Automatically set when card record is first created
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last update timestamp for audit trail
     * Automatically updated whenever card record is modified
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Default constructor required by JPA specification
     * Initializes empty card with default values
     */
    public Card() {
        // Default constructor for JPA
    }

    /**
     * Constructor for creating new card with required fields
     * 
     * @param cardNumber 16-digit unique card identifier
     * @param accountId 11-digit account identifier for card-account association
     * @param cardCvvCode 3-digit CVV security code
     * @param cardEmbossedName name to be embossed on the card
     * @param cardExpirationDate expiration date in YYYY-MM-DD format
     * @param cardActiveStatus single character status indicator
     */
    public Card(String cardNumber, String accountId, String cardCvvCode,
                String cardEmbossedName, String cardExpirationDate, String cardActiveStatus) {
        this.cardNumber = cardNumber;
        this.accountId = accountId;
        this.cardCvvCode = cardCvvCode;
        this.cardEmbossedName = cardEmbossedName;
        this.cardExpirationDate = cardExpirationDate;
        this.cardActiveStatus = cardActiveStatus;
    }

    // Getters and Setters

    /**
     * Gets the card number (Primary Key)
     * 
     * @return 16-digit card identifier
     */
    public String getCardNumber() {
        return cardNumber;
    }

    /**
     * Sets the card number (Primary Key)
     * 
     * @param cardNumber 16-digit card identifier
     */
    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    /**
     * Gets the account ID (Foreign Key)
     * 
     * @return 11-digit account identifier
     */
    public String getAccountId() {
        return accountId;
    }

    /**
     * Sets the account ID (Foreign Key)
     * 
     * @param accountId 11-digit account identifier
     */
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    /**
     * Gets the account entity
     * 
     * @return account associated with this card
     */
    public Account getAccount() {
        return account;
    }

    /**
     * Sets the account entity
     * 
     * @param account account associated with this card
     */
    public void setAccount(Account account) {
        this.account = account;
    }

    /**
     * Gets the CVV code
     * 
     * @return 3-digit CVV security code
     */
    public String getCardCvvCode() {
        return cardCvvCode;
    }

    /**
     * Sets the CVV code
     * 
     * @param cardCvvCode 3-digit CVV security code
     */
    public void setCardCvvCode(String cardCvvCode) {
        this.cardCvvCode = cardCvvCode;
    }

    /**
     * Gets the embossed name
     * 
     * @return name embossed on the card
     */
    public String getCardEmbossedName() {
        return cardEmbossedName;
    }

    /**
     * Sets the embossed name
     * 
     * @param cardEmbossedName name embossed on the card
     */
    public void setCardEmbossedName(String cardEmbossedName) {
        this.cardEmbossedName = cardEmbossedName;
    }

    /**
     * Gets the expiration date
     * 
     * @return expiration date in YYYY-MM-DD format
     */
    public String getCardExpirationDate() {
        return cardExpirationDate;
    }

    /**
     * Sets the expiration date
     * 
     * @param cardExpirationDate expiration date in YYYY-MM-DD format
     */
    public void setCardExpirationDate(String cardExpirationDate) {
        this.cardExpirationDate = cardExpirationDate;
    }

    /**
     * Gets the active status
     * 
     * @return single character status indicator
     */
    public String getCardActiveStatus() {
        return cardActiveStatus;
    }

    /**
     * Sets the active status
     * 
     * @param cardActiveStatus single character status indicator
     */
    public void setCardActiveStatus(String cardActiveStatus) {
        this.cardActiveStatus = cardActiveStatus;
    }

    /**
     * Gets the row version for optimistic locking
     * 
     * @return current version number managed by JPA
     */
    public Integer getRowVersion() {
        return rowVersion;
    }

    /**
     * Sets the row version for optimistic locking
     * Note: This is typically managed automatically by JPA
     * 
     * @param rowVersion version number for optimistic locking
     */
    public void setRowVersion(Integer rowVersion) {
        this.rowVersion = rowVersion;
    }

    /**
     * Gets the creation timestamp
     * 
     * @return when card record was first created
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp
     * Note: This is typically managed automatically by JPA
     * 
     * @param createdAt when card record was first created
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the last update timestamp
     * 
     * @return when card record was last modified
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last update timestamp
     * Note: This is typically managed automatically by JPA
     * 
     * @param updatedAt when card record was last modified
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Business Logic Methods

    /**
     * Business method to validate card number using Luhn algorithm
     * Implements COBOL-style card number validation equivalent to 88-level conditions
     * 
     * The Luhn algorithm validates credit card numbers by:
     * 1. Starting from the rightmost digit, double every second digit
     * 2. If doubling results in a two-digit number, add the digits together
     * 3. Sum all digits
     * 4. If the total sum is divisible by 10, the number is valid
     * 
     * @return true if card number passes Luhn algorithm validation
     */
    public boolean isValidCardNumber() {
        if (cardNumber == null || cardNumber.length() != 16) {
            return false;
        }
        
        try {
            int sum = 0;
            boolean alternate = false;
            
            // Process digits from right to left
            for (int i = cardNumber.length() - 1; i >= 0; i--) {
                int digit = Character.getNumericValue(cardNumber.charAt(i));
                
                if (alternate) {
                    digit *= 2;
                    if (digit > 9) {
                        digit = (digit / 10) + (digit % 10);
                    }
                }
                
                sum += digit;
                alternate = !alternate;
            }
            
            return (sum % 10 == 0);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Business method to check if card is currently active
     * Implements COBOL-style status validation equivalent to 88-level conditions
     * 
     * @return true if card status is 'Y' (Active)
     */
    public boolean isActive() {
        return "Y".equals(cardActiveStatus);
    }

    /**
     * Business method to check if card is inactive
     * Implements COBOL-style status validation equivalent to 88-level conditions
     * 
     * @return true if card status is 'N' (Inactive)
     */
    public boolean isInactive() {
        return "N".equals(cardActiveStatus);
    }

    /**
     * Business method to check if card is blocked/suspended
     * Implements COBOL-style status validation equivalent to 88-level conditions
     * 
     * @return true if card status is 'B' (Blocked)
     */
    public boolean isBlocked() {
        return "B".equals(cardActiveStatus);
    }

    /**
     * Business method to check if card is expired
     * Implements COBOL-style status validation equivalent to 88-level conditions
     * 
     * @return true if card status is 'E' (Expired)
     */
    public boolean isExpired() {
        return "E".equals(cardActiveStatus);
    }

    /**
     * Business method to check if card is closed/cancelled
     * Implements COBOL-style status validation equivalent to 88-level conditions
     * 
     * @return true if card status is 'C' (Closed)
     */
    public boolean isClosed() {
        return "C".equals(cardActiveStatus);
    }

    /**
     * Business method to check if card can be used for transactions
     * Implements business logic for transaction authorization
     * 
     * @return true if card is active and not expired by date
     */
    public boolean isUsableForTransactions() {
        return isActive() && !isExpiredByDate();
    }

    /**
     * Business method to check if card expiration date has passed
     * Validates expiration date against current date
     * 
     * @return true if card has expired based on expiration date
     */
    public boolean isExpiredByDate() {
        if (cardExpirationDate == null || cardExpirationDate.trim().isEmpty()) {
            return false;
        }
        
        try {
            // Parse YYYY-MM-DD date and compare with current year-month
            YearMonth expirationYearMonth = YearMonth.parse(cardExpirationDate.substring(0, 7));
            YearMonth currentYearMonth = YearMonth.now();
            
            return expirationYearMonth.isBefore(currentYearMonth);
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            // If date parsing fails, consider card expired for safety
            return true;
        }
    }

    /**
     * Business method to get card type based on card number prefix
     * Implements industry-standard card type identification
     * 
     * @return card type string based on card number prefix
     */
    public String getCardType() {
        if (cardNumber == null || cardNumber.length() < 2) {
            return "UNKNOWN";
        }
        
        String prefix = cardNumber.substring(0, 2);
        int firstDigit = Character.getNumericValue(cardNumber.charAt(0));
        
        // Visa: starts with 4
        if (firstDigit == 4) {
            return "VISA";
        }
        
        // Mastercard: starts with 51-55 or 2221-2720
        if (prefix.equals("51") || prefix.equals("52") || prefix.equals("53") || 
            prefix.equals("54") || prefix.equals("55")) {
            return "MASTERCARD";
        }
        
        // American Express: starts with 34 or 37
        if (prefix.equals("34") || prefix.equals("37")) {
            return "AMEX";
        }
        
        // Discover: starts with 6011, 622126-622925, 644-649, 65
        if (prefix.equals("60") || prefix.equals("62") || prefix.equals("64") || 
            prefix.equals("65")) {
            return "DISCOVER";
        }
        
        return "OTHER";
    }

    /**
     * Business method to get masked card number for display
     * Shows only last 4 digits with asterisks for security
     * 
     * @return masked card number in format ************1234
     */
    public String getMaskedCardNumber() {
        if (cardNumber == null || cardNumber.length() != 16) {
            return "****************";
        }
        
        return "************" + cardNumber.substring(12);
    }

    /**
     * Business method to format expiration date for display
     * Converts YYYY-MM-DD to MM/YY format commonly used on cards
     * 
     * @return formatted expiration date in MM/YY format
     */
    public String getFormattedExpirationDate() {
        if (cardExpirationDate == null || cardExpirationDate.length() != 10) {
            return "MM/YY";
        }
        
        try {
            String year = cardExpirationDate.substring(2, 4);  // Get YY from YYYY
            String month = cardExpirationDate.substring(5, 7); // Get MM
            return month + "/" + year;
        } catch (StringIndexOutOfBoundsException e) {
            return "MM/YY";
        }
    }

    /**
     * Business method to get card status description
     * Provides human-readable status descriptions for display
     * 
     * @return descriptive text for card status
     */
    public String getCardStatusDescription() {
        if (cardActiveStatus == null) {
            return "Unknown";
        }
        
        switch (cardActiveStatus) {
            case "Y":
                return "Active";
            case "N":
                return "Inactive";
            case "B":
                return "Blocked";
            case "E":
                return "Expired";
            case "C":
                return "Closed";
            default:
                return "Unknown";
        }
    }

    /**
     * Business validation method to ensure card data integrity
     * Implements comprehensive validation equivalent to COBOL 88-level conditions
     * 
     * @return true if card data is valid for processing
     */
    public boolean isValid() {
        return cardNumber != null && 
               cardNumber.length() == 16 &&
               isValidCardNumber() &&
               accountId != null && 
               accountId.length() == 11 &&
               cardCvvCode != null && 
               cardCvvCode.length() == 3 &&
               cardEmbossedName != null && 
               !cardEmbossedName.trim().isEmpty() &&
               cardExpirationDate != null && 
               cardExpirationDate.length() == 10 &&
               cardActiveStatus != null && 
               cardActiveStatus.matches("^[YNBEC]$");
    }

    /**
     * Business method to check if card requires immediate attention
     * Implements business rules for card management alerting
     * 
     * @return true if card needs manual review or action
     */
    public boolean requiresAttention() {
        // Card is blocked or closed
        if (isBlocked() || isClosed()) {
            return true;
        }
        
        // Card has expired by date but status hasn't been updated
        if (isExpiredByDate() && !isExpired()) {
            return true;
        }
        
        // Invalid card number
        if (!isValidCardNumber()) {
            return true;
        }
        
        // CVV code is missing or invalid
        if (cardCvvCode == null || !cardCvvCode.matches("^[0-9]{3}$")) {
            return true;
        }
        
        return false;
    }

    /**
     * Business method to activate the card
     * Changes card status to active if conditions are met
     * 
     * @return true if card was successfully activated
     */
    public boolean activate() {
        if (isClosed() || isExpiredByDate()) {
            return false; // Cannot activate closed or expired cards
        }
        
        if (isValid()) {
            cardActiveStatus = "Y";
            return true;
        }
        
        return false;
    }

    /**
     * Business method to deactivate the card
     * Changes card status to inactive
     * 
     * @return true if card was successfully deactivated
     */
    public boolean deactivate() {
        if (!isClosed()) {
            cardActiveStatus = "N";
            return true;
        }
        
        return false; // Cannot deactivate closed cards
    }

    /**
     * Business method to block the card
     * Changes card status to blocked for security purposes
     * 
     * @return true if card was successfully blocked
     */
    public boolean block() {
        if (!isClosed()) {
            cardActiveStatus = "B";
            return true;
        }
        
        return false; // Cannot block closed cards
    }

    /**
     * Business method to close the card permanently
     * Changes card status to closed (irreversible)
     * 
     * @return true if card was successfully closed
     */
    public boolean close() {
        cardActiveStatus = "C";
        return true;
    }

    /**
     * Creates a formatted display string for card summary
     * Useful for logging, debugging, and customer service displays
     * 
     * @return formatted card summary string
     */
    public String getCardSummary() {
        return String.format("Card %s: %s %s, Status: %s, Account: %s", 
                getMaskedCardNumber(),
                getCardType(),
                cardEmbossedName != null ? cardEmbossedName : "Unknown",
                getCardStatusDescription(),
                accountId != null ? accountId : "Unknown");
    }

    /**
     * Converts date to COBOL-compatible string format
     * Maintains compatibility with legacy system interfaces
     * 
     * @param date the LocalDateTime to format
     * @return COBOL-compatible date string
     */
    public static String formatDateForCobol(LocalDateTime date) {
        if (date == null) {
            return "";
        }
        return date.format(COBOL_DATE_FORMATTER);
    }

    /**
     * Parses COBOL-compatible date string to LocalDateTime
     * Enables migration from legacy date formats
     * 
     * @param cobolDate COBOL date string in YYYY-MM-DD format
     * @return parsed LocalDateTime at start of day
     */
    public static LocalDateTime parseCobolDate(String cobolDate) {
        if (cobolDate == null || cobolDate.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(cobolDate + "T00:00:00");
        } catch (DateTimeParseException e) {
            // Handle parsing errors gracefully
            return null;
        }
    }

    /**
     * Equals method based on business key (card_number) for entity comparison
     * 
     * @param obj object to compare with
     * @return true if objects are equal based on card number
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Card that = (Card) obj;
        return Objects.equals(cardNumber, that.cardNumber);
    }

    /**
     * Hash code method based on business key (card_number) for collection operations
     * 
     * @return hash code for this card
     */
    @Override
    public int hashCode() {
        return Objects.hash(cardNumber);
    }

    /**
     * String representation for debugging and logging purposes
     * 
     * @return formatted string representation of card (with masked card number for security)
     */
    @Override
    public String toString() {
        return String.format(
            "Card{cardNumber='%s', accountId='%s', embossedName='%s', " +
            "expirationDate='%s', status='%s', cardType='%s', rowVersion=%d}",
            getMaskedCardNumber(), 
            accountId,
            cardEmbossedName,
            getFormattedExpirationDate(),
            getCardStatusDescription(),
            getCardType(),
            rowVersion
        );
    }
}