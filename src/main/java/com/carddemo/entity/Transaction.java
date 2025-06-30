/*
 * Transaction Entity
 * 
 * Converted from COBOL copybook: app/cpy/CVTRA05Y.cpy
 * Original structure: TRAN-RECORD (Record Length = 350)
 * 
 * This JPA entity represents financial transaction history transformed from COBOL 
 * TRAN-RECORD structure. Features BigDecimal transaction amounts for precise 
 * financial calculations, LocalDateTime fields for timestamp management, and 
 * foreign key relationships to Card, TransactionType, and TransactionCategory entities.
 * 
 * COBOL Structure Mapping:
 * - TRAN-ID (PIC X(16)) -> transactionId
 * - TRAN-TYPE-CD (PIC X(02)) -> transactionType (ManyToOne relationship)
 * - TRAN-CAT-CD (PIC 9(04)) -> transactionCategory (ManyToOne relationship)  
 * - TRAN-SOURCE (PIC X(10)) -> transactionSource
 * - TRAN-DESC (PIC X(100)) -> transactionDescription
 * - TRAN-AMT (PIC S9(09)V99) -> transactionAmount (BigDecimal with scale 2)
 * - TRAN-MERCHANT-ID (PIC 9(09)) -> merchantId
 * - TRAN-MERCHANT-NAME (PIC X(50)) -> merchantName
 * - TRAN-MERCHANT-CITY (PIC X(50)) -> merchantCity
 * - TRAN-MERCHANT-ZIP (PIC X(10)) -> merchantZip
 * - TRAN-CARD-NUM (PIC X(16)) -> card (ManyToOne relationship)
 * - TRAN-ORIG-TS (PIC X(26)) -> originalTimestamp
 * - TRAN-PROC-TS (PIC X(26)) -> processedTimestamp
 * 
 * Features:
 * - JPA entity mapping to PostgreSQL transactions table
 * - Composite indexes for high-performance transaction processing
 * - Optimistic locking with @Version for concurrent access control
 * - BigDecimal precision matching COBOL COMP-3 monetary calculations
 * - Foreign key relationships maintaining referential integrity
 * - Bean validation annotations for data integrity enforcement
 * - Business methods for transaction classification and processing
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * JPA Entity representing financial transaction history converted from COBOL TRAN-RECORD structure.
 * 
 * This entity implements comprehensive transaction management with precise monetary calculations,
 * timestamp tracking, and foreign key relationships for enterprise transaction processing.
 * 
 * Maps to PostgreSQL transactions table with composite indexes on:
 * - (transaction_id) - Primary key access
 * - (card_number, transaction_timestamp) - Card transaction history queries
 * - (transaction_timestamp) - Time-based transaction processing
 * - (merchant_id) - Merchant transaction analysis
 * - (transaction_type_cd, transaction_cat_cd) - Transaction categorization queries
 */
@Entity
@Table(name = "transactions", 
       indexes = {
           @Index(name = "idx_transactions_primary", columnList = "transaction_id"),
           @Index(name = "idx_transactions_card_timestamp", columnList = "card_number, original_timestamp"),
           @Index(name = "idx_transactions_timestamp", columnList = "original_timestamp"),
           @Index(name = "idx_transactions_merchant", columnList = "merchant_id"),
           @Index(name = "idx_transactions_type_category", columnList = "transaction_type_cd, transaction_cat_cd"),
           @Index(name = "idx_transactions_amount", columnList = "transaction_amount"),
           @Index(name = "idx_transactions_processed", columnList = "processed_timestamp")
       })
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Cacheable
public class Transaction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Date/time formatter for COBOL timestamp compatibility
     * Supports YYYY-MM-DD HH:MM:SS.ssssss format from COBOL TRAN-ORIG-TS/TRAN-PROC-TS
     */
    private static final DateTimeFormatter COBOL_TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    /**
     * Transaction ID - 16-character unique identifier (Primary Key)
     * Converted from COBOL TRAN-ID PIC X(16)
     * 
     * Validation rules:
     * - Must be exactly 16 characters
     * - Cannot be null or blank
     * - Used for direct access and cross-reference lookups
     */
    @Id
    @Column(name = "transaction_id", length = 16, nullable = false)
    @NotBlank(message = "Transaction ID cannot be blank")
    @Size(min = 16, max = 16, message = "Transaction ID must be exactly 16 characters")
    @Pattern(regexp = "^[A-Z0-9]{16}$", 
             message = "Transaction ID must contain only uppercase letters and digits")
    private String transactionId;

    /**
     * Transaction type code with foreign key relationship to TransactionType entity
     * Converted from COBOL TRAN-TYPE-CD PIC X(02)
     * 
     * Establishes relationship for transaction type classification and validation
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_type_cd", referencedColumnName = "type_code", nullable = false)
    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;

    /**
     * Transaction category with foreign key relationship to TransactionCategory entity
     * Converted from COBOL TRAN-CAT-CD PIC 9(04)
     * 
     * Links to composite key in TransactionCategory for transaction classification
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "transaction_type_cd", referencedColumnName = "type_code", insertable = false, updatable = false),
        @JoinColumn(name = "transaction_cat_cd", referencedColumnName = "category_code", nullable = false)
    })
    @NotNull(message = "Transaction category is required")
    private TransactionCategory transactionCategory;

    /**
     * Transaction source identifier - system or channel that initiated the transaction
     * Converted from COBOL TRAN-SOURCE PIC X(10)
     * 
     * Examples: "ATM", "ONLINE", "POS", "BATCH", "PHONE"
     */
    @Column(name = "transaction_source", length = 10, nullable = false)
    @NotBlank(message = "Transaction source cannot be blank")
    @Size(max = 10, message = "Transaction source cannot exceed 10 characters")
    private String transactionSource;

    /**
     * Transaction description - human-readable description of the transaction
     * Converted from COBOL TRAN-DESC PIC X(100)
     * 
     * Provides detailed information about the transaction for reporting and customer service
     */
    @Column(name = "transaction_description", length = 100, nullable = false)
    @NotBlank(message = "Transaction description cannot be blank")
    @Size(max = 100, message = "Transaction description cannot exceed 100 characters")
    private String transactionDescription;

    /**
     * Transaction amount with exact COBOL COMP-3 precision
     * Converted from COBOL TRAN-AMT PIC S9(09)V99
     * 
     * Uses BigDecimal with scale 2 for precise monetary calculations
     * Supports signed amounts for debits (negative) and credits (positive)
     * Maximum precision: 9 digits before decimal + 2 decimal places
     */
    @Column(name = "transaction_amount", precision = 11, scale = 2, nullable = false)
    @NotNull(message = "Transaction amount is required")
    @DecimalMin(value = "-999999999.99", message = "Transaction amount cannot be less than -999,999,999.99")
    @DecimalMax(value = "999999999.99", message = "Transaction amount cannot exceed 999,999,999.99")
    @Digits(integer = 9, fraction = 2, message = "Transaction amount must have at most 9 digits before decimal and 2 decimal places")
    private BigDecimal transactionAmount;

    /**
     * Merchant identifier - unique ID for the merchant where transaction occurred
     * Converted from COBOL TRAN-MERCHANT-ID PIC 9(09)
     * 
     * Links to merchant master data for transaction processing and reporting
     */
    @Column(name = "merchant_id", nullable = true)
    @Min(value = 1, message = "Merchant ID must be a positive number")
    @Max(value = 999999999L, message = "Merchant ID cannot exceed 999,999,999")
    private Long merchantId;

    /**
     * Merchant name - business name where transaction occurred
     * Converted from COBOL TRAN-MERCHANT-NAME PIC X(50)
     * 
     * Displays on customer statements and transaction history reports
     */
    @Column(name = "merchant_name", length = 50, nullable = true)
    @Size(max = 50, message = "Merchant name cannot exceed 50 characters")
    private String merchantName;

    /**
     * Merchant city - location where transaction occurred
     * Converted from COBOL TRAN-MERCHANT-CITY PIC X(50)
     * 
     * Provides geographic context for transaction analysis and fraud detection
     */
    @Column(name = "merchant_city", length = 50, nullable = true)
    @Size(max = 50, message = "Merchant city cannot exceed 50 characters")
    private String merchantCity;

    /**
     * Merchant postal code - zip code where transaction occurred
     * Converted from COBOL TRAN-MERCHANT-ZIP PIC X(10)
     * 
     * Supports both US 5-digit and international postal code formats
     */
    @Column(name = "merchant_zip", length = 10, nullable = true)
    @Size(max = 10, message = "Merchant zip code cannot exceed 10 characters")
    @Pattern(regexp = "^[A-Z0-9\\s-]*$", message = "Merchant zip code must contain only letters, digits, spaces, and hyphens")
    private String merchantZip;

    /**
     * Card number with foreign key relationship to Card entity
     * Converted from COBOL TRAN-CARD-NUM PIC X(16)
     * 
     * Links transaction to the card used for the transaction
     * Supports cross-reference navigation for card transaction history
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_number", referencedColumnName = "card_number", nullable = false)
    @NotNull(message = "Card is required for transaction")
    private Card card;

    /**
     * Original timestamp - when transaction was originally initiated
     * Converted from COBOL TRAN-ORIG-TS PIC X(26)
     * 
     * Preserves the exact time when customer initiated the transaction
     * Used for transaction ordering and duplicate detection
     */
    @Column(name = "original_timestamp", nullable = false)
    @NotNull(message = "Original timestamp is required")
    private LocalDateTime originalTimestamp;

    /**
     * Processed timestamp - when transaction was processed by the system
     * Converted from COBOL TRAN-PROC-TS PIC X(26)
     * 
     * Records when the transaction was actually processed and posted
     * Used for batch processing reconciliation and audit trails
     */
    @Column(name = "processed_timestamp", nullable = true)
    private LocalDateTime processedTimestamp;

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
     * Automatically set when transaction record is first created
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last update timestamp for audit trail
     * Automatically updated whenever transaction record is modified
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Default constructor required by JPA specification
     * Initializes empty transaction with default values
     */
    public Transaction() {
        // Default constructor for JPA
    }

    /**
     * Constructor for creating new transaction with required fields
     * 
     * @param transactionId 16-character unique transaction identifier
     * @param transactionType transaction type entity reference
     * @param transactionCategory transaction category entity reference
     * @param transactionSource source system or channel
     * @param transactionDescription human-readable description
     * @param transactionAmount monetary amount with precise decimal handling
     * @param card card entity reference
     * @param originalTimestamp when transaction was initiated
     */
    public Transaction(String transactionId, TransactionType transactionType, 
                      TransactionCategory transactionCategory, String transactionSource,
                      String transactionDescription, BigDecimal transactionAmount,
                      Card card, LocalDateTime originalTimestamp) {
        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.transactionCategory = transactionCategory;
        this.transactionSource = transactionSource;
        this.transactionDescription = transactionDescription;
        this.transactionAmount = transactionAmount;
        this.card = card;
        this.originalTimestamp = originalTimestamp;
    }

    // Getters and Setters

    /**
     * Gets the transaction ID (Primary Key)
     * 
     * @return 16-character transaction identifier
     */
    public String getTransactionId() {
        return transactionId;
    }

    /**
     * Sets the transaction ID (Primary Key)
     * 
     * @param transactionId 16-character transaction identifier
     */
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * Gets the transaction type entity
     * 
     * @return transaction type with classification details
     */
    public TransactionType getTransactionType() {
        return transactionType;
    }

    /**
     * Sets the transaction type entity
     * 
     * @param transactionType transaction type with classification details
     */
    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    /**
     * Gets the transaction category entity
     * 
     * @return transaction category with detailed classification
     */
    public TransactionCategory getTransactionCategory() {
        return transactionCategory;
    }

    /**
     * Sets the transaction category entity
     * 
     * @param transactionCategory transaction category with detailed classification
     */
    public void setTransactionCategory(TransactionCategory transactionCategory) {
        this.transactionCategory = transactionCategory;
    }

    /**
     * Gets the transaction source
     * 
     * @return source system or channel identifier
     */
    public String getTransactionSource() {
        return transactionSource;
    }

    /**
     * Sets the transaction source
     * 
     * @param transactionSource source system or channel identifier
     */
    public void setTransactionSource(String transactionSource) {
        this.transactionSource = transactionSource;
    }

    /**
     * Gets the transaction description
     * 
     * @return human-readable transaction description
     */
    public String getTransactionDescription() {
        return transactionDescription;
    }

    /**
     * Sets the transaction description
     * 
     * @param transactionDescription human-readable transaction description
     */
    public void setTransactionDescription(String transactionDescription) {
        this.transactionDescription = transactionDescription;
    }

    /**
     * Gets the transaction amount
     * 
     * @return precise monetary amount with BigDecimal precision
     */
    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    /**
     * Sets the transaction amount
     * 
     * @param transactionAmount precise monetary amount with BigDecimal precision
     */
    public void setTransactionAmount(BigDecimal transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    /**
     * Gets the merchant ID
     * 
     * @return unique merchant identifier
     */
    public Long getMerchantId() {
        return merchantId;
    }

    /**
     * Sets the merchant ID
     * 
     * @param merchantId unique merchant identifier
     */
    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    /**
     * Gets the merchant name
     * 
     * @return business name where transaction occurred
     */
    public String getMerchantName() {
        return merchantName;
    }

    /**
     * Sets the merchant name
     * 
     * @param merchantName business name where transaction occurred
     */
    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    /**
     * Gets the merchant city
     * 
     * @return city where transaction occurred
     */
    public String getMerchantCity() {
        return merchantCity;
    }

    /**
     * Sets the merchant city
     * 
     * @param merchantCity city where transaction occurred
     */
    public void setMerchantCity(String merchantCity) {
        this.merchantCity = merchantCity;
    }

    /**
     * Gets the merchant zip code
     * 
     * @return postal code where transaction occurred
     */
    public String getMerchantZip() {
        return merchantZip;
    }

    /**
     * Sets the merchant zip code
     * 
     * @param merchantZip postal code where transaction occurred
     */
    public void setMerchantZip(String merchantZip) {
        this.merchantZip = merchantZip;
    }

    /**
     * Gets the card entity
     * 
     * @return card used for this transaction
     */
    public Card getCard() {
        return card;
    }

    /**
     * Sets the card entity
     * 
     * @param card card used for this transaction
     */
    public void setCard(Card card) {
        this.card = card;
    }

    /**
     * Gets the original timestamp
     * 
     * @return when transaction was originally initiated
     */
    public LocalDateTime getOriginalTimestamp() {
        return originalTimestamp;
    }

    /**
     * Sets the original timestamp
     * 
     * @param originalTimestamp when transaction was originally initiated
     */
    public void setOriginalTimestamp(LocalDateTime originalTimestamp) {
        this.originalTimestamp = originalTimestamp;
    }

    /**
     * Gets the processed timestamp
     * 
     * @return when transaction was processed by the system
     */
    public LocalDateTime getProcessedTimestamp() {
        return processedTimestamp;
    }

    /**
     * Sets the processed timestamp
     * 
     * @param processedTimestamp when transaction was processed by the system
     */
    public void setProcessedTimestamp(LocalDateTime processedTimestamp) {
        this.processedTimestamp = processedTimestamp;
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
     * @return when transaction record was first created
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp
     * Note: This is typically managed automatically by JPA
     * 
     * @param createdAt when transaction record was first created
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the last update timestamp
     * 
     * @return when transaction record was last modified
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last update timestamp
     * Note: This is typically managed automatically by JPA
     * 
     * @param updatedAt when transaction record was last modified
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Convenience methods for accessing related entity properties

    /**
     * Gets the transaction type code from the related TransactionType entity
     * 
     * @return 2-character transaction type code
     */
    public String getTransactionTypeCode() {
        return transactionType != null ? transactionType.getTypeCode() : null;
    }

    /**
     * Gets the transaction category code from the related TransactionCategory entity
     * 
     * @return 4-digit transaction category code
     */
    public Integer getTransactionCategoryCode() {
        return transactionCategory != null ? transactionCategory.getCategoryCode() : null;
    }

    /**
     * Gets the card number from the related Card entity
     * 
     * @return 16-character card number
     */
    public String getCardNumber() {
        return card != null ? card.getCardNumber() : null;
    }

    /**
     * Gets the account ID associated with this transaction through the card
     * 
     * @return 11-character account identifier
     */
    public String getAccountId() {
        return card != null ? card.getAccountId() : null;
    }

    // Business Logic Methods

    /**
     * Business method to check if this transaction is a debit (negative amount)
     * Implements COBOL-style logic for transaction classification
     * 
     * @return true if transaction amount is negative (debit)
     */
    public boolean isDebit() {
        return transactionAmount != null && transactionAmount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Business method to check if this transaction is a credit (positive amount)
     * Implements COBOL-style logic for transaction classification
     * 
     * @return true if transaction amount is positive (credit)
     */
    public boolean isCredit() {
        return transactionAmount != null && transactionAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Business method to get the absolute amount of the transaction
     * Useful for display purposes where sign is handled separately
     * 
     * @return absolute value of transaction amount
     */
    public BigDecimal getAbsoluteAmount() {
        return transactionAmount != null ? transactionAmount.abs() : BigDecimal.ZERO;
    }

    /**
     * Business method to check if transaction has been processed
     * Determines if the transaction has completed processing
     * 
     * @return true if processed timestamp is set
     */
    public boolean isProcessed() {
        return processedTimestamp != null;
    }

    /**
     * Business method to check if transaction is pending processing
     * Useful for batch processing and status reporting
     * 
     * @return true if transaction has not been processed yet
     */
    public boolean isPending() {
        return processedTimestamp == null;
    }

    /**
     * Business method to calculate processing time in seconds
     * Useful for performance monitoring and SLA compliance
     * 
     * @return processing time in seconds, or null if not processed
     */
    public Long getProcessingTimeSeconds() {
        if (originalTimestamp == null || processedTimestamp == null) {
            return null;
        }
        return java.time.Duration.between(originalTimestamp, processedTimestamp).getSeconds();
    }

    /**
     * Business method to check if transaction is same-day processed
     * Useful for daily reconciliation and batch processing validation
     * 
     * @return true if original and processed timestamps are on the same date
     */
    public boolean isSameDayProcessed() {
        if (originalTimestamp == null || processedTimestamp == null) {
            return false;
        }
        return originalTimestamp.toLocalDate().equals(processedTimestamp.toLocalDate());
    }

    /**
     * Business method to format transaction amount for display
     * Implements COBOL-style monetary formatting with proper decimal places
     * 
     * @return formatted amount string with 2 decimal places
     */
    public String getFormattedAmount() {
        if (transactionAmount == null) {
            return "0.00";
        }
        return String.format("%.2f", transactionAmount);
    }

    /**
     * Business method to get merchant location as combined city/zip
     * Useful for display purposes and reporting
     * 
     * @return formatted merchant location string
     */
    public String getMerchantLocation() {
        if (merchantCity == null && merchantZip == null) {
            return "";
        }
        if (merchantCity == null) {
            return merchantZip;
        }
        if (merchantZip == null) {
            return merchantCity;
        }
        return merchantCity + ", " + merchantZip;
    }

    /**
     * Business validation method to ensure transaction integrity
     * Implements COBOL-style validation equivalent to 88-level conditions
     * 
     * @return true if transaction data is valid for processing
     */
    public boolean isValid() {
        return transactionId != null && 
               transactionId.length() == 16 &&
               transactionType != null &&
               transactionCategory != null &&
               transactionSource != null && 
               !transactionSource.trim().isEmpty() &&
               transactionDescription != null && 
               !transactionDescription.trim().isEmpty() &&
               transactionAmount != null &&
               card != null &&
               originalTimestamp != null;
    }

    /**
     * Business method to check if transaction requires manual review
     * Implements business rules for fraud detection and compliance
     * 
     * @return true if transaction should be flagged for manual review
     */
    public boolean requiresManualReview() {
        if (transactionAmount == null) {
            return true;
        }
        
        // Flag high-value transactions (over $10,000)
        BigDecimal highValueThreshold = new BigDecimal("10000.00");
        if (transactionAmount.abs().compareTo(highValueThreshold) > 0) {
            return true;
        }
        
        // Flag transactions with missing merchant information
        if (merchantId == null || merchantName == null || merchantName.trim().isEmpty()) {
            return true;
        }
        
        // Flag transactions processed more than 24 hours after initiation
        if (isProcessed()) {
            long processingHours = java.time.Duration.between(originalTimestamp, processedTimestamp).toHours();
            if (processingHours > 24) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Creates a formatted display string for transaction summary
     * Useful for logging, debugging, and customer service displays
     * 
     * @return formatted transaction summary string
     */
    public String getTransactionSummary() {
        return String.format("Transaction %s: %s %s at %s on %s", 
                transactionId,
                isDebit() ? "Debit" : "Credit",
                getFormattedAmount(),
                merchantName != null ? merchantName : "Unknown Merchant",
                originalTimestamp != null ? originalTimestamp.toLocalDate() : "Unknown Date");
    }

    /**
     * Converts timestamp to COBOL-compatible string format
     * Maintains compatibility with legacy system interfaces
     * 
     * @param timestamp the timestamp to format
     * @return COBOL-compatible timestamp string
     */
    public static String formatTimestampForCobol(LocalDateTime timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.format(COBOL_TIMESTAMP_FORMATTER);
    }

    /**
     * Parses COBOL-compatible timestamp string to LocalDateTime
     * Enables migration from legacy timestamp formats
     * 
     * @param cobolTimestamp COBOL timestamp string
     * @return parsed LocalDateTime
     */
    public static LocalDateTime parseCobolTimestamp(String cobolTimestamp) {
        if (cobolTimestamp == null || cobolTimestamp.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(cobolTimestamp.trim(), COBOL_TIMESTAMP_FORMATTER);
        } catch (Exception e) {
            // Handle parsing errors gracefully
            return null;
        }
    }

    /**
     * Equals method based on business key (transaction_id) for entity comparison
     * 
     * @param obj object to compare with
     * @return true if objects are equal based on transaction ID
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Transaction that = (Transaction) obj;
        return Objects.equals(transactionId, that.transactionId);
    }

    /**
     * Hash code method based on business key (transaction_id) for collection operations
     * 
     * @return hash code for this transaction
     */
    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }

    /**
     * String representation for debugging and logging purposes
     * 
     * @return formatted string representation of transaction
     */
    @Override
    public String toString() {
        return String.format(
            "Transaction{transactionId='%s', amount=%s, source='%s', cardNumber='%s', " +
            "typeCode='%s', categoryCode=%d, merchant='%s', originalTimestamp=%s, " +
            "processedTimestamp=%s, rowVersion=%d}",
            transactionId, 
            transactionAmount,
            transactionSource,
            getCardNumber(),
            getTransactionTypeCode(),
            getTransactionCategoryCode(),
            merchantName,
            originalTimestamp,
            processedTimestamp,
            rowVersion
        );
    }
}