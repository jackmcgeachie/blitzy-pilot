package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.cache.annotation.Cacheable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * JPA entity representing discount group configuration converted from COBOL DIS-GROUP-RECORD structure.
 * 
 * This entity maps discount group rules that determine interest rates applied to account transactions
 * based on account group, transaction type, and transaction category combinations. The implementation
 * preserves exact COBOL COMP-3 precision for financial calculations while providing modern JPA
 * capabilities including optimistic locking and caching optimization for reference data.
 * 
 * Original COBOL structure from CVTRA02Y.cpy:
 * 01  DIS-GROUP-RECORD.
 *     05  DIS-GROUP-KEY.
 *        10 DIS-ACCT-GROUP-ID     PIC X(10).
 *        10 DIS-TRAN-TYPE-CD      PIC X(02).
 *        10 DIS-TRAN-CAT-CD       PIC 9(04).
 *     05  DIS-INT-RATE            PIC S9(04)V99.
 *     05  FILLER                  PIC X(28).
 * 
 * Database mapping: PostgreSQL discount_groups table
 * Composite primary key: (group_code, type_code, category_code)
 * Supports: Reference data caching, optimistic locking, foreign key relationships
 * 
 * @see TransactionType for transaction type definitions
 * @see TransactionCategory for transaction category definitions
 */
@Entity
@Table(name = "discount_groups", indexes = {
    @Index(name = "idx_discount_groups_type_category", columnList = "type_code, category_code"),
    @Index(name = "idx_discount_groups_group_type", columnList = "group_code, type_code")
})
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Cacheable("discountGroups")
public class DiscountGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Composite primary key class for DiscountGroup entity.
     * 
     * Implements the composite DIS-GROUP-KEY structure from COBOL, combining:
     * - Account group identifier (DIS-ACCT-GROUP-ID)
     * - Transaction type code (DIS-TRAN-TYPE-CD) 
     * - Transaction category code (DIS-TRAN-CAT-CD)
     */
    @Embeddable
    public static class DiscountGroupId implements Serializable {
        
        private static final long serialVersionUID = 1L;

        /**
         * Account group identifier (DIS-ACCT-GROUP-ID).
         * 10-character string identifier for discount group classification.
         */
        @Column(name = "group_code", length = 10, nullable = false)
        @NotBlank(message = "Group code is required")
        @Size(max = 10, message = "Group code cannot exceed 10 characters")
        @Pattern(regexp = "^[A-Z0-9]+$", message = "Group code must contain only uppercase letters and numbers")
        private String groupCode;

        /**
         * Transaction type code (DIS-TRAN-TYPE-CD).
         * 2-character code referencing TransactionType entity.
         */
        @Column(name = "type_code", length = 2, nullable = false)
        @NotBlank(message = "Transaction type code is required")
        @Size(min = 2, max = 2, message = "Transaction type code must be exactly 2 characters")
        @Pattern(regexp = "^[A-Z0-9]{2}$", message = "Transaction type code must be 2 uppercase alphanumeric characters")
        private String typeCode;

        /**
         * Transaction category code (DIS-TRAN-CAT-CD).
         * 4-digit integer code referencing TransactionCategory entity.
         */
        @Column(name = "category_code", nullable = false)
        @NotNull(message = "Transaction category code is required")
        @Min(value = 1, message = "Transaction category code must be positive")
        @Max(value = 9999, message = "Transaction category code cannot exceed 4 digits")
        private Integer categoryCode;

        // Default constructor required by JPA
        public DiscountGroupId() {}

        /**
         * Constructor for creating composite primary key.
         * 
         * @param groupCode Account group identifier (10 characters max)
         * @param typeCode Transaction type code (2 characters)
         * @param categoryCode Transaction category code (4 digits max)
         */
        public DiscountGroupId(String groupCode, String typeCode, Integer categoryCode) {
            this.groupCode = groupCode;
            this.typeCode = typeCode;
            this.categoryCode = categoryCode;
        }

        // Getters and setters with validation
        public String getGroupCode() {
            return groupCode;
        }

        public void setGroupCode(String groupCode) {
            this.groupCode = groupCode;
        }

        public String getTypeCode() {
            return typeCode;
        }

        public void setTypeCode(String typeCode) {
            this.typeCode = typeCode;
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
            DiscountGroupId that = (DiscountGroupId) o;
            return Objects.equals(groupCode, that.groupCode) &&
                   Objects.equals(typeCode, that.typeCode) &&
                   Objects.equals(categoryCode, that.categoryCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupCode, typeCode, categoryCode);
        }

        @Override
        public String toString() {
            return String.format("DiscountGroupId{groupCode='%s', typeCode='%s', categoryCode=%d}", 
                               groupCode, typeCode, categoryCode);
        }
    }

    /**
     * Composite primary key identifying the discount group.
     * Maps to DIS-GROUP-KEY from COBOL structure.
     */
    @EmbeddedId
    private DiscountGroupId id;

    /**
     * Interest rate for this discount group (DIS-INT-RATE).
     * 
     * Converted from COBOL PIC S9(04)V99 with exact precision maintained.
     * Scale 2 ensures proper monetary calculations matching COBOL COMP-3 precision.
     * Range: -99.99 to +99.99 representing percentage rates.
     */
    @Column(name = "interest_rate", precision = 6, scale = 2, nullable = false)
    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "-99.99", message = "Interest rate cannot be less than -99.99%")
    @DecimalMax(value = "99.99", message = "Interest rate cannot exceed 99.99%")
    @Digits(integer = 4, fraction = 2, message = "Interest rate must have at most 4 integer digits and 2 decimal places")
    private BigDecimal interestRate;

    /**
     * Relationship to TransactionType entity based on type_code.
     * Provides navigation to transaction type definitions and validation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_code", referencedColumnName = "type_code", insertable = false, updatable = false)
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    private TransactionType transactionType;

    /**
     * Relationship to TransactionCategory entity based on category_code.
     * Provides navigation to transaction category definitions and validation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "type_code", referencedColumnName = "type_code", insertable = false, updatable = false),
        @JoinColumn(name = "category_code", referencedColumnName = "category_code", insertable = false, updatable = false)
    })
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    private TransactionCategory transactionCategory;

    /**
     * Version field for optimistic locking per Section 6.2.1.1 database design requirements.
     * Prevents concurrent modifications and ensures data consistency.
     */
    @Version
    @Column(name = "row_version")
    private Integer version;

    // Default constructor required by JPA
    public DiscountGroup() {}

    /**
     * Constructor for creating new discount group.
     * 
     * @param id Composite primary key containing group, type, and category codes
     * @param interestRate Interest rate percentage with 2 decimal precision
     */
    public DiscountGroup(DiscountGroupId id, BigDecimal interestRate) {
        this.id = id;
        this.interestRate = interestRate;
    }

    /**
     * Convenience constructor for creating discount group with individual key components.
     * 
     * @param groupCode Account group identifier (10 characters max)
     * @param typeCode Transaction type code (2 characters)
     * @param categoryCode Transaction category code (4 digits max)
     * @param interestRate Interest rate percentage with 2 decimal precision
     */
    public DiscountGroup(String groupCode, String typeCode, Integer categoryCode, BigDecimal interestRate) {
        this.id = new DiscountGroupId(groupCode, typeCode, categoryCode);
        this.interestRate = interestRate;
    }

    // Getters and setters with proper validation

    public DiscountGroupId getId() {
        return id;
    }

    public void setId(DiscountGroupId id) {
        this.id = id;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    // Convenience getters for composite key components
    
    /**
     * Gets the account group code from the composite key.
     * @return Account group identifier (DIS-ACCT-GROUP-ID)
     */
    public String getGroupCode() {
        return id != null ? id.getGroupCode() : null;
    }

    /**
     * Gets the transaction type code from the composite key.
     * @return Transaction type code (DIS-TRAN-TYPE-CD)
     */
    public String getTypeCode() {
        return id != null ? id.getTypeCode() : null;
    }

    /**
     * Gets the transaction category code from the composite key.
     * @return Transaction category code (DIS-TRAN-CAT-CD)
     */
    public Integer getCategoryCode() {
        return id != null ? id.getCategoryCode() : null;
    }

    /**
     * Validates that interest rate precision matches COBOL COMP-3 requirements.
     * 
     * @return true if interest rate has proper scale and precision
     */
    public boolean isValidInterestRatePrecision() {
        if (interestRate == null) return false;
        return interestRate.scale() == 2 && interestRate.precision() <= 6;
    }

    /**
     * Calculates the actual interest amount for a given principal amount.
     * 
     * @param principalAmount The base amount to calculate interest on
     * @return The calculated interest amount with COBOL-equivalent precision
     */
    public BigDecimal calculateInterestAmount(BigDecimal principalAmount) {
        if (principalAmount == null || interestRate == null) {
            return BigDecimal.ZERO;
        }
        
        // Calculate interest: principal * (rate / 100)
        // Maintain COBOL precision with proper rounding
        return principalAmount.multiply(interestRate)
                            .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Creates a formatted string representation for display purposes.
     * Maintains compatibility with COBOL display formats.
     * 
     * @return Formatted string showing group details and interest rate
     */
    public String getDisplayString() {
        return String.format("Group: %s | Type: %s | Category: %04d | Rate: %+.2f%%",
                           getGroupCode(), getTypeCode(), getCategoryCode(), interestRate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscountGroup that = (DiscountGroup) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("DiscountGroup{id=%s, interestRate=%s, version=%d}", 
                           id, interestRate, version);
    }
}