package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA entity class representing transaction category balance tracking 
 * converted from COBOL TRAN-CAT-BAL-RECORD structure.
 * 
 * Maps to PostgreSQL category_balances table with composite primary key
 * supporting balance tracking by category for reporting and analytics operations.
 * 
 * Features:
 * - Composite primary key with relationships to Account, TransactionType, and TransactionCategory entities
 * - BigDecimal balance field for precise monetary calculations matching COBOL COMP-3 precision
 * - Optimistic locking for concurrent balance updates
 * - Composite indexes for efficient balance tracking and reporting operations
 * 
 * Source: app/cpy/CVTRA01Y.cpy - TRAN-CAT-BAL-RECORD structure
 * Migration: Section 0.2.2 COBOL to Java Service Mappings
 */
@Entity
@Table(name = "category_balances", 
       indexes = {
           @Index(name = "idx_category_balances_acct_type", columnList = "account_id, transaction_type_code"),
           @Index(name = "idx_category_balances_acct_cat", columnList = "account_id, category_code"), 
           @Index(name = "idx_category_balances_type_cat", columnList = "transaction_type_code, category_code"),
           @Index(name = "idx_category_balances_balance", columnList = "balance"),
           @Index(name = "idx_category_balances_last_updated", columnList = "last_updated_timestamp")
       })
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class CategoryBalance implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Composite primary key class for CategoryBalance entity.
     * Maps to COBOL TRAN-CAT-KEY structure with three components:
     * - TRANCAT-ACCT-ID: PIC 9(11) -> String account_id
     * - TRANCAT-TYPE-CD: PIC X(02) -> String transaction_type_code  
     * - TRANCAT-CD: PIC 9(04) -> Integer category_code
     */
    @Embeddable
    public static class CategoryBalanceId implements Serializable {
        
        private static final long serialVersionUID = 1L;

        /**
         * Account identifier - maps to COBOL TRANCAT-ACCT-ID PIC 9(11)
         * Foreign key to Account entity
         */
        @Column(name = "account_id", length = 11, nullable = false)
        @NotNull(message = "Account ID cannot be null")
        @Size(min = 11, max = 11, message = "Account ID must be exactly 11 digits")
        private String accountId;

        /**
         * Transaction type code - maps to COBOL TRANCAT-TYPE-CD PIC X(02)
         * Foreign key to TransactionType entity
         */
        @Column(name = "transaction_type_code", length = 2, nullable = false)
        @NotNull(message = "Transaction type code cannot be null")
        @Size(min = 2, max = 2, message = "Transaction type code must be exactly 2 characters")
        private String transactionTypeCode;

        /**
         * Category code - maps to COBOL TRANCAT-CD PIC 9(04)
         * Foreign key to TransactionCategory entity
         */
        @Column(name = "category_code", nullable = false)
        @NotNull(message = "Category code cannot be null")
        @Min(value = 1, message = "Category code must be positive")
        @Max(value = 9999, message = "Category code must not exceed 4 digits")
        private Integer categoryCode;

        // Default constructor required by JPA
        public CategoryBalanceId() {}

        /**
         * Constructor for creating composite key
         */
        public CategoryBalanceId(String accountId, String transactionTypeCode, Integer categoryCode) {
            this.accountId = accountId;
            this.transactionTypeCode = transactionTypeCode;
            this.categoryCode = categoryCode;
        }

        // Getters and setters
        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getTransactionTypeCode() {
            return transactionTypeCode;
        }

        public void setTransactionTypeCode(String transactionTypeCode) {
            this.transactionTypeCode = transactionTypeCode;
        }

        public Integer getCategoryCode() {
            return categoryCode;
        }

        public void setCategoryCode(Integer categoryCode) {
            this.categoryCode = categoryCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CategoryBalanceId that = (CategoryBalanceId) o;
            return Objects.equals(accountId, that.accountId) &&
                   Objects.equals(transactionTypeCode, that.transactionTypeCode) &&
                   Objects.equals(categoryCode, that.categoryCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, transactionTypeCode, categoryCode);
        }

        @Override
        public String toString() {
            return String.format("CategoryBalanceId{accountId='%s', transactionTypeCode='%s', categoryCode=%d}", 
                               accountId, transactionTypeCode, categoryCode);
        }
    }

    /**
     * Composite primary key - maps to COBOL TRAN-CAT-KEY
     */
    @EmbeddedId
    @Valid
    private CategoryBalanceId id;

    /**
     * Account entity relationship - joins on account_id component of composite key
     * Maps to Account entity for balance tracking per account
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", 
                insertable = false, updatable = false)
    @NotNull(message = "Account reference cannot be null")
    private Account account;

    /**
     * Transaction type entity relationship - joins on transaction_type_code component
     * Maps to TransactionType entity for type-based balance categorization
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_type_code", referencedColumnName = "type_code",
                insertable = false, updatable = false)
    @NotNull(message = "Transaction type reference cannot be null")  
    private TransactionType transactionType;

    /**
     * Transaction category entity relationship - joins on category_code component
     * Maps to TransactionCategory entity for detailed categorization
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_code", referencedColumnName = "category_code",
                insertable = false, updatable = false)
    @NotNull(message = "Transaction category reference cannot be null")
    private TransactionCategory transactionCategory;

    /**
     * Category balance amount - maps to COBOL TRAN-CAT-BAL PIC S9(09)V99
     * BigDecimal with scale 2 for exact monetary precision
     * Supports positive and negative balances for comprehensive tracking
     */
    @Column(name = "balance", precision = 11, scale = 2, nullable = false)
    @NotNull(message = "Balance cannot be null")
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * Creation timestamp for audit trail
     */
    @CreationTimestamp
    @Column(name = "created_timestamp", nullable = false, updatable = false)
    private LocalDateTime createdTimestamp;

    /**
     * Last update timestamp for audit trail and change tracking
     */
    @UpdateTimestamp
    @Column(name = "last_updated_timestamp", nullable = false)
    private LocalDateTime lastUpdatedTimestamp;

    /**
     * Optimistic locking version field per Section 6.2.1.1 database design
     * Enables concurrent balance updates without explicit locking
     */
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion = 0;

    // Default constructor required by JPA
    public CategoryBalance() {}

    /**
     * Constructor for creating new category balance record
     */
    public CategoryBalance(CategoryBalanceId id, BigDecimal balance) {
        this.id = id;
        this.balance = balance;
    }

    /**
     * Constructor with all required fields
     */
    public CategoryBalance(String accountId, String transactionTypeCode, 
                          Integer categoryCode, BigDecimal balance) {
        this.id = new CategoryBalanceId(accountId, transactionTypeCode, categoryCode);
        this.balance = balance;
    }

    // Business methods

    /**
     * Add amount to current balance
     * Maintains COBOL-equivalent arithmetic precision
     */
    public void addToBalance(BigDecimal amount) {
        if (amount != null) {
            this.balance = this.balance.add(amount);
        }
    }

    /**
     * Subtract amount from current balance  
     * Maintains COBOL-equivalent arithmetic precision
     */
    public void subtractFromBalance(BigDecimal amount) {
        if (amount != null) {
            this.balance = this.balance.subtract(amount);
        }
    }

    /**
     * Set new balance amount with validation
     */
    public void setBalance(BigDecimal balance) {
        this.balance = balance != null ? balance : BigDecimal.ZERO;
    }

    /**
     * Check if balance is positive
     */
    public boolean hasPositiveBalance() {
        return balance.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if balance is negative
     */
    public boolean hasNegativeBalance() {
        return balance.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Check if balance is zero
     */
    public boolean hasZeroBalance() {
        return balance.compareTo(BigDecimal.ZERO) == 0;
    }

    // Standard getters and setters

    public CategoryBalanceId getId() {
        return id;
    }

    public void setId(CategoryBalanceId id) {
        this.id = id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public TransactionCategory getTransactionCategory() {
        return transactionCategory;
    }

    public void setTransactionCategory(TransactionCategory transactionCategory) {
        this.transactionCategory = transactionCategory;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public LocalDateTime getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(LocalDateTime createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public LocalDateTime getLastUpdatedTimestamp() {
        return lastUpdatedTimestamp;
    }

    public void setLastUpdatedTimestamp(LocalDateTime lastUpdatedTimestamp) {
        this.lastUpdatedTimestamp = lastUpdatedTimestamp;
    }

    public Integer getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Integer rowVersion) {
        this.rowVersion = rowVersion;
    }

    // JPA lifecycle callbacks

    /**
     * Pre-persist validation to ensure data integrity
     */
    @PrePersist
    protected void prePersist() {
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        if (rowVersion == null) {
            rowVersion = 0;
        }
    }

    /**
     * Pre-update validation and timestamp management
     */
    @PreUpdate
    protected void preUpdate() {
        // Ensure balance is never null
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
    }

    // Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CategoryBalance that = (CategoryBalance) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("CategoryBalance{id=%s, balance=%s, version=%d}", 
                           id, balance, rowVersion);
    }
}