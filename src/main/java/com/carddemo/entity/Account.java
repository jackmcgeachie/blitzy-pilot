package com.carddemo.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

/**
 * JPA entity class representing account records with exact COBOL field mapping from CVACT01Y copybook.
 * 
 * This entity maps the COBOL ACCOUNT-RECORD structure to PostgreSQL accounts table with:
 * - BigDecimal monetary precision for COMP-3 fields maintaining exact accuracy
 * - Optimistic locking with version fields for concurrent access control
 * - Comprehensive validation annotations matching COBOL 88-level conditions
 * - Account balance and credit limit data required for statement generation processing
 * 
 * COBOL Source: CVACT01Y.cpy - ACCOUNT-RECORD structure (RECLN 300)
 * 
 * Database Table: accounts
 * Primary Key: account_id (VARCHAR(11))
 * Indexes: Primary key on account_id, composite index on (customer_id, account_status)
 * 
 * @author Blitzy agent
 * @version 1.0
 */
@Entity
@Table(name = "accounts", 
       indexes = {
           @Index(name = "idx_accounts_customer_status", columnList = "customer_id, active_status"),
           @Index(name = "idx_accounts_group_id", columnList = "group_id")
       })
public class Account {

    /**
     * Primary key: 11-digit account identifier
     * COBOL: ACCT-ID PIC 9(11) - Account unique identifier
     */
    @Id
    @Column(name = "account_id", length = 11, nullable = false)
    @NotNull(message = "Account ID cannot be null")
    @Pattern(regexp = "^\\d{11}$", message = "Account ID must be exactly 11 digits")
    private String accountId;

    /**
     * Customer identifier linking to customers table
     * Required for account-customer relationship
     */
    @Column(name = "customer_id", length = 9, nullable = false)
    @NotNull(message = "Customer ID cannot be null")
    @Pattern(regexp = "^\\d{9}$", message = "Customer ID must be exactly 9 digits")
    private String customerId;

    /**
     * Account status indicator
     * COBOL: ACCT-ACTIVE-STATUS PIC X(01) - Account active status
     */
    @Column(name = "active_status", length = 1, nullable = false)
    @NotNull(message = "Active status cannot be null")
    @Pattern(regexp = "^[AYN]$", message = "Active status must be A (Active), Y (Yes), or N (No)")
    private String activeStatus;

    /**
     * Current account balance with exact COBOL precision
     * COBOL: ACCT-CURR-BAL PIC S9(10)V99 - Current balance with 2 decimal places
     */
    @Column(name = "current_balance", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Current balance cannot be null")
    @DecimalMin(value = "-99999999.99", message = "Current balance cannot be less than -99999999.99")
    @DecimalMax(value = "99999999.99", message = "Current balance cannot exceed 99999999.99")
    private BigDecimal currentBalance;

    /**
     * Credit limit for the account
     * COBOL: ACCT-CREDIT-LIMIT PIC S9(10)V99 - Credit limit with 2 decimal places
     */
    @Column(name = "credit_limit", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Credit limit cannot be null")
    @DecimalMin(value = "0.00", message = "Credit limit cannot be negative")
    @DecimalMax(value = "99999999.99", message = "Credit limit cannot exceed 99999999.99")
    private BigDecimal creditLimit;

    /**
     * Cash advance credit limit
     * COBOL: ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99 - Cash credit limit with 2 decimal places
     */
    @Column(name = "cash_credit_limit", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Cash credit limit cannot be null")
    @DecimalMin(value = "0.00", message = "Cash credit limit cannot be negative")
    @DecimalMax(value = "99999999.99", message = "Cash credit limit cannot exceed 99999999.99")
    private BigDecimal cashCreditLimit;

    /**
     * Account opening date
     * COBOL: ACCT-OPEN-DATE PIC X(10) - Account opening date YYYY-MM-DD format
     */
    @Column(name = "open_date", nullable = false)
    @NotNull(message = "Open date cannot be null")
    @PastOrPresent(message = "Open date cannot be in the future")
    private LocalDate openDate;

    /**
     * Account expiration date
     * COBOL: ACCT-EXPIRAION-DATE PIC X(10) - Account expiration date YYYY-MM-DD format
     * Note: There's a typo in the original copybook field name "EXPIRAION"
     */
    @Column(name = "expiration_date", nullable = true)
    private LocalDate expirationDate;

    /**
     * Account reissue date
     * COBOL: ACCT-REISSUE-DATE PIC X(10) - Account reissue date YYYY-MM-DD format
     */
    @Column(name = "reissue_date", nullable = true)
    private LocalDate reissueDate;

    /**
     * Current cycle credit amount
     * COBOL: ACCT-CURR-CYC-CREDIT PIC S9(10)V99 - Current cycle credit with 2 decimal places
     */
    @Column(name = "current_cycle_credit", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Current cycle credit cannot be null")
    @DecimalMin(value = "0.00", message = "Current cycle credit cannot be negative")
    @DecimalMax(value = "99999999.99", message = "Current cycle credit cannot exceed 99999999.99")
    private BigDecimal currentCycleCredit;

    /**
     * Current cycle debit amount
     * COBOL: ACCT-CURR-CYC-DEBIT PIC S9(10)V99 - Current cycle debit with 2 decimal places
     */
    @Column(name = "current_cycle_debit", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Current cycle debit cannot be null")
    @DecimalMin(value = "0.00", message = "Current cycle debit cannot be negative")
    @DecimalMax(value = "99999999.99", message = "Current cycle debit cannot exceed 99999999.99")
    private BigDecimal currentCycleDebit;

    /**
     * Address ZIP code
     * COBOL: ACCT-ADDR-ZIP PIC X(10) - Address ZIP code
     */
    @Column(name = "address_zip", length = 10, nullable = true)
    @Pattern(regexp = "^\\d{5}(-\\d{4})?$|^$", message = "ZIP code must be 5 digits or 5+4 format")
    private String addressZip;

    /**
     * Group identifier for discount/category grouping
     * COBOL: ACCT-GROUP-ID PIC X(10) - Group identifier
     */
    @Column(name = "group_id", length = 10, nullable = true)
    @Size(max = 10, message = "Group ID cannot exceed 10 characters")
    private String groupId;

    /**
     * Version number for optimistic locking
     * Prevents concurrent modification conflicts through JPA @Version annotation
     */
    @Version
    @Column(name = "version_number", nullable = false)
    private Long versionNumber;

    /**
     * Record creation timestamp for audit purposes
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Record last update timestamp for audit purposes
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Default constructor for JPA
     */
    public Account() {
        // Initialize monetary fields to zero to match COBOL defaults
        this.currentBalance = BigDecimal.ZERO;
        this.creditLimit = BigDecimal.ZERO;
        this.cashCreditLimit = BigDecimal.ZERO;
        this.currentCycleCredit = BigDecimal.ZERO;
        this.currentCycleDebit = BigDecimal.ZERO;
        this.versionNumber = 0L;
    }

    /**
     * JPA PrePersist callback to set creation timestamp
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * JPA PreUpdate callback to set update timestamp
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getter methods

    /**
     * Gets the account ID
     * @return the 11-digit account identifier
     */
    public String getAccountId() {
        return accountId;
    }

    /**
     * Gets the customer ID
     * @return the 9-digit customer identifier
     */
    public String getCustomerId() {
        return customerId;
    }

    /**
     * Gets the active status
     * @return the account active status (A/Y/N)
     */
    public String getActiveStatus() {
        return activeStatus;
    }

    /**
     * Gets the current balance
     * @return the current account balance with 2 decimal places
     */
    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    /**
     * Gets the credit limit
     * @return the account credit limit with 2 decimal places
     */
    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    /**
     * Gets the cash credit limit
     * @return the cash advance credit limit with 2 decimal places
     */
    public BigDecimal getCashCreditLimit() {
        return cashCreditLimit;
    }

    /**
     * Gets the open date
     * @return the account opening date
     */
    public LocalDate getOpenDate() {
        return openDate;
    }

    /**
     * Gets the expiration date
     * @return the account expiration date
     */
    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    /**
     * Gets the reissue date
     * @return the account reissue date
     */
    public LocalDate getReissueDate() {
        return reissueDate;
    }

    /**
     * Gets the current cycle credit
     * @return the current cycle credit amount with 2 decimal places
     */
    public BigDecimal getCurrentCycleCredit() {
        return currentCycleCredit;
    }

    /**
     * Gets the current cycle debit
     * @return the current cycle debit amount with 2 decimal places
     */
    public BigDecimal getCurrentCycleDebit() {
        return currentCycleDebit;
    }

    /**
     * Gets the address ZIP code
     * @return the address ZIP code
     */
    public String getAddressZip() {
        return addressZip;
    }

    /**
     * Gets the group ID
     * @return the group identifier
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Gets the version number for optimistic locking
     * @return the version number
     */
    public Long getVersionNumber() {
        return versionNumber;
    }

    /**
     * Gets the creation timestamp
     * @return the record creation timestamp
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the last update timestamp
     * @return the record last update timestamp
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Setter methods

    /**
     * Sets the account ID
     * @param accountId the 11-digit account identifier
     */
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    /**
     * Sets the customer ID
     * @param customerId the 9-digit customer identifier
     */
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    /**
     * Sets the active status
     * @param activeStatus the account active status (A/Y/N)
     */
    public void setActiveStatus(String activeStatus) {
        this.activeStatus = activeStatus;
    }

    /**
     * Sets the current balance
     * @param currentBalance the current account balance with 2 decimal places
     */
    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance != null ? currentBalance.setScale(2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
    }

    /**
     * Sets the credit limit
     * @param creditLimit the account credit limit with 2 decimal places
     */
    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit != null ? creditLimit.setScale(2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
    }

    /**
     * Sets the cash credit limit
     * @param cashCreditLimit the cash advance credit limit with 2 decimal places
     */
    public void setCashCreditLimit(BigDecimal cashCreditLimit) {
        this.cashCreditLimit = cashCreditLimit != null ? cashCreditLimit.setScale(2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
    }

    /**
     * Sets the open date
     * @param openDate the account opening date
     */
    public void setOpenDate(LocalDate openDate) {
        this.openDate = openDate;
    }

    /**
     * Sets the expiration date
     * @param expirationDate the account expiration date
     */
    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    /**
     * Sets the reissue date
     * @param reissueDate the account reissue date
     */
    public void setReissueDate(LocalDate reissueDate) {
        this.reissueDate = reissueDate;
    }

    /**
     * Sets the current cycle credit
     * @param currentCycleCredit the current cycle credit amount with 2 decimal places
     */
    public void setCurrentCycleCredit(BigDecimal currentCycleCredit) {
        this.currentCycleCredit = currentCycleCredit != null ? currentCycleCredit.setScale(2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
    }

    /**
     * Sets the current cycle debit
     * @param currentCycleDebit the current cycle debit amount with 2 decimal places
     */
    public void setCurrentCycleDebit(BigDecimal currentCycleDebit) {
        this.currentCycleDebit = currentCycleDebit != null ? currentCycleDebit.setScale(2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
    }

    /**
     * Sets the address ZIP code
     * @param addressZip the address ZIP code
     */
    public void setAddressZip(String addressZip) {
        this.addressZip = addressZip;
    }

    /**
     * Sets the group ID
     * @param groupId the group identifier
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * Sets the version number for optimistic locking
     * @param versionNumber the version number
     */
    public void setVersionNumber(Long versionNumber) {
        this.versionNumber = versionNumber;
    }

    // Business logic methods

    /**
     * Calculates available credit based on current balance and credit limit
     * @return available credit amount
     */
    public BigDecimal getAvailableCredit() {
        if (creditLimit == null || currentBalance == null) {
            return BigDecimal.ZERO;
        }
        return creditLimit.subtract(currentBalance).max(BigDecimal.ZERO);
    }

    /**
     * Calculates available cash credit based on current balance and cash credit limit
     * @return available cash credit amount
     */
    public BigDecimal getAvailableCashCredit() {
        if (cashCreditLimit == null || currentBalance == null) {
            return BigDecimal.ZERO;
        }
        return cashCreditLimit.subtract(currentBalance).max(BigDecimal.ZERO);
    }

    /**
     * Checks if the account is active
     * @return true if account status is 'A' or 'Y', false otherwise
     */
    public boolean isActive() {
        return "A".equals(activeStatus) || "Y".equals(activeStatus);
    }

    /**
     * Checks if the account is expired
     * @return true if current date is after expiration date, false otherwise
     */
    public boolean isExpired() {
        if (expirationDate == null) {
            return false;
        }
        return LocalDate.now().isAfter(expirationDate);
    }

    // Object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(accountId, account.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }

    @Override
    public String toString() {
        return "Account{" +
                "accountId='" + accountId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", activeStatus='" + activeStatus + '\'' +
                ", currentBalance=" + currentBalance +
                ", creditLimit=" + creditLimit +
                ", cashCreditLimit=" + cashCreditLimit +
                ", openDate=" + openDate +
                ", expirationDate=" + expirationDate +
                ", reissueDate=" + reissueDate +
                ", currentCycleCredit=" + currentCycleCredit +
                ", currentCycleDebit=" + currentCycleDebit +
                ", addressZip='" + addressZip + '\'' +
                ", groupId='" + groupId + '\'' +
                ", versionNumber=" + versionNumber +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}