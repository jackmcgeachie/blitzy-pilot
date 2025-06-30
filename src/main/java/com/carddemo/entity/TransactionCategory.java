/*
 * TransactionCategory Entity
 * 
 * Converted from COBOL copybook: app/cpy/CVTRA04Y.cpy
 * Original structure: TRAN-CAT-RECORD (Record Length = 60)
 * 
 * This JPA entity represents transaction category definitions that provide
 * reference data for transaction classification in the CardDemo system.
 * 
 * COBOL Structure Mapping:
 * - TRAN-CAT-KEY.TRAN-TYPE-CD (PIC X(02)) -> TransactionCategoryId.typeCode
 * - TRAN-CAT-KEY.TRAN-CAT-CD (PIC 9(04)) -> TransactionCategoryId.categoryCode  
 * - TRAN-CAT-TYPE-DESC (PIC X(50)) -> description
 * 
 * Features:
 * - Composite primary key using @EmbeddedId for (type_code, category_code)
 * - Optimistic locking with @Version for concurrent access control
 * - Relationship mappings for Transaction and TransactionType entities
 * - Caching annotations for reference data optimization
 * - Bean validation annotations matching COBOL field constraints
 */

package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/**
 * JPA Entity representing transaction category definitions.
 * 
 * This entity converts the COBOL TRAN-CAT-RECORD structure to support
 * transaction categorization through composite key management.
 * 
 * Maps to PostgreSQL transaction_categories table with composite primary key
 * on (type_code, category_code) matching COBOL TRAN-CAT-KEY structure.
 */
@Entity
@Table(name = "transaction_categories")
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class TransactionCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Composite primary key class for TransactionCategory entity.
     * 
     * Maps COBOL TRAN-CAT-KEY structure:
     * - TRAN-TYPE-CD PIC X(02) -> typeCode
     * - TRAN-CAT-CD PIC 9(04) -> categoryCode
     */
    @Embeddable
    public static class TransactionCategoryId implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Transaction type code component of composite key.
         * Maps to COBOL TRAN-TYPE-CD PIC X(02).
         */
        @Column(name = "type_code", length = 2, nullable = false)
        @NotNull(message = "Transaction type code is required")
        @Size(min = 2, max = 2, message = "Transaction type code must be exactly 2 characters")
        private String typeCode;

        /**
         * Transaction category code component of composite key.
         * Maps to COBOL TRAN-CAT-CD PIC 9(04).
         */
        @Column(name = "category_code", nullable = false)
        @NotNull(message = "Transaction category code is required")
        private Integer categoryCode;

        // Default constructor required by JPA
        public TransactionCategoryId() {}

        /**
         * Constructor for creating composite key.
         * 
         * @param typeCode Transaction type code (2 characters)
         * @param categoryCode Transaction category code (4 digits)
         */
        public TransactionCategoryId(String typeCode, Integer categoryCode) {
            this.typeCode = typeCode;
            this.categoryCode = categoryCode;
        }

        // Getters and setters
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
            TransactionCategoryId that = (TransactionCategoryId) o;
            return Objects.equals(typeCode, that.typeCode) && 
                   Objects.equals(categoryCode, that.categoryCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeCode, categoryCode);
        }

        @Override
        public String toString() {
            return "TransactionCategoryId{" +
                    "typeCode='" + typeCode + '\'' +
                    ", categoryCode=" + categoryCode +
                    '}';
        }
    }

    /**
     * Composite primary key for transaction category.
     * Combines type_code and category_code matching COBOL TRAN-CAT-KEY structure.
     */
    @EmbeddedId
    @Valid
    private TransactionCategoryId id;

    /**
     * Transaction category description.
     * Maps to COBOL TRAN-CAT-TYPE-DESC PIC X(50).
     */
    @Column(name = "category_description", length = 50, nullable = false)
    @NotNull(message = "Transaction category description is required")
    @Size(max = 50, message = "Transaction category description cannot exceed 50 characters")
    private String description;

    /**
     * Version field for optimistic locking.
     * Enables concurrent access control per Section 6.2.1.1 requirements.
     */
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer version;

    /**
     * Many-to-one relationship with TransactionType entity.
     * Links to transaction type through type_code component of composite key.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_code", referencedColumnName = "type_code", insertable = false, updatable = false)
    private TransactionType transactionType;

    /**
     * One-to-many relationship with Transaction entities.
     * Maps transactions that use this category for classification.
     */
    @OneToMany(mappedBy = "transactionCategory", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Transaction> transactions;

    // Default constructor required by JPA
    public TransactionCategory() {}

    /**
     * Constructor for creating TransactionCategory with composite key and description.
     * 
     * @param typeCode Transaction type code (2 characters)
     * @param categoryCode Transaction category code (4 digits)
     * @param description Category description (up to 50 characters)
     */
    public TransactionCategory(String typeCode, Integer categoryCode, String description) {
        this.id = new TransactionCategoryId(typeCode, categoryCode);
        this.description = description;
    }

    /**
     * Constructor for creating TransactionCategory with composite key ID and description.
     * 
     * @param id Composite primary key
     * @param description Category description
     */
    public TransactionCategory(TransactionCategoryId id, String description) {
        this.id = id;
        this.description = description;
    }

    // Getters and setters

    public TransactionCategoryId getId() {
        return id;
    }

    public void setId(TransactionCategoryId id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public Set<Transaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(Set<Transaction> transactions) {
        this.transactions = transactions;
    }

    // Convenience methods for accessing composite key components

    /**
     * Gets the transaction type code component of the composite key.
     * 
     * @return Transaction type code (2 characters)
     */
    public String getTypeCode() {
        return id != null ? id.getTypeCode() : null;
    }

    /**
     * Sets the transaction type code component of the composite key.
     * 
     * @param typeCode Transaction type code (2 characters)
     */
    public void setTypeCode(String typeCode) {
        if (id == null) {
            id = new TransactionCategoryId();
        }
        id.setTypeCode(typeCode);
    }

    /**
     * Gets the transaction category code component of the composite key.
     * 
     * @return Transaction category code (4 digits)
     */
    public Integer getCategoryCode() {
        return id != null ? id.getCategoryCode() : null;
    }

    /**
     * Sets the transaction category code component of the composite key.
     * 
     * @param categoryCode Transaction category code (4 digits)
     */
    public void setCategoryCode(Integer categoryCode) {
        if (id == null) {
            id = new TransactionCategoryId();
        }
        id.setCategoryCode(categoryCode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionCategory that = (TransactionCategory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TransactionCategory{" +
                "id=" + id +
                ", description='" + description + '\'' +
                ", version=" + version +
                '}';
    }
}