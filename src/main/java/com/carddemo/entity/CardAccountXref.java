package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA entity class implementing cross-reference table for bidirectional Card-Account relationships
 * per PostgreSQL database design. This entity provides efficient navigation between Card and Account
 * entities through a dedicated cross-reference table with composite primary key structure.
 * 
 * <p>Converted from COBOL CARD-RECORD and ACCOUNT-RECORD structures found in CVACT02Y.cpy and CVACT01Y.cpy.
 * Implements bidirectional navigation capabilities that replicate VSAM alternate index functionality
 * through PostgreSQL composite indexes for optimal query performance.</p>
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Composite primary key using @EmbeddedId with card_number and account_id components</li>
 *   <li>@ManyToOne relationships to both Card and Account entities for bidirectional navigation</li>
 *   <li>Optimistic locking through @Version field per Section 6.2.1.1 database design requirements</li>
 *   <li>Composite index annotations for (card_number, account_id) per Section 6.2.1.3 indexing strategy</li>
 *   <li>Audit trail fields for relationship tracking and modification history</li>
 *   <li>Validation annotations for referential integrity constraints</li>
 * </ul>
 *
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-12-01
 */
@Entity
@Table(name = "card_account_xref", 
       indexes = {
           @Index(name = "idx_card_account_xref_composite", 
                  columnList = "card_number, account_id", 
                  unique = true),
           @Index(name = "idx_card_account_xref_card", 
                  columnList = "card_number"),
           @Index(name = "idx_card_account_xref_account", 
                  columnList = "account_id")
       })
public class CardAccountXref implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Composite primary key using card_number and account_id components.
     * Implements @Embeddable pattern for efficient database operations.
     */
    @Embeddable
    public static class CardAccountXrefId implements Serializable {
        
        private static final long serialVersionUID = 1L;

        /**
         * Card number component - 16 digit card identifier
         * Maps to CARD-NUM from CVACT02Y.cpy PIC X(16)
         */
        @Column(name = "card_number", length = 16)
        @NotNull(message = "Card number is required")
        @Pattern(regexp = "^\\d{16}$", message = "Card number must be exactly 16 digits")
        private String cardNumber;

        /**
         * Account ID component - 11 digit account identifier  
         * Maps to ACCT-ID from CVACT01Y.cpy PIC 9(11)
         */
        @Column(name = "account_id", length = 11)
        @NotNull(message = "Account ID is required")
        @Pattern(regexp = "^\\d{11}$", message = "Account ID must be exactly 11 digits")
        private String accountId;

        /**
         * Default constructor for JPA
         */
        public CardAccountXrefId() {}

        /**
         * Constructor with card number and account ID
         *
         * @param cardNumber the 16-digit card number
         * @param accountId the 11-digit account ID
         */
        public CardAccountXrefId(String cardNumber, String accountId) {
            this.cardNumber = cardNumber;
            this.accountId = accountId;
        }

        // Getters and setters
        public String getCardNumber() {
            return cardNumber;
        }

        public void setCardNumber(String cardNumber) {
            this.cardNumber = cardNumber;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CardAccountXrefId that = (CardAccountXrefId) obj;
            return Objects.equals(cardNumber, that.cardNumber) && 
                   Objects.equals(accountId, that.accountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cardNumber, accountId);
        }

        @Override
        public String toString() {
            return String.format("CardAccountXrefId{cardNumber='%s', accountId='%s'}", 
                               cardNumber, accountId);
        }
    }

    /**
     * Composite primary key instance
     */
    @EmbeddedId
    private CardAccountXrefId id;

    /**
     * ManyToOne relationship to Card entity for bidirectional navigation
     * Uses card_number component of composite key as foreign key
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_number", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_card_account_xref_card"))
    private Card card;

    /**
     * ManyToOne relationship to Account entity for bidirectional navigation  
     * Uses account_id component of composite key as foreign key
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", insertable = false, updatable = false,
                foreignKey = @ForeignKey(name = "fk_card_account_xref_account"))
    private Account account;

    /**
     * Optimistic locking version field per Section 6.2.1.1 database design requirements.
     * Automatically managed by JPA for concurrent access control.
     */
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer version = 0;

    /**
     * Relationship status indicator for active/inactive associations
     */
    @Column(name = "relationship_status", length = 1, nullable = false)
    @NotNull(message = "Relationship status is required")
    @Pattern(regexp = "^[AIS]$", message = "Relationship status must be A (Active), I (Inactive), or S (Suspended)")
    private String relationshipStatus = "A";

    /**
     * Relationship type code for classification of card-account associations
     */
    @Column(name = "relationship_type", length = 2, nullable = false)
    @NotNull(message = "Relationship type is required")
    @Size(min = 1, max = 2, message = "Relationship type must be 1-2 characters")
    private String relationshipType = "PR"; // Primary relationship

    /**
     * Creation timestamp for audit trail tracking
     */
    @CreationTimestamp
    @Column(name = "created_timestamp", nullable = false, updatable = false)
    private LocalDateTime createdTimestamp;

    /**
     * Last update timestamp for audit trail tracking
     */
    @UpdateTimestamp
    @Column(name = "updated_timestamp", nullable = false)
    private LocalDateTime updatedTimestamp;

    /**
     * User ID who created this relationship for audit purposes
     */
    @Column(name = "created_by", length = 8, nullable = false, updatable = false)
    @NotNull(message = "Created by is required")
    @Size(min = 1, max = 8, message = "Created by must be 1-8 characters")
    private String createdBy;

    /**
     * User ID who last updated this relationship for audit purposes
     */
    @Column(name = "updated_by", length = 8, nullable = false)
    @NotNull(message = "Updated by is required")
    @Size(min = 1, max = 8, message = "Updated by must be 1-8 characters") 
    private String updatedBy;

    /**
     * Default constructor for JPA
     */
    public CardAccountXref() {}

    /**
     * Constructor with composite primary key
     *
     * @param id the composite primary key
     */
    public CardAccountXref(CardAccountXrefId id) {
        this.id = id;
    }

    /**
     * Constructor with card number and account ID
     *
     * @param cardNumber the 16-digit card number
     * @param accountId the 11-digit account ID
     */
    public CardAccountXref(String cardNumber, String accountId) {
        this.id = new CardAccountXrefId(cardNumber, accountId);
    }

    /**
     * Constructor with card and account entities
     *
     * @param card the Card entity
     * @param account the Account entity
     */
    public CardAccountXref(Card card, Account account) {
        this.id = new CardAccountXrefId(card.getCardNumber(), account.getAccountId());
        this.card = card;
        this.account = account;
    }

    // Getters and setters

    public CardAccountXrefId getId() {
        return id;
    }

    public void setId(CardAccountXrefId id) {
        this.id = id;
    }

    public Card getCard() {
        return card;
    }

    public void setCard(Card card) {
        this.card = card;
        if (card != null && this.id != null) {
            this.id.setCardNumber(card.getCardNumber());
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getRelationshipStatus() {
        return relationshipStatus;
    }

    public void setRelationshipStatus(String relationshipStatus) {
        this.relationshipStatus = relationshipStatus;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public LocalDateTime getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(LocalDateTime createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public LocalDateTime getUpdatedTimestamp() {
        return updatedTimestamp;
    }

    public void setUpdatedTimestamp(LocalDateTime updatedTimestamp) {
        this.updatedTimestamp = updatedTimestamp;
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

    /**
     * Convenience method to get card number from composite key
     *
     * @return the card number
     */
    public String getCardNumber() {
        return id != null ? id.getCardNumber() : null;
    }

    /**
     * Convenience method to get account ID from composite key
     *
     * @return the account ID  
     */
    public String getAccountId() {
        return id != null ? id.getAccountId() : null;
    }

    /**
     * Business method to check if relationship is active
     *
     * @return true if relationship status is 'A' (Active)
     */
    public boolean isActive() {
        return "A".equals(relationshipStatus);
    }

    /**
     * Business method to activate the relationship
     */
    public void activate() {
        this.relationshipStatus = "A";
    }

    /**
     * Business method to deactivate the relationship
     */
    public void deactivate() {
        this.relationshipStatus = "I";
    }

    /**
     * Business method to suspend the relationship
     */
    public void suspend() {
        this.relationshipStatus = "S";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CardAccountXref that = (CardAccountXref) obj;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format(
            "CardAccountXref{id=%s, relationshipStatus='%s', relationshipType='%s', version=%d, createdBy='%s', updatedBy='%s'}",
            id, relationshipStatus, relationshipType, version, createdBy, updatedBy);
    }

    /**
     * JPA PrePersist callback to ensure audit fields are set
     */
    @PrePersist
    protected void onCreate() {
        if (createdTimestamp == null) {
            createdTimestamp = LocalDateTime.now();
        }
        if (updatedTimestamp == null) {
            updatedTimestamp = LocalDateTime.now();
        }
        if (createdBy == null) {
            // This should be set by the service layer with the current user
            createdBy = "SYSTEM";
        }
        if (updatedBy == null) {
            updatedBy = createdBy;
        }
    }

    /**
     * JPA PreUpdate callback to ensure update audit fields are set
     */
    @PreUpdate
    protected void onUpdate() {
        updatedTimestamp = LocalDateTime.now();
        if (updatedBy == null) {
            // This should be set by the service layer with the current user
            updatedBy = "SYSTEM";
        }
    }
}