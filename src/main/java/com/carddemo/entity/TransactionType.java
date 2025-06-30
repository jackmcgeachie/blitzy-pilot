package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * JPA Entity class representing transaction type definitions converted from COBOL TRAN-TYPE-RECORD structure.
 * 
 * Original COBOL structure (CVTRA03Y.cpy):
 * - TRAN-TYPE: PIC X(02) - 2-character transaction type code  
 * - TRAN-TYPE-DESC: PIC X(50) - 50-character description
 * 
 * This entity implements reference data table with primary key on type_code, OneToMany relationship 
 * with Transaction entity, and caching annotations for optimal performance. Supports transaction 
 * categorization and validation through type code management.
 * 
 * Maps to PostgreSQL transaction_types table with the following structure:
 * - type_code VARCHAR(2) PRIMARY KEY
 * - type_description VARCHAR(50) NOT NULL
 * - row_version INTEGER NOT NULL (for optimistic locking)
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since CardDemo v1.0-15-g27d6c6f-68
 */
@Entity
@Table(name = "transaction_types", 
       uniqueConstraints = @UniqueConstraint(columnNames = "type_code"))
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Cacheable
public class TransactionType implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Transaction type code - 2-character identifier (Primary Key)
     * Converted from COBOL TRAN-TYPE PIC X(02)
     * 
     * Validation rules:
     * - Must be exactly 2 characters
     * - Must contain only uppercase letters and digits
     * - Cannot be null or blank
     */
    @Id
    @Column(name = "type_code", length = 2, nullable = false)
    @NotBlank(message = "Transaction type code cannot be blank")
    @Size(min = 2, max = 2, message = "Transaction type code must be exactly 2 characters")
    @Pattern(regexp = "^[A-Z0-9]{2}$", 
             message = "Transaction type code must contain only uppercase letters and digits")
    private String typeCode;

    /**
     * Transaction type description - descriptive name for the transaction type
     * Converted from COBOL TRAN-TYPE-DESC PIC X(50)
     * 
     * Validation rules:
     * - Maximum 50 characters as per original COBOL specification
     * - Cannot be null or blank
     * - Used for display purposes and reporting
     */
    @Column(name = "type_description", length = 50, nullable = false)
    @NotBlank(message = "Transaction type description cannot be blank")
    @Size(max = 50, message = "Transaction type description cannot exceed 50 characters")
    private String typeDescription;

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
     * OneToMany relationship mapping to Transaction entity for transaction type categorization
     * Implements bidirectional association for efficient navigation between transaction types
     * and their associated transactions.
     * 
     * Configuration:
     * - mappedBy: References the transactionType field in Transaction entity
     * - fetch = LAZY: Deferred loading for performance optimization
     * - cascade = CascadeType.NONE: Transaction type deletion does not cascade to transactions
     */
    @OneToMany(mappedBy = "transactionType", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<Transaction> transactions = new HashSet<>();

    /**
     * Default constructor required by JPA specification
     * Initializes empty transaction type with default values
     */
    public TransactionType() {
        // Default constructor for JPA
    }

    /**
     * Constructor with type code for creating new transaction type instances
     * 
     * @param typeCode 2-character transaction type identifier
     */
    public TransactionType(String typeCode) {
        this.typeCode = typeCode;
    }

    /**
     * Full constructor for creating transaction type with all required fields
     * 
     * @param typeCode 2-character transaction type identifier
     * @param typeDescription descriptive name for the transaction type
     */
    public TransactionType(String typeCode, String typeDescription) {
        this.typeCode = typeCode;
        this.typeDescription = typeDescription;
    }

    /**
     * Gets the transaction type code (Primary Key)
     * 
     * @return 2-character transaction type identifier
     */
    public String getTypeCode() {
        return typeCode;
    }

    /**
     * Sets the transaction type code (Primary Key)
     * 
     * @param typeCode 2-character transaction type identifier
     */
    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    /**
     * Gets the transaction type description
     * 
     * @return descriptive name for the transaction type
     */
    public String getTypeDescription() {
        return typeDescription;
    }

    /**
     * Sets the transaction type description
     * 
     * @param typeDescription descriptive name for the transaction type
     */
    public void setTypeDescription(String typeDescription) {
        this.typeDescription = typeDescription;
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
     * Gets the set of transactions associated with this transaction type
     * 
     * @return set of transactions using this type
     */
    public Set<Transaction> getTransactions() {
        return transactions;
    }

    /**
     * Sets the set of transactions associated with this transaction type
     * 
     * @param transactions set of transactions using this type
     */
    public void setTransactions(Set<Transaction> transactions) {
        this.transactions = transactions;
    }

    /**
     * Convenience method to add a transaction to this transaction type
     * Maintains bidirectional relationship consistency
     * 
     * @param transaction transaction to associate with this type
     */
    public void addTransaction(Transaction transaction) {
        if (transaction != null) {
            this.transactions.add(transaction);
            transaction.setTransactionType(this);
        }
    }

    /**
     * Convenience method to remove a transaction from this transaction type
     * Maintains bidirectional relationship consistency
     * 
     * @param transaction transaction to dissociate from this type
     */
    public void removeTransaction(Transaction transaction) {
        if (transaction != null) {
            this.transactions.remove(transaction);
            transaction.setTransactionType(null);
        }
    }

    /**
     * Business method to check if this transaction type is actively used
     * Useful for validation before deletion or modification
     * 
     * @return true if this type has associated transactions
     */
    public boolean hasActiveTransactions() {
        return transactions != null && !transactions.isEmpty();
    }

    /**
     * Business method to get the count of associated transactions
     * Useful for reporting and analysis
     * 
     * @return number of transactions using this type
     */
    public int getTransactionCount() {
        return transactions != null ? transactions.size() : 0;
    }

    /**
     * Equals method based on business key (type_code) for entity comparison
     * 
     * @param obj object to compare with
     * @return true if objects are equal based on type code
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TransactionType that = (TransactionType) obj;
        return Objects.equals(typeCode, that.typeCode);
    }

    /**
     * Hash code method based on business key (type_code) for collection operations
     * 
     * @return hash code for this transaction type
     */
    @Override
    public int hashCode() {
        return Objects.hash(typeCode);
    }

    /**
     * String representation for debugging and logging purposes
     * 
     * @return formatted string representation of transaction type
     */
    @Override
    public String toString() {
        return String.format("TransactionType{typeCode='%s', typeDescription='%s', rowVersion=%d, transactionCount=%d}",
                typeCode, typeDescription, rowVersion, getTransactionCount());
    }

    /**
     * Business validation method to ensure transaction type integrity
     * Implements COBOL-style validation equivalent to 88-level conditions
     * 
     * @return true if transaction type data is valid
     */
    public boolean isValid() {
        return typeCode != null && 
               typeCode.length() == 2 && 
               typeCode.matches("^[A-Z0-9]{2}$") &&
               typeDescription != null && 
               !typeDescription.trim().isEmpty() && 
               typeDescription.length() <= 50;
    }

    /**
     * Creates a formatted display string for UI presentation
     * Combines type code and description for user-friendly display
     * 
     * @return formatted display string
     */
    public String getDisplayValue() {
        return String.format("%s - %s", typeCode, typeDescription);
    }
}