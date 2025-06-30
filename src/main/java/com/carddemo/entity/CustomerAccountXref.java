package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA entity class implementing cross-reference table for Customer-Account relationships
 * providing alternate index access per PostgreSQL database design.
 * 
 * Converted from COBOL structures CUSTREC.cpy and CVACT01Y.cpy to maintain
 * bidirectional entity relationships through dedicated cross-reference tables.
 * 
 * Features:
 * - Composite primary key using @EmbeddedId with customer_id and account_id components
 * - @ManyToOne relationships to both Customer and Account entities for alternate index access
 * - Optimistic locking through @Version field per Section 6.2.1.1 database design requirements
 * - Composite indexes on (customer_id, account_id) per Section 6.2.1.3 indexing strategy
 * - Audit trail fields for relationship establishment and modification tracking
 * - Validation annotations for referential integrity and data consistency
 * 
 * @author Blitzy agent
 * @version 1.0
 */
@Entity
@Table(name = "customer_account_xref",
       indexes = {
           @Index(name = "idx_customer_account_xref_customer_id", columnList = "customer_id"),
           @Index(name = "idx_customer_account_xref_account_id", columnList = "account_id"),
           @Index(name = "idx_customer_account_xref_composite", columnList = "customer_id, account_id")
       })
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class CustomerAccountXref implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Composite primary key for customer-account cross-reference relationship
     */
    @EmbeddedId
    private CustomerAccountXrefId id;

    /**
     * ManyToOne relationship to Customer entity for alternate index access
     * Maps to customer_id component of composite primary key
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("customerId")
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", nullable = false)
    private Customer customer;

    /**
     * ManyToOne relationship to Account entity for alternate index access
     * Maps to account_id component of composite primary key
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("accountId")
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", nullable = false)
    private Account account;

    /**
     * Optimistic locking version field per Section 6.2.1.1 database design requirements
     * Enables concurrent access control replacing CICS record-level sharing (RLS)
     */
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion = 0;

    /**
     * Audit trail field - relationship establishment timestamp
     * Tracks when the customer-account relationship was first created
     */
    @Column(name = "relationship_created_at", nullable = false, updatable = false)
    private LocalDateTime relationshipCreatedAt;

    /**
     * Audit trail field - relationship modification timestamp
     * Tracks when the customer-account relationship was last modified
     */
    @Column(name = "relationship_updated_at", nullable = false)
    private LocalDateTime relationshipUpdatedAt;

    /**
     * Audit trail field - user who created the relationship
     * Tracks which user established the customer-account relationship
     */
    @Column(name = "created_by", length = 8, nullable = false, updatable = false)
    @Pattern(regexp = "^[A-Z0-9]{1,8}$", message = "Created by user ID must be 1-8 alphanumeric characters")
    private String createdBy;

    /**
     * Audit trail field - user who last modified the relationship
     * Tracks which user last modified the customer-account relationship
     */
    @Column(name = "updated_by", length = 8, nullable = false)
    @Pattern(regexp = "^[A-Z0-9]{1,8}$", message = "Updated by user ID must be 1-8 alphanumeric characters")
    private String updatedBy;

    /**
     * Default constructor for JPA
     */
    public CustomerAccountXref() {
    }

    /**
     * Constructor with customer and account entities
     * Automatically sets audit timestamps and builds composite primary key
     *
     * @param customer the customer entity
     * @param account the account entity
     * @param createdBy the user ID who created this relationship
     */
    public CustomerAccountXref(Customer customer, Account account, String createdBy) {
        this.customer = customer;
        this.account = account;
        this.id = new CustomerAccountXrefId(customer.getCustomerId(), account.getAccountId());
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.relationshipCreatedAt = LocalDateTime.now();
        this.relationshipUpdatedAt = LocalDateTime.now();
    }

    /**
     * JPA PrePersist callback to set audit timestamps on creation
     */
    @PrePersist
    protected void onCreate() {
        if (relationshipCreatedAt == null) {
            relationshipCreatedAt = LocalDateTime.now();
        }
        if (relationshipUpdatedAt == null) {
            relationshipUpdatedAt = LocalDateTime.now();
        }
    }

    /**
     * JPA PreUpdate callback to update modification timestamp
     */
    @PreUpdate
    protected void onUpdate() {
        relationshipUpdatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public CustomerAccountXrefId getId() {
        return id;
    }

    public void setId(CustomerAccountXrefId id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
        if (customer != null && this.id != null) {
            this.id.setCustomerId(customer.getCustomerId());
        }
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
        if (account != null && this.id != null) {
            this.id.setAccountId(account.getAccountId());
        }
    }

    public Integer getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Integer rowVersion) {
        this.rowVersion = rowVersion;
    }

    public LocalDateTime getRelationshipCreatedAt() {
        return relationshipCreatedAt;
    }

    public void setRelationshipCreatedAt(LocalDateTime relationshipCreatedAt) {
        this.relationshipCreatedAt = relationshipCreatedAt;
    }

    public LocalDateTime getRelationshipUpdatedAt() {
        return relationshipUpdatedAt;
    }

    public void setRelationshipUpdatedAt(LocalDateTime relationshipUpdatedAt) {
        this.relationshipUpdatedAt = relationshipUpdatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerAccountXref that = (CustomerAccountXref) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CustomerAccountXref{" +
                "id=" + id +
                ", rowVersion=" + rowVersion +
                ", relationshipCreatedAt=" + relationshipCreatedAt +
                ", relationshipUpdatedAt=" + relationshipUpdatedAt +
                ", createdBy='" + createdBy + '\'' +
                ", updatedBy='" + updatedBy + '\'' +
                '}';
    }

    /**
     * Composite primary key class for CustomerAccountXref entity
     * Implements Serializable interface as required for JPA composite keys
     */
    @Embeddable
    public static class CustomerAccountXrefId implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Customer ID component of composite primary key
         * Maps to COBOL PIC 9(09) CUST-ID from CUSTREC.cpy
         */
        @Column(name = "customer_id", length = 9, nullable = false)
        @NotNull(message = "Customer ID is required")
        @Pattern(regexp = "^\\d{9}$", message = "Customer ID must be exactly 9 digits")
        private String customerId;

        /**
         * Account ID component of composite primary key
         * Maps to COBOL PIC 9(11) ACCT-ID from CVACT01Y.cpy
         */
        @Column(name = "account_id", length = 11, nullable = false)
        @NotNull(message = "Account ID is required")
        @Pattern(regexp = "^\\d{11}$", message = "Account ID must be exactly 11 digits")
        private String accountId;

        /**
         * Default constructor for JPA
         */
        public CustomerAccountXrefId() {
        }

        /**
         * Constructor with customer ID and account ID
         *
         * @param customerId the 9-digit customer ID
         * @param accountId the 11-digit account ID
         */
        public CustomerAccountXrefId(String customerId, String accountId) {
            this.customerId = customerId;
            this.accountId = accountId;
        }

        // Getters and Setters

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomerAccountXrefId that = (CustomerAccountXrefId) o;
            return Objects.equals(customerId, that.customerId) &&
                   Objects.equals(accountId, that.accountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(customerId, accountId);
        }

        @Override
        public String toString() {
            return "CustomerAccountXrefId{" +
                    "customerId='" + customerId + '\'' +
                    ", accountId='" + accountId + '\'' +
                    '}';
        }
    }
}